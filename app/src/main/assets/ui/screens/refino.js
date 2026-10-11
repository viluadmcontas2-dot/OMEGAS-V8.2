(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Refino OMEGAS: o "nosso AutoCal". Mesma linguagem visual do AutoCal da Platina
  // (cabeçalho, gráfico Petrol Inj. × MAP, revisão), com os nossos pontos por cima.
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
  const VERDICT = {
    CONFIRMADA: 'chegou na gasolina',
    PASSOU: 'passou do ponto · próxima mais suave',
    CURTA: 'faltou · próxima mais firme',
    PIOROU: 'piorou',
    COLETANDO: 'medindo…',
    SEM_ANTES: 'sem medição anterior',
    SEM_DADOS: 'sem leituras no prazo',
    INTERROMPIDA: 'outra gravação mexeu aqui',
  };
  /** As três palavras do dono para cada ponto gravado (+ sem medição possível). */
  const OUTCOME = { MELHOROU: 'melhorou', PIOROU: 'piorou', MEDINDO: 'ainda medindo', SEM_MEDICAO: 'sem medição' };
  const HELD_REASON = {
    MEDINDO: 'ainda medindo a última gravação',
    AGUARDA_EVIDENCIA_NOVA: 'restaurado/desfeito: espera leituras novas',
    VALOR_QUE_PIOROU: 'voltaria ao valor que piorou',
    OFERECIDO_PARA_RESTAURAR: 'oferecido para restaurar',
  };
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

  /** O cartão aberto só grava a lista que está na tela; se a do Kotlin mudou, atualiza e avisa antes. */
  function reviewGate(displayed, eq) {
    if (!displayed || !displayed.kind) return 'gone';
    const now = reviewList(eq, displayed.kind);
    if (!now.points.length) return 'gone';
    return now.signature === displayed.signature ? 'write' : 'changed';
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
      this.analysis = null;
      this.snapshot = null;
      this.ticks = 0;
      this.operation = { phase: 'idle' };
      this.review = null;
      this.view = 'chart';
      this.lastRenderKey = '';
      this.inject();
      this.unsubscribeStatus = this.scheduler.addHook('status', () => {
        if (this.store.get().route !== 'refino') return;
        this.ticks += 1;
        if (this.ticks % DATA_EVERY_TICKS === 0) this.refresh();
      });
      this.unsubscribeFast = this.scheduler.addHook('fast', () => {
        if (this.store.get().route === 'refino') this.renderLive();
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
      // Frase de estado sob o título; botões embaixo, à esquerda (barra única, um botão por ação); "Detalhes"
      // troca o gráfico pelas seções de resultado/técnicas no mesmo lugar (sem rolagem). A navegação inferior
      // do app nunca some (decisão do dono): não há botão de abas aqui.
      host.innerHTML = `
        <section class="autocal-cockpit refino-cockpit" aria-label="Refino OMEGAS" data-refino-view="chart">
          <header class="autocal-focus-toolbar">
            <div class="autocal-focus-title">
              <div class="autocal-title-line"><h3>Refino · GNV = Gasolina</h3><span id="refinoPhaseChip" class="autocal-fuel-chip" data-fuel-state="unknown">—</span></div>
              <p id="refinoHeadline">Aguardando a ECU</p>
            </div>
            <div class="autocal-focus-metrics" aria-live="polite">
              <div class="autocal-focus-metric"><small>GNV ÷ Gasolina</small><b><span id="refinoRatio">—</span></b></div>
              <div class="autocal-focus-metric"><small>Pontos da ECU</small><b class="refino-split" id="refinoEcuPoints">—</b></div>
              <div class="autocal-focus-metric"><small>Nossos pontos</small><b class="refino-split" id="refinoOurPoints">—</b></div>
            </div>
          </header>
          <ol class="refino-steps" id="refinoSteps" aria-label="Fases do refino"></ol>
          <p class="refino-next" id="refinoNext"></p>
          <section class="autocal-reference-card" aria-label="NOSSA CURVA · Gasolina × GNV" data-refino-panel="chart">
            <span class="autocal-plot-title">NOSSA CURVA · Gasolina × GNV</span>
            <div class="autocal-chart-workspace">
              <div id="refinoChart" class="autocal-chart-host"><div class="chart-empty">Aguardando a leitura da ECU.</div></div>
              <div class="autocal-chart-legend">
                <span class="petrol">Curva gasolina (ECU)</span>
                <span class="gas">Curva GNV (ECU)</span>
                <span class="acquired">Pontos da ECU</span>
                <span class="refino-ours-petrol">Nossos · gasolina</span>
                <span class="refino-ours-gas">Nossos · GNV</span>
                <span class="refino-stall-legend">Motor apagou</span>
                <span class="live">AGORA</span>
              </div>
              <aside id="refinoInspector" class="autocal-chart-inspector"><b>Toque em um ponto</b><span>Bolinhas: pontos da ECU (azul gasolina, verde GNV). Quadradinhos: nossos pontos (laranja gasolina, roxo GNV), um a cada 0,025 bar.</span></aside>
            </div>
          </section>
          <section class="autocal-reference-card refino-details" aria-label="Detalhes do refino" data-refino-panel="details" hidden>
            <div class="refino-stalls" id="refinoStalls" hidden></div>
            <h4 id="refinoResultTitle">Resultado da última gravação</h4>
            <div id="refinoJournal" class="refino-journal"></div>
            <h4>Detalhes técnicos</h4>
            <div id="refinoTech" class="refino-tech"></div>
          </section>
          <div class="refino-action-bar" data-refino-actions role="toolbar" aria-label="Ações do refino">
            <button type="button" class="autocal-primary-action" data-refino-primary hidden></button>
            <button type="button" class="autocal-primary-action" data-kind="review" data-refino-review hidden>Revisar e gravar</button>
            <button type="button" class="secondary autocal-primary-action" data-kind="undo" data-refino-undo hidden>Desfazer última gravação</button>
            <button type="button" class="secondary autocal-primary-action" data-kind="details" data-refino-details aria-expanded="false">Detalhes</button>
          </div>
          <div id="refinoReview" class="autocal-review" hidden></div>
        </section>`;
      host.addEventListener('click', event => this.onClick(event));
    }

    refresh(force) {
      if (this.operation.phase === 'reading' || this.operation.phase === 'writing') return;
      if (!this.api?.available?.()) { this.renderUnavailable(); return; }
      this.eq = this.api.equivalence?.() || null;
      this.analysis = this.api.refinedAnalysis?.() || null;
      const projection = this.api.projection?.() || {};
      this.snapshot = projection.ok === true ? (projection.snapshot || {}) : {};
      this.render(force === true);
      this.syncReview();
    }

    onClick(event) {
      if (event.target.closest('[data-refino-dismiss]')) { this.operation = { phase: 'idle' }; this.refresh(true); return; }
      if (event.target.closest('[data-refino-primary]')) { this.primary(); return; }
      if (event.target.closest('[data-refino-cancel]')) { this.closeReview(); return; }
      if (event.target.closest('[data-refino-confirm]')) { this.confirm(); return; }
      if (event.target.closest('[data-refino-review]')) { this.openReview('apply'); return; }
      if (event.target.closest('[data-refino-undo]')) { this.openReview('undo'); return; }
      if (event.target.closest('[data-refino-details]')) { this.toggleDetails(); return; }
      const dot = event.target.closest('[data-refino-dot]');
      if (dot) this.inspect(dot.dataset.refinoDot);
    }

    primary() {
      const action = primaryAction(this.eq);
      if (!action.enabled) return;
      if (action.kind === 'review') this.openReview('apply');
      if (action.kind === 'restore') this.openReview('restore');
    }

    toggleDetails() {
      this.view = this.view === 'details' ? 'chart' : 'details';
      this.renderView();
    }

    renderView() {
      const cockpit = document.querySelector('.refino-cockpit');
      if (cockpit) cockpit.dataset.refinoView = this.view;
      document.querySelectorAll('[data-refino-panel]').forEach(panel => { panel.hidden = panel.dataset.refinoPanel !== this.view; });
      const button = document.querySelector('[data-refino-details]');
      if (button) { button.textContent = this.view === 'details' ? 'Voltar ao gráfico' : 'Detalhes'; button.setAttribute('aria-expanded', String(this.view === 'details')); }
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
      if (gate === 'write') return;
      if (gate === 'changed') {
        const list = reviewList(this.eq, this.review.kind);
        this.review = { ...this.review, ...list, notice: 'A proposta mudou com leituras novas. Confira a lista atualizada antes de gravar.' };
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
      const canWrite = current.points.length > 0;
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
          if (this.operation.phase === 'writing') { this.operation.progress = finite(status.progress); this.renderOperation(); }
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

    renderOperation() {
      const button = document.querySelector('[data-refino-primary]');
      const op = this.operation;
      if (!button) return;
      if (op.phase === 'reading' || op.phase === 'writing') {
        button.hidden = false;
        button.disabled = true;
        button.dataset.kind = 'waiting';
        const progress = finite(op.progress);
        button.textContent = op.phase === 'reading' ? 'Conferindo a curva da ECU…' : `Gravando…${progress === null ? '' : ' ' + Math.round(progress) + '%'}`;
      }
    }

    render(force) {
      const eq = this.eq || {};
      const pilot = eq.autopilot || {};
      const phase = pilot.phase || 'SEM_ECU';
      const op = this.operation;
      const result = eq.refinement?.result || null;
      const key = JSON.stringify([phase, pilot.headline, pilot.next, pilot.action, pilot.secondary, pilot.proposal?.signature, pilot.petrolValid, pilot.gasValid,
        eq.ratio, eq.samples, op, eq.denseBands, eq.stalls?.count, eq.gasEpochAt, eq.gasInvalidation?.at, this.snapshot?.snapshotHash,
        result && [result.id, result.status, (result.points || []).map(p => p.outcome)], eq.refinement?.verifying, this.view]);
      if (!force && key === this.lastRenderKey) return;
      this.lastRenderKey = key;

      const chip = document.getElementById('refinoPhaseChip');
      if (chip) { chip.dataset.fuelState = PHASE_TONE[phase] || 'unknown'; chip.textContent = (PHASES.find(([k]) => k === phase) || [null, phase === 'RESTAURAR_TRECHO' ? 'Pontos pioraram' : 'Sem ECU'])[1]; }
      const headline = op.phase === 'failed' ? op.message : pilot.headline || 'Conecte a ECU para acompanhar a calibração.';
      const next = op.phase === 'failed' ? 'Nada foi dado como gravado. Toque em "Tentar de novo" quando a ECU estabilizar: a lista é refeita com a curva lida na hora.' : pilot.next || '';
      setText('refinoHeadline', headline);
      setText('refinoNext', next + (op.phase === 'idle' ? this.gasNote(eq) : ''));
      setText('refinoRatio', pct(eq.ratio));
      setText('refinoEcuPoints', pilot.petrolValid === undefined ? '—' : `Gas ${fmt(pilot.petrolValid, 0)} · GNV ${fmt(pilot.gasValid, 0)}`);
      const dense = eq.denseBands || {};
      const oursPetrol = Array.isArray(dense.petrol) ? dense.petrol.length : 0;
      const oursGas = Array.isArray(dense.gas) ? dense.gas.length : 0;
      setText('refinoOurPoints', `Gas ${oursPetrol} · GNV ${oursGas}`);
      const stalls = eq.stalls || {};
      const stallNode = document.getElementById('refinoStalls');
      if (stallNode) {
        const region = Array.isArray(stalls.regions) ? stalls.regions[0] : null;
        stallNode.hidden = !(finite(stalls.count) > 0);
        stallNode.innerHTML = region ? `<b>O motor apagou ${fmt(stalls.count, 0)} vez${stalls.count === 1 ? '' : 'es'} no GNV</b><span>Mais vezes perto de ${fmt(region.fromMs, 1)}–${fmt(region.toMs, 1)} ms · MAP ${fmt(region.mapBar, 2)} bar (desaceleração/embreagem). O refino nunca empobrece abaixo de ${fmt(this.analysis?.guards?.lowGuardMs ?? 3.5, 1)} ms; se continuar, enriqueça essa região no Ajuste global.</span>` : '';
      }

      const steps = document.getElementById('refinoSteps');
      if (steps) {
        const at = PHASES.findIndex(([k]) => k === (phase === 'RESTAURAR_TRECHO' ? 'VERIFICANDO' : phase));
        steps.innerHTML = PHASES.map(([k, label], i) => `<li data-state="${at < 0 ? 'pending' : i < at ? 'done' : i === at ? 'active' : 'pending'}"${phase === 'RESTAURAR_TRECHO' && k === 'VERIFICANDO' ? ' data-problem="true"' : ''}><i aria-hidden="true">${at >= 0 && i < at ? '✓' : i + 1}</i>${escapeHtml(label)}</li>`).join('');
      }

      const button = document.querySelector('[data-refino-primary]');
      if (button) {
        if (op.phase === 'reading' || op.phase === 'writing') this.renderOperation();
        else if (op.phase === 'failed') { button.hidden = false; button.disabled = false; button.textContent = 'Tentar de novo'; button.dataset.kind = 'retry'; button.dataset.refinoDismiss = ''; }
        else {
          delete button.dataset.refinoDismiss;
          const action = primaryAction(eq);
          button.hidden = action.kind === 'none';
          button.disabled = !action.enabled;
          button.textContent = action.label;
          button.dataset.kind = action.kind;
        }
      }
      const busy = op.phase !== 'idle';
      const secondary = secondaryActions(eq);
      [['review', '[data-refino-review]'], ['undo', '[data-refino-undo]']].forEach(([kind, selector]) => {
        const node = document.querySelector(selector);
        if (!node) return;
        const item = secondary.find(s => s.kind === kind);
        node.hidden = busy || !item;
        node.disabled = !item || !item.enabled;
        if (item) node.textContent = item.label;
      });
      this.renderView();
      this.renderChart();
      this.renderJournal();
      this.renderTech();
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

    renderChart() {
      const host = document.getElementById('refinoChart');
      const model = ns.AutoCalUxModel;
      if (!host || !model) return;
      const snapshot = this.snapshot || {};
      // Curva RV zerada (ECU ainda sem aquisição daquele combustível) não vira linha no zero.
      const reference = model.referencePoints(snapshot, {});
      const hasPetrolRv = reference.some(p => finite(p.petrolMapBar) > 0);
      const hasGasRv = reference.some(p => finite(p.gasMapBar) > 0);
      const acquired = [...model.acquiredPoints(snapshot, 'petrol'), ...model.acquiredPoints(snapshot, 'gas')];
      const dense = this.eq?.denseBands || {};
      const ours = [
        ...(Array.isArray(dense.petrol) ? dense.petrol : []).map(p => ({ ...p, fuel: 'PETROL' })),
        ...(Array.isArray(dense.gas) ? dense.gas : []).map(p => ({ ...p, fuel: 'GAS' })),
      ].filter(p => finite(p.tpetMs) !== null && finite(p.mapBar) !== null);
      this.ours = ours;
      this.acquired = acquired;
      if (!reference.length && !acquired.length && !ours.length) {
        host.innerHTML = '<div class="chart-empty"><b>SEM PONTOS AINDA</b><span>Rode na gasolina e no GNV com a ECU conectada. Os pontos da ECU e os nossos aparecem aqui.</span></div>';
        return;
      }
      // Escala focada onde há pontos medidos (ECU + nossos); a RV só completa o contexto.
      const measuredXs = [...acquired.map(p => p.petrolMs), ...ours.map(p => p.tpetMs)].filter(v => v > 0);
      const xs = measuredXs.length ? measuredXs : reference.map(p => p.petrolMs).filter(v => v > 0);
      const xMin = 0;
      const xMax = Math.max(4, Math.ceil(Math.max(...xs) * 1.25));
      const ys = [
        ...reference.filter(p => p.petrolMs <= xMax).flatMap(p => [hasPetrolRv ? p.petrolMapBar : null, hasGasRv ? p.gasMapBar : null]),
        ...acquired.map(p => p.mapBar), ...ours.map(p => p.mapBar),
      ].filter(v => Number.isFinite(v) && v > 0);
      const yMin = Math.max(0, Math.floor(Math.min(...ys) * 20) / 20 - 0.05);
      const yMax = Math.ceil(Math.max(...ys) * 20) / 20 + 0.05;
      const width = 1000; const height = 400; const padLeft = 64; const padRight = 28; const padTop = 22; const padBottom = 48;
      const xFor = v => padLeft + ((v - xMin) / (xMax - xMin)) * (width - padLeft - padRight);
      const yFor = v => height - padBottom - ((v - yMin) / (yMax - yMin)) * (height - padTop - padBottom);
      const xTicks = Array.from({ length: 6 }, (_, i) => xMin + i * (xMax - xMin) / 5);
      const yTicks = Array.from({ length: 5 }, (_, i) => yMin + i * (yMax - yMin) / 4);
      const grid = yTicks.map(v => `<line class="autocal-grid-line" x1="${padLeft}" y1="${yFor(v).toFixed(1)}" x2="${width - padRight}" y2="${yFor(v).toFixed(1)}"></line><text class="autocal-axis-tick-y" x="${padLeft - 8}" y="${(yFor(v) + 4).toFixed(1)}" text-anchor="end">${v.toFixed(2)}</text>`).join('') +
        xTicks.map(v => `<line class="autocal-grid-line vertical" x1="${xFor(v).toFixed(1)}" y1="${padTop}" x2="${xFor(v).toFixed(1)}" y2="${height - padBottom}"></line><text class="autocal-axis-tick-x" x="${xFor(v).toFixed(1)}" y="${height - 23}" text-anchor="middle">${v.toFixed(1)}</text>`).join('');
      const path = key => reference.filter(p => finite(p[key]) !== null && p[key] > 0 && p[key] >= yMin && p[key] <= yMax && p.petrolMs <= xMax)
        .map((p, i) => `${i ? 'L' : 'M'} ${xFor(p.petrolMs).toFixed(1)} ${yFor(p[key]).toFixed(1)}`).join(' ');
      const acquiredMarkup = acquired.map((p, i) => `<circle class="autocal-acquired-point ${p.fuel === 'GAS' ? 'gas' : 'petrol'} ${p.acquisitionState === 'ACQUIRED' ? 'acquired' : 'collecting'}" data-refino-dot="ecu:${i}" cx="${xFor(p.petrolMs).toFixed(1)}" cy="${yFor(p.mapBar).toFixed(1)}" r="${p.acquisitionState === 'ACQUIRED' ? '6.0' : '4.6'}"></circle>`).join('');
      const oursMarkup = ours.map((p, i) => `<rect class="refino-our-point ${p.fuel === 'GAS' ? 'gas' : 'petrol'}" data-refino-dot="our:${i}" x="${(xFor(p.tpetMs) - 3.5).toFixed(1)}" y="${(yFor(p.mapBar) - 3.5).toFixed(1)}" width="7" height="7" rx="1.5"></rect>`).join('');
      const stallEvents = Array.isArray(this.eq?.stalls?.events) ? this.eq.stalls.events : [];
      const stallMarkup = stallEvents.filter(e => finite(e.petrolMs) !== null && finite(e.mapBar) !== null && e.petrolMs <= xMax && e.mapBar >= yMin && e.mapBar <= yMax)
        .map(e => { const x = xFor(e.petrolMs); const y = yFor(e.mapBar); return `<path class="refino-stall-mark" d="M${(x - 6).toFixed(1)} ${(y - 6).toFixed(1)} L${(x + 6).toFixed(1)} ${(y + 6).toFixed(1)} M${(x + 6).toFixed(1)} ${(y - 6).toFixed(1)} L${(x - 6).toFixed(1)} ${(y + 6).toFixed(1)}"></path>`; }).join('');
      this.chartScale = { xMin, xMax, yMin, yMax, xFor, yFor };
      host.innerHTML = `<svg class="autocal-reference-svg" viewBox="0 0 ${width} ${height}" role="img" aria-label="Nossa curva: Petrol Inj. por MAP, gasolina e GNV, pontos da ECU e nossos">${grid}` +
        `<text class="autocal-axis-title x" x="${((padLeft + width - padRight) / 2).toFixed(1)}" y="${height - 5}" text-anchor="middle">Petrol Inj. (ms)</text>` +
        `<text class="autocal-axis-title y" x="14" y="${height / 2}" text-anchor="middle" transform="rotate(-90 14 ${height / 2})">MAP (bar)</text>` +
        `<g>${hasPetrolRv ? `<path class="autocal-reference-line petrol" d="${path('petrolMapBar')}"></path>` : ''}${hasGasRv ? `<path class="autocal-reference-line gas" d="${path('gasMapBar')}"></path>` : ''}${oursMarkup}${acquiredMarkup}${stallMarkup}<g class="autocal-live-layer" data-refino-live hidden><circle class="autocal-live-halo" r="13"></circle><circle class="autocal-live-point" r="6"></circle><text class="autocal-live-label" text-anchor="start">AGORA</text></g></g></svg>`;
      this.renderLive();
    }

    /** Cursor AGORA (Petrol Inj. × MAP da telemetria ao vivo); só move o marcador, sem redesenhar. */
    renderLive() {
      const layer = document.querySelector('[data-refino-live]');
      const scale = this.chartScale;
      if (!layer || !scale) return;
      const live = ns.AutoCalUxModel?.livePoint?.(this.store.get().telemetry || {});
      if (!live || finite(live.petrolMs) === null || finite(live.mapBar) === null) { layer.setAttribute('hidden', ''); return; }
      const px = Math.min(Math.max(live.petrolMs, scale.xMin), scale.xMax);
      const py = Math.min(Math.max(live.mapBar, scale.yMin), scale.yMax);
      const x = scale.xFor(px).toFixed(1);
      const y = scale.yFor(py).toFixed(1);
      layer.removeAttribute('hidden');
      layer.querySelectorAll('circle').forEach(c => { c.setAttribute('cx', x); c.setAttribute('cy', y); });
      const label = layer.querySelector('text');
      if (label) { label.setAttribute('x', (Number(x) + 12).toFixed(1)); label.setAttribute('y', (Number(y) - 10).toFixed(1)); }
    }

    inspect(token) {
      const [kind, raw] = String(token || '').split(':');
      const index = Number(raw);
      const inspector = document.getElementById('refinoInspector');
      if (!inspector) return;
      if (kind === 'our') {
        const p = (this.ours || [])[index];
        if (!p) return;
        inspector.innerHTML = `<b>Nosso ponto · ${p.fuel === 'GAS' ? 'GNV' : 'Gasolina'}</b><span>MAP ${fmt(p.mapBar, 3)} bar · ${fmt(p.tpetMs, 2)} ms · ${fmt(p.samples, 0)} leituras estáveis · RPM típico ${fmt(p.rpmMedian, 0)}</span>`;
      } else {
        const p = (this.acquired || [])[index];
        if (!p) return;
        inspector.innerHTML = `<b>Ponto da ECU · ${escapeHtml(p.fuelLabel)} B${p.point}</b><span>MAP ${fmt(p.mapBar, 3)} bar · ${fmt(p.petrolMs, 2)} ms · ${p.acquisitionState === 'ACQUIRED' ? 'adquirido' : 'coletando'} (${fmt(p.counter, 0)}/${fmt(p.threshold, 0)})</span>`;
      }
    }

    /**
     * Resultado da gravação, ponto por ponto (vereditos do Kotlin), compacto para caber sem rolagem: cada linha
     * é uma das palavras do dono com os pontos (ms) que estão nela; quem piorou mostra antes → depois.
     */
    renderJournal() {
      const host = document.getElementById('refinoJournal');
      if (!host) return;
      const result = this.eq?.refinement?.result;
      if (!result || !result.status) { host.innerHTML = '<p>Nenhuma gravação feita pelo refino ainda.</p>'; return; }
      const rows = resultRows(result);
      const summary = resultSummary(result);
      const kind = { PROPOSTA: 'proposta do refino', RESTAURAR: 'restauração', DESFAZER: 'desfazer', MANUAL: 'ajuste manual' }[result.kind] || 'gravação';
      const groups = [['PIOROU', 'Pioraram'], ['MEDINDO', 'Ainda medindo'], ['MELHOROU', 'Melhoraram'], ['SEM_MEDICAO', 'Sem medição']]
        .map(([outcome, title]) => {
          const list = rows.filter(r => r.outcome === outcome);
          if (!list.length) return '';
          const items = list.map(r => outcome === 'PIOROU'
            ? `${fmt(r.ms, 1)} ms (${fmt(r.beforeRaw / 16384, 3)} → ${fmt(r.afterRaw / 16384, 3)})`
            : fmt(r.ms, 1)).join(' · ');
          return `<div data-outcome="${outcome}"><dt>${title} (${list.length})</dt><dd>${items}${outcome === 'PIOROU' ? '' : ' ms'}</dd></div>`;
        }).join('');
      const head = `<p class="refino-note">Última ${escapeHtml(kind)}: ${summary.improved} ${plural(summary.improved, 'melhorou', 'melhoraram')} · ${summary.worse} ${plural(summary.worse, 'piorou', 'pioraram')} · ${summary.measuring} ainda medindo${summary.none ? ` · ${summary.none} sem medição` : ''}. Só o resultado de uma proposta ajusta a força da próxima correção; restaurar e desfazer não ensinam.</p>`;
      host.innerHTML = `${head}${groups ? `<div class="refino-tech refino-result-points"><dl>${groups}</dl></div>` : '<p>Dirija no GNV: o app compara cada faixa com a gasolina no mesmo RPM e MAP.</p>'}`;
    }

    renderTech() {
      const host = document.getElementById('refinoTech');
      if (!host) return;
      const a = this.analysis || {};
      const pilot = this.eq?.autopilot || {};
      const proposal = pilot.proposal || {};
      const held = Array.isArray(proposal.held) ? proposal.held : [];
      const heldText = held.length ? held.map(h => `${fmt(h.referenceTimeMs, 1)} ms (${escapeHtml(HELD_REASON[h.reason] || h.reason)})`).join(', ') : 'nenhum';
      host.innerHTML = `<dl>
        <div><dt>Modo</dt><dd>${escapeHtml(a.refinementMode || '—')} · ${a.available ? 'disponível' : escapeHtml(a.message || 'aguardando evidência')}</dd></div>
        <div><dt>Proposta</dt><dd>${fmt(proposal.count ?? 0, 0)} ${plural(proposal.count ?? 0, 'ponto', 'pontos')} · motor mudaria ${proposedPoints(a).length} · fora agora: ${heldText}</dd></div>
        <div><dt>Bandas comuns maduras</dt><dd>${fmt(a.matureCommonPoints, 0)} de ${fmt(a.minimumMatureCommonPoints, 0)} necessárias · ${fmt(a.telemetryTargets, 0)} alvos dos nossos pontos</dd></div>
        <div><dt>Trava</dt><dd>±${fmt(a.guards?.maximumStepPercent, 0)}% por gravação · |Δ ln K/Δ ln t| ≤ ${fmt(a.elasticityLimit, 2)}</dd></div>
        <div><dt>Automático da ECU</dt><dd>${fmt(pilot.autoMatchCount, 0)} de ${fmt(pilot.maxAutomatch, 0)} · ${escapeHtml(pilot.ecuDoneReason || 'ainda trabalhando')}</dd></div>
      </dl>`;
    }
  }

  function setText(id, value) {
    const node = document.getElementById(id);
    if (node && node.textContent !== String(value ?? '')) node.textContent = String(value ?? '');
  }

  function boot() {
    const app = root.OmegasApp;
    if (!app?.store || !app?.scheduler || !app?.api || !ns.AutoCalApi || !ns.AutoCalUxModel) {
      root.setTimeout(boot, 50);
      return;
    }
    if (app.refino) return;
    app.refino = new RefinoScreen(app);
  }

  ns.RefinoModel = { proposedPoints, undoPoints, primaryAction, secondaryActions, reviewList, reviewGate, resultRows, resultSummary, REASONS };
  ns.RefinoScreen = RefinoScreen;
  if (typeof document !== 'undefined') boot();
})(typeof window !== 'undefined' ? window : globalThis);
