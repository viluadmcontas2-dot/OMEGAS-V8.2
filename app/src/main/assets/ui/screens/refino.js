(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Refino OMEGAS: o "nosso AutoCal". O gráfico é O MESMO objeto do AutoCal (screens/autocal-original.js: zonas, curvas da
  // ECU, curva K, AGORA), sem o painel de aquisição à esquerda; por cima entram só os nossos pontos, a nossa curva e as marcas
  // de engasgo (dono, 2026-10-13).
  // A matemática é do Kotlin (AutoMatchRefinedEngine, EquivalenceLedger, diário e piloto).
  // A fase, a frase e os botões também: esta tela só desenha o que o piloto decidiu
  // (eq.autopilot.action/secondary/proposal) e nunca calcula um "pronto" próprio.
  // Nada grava sozinho: a gravação usa o fluxo da Curva K da Platina (leitura → conferência
  // → escrita → ACK → readback), sempre depois do toque de confirmação.

  const PHASES = [
    ['ECU_TRABALHANDO', 'ECU no automático'],
    ['COLETANDO_NOSSOS', 'Nossos pontos'],
    ['PROPOSTA_PRONTA', 'Curva pronta'],
    ['VERIFICANDO', 'Verificando'],
    ['ESTAVEL', 'Estável'],
  ];
  const PHASE_TONE = {
    SEM_ECU: 'unknown', ECU_TRABALHANDO: 'unknown', COLETANDO_NOSSOS: 'collecting',
    PROPOSTA_PRONTA: 'ready', VERIFICANDO: 'collecting', RESTAURAR_TRECHO: 'problem', ESTAVEL: 'ok',
  };
  /** As três palavras do dono para cada ponto gravado (+ sem medição possível). */
  const OUTCOME = { MELHOROU: 'melhorou', PIOROU: 'piorou', MEDINDO: 'ainda medindo', SEM_MEDICAO: 'sem medição' };
  const ORIGIN = { MEASURED: 'Medido', BLENDED: 'Transição', SMOOTHED: 'Anti-tranco', HELD: 'Mantido', LOW_GUARD: 'Trava da baixa' };
  /** Motivo que vai para a ECU com a gravação; o Kotlin classifica por ele (RefinementJournal.kindFromReason). */
  const REASONS = {
    apply: 'Refino OMEGAS: curva refinada confirmada',
    restore: 'Refino OMEGAS: restaurar pontos que pioraram',
    undo: 'Refino OMEGAS: desfazer última gravação',
  };
  const TITLES = { apply: 'Gravar curva refinada', restore: 'Restaurar pontos que pioraram', undo: 'Desfazer última gravação' };
  const POLL_MS = 300;
  const OPERATION_TIMEOUT_MS = 90000;
  const DATA_EVERY_TICKS = 2;

  function finite(value) {
    if (value === null || value === undefined || value === '') return null;
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[char]));
  }
  function fmt(value, digits) {
    const number = finite(value);
    return number === null ? '—' : number.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  }
  function pct(ratio) {
    const r = finite(ratio);
    if (r === null) return '—';
    const p = (r - 1) * 100;
    return `${p > 0 ? '+' : ''}${fmt(p, 1)}%`;
  }
  function plural(n, one, many) { return Number(n) === 1 ? one : many; }
  function pointList(list) {
    return (Array.isArray(list) ? list : [])
      .filter(p => p && finite(p.index) !== null && finite(p.currentRaw) !== null && finite(p.targetRaw) !== null)
      .map(p => ({ index: Number(p.index), currentRaw: Number(p.currentRaw), targetRaw: Number(p.targetRaw), referenceTimeMs: finite(p.referenceTimeMs), origin: p.origin || null }));
  }
  function signatureOf(prefix, points) {
    return prefix + points.map(p => `${p.index}:${p.currentRaw}>${p.targetRaw}`).join(';');
  }

  const vezes = n => (n === 1 ? '1 vez' : `${n} vezes`);
  /** O que dizer sobre os engasgos registrados (puro: testado sem tela). */
  function describeStalls(stalls) {
    const dead = finite(stalls?.count) || 0;
    const near = finite(stalls?.nearCount) || 0;
    const total = dead + near;
    if (!total) return { total: 0, title: 'Nenhum engasgo registrado', text: '' };
    const top = root.OmegasStallRegion?.topRegion?.(stalls);
    const when = top ? ` Quase sempre ao soltar e voltar do acelerador, perto de ${Number(top.fromMs).toFixed(1).replace('.', ',')} ms.` : '';
    const died = dead ? ` Em ${dead} o motor apagou.` : '';
    return { total, title: `O carro engasgou ${vezes(total)} no GNV`, text: `${died}${when}`.trim() };
  }

  /** Pontos que o motor mudaria (regra de entrada, só para os detalhes técnicos; o botão vem do Kotlin). */
  function proposedPoints(analysis) {
    const points = Array.isArray(analysis?.points) ? analysis.points : [];
    return points
      .filter(p => p && p.origin !== 'HELD' && finite(p.calculatedRaw) !== null && Number(p.calculatedRaw) !== Number(p.currentRaw))
      .map(p => ({ index: Number(p.index), currentRaw: Number(p.currentRaw), targetRaw: Number(p.calculatedRaw) }));
  }

  /**
   * Desfazer a última gravação registrada no diário (antes ← depois). Se o Kotlin diz que não há o que
   * desfazer (undoable === false: foi um desfazer/restauração, já desfeita ou aceita), não há lista — sem laço.
   */
  function undoPoints(latest) {
    if (latest && latest.undoable === false) return [];
    if (Array.isArray(latest?.undoPoints)) return pointList(latest.undoPoints).map(({ index, currentRaw, targetRaw }) => ({ index, currentRaw, targetRaw }));
    const before = latest?.beforeRaw;
    const after = latest?.afterRaw;
    if (!Array.isArray(before) || !Array.isArray(after) || before.length !== 30 || after.length !== 30) return [];
    const out = [];
    for (let i = 0; i < 30; i += 1) if (Number(before[i]) !== Number(after[i])) out.push({ index: i, currentRaw: Number(after[i]), targetRaw: Number(before[i]) });
    return out;
  }

  /** Ação principal = a que o piloto (Kotlin) decidiu para a fase. Sem ação do Kotlin, nenhum botão. */
  function primaryAction(eq) {
    const action = eq?.autopilot?.action;
    const kind = String(action?.kind || 'none');
    if (kind === 'none' || !action) return { kind: 'none', label: '', enabled: false };
    const enabled = action.enabled !== false && kind !== 'waiting' && kind !== 'stable';
    return { kind, label: String(action.label || ''), enabled };
  }

  /**
   * Ações a mais decididas no Kotlin (um botão por ação): "Revisar e gravar" junto de "Restaurar" (pontos
   * independentes) e "Desfazer última gravação" (só enquanto a última proposta gravada ainda é medida).
   */
  function secondaryActions(eq) {
    const list = Array.isArray(eq?.autopilot?.secondary) ? eq.autopilot.secondary : [];
    return list.filter(s => s && (s.kind === 'review' || s.kind === 'undo'))
      .map(s => ({ kind: s.kind, label: String(s.label || (s.kind === 'undo' ? 'Desfazer última gravação' : 'Revisar e gravar')), enabled: s.enabled !== false }));
  }

  /**
   * Ajuste local no Mapa K decidido no Kotlin (piloto: grupo separável e diferente das outras colunas no mesmo t).
   * Sem a ação do Kotlin, ou sem células com número, nenhum botão. A tela só leva as células ao Mapa K.
   */
  function localAction(eq) {
    const list = Array.isArray(eq?.autopilot?.secondary) ? eq.autopilot.secondary : [];
    const item = list.find(s => s && s.kind === 'local');
    const cells = (Array.isArray(item?.cells) ? item.cells : [])
      .filter(c => c && Number.isInteger(Number(c.row)) && Number.isInteger(Number(c.column)) && finite(c.deltaPercent) !== null)
      .map(c => ({ row: Number(c.row), column: Number(c.column), deltaPercent: Number(c.deltaPercent) }));
    if (!item || !cells.length) return { kind: 'none', label: '', enabled: false, cells: [] };
    return { kind: 'local', label: String(item.label || 'Revisar ajuste local no Mapa K'), enabled: item.enabled !== false, cells };
  }

  /** Lista que a revisão mostra e grava: sempre a do Kotlin naquele instante, com assinatura. */
  function reviewList(eq, kind) {
    if (kind === 'apply') {
      const proposal = eq?.autopilot?.proposal || {};
      const points = pointList(proposal.points);
      return { kind, points, signature: points.length ? 'P:' + String(proposal.signature || signatureOf('', points)) : '' };
    }
    if (kind === 'restore') {
      const points = pointList(eq?.restorePoints);
      return { kind, points, signature: points.length ? signatureOf('R:', points) : '' };
    }
    if (kind === 'undo') {
      const latest = eq?.refinement?.latest;
      const points = latest?.undoable === true ? pointList(latest.undoPoints) : [];
      return { kind, points, signature: points.length ? signatureOf('U:', points) : '' };
    }
    return { kind, points: [], signature: '' };
  }

  /** A ação daquele tipo está liberada agora no piloto (Kotlin)? */
  function actionEnabled(eq, kind) {
    const primary = primaryAction(eq);
    const secondary = secondaryActions(eq);
    if (kind === 'apply') return (primary.kind === 'review' && primary.enabled) || secondary.some(s => s.kind === 'review' && s.enabled);
    if (kind === 'restore') return primary.kind === 'restore' && primary.enabled;
    if (kind === 'undo') return secondary.some(s => s.kind === 'undo' && s.enabled);
    return false;
  }

  /**
   * O cartão aberto só grava a lista que está na tela. Confere elegibilidade, não o alvo exato do motor (que anda
   * com cada leitura nova): mesmos pontos, mesmo valor atual na ECU, nada retido agora e a ação liberada.
   * 'write' grava a lista revisada; 'changed' atualiza e avisa; 'blocked' segura; 'gone' não há o que gravar.
   */
  function reviewGate(displayed, eq) {
    if (!displayed || !displayed.kind) return 'gone';
    const now = reviewList(eq, displayed.kind);
    if (!now.points.length) return 'gone';
    if (!actionEnabled(eq, displayed.kind)) return 'blocked';
    const shown = Array.isArray(displayed.points) ? displayed.points : [];
    const current = new Map(now.points.map(p => [p.index, p.currentRaw]));
    const held = new Set((Array.isArray(eq?.autopilot?.proposal?.held) && displayed.kind === 'apply' ? eq.autopilot.proposal.held : [])
      .map(h => Number(h?.index)));
    const same = shown.length === now.points.length && shown.every(p => current.get(p.index) === p.currentRaw && !held.has(p.index));
    return same ? 'write' : 'changed';
  }

  /** Pontos da ECU com atividade; desconhecido (aquisição não lida, campo indisponível) = "—". */
  // "Gasolina" por extenso (dono, 2026-10-11): "Gas" se lê gás = GNV.
  function ecuPointsText(pilot) {
    const petrol = finite(pilot?.petrolValid);
    const gas = finite(pilot?.gasValid);
    return petrol === null || gas === null ? '—' : `Gasolina ${fmt(petrol, 0)} · GNV ${fmt(gas, 0)}`;
  }

  /** Nossos pontos (faixas densas medidas pelo app); sem a medição (lista ausente) = "—", nunca "0". */
  function ourPointsText(dense) {
    const petrol = Array.isArray(dense?.petrol) ? dense.petrol.length : null;
    const gas = Array.isArray(dense?.gas) ? dense.gas.length : null;
    return petrol === null || gas === null ? '—' : `Gasolina ${fmt(petrol, 0)} · GNV ${fmt(gas, 0)}`;
  }

  /** Resultado por ponto da gravação que a tela mostra (o Kotlin já deu o veredito de cada ponto). */
  function resultRows(result) {
    const points = Array.isArray(result?.points) ? result.points : [];
    return points.map(p => ({
      index: Number(p.index), ms: finite(p.ms), beforeRaw: finite(p.beforeRaw), afterRaw: finite(p.afterRaw),
      verdict: String(p.verdict || ''), outcome: String(p.outcome || 'SEM_MEDICAO'),
      label: OUTCOME[p.outcome] || OUTCOME.SEM_MEDICAO,
    }));
  }

  function resultSummary(result) {
    const rows = resultRows(result);
    const count = name => rows.filter(r => r.outcome === name).length;
    return { improved: count('MELHOROU'), worse: count('PIOROU'), measuring: count('MEDINDO'), none: count('SEM_MEDICAO'), total: rows.length };
  }

  class RefinoScreen {
    constructor(app) {
      this.app = app;
      this.store = app.store;
      this.scheduler = app.scheduler;
      this.native = app.api;
      this.api = ns.AutoCalApi;
      this.eq = null;
      this.snapshot = null;
      this.ticks = 0;
      this.operation = { phase: 'idle' };
      this.review = null;
      this.lastRenderKey = '';
      // Gravar no Mapa K sem sair da tela (core/map-fix.js): o mesmo fluxo para engasgo e para diferença por RPM.
      this.mapFix = root.OmegasMapFix && root.OmegasMapEditor
        ? new root.OmegasMapFix.MapFix({ native: this.native, editor: new root.OmegasMapEditor.MapEditor(), onChange: () => this.render(true), env: { setTimeout: root.setTimeout.bind(root) } })
        : null;
      this.inject();
      this.unsubscribeStatus = this.scheduler.addHook('status', () => {
        if (this.store.get().route !== 'refino') return;
        this.ticks += 1;
        if (this.ticks % DATA_EVERY_TICKS === 0) this.refresh();
      });
      this.unsubscribeFast = this.scheduler.addHook('fast', () => {
        if (this.store.get().route === 'refino') this.paintLive();
      });
      // Ao entrar na aba, desenha na hora (sem esperar o próximo tick).
      let lastRoute = null;
      this.store.subscribe(state => {
        if (state.route === lastRoute) return;
        lastRoute = state.route;
        if (state.route === 'refino') root.setTimeout(() => this.refresh(true), 0);
      }, true);
    }

    inject() {
      const host = document.getElementById('refinoScreenHost');
      if (!host || host.querySelector('.autocal-cockpit')) return;
      // Frase de estado sob o título, o MESMO gráfico do AutoCal (sempre visível) e a barra de botões embaixo, à esquerda.
      // A navegação inferior do app nunca some (decisão do dono): não há botão de abas aqui.
      host.innerHTML = `
        <section class="autocal-cockpit refino-cockpit" aria-label="Refino OMEGAS">
          <header class="autocal-focus-toolbar">
            <div class="autocal-focus-title">
              <div class="autocal-title-line"><h3>Refino · GNV = Gasolina</h3><span id="refinoPhaseChip" class="autocal-fuel-chip" data-fuel-state="unknown">—</span></div>
              <p id="refinoHeadline">Aguardando a ECU</p>
            </div>
          </header>
          <p class="refino-next" id="refinoNext"></p>
          <div id="refinoChart" class="autocal-chart-host refino-chart" aria-label="Gráfico do Refino"><div class="chart-empty">Aguardando a leitura da ECU.</div></div>
          <div class="refino-legend" aria-hidden="true">
            <span class="ao-key"><i class="ln petrol"></i><b>Gasolina (referência)</b></span>
            <span class="ao-key"><i class="sq gas"></i><b>GNV medido por nós</b></span>
            <span class="ao-key"><i class="ln gas dim"></i><b>GNV hoje na ECU</b></span>
            <span class="ao-key"><i class="x">✕</i><b>Apagou</b></span>
            <span class="ao-key"><i class="dm"></i><b>Quase apagou</b></span>
          </div>
          <div class="refino-action-bar" data-refino-actions role="toolbar" aria-label="Ações do refino">
            <button type="button" class="autocal-primary-action" data-kind="review" data-refino-curve hidden>Gravar na Curva K</button>
            <button type="button" class="autocal-primary-action" data-kind="map" data-refino-map hidden>Gravar no Mapa K</button>
            <button type="button" class="secondary autocal-primary-action" data-kind="undo" data-refino-undo hidden>Desfazer</button>
            <button type="button" class="secondary autocal-primary-action" data-kind="cancel" data-refino-cancel-fix hidden>Cancelar</button>
            <button type="button" class="secondary autocal-primary-action" data-kind="ok" data-refino-ok hidden>Entendi</button>
          </div>
          <div id="refinoReview" class="autocal-review" hidden></div>
        </section>`;
      host.addEventListener('click', event => this.onClick(event));
    }

    refresh(force) {
      if (this.operation.phase === 'reading' || this.operation.phase === 'writing') return;
      if (!this.api?.available?.()) { this.renderUnavailable(); return; }
      this.eq = this.api.equivalence?.() || null;
      const projection = this.api.projection?.() || {};
      this.snapshot = projection.ok === true ? (projection.snapshot || {}) : {};
      this.render(force === true);
      this.syncReview();
    }

    onClick(event) {
      const hit = selector => event.target.closest(selector);
      if (hit('[data-refino-ok]')) { this.operation = { phase: 'idle' }; this.mapFix?.dismiss(); this.refresh(true); return; }
      if (hit('[data-refino-cancel]')) { this.closeReview(); return; }
      if (hit('[data-refino-confirm]')) { this.confirm(); return; }
      if (hit('[data-refino-cancel-fix]')) { this.mapFix?.dismiss(); return; }
      if (hit('[data-refino-curve]')) { this.openReview(primaryAction(this.eq).kind === 'restore' ? 'restore' : 'apply'); return; }
      if (hit('[data-refino-map]')) { this.mapAction(); return; }
      if (hit('[data-refino-undo]')) {
        if (this.mapFix?.state.phase === 'done') this.mapFix.undo();
        else this.openReview('undo');
      }
    }

    /** Uma proposta para o Mapa K por vez: o engasgo vem primeiro (segurança); depois a diferença por RPM do piloto. */
    mapProposal() {
      const stalls = this.eq?.stalls;
      const info = describeStalls(stalls);
      if (info.total > 0 && root.OmegasStallRegion) {
        return {
          sub: 'reforço do engasgo',
          plan: ({ axes }) => {
            const suggestion = root.OmegasStallRegion.suggest(stalls, { petrolBins: axes.petrolBins, rpmBins: axes.rpmBins });
            if (!suggestion.ok) return { ok: false, reason: suggestion.reason };
            const region = suggestion.region;
            const ms = v => Number(v).toFixed(1).replace('.', ',');
            return {
              ok: true,
              cells: suggestion.cells.map(c => ({ row: c.row, column: c.column, deltaPercent: suggestion.percent })),
              summary: count => `Reforço do GNV em ${count} ${count === 1 ? 'célula' : 'células'} do Mapa K (+${suggestion.percent}%), perto de ${ms(region.fromMs)}–${ms(region.toMs)} ms. O resto do mapa não muda.`,
            };
          },
        };
      }
      const local = localAction(this.eq);
      if (local.kind === 'local' && local.enabled) {
        return {
          sub: 'diferença por RPM',
          plan: () => ({
            ok: true,
            cells: local.cells,
            summary: count => `Ajuste de ${count} ${count === 1 ? 'célula' : 'células'} do Mapa K onde a diferença é daquela rotação. O resto do mapa não muda.`,
          }),
        };
      }
      return null;
    }

    mapAction() {
      const fix = this.mapFix;
      if (!fix || fix.busy) return;
      if (fix.state.phase === 'ready') { fix.write('apply'); return; }
      const proposal = this.mapProposal();
      if (proposal) fix.start(proposal.plan);
    }

    openReview(kind) {
      const list = reviewList(this.eq, kind);
      if (!list.points.length) return;
      this.review = { ...list, reason: REASONS[kind], title: TITLES[kind], notice: '' };
      this.renderReview();
    }

    /** Lista na tela = lista que será gravada. Mudou no Kotlin? Atualiza o cartão e avisa; sumiu? Diz por quê. */
    syncReview() {
      if (!this.review) return;
      const gate = reviewGate(this.review, this.eq);
      if (gate === 'write') {
        if (this.review.blocked) { this.review = { ...this.review, blocked: false, notice: '' }; this.renderReview(); }
        return;
      }
      if (gate === 'blocked') {
        const action = primaryAction(this.eq);
        const why = action.label && !action.enabled ? action.label : 'a ação não está liberada agora';
        this.review = { ...this.review, blocked: true, notice: `Gravação indisponível agora: ${why}. Nada foi enviado à ECU.` };
      } else if (gate === 'changed') {
        const list = reviewList(this.eq, this.review.kind);
        this.review = { ...this.review, ...list, blocked: false, notice: 'A proposta mudou com leituras novas. Confira a lista atualizada antes de gravar.' };
      } else {
        this.review = { ...this.review, points: [], signature: '', notice: 'Nada para gravar agora: a lista deixou de valer com as leituras novas.' };
      }
      this.renderReview();
    }

    renderReview() {
      const review = document.getElementById('refinoReview');
      const current = this.review;
      if (!review || !current) return;
      const rows = current.points.map(p => {
        const before = p.currentRaw / 16384;
        const after = p.targetRaw / 16384;
        return `<div><dt>${fmt(p.referenceTimeMs, 1)} ms</dt><dd><b>${fmt(before, 3)} → ${fmt(after, 3)}</b> · ${pct(after / before)}${p.origin ? ' · ' + escapeHtml(ORIGIN[p.origin] || p.origin) : ''}</dd></div>`;
      }).join('');
      const notice = current.notice ? `<p class="refino-warning" data-refino-notice>${escapeHtml(current.notice)}</p>` : '';
      const canWrite = current.points.length > 0 && !current.blocked;
      review.hidden = false;
      review.innerHTML = `<div class="autocal-review-card"><header><div><small>REVISÃO ANTES DA ECU</small><h3>${escapeHtml(current.title)} · ${current.points.length} ${plural(current.points.length, 'ponto', 'pontos')} da Curva K</h3></div><button type="button" data-refino-cancel class="icon-close" aria-label="Fechar revisão">×</button></header>${notice}<dl class="refino-review-list">${rows}</dl><div class="write-contract"><b>Nada foi enviado à ECU.</b><span>Ao confirmar, o app relê a curva, confere que nada mudou, grava só estes pontos e confirma por readback. Depois o OMEGAS mede sozinho, ponto por ponto.</span></div><div class="operation-actions"><button type="button" data-refino-cancel class="secondary">Cancelar</button>${canWrite ? '<button type="button" data-refino-confirm class="danger-primary">Gravar na ECU</button>' : ''}</div></div>`;
    }

    closeReview() {
      this.review = null;
      const review = document.getElementById('refinoReview');
      if (review) { review.hidden = true; review.innerHTML = ''; }
    }

    confirm() {
      const pending = this.review;
      if (!pending?.points?.length) return;
      // Confere de novo no toque: se a lista do Kotlin mudou desde que o cartão foi desenhado, não grava.
      const fresh = this.api?.equivalence?.() || this.eq;
      if (fresh) this.eq = fresh;
      if (reviewGate(pending, this.eq) !== 'write') { this.syncReview(); return; }
      this.closeReview();
      this.runWrite(pending.points.map(({ index, currentRaw, targetRaw }) => ({ index, currentRaw, targetRaw })), pending.reason);
    }

    /** Lê a Curva K nesta conexão, confere com o snapshot e só então grava (ACK + readback no Kotlin). */
    runWrite(points, reason) {
      const read = this.native?.startCurveRead?.();
      if (!read?.ok || !read?.started) { this.fail(read?.error || 'A leitura da Curva K não iniciou.'); return; }
      this.operation = { phase: 'reading' };
      this.render(true);
      this.poll(result => {
        const list = Array.isArray(result.points) ? result.points : [];
        if (result.state !== 'COMPLETED' || list.length !== 30) { this.fail(result.error || 'A ECU não devolveu os 30 pontos da Curva K.'); return; }
        const factors = new Map(list.map((p, i) => [Number(p.index ?? i), Number(p.factorRaw)]));
        const stale = points.find(p => factors.get(p.index) !== p.currentRaw);
        if (stale) { this.fail('A Curva K da ECU mudou desde a última leitura. Aguarde a próxima atualização e revise de novo.'); return; }
        const write = this.native.writeCurve(points, reason);
        if (!write?.ok || !write?.started) { this.fail(write?.error || 'A gravação não iniciou.'); return; }
        this.operation = { phase: 'writing', total: points.length };
        this.render(true);
        this.poll(done => {
          if (done.state === 'BATCH_CONFIRMED' && done.readbackValid === true) {
            // Gravado e conferido pela ECU: o piloto já está medindo; a tela volta a ele sem botão extra.
            this.operation = { phase: 'idle' };
            this.refresh(true);
          } else {
            this.fail(done.error || 'O readback não confirmou a gravação. A ECU manteve a curva anterior.');
          }
        });
      });
    }

    poll(onFinish) {
      const started = Date.now();
      const tick = () => {
        const status = this.native.curveOperation() || {};
        if (status.busy === true || /QUEUED|READING|WRITING/.test(String(status.state || ''))) {
          if (Date.now() - started > OPERATION_TIMEOUT_MS) { this.fail('Tempo limite aguardando a ECU.'); return; }
          
          root.setTimeout(tick, POLL_MS);
          return;
        }
        if (status.ok === false || /FAILED|TIMEOUT/.test(String(status.state || ''))) { this.fail(status.error || 'A ECU recusou a operação.'); return; }
        onFinish(status);
      };
      root.setTimeout(tick, POLL_MS);
    }

    fail(message) {
      this.operation = { phase: 'failed', message: String(message || 'Falha') };
      this.render(true);
    }

    renderUnavailable() {
      const headline = document.getElementById('refinoHeadline');
      if (headline) headline.textContent = 'Refino indisponível nesta tela (ponte AutoCal ausente).';
    }

    /** Frase e botões por estado. Nada de botão desabilitado: sem ação possível, só a frase. */
    render(force) {
      const eq = this.eq || {};
      const pilot = eq.autopilot || {};
      const phase = pilot.phase || 'SEM_ECU';
      const op = this.operation;
      const fix = this.mapFix?.state || { phase: 'idle' };
      const result = eq.refinement?.result || null;
      const key = JSON.stringify([phase, pilot.headline, pilot.next, pilot.action, pilot.secondary, pilot.proposal?.signature, pilot.petrolValid, pilot.gasValid,
        eq.ratio, eq.samples, op, fix, eq.denseBands, eq.stalls?.count, eq.stalls?.nearCount, eq.gasEpochAt, eq.gasInvalidation?.at, this.snapshot?.snapshotHash,
        result && [result.id, result.status, (result.points || []).map(p => p.outcome)], eq.refinement?.verifying]);
      if (!force && key === this.lastRenderKey) return;
      this.lastRenderKey = key;

      const chip = document.getElementById('refinoPhaseChip');
      if (chip) { chip.dataset.fuelState = PHASE_TONE[phase] || 'unknown'; chip.textContent = (PHASES.find(([k]) => k === phase) || [null, phase === 'RESTAURAR_TRECHO' ? 'Pontos pioraram' : 'Sem ECU'])[1]; }

      let headline = pilot.headline || 'Conecte a ECU para acompanhar a calibração.';
      let next = (pilot.next || '') + (op.phase === 'idle' && fix.phase === 'idle' ? this.gasNote(eq) : '');
      const show = { curve: false, map: false, undo: false, cancel: false, ok: false };
      const label = {};
      if (op.phase === 'reading' || op.phase === 'writing') {
        headline = op.phase === 'reading' ? 'Conferindo a Curva K da ECU…' : 'Gravando e conferindo na ECU…'; next = 'Não desligue o carro nem tire o cabo.';
      } else if (op.phase === 'failed') {
        headline = op.message; next = 'Nada foi dado como gravado. A lista é refeita com a curva lida na hora.'; show.ok = true; label.ok = 'Tentar de novo';
      } else if (fix.phase === 'reading' || fix.phase === 'writing') {
        headline = fix.message; next = 'Não desligue o carro nem tire o cabo.';
      } else if (fix.phase === 'ready') {
        headline = 'Pronto para gravar no Mapa K'; next = fix.summary; show.map = true; show.cancel = true; label.map = 'Gravar no Mapa K';
      } else if (fix.phase === 'done') {
        headline = 'Gravado e conferido no Mapa K'; next = 'Dirija e veja se o engasgo some. Se piorar, desfaça.'; show.undo = true; show.ok = true; label.ok = 'Pronto';
      } else if (fix.phase === 'undone') {
        headline = 'Desfeito e conferido na ECU'; next = 'O Mapa K voltou ao valor de antes.'; show.ok = true; label.ok = 'Pronto';
      } else if (fix.phase === 'failed') {
        headline = fix.message; next = 'Nada foi dado como gravado. Confira o Mapa K antes de dirigir.'; show.ok = true; label.ok = 'Entendi';
      } else {
        const stallInfo = describeStalls(eq.stalls);
        if (stallInfo.total > 0) next = `${stallInfo.title}. ${stallInfo.text}`.trim();
        if (fix.note) next = fix.note;
        const primary = primaryAction(eq);
        const secondary = secondaryActions(eq);
        const curveReady = (primary.kind === 'review' && primary.enabled) || secondary.some(item => item.kind === 'review' && item.enabled);
        const restore = primary.kind === 'restore' && primary.enabled;
        show.curve = curveReady || restore;
        label.curve = restore && !curveReady ? 'Restaurar na Curva K' : 'Gravar na Curva K';
        show.map = !!this.mapProposal();
        show.undo = secondary.some(item => item.kind === 'undo' && item.enabled);
        label.undo = 'Desfazer última gravação';
      }
      setText('refinoHeadline', headline);
      setText('refinoNext', next);
      const proposal = show.map && fix.phase === 'idle' ? this.mapProposal() : null;
      for (const [name, selector] of [['curve', '[data-refino-curve]'], ['map', '[data-refino-map]'], ['undo', '[data-refino-undo]'], ['cancel', '[data-refino-cancel-fix]'], ['ok', '[data-refino-ok]']]) {
        const node = document.querySelector(selector);
        if (!node) continue;
        node.hidden = !show[name];
        if (label[name] && node.textContent !== label[name] && name !== 'map') node.textContent = label[name];
      }
      const mapNode = document.querySelector('[data-refino-map]');
      if (mapNode && show.map) {
        const html = fix.phase === 'ready' ? 'Gravar no Mapa K' : `Gravar no Mapa K<small>${escapeHtml(proposal?.sub || '')}</small>`;
        if (mapNode.innerHTML !== html) mapNode.innerHTML = html;
      }
      this.renderChart();
    }

    /** Por que nossos pontos de GNV recomeçaram (inteiros ou só onde a gravação mexeu). */
    gasNote(eq) {
      const at = value => (finite(value) ? new Date(value).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) : '');
      const partial = eq.gasInvalidation;
      if (partial && finite(partial.at) && (!finite(eq.gasEpochAt) || partial.at >= eq.gasEpochAt)) {
        const ranges = (Array.isArray(partial.ranges) ? partial.ranges : [])
          .map(r => `${fmt(r[0], 1)}–${finite(r[1]) === null ? 'fim' : fmt(r[1], 1)} ms`).join(', ');
        return ranges ? ` Nossos pontos de GNV em ${ranges} recomeçaram às ${at(partial.at)}: a Curva K foi gravada ali (o resto continua valendo).` : '';
      }
      const reason = { AUTOMATCH_NATIVO: 'a ECU trocou a curva no automático', CURVA_K_GRAVADA: 'a Curva K foi gravada', MAPA_K_GRAVADO: 'o Mapa K foi gravado', CURVA_K_MUDOU_FORA_DO_APP: 'a curva mudou fora do app' }[eq.gasEpochReason];
      return reason ? ` Nossos pontos de GNV recomeçaram às ${at(eq.gasEpochAt)} porque ${reason} (o GNV medido com a curva antiga não vale para a nova).` : '';
    }

    /**
     * O MESMO gráfico do AutoCal (AutoCalOriginal.build/renderMain/renderK), sem o painel de aquisição, com os nossos pontos,
     * a nossa curva e as marcas de engasgo por cima. Como no AutoCal: leitura ruim/zona nova não apaga o desenho (guarda o
     * último bom) e o AGORA anda numa camada leve (paintLive), sem refazer o SVG.
     */
    renderChart() {
      const O = ns.AutoCalOriginal;
      const host = document.getElementById('refinoChart');
      if (!O || !host) return;
      const ux = ns.AutoCalUxModel;
      const live = ux?.livePoint?.(this.store.get().telemetry || {}) || null;
      const fuel = ux?.liveFuelState?.(live?.fuel)?.kind === 'petrol' ? 'petrol' : 'gas';
      const dense = this.eq?.denseBands || {};
      const stalls = this.eq?.stalls || {};
      const extras = { petrol: dense.petrol, gas: dense.gas, stalls: stalls.events, near: stalls.nearEvents };
      let model = O.build(this.snapshot || {}, { fuel, live, extras });
      if (model.ok) this.lastGoodSnapshot = this.snapshot;
      else if (this.lastGoodSnapshot) model = O.build(this.lastGoodSnapshot, { fuel, live, extras });
      if (!model.ok) {
        if (this.chart) return;
        host.innerHTML = '<div class="chart-empty"><b>SEM PONTOS AINDA</b><span>Ligue a ECU e rode na gasolina e no GNV. O gráfico aparece aqui, igual ao do AutoCal.</span></div>';
        return;
      }
      const box = host.getBoundingClientRect?.() || {};
      const width = Math.max(420, Math.round(box.width || 860));
      const height = Math.max(150, Math.round(box.height || 300));
      const mainHeight = Math.round(height * 0.74);
      const kHeight = height - mainHeight - 6;
      const sizes = { sideWidth: 60, mainWidth: width, mainHeight, kHeight };
      const key = JSON.stringify([O.staticModel(model), width, height]);
      if (this.chart && this.chart.host === host && this.chart.key === key && host.isConnected) {
        this.chart.model = model;
        this.paintLive(true);
        return;
      }
      host.innerHTML = '<div class="ao-layout" style="grid-template-columns:' + width + 'px;grid-template-rows:' + mainHeight + 'px ' + kHeight + 'px">' +
        '<div class="ao-cell ao-main">' + O.renderMain(model, width, mainHeight) + '</div>' +
        '<div class="ao-cell ao-k">' + O.renderK(model, width, kHeight) + '</div></div>';
      this.chart = { host, model, sizes, key, painted: {}, telemetry: null };
      this.paintLive(true);
    }

    /** Anda só a camada AO VIVO (faixa de MAP atual + cursor); se o motor passou do fim do eixo, refaz uma vez. */
    paintLive(force) {
      const chart = this.chart;
      const O = ns.AutoCalOriginal;
      if (!chart || !O?.renderLive || !chart.host?.isConnected) return;
      const telemetry = this.store.get().telemetry || {};
      if (!force && chart.telemetry === telemetry) return;
      chart.telemetry = telemetry;
      const live = ns.AutoCalUxModel?.livePoint?.(telemetry) || null;
      const state = live ? O.liveState(chart.model, live) : { band: null, run: null, mapNow: null };
      if (state.run && state.run.x > chart.model.axisX.max + 1e-9 && chart.model.axisX.max < chart.model.fullAxisX.max && !this.growing) {
        this.growing = true;
        this.chart = null;
        this.renderChart();
        this.growing = false;
        return;
      }
      const parts = O.renderLive({ ...chart.model, ...state }, chart.sizes);
      if (!parts) return;
      chart.model = { ...chart.model, ...state };
      for (const name of ['band', 'run', 'k']) {
        if (chart.painted[name] === parts[name]) continue;
        const node = chart.host.querySelector('[data-ao-live="' + name + '"]');
        if (node) { node.innerHTML = parts[name]; chart.painted[name] = parts[name]; }
      }
    }
  }

  function setText(id, value) {
    const node = document.getElementById(id);
    if (node && node.textContent !== String(value ?? '')) node.textContent = String(value ?? '');
  }

  function boot() {
    const app = root.OmegasApp;
    if (!app?.store || !app?.scheduler || !app?.api || !ns.AutoCalApi || !ns.AutoCalUxModel || !ns.AutoCalOriginal) {
      root.setTimeout(boot, 50);
      return;
    }
    if (app.refino) return;
    app.refino = new RefinoScreen(app);
  }

  ns.RefinoModel = { describeStalls, proposedPoints, undoPoints, primaryAction, secondaryActions, localAction, reviewList, reviewGate, actionEnabled, ecuPointsText, ourPointsText, resultRows, resultSummary, REASONS };
  ns.RefinoScreen = RefinoScreen;
  if (typeof document !== 'undefined') boot();
})(typeof window !== 'undefined' ? window : globalThis);
