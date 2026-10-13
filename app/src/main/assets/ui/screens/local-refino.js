(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  /**
   * Refino local (dono, 2026-10-13: "só me mostre um botão e acabou"). Substitui a antiga aba Aprender.
   * A ECU faz tudo (AutoMatch, buffers, bolinhas); o app só lê e registra ONDE o carro engasgou no GNV (StallWatch).
   * Aqui: uma frase do que aconteceu e UM botão. O botão lê o Mapa K da ECU na hora, escolhe as células do lugar do
   * engasgo (core/stall-region.js), mostra o que vai fazer e grava com um toque (ACK + leitura de volta pela ponte);
   * depois oferece "Desfazer". Nada é gravado sem o toque em "Gravar".
   */
  const STALL_EVERY_TICKS = 1;
  const POLL_MS = 300;
  const TIMEOUT_MS = 60000;
  const finite = value => value === null || value === undefined || value === '' || !Number.isFinite(Number(value)) ? null : Number(value);
  const esc = value => String(value ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const vezes = n => (n === 1 ? '1 vez' : `${n} vezes`);
  const ms1 = v => Number(v).toFixed(1).replace('.', ',');

  /** O que dizer sobre os engasgos registrados (puro: testado sem tela). */
  function describe(stalls) {
    const dead = finite(stalls?.count) || 0;
    const near = finite(stalls?.nearCount) || 0;
    const total = dead + near;
    if (!total) {
      return { total: 0, title: 'Nenhum engasgo registrado', text: 'Dirija normalmente. Se o carro engasgar ou apagar no GNV, ele aparece aqui.' };
    }
    const top = root.OmegasStallRegion?.topRegion?.(stalls);
    const when = top ? ` Quase sempre ao soltar e voltar do acelerador, perto de ${ms1(top.fromMs)} ms.` : '';
    const died = dead ? ` Em ${dead === 1 ? '1' : dead} o motor apagou.` : '';
    return { total, title: `O carro engasgou ${vezes(total)} no GNV`, text: `${died}${when}`.trim() };
  }

  class LocalRefinoScreen {
    constructor(app) {
      this.app = app;
      this.store = app.store;
      this.scheduler = app.scheduler;
      this.native = app.api;
      this.api = ns.AutoCalApi;
      this.editor = new root.OmegasMapEditor.MapEditor();
      this.stalls = null;
      this.state = { phase: 'idle' };
      this.ticks = 0;
      this.lastHtml = '';
      this.inject();
      this.unsubscribe = this.scheduler.addHook('status', () => {
        if (this.store.get().route !== 'local') return;
        this.ticks += 1;
        if (this.ticks % STALL_EVERY_TICKS === 0) this.refresh();
      });
      let lastRoute = null;
      this.store.subscribe(state => {
        if (state.route === lastRoute) return;
        lastRoute = state.route;
        if (state.route === 'local') root.setTimeout(() => this.refresh(true), 0);
      }, true);
    }

    inject() {
      this.host = document.getElementById('localRefinoHost');
      if (!this.host) return;
      this.host.addEventListener('click', event => {
        const action = event.target.closest('[data-local-action]')?.dataset.localAction;
        if (action === 'fix') this.start();
        else if (action === 'write') this.write(this.state.items, 'Refino local: reforço no engasgo', 'done');
        else if (action === 'cancel' || action === 'ok') { this.state = { phase: 'idle' }; this.render(true); }
        else if (action === 'undo') this.write(this.state.undoItems, 'Refino local: desfazer', 'undone');
      });
      this.render(true);
    }

    refresh(force) {
      if (this.state.phase === 'reading' || this.state.phase === 'writing') return;
      const reading = this.api?.stalls?.() || { ok: false };
      this.stalls = reading.ok === false ? null : (reading.stalls || null);
      this.render(force === true);
    }

    /** 1) lê o Mapa K da ECU agora; 2) escolhe as células; 3) mostra o que vai fazer. */
    start() {
      if (this.state.phase !== 'idle' || !this.stalls) return;
      const started = this.native.startMapRead();
      if (!started?.ok || !started?.started) { this.fail(started?.error || 'Não consegui ler o Mapa K da ECU.'); return; }
      this.state = { phase: 'reading', message: 'Lendo o Mapa K da ECU…' };
      this.render(true);
      this.poll(() => this.native.mapReadResult(), result => {
        if (result.ok === false || result.state === 'FAILED') { this.fail(result.error || 'Falha ao ler o Mapa K.'); return; }
        try { this.prepare(result); } catch (error) { this.fail(error.message); }
      });
    }

    prepare(mapResult) {
      this.editor.reset();
      this.editor.load(mapResult);
      const axes = this.editor.snapshot().axes;
      const suggestion = root.OmegasStallRegion.suggest(this.stalls, { petrolBins: axes.petrolBins, rpmBins: axes.rpmBins });
      if (!suggestion.ok) { this.state = { phase: 'idle', note: suggestion.reason }; this.render(true); return; }
      this.editor.clearSelection();
      suggestion.cells.forEach(cell => this.editor.toggle(cell.row, cell.column));
      const preview = this.native.previewMapAdjustment(this.editor.selectedCells(), 'percent', suggestion.percent);
      if (!preview?.ok || !Array.isArray(preview.items)) throw new Error(preview?.error || 'Prévia do Mapa K indisponível.');
      this.editor.setTargetOverrides(preview.items.map(item => ({ row: Number(item.row), column: Number(item.column), after: Number(item.target) })));
      let review;
      try { review = this.editor.buildReview(); } catch (_) { review = null; }
      if (!review?.items?.length) { this.state = { phase: 'idle', note: 'As células desse lugar já estão no limite do Mapa K: nada a reforçar.' }; this.render(true); return; }
      const region = suggestion.region;
      this.state = {
        phase: 'ready',
        items: review.items,
        undoItems: review.items.map(item => ({ ...item, current: item.target, target: item.current })),
        summary: `Vou reforçar o GNV em ${review.items.length} ${review.items.length === 1 ? 'célula' : 'células'} do Mapa K (+${suggestion.percent}%), perto de ${ms1(region.fromMs)}–${ms1(region.toMs)} ms. O resto do mapa não muda.`,
      };
      this.render(true);
    }

    /** Grava (ACK + leitura de volta na ponte) e só diz "gravado" com BATCH_CONFIRMED + readbackValid. */
    write(items, reason, doneKind) {
      if (!Array.isArray(items) || !items.length || this.state.phase === 'writing') return;
      const result = this.native.writeMap(items, 0, 0, reason);
      if (!result?.ok || !result?.started) { this.fail(result?.error || 'A gravação não iniciou.'); return; }
      const undoItems = this.state.undoItems;
      this.state = { phase: 'writing', message: 'Gravando e conferindo na ECU…', undoItems };
      this.render(true);
      const startedAt = result.startedAt ?? null;
      this.poll(() => this.native.mapWriteOperation(), operation => {
        if (operation.state === 'BATCH_CONFIRMED' && operation.readbackValid === true) {
          this.state = doneKind === 'done' ? { phase: 'done', undoItems } : { phase: 'undone' };
          this.render(true);
        } else {
          this.fail(operation.failure?.error || operation.error || 'A ECU não confirmou a gravação. Confira o Mapa K antes de dirigir.');
        }
      }, operation => startedAt == null || operation.startedAt === startedAt);
    }

    poll(read, onFinish, accept = () => true) {
      const started = Date.now();
      const tick = () => {
        const status = read() || {};
        const waiting = !accept(status) || status.busy === true || /QUEUED|READING|WRITING/.test(String(status.state || ''));
        if (waiting) {
          if (Date.now() - started > TIMEOUT_MS) { this.fail('Tempo limite aguardando a ECU.'); return; }
          root.setTimeout(tick, POLL_MS);
          return;
        }
        onFinish(status);
      };
      root.setTimeout(tick, POLL_MS);
    }

    fail(message) {
      this.state = { phase: 'failed', message: String(message || 'Falha') };
      this.render(true);
    }

    render(force) {
      if (!this.host) return;
      const s = this.state;
      const info = describe(this.stalls);
      let title = info.title;
      let text = info.text;
      let buttons = '';
      let level = info.total ? 'warn' : 'ok';
      if (!this.api?.available?.() && !this.stalls) {
        title = 'Sem ligação com a ECU';
        text = 'Ligue a ignição e confira o cabo USB. A tela volta sozinha.';
        level = 'idle';
        buttons = '<button type="button" class="local-main" disabled>Corrigir engasgo</button>';
      } else if (s.phase === 'idle') {
        if (s.note) text = s.note;
        buttons = `<button type="button" class="local-main" data-local-action="fix"${info.total ? '' : ' disabled'}>Corrigir engasgo</button>`;
      } else if (s.phase === 'reading' || s.phase === 'writing') {
        title = s.message; text = 'Não desligue o carro nem tire o cabo.'; level = 'busy';
        buttons = '<button type="button" class="local-main" disabled>Aguarde…</button>';
      } else if (s.phase === 'ready') {
        title = 'Pronto para corrigir'; text = s.summary; level = 'warn';
        buttons = '<button type="button" class="local-main" data-local-action="write">Gravar</button><button type="button" class="local-second" data-local-action="cancel">Cancelar</button>';
      } else if (s.phase === 'done') {
        title = 'Gravado e conferido na ECU'; text = 'Dirija e veja se o engasgo some. Se piorar, desfaça.'; level = 'ok';
        buttons = '<button type="button" class="local-main" data-local-action="ok">Pronto</button><button type="button" class="local-second" data-local-action="undo">Desfazer</button>';
      } else if (s.phase === 'undone') {
        title = 'Desfeito e conferido na ECU'; text = 'O Mapa K voltou ao valor de antes.'; level = 'ok';
        buttons = '<button type="button" class="local-main" data-local-action="ok">Pronto</button>';
      } else if (s.phase === 'failed') {
        title = 'Não deu certo'; text = `${s.message} Nada foi dado como gravado.`; level = 'bad';
        buttons = '<button type="button" class="local-main" data-local-action="ok">Entendi</button>';
      }
      const html = `<section class="local-refino" data-level="${level}"><h2>${esc(title)}</h2><p>${esc(text)}</p><div class="local-actions">${buttons}</div></section>`;
      if (!force && html === this.lastHtml) return;
      this.lastHtml = html;
      this.host.innerHTML = html;
    }
  }

  function boot() {
    const app = root.OmegasApp;
    if (!app?.store || !app?.scheduler || !app?.api || !ns.AutoCalApi || !root.OmegasMapEditor || !root.OmegasStallRegion) {
      root.setTimeout(boot, 50);
      return;
    }
    if (app.localRefino) return;
    app.localRefino = new LocalRefinoScreen(app);
  }

  ns.LocalRefinoModel = { describe };
  ns.LocalRefinoScreen = LocalRefinoScreen;
  boot();
})(typeof window !== 'undefined' ? window : globalThis);
