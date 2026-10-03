(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Refino OMEGAS: o "nosso AutoCal". Mesma linguagem visual do AutoCal da Platina
  // (cabeçalho, gráfico Petrol Inj. × MAP, revisão), com os nossos pontos por cima.
  // A matemática é do Kotlin (AutoMatchRefinedEngine, EquivalenceLedger, diário e piloto).
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
    SEM_ECU: 'unknown', LENDO_ECU: 'unknown', TENTATIVA_ENCERRADA: 'problem', ECU_TRABALHANDO: 'unknown', COLETANDO_NOSSOS: 'collecting',
    PROPOSTA_PRONTA: 'ready', VERIFICANDO: 'collecting', RESTAURAR_TRECHO: 'problem', ESTAVEL: 'ok',
  };
  const VERDICT = {
    CONFIRMADA: 'chegou na gasolina',
    PASSOU: 'passou do ponto · próxima mais suave',
    CURTA: 'faltou · próxima mais firme',
    PIOROU: 'piorou',
    COLETANDO: 'medindo…',
    SEM_ANTES: 'sem medição anterior',
    SEM_DADOS: 'poucas leituras · não deu para julgar',
  };
  // O que cada fechamento do diário significa, em linguagem simples.
  const JOURNAL_NOTE = {
    SEM_BASE: 'As faixas alteradas não tinham medição de antes. A medição de agora vira a base e o refino segue sozinho.',
    INCONCLUSIVO: 'Poucas leituras nas faixas alteradas: não deu para julgar. O refino segue medindo do zero.',
    INTERROMPIDO: 'A ECU mudou a curva por fora (AutoMatch/Mapa K) durante a verificação, então o resultado perdeu validade.',
    FALHA_PARCIAL: 'A gravação falhou no meio e a ECU pode ter sido alterada em parte. Toque em Desfazer última gravação para voltar à foto de antes.',
  };
  const ORIGIN = { MEASURED: 'Medido', BLENDED: 'Transição', SMOOTHED: 'Anti-tranco', HELD: 'Mantido' };
  const POLL_MS = 300;
  const OPERATION_TIMEOUT_MS = 90000;
  const DATA_EVERY_TICKS = 2;

  function finite(value) {
    if (value === null || value === undefined || value === '') return null;
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }
  /** Mínimo/máximo que nunca devolvem ±Infinity para lista vazia (gráfico sem NaN). */
  function safeMin(list, fallback) {
    let best = Infinity;
    for (const v of list) if (Number.isFinite(v) && v < best) best = v;
    return Number.isFinite(best) ? best : fallback;
  }
  function safeMax(list, fallback) {
    let best = -Infinity;
    for (const v of list) if (Number.isFinite(v) && v > best) best = v;
    return Number.isFinite(best) ? best : fallback;
  }
  function failureText(operation, fallback) {
    const rules = ns.DisplayRules;
    return rules && typeof rules.failureText === 'function' ? rules.failureText(operation, fallback) : String((operation && operation.error) || fallback || '');
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

  /** "agora", "há 12 s", "há 3 min", "há 2 h". Sem data conhecida: "—" (nunca "há 0 s"). */
  function ageText(atMs, nowMs) {
    const at = finite(atMs);
    const now = finite(nowMs);
    if (at === null || at <= 0 || now === null) return '—';
    const s = Math.max(0, Math.round((now - at) / 1000));
    if (s < 5) return 'agora';
    if (s < 90) return `há ${s} s`;
    if (s < 5400) return `há ${Math.round(s / 60)} min`;
    return `há ${Math.round(s / 3600)} h`;
  }

  /** Resultado do experimento em linguagem simples (histórico). */
  const STATUS_WORDS = {
    VERIFICADO: 'Chegou na gasolina',
    PIOROU_EM_PARTE: 'Piorou em parte em alguma faixa',
    FALHA_PARCIAL: 'A gravação falhou no meio: a ECU pode ter sido alterada em parte',
    VERIFICANDO: 'Medindo…',
    SEM_BASE: 'Sem medição anterior: a medição de agora virou a base',
    INCONCLUSIVO: 'Poucas leituras: não deu para julgar',
    INTERROMPIDO: 'A ECU mudou a curva no meio (AutoMatch ou Mapa K)',
  };

  /**
   * O que o ponto tocado é: de quem, quando foi medido e por que conta (ou não) para a curva.
   * kind = 'our' (dos pontos densos do OMEGAS) ou 'ecu' (ponto adquirido pelo AutoCal nativo).
   */
  function explainPoint(kind, point, ctx) {
    const now = ctx?.now;
    if (kind === 'our') {
      const gas = point.fuel === 'GAS';
      const idle = (finite(point.idleShare) ?? 0) >= 0.5 || (finite(point.rpmMedian) ?? Infinity) < 1000;
      const lines = [
        'De quem é: medido pelo OMEGAS na sua condução (leitura estável = 3 quadros seguidos).',
        `MAP ${fmt(point.mapBar, 3)} bar · ${fmt(point.tpetMs, 2)} ms · ${fmt(point.samples, 0)} leituras · RPM típico ${fmt(point.rpmMedian, 0)}`,
        `Quando: última leitura ${ageText(point.lastAtMs, now)}.`,
      ];
      lines.push(idle
        ? 'Por que conta: NÃO conta. É marcha lenta: aparece no gráfico, mas não corrige a curva (a ECU trata a lenta à parte).'
        : gas
          ? 'Por que conta: forma par com a gasolina no mesmo RPM e MAP. Só vale para a curva atual: se a curva mudar, recomeça.'
          : 'Por que conta: é a referência da gasolina. O GNV é comparado com ela no mesmo RPM e MAP.');
      return { title: `Nosso ponto · ${gas ? 'GNV' : 'Gasolina'}`, lines, counts: !idle };
    }
    const gas = point.fuel === 'GAS';
    const rejected = (ctx?.rejected || []).some(r => (r.fuel === 'GNV') === gas && Number(r.band) === Number(point.index));
    const acquired = point.acquisitionState === 'ACQUIRED';
    const progress = finite(point.counter) === null ? '' : ` (${fmt(point.counter, 0)}${finite(point.threshold) === null ? '' : '/' + fmt(point.threshold, 0)})`;
    const lines = [
      'De quem é: medido pela ECU (AutoCal nativo), não pelo OMEGAS.',
      `MAP ${fmt(point.mapBar, 3)} bar · ${fmt(point.petrolMs, 2)} ms · ${acquired ? 'adquirido' : 'coletando'}${progress}`,
      `Quando: leitura da ECU ${ageText(ctx?.capturedAtMs, now)}.`,
    ];
    if (rejected) lines.push('Por que conta: NÃO conta. Foi descartado como anomalia: fora da tendência das outras faixas (típico de marcha lenta puxando a curva).');
    else if (acquired) lines.push('Por que conta: faixa adquirida pela ECU; entra no cálculo junto com os nossos pontos.');
    else lines.push('Por que conta: ainda coletando; só conta quando a ECU terminar de adquirir esta faixa.');
    return { title: `Ponto da ECU · ${point.fuelLabel} B${point.point}`, lines, counts: acquired && !rejected };
  }

  /** Pontos que a revisão propõe gravar (puro, testável). */
  function proposedPoints(analysis) {
    const points = Array.isArray(analysis?.points) ? analysis.points : [];
    return points
      .filter(p => p && p.origin !== 'HELD' && finite(p.calculatedRaw) !== null && Number(p.calculatedRaw) !== Number(p.currentRaw))
      .map(p => ({ index: Number(p.index), currentRaw: Number(p.currentRaw), targetRaw: Number(p.calculatedRaw) }));
  }

  /** Desfazer a última gravação registrada no diário (antes ← depois). */
  function undoPoints(latest) {
    const before = latest?.beforeRaw;
    const after = latest?.afterRaw;
    if (!Array.isArray(before) || !Array.isArray(after) || before.length !== 30 || after.length !== 30) return [];
    const out = [];
    for (let i = 0; i < 30; i += 1) if (Number(before[i]) !== Number(after[i])) out.push({ index: i, currentRaw: Number(after[i]), targetRaw: Number(before[i]) });
    return out;
  }

  /** O Desfazer existe quando o diário guarda a foto de antes ou o antes/depois do último experimento. */
  function undoSource(latest) {
    const photo = String(latest?.photoFile || '');
    return { photoFile: photo, available: Boolean(photo) || undoPoints(latest).length > 0 };
  }

  /**
   * Fase e proposta precisam concordar: nunca "Revise e grave" sem o botão, nunca "ainda medindo" com o botão
   * "Revisar e gravar". O Kotlin decide a fase pelo livro; a proposta vem do motor. Aqui o texto segue o botão.
   */
  function agreedTexts(eq, analysis) {
    const pilot = eq?.autopilot || {};
    const phase = pilot.phase === 'TENTATIVA_ENCERRADA' ? String(pilot.expiredFrom || '') : String(pilot.phase || '');
    const proposal = proposedPoints(analysis).length;
    if (phase === 'PROPOSTA_PRONTA' && !(proposal && analysis?.available)) {
      return {
        headline: 'As faixas estão fora da gasolina, mas ainda não há uma correção segura para propor.',
        next: 'Continue rodando no GNV: o app propõe a correção quando a evidência bastar.',
      };
    }
    if (pilot.phase === 'COLETANDO_NOSSOS' && proposal && analysis?.available) {
      return {
        headline: `Há uma correção proposta para ${proposal} ponto${proposal === 1 ? '' : 's'}. Toque em Revisar e gravar.`,
        next: 'Se continuar rodando no GNV, a proposta fica mais precisa.',
      };
    }
    return null;
  }

  const ROUTE_NAMES = { map: 'Mapa K', curve: 'Curva K', autocal: 'AutoCal', refino: 'Refino', sessions: 'Sessões', tools: 'Ferramentas' };
  /** Índice de equivalência: fração 0..1 (percentual = ×100). Sem número válido: null (nunca 0 %). */
  function indexPercent(eq) {
    const value = finite(eq?.index?.value);
    return value === null ? null : Math.round(Math.max(0, Math.min(1, value)) * 100);
  }
  /**
   * Faixa discreta do Refino (D1): "GNV ≈ gasolina em N %" + UMA próxima ação + o botão de um toque quando a ação
   * aponta para outra aba. Só mostra e leva; nunca executa. Sem dado nenhum: null (faixa oculta).
   */
  function equivalenceStrip(eq, routes) {
    const percent = indexPercent(eq);
    const action = eq?.nextAction || null;
    const text = action && action.text ? String(action.text) : '';
    if (percent === null && !text) return null;
    const route = action && action.route && action.route !== 'refino' && (!routes || routes.includes(action.route)) ? String(action.route) : '';
    return {
      indexText: percent === null ? 'GNV ≈ gasolina em —' : `GNV ≈ gasolina em ${percent} %${eq.index.provisional === true ? ' (provisório)' : ''}`,
      nextText: text || 'Nada a fazer agora.',
      route,
      subpage: route ? String(action.subpage || '') : '',
      routeLabel: route ? `Ir para ${ROUTE_NAMES[route] || route}` : '',
    };
  }

  /** Ação principal conforme a fase do piloto (uma só por vez). */
  function primaryAction(eq, analysis) {
    const phase = eq?.autopilot?.phase || 'SEM_ECU';
    const proposal = proposedPoints(analysis).length;
    if (phase === 'TENTATIVA_ENCERRADA') {
      // O prazo da tentativa pausa o acompanhamento, não a proposta: ela continua válida e o botão continua.
      const from = eq?.autopilot?.expiredFrom;
      if ((from === 'PROPOSTA_PRONTA' || from === 'ECU_TRABALHANDO') && proposal && analysis?.available) {
        return { kind: 'review', label: `Revisar e gravar ${proposal} ponto${proposal === 1 ? '' : 's'}`, expired: true };
      }
      return { kind: 'none', label: '' };
    }
    const restore = Array.isArray(eq?.restorePoints) ? eq.restorePoints.length : 0;
    if (phase === 'RESTAURAR_TRECHO' && restore) return { kind: 'restore', label: `Restaurar trecho que piorou (${restore} ponto${restore === 1 ? '' : 's'})` };
    if (phase === 'ESTAVEL') return { kind: 'stable', label: '✓ Estável · pode desconectar' };
    // Enquanto a ECU faz o automático ela pode sobrescrever qualquer curva: o refino calcula e
    // mostra, mas a gravação só libera quando a ECU terminar.
    if (phase === 'SEM_ECU' || phase === 'LENDO_ECU' || phase === 'ECU_TRABALHANDO') {
      const p = eq?.autopilot || {};
      const progress = finite(p.autoMatchCount) !== null ? ` (${p.autoMatchCount}${finite(p.maxAutomatch) !== null ? ' de ' + p.maxAutomatch : ''})` : '';
      return proposal ? { kind: 'waiting', label: `Aguardando a ECU terminar o automático${progress}` } : { kind: 'none', label: '' };
    }
    if (phase === 'VERIFICANDO') return { kind: 'waiting', label: 'Medindo a última gravação…' };
    if (proposal && analysis?.available) {
      return { kind: 'review', label: `Revisar e gravar ${proposal} ponto${proposal === 1 ? '' : 's'}` };
    }
    return { kind: 'none', label: '' };
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
      this.reviewPoints = null;
      // A foto de antes de cada gravação vive no diário (photoFile do ÚLTIMO experimento): o Desfazer restaura
      // exatamente a do último, mesmo que o anterior tenha sido gravado por outra aba.
      this.lastRenderKey = '';
      this.inject();
      this.unsubscribeStatus = this.scheduler.addHook('status', () => {
        if (this.store.get().route !== 'refino') return;
        this.ticks += 1;
        if (this.ticks % DATA_EVERY_TICKS === 0) this.refresh();
      });
      this.unsubscribeFast = this.scheduler.addHook('fast', () => {
        this.tickJob();
        if (this.store.get().route !== 'refino') return;
        if (this.enterRefreshPending) { this.enterRefreshPending = false; this.refresh(true); }
        this.renderLive();
      });
      // Ao entrar na aba, desenha na hora (sem esperar o próximo tick).
      let lastRoute = null;
      this.store.subscribe(state => {
        if (state.route === lastRoute) return;
        lastRoute = state.route;
        if (state.route === 'refino') this.enterRefreshPending = true;
      }, true);
    }

    inject() {
      const host = document.getElementById('refinoScreenHost');
      if (!host || host.querySelector('.autocal-cockpit')) return;
      host.innerHTML = `
        <section class="autocal-cockpit refino-cockpit" aria-label="Refino OMEGAS">
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
            <div class="autocal-focus-actions"><button type="button" class="autocal-primary-action" data-refino-primary hidden></button></div>
          </header>
          <ol class="refino-steps" id="refinoSteps" aria-label="Fases do refino"></ol>
          <p class="refino-next" id="refinoNext"></p>
          <div class="refino-eq" id="refinoEq" aria-label="Equivalência com a gasolina" hidden><b id="refinoEqIndex">—</b><span id="refinoEqNext"></span><button type="button" class="secondary" id="refinoEqGo" data-refino-go hidden></button></div>
          <div class="refino-undo" id="refinoUndo" hidden></div>
          <div class="refino-stalls" id="refinoStalls" hidden></div>
          <section class="autocal-reference-card" aria-label="NOSSA CURVA · Gasolina × GNV">
            <span class="autocal-plot-title">NOSSA CURVA · Gasolina × GNV</span>
            <div class="autocal-chart-legend" id="refinoLegend" aria-label="Legenda do gráfico">
              <span class="petrol">Curva gasolina (ECU)</span>
              <span class="gas">Curva GNV (ECU)</span>
              <span class="acquired">Pontos da ECU</span>
              <span class="refino-ours-petrol">Nossos · gasolina</span>
              <span class="refino-ours-gas">Nossos · GNV</span>
              <span class="refino-stall-legend">Motor apagou</span>
              <span class="live">AGORA</span>
            </div>
            <div class="autocal-chart-workspace">
              <div id="refinoChart" class="autocal-chart-host"><div class="chart-empty">Aguardando a leitura da ECU.</div></div>
              <aside id="refinoInspector" class="autocal-chart-inspector"><b>Toque em um ponto</b><span>Bolinhas: pontos da ECU (azul gasolina, verde GNV). Quadradinhos: nossos pontos (laranja gasolina, roxo GNV), um a cada 0,025 bar. Ao tocar eu digo de quem é, quando foi medido e se conta para a curva.</span></aside>
            </div>
          </section>
          <details class="autocal-secondary-details" id="refinoResultDetails">
            <summary>Resultado da última gravação</summary>
            <div id="refinoJournal" class="refino-journal"></div>
          </details>
          <details class="autocal-secondary-details">
            <summary>Detalhes técnicos</summary>
            <div id="refinoTech" class="refino-tech"></div>
          </details>
          <div id="refinoReview" class="autocal-review" hidden></div>
        </section>`;
      host.addEventListener('click', event => this.onClick(event));
    }

    refresh(force, fresh) {
      if (this.operation.phase === 'reading' || this.operation.phase === 'writing') return;
      if (!this.api?.available?.()) { this.renderUnavailable(); return; }
      // O Kotlin já deixa o resultado pronto em segundo plano; "fresco" só depois de gravar/desfazer.
      this.eq = (fresh === true ? this.api.equivalenceFresh?.() : this.api.equivalence?.()) || null;
      this.analysis = this.api.refinedAnalysis?.() || null;
      const projection = this.api.projection?.() || {};
      this.snapshot = projection.ok === true ? (projection.snapshot || {}) : {};
      this.render(force === true);
    }

    onClick(event) {
      const go = event.target.closest('[data-refino-go]');
      if (go) { if (go.dataset.route) this.app.router?.open(go.dataset.route, go.dataset.subpage || ''); return; }
      if (event.target.closest('[data-refino-dismiss]')) { this.operation = { phase: 'idle' }; this.refresh(true, true); return; }
      if (event.target.closest('[data-refino-primary]')) this.primary();
      if (event.target.closest('[data-refino-cancel]')) this.closeReview();
      if (event.target.closest('[data-refino-confirm]')) this.commitReview();
      if (event.target.closest('[data-refino-undo]')) this.openUndo();
      const dot = event.target.closest('[data-refino-dot]');
      if (dot) this.inspect(dot.dataset.refinoDot);
    }

    primary() {
      const action = primaryAction(this.eq, this.analysis);
      if (action.kind === 'review') this.openReview('apply');
      if (action.kind === 'restore') this.openReview('restore');
    }

    /** Foto do último experimento do diário; durante uma falha parcial, a que o Kotlin acabou de informar. */
    undoFile() {
      const latest = this.eq?.refinement?.latest;
      return String(latest?.photoFile || this.operation?.photoFile || '');
    }

    openReview(kind) {
      let points = [];
      let title = '';
      let reason = '';
      if (kind === 'apply') { points = proposedPoints(this.analysis); title = 'Gravar curva refinada'; reason = 'Refino OMEGAS: curva refinada confirmada'; }
      if (kind === 'restore') { points = (this.eq?.restorePoints || []).map(p => ({ index: Number(p.index), currentRaw: Number(p.currentRaw), targetRaw: Number(p.targetRaw) })); title = 'Restaurar trecho que piorou'; reason = 'Refino OMEGAS: restaurar trecho que piorou'; }
      if (kind === 'undo') { points = undoPoints(this.eq?.refinement?.latest); title = 'Desfazer última gravação'; reason = 'Refino OMEGAS: desfazer última gravação'; }
      const pilot = this.eq?.autopilot || {};
      // Prazo vencido no automático da ECU continua pedindo o aviso de revisão (a ECU ainda pode sobrescrever).
      const phase = pilot.phase === 'TENTATIVA_ENCERRADA' ? pilot.expiredFrom : pilot.phase;
      this.showReview(points, title, reason, '', kind === 'apply' ? phase : '');
    }

    /**
     * Desfazer: com a foto desta sessão, a prévia vem dela (o Kotlin relê a curva e lista o que volta);
     * sem foto (app reaberto), cai no diário (antes ← depois). Em ambos o dono confirma com um toque.
     */
    openUndo() {
      const file = this.undoFile();
      if (!file || typeof this.native?.prepareCurveRestore !== 'function') { this.openReview('undo'); return; }
      const prep = this.native.prepareCurveRestore(file);
      if (!prep?.ok || !prep?.started) { this.fail(failureText(prep, 'Não foi possível preparar o Desfazer.')); return; }
      this.operation = { phase: 'reading' };
      this.render(true);
      this.poll(result => {
        this.operation = { phase: 'idle' };
        const points = (Array.isArray(result.points) ? result.points : [])
          .map(p => ({ index: Number(p.index), currentRaw: Number(p.currentRaw), targetRaw: Number(p.targetRaw) }));
        if (!points.length) { this.fail('Nada a desfazer: a ECU já está igual à foto de antes da gravação.'); return; }
        this.render(true);
        this.showReview(points, 'Desfazer última gravação', 'Refino OMEGAS: desfazer última gravação', file);
      });
    }

    showReview(points, title, reason, restoreFile, phase) {
      if (!points.length) return;
      this.reviewPoints = { points, reason, restoreFile: restoreFile || '' };
      const byIndex = new Map((this.analysis?.points || []).map(p => [Number(p.index), p]));
      const rows = points.map(p => {
        const ref = byIndex.get(p.index) || {};
        const before = p.currentRaw / 16384;
        const after = p.targetRaw / 16384;
        return `<div><dt>${fmt(ref.referenceTimeMs, 1)} ms</dt><dd><b>${fmt(before, 3)} → ${fmt(after, 3)}</b> · ${pct(after / before)}${ref.origin ? ' · ' + escapeHtml(ORIGIN[ref.origin] || ref.origin) : ''}</dd></div>`;
      }).join('');
      const early = phase === 'ECU_TRABALHANDO'
        ? '<p class="refino-warning">A ECU ainda está no automático e pode sobrescrever esta curva. O ideal é gravar quando ela terminar — o app avisa.</p>' : '';
      const review = document.getElementById('refinoReview');
      if (!review) return;
      review.hidden = false;
      review.innerHTML = `<div class="autocal-review-card"><header><div><small>REVISÃO ANTES DA ECU</small><h3>${escapeHtml(title)} · ${points.length} ponto${points.length === 1 ? '' : 's'} da Curva K</h3></div><button type="button" data-refino-cancel class="icon-close" aria-label="Fechar revisão">×</button></header>${early}<dl class="refino-review-list">${rows}</dl><div class="write-contract"><b>Nada foi enviado à ECU.</b><span>Ao confirmar, o app relê a curva, confere que nada mudou, grava e confirma por readback. A curva anterior fica no diário para desfazer.</span></div><div class="operation-actions"><button type="button" data-refino-cancel class="secondary">Cancelar</button><button type="button" data-refino-confirm class="danger-primary">Gravar na ECU</button></div></div>`;
    }

    closeReview() {
      this.reviewPoints = null;
      const review = document.getElementById('refinoReview');
      if (review) { review.hidden = true; review.innerHTML = ''; }
    }

    commitReview() {
      const pending = this.reviewPoints;
      this.closeReview();
      if (!pending?.points?.length) return;
      this.runWrite(pending.points, pending.reason, pending.restoreFile || '');
    }

    /** Lê a Curva K nesta conexão, confere com o snapshot e só então grava (ACK + readback no Kotlin). */
    runWrite(points, reason, restoreFile) {
      const read = this.native?.startCurveRead?.();
      if (!read?.ok || !read?.started) { this.fail(read?.error || 'A leitura da Curva K não iniciou.'); return; }
      this.operation = { phase: 'reading' };
      this.render(true);
      this.poll(result => {
        const list = Array.isArray(result.points) ? result.points : [];
        if (result.state !== 'COMPLETED' || list.length !== 30) { this.fail(failureText(result, 'A ECU não devolveu os 30 pontos da Curva K.')); return; }
        const factors = new Map(list.map((p, i) => [Number(p.index ?? i), Number(p.factorRaw)]));
        const stale = points.find(p => factors.get(p.index) !== p.currentRaw);
        if (stale) { this.fail('A Curva K da ECU mudou desde a última leitura. Aguarde a próxima atualização e revise de novo.'); return; }
        const write = restoreFile && typeof this.native.restoreCurve === 'function'
          ? this.native.restoreCurve(points, restoreFile)
          : this.native.writeCurve(points, reason);
        if (!write?.ok || !write?.started) { this.fail(failureText(write, 'A gravação não iniciou.')); return; }
        this.operation = { phase: 'writing', total: points.length };
        this.render(true);
        this.poll(done => {
          if (done.state === 'BATCH_CONFIRMED' && done.readbackValid === true) {
            // O diário já guarda a foto de antes DESTA gravação; o próximo Desfazer a usa.
            this.operation = { phase: 'done' };
            this.render(true);
          } else {
            this.fail(failureText(done, 'O readback não confirmou a gravação. A ECU manteve a curva anterior.'));
          }
        });
      });
    }

    /** Acompanha a operação da ECU pelo gancho 'fast' do scheduler (sem timer próprio). */
    poll(onFinish) {
      this.job = { started: Date.now(), lastAt: 0, onFinish };
    }

    tickJob() {
      const job = this.job;
      if (!job) return;
      const now = Date.now();
      if (now - job.lastAt < POLL_MS) return;
      job.lastAt = now;
      const status = this.native.curveOperation() || {};
      if (status.busy === true || /QUEUED|READING|WRITING/.test(String(status.state || ''))) {
        if (now - job.started > OPERATION_TIMEOUT_MS) { this.job = null; this.fail('Tempo limite aguardando a ECU.'); return; }
        if (this.operation.phase === 'writing') { this.operation.progress = finite(status.progress); this.renderOperation(); }
        return;
      }
      this.job = null;
      if (status.ok === false || /FAILED|TIMEOUT/.test(String(status.state || ''))) {
        // Falha com a ECU possivelmente alterada: o Desfazer continua disponível, com a foto de antes.
        const mayHaveChanged = status.partial === true || status.mutationMayHaveStarted === true;
        const text = failureText(status, 'A ECU recusou a operação.');
        this.fail(mayHaveChanged ? `A ECU pode ter sido alterada em parte. ${text}` : text, mayHaveChanged ? { partial: true, photoFile: String(status.photoFile || '') } : undefined);
        return;
      }
      job.onFinish(status);
    }

    fail(message, extra) {
      this.operation = { phase: 'failed', message: String(message || 'Falha'), ...(extra || {}) };
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
        const progress = finite(op.progress);
        button.textContent = op.phase === 'reading' ? 'Conferindo a curva da ECU…' : `Gravando…${progress === null ? '' : ' ' + Math.round(progress) + '%'}`;
      }
    }

    render(force) {
      const eq = this.eq || {};
      const pilot = eq.autopilot || {};
      const phase = pilot.phase || 'SEM_ECU';
      const op = this.operation;
      const key = JSON.stringify([phase, pilot.expiredFrom, eq.refinement?.latest?.photoFile, pilot.headline, pilot.next, pilot.petrolValid, pilot.gasValid, eq.ratio, eq.samples, op, eq.denseBands, eq.stalls?.count, eq.gasEpochAt, (this.analysis?.points || []).map(p => p.calculatedRaw), this.snapshot?.snapshotHash, eq.refinement?.latest?.status]);
      if (!force && key === this.lastRenderKey) return;
      this.lastRenderKey = key;

      const chip = document.getElementById('refinoPhaseChip');
      if (chip) { chip.dataset.fuelState = (phase === 'TENTATIVA_ENCERRADA' && pilot.expiredFrom === 'PROPOSTA_PRONTA' ? 'ready' : PHASE_TONE[phase]) || 'unknown'; chip.textContent = (PHASES.find(([k]) => k === phase) || [null, phase === 'RESTAURAR_TRECHO' ? 'Trecho piorou' : phase === 'LENDO_ECU' ? 'Lendo a ECU' : phase === 'TENTATIVA_ENCERRADA' ? (pilot.expiredFrom === 'PROPOSTA_PRONTA' ? 'Proposta válida' : 'Etapa pausada') : 'Sem ECU'])[1]; }
      const agreed = op.phase === 'idle' ? agreedTexts(eq, this.analysis) : null;
      const headline = op.phase === 'done' ? 'Curva gravada e conferida pela ECU.'
        : op.phase === 'failed' ? op.message
          : (agreed ? agreed.headline : pilot.headline) || 'Conecte a ECU para acompanhar a calibração.';
      const next = op.phase === 'done' ? 'Dirija normalmente: o app mede se o GNV chegou na gasolina, faixa por faixa.'
        : op.phase === 'failed'
          ? (op.partial
            ? 'Parte dos pontos pode ter sido gravada. Toque em "Desfazer última gravação" para voltar à foto de antes, ou em "Entendi" para seguir.'
            : 'Nada foi gravado: a ECU manteve a curva anterior. Toque em "Entendi" e tente de novo quando a ECU estabilizar.')
          : (agreed ? agreed.next : pilot.next || '');
      setText('refinoHeadline', headline);
      const resetText = op.phase === 'idle' ? ns.DisplayRules?.gasResetNote(eq.gasEpochReason, eq.gasEpochAt) || '' : '';
      const resetNote = resetText ? ` ${resetText}` : '';
      setText('refinoNext', next + resetNote);
      // Histórico continua no gráfico/diário; o destaque atual exige uma fase com fonte conhecida.
      const currentEvidence = !['SEM_ECU', 'LENDO_ECU', 'TENTATIVA_ENCERRADA'].includes(phase);
      setText('refinoRatio', pct(currentEvidence ? eq.ratio : null));
      // Sem leitura da ECU o número é desconhecido: mostra "—", nunca 0.
      const ecuKnown = finite(pilot.petrolValid) !== null && finite(pilot.gasValid) !== null;
      setText('refinoEcuPoints', ecuKnown ? `Gas ${fmt(pilot.petrolValid, 0)} · GNV ${fmt(pilot.gasValid, 0)}` : '—');
      const dense = eq.denseBands || {};
      const oursKnown = eq.ok !== false && Array.isArray(dense.petrol) && Array.isArray(dense.gas);
      setText('refinoOurPoints', oursKnown ? `Gas ${dense.petrol.length} · GNV ${dense.gas.length}` : '—');
      const stalls = eq.stalls || {};
      const stallNode = document.getElementById('refinoStalls');
      if (stallNode) {
        const region = Array.isArray(stalls.regions) ? stalls.regions[0] : null;
        const real = finite(stalls.count) ?? 0;
        const near = finite(stalls.nearCount) ?? 0;
        stallNode.hidden = !(real > 0 || near > 0);
        const plural = n => (n === 1 ? 'vez' : 'vezes');
        const parts = [];
        if (real > 0) parts.push(`apagou ${fmt(real, 0)} ${plural(real)}${finite(stalls.restartedCount) ? ` (religou ${fmt(stalls.restartedCount, 0)})` : ''}`);
        if (near > 0) parts.push(`quase apagou ${fmt(near, 0)} ${plural(near)}`);
        const title = parts.length ? `O motor ${parts.join(' e ')} no GNV` : '';
        const where = region ? `Mais perto de ${fmt(region.fromMs, 1)}–${fmt(region.toMs, 1)} ms · MAP ${fmt(region.mapBar, 2)} bar (desaceleração/embreagem). ` : '';
        const guard = fmt(this.analysis?.guards?.lowGuardMs, 1);
        stallNode.innerHTML = title ? `<b>${escapeHtml(title)}</b><span>${escapeHtml(where)}${guard === '—' ? '' : `O refino nunca empobrece abaixo de ${escapeHtml(guard)} ms; `}se continuar, enriqueça essa região no Ajuste global. Desligar o carro na lenta não conta.</span>` : '';
      }

      const steps = document.getElementById('refinoSteps');
      if (steps) {
        const at = PHASES.findIndex(([k]) => k === (phase === 'RESTAURAR_TRECHO' ? 'VERIFICANDO' : phase));
        steps.innerHTML = PHASES.map(([k, label], i) => `<li data-state="${at < 0 ? 'pending' : i < at ? 'done' : i === at ? 'active' : 'pending'}"${phase === 'RESTAURAR_TRECHO' && k === 'VERIFICANDO' ? ' data-problem="true"' : ''}><i aria-hidden="true">${at >= 0 && i < at ? '✓' : i + 1}</i>${escapeHtml(label)}</li>`).join('');
      }

      const button = document.querySelector('[data-refino-primary]');
      if (button) {
        if (op.phase === 'reading' || op.phase === 'writing') this.renderOperation();
        else if (op.phase === 'done' || op.phase === 'failed') { button.hidden = false; button.disabled = false; button.textContent = 'Entendi'; button.dataset.refinoDismiss = ''; }
        else {
          delete button.dataset.refinoDismiss;
          const action = primaryAction(eq, this.analysis);
          button.hidden = action.kind === 'none';
          button.disabled = action.kind === 'stable' || action.kind === 'waiting';
          button.textContent = action.label;
          button.dataset.kind = action.kind;
        }
      }
      this.renderEquivalenceStrip();
      this.renderUndo();
      this.renderChart();
      this.renderJournal();
      this.renderTech();
    }

    renderEquivalenceStrip() {
      const host = document.getElementById('refinoEq');
      if (!host) return;
      const strip = equivalenceStrip(this.eq, ns.ROUTES);
      host.hidden = !strip;
      if (!strip) return;
      setText('refinoEqIndex', strip.indexText);
      setText('refinoEqNext', strip.nextText);
      const go = document.getElementById('refinoEqGo');
      if (go) {
        go.hidden = !strip.route;
        go.dataset.route = strip.route;
        go.dataset.subpage = strip.subpage;
        setText('refinoEqGo', strip.routeLabel);
      }
    }

    /** Desfazer fora do <details> recolhido: sempre à vista quando há para onde voltar (foto ou antes/depois). */
    renderUndo() {
      const host = document.getElementById('refinoUndo');
      if (!host) return;
      const busy = this.operation.phase === 'reading' || this.operation.phase === 'writing';
      const latest = this.eq?.refinement?.latest;
      const available = !busy && (Boolean(this.undoFile()) || undoSource(latest).available);
      host.hidden = !available;
      if (!available) { if (host.innerHTML) host.innerHTML = ''; return; }
      if (!host.querySelector('[data-refino-undo]')) host.innerHTML = '<button type="button" class="secondary" data-refino-undo>Desfazer última gravação</button>';
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
      const xMax = Math.max(4, Math.ceil(safeMax(xs, 3) * 1.25));
      const ys = [
        ...reference.filter(p => p.petrolMs <= xMax).flatMap(p => [hasPetrolRv ? p.petrolMapBar : null, hasGasRv ? p.gasMapBar : null]),
        ...acquired.map(p => p.mapBar), ...ours.map(p => p.mapBar),
      ].filter(v => Number.isFinite(v) && v > 0);
      const yMin = Math.max(0, Math.floor(safeMin(ys, 0.3) * 20) / 20 - 0.05);
      const yMaxRaw = Math.ceil(safeMax(ys, 0.9) * 20) / 20 + 0.05;
      const yMax = yMaxRaw > yMin + 0.1 ? yMaxRaw : yMin + 0.1;
      const width = 1000; const height = 400; const padLeft = 64; const padRight = 28; const padTop = 22; const padBottom = 48;
      const xFor = v => padLeft + ((v - xMin) / (xMax - xMin)) * (width - padLeft - padRight);
      const yFor = v => height - padBottom - ((v - yMin) / (yMax - yMin)) * (height - padTop - padBottom);
      const xTicks = Array.from({ length: 6 }, (_, i) => xMin + i * (xMax - xMin) / 5);
      const yTicks = Array.from({ length: 5 }, (_, i) => yMin + i * (yMax - yMin) / 4);
      const grid = yTicks.map(v => `<line class="autocal-grid-line" x1="${padLeft}" y1="${yFor(v).toFixed(1)}" x2="${width - padRight}" y2="${yFor(v).toFixed(1)}"></line><text class="autocal-axis-tick-y" x="${padLeft - 8}" y="${(yFor(v) + 4).toFixed(1)}" text-anchor="end">${v.toFixed(2)}</text>`).join('') +
        xTicks.map(v => `<line class="autocal-grid-line vertical" x1="${xFor(v).toFixed(1)}" y1="${padTop}" x2="${xFor(v).toFixed(1)}" y2="${height - padBottom}"></line><text class="autocal-axis-tick-x" x="${xFor(v).toFixed(1)}" y="${height - 23}" text-anchor="middle">${v.toFixed(1)}</text>`).join('');
      const path = key => reference.filter(p => finite(p[key]) !== null && p[key] > 0 && p[key] >= yMin && p[key] <= yMax && p.petrolMs <= xMax)
        .map((p, i) => `${i ? 'L' : 'M'} ${xFor(p.petrolMs).toFixed(1)} ${yFor(p[key]).toFixed(1)}`).join(' ');
      // Alvo de toque de 44 unidades (círculo invisível por baixo do ponto, como na aba AutoCal).
      const acquiredMarkup = acquired.map((p, i) => `<circle class="autocal-acquired-hit" data-refino-dot="ecu:${i}" cx="${xFor(p.petrolMs).toFixed(1)}" cy="${yFor(p.mapBar).toFixed(1)}" r="22"></circle><circle class="autocal-acquired-point ${p.fuel === 'GAS' ? 'gas' : 'petrol'} ${p.acquisitionState === 'ACQUIRED' ? 'acquired' : 'collecting'}" cx="${xFor(p.petrolMs).toFixed(1)}" cy="${yFor(p.mapBar).toFixed(1)}" r="${p.acquisitionState === 'ACQUIRED' ? '6.0' : '4.6'}"></circle>`).join('');
      const oursMarkup = ours.map((p, i) => `<circle class="autocal-acquired-hit" data-refino-dot="our:${i}" cx="${xFor(p.tpetMs).toFixed(1)}" cy="${yFor(p.mapBar).toFixed(1)}" r="22"></circle><rect class="refino-our-point ${p.fuel === 'GAS' ? 'gas' : 'petrol'}" x="${(xFor(p.tpetMs) - 3.5).toFixed(1)}" y="${(yFor(p.mapBar) - 3.5).toFixed(1)}" width="7" height="7" rx="1.5"></rect>`).join('');
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
      const now = Date.now();
      let explained = null;
      if (kind === 'our') {
        const p = (this.ours || [])[index];
        if (p) explained = explainPoint('our', p, { now });
      } else {
        const p = (this.acquired || [])[index];
        if (p) {
          const key = p.fuel === 'GAS' ? 'PETR_INJ_TBUF_GAS' : 'PETR_INJ_TBUF';
          const field = (Array.isArray(this.snapshot?.fields) ? this.snapshot.fields : []).find(f => f && f.key === key && f.status === 'VALID');
          explained = explainPoint('ecu', p, { now, capturedAtMs: field?.capturedAtMs, rejected: this.analysis?.rejectedBands });
        }
      }
      if (!explained) return;
      inspector.dataset.counts = explained.counts ? 'true' : 'false';
      inspector.innerHTML = `<b>${escapeHtml(explained.title)}</b>${explained.lines.map(line => `<span>${escapeHtml(line)}</span>`).join('')}`;
    }

    renderJournal() {
      const host = document.getElementById('refinoJournal');
      if (!host) return;
      const latest = this.eq?.refinement?.latest;
      if (!latest || !latest.status) { host.innerHTML = '<p>Nenhuma gravação feita pelo refino ainda.</p>'; return; }
      if (latest.status === 'FALHA_PARCIAL') { host.innerHTML = `<p class="refino-note">${escapeHtml(JOURNAL_NOTE.FALHA_PARCIAL)}</p>`; return; }
      const bands = (Array.isArray(latest.bands) ? latest.bands : []).filter(b => VERDICT[b.verdict]);
      const rows = bands.map(b => `<div data-verdict="${escapeHtml(b.verdict)}"><span>${fmt(b.fromMs, 1)}–${fmt(b.toMs, 1)} ms</span><b>${pct(b.ratioBefore)}${finite(b.ratioAfter) === null ? '' : ' → ' + pct(b.ratioAfter)}</b><small>${escapeHtml(VERDICT[b.verdict])}</small></div>`).join('');
      let closingText = JOURNAL_NOTE[latest.status] || '';
      if (latest.status === 'PIOROU_EM_PARTE') {
        const restorable = Array.isArray(this.eq?.restorePoints) && this.eq.restorePoints.length > 0;
        closingText = restorable
          ? 'Dá para restaurar só o trecho que piorou: toque no botão de cima.'
          : 'A oferta de restaurar só o trecho expirou (vale por 30 min). Para voltar à curva de antes, use Desfazer última gravação.';
      }
      const closing = closingText ? `<p class="refino-note">${escapeHtml(closingText)}</p>` : '';
      const history = (Array.isArray(this.eq?.refinement?.history) ? this.eq.refinement.history : []).slice().reverse().map(item => {
        const when = finite(item.appliedAt) ? new Date(item.appliedAt).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—';
        const ratios = finite(item.ratioBefore) === null ? '' : ` · GNV÷gasolina ${pct(item.ratioBefore)}${finite(item.ratioAfter) === null ? '' : ' → ' + pct(item.ratioAfter)}`;
        return `<li data-status="${escapeHtml(item.status)}"><b>${escapeHtml(when)}</b> ${escapeHtml(STATUS_WORDS[item.status] || 'Resultado desconhecido')}${escapeHtml(ratios)}</li>`;
      }).join('');
      host.innerHTML = `${rows ? `<div class="refino-verdicts">${rows}</div>` : '<p>Dirija no GNV: o app compara cada faixa com a gasolina no mesmo RPM e MAP.</p>'}${closing}<p class="refino-note">Cada resultado ajusta a força da próxima correção naquela faixa.</p>${history ? `<h4 class="refino-history-title">Histórico de gravações</h4><ol class="refino-history">${history}</ol>` : ''}`;
    }

    renderTech() {
      const host = document.getElementById('refinoTech');
      if (!host) return;
      const a = this.analysis || {};
      const pilot = this.eq?.autopilot || {};
      host.innerHTML = `<dl>
        <div><dt>Modo</dt><dd>${escapeHtml(a.refinementMode || '—')} · ${a.available ? 'disponível' : escapeHtml(a.message || 'aguardando evidência')}</dd></div>
        <div><dt>Gasolina de referência</dt><dd>${escapeHtml({ ECU: 'curva de gasolina que a ECU já tem', PROPRIA: 'gasolina medida por este app', MISTA: 'medida por este app + curva da ECU', NENHUMA: 'ainda sem referência: rode na gasolina' }[this.eq?.petrolReference] || '—')}</dd></div>
        <div><dt>De onde vem a proposta</dt><dd>${escapeHtml({ ECU_E_CONDUCAO: 'faixas da ECU + sua condução', CONDUCAO: 'só a sua condução (a ECU ainda não tem faixas maduras)', NENHUMA: 'sem evidência suficiente: nada muda' }[a.evidenceSource] || '—')}</dd></div>
        <div><dt>Bandas comuns maduras</dt><dd>${fmt(a.matureCommonPoints, 0)} de ${fmt(a.minimumMatureCommonPoints, 0)} necessárias · ${fmt(a.telemetryTargets, 0)} alvos dos nossos pontos</dd></div>
        <div><dt>Pontos da ECU descartados</dt><dd>${Array.isArray(a.rejectedBands) && a.rejectedBands.length ? a.rejectedBands.map(r => `${escapeHtml(r.fuel === 'GNV' ? 'GNV' : 'Gasolina')} B${Number(r.band) + 1} (${fmt(r.timeMs, 1)} ms · ${fmt(r.mapBar, 2)} bar)`).join(', ') + ' — fora da tendência; não entram no cálculo' : 'nenhum'}</dd></div>
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
      if (typeof root.addEventListener === 'function') root.addEventListener('omegas-app-ready', boot, { once: true });
      return;
    }
    if (app.refino) return;
    app.refino = new RefinoScreen(app);
  }

  ns.RefinoModel = { equivalenceStrip, indexPercent, proposedPoints, undoPoints, undoSource, agreedTexts, primaryAction, explainPoint, ageText, safeMin, safeMax, STATUS_WORDS };
  ns.RefinoScreen = RefinoScreen;
  if (typeof document !== 'undefined') boot();
})(typeof window !== 'undefined' ? window : globalThis);
