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
    CONFIRMADA: 'GNV igual à gasolina',
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
    VERIFICADO: 'GNV igual à gasolina',
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
    else if (acquired) lines.push('Por que conta: faixa lida pela ECU; entra no cálculo junto com os pontos do OMEGAS.');
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

  /**
   * Pontos que o cérebro mandou gravar (APPLY com `currentRaw`/`refinedRaw`/`pointIndexes`, do próprio motor e dos mesmos
   * pares do veredito; local ou da curva inteira). null = a ação não traz curva (ação de outro tipo ou ponte antiga).
   */
  function brainPoints(action) {
    if (action?.kind !== 'APPLY') return null;
    const before = action.currentRaw, after = action.refinedRaw, indexes = action.pointIndexes;
    const raw = value => Number.isInteger(value) && value > 0 && value <= 65535;
    if (!Array.isArray(before) || !Array.isArray(after) || before.length !== 30 || after.length !== 30 ||
        !before.every(raw) || !after.every(raw) || !Array.isArray(indexes) || !indexes.length ||
        !indexes.every(i => Number.isInteger(i) && i >= 0 && i < 30)) return null;
    return [...new Set(indexes)].filter(i => before[i] !== after[i])
      .map(i => ({ index: i, currentRaw: before[i], targetRaw: after[i] }));
  }

  /**
   * O que o botão grava: a proposta que o cérebro autorizou (fonte única: veredito e proposta dos mesmos pares). Sem
   * curva na ação, a análise refinada do snapshot (ponte antiga); ação local sem curva válida não grava nada.
   */
  function readyPoints(eq, analysis) {
    const action = eq?.nextAction;
    const brain = brainPoints(action);
    if (brain !== null) return brain;
    if (action?.kind === 'APPLY' && action.local === true) return [];
    return proposedPoints(analysis);
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

  /**
   * O Desfazer existe quando o diário guarda a foto de antes ou o antes/depois do último experimento E ainda há o que
   * desfazer: a ECU não mudou a curva por fora depois da foto (INTERROMPIDO) e o último registro não é, ele mesmo, um desfazer.
   * `ageText` diz há quanto tempo é a foto ("foto de há 12 min"); sem hora conhecida, "—".
   */
  function undoSource(latest, now) {
    const photo = String(latest?.photoFile || '');
    const status = String(latest?.status || '');
    const partial = status === 'FALHA_PARCIAL';
    // A ECU (ou outro aparelho) reescreveu a curva: a foto não vale mais. Mapa K gravado pelo dono não muda a Curva K.
    // Falha fechada: INTERROMPIDO sem motivo conhecido = mudou por fora. Só o que o próprio dono fez (Mapa K, reinício do GNV)
    // mantém a foto válida.
    const changedByEcu = status === 'INTERROMPIDO' && !['REINICIO_GNV_PELO_DONO', 'MAPA_K_GRAVADO'].includes(String(latest?.interruptReason || ''));
    // Depois de um Desfazer não há o que desfazer (senão virava "refazer"); o diário marca `restore`, o `source` é só o id.
    const wasUndo = latest?.restore === true || /desfazer|restaurar/i.test(String(latest?.source || ''));
    const has = Boolean(photo) || undoPoints(latest).length > 0;
    const available = has && (partial || (!changedByEcu && !wasUndo));
    const at = finite(latest?.appliedAt);
    return { photoFile: photo, available, ageText: at === null ? '—' : ageText(at, now === undefined ? Date.now() : now), changedByEcu };
  }

  const ROUTE_NAMES = { map: 'Mapa K', curve: 'Curva K', autocal: 'AutoCal', refino: 'Ajuste GNV', sessions: 'Sessões', tools: 'Ferramentas' };
  /**
   * Índice de equivalência: o Kotlin manda `index` como NÚMERO escalar 0..1 (percentual = ×100), com `coverage` e
   * `provisional` irmãos planos (EquivalenceJson.result). Sem número válido: null (nunca 0%).
   */
  function indexPercent(eq) {
    const raw = eq?.index;
    const value = typeof raw === 'number' ? finite(raw) : null;
    return value === null ? null : Math.round(Math.max(0, Math.min(1, value)) * 100);
  }
  /**
   * Faixa única do Refino (D1): "N% da sua condução já equivale à gasolina" + UMA próxima ação (a do cérebro, eq.nextAction) + o botão de
   * um toque quando a ação aponta para outra aba. Só mostra e leva; nunca executa. Sem dado do cérebro: aviso neutro,
   * sem ação (a UI não deriva ação da fase).
   */
  const WAITING_TEXT = 'Aguardando dados da ECU';
  const FREEZE_TEXT = 'Salvar a curva atual da ECU como referência';
  const FREEZE_WHY = 'Serve de régua para comparar o GNV com a gasolina.';
  function equivalenceStrip(eq, routes) {
    const percent = indexPercent(eq);
    const action = eq?.nextAction || null;
    const text = action && action.text ? String(action.text) : '';
    const route = action && action.route && action.route !== 'refino' && (!routes || routes.includes(action.route)) ? String(action.route) : '';
    // Ações que o próprio Refino executa (nunca navegam para um lugar sem o botão): congelar a Referência (um toque, não grava a ECU),
    // revisar e gravar (APPLY → o botão principal do cabeçalho) e desfazer (CONTESTED → Desfazer a gravação).
    const kind = action && action.kind ? String(action.kind) : '';
    const act = kind === 'FREEZE_REFERENCE' ? 'freeze' : kind === 'APPLY' ? 'review' : kind === 'CONTESTED' ? 'undo' : '';
    const ACT_LABEL = { freeze: 'Salvar como referência', review: 'Gravar', undo: 'Desfazer a gravação' };
    return {
      indexText: percent === null ? '— da condução já equivale à gasolina' : `${percent}% da condução já equivale à gasolina${eq.provisional === true ? ' · provisório' : ''}`,
      nextText: act === 'freeze' ? FREEZE_TEXT : (text || WAITING_TEXT),
      whyText: act === 'freeze' ? FREEZE_WHY : '',
      hasAction: Boolean(text),
      act,
      actLabel: act ? ACT_LABEL[act] : '',
      route,
      subpage: route ? String(action.subpage || '') : '',
      routeLabel: route ? `Ir para ${ROUTE_NAMES[route] || route}` : '',
    };
  }

  /** Ação principal conforme a fase do piloto (uma só por vez). */
  function primaryAction(eq, analysis) {
    const phase = eq?.autopilot?.phase || 'SEM_ECU';
    const proposal = readyPoints(eq, analysis).length;
    const local = eq?.nextAction?.kind === 'APPLY' && eq.nextAction.local === true;
    if (local) {
      const blocked = ['SEM_ECU', 'LENDO_ECU', 'ECU_TRABALHANDO', 'VERIFICANDO'].includes(phase);
      if (blocked || eq?.refinoState?.canAct !== true || !proposal) return { kind: 'none', label: '' };
      return { kind: 'review', label: `Gravar ${D.plural(proposal, 'ponto', 'pontos')}` };
    }
    // A proposta do cérebro vale por si; a análise do snapshot só precisa estar disponível quando é ela a fonte.
    const available = brainPoints(eq?.nextAction) !== null || analysis?.available;
    if (phase === 'TENTATIVA_ENCERRADA') {
      // O prazo da tentativa pausa o acompanhamento, não a proposta: ela continua válida e o botão continua.
      const from = eq?.autopilot?.expiredFrom;
      if ((from === 'PROPOSTA_PRONTA' || from === 'ECU_TRABALHANDO') && proposal && available) {
        return { kind: 'review', label: `Gravar ${D.plural(proposal, 'ponto', 'pontos')}`, expired: true };
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
    if (proposal && available) {
      return { kind: 'review', label: `Gravar ${D.plural(proposal, 'ponto', 'pontos')}` };
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
      this.gasResetPending = false;
      // A foto de antes de cada gravação vive no diário (photoFile do ÚLTIMO experimento): o Desfazer restaura
      // exatamente a do último, mesmo que o anterior tenha sido gravado por outra aba.
      this.lastRenderKey = '';
      // A bolinha AGORA do Refino mora só no quadro do Refino: o AutoCal também tem uma (outro quadro, outro nó).
      this.cursor = new ns.LiveStore.EaseCursor(() => this.liveLayer());
      this.inject();
      // Evidência, tabelas e sessão só são relidas quando a revisão do tipo andou (ou o vigia vence); ocupado relê sempre.
      const revisions = ns.Revisions;
      this.dataGate = revisions ? revisions.gate(revisions.SLOW_KINDS, revisions.WATCHDOG_MS) : { due: () => true, mark() {} };
      this.dataDirty = false;
      this.unsubscribeRevisions = revisions ? revisions.subscribe(kind => { if (kind !== 'live') this.dataDirty = true; }) : () => {};
      this.unsubscribeStatus = this.scheduler.addHook('status', () => {
        if (this.store.get().route !== 'refino') return;
        this.renderUndo();
        const stallNow = document.getElementById('refinoStalls');
        if (stallNow && !stallNow.hidden && stallNow.dataset.tone !== this.stallTone()) stallNow.dataset.tone = this.stallTone();
        if (this.dataGate.due(false)) { this.refresh(); this.dataGate.mark(); }
      });
      this.unsubscribeFast = this.scheduler.addHook('fast', () => {
        this.tickJob();
        if (this.store.get().route !== 'refino') { this.releaseFrame(); return; }
        this.ensureFrame();
        // Leitura nova chega no tick rápido: o laço de quadros pode estar dormindo (cursor parado no alvo).
        this.updateLiveTarget();
        if (this.enterRefreshPending) { this.enterRefreshPending = false; this.refresh(true); this.dataGate.mark(); this.dataDirty = false; }
        else if (this.dataDirty) { this.dataDirty = false; this.refresh(); this.dataGate.mark(); }
      });
      // O cursor AGORA anda no quadro de animação (rAF do scheduler), sem redesenhar o gráfico; só enquanto a aba está aberta.
      this.unsubscribeFrame = null;
      // Ao entrar na aba, desenha na hora (sem esperar o próximo tick).
      // A releitura ao entrar vem do app (refreshNow, uma vez só); aqui só quando a tela nasce já aberta.
      let lastRoute = null;
      this.enterRefreshPending = this.store.get().route === 'refino';
      this.store.subscribe(state => {
        if (state.route === lastRoute) return;
        lastRoute = state.route;
        if (state.route === 'refino') this.ensureFrame(); else this.releaseFrame();
      }, true);
    }

    inject() {
      const host = document.getElementById('refinoScreenHost');
      if (!host || host.querySelector('.autocal-cockpit')) return;
      host.innerHTML = `
        <section class="autocal-cockpit refino-cockpit ar-shell ar-refino" aria-label="Refino OMEGAS">
          <section class="ar-chart-card" aria-label="Curva de aquisição · Gasolina × GNV">
            <div class="ar-legend-row"><div class="ar-legend" id="refinoLegend" aria-label="Legenda do gráfico"></div><span id="refinoPhaseChip" class="ar-fuel autocal-fuel-chip" data-fuel-state="unknown">—</span></div>
            <div class="refino-stalls ar-stall" id="refinoStalls" hidden></div>
            <div id="refinoChart" class="ar-chart-host"><div class="chart-empty">Aguardando a leitura da ECU.</div></div>
            <div class="ar-readout" id="refinoInspector" data-empty="true"><span>Toque num ponto do gráfico.</span></div>
          </section>
          <div class="ar-act">
            <div class="refino-statusline" aria-live="polite"><p id="refinoHeadline" class="ar-sentence" data-level="neutral">Aguardando dados da ECU</p><p id="refinoNext" class="ar-reason" hidden></p></div>
            <div class="ar-buttons">
              <div class="instrument-menus"><details class="instrument-details refino-proposals"><summary>Sugestões</summary><div class="instrument-detail-content" id="refinoProposals">Ainda sem proposta. O app continua medindo.</div></details>
            <details class="instrument-details refino-details"><summary>Ver detalhes</summary><div class="instrument-detail-content"><p><small>Diferença GNV × gasolina</small><b id="refinoRatio">—</b></p><p id="refinoDetailCounts">Aguardando medição</p><p id="refinoDetailReason"></p>${ns.CurveChart.viewControls()}<section class="refino-evidence-options"><h3>Aprendizado do GNV</h3><p>Descarta apenas as medições de GNV do OMEGAS. Mantém a gasolina como referência e a calibração da ECU. As medições descartadas não podem ser desfeitas.</p></section></div></details></div>
              <button type="button" class="btn-primary" data-refino-primary hidden></button>
              <button type="button" class="btn-ghost" data-refino-reset-gas>Apagar medições do app (GNV)</button>
              <button type="button" class="btn-ghost" data-refino-acquisition>Ver aprendizado da ECU</button>
              <span class="refino-undo" id="refinoUndo" hidden></span>
              <button type="button" class="btn-ghost" id="refinoEqUnfreeze" data-refino-unfreeze hidden>Desfazer referência</button>
            </div>
            <div class="refino-confirm" id="refinoResetConfirm" role="alertdialog" aria-label="Confirmar apagar medições do GNV" hidden>
              <p>Apagar tudo que o app mediu no GNV? Não dá para desfazer.</p>
              <button type="button" class="btn-danger" data-refino-reset-confirm>Apagar</button>
              <button type="button" class="btn-ghost" data-refino-reset-cancel>Cancelar</button>
            </div>
          </div>
        </section>`;
      host.addEventListener('click', event => this.onClick(event));
      ns.CurveChart.bindView(host, this, () => this.renderChart());
    }

    refresh(force, fresh) {
      if (this.operation.phase === 'reading' || this.operation.phase === 'writing') return;
      if (!this.api?.available?.()) { this.renderUnavailable(); return; }
      // O Kotlin já deixa o resultado pronto em segundo plano; "fresco" só depois de gravar/desfazer.
      this.eq = (fresh === true ? this.api.equivalenceFresh?.() : this.api.equivalence?.()) || null;
      if (this.gasResetPending && (this.eq?.gasObservations === 0 || this.eq?.gasEpochReason === 'REINICIO_GNV_PELO_DONO') && this.eq?.refinoState?.canAct === false) this.gasResetPending = false;
      this.analysis = this.api.refinedAnalysis?.() || null;
      const projection = this.api.projection?.() || {};
      this.projection = projection.ok === true ? projection : {};
      this.snapshot = projection.ok === true ? (projection.snapshot || {}) : {};
      // O armazém único da evidência também alimenta o AutoCal: ele não busca de novo o que o Refino já leu.
      ns.CurveChart?.setEvidence(this.eq, this.analysis, Date.now());
      this.render(force === true);
    }

    /** UMA releitura ao entrar na aba (o app chama depois do primeiro quadro pintado). */
    refreshNow() {
      this.enterRefreshPending = false;
      this.dataDirty = false;
      this.refresh(true);
      this.dataGate.mark();
    }

    onClick(event) {
      // Apagar as medições do GNV não tem volta: pede confirmação antes.
      if (event.target.closest('[data-refino-reset-gas]')) { this.askResetGas(true); return; }
      if (event.target.closest('[data-refino-reset-cancel]')) { this.askResetGas(false); return; }
      if (event.target.closest('[data-refino-reset-confirm]')) { this.askResetGas(false); this.resetGasEvidence(); return; }
      if (event.target.closest('[data-refino-acquisition]')) { this.app.router?.open('autocal'); return; }
      if (event.target.closest('[data-refino-unfreeze]')) { this.unfreezeReference(); return; }
      if (event.target.closest('[data-refino-primary]')) { this.primary(); return; }
      if (event.target.closest('[data-refino-undo]')) { this.openUndo(); return; }
      const dot = event.target.closest('[data-refino-dot]');
      if (dot) { this.inspect(dot.dataset.refinoDot); return; }
      // Ponto da curva de referência (área de toque do gráfico compartilhado).
      const reference = event.target.closest('[data-autocal-ref-index]');
      if (reference) this.inspectReference(Number(reference.dataset.autocalRefIndex));
    }

    /** Mostra/esconde a confirmação "Apagar tudo que o app mediu no GNV? Não dá para desfazer." */
    askResetGas(open) {
      const box = document.getElementById('refinoResetConfirm');
      if (!box) return;
      box.hidden = !open;
      const trigger = document.querySelector('[data-refino-reset-gas]');
      if (trigger) trigger.hidden = !!open;
    }

    /** Linha curta ao tocar; sem card, sem código. */
    readout(text) {
      const host = document.getElementById('refinoInspector');
      if (!host) return;
      const line = String(text || '').trim();
      host.dataset.empty = line ? 'false' : 'true';
      host.innerHTML = `<span>${escapeHtml(line || 'Toque num ponto do gráfico.')}</span>`;
    }

    inspectReference(index) {
      const point = ((this.model || {}).reference || []).find(item => Number(item.index) === Number(index));
      if (!point || finite(point.petrolMs) === null) return;
      this.readout(`Trecho ${Number(point.index) + 1} da curva · ${D.msUnit(point.petrolMs)} de injeção`);
      this.select({ ref: Number(point.index) });
    }

    select(sel) {
      this.selected = sel || {};
      ns.CurveChart?.applySelection(this.selected, 'between');
    }

    /** Congelar a Referência: não escreve na ECU. Um toque; o resultado fica à vista e o Desfazer volta à anterior. */
    resetGasEvidence() {
      if (this.operation.phase === 'reading' || this.operation.phase === 'writing') return;
      const result = this.api.resetGasEvidence?.() || { ok: false, message: 'Reinício do aprendizado indisponível.' };
      this.gasRestartNotice = { phase: result.ok === true ? 'done' : 'failed',
        message: result.message || (result.ok === true ? 'Aprendizado GNV reiniciado. A gasolina continua como referência.' : 'Não foi possível reiniciar agora.'), at: Date.now() };
      if (result.ok === true) { this.gasResetPending = true; this.selected = {}; this.readout(''); ns.CurveChart?.reset(); }
      this.refresh(true, true);
    }

    freezeReference() {
      const result = this.api.freezeReference?.() || { ok: false };
      this.freeze = result.ok === true
        ? { phase: 'done', message: 'Referência congelada.', at: Date.now() }
        : { phase: 'failed', message: String(result.message || 'Não consegui congelar agora. A ECU ainda não tem curva de gasolina madura; rode um pouco na gasolina e toque de novo.'), at: Date.now() };
      this.refresh(true, true);
    }

    /** Desfazer do congelamento: volta à Referência anterior desta sessão (só existe quando havia uma). */
    unfreezeReference() {
      const result = this.api.restorePreviousReference?.() || { ok: false };
      this.freeze = result.ok === true
        ? { phase: 'undone', message: 'Referência anterior restaurada.', at: Date.now() }
        : { phase: 'failed', message: String(result.message || 'Não há referência anterior nesta sessão.'), at: Date.now() };
      this.refresh(true, true);
    }

    /**
     * UMA ação primária que diz o que vai fazer (verbo do efeito). Só existe quando o app pode executá-la agora;
     * senão a frase de estado diz o motivo (nunca um botão desabilitado sem explicação).
     */
    actionModel() {
      const eq = this.eq || {};
      const rs = eq.refinoState && typeof eq.refinoState === 'object' ? eq.refinoState : null;
      const said = rs && typeof rs.nextAction === 'string' && rs.nextAction.trim() ? rs.nextAction.trim() : '';
      const frozenNow = this.freeze && this.freeze.phase === 'done' && Date.now() - this.freeze.at < 10000;
      // refinoState decide se existe ação; uma proposta antiga não transforma "seguir dirigindo" em gravação.
      if (this.gasResetPending || (rs && rs.canAct !== true)) return { kind: 'none', label: '' };
      if (eq.nextAction?.kind === 'FREEZE_REFERENCE') return frozenNow ? { kind: 'none', label: '' } : { kind: 'freeze', label: said || 'Salvar a gasolina como referência' };
      const action = primaryAction(eq, this.analysis);
      if (action.kind === 'review') return { kind: 'review', label: said || 'Aplicar ajuste' };
      if (action.kind === 'restore') return { kind: 'restore', label: said || 'Desfazer o trecho que piorou' };
      const strip = equivalenceStrip(eq, ns.ROUTES);
      if (action.kind === 'none' && strip.act === 'freeze' && !frozenNow) return { kind: 'freeze', label: said || 'Salvar a gasolina como referência' };
      if (action.kind === 'none' && strip.route) return { kind: 'route', label: said || strip.routeLabel, route: strip.route, subpage: strip.subpage };
      return { kind: 'none', label: '' };
    }

    primary() {
      if (this.operation.phase === 'reading' || this.operation.phase === 'writing') return;
      const action = this.actionModel();
      if (action.kind === 'review') this.openReview('apply');
      else if (action.kind === 'restore') this.openReview('restore');
      else if (action.kind === 'freeze') this.freezeReference();
      else if (action.kind === 'route') this.app.router?.open(action.route, action.subpage || '');
    }

    /** Há o que desfazer? Só com foto/antes-depois do último experimento E sem a ECU ter mudado a curva por fora (ver undoSource). */
    undoAvailable() {
      const partialNow = this.operation?.phase === 'failed' && this.operation.partial === true && Boolean(this.operation.photoFile);
      return partialNow || undoSource(this.eq?.refinement?.latest).available;
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
      if (kind === 'apply') { points = readyPoints(this.eq, this.analysis); title = 'Gravar curva refinada'; reason = 'Refino OMEGAS: curva refinada confirmada'; }
      if (kind === 'restore') { points = (this.eq?.restorePoints || []).map(p => ({ index: Number(p.index), currentRaw: Number(p.currentRaw), targetRaw: Number(p.targetRaw) })); title = 'Restaurar trecho que piorou'; reason = 'Refino OMEGAS: restaurar trecho que piorou'; }
      if (kind === 'undo') { points = undoPoints(this.eq?.refinement?.latest); title = 'Desfazer última gravação'; reason = 'Refino OMEGAS: desfazer última gravação'; }
      this.showReview(points, title, reason, '');
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

    /** Um toque grava: sem diálogo de confirmação (a proteção é a foto antes + Desfazer depois). */
    showReview(points, title, reason, restoreFile) {
      if (!points.length) return;
      this.runWrite(points, reason, restoreFile || '');
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
            this.operation = { phase: 'done', at: Date.now() };
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
      this.operation = { phase: 'failed', message: String(message || 'Falha'), at: Date.now(), ...(extra || {}) };
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
      // Resultado de gravação some sozinho (sem botão "Entendi"): a frase volta ao estado normal.
      if ((this.operation.phase === 'done' || this.operation.phase === 'failed') && Date.now() - (this.operation.at || 0) > 15000) this.operation = { phase: 'idle' };
      const op = this.operation;
      const rs = eq.refinoState && typeof eq.refinoState === 'object' ? eq.refinoState : null;
      const chart = ns.CurveChart;
      const key = [phase, pilot.expiredFrom, eq.refinement?.latest?.photoFile, eq.refinement?.latest?.status, pilot.petrolValid, pilot.gasValid,
        eq.ratio, eq.index, brainPoints(eq.nextAction) ? readyPoints(eq, this.analysis).map(p => [p.index, p.currentRaw, p.targetRaw].join(':')).join(',') : '', eq.nextAction?.text, eq.nextAction?.route, op.phase, op.message, op.progress, this.gasResetPending, eq.stalls?.count, eq.stalls?.nearCount, this.stallTone(), eq.gasEpochAt,
        rs ? [rs.phase, rs.label, rs.whatNow, rs.nextAction, rs.canAct, rs.reason, [rs.counts?.intervalsTotal,rs.counts?.intervalsCollected,rs.counts?.pointsToWrite].join(':'),rs.whyNoProposal].join('~') : '', this.freeze ? this.freeze.phase + this.freeze.at : '', op.phase === 'idle' ? '' : Math.floor(Date.now() / 5000),
        chart ? chart.evidenceSignature({ snapshot: this.snapshot, eq, analysis: this.analysis, sessionId: this.projection?.sessionId, extra: this.sizeKey() }) : ''].join('|');
      if (!force && key === this.lastRenderKey) return;
      this.lastRenderKey = key;

      const chip = document.getElementById('refinoPhaseChip');
      if (chip) {
        const readyAfterExpiry = phase === 'TENTATIVA_ENCERRADA' && pilot.expiredFrom === 'PROPOSTA_PRONTA';
        chip.dataset.fuelState = (readyAfterExpiry ? 'ready' : PHASE_TONE[phase]) || 'unknown';
        // A frase e o chip usam o mesmo contrato; um piloto antigo não mascara uma leitura pendente. O rótulo curto vem do
        // Kotlin (`label`), que sabe em que combustível o motor está agora: na gasolina ele diz "Medindo a gasolina", nunca "o GNV".
        const human = rs?.phase;
        const label = typeof rs?.label === 'string' && rs.label.trim() ? rs.label.trim() : '';
        chip.textContent = label || (human ? (/^Coletando/.test(human) ? 'Medindo' : /^Pronto para gravar/.test(human) ? 'Curva pronta' : human) : D.phaseLabel(phase, pilot.expiredFrom));
      }
      const currentEvidence = !['SEM_ECU', 'LENDO_ECU', 'TENTATIVA_ENCERRADA'].includes(phase);
      setText('refinoRatio', pct(currentEvidence ? eq.ratio : null));
      setText('refinoDetailCounts', rs?.counts ? `${rs.counts.intervalsCollected ?? '—'} de ${rs.counts.intervalsTotal ?? '—'} regiões medidas · ${rs.counts.pointsToWrite ?? '—'} trechos da curva com ajuste sugerido` : 'Aguardando medição');
      setText('refinoDetailReason', rs?.whyNoProposal || rs?.reason || '');
      const proposals = document.getElementById('refinoProposals');
      if (proposals) {
        const points = this.actionModel().kind === 'review' ? readyPoints(eq, this.analysis) : [];
        proposals.innerHTML = points.length ? `<p>${D.plural(points.length, 'trecho', 'trechos')} da curva · confira o efeito antes de aplicar.</p><dl>${points.map(p => `<div><dt>${escapeHtml(this.stretchLabel(p.index))}</dt><dd>${escapeHtml(this.changeLabel(p))}</dd></div>`).join('')}</dl><p>Aplicar guarda a cópia anterior e confere a gravação na ECU. Desfazer restaura essa cópia.</p>` : '<p>Ainda sem proposta. O app continua medindo.</p>';
      }
      const resetGas = document.querySelector('[data-refino-reset-gas]');
      if (resetGas) resetGas.disabled = op.phase === 'reading' || op.phase === 'writing' || this.store.get()?.status?.usbConnected !== true;
      this.renderStalls();
      this.renderSentence(rs, phase, pilot);
      this.renderPrimary();
      this.renderUndo();
      this.renderChart();
    }

    /** "Trecho 3,2–3,6 ms": o trecho da curva entre este ponto e o seguinte (injeção de gasolina). Sem eixo: "Trecho N". */
    stretchLabel(index) {
      const byIndex = new Map((Array.isArray(this.analysis?.points) ? this.analysis.points : []).map(p => [Number(p.index), p]));
      const reference = (this.model && Array.isArray(this.model.reference)) ? this.model.reference : [];
      const msOf = i => finite(byIndex.get(i)?.referenceTimeMs) ?? finite(reference.find(r => Number(r.index) === i)?.petrolMs);
      const from = msOf(index);
      const to = msOf(index + 1);
      if (from === null) return `Trecho ${index + 1}`;
      return to === null || to <= from ? `Trecho ${fmt(from, 1)} ms` : `Trecho ${fmt(from, 1)}–${fmt(to, 1)} ms`;
    }

    /** "+4% de GNV" / "−3% de GNV": quanto a mistura do GNV muda nesse trecho. */
    changeLabel(point) {
      const change = point.currentRaw > 0 ? point.targetRaw / point.currentRaw - 1 : null;
      if (change === null || !Number.isFinite(change)) return '—';
      const percent = Math.round(change * 100);
      if (percent === 0) return 'ajuste fino (menos de 1%)';
      return `${percent > 0 ? '+' : '−'}${Math.abs(percent)}% de GNV`;
    }

    renderStalls() {
      const stalls = (this.eq || {}).stalls || {};
      const stallNode = document.getElementById('refinoStalls');
      if (!stallNode) return;
      const real = finite(stalls.count) ?? 0;
      const near = finite(stalls.nearCount) ?? 0;
      stallNode.hidden = !(real > 0 || near > 0);
      stallNode.dataset.tone = this.stallTone();
      const region = Array.isArray(stalls.regions) ? stalls.regions[0] : null;
      // Em vez de mandar o dono mexer na Curva K: o próprio Refino cuida da região (a faixa técnica fica no título).
      const where = region ? `Mais perto de ${D.msBand(region.fromMs, region.toMs)}. ` : '';
      this.stallDetail = (real > 0 || near > 0) ? 'O Refino vai propor um ajuste para essa região. Continue dirigindo.' : '';
      stallNode.dataset.where = where.trim();
      stallNode.title = this.stallDetail;
      const base = real > 0 ? `O motor apagou ${real === 1 ? '1 vez' : real + ' vezes'} no GNV` : near > 0 ? 'O motor quase apagou no GNV' : '';
      const text = base ? `${base}. ${this.stallDetail}` : '';
      if (stallNode.textContent !== text) stallNode.textContent = text;
    }

    /** UMA frase humana de estado + o motivo quando não há ação. Sem contagens, minutos ou visitas. */
    renderSentence(rs, phase, pilot) {
      const op = this.operation;
      const action = this.actionModel();
      const frz = this.freeze && Date.now() - this.freeze.at < 10000 ? this.freeze : null;
      let level = 'neutral';
      let text = '';
      let reason = '';
      if (op.phase === 'done') { text = 'Gravado e conferido na ECU.'; level = 'ok'; }
      else if (op.phase === 'failed') {
        text = op.message; level = 'error';
        reason = op.partial ? 'Parte dos pontos pode ter sido gravada. Toque em Desfazer para voltar à foto de antes.' : 'Nada foi gravado: a ECU manteve a curva anterior.';
      }
      else if (op.phase === 'reading') text = 'Lendo a curva da ECU…';
      else if (op.phase === 'writing') text = 'Gravando na ECU…';
      else if (this.gasRestartNotice && Date.now() - this.gasRestartNotice.at < 10000) { text = this.gasRestartNotice.message; level = this.gasRestartNotice.phase === 'failed' ? 'warn' : 'ok'; }
      else if (frz) { text = frz.message; level = frz.phase === 'failed' ? 'warn' : 'ok'; }
      else {
        const strip = equivalenceStrip(this.eq, ns.ROUTES);
        text = (rs && (rs.whatNow || rs.phase)) || (strip.hasAction ? strip.nextText : '') || strip.nextText;
        if (phase === 'ESTAVEL') level = 'ok';
        if (phase === 'TENTATIVA_ENCERRADA' || phase === 'RESTAURAR_TRECHO') level = 'warn';
        const closing = this.eq?.refinement?.latest?.status;
        if (closing === 'FALHA_PARCIAL' || closing === 'INTERROMPIDO') { reason = closing === 'INTERROMPIDO' && this.eq?.refinement?.latest?.interruptReason === 'REINICIO_GNV_PELO_DONO' ? 'A verificação foi interrompida pelo reinício das medições do GNV. A curva e a foto anterior continuam disponíveis.' : JOURNAL_NOTE[closing]; level = 'warn'; }
        else if (action.kind === 'freeze') reason = strip.whyText;
        else if (action.kind === 'none') reason = (rs && (rs.nextAction || rs.reason)) || '';
        else if (action.kind === 'review' && (pilot.phase === 'TENTATIVA_ENCERRADA' ? pilot.expiredFrom : pilot.phase) === 'ECU_TRABALHANDO') reason = 'A ECU ainda está no automático e pode sobrescrever este ajuste.';
      }
      const node = document.getElementById('refinoHeadline');
      if (node) { if (node.textContent !== text) node.textContent = text; node.dataset.level = level; }
      const why = document.getElementById('refinoNext');
      if (why) { why.hidden = !reason; if (why.textContent !== reason) why.textContent = reason; }
    }

    renderPrimary() {
      const button = document.querySelector('[data-refino-primary]');
      if (!button) return;
      const op = this.operation;
      if (op.phase === 'reading' || op.phase === 'writing') { this.renderOperation(); return; }
      const action = this.actionModel();
      button.hidden = action.kind === 'none';
      button.disabled = false;
      button.dataset.kind = action.kind;
      if (button.textContent !== action.label) button.textContent = action.label;
      const unfreeze = document.getElementById('refinoEqUnfreeze');
      if (unfreeze) unfreeze.hidden = !(op.phase === 'idle' && this.freeze && this.freeze.phase === 'done' && this.eq?.reference?.previousId);
    }

    /** Um único Desfazer, discreto, só quando há o que desfazer (ao lado do feito). Sem cartão, sem letras miúdas. */
    renderUndo() {
      const host = document.getElementById('refinoUndo');
      if (!host) return;
      const busy = this.operation.phase === 'reading' || this.operation.phase === 'writing';
      const available = !busy && this.undoAvailable();
      host.hidden = !available;
      if (!available) { if (host.innerHTML) host.innerHTML = ''; return; }
      if (!host.querySelector('[data-refino-undo]')) host.innerHTML = '<button type="button" class="btn-ghost" data-refino-undo>Desfazer</button>';
    }

    /** Tamanho do quadro do gráfico: faz parte da assinatura (outro tamanho = outro desenho). */
    sizeKey() {
      const host = document.getElementById('refinoChart');
      const w = host?.clientWidth || 0;
      const h = host?.clientHeight || 0;
      return `between|${Math.round(w / 16)}x${Math.round(h / 16)}|${ns.CurveChart.viewKey(this.chartView)}`;
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
      const input = { snapshot: this.snapshot, projection: this.projection || {}, eq, analysis: this.analysis, mode: 'between', view: this.chartView };
      chart.mount(host, signature, () => {
        const model = chart.buildModel(input);
        if (!model || !model.domain || (!model.reference.length && !model.ecu.length && !model.betweenPoints.length)) {
          return { html: '<div class="chart-empty"><b>SEM PONTOS AINDA</b><span>Rode na gasolina e no GNV com a ECU conectada. Os pontos da ECU e os nossos aparecem aqui.</span></div>', scale: null, model: null };
        }
        const built = chart.buildSvg(model, { width, height, mode: 'between', selected: this.selected });
        return built.empty ? { html: '<div class="chart-empty"><b>SEM PONTOS AINDA</b></div>', scale: null, model: null } : { ...built, model };
      }, 'between');
      const shown = chart.shared;
      this.chartScale = shown.scale;
      this.model = shown.model;
      this.chartSignature = signature;
      const legend = document.getElementById('refinoLegend');
      const flags = { mode: 'between', proposal: false, stall: !!(this.model && this.model.stalls.length), missing: !!(this.model && this.model.betweenPoints.some(b => b.state === 'missing')) };
      const legendKey = `between|${flags.stall}|${flags.missing}`;
      if (legend && this.legendKey !== legendKey) { this.legendKey = legendKey; legend.innerHTML = chart.legendHtml(flags); }
    }

    /** 'now' só com o motor apagado de verdade AGORA; RPM desconhecido ou velho nunca vira "apagado". */
    stallTone() {
      const state = this.store.get();
      if (state?.status?.usbConnected !== true) return 'history';
      const rpmNow = ns.LiveStore.read(state).rpm;
      return rpmNow !== null && rpmNow < 300 ? 'now' : 'history';
    }

    /** Cursor AGORA: só calcula o alvo a partir da leitura viva única (LiveStore); quem move é o quadro de animação. */
    renderLive() {
      const scale = this.chartScale;
      const layer = this.liveLayer();
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

    /** Camada AGORA do gráfico do Refino (nunca a do AutoCal, que vem antes no documento). */
    liveLayer() {
      const host = document.getElementById('refinoChart');
      return host && typeof host.querySelector === 'function' ? host.querySelector('[data-chart-live]') : null;
    }

    ensureFrame() {
      if (!this.unsubscribeFrame && typeof this.scheduler.addFrameHook === 'function') this.unsubscribeFrame = this.scheduler.addFrameHook(timestamp => this.animateLive(timestamp));
    }

    releaseFrame() {
      if (this.unsubscribeFrame) { this.unsubscribeFrame(); this.unsubscribeFrame = null; this.cursor.frameAt = null; }
    }

    /** Quadro de animação (rAF do scheduler): só na aba Refino; o alvo só é recalculado quando chega leitura nova. */
    animateLive(timestamp) {
      if (this.store.get().route !== 'refino') { this.cursor.frameAt = null; return false; }
      this.updateLiveTarget();
      return this.cursor.frame(timestamp);
    }

    /** Alvo do cursor: só recalcula quando chega leitura nova (ou o gráfico mudou); alvo novo acorda o laço de quadros. */
    updateLiveTarget() {
      const telemetry = this.store.get().telemetry || {};
      const key = `${telemetry.sequence}|${Math.round((telemetry.telemetryAgeMs || 0) / 500)}|${this.chartSignature}`;
      if (key === this.liveKey) return;
      this.liveKey = key;
      this.renderLive();
      this.scheduler?.wake?.();
    }

    inspect(token) {
      const [kind, raw] = String(token || '').split(':');
      const index = Number(raw);
      const model = this.model || {};
      if (kind === 'ourb') {
        const b = (model.betweenPoints || [])[index];
        if (!b) return;
        this.readout(ns.CurveChart.describeBetween(b));
        this.select({ our: `b:${index}` });
        return;
      }
      const p = (model.ecu || [])[index];
      const coherent = p && finite(p.petrolMs) !== null && finite(p.mapBar) !== null;
      if (!coherent) { this.readout(''); return; }
      this.readout(`Ponto ${p.point} ${p.fuel === 'GAS' ? 'do GNV' : 'da gasolina'} (zona ${p.zone}) · ${p.acquisitionState === 'ACQUIRED' ? 'a ECU já aprendeu' : 'a ECU ainda está aprendendo'}`);
      this.select({ ecu: `${p.fuel}:${p.index}` });
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

  ns.RefinoModel = { equivalenceStrip, indexPercent, WAITING_TEXT, proposedPoints, brainPoints, readyPoints, undoPoints, undoSource, primaryAction, explainPoint, ageText, safeMin, safeMax, STATUS_WORDS };
  ns.RefinoScreen = RefinoScreen;
  if (typeof document !== 'undefined') boot();
})(typeof window !== 'undefined' ? window : globalThis);
