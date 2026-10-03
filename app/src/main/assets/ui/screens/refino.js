(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Refino OMEGAS: o "nosso AutoCal". Mesma linguagem visual do AutoCal da Platina
  // (cabeçalho, gráfico Petrol Inj. × MAP, revisão), com os nossos pontos por cima.
  // A matemática é do Kotlin (AutoMatchRefinedEngine, EquivalenceLedger, diário e piloto).
  // Nada grava sozinho: a gravação usa o fluxo da Curva K da Platina (leitura → conferência
  // → gravação → conferência na ECU), sempre depois do seu toque.

  const PHASES = [
    ['ECU_TRABALHANDO', 'ECU no automático'],
    ['COLETANDO_NOSSOS', 'Medindo o GNV'],
    ['PROPOSTA_PRONTA', 'Curva pronta'],
    ['VERIFICANDO', 'Medindo'],
    ['ESTAVEL', 'Estável'],
  ];
  const PHASE_TONE = {
    SEM_ECU: 'unknown', LENDO_ECU: 'unknown', TENTATIVA_ENCERRADA: 'problem', ECU_TRABALHANDO: 'unknown', COLETANDO_NOSSOS: 'collecting',
    PROPOSTA_PRONTA: 'ready', VERIFICANDO: 'collecting', RESTAURAR_TRECHO: 'problem', ESTAVEL: 'ok',
  };
  const VERDICT = {
    CONFIRMADA: 'Chegou na gasolina',
    PASSOU: 'Passou do ponto · próxima mais suave',
    CURTA: 'Faltou · próxima mais firme',
    PIOROU: 'Piorou',
    COLETANDO: 'Medindo…',
    SEM_ANTES: 'sem medição anterior',
    SEM_DADOS: 'poucas leituras · não deu para julgar',
  };
  // O que cada fechamento do diário significa, em linguagem simples.
  const JOURNAL_NOTE = {
    SEM_BASE: 'As faixas alteradas não tinham medição de antes. A medição de agora vira a base e o Refino segue sozinho.',
    INCONCLUSIVO: 'Poucas leituras nas faixas alteradas: não deu para julgar. O Refino segue medindo do zero.',
    INTERROMPIDO: 'A ECU mudou a curva por fora (AutoMatch ou Mapa K) durante a medição, então o resultado perdeu a validade.',
    FALHA_PARCIAL: 'A gravação falhou no meio e a ECU pode ter sido alterada em parte. Toque em Desfazer para voltar à foto de antes.',
  };
  const ORIGIN = { MEASURED: 'Medido', BLENDED: 'Transição', SMOOTHED: 'Anti-tranco', HELD: 'Mantido' };
  const POLL_MS = 300;
  // O gráfico é do componente compartilhado; a ponte da ECU entrega o estado em ciclos de 2 leituras do relógio.
  const OPERATION_TIMEOUT_MS = 90000;
  const DATA_EVERY_TICKS = 2;

  const D = ns.DisplayRules;
  const finite = D.finite;
  const escapeHtml = D.escapeHtml;
  const fmt = D.fmt;
  const pct = D.gapPercent;
  const ageText = D.ageText;
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
  /** Resultado do experimento em linguagem simples (histórico). */
  const STATUS_WORDS = {
    VERIFICADO: 'Chegou na gasolina',
    PIOROU_EM_PARTE: 'Piorou em parte',
    FALHA_PARCIAL: 'A gravação falhou no meio: a ECU pode ter sido alterada em parte',
    VERIFICANDO: 'Medindo…',
    SEM_BASE: 'Sem medição anterior: a medição de agora virou a base',
    INCONCLUSIVO: 'Inconclusivo',
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
        'De quem é: medido pelo OMEGAS na sua condução (leitura estável = 3 leituras seguidas).',
        `${D.barUnit(point.mapBar)} · ${D.msUnit(point.tpetMs)} · ${fmt(point.samples, 0)} leituras · RPM típico ${D.rpm(point.rpmMedian)}`,
        `Quando: última leitura ${ageText(point.lastAtMs, now)}.`,
      ];
      lines.push(idle
        ? 'Por que conta: NÃO conta. É marcha lenta: aparece no gráfico, mas não corrige a curva (a ECU trata a lenta à parte).'
        : gas
          ? 'Por que conta: forma par com a gasolina no mesmo RPM e MAP. Só vale para a curva atual: se a curva mudar, recomeça.'
          : 'Por que conta: é a referência da gasolina. O GNV é comparado com ela no mesmo RPM e MAP.');
      if (finite(point.slot) !== null) {
        lines.splice(2, 0, `${point.kind === 'below' ? 'Abaixo da primeira faixa da ECU' : point.kind === 'above' ? 'Acima da última faixa da ECU' : `Entre as faixas ${fmt(point.slot, 0)} e ${fmt(point.slot + 1, 0)} da ECU`} · ${fmt(point.episodes, 0)} ${point.episodes === 1 ? 'episódio' : 'episódios'} · confiança ${D.percentFraction(point.confidence)}.`);
        const fine = Array.isArray(point.fineBins) ? point.fineBins : [];
        if (fine.length > 1) {
          lines.splice(3, 0, `Dentro da faixa (${fine.length} medidas): ${fine.slice(0, 5).map(f => `${D.barUnit(f.mapBar)} · ${D.msUnit(f.tpetMs)}`).join(' | ')}${fine.length > 5 ? ' …' : ''}`);
        }
      }
      return { title: `Medido pelo OMEGAS · ${gas ? 'GNV' : 'Gasolina'}`, lines, counts: !idle };
    }
    const gas = point.fuel === 'GAS';
    const rejected = (ctx?.rejected || []).some(r => (r.fuel === 'GNV') === gas && Number(r.band) === Number(point.index));
    const acquired = point.acquisitionState === 'ACQUIRED';
    const progress = finite(point.counter) === null ? '' : ` (${fmt(point.counter, 0)}${finite(point.threshold) === null ? '' : '/' + fmt(point.threshold, 0)})`;
    const lines = [
      'De quem é: medido pela ECU (AutoCal da ECU), não pelo OMEGAS.',
      `${D.barUnit(point.mapBar)} · ${D.msUnit(point.petrolMs)} · ${acquired ? 'lido pela ECU' : 'lendo'}${progress}`,
      `Quando: leitura da ECU ${ageText(ctx?.capturedAtMs, now)}.`,
    ];
    if (rejected) lines.push('Por que conta: NÃO conta. Foi descartado como anomalia: fora da tendência das outras faixas (típico de marcha lenta puxando a curva).');
    else if (acquired) lines.push('Por que conta: faixa lida pela ECU; entra no cálculo junto com os nossos pontos.');
    else lines.push('Por que conta: ainda lendo; só conta quando a ECU terminar esta faixa.');
    return { title: `Ponto da ECU · ${point.fuelLabel} faixa ${point.point}`, lines, counts: acquired && !rejected };
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

  const ROUTE_NAMES = { map: 'Mapa K', curve: 'Curva K', autocal: 'AutoCal', refino: 'Refino', sessions: 'Sessões', tools: 'Ferramentas' };
  /** Índice de equivalência: fração 0..1 (percentual = ×100). Sem número válido: null (nunca 0%). */
  function indexPercent(eq) {
    const value = finite(eq?.index?.value);
    return value === null ? null : Math.round(Math.max(0, Math.min(1, value)) * 100);
  }
  /**
   * Faixa única do Refino (D1): "GNV ≈ gasolina em N%" + UMA próxima ação (a do cérebro, eq.nextAction) + o botão de
   * um toque quando a ação aponta para outra aba. Só mostra e leva; nunca executa. Sem dado do cérebro: aviso neutro,
   * sem ação (a UI não deriva ação da fase).
   */
  const WAITING_TEXT = 'Aguardando dados da ECU';
  function equivalenceStrip(eq, routes) {
    const percent = indexPercent(eq);
    const action = eq?.nextAction || null;
    const text = action && action.text ? String(action.text) : '';
    const route = action && action.route && action.route !== 'refino' && (!routes || routes.includes(action.route)) ? String(action.route) : '';
    return {
      indexText: percent === null ? 'GNV ≈ gasolina em —' : `GNV ≈ gasolina em ${percent}%${eq.index.provisional === true ? ' (provisório)' : ''}`,
      nextText: text || WAITING_TEXT,
      hasAction: Boolean(text),
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
        return { kind: 'review', label: `Revisar e gravar ${D.plural(proposal, 'ponto', 'pontos')}`, expired: true };
      }
      return { kind: 'none', label: '' };
    }
    const restore = Array.isArray(eq?.restorePoints) ? eq.restorePoints.length : 0;
    if (phase === 'RESTAURAR_TRECHO' && restore) return { kind: 'restore', label: `Desfazer o trecho que piorou (${D.plural(restore, 'ponto', 'pontos')})` };
    if (phase === 'ESTAVEL') return { kind: 'stable', label: '✓ Estável · pode desconectar' };
    // Enquanto a ECU faz o automático ela pode sobrescrever qualquer curva: o refino calcula e
    // mostra, mas a gravação só libera quando a ECU terminar.
    if (phase === 'SEM_ECU' || phase === 'LENDO_ECU' || phase === 'ECU_TRABALHANDO') {
      const p = eq?.autopilot || {};
      const progress = finite(p.autoMatchCount) !== null ? ` (${p.autoMatchCount}${finite(p.maxAutomatch) !== null ? ' de ' + p.maxAutomatch : ''})` : '';
      return proposal ? { kind: 'waiting', label: `Aguardando a ECU no automático${progress}` } : { kind: 'none', label: '' };
    }
    if (phase === 'VERIFICANDO') return { kind: 'waiting', label: 'Medindo a última gravação…' };
    if (proposal && analysis?.available) {
      return { kind: 'review', label: `Revisar e gravar ${D.plural(proposal, 'ponto', 'pontos')}` };
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
      this.cursor = new ns.LiveStore.EaseCursor(() => document.querySelector('[data-chart-live]'));
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
      });
      // O cursor AGORA anda no quadro de animação (rAF do scheduler), sem redesenhar o gráfico.
      this.unsubscribeFrame = this.scheduler.addFrameHook(timestamp => this.animateLive(timestamp));
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
              <div class="autocal-title-line"><h3>Refino</h3><span id="refinoPhaseChip" class="autocal-fuel-chip" data-fuel-state="unknown">—</span></div>
            </div>
            <div class="autocal-focus-metrics" aria-live="polite">
              <div class="autocal-focus-metric"><small>GNV igual à gasolina</small><b><span id="refinoRatio">—</span></b></div>
            </div>
            <div class="autocal-focus-actions"><button type="button" class="autocal-primary-action" data-refino-primary hidden></button></div>
          </header>
          <ol class="refino-steps" id="refinoSteps" aria-label="Fases do refino"></ol>
          <div class="refino-eq" id="refinoEq" aria-label="Equivalência com a gasolina"><b id="refinoEqIndex">—</b><span id="refinoHeadline">Aguardando dados da ECU</span><button type="button" class="secondary" id="refinoEqGo" data-refino-go hidden></button></div>
          <p class="refino-next" id="refinoNext" hidden></p>
          <div class="refino-actions-row">
            <div class="refino-stalls" id="refinoStalls" hidden></div>
            <div class="refino-undo" id="refinoUndo" hidden></div>
            <details class="autocal-secondary-details" id="refinoResultDetails">
              <summary>Último resultado</summary>
              <div id="refinoJournal" class="refino-journal"></div>
            </details>
            <details class="autocal-secondary-details">
              <summary>Detalhes técnicos</summary>
              <div id="refinoTech" class="refino-tech"></div>
            </details>
          </div>
          <section class="autocal-reference-card refino-chart-card" aria-label="Curva de aquisição · Gasolina × GNV">
            <div class="refino-chart-head">
              <span class="autocal-plot-title">Curva de aquisição · Gasolina × GNV</span>
              <p class="refino-counts"><span>Pontos da ECU <b id="refinoEcuPoints">—</b></span><span>Pontos do OMEGAS <b id="refinoOurPoints">—</b></span></p>
              <div class="autocal-chart-legend" id="refinoLegend" aria-label="Legenda do gráfico"></div>
            </div>
            <div class="autocal-chart-workspace">
              <div id="refinoChart" class="autocal-chart-host"><div class="chart-empty">Aguardando a leitura da ECU.</div></div>
              <aside id="refinoInspector" class="autocal-chart-inspector"><b>Toque em um marcador</b><span>Quanto maior e mais forte, mais leituras. Eu digo de quem é, quando foi medido e se conta para a curva.</span></aside>
            </div>
          </section>
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
      this.projection = projection.ok === true ? projection : {};
      this.snapshot = projection.ok === true ? (projection.snapshot || {}) : {};
      // O armazém único da evidência também alimenta o AutoCal: ele não busca de novo o que o Refino já leu.
      ns.CurveChart?.setEvidence(this.eq, this.analysis, Date.now());
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
        return `<div><dt>${fmt(ref.referenceTimeMs, 1)} ms</dt><dd><b>${D.kValue(before)} → ${D.kValue(after)}</b> · ${pct(after / before)}${ref.origin ? ' · ' + escapeHtml(ORIGIN[ref.origin] || ref.origin) : ''}</dd></div>`;
      }).join('');
      const early = phase === 'ECU_TRABALHANDO'
        ? '<p class="refino-warning">A ECU ainda está no automático e pode sobrescrever esta curva. O ideal é gravar quando ela terminar; o app avisa.</p>' : '';
      const review = document.getElementById('refinoReview');
      if (!review) return;
      review.hidden = false;
      review.innerHTML = `<div class="autocal-review-card"><header><div><small>REVISÃO ANTES DA ECU</small><h3>${escapeHtml(title)} · ${D.plural(points.length, 'ponto', 'pontos')} da Curva K</h3></div><button type="button" data-refino-cancel class="icon-close" aria-label="Fechar revisão">×</button></header>${early}<dl class="refino-review-list">${rows}</dl><div class="write-contract"><b>Nada foi enviado à ECU.</b><span>Gravar é um toque: o app guarda a foto antes, grava e confere na ECU. Desfazer volta à foto.</span></div><div class="operation-actions"><button type="button" data-refino-cancel class="secondary">Cancelar</button><button type="button" data-refino-confirm class="primary">Gravar na ECU</button></div></div>`;
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
      if (!read?.ok || !read?.started) { this.fail(read?.error || 'O app não conseguiu ler a Curva K da ECU.'); return; }
      this.operation = { phase: 'reading' };
      this.render(true);
      this.poll(result => {
        const list = Array.isArray(result.points) ? result.points : [];
        if (result.state !== 'COMPLETED' || list.length !== 30) { this.fail(failureText(result, 'O app não conseguiu confirmar na ECU. A curva anterior continua.')); return; }
        const factors = new Map(list.map((p, i) => [Number(p.index ?? i), Number(p.factorRaw)]));
        const stale = points.find(p => factors.get(p.index) !== p.currentRaw);
        if (stale) { this.fail('A Curva K da ECU mudou desde a última leitura. Aguarde a próxima leitura e revise de novo.'); return; }
        const write = restoreFile && typeof this.native.restoreCurve === 'function'
          ? this.native.restoreCurve(points, restoreFile)
          : this.native.writeCurve(points, reason);
        if (!write?.ok || !write?.started) { this.fail(failureText(write, 'O app não conseguiu iniciar a gravação.')); return; }
        this.operation = { phase: 'writing', total: points.length };
        this.render(true);
        this.poll(done => {
          if (done.state === 'BATCH_CONFIRMED' && done.readbackValid === true) {
            // O diário já guarda a foto de antes DESTA gravação; o próximo Desfazer a usa.
            this.operation = { phase: 'done' };
            this.render(true);
          } else {
            this.fail(failureText(done, 'O app não conseguiu confirmar na ECU. A curva anterior continua.'));
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
        if (now - job.started > OPERATION_TIMEOUT_MS) { this.job = null; this.fail('A ECU demorou demais para responder. A curva anterior continua.'); return; }
        if (this.operation.phase === 'writing') { this.operation.progress = finite(status.progress); this.renderOperation(); }
        return;
      }
      this.job = null;
      if (status.ok === false || /FAILED|TIMEOUT/.test(String(status.state || ''))) {
        // Falha com a ECU possivelmente alterada: o Desfazer continua disponível, com a foto de antes.
        const mayHaveChanged = status.partial === true || status.mutationMayHaveStarted === true;
        const text = failureText(status, 'O app não conseguiu confirmar na ECU.');
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
      if (headline) headline.textContent = 'Refino indisponível: a ponte do AutoCal não respondeu.';
    }

    renderOperation() {
      const button = document.querySelector('[data-refino-primary]');
      const op = this.operation;
      if (!button) return;
      if (op.phase === 'reading' || op.phase === 'writing') {
        button.hidden = false;
        button.disabled = true;
        const progress = finite(op.progress);
        button.textContent = op.phase === 'reading' ? 'Lendo a curva da ECU…' : `Gravando…${progress === null ? '' : ' ' + Math.round(progress) + '%'}`;
      }
    }

    render(force) {
      const eq = this.eq || {};
      const pilot = eq.autopilot || {};
      const phase = pilot.phase || 'SEM_ECU';
      const op = this.operation;
      // Assinatura barata (sem serializar pontos): só o que muda o texto, a ação ou o desenho.
      const chart = ns.CurveChart;
      const key = [phase, pilot.expiredFrom, eq.refinement?.latest?.photoFile, eq.refinement?.latest?.status, pilot.petrolValid, pilot.gasValid,
        eq.ratio, eq.samples, eq.index?.value, eq.nextAction?.text, eq.nextAction?.route, op.phase, op.message, op.progress, eq.stalls?.count, eq.gasEpochAt,
        chart ? chart.evidenceSignature({ snapshot: this.snapshot, eq, analysis: this.analysis, sessionId: this.projection?.sessionId, extra: this.sizeKey() }) : ''].join('|');
      if (!force && key === this.lastRenderKey) return;
      this.lastRenderKey = key;

      const chip = document.getElementById('refinoPhaseChip');
      if (chip) {
        const readyAfterExpiry = phase === 'TENTATIVA_ENCERRADA' && pilot.expiredFrom === 'PROPOSTA_PRONTA';
        chip.dataset.fuelState = (readyAfterExpiry ? 'ready' : PHASE_TONE[phase]) || 'unknown';
        chip.textContent = D.phaseLabel(phase, pilot.expiredFrom);
      }
      const next = op.phase === 'done' ? 'Dirija normalmente: o app mede se o GNV chegou na gasolina, faixa por faixa.'
        : op.phase === 'failed'
          ? (op.partial
            ? 'Parte dos pontos pode ter sido gravada. Toque em Desfazer para voltar à foto de antes, ou em Entendi para seguir.'
            : 'Nada foi gravado: a ECU manteve a curva anterior. Toque em Entendi e tente de novo quando a ECU estabilizar.')
          : '';
      const resetText = op.phase === 'idle' ? D.gasResetNote(eq.gasEpochReason, eq.gasEpochAt) || '' : '';
      const nextLine = [next, resetText].filter(Boolean).join(' ');
      const nextNode = document.getElementById('refinoNext');
      if (nextNode) { nextNode.hidden = !nextLine; setText('refinoNext', nextLine); }
      // Histórico continua no gráfico/diário; o destaque atual exige uma fase com fonte conhecida.
      const currentEvidence = !['SEM_ECU', 'LENDO_ECU', 'TENTATIVA_ENCERRADA'].includes(phase);
      setText('refinoRatio', pct(currentEvidence ? eq.ratio : null));
      // Sem leitura da ECU o número é desconhecido: mostra "—", nunca 0.
      const ecuKnown = finite(pilot.petrolValid) !== null && finite(pilot.gasValid) !== null;
      setText('refinoEcuPoints', ecuKnown ? `Gasolina ${fmt(pilot.petrolValid, 0)} · GNV ${fmt(pilot.gasValid, 0)}` : '—');
      const dense = eq.denseBands || {};
      const oursKnown = eq.ok !== false && Array.isArray(dense.petrol) && Array.isArray(dense.gas);
      setText('refinoOurPoints', oursKnown ? `Gasolina ${dense.petrol.length} · GNV ${dense.gas.length}` : '—');
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
        const title = parts.length ? `Motor ${parts.join(' e ')} no GNV` : '';
        const where = region ? `Mais perto da faixa ${D.msBand(region.fromMs, region.toMs)} · ${D.barUnit(region.mapBar)} (desaceleração ou embreagem). ` : '';
        const guard = fmt(this.analysis?.guards?.lowGuardMs, 1);
        this.stallDetail = title ? `${where}${guard === '—' ? '' : `O Refino nunca deixa a mistura mais pobre abaixo de ${guard} ms; `}se continuar, deixe a mistura mais rica nessa região, na Curva K. Desligar o carro na lenta não conta.` : '';
        stallNode.title = this.stallDetail;
        stallNode.innerHTML = title ? `<b>${escapeHtml(title)}</b>` : '';
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
      const op = this.operation;
      // Uma fala só por tela: o texto do cérebro; durante/depois de uma gravação, o resultado dela.
      const text = op.phase === 'done' ? 'Curva gravada e conferida pela ECU.' : op.phase === 'failed' ? op.message : strip.nextText;
      setText('refinoEqIndex', strip.indexText);
      setText('refinoHeadline', text);
      host.dataset.hasAction = strip.hasAction ? 'true' : 'false';
      const go = document.getElementById('refinoEqGo');
      if (go) {
        const showGo = !!strip.route && op.phase === 'idle';
        go.hidden = !showGo;
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
      if (!host.querySelector('[data-refino-undo]')) host.innerHTML = '<button type="button" class="secondary" data-refino-undo>Desfazer a gravação</button>';
    }

    /** Tamanho do quadro do gráfico: faz parte da assinatura (outro tamanho = outro desenho). */
    sizeKey() {
      const host = document.getElementById('refinoChart');
      const w = host?.clientWidth || 0;
      const h = host?.clientHeight || 0;
      return `${Math.round(w / 16)}x${Math.round(h / 16)}`;
    }

    /**
     * Gráfico = componente compartilhado com o AutoCal. Aqui só se monta o nó dele no nosso quadro:
     * ele é redesenhado SÓ quando a assinatura da evidência muda (nada de repintar a cada leitura).
     */
    renderChart() {
      const host = document.getElementById('refinoChart');
      const chart = ns.CurveChart;
      if (!host || !chart) return;
      const eq = this.eq || {};
      const width = Math.round(host.clientWidth) || 1000;
      const height = Math.round(host.clientHeight) || 400;
      const signature = chart.evidenceSignature({ snapshot: this.snapshot, eq, analysis: this.analysis, sessionId: this.projection?.sessionId, extra: this.sizeKey() });
      const input = { snapshot: this.snapshot, projection: this.projection || {}, eq, analysis: this.analysis };
      chart.mount(host, signature, () => {
        const model = chart.buildModel(input);
        if (!model || !model.domain || (!model.reference.length && !model.ecu.length && !model.ours.length)) {
          return { html: '<div class="chart-empty"><b>SEM PONTOS AINDA</b><span>Rode na gasolina e no GNV com a ECU conectada. Os pontos da ECU e os do OMEGAS aparecem aqui.</span></div>', scale: null, model: null };
        }
        const built = chart.buildSvg(model, { width, height });
        return built.empty ? { html: '<div class="chart-empty"><b>SEM PONTOS AINDA</b></div>', scale: null, model: null } : { ...built, model };
      }, 'between');
      const shown = chart.shared;
      this.chartScale = shown.scale;
      this.model = shown.model;
      const legend = document.getElementById('refinoLegend');
      const flags = { mode: 'between', proposal: !!(this.model && this.model.proposal.length), stall: !!(this.model && this.model.stalls.length) };
      const legendKey = `between|${flags.proposal}|${flags.stall}`;
      if (legend && legend.dataset.key !== legendKey) { legend.dataset.key = legendKey; legend.innerHTML = chart.legendHtml(flags); }
    }

    /** Cursor AGORA: só calcula o alvo a partir da leitura viva única (LiveStore); quem move é o quadro de animação. */
    renderLive() {
      const scale = this.chartScale;
      const layer = document.querySelector('[data-chart-live]');
      if (!layer || !scale) { this.cursor.clear(); return; }
      const live = ns.LiveStore.point(this.store.get().telemetry || {});
      if (!live) { layer.setAttribute('display', 'none'); this.cursor.clear(); return; }
      const projected = ns.AutoCalUxModel.projectLive(live, scale);
      layer.removeAttribute('display');
      layer.setAttribute('data-stale', live.grey ? 'true' : 'false');
      layer.setAttribute('data-out-of-range', projected.outOfRange ? 'true' : 'false');
      this.cursor.setTarget(projected.x, projected.y, scale, projected.outOfRange);
      const label = layer.querySelector('[data-autocal-live-label]');
      const text = projected.outOfRange ? 'Agora · fora da escala' : 'Agora';
      if (label && label.textContent !== text) label.textContent = text;
    }

    /** Quadro de animação (rAF do scheduler): só na aba Refino; o alvo só é recalculado quando chega leitura nova. */
    animateLive(timestamp) {
      if (this.store.get().route !== 'refino') { this.cursor.frameAt = null; return; }
      const telemetry = this.store.get().telemetry || {};
      const key = `${telemetry.sequence}|${Math.round((telemetry.telemetryAgeMs || 0) / 500)}|${ns.CurveChart?.shared.signature}`;
      if (key !== this.liveKey) { this.liveKey = key; this.renderLive(); }
      this.cursor.frame(timestamp);
    }

    inspect(token) {
      const [kind, raw] = String(token || '').split(':');
      const index = Number(raw);
      const inspector = document.getElementById('refinoInspector');
      if (!inspector) return;
      const now = Date.now();
      const model = this.model || {};
      let explained = null;
      if (kind === 'our' || kind === 'ourb' || kind === 'oure') {
        const p = ((kind === 'oure' ? model.ours : model.between) || [])[index];
        if (p) explained = explainPoint('our', { ...p, fuel: p.fuel }, { now });
      } else {
        const p = (model.ecu || [])[index];
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
      if (!latest || !latest.status) { host.innerHTML = '<p>Nenhuma gravação feita pelo Refino ainda.</p>'; return; }
      if (latest.status === 'FALHA_PARCIAL') { host.innerHTML = `<p class="refino-note">${escapeHtml(JOURNAL_NOTE.FALHA_PARCIAL)}</p>`; return; }
      const bands = (Array.isArray(latest.bands) ? latest.bands : []).filter(b => VERDICT[b.verdict]);
      const rows = bands.map(b => `<div data-verdict="${escapeHtml(b.verdict)}"><span>${fmt(b.fromMs, 1)}–${fmt(b.toMs, 1)} ms</span><b>${pct(b.ratioBefore)}${finite(b.ratioAfter) === null ? '' : ' → ' + pct(b.ratioAfter)}</b><small>${escapeHtml(VERDICT[b.verdict])}</small></div>`).join('');
      let closingText = JOURNAL_NOTE[latest.status] || '';
      if (latest.status === 'PIOROU_EM_PARTE') {
        const restorable = Array.isArray(this.eq?.restorePoints) && this.eq.restorePoints.length > 0;
        closingText = restorable
          ? 'Dá para desfazer só o trecho que piorou: toque no botão de cima.'
          : 'A oferta de desfazer só o trecho expirou (vale por 30 min). Para voltar à curva de antes, use Desfazer a última gravação.';
      }
      const closing = closingText ? `<p class="refino-note">${escapeHtml(closingText)}</p>` : '';
      const history = (Array.isArray(this.eq?.refinement?.history) ? this.eq.refinement.history : []).slice().reverse().map(item => {
        const when = finite(item.appliedAt) ? new Date(item.appliedAt).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—';
        const ratios = finite(item.ratioBefore) === null ? '' : ` · GNV igual à gasolina ${pct(item.ratioBefore)}${finite(item.ratioAfter) === null ? '' : ' → ' + pct(item.ratioAfter)}`;
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
        <div><dt>Gasolina de referência</dt><dd>${escapeHtml({ ECU: 'da ECU', PROPRIA: 'medida pelo app', MISTA: 'as duas: medida pelo app e da ECU', NENHUMA: 'ainda sem referência: rode na gasolina' }[this.eq?.petrolReference] || '—')}</dd></div>
        <div><dt>De onde vem a proposta</dt><dd>${escapeHtml({ ECU_E_CONDUCAO: 'faixas da ECU + sua condução', CONDUCAO: 'só a sua condução (a ECU ainda não tem faixas maduras)', NENHUMA: 'sem evidência suficiente: nada muda' }[a.evidenceSource] || '—')}</dd></div>
        <div><dt>Faixas maduras em comum</dt><dd>${fmt(a.matureCommonPoints, 0)} de ${fmt(a.minimumMatureCommonPoints, 0)} necessárias · ${fmt(a.telemetryTargets, 0)} alvos dos pontos do OMEGAS</dd></div>
        <div><dt>Pontos da ECU descartados</dt><dd>${Array.isArray(a.rejectedBands) && a.rejectedBands.length ? a.rejectedBands.map(r => `${escapeHtml(r.fuel === 'GNV' ? 'GNV' : 'Gasolina')} faixa ${Number(r.band) + 1} (${D.msUnit(r.timeMs)} · ${D.barUnit(r.mapBar)})`).join(', ') + ' — fora da tendência; não entram no cálculo' : 'nenhum'}</dd></div>
        <div><dt>Trava</dt><dd>±${fmt(a.guards?.maximumStepPercent, 0)}% por gravação · |Δ ln K/Δ ln t| ≤ ${fmt(a.elasticityLimit, 2)}</dd></div>
        ${this.stallDetail ? `<div><dt>Motor apagou</dt><dd>${escapeHtml(this.stallDetail)}</dd></div>` : ''}
        <div><dt>AutoMatch da ECU</dt><dd>${fmt(pilot.autoMatchCount, 0)} de ${fmt(pilot.maxAutomatch, 0)} · ${escapeHtml(pilot.ecuDoneReason || 'ainda trabalhando')}</dd></div>
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

  ns.RefinoModel = { equivalenceStrip, indexPercent, WAITING_TEXT, proposedPoints, undoPoints, undoSource, primaryAction, explainPoint, ageText, safeMin, safeMax, STATUS_WORDS };
  ns.RefinoScreen = RefinoScreen;
  if (typeof document !== 'undefined') boot();
})(typeof window !== 'undefined' ? window : globalThis);
