(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  const AUTO_CAL_LIVE_STALE_MS = 2500;

  function finite(value) {
    if (value === null || value === undefined || value === '') return null;
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[char]));
  }
  function field(snapshot, key) {
    const fields = Array.isArray(snapshot?.fields) ? snapshot.fields : [];
    return fields.find(item => String(item?.key || '') === key && String(item?.status || '') === 'VALID') || null;
  }
  function vector(snapshot, key) {
    const item = field(snapshot, key);
    return Array.isArray(item?.rawValues) ? item.rawValues.map(value => finite(value) ?? 0) : [];
  }
  function actionLabel(action) {
    return ({
      ENABLE_AUTO_CAL: 'Habilitar Auto Calibration',
      DISABLE_AUTO_CAL: 'Pausar Auto Calibration',
      RESET_PETROL: 'Resetar aquisição gasolina',
      RESET_GAS: 'Resetar aquisição GNV',
    })[action] || action;
  }

  function physicalVector(snapshot, key) {
    const item = field(snapshot, key);
    return Array.isArray(item?.physicalValues) ? item.physicalValues.map(value => finite(value)) : [];
  }

  function zoneForBand(index) {
    if (index <= 5) return 0;
    if (index <= 9) return 1;
    if (index <= 13) return 2;
    return 3;
  }

  function nativeZoneFlags(snapshot, key) {
    const values = vector(snapshot, key).slice(0, 4);
    return Array.from({ length: 4 }, (_, index) => (finite(values[index]) ?? 0) > 0);
  }

  function nativeZoneCount(snapshot, key) {
    return nativeZoneFlags(snapshot, key).filter(Boolean).length;
  }

  function projectedZoneFlags(projection, fuel) {
    const zones = projection?.ok === true && projection?.acquisitionZones && typeof projection.acquisitionZones === 'object'
      ? projection.acquisitionZones[fuel]
      : null;
    if (!Array.isArray(zones)) return null;
    return Array.from({ length: 4 }, (_, index) => zones[index] === true);
  }

  function scalarValue(snapshot, key) {
    const item = field(snapshot, key);
    const values = Array.isArray(item?.rawValues) ? item.rawValues : [];
    return values.length === 1 ? finite(values[0]) : null;
  }

  const AutoCalUxModel = {
    humanState(snapshot = {}, state = {}, projection = {}) {
      const nativeSnapshot = state.latestSnapshot?.fields ? state.latestSnapshot : {};
      const evidenceSnapshot = nativeSnapshot.fields ? nativeSnapshot : snapshot;
      const enabled = finite(state.autoCalEnabled ?? nativeSnapshot.autoCalEnabled ?? scalarValue(nativeSnapshot, 'AUTO_CAL_ENABLE'));
      const petrolZoneFlags = projectedZoneFlags(projection, 'petrol')
        ?? nativeZoneFlags(evidenceSnapshot, 'ACQUIRED_ZONES_PETROL');
      const gasZoneFlags = projectedZoneFlags(projection, 'gas')
        ?? nativeZoneFlags(evidenceSnapshot, 'ACQUIRED_ZONES_GAS');
      const petrolZones = petrolZoneFlags.filter(Boolean).length;
      const gasZones = gasZoneFlags.filter(Boolean).length;
      const nativeStatus = nativeSnapshot.nativeStatus || {};
      const autoMatchCount = finite(state.autoMatchCount ?? nativeStatus.autoMatchCount ?? scalarValue(nativeSnapshot, 'NUM_AUTOMATCH_EXECUTED'));
      const maxAutoMatch = finite(state.maxAutomatch ?? nativeSnapshot.maxAutomatch ?? scalarValue(nativeSnapshot, 'MAX_AUTOMATCH'));
      const acquisitionState = String(state.state || '').toUpperCase();
      const title = acquisitionState === 'UNAVAILABLE'
        ? 'AutoCal sem estado confiável'
        : acquisitionState === 'PROBE_FAILED' || acquisitionState === 'FAILED'
          ? 'AutoCal com erro de leitura'
          : acquisitionState === 'WAITING_TELEMETRY_SETTLE'
            ? 'Conectando à aquisição'
            : enabled === 1 ? 'AutoCal adquirindo'
            : enabled === 0 ? 'AutoCal pausado'
            : snapshot.available ? 'AutoCal aguardando estado' : 'Aguardando AutoCal';
      const progress = 'Gasolina ' + petrolZones + '/4 zonas · GNV ' + gasZones + '/4 zonas';
      const autoMatch = autoMatchCount === null
        ? 'AutoMatch ainda sem contador válido'
        : Math.round(autoMatchCount) + ' AutoMatch ' + (Math.round(autoMatchCount) === 1 ? 'executado' : 'executados') +
          (maxAutoMatch === null ? '' : ' · limite configurado ' + Math.round(maxAutoMatch));
      let nextAction = 'Consulte a ECU para receber o estado nativo.';
      if (acquisitionState === 'UNAVAILABLE') {
        nextAction = String(state.message || state.error || 'A projeção nativa do AutoCal está indisponível.') + ' · Nenhuma referência será escolhida pela interface.';
      } else if (acquisitionState === 'PROBE_FAILED' || acquisitionState === 'FAILED') {
        nextAction = String(state.message || state.error || 'Não foi possível ler o estado nativo.') + ' · Verifique a conexão e tente consultar novamente.';
      } else if (enabled === 0) nextAction = 'Inicie a aquisição quando quiser continuar o aprendizado nativo.';
      else if (enabled === 1 && gasZones < 4) nextAction = 'Aquisição habilitada. Mantenha condições estáveis para visitar as regiões que ainda faltam.';
      else if (enabled === 1) nextAction = 'As 4 zonas GNV já foram marcadas pela ECU. Continue acompanhando sem resetar dados.';
      return { title, progress, autoMatch, nextAction, petrolZones, gasZones, petrolZoneFlags, gasZoneFlags, enabled, autoMatchCount, maxAutoMatch };
    },

    readNarrative(readerState = {}) {
      const state = String(readerState.state || 'IDLE').toUpperCase();
      const busy = readerState.busy === true || state === 'QUEUED' || state === 'READING' || state === 'CANCEL_REQUESTED';
      const progress = finite(readerState.progress);
      const suffix = progress !== null && busy ? ' · ' + Math.round(progress) + '%' : '';
      if (state === 'QUEUED') return { state, busy, level: 'working', title: 'Solicitação recebida', detail: 'Preparando leitura da ECU' + suffix, next: 'Aguarde a leitura iniciar.' };
      if (state === 'READING') return { state, busy, level: 'working', title: 'Lendo ECU', detail: String(readerState.message || 'Recebendo campos AutoCal') + suffix, next: 'Aguarde ou cancele a leitura.' };
      if (state === 'CANCEL_REQUESTED') return { state, busy: true, cancelling: true, level: 'working', title: 'Cancelando leitura', detail: String(readerState.message || 'Cancelamento solicitado') + suffix, next: 'Aguarde a leitura encerrar com segurança.' };
      if (state === 'READY') return { state, busy: false, level: 'ok', title: 'Leitura concluída', detail: String(readerState.message || 'Todos os campos esperados foram processados.'), next: 'Dados prontos para inspeção.' };
      if (state === 'READY_PARTIAL') return { state, busy: false, level: 'warning', title: 'Leitura parcial', detail: String(readerState.message || 'Alguns campos não foram confirmados pela ECU.'), next: 'Veja os detalhes técnicos ou tente consultar novamente.' };
      if (state === 'CANCELLED') return { state, busy: false, level: 'neutral', title: 'Leitura cancelada', detail: String(readerState.message || 'A leitura foi interrompida sem alterar a ECU.'), next: 'Consulte novamente quando quiser.' };
      if (state === 'DISCONNECTED') return { state, busy: false, level: 'error', title: 'ECU desconectada', detail: String(readerState.message || 'A conexão foi perdida.'), next: 'Reconecte a ECU e tente novamente.' };
      if (state === 'STALE_SESSION') return { state, busy: false, level: 'error', title: 'Sessão mudou', detail: String(readerState.message || 'A sessão USB mudou durante a leitura.'), next: 'Faça uma nova consulta na sessão atual.' };
      if (state === 'CALIBRATION_CONFLICT') return { state, busy: false, level: 'warning', title: 'Outra calibração está em uso', detail: String(readerState.message || 'A porta serial está ocupada por outra operação.'), next: 'Finalize a outra operação e tente novamente.' };
      if (state === 'FAILED' || state === 'TIMEOUT' || state === 'UNAVAILABLE') return { state, busy: false, level: 'error', title: state === 'TIMEOUT' ? 'Tempo de leitura esgotado' : 'Leitura falhou', detail: String(readerState.error || readerState.message || 'A ECU não concluiu a leitura.'), next: 'Verifique a conexão e tente consultar novamente.' };
      return { state, busy: false, level: 'neutral', title: 'Leitura pronta para iniciar', detail: 'Nenhuma consulta manual em andamento.', next: 'Use Consultar ECU para obter um snapshot completo.' };
    },

    sessionNarrative(status = {}) {
      const summary = status?.semanticSummary && typeof status.semanticSummary === 'object' ? status.semanticSummary : {};
      const autocal = summary?.autocal && typeof summary.autocal === 'object' ? summary.autocal : {};
      const recording = status.recording === true;
      const durationMs = Math.max(0, finite(status.durationMs ?? summary.durationMs) ?? 0);
      const minutes = Math.floor(durationMs / 60000);
      const regions = Array.isArray(autocal.correlatedRegions) ? autocal.correlatedRegions.length : 0;
      const gasZones = Math.max(0, Math.min(4, Math.round(finite(autocal.gasZones) ?? 0)));
      const dropped = Math.max(0, Math.round(finite(status.droppedEvents) ?? 0));
      const lastError = String(status.lastError || '');
      const documentsMirror = status?.documentsMirror && typeof status.documentsMirror === 'object' ? status.documentsMirror : {};
      const mirrorFailed = documentsMirror.available === false || documentsMirror.lastSyncOk === false;
      const warning = dropped > 0 || lastError.length > 0 || mirrorFailed;
      const title = recording ? 'Sessão atual' : summary?.sessionId ? 'Última sessão' : 'Sessões prontas';
      const detail = (recording ? minutes + ' min' : 'histórico preservado') +
        ' · ' + regions + ' ' + (regions === 1 ? 'região correlacionada' : 'regiões correlacionadas') +
        ' · GNV ' + gasZones + '/4';
      const next = dropped > 0 || lastError.length > 0
        ? 'Há uma lacuna na gravação interna da evidência. Veja os detalhes antes de usar esta sessão.'
        : mirrorFailed
          ? 'A sessão segue protegida na memória interna, mas Documentos/Omegas precisa de atenção.'
          : recording
            ? 'Salvando em Documentos/Omegas automaticamente enquanto a sessão acontece.'
            : 'Salvo em Documentos/Omegas. Abra Sessões apenas para revisar ou exportar.';
      return { title, detail, next, level: warning ? 'warning' : 'ok', recording, minutes, regions, gasZones, dropped, documentsMirror, mirrorFailed };
    },

    livePoint(telemetry = {}, projection = {}) {
      const source = telemetry || {};
      const ageMs = finite(source.telemetryAgeMs ?? source.ageMs);
      if (source.valid !== true || ageMs === null || ageMs < 0 || ageMs > AUTO_CAL_LIVE_STALE_MS) return null;
      const live = source.live || source.data || source;
      const petrolMs = finite(live.petrol_ms ?? live.petrolMs);
      const mapBar = finite(live.load_bar ?? live.map_bar ?? live.mapBar);
      const rpm = finite(live.rpm);
      const levelRaw = finite(live.level_raw ?? live.levelRaw);
      if (petrolMs === null || mapBar === null) return null;
      return { petrolMs, mapBar, rpm, levelRaw, fuel: String(live.fuel || live.state || '—'), sequence: finite(source.sequence), ageMs };
    },

    referencePoints(snapshot = {}, analysis = {}) {
      const petrolMs = physicalVector(snapshot, 'PETR_INJ_TBP');
      const petrolMap = physicalVector(snapshot, 'PETR_MNFLD_PRESS_RV');
      const gasMap = physicalVector(snapshot, 'GAS_MNFLD_PRESS_RV');
      const analysisPoints = Array.isArray(analysis?.points) ? analysis.points : [];
      const byIndex = new Map(analysisPoints.map(point => [Number(point?.index), point]));
      const count = Math.min(petrolMs.length, petrolMap.length, gasMap.length);
      const points = [];
      for (let index = 0; index < count; index += 1) {
        const x = finite(petrolMs[index]);
        const petrol = finite(petrolMap[index]);
        const gas = finite(gasMap[index]);
        const nativeAnalysis = byIndex.get(index) || {};
        const gasEquivalentMs = finite(nativeAnalysis.gasEquivalentTimeMs);
        if (x !== null && petrol !== null && gas !== null) {
          points.push({ index, petrolMs: x, petrolMapBar: petrol, gasMapBar: gas, gasEquivalentMs });
        }
      }
      return points;
    },

    referenceDomain(points = [], history = []) {
      const all = [...points, ...history];
      if (!all.length) return null;
      const xValues = all.flatMap(point => {
        const values = [finite(point.petrolMs)];
        const equivalent = finite(point.gasEquivalentMs);
        if (equivalent !== null) values.push(equivalent);
        return values.filter(value => value !== null);
      });
      const yValues = all
        .flatMap(point => [finite(point.petrolMapBar), finite(point.gasMapBar)])
        .filter(value => value !== null);
      if (!xValues.length || !yValues.length) return null;
      let xMin = Math.min(...xValues);
      let xMax = Math.max(...xValues);
      let yMin = Math.min(...yValues);
      let yMax = Math.max(...yValues);
      if (xMax - xMin < 0.01) {
        const pad = Math.max(0.25, Math.abs(xMin) * 0.08);
        xMin -= pad; xMax += pad;
      } else {
        const pad = Math.max(0.08, (xMax - xMin) * 0.05);
        xMin -= pad; xMax += pad;
      }
      if (yMax - yMin < 0.01) {
        yMin -= 0.08; yMax += 0.08;
      } else {
        const pad = Math.max(0.02, (yMax - yMin) * 0.12);
        yMin -= pad; yMax += pad;
      }
      return { xMin, xMax, yMin, yMax };
    },

    projectLive(live, scale) {
      if (!live || !scale) return null;
      const outsideX = live.petrolMs < scale.xMin || live.petrolMs > scale.xMax;
      const outsideY = live.mapBar < scale.yMin || live.mapBar > scale.yMax;
      const clampedX = Math.max(scale.xMin, Math.min(scale.xMax, live.petrolMs));
      const clampedY = Math.max(scale.yMin, Math.min(scale.yMax, live.mapBar));
      return {
        x: scale.xFor(clampedX),
        y: scale.yFor(clampedY),
        outOfRange: outsideX || outsideY,
      };
    },

    referenceTransition(previousProjection = {}, nextProjection = {}, previousSnapshot = {}, nextSnapshot = {}, previousAnalysis = {}) {
      const previousSession = String(previousProjection?.sessionId ?? '');
      const nextSession = String(nextProjection?.sessionId ?? '');
      const sessionChanged = previousSession !== nextSession;
      const previousUsable = previousProjection?.referenceUsable === true;
      const nextUsable = nextProjection?.referenceUsable === true;
      const referenceLost = previousUsable && !nextUsable;
      const referenceRegained = !previousUsable && nextUsable;
      const previousHash = String(previousSnapshot?.snapshotHash || '');
      const nextHash = String(nextSnapshot?.snapshotHash || '');
      const referenceChanged = previousUsable && nextUsable &&
        !!(previousHash && nextHash && previousHash !== nextHash);
      return {
        sessionChanged,
        referenceChanged,
        referenceLost,
        referenceRegained,
        resetSelection: sessionChanged || referenceChanged || referenceLost || referenceRegained,
        clearHistory: sessionChanged || referenceLost,
        previousPoints: !sessionChanged && referenceChanged
          ? this.referencePoints(previousSnapshot, previousAnalysis)
          : [],
      };
    },

    bandStrip(snapshot = {}, projection = {}) {
      const counters = vector(snapshot, 'NUM_BUF_UPD_GAS');
      const rawZones = vector(snapshot, 'ACQUIRED_ZONES_GAS');
      const projectedZones = projectedZoneFlags(projection, 'gas');
      const events = Array.isArray(projection?.correlation)
        ? projection.correlation
        : Array.isArray(snapshot.nativeMaturityEvents) ? snapshot.nativeMaturityEvents : [];
      const byBand = new Map(events.map(event => [Number(event?.bandIndex), event]));
      const persistent = projection?.correlationState && typeof projection.correlationState === 'object'
        ? projection.correlationState
        : {};
      const correlatedBands = new Set(Array.isArray(persistent.correlatedBands)
        ? persistent.correlatedBands.map(Number).filter(Number.isInteger)
        : []);
      const retryableBands = new Set(Array.isArray(persistent.retryableBands)
        ? persistent.retryableBands.map(Number).filter(Number.isInteger)
        : []);
      return Array.from({ length: 18 }, (_, index) => {
        const counter = finite(counters[index]) ?? 0;
        const zone = zoneForBand(index);
        const event = byBand.get(index) || null;
        const correlated = correlatedBands.has(index) || String(event?.correlationState || '') === 'CORRELATED';
        const retryable = retryableBands.has(index);
        const stateName = correlated ? 'anchored' : (event || retryable) ? 'mature' : counter > 0 ? 'activity' : 'empty';
        return {
          index,
          zone,
          counter,
          zoneAcquired: projectedZones ? projectedZones[zone] === true : (finite(rawZones[zone]) ?? 0) > 0,
          state: stateName,
          event,
        };
      });
    },

    referenceSourceLabel(projection = {}) {
      const source = String(projection?.source || '').toUpperCase();
      const freshness = String(projection?.freshness || '').toUpperCase();
      if (source === 'NATIVE_MONITOR') {
        return freshness === 'CURRENT_SESSION' ? 'Monitor nativo · sessão atual' : 'Monitor nativo';
      }
      if (source === 'MANUAL_READER') {
        return freshness === 'CURRENT_SESSION' ? 'Leitura manual · sessão atual' : 'Leitura manual';
      }
      if (freshness === 'STALE_SESSION') return 'Sem fonte atual · sessão anterior rejeitada';
      if (source === 'NONE') return 'Sem fonte confiável';
      return source || '—';
    },

    bandNarrative(band = {}) {
      const eventState = String(band?.event?.correlationState || '');
      if (band.state === 'anchored') {
        return eventState === 'CORRELATED'
          ? 'Nesta leitura, a região amadureceu e encontrou correlação física confiável.'
          : 'Correlação física confiável confirmada nesta sessão.';
      }
      if (band.state === 'mature') {
        return band.event
          ? 'Nesta leitura, a região amadureceu; a correlação física ainda não foi confirmada com confiança.'
          : 'Região madura nesta sessão; aguardando nova janela de telemetria para tentar a correlação física novamente.';
      }
      return band.counter > 0
        ? 'A ECU registrou atividade nesta região.'
        : 'Ainda não há atividade nesta região.';
    },

    toggleAction(enabled) {
      const value = finite(enabled);
      if (value === 1) return 'DISABLE_AUTO_CAL';
      if (value === 0) return 'ENABLE_AUTO_CAL';
      return null;
    },


  };

  class AutoCalCockpit {
    constructor(app) {
      this.app = app;
      this.store = app.store;
      this.scheduler = app.scheduler;
      this.api = ns.AutoCalApi;
      this.active = false;
      this.prepared = null;
      this.state = {};
      this.snapshot = {};
      this.projection = {};
      this.analysis = {};
      this.referenceUsable = false;
      this.operationalPending = false;
      this.readerState = {};
      this.readerSnapshot = {};
      this.acquisitionState = {};
      this.acquisitionSnapshot = {};
      this.actionState = {};
      this.sessionState = {};
      this.sessions = [];
      this.sessionDrawerOpen = false;
      this.chartScale = null;
      this.previousReferencePoints = [];
      this.currentReferencePoints = [];
      this.chartHistoryVisible = false;
      this.selectedReferenceIndex = null;
      this.selectedBandIndex = null;
      this.inject();
      this.bind();
      this.unsubscribeContext = this.scheduler.addHook('context', () => {
        if (this.store.get().route === 'autocal') this.refresh();
      });
      this.unsubscribeFast = this.scheduler.addHook('fast', () => {
        if (this.store.get().route === 'autocal') this.renderLiveCursor();
      });
      if (this.store.get().route === 'autocal') {
        this.active = true;
        root.setTimeout(() => this.refresh(), 0);
      }
    }

    inject() {
      if (!document.querySelector('link[data-autocal-cockpit-style]')) {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = 'styles-autocal-cockpit.css';
        link.dataset.autocalCockpitStyle = 'true';
        document.head.appendChild(link);
      }
      const stack = document.getElementById('autocalScreenHost');
      if (stack && !stack.querySelector('.autocal-cockpit')) {
        const panel = document.createElement('div');
        panel.className = 'autocal-route-panel';
        panel.innerHTML = `
          <section class="autocal-cockpit" aria-label="Auto Calibration nativa">
            <header class="autocal-hero" aria-live="polite">
              <div class="autocal-human-copy">
                <small>AGORA</small>
                <h3 id="autocalHumanTitle">Aguardando AutoCal</h3>
                <p id="autocalHumanProgress">Gasolina 0/4 zonas · GNV 0/4 zonas</p>
                <div id="autocalZoneMeter" class="autocal-zone-meter" aria-label="Gasolina 0 de 4 zonas, GNV 0 de 4 zonas">
                  <div class="petrol"><span>Gasolina</span><div class="autocal-zone-dots"><i data-autocal-zone-petrol="0"></i><i data-autocal-zone-petrol="1"></i><i data-autocal-zone-petrol="2"></i><i data-autocal-zone-petrol="3"></i></div></div>
                  <div class="gas"><span>GNV</span><div class="autocal-zone-dots"><i data-autocal-zone-gas="0"></i><i data-autocal-zone-gas="1"></i><i data-autocal-zone-gas="2"></i><i data-autocal-zone-gas="3"></i></div></div>
                </div>
                <strong id="autocalHumanAction">Consulte a ECU para receber o estado nativo.</strong>
              </div>
              <div class="autocal-hero-actions">
                <span id="autocalNativeState" class="source-status">Aquisição: aguardando ECU</span>
                <span id="autocalReadState" class="source-status" data-level="neutral">Leitura pronta</span>
                <button type="button" data-autocal-read class="secondary">Consultar ECU</button>
                <button type="button" data-autocal-cancel-read class="secondary" hidden>Cancelar leitura</button>
              </div>
              <div class="autocal-read-context" data-autocal-read-context data-read-level="neutral" hidden>
                <b id="autocalReadTitle">Leitura pronta para iniciar</b>
                <span id="autocalReadDetail">Nenhuma consulta manual em andamento.</span>
                <p id="autocalReadNext">Use Consultar ECU para obter um snapshot completo.</p>
              </div>
            </header>

            <section class="autocal-session-strip" data-session-level="ok" aria-live="polite">
              <div class="autocal-session-copy">
                <small>SESSÃO</small>
                <b id="autocalSessionSummary">Sessões prontas</b>
                <span id="autocalSessionDetail">Histórico ainda sem dados desta conexão.</span>
              </div>
              <div class="autocal-session-actions">
                <span id="autocalSessionState">Documentos/Omegas · persistência automática</span>
                <button type="button" data-autocal-sessions class="secondary">Ver sessões</button>
              </div>
            </section>
            <section id="autocalSessionDrawer" class="autocal-session-drawer" hidden aria-label="Histórico de sessões AutoCal">
              <div class="autocal-section-head compact"><div><small>HISTÓRICO</small><h4>Sessões recentes</h4><p id="autocalSessionNext">As sessões são separadas pela geração física USB.</p></div></div>
              <div id="autocalSessionList" class="autocal-session-list"></div>
            </section>

            <section class="autocal-reference-card">
              <div class="autocal-section-head">
                <div><small>REFERÊNCIA NATIVA</small><h4>Gasolina × GNV</h4><p>Petrol Inj. no eixo horizontal e MAP no vertical. A tela só desenha o que a ECU publicou.</p></div>
                <div class="autocal-chart-tools" aria-label="Controles do gráfico">
                  <button type="button" data-autocal-history disabled aria-label="Mostrar leitura anterior">Leitura anterior</button>
                </div>
              </div>
              <div class="autocal-chart-legend"><span class="petrol">Gasolina</span><span class="gas">GNV</span><span class="equivalence">GNV equivalente</span><span class="live">AGORA</span><span id="autocalReferenceCount">0 pontos nativos</span></div>
              <div class="autocal-live-strip" aria-live="polite">
                <div class="autocal-live-summary"><small>AGORA</small><b id="autocalLiveTitle">Aguardando telemetria</b><span id="autocalLiveFuel">—</span></div>
                <div class="autocal-live-metric"><small>RPM</small><b id="autocalLiveRpm">—</b></div>
                <div class="autocal-live-metric"><small>PETROL INJ.</small><b><span id="autocalLivePetrol">—</span> ms</b></div>
                <div class="autocal-live-metric"><small>MAP</small><b><span id="autocalLiveMap">—</span> bar</b></div>
                <div class="autocal-live-metric"><small>LEVELS RAW</small><b id="autocalLiveLevel">—</b></div>
                <p id="autocalLiveNarrative" class="autocal-live-narrative">O cursor AGORA aparece quando a telemetria MP48 é válida. Ele nunca vira evidência adquirida.</p>
              </div>
              <div class="autocal-chart-workspace">
                <div id="autocalReferenceChart" class="autocal-chart-host"><div class="chart-empty">Aguardando os vetores nativos da ECU.</div></div>
                <aside id="autocalChartInspector" class="autocal-chart-inspector"><b>Toque em um ponto</b><span>Veja Petrol Inj. e MAP de gasolina/GNV sem alterar nada.</span></aside>
              </div>
            </section>

            <section class="autocal-bands-card">
              <div class="autocal-section-head compact">
                <div><small>18 REGIÕES DE AQUISIÇÃO GNV</small><h4>Onde a ECU já registrou atividade</h4></div>
                <span id="autocalZoneSummary">0/4 zonas GNV</span>
              </div>
              <div class="autocal-band-legend" aria-label="Legenda das faixas">
                <span data-state="empty">Sem atividade</span><span data-state="activity">Atividade</span><span data-state="mature">Evento</span><span data-state="anchored">Correlacionada</span>
              </div>
              <div id="autocalBands" class="autocal-band-strip" role="list"></div>
              <div id="autocalBandInspector" class="autocal-inline-inspector"><b>Toque numa região</b><span>O estado humano aparece aqui; detalhes RAW ficam no painel técnico.</span></div>
            </section>

            <section class="autocal-command-bar">
              <div class="autocal-command-copy"><small>AUTOMATCH DA ECU</small><b id="autocalHumanAutoMatch">Ainda sem contador válido</b><span id="autocalActionStatus">Nenhuma ação preparada.</span></div>
              <button type="button" data-autocal-toggle class="autocal-primary-action" disabled>Aguardando estado</button>
              <details class="autocal-more-actions">
                <summary>Mais ações</summary>
                <div class="autocal-reset-actions">
                  <button type="button" data-autocal-action="RESET_PETROL">Reset gasolina</button>
                  <button type="button" data-autocal-action="RESET_GAS">Reset GNV</button>
                  <p>Reset é uma ação crítica. A revisão WebView e a confirmação Android continuam obrigatórias.</p>
                </div>
              </details>
            </section>

            <details id="autocalTechnicalDetails" class="autocal-technical-details">
              <summary>Detalhes técnicos</summary>
              <div class="autocal-tech-grid">
                <div><small>ESTADO RAW</small><b id="autocalStateRaw">—</b></div>
                <div><small>AQUISIÇÃO</small><b id="autocalEnableRaw">—</b></div>
                <div><small>SNAPSHOT</small><b id="autocalSnapshotHash">—</b></div>
                <div><small>FONTE DA REFERÊNCIA</small><b id="autocalReferenceSource">—</b></div>
                <div><small>EVENTOS DESTA LEITURA</small><b id="autocalMaturityRaw">0</b></div>
              </div>
              <div id="autocalEvents" class="autocal-events"></div>
            </details>

            <div id="autocalReview" class="autocal-review" hidden></div>
          </section>`;
        stack.appendChild(panel);
        this.panel = panel;
      } else {
        this.panel = stack?.querySelector('.autocal-route-panel') || null;
      }
    }

    bind() {
      this.panel?.querySelector('[data-autocal-read]')?.addEventListener('click', () => this.requestRead());
      this.panel?.querySelector('[data-autocal-cancel-read]')?.addEventListener('click', () => this.cancelRead());
      this.panel?.querySelector('[data-autocal-toggle]')?.addEventListener('click', event => {
        const action = event.currentTarget?.dataset?.action;
        if (action) this.runOperational(action);
      });
      this.panel?.querySelectorAll('[data-autocal-action]').forEach(button => {
        button.addEventListener('click', () => this.prepare(button.dataset.autocalAction));
      });
      this.panel?.querySelector('[data-autocal-history]')?.addEventListener('click', () => {
        if (!this.previousReferencePoints.length) return;
        this.chartHistoryVisible = !this.chartHistoryVisible;
        this.renderReferenceChart(this.snapshot);
      });
      this.panel?.querySelector('[data-autocal-sessions]')?.addEventListener('click', event => {
        this.sessionDrawerOpen = !this.sessionDrawerOpen;
        const drawer = document.getElementById('autocalSessionDrawer');
        if (drawer) drawer.hidden = !this.sessionDrawerOpen;
        event.currentTarget.textContent = this.sessionDrawerOpen ? 'Ocultar sessões' : 'Ver sessões';
        if (this.sessionDrawerOpen) this.loadSessions();
      });
      this.panel?.addEventListener('click', event => {
        if (event.target.closest('[data-autocal-cancel]')) this.cancelPrepared();
        if (event.target.closest('[data-autocal-confirm]')) this.confirmPrepared();
        const band = event.target.closest('[data-autocal-band-index]');
        if (band) this.inspectBand(Number(band.dataset.autocalBandIndex));
        const point = event.target.closest('[data-autocal-ref-index]');
        if (point) this.inspectReferencePoint(Number(point.dataset.autocalRefIndex));
        const exportButton = event.target.closest('[data-autocal-export-session]');
        if (exportButton?.dataset?.sessionId) this.api?.exportSession?.(exportButton.dataset.sessionId);
      });
    }

    enter() {
      this.active = true;
      this.refresh();
    }

    refresh() {
      if (!this.api?.available?.()) {
        this.renderUnavailable();
        return;
      }
      const projection = this.api.projection?.() || {};
      const authoritative = projection?.ok === true;
      const previousProjection = this.projection || {};
      this.projection = projection;

      if (!authoritative) {
        const message = String(projection?.error || 'A projeção nativa do AutoCal não respondeu com estado confiável.');
        this.readerState = {
          state: 'UNAVAILABLE',
          error: message,
          reasonCode: 'AUTOCAL_PROJECTION_UNAVAILABLE',
        };
        this.readerSnapshot = { available: false, fields: [] };
        this.acquisitionSnapshot = { available: false, fields: [] };
        this.acquisitionState = {
          state: 'UNAVAILABLE',
          message,
          error: message,
          reasonCode: 'AUTOCAL_PROJECTION_UNAVAILABLE',
        };
        this.state = this.acquisitionState;
        this.snapshot = { available: false, fields: [] };
        this.analysis = {};
        this.referenceUsable = false;
        this.actionState = this.api.actionStatus() || {};
        this.operationalPending = this.actionState?.busy === true ||
          ['QUEUED', 'READING_BEFORE', 'SENDING_ACTION', 'READING_AFTER'].includes(String(this.actionState?.state || ''));
        this.sessionState = this.api.sessionStatus?.() || {};
        this.render();
        return;
      }

      this.readerState = projection.manualStatus || {};
      this.readerSnapshot = projection.manualSnapshot || {};
      const acquisitionStatus = projection.nativeStatus || {};
      this.acquisitionSnapshot = projection.nativeSnapshot || {};
      this.acquisitionState = {
        ...acquisitionStatus,
        latestSnapshot: this.acquisitionSnapshot?.available
          ? this.acquisitionSnapshot
          : acquisitionStatus.latestSnapshot,
      };
      this.state = this.acquisitionState;

      const nextSnapshot = projection.snapshot || {};
      const nextAnalysis = projection.analysis || {};
      this.referenceUsable = projection.referenceUsable === true;

      const referenceTransition = AutoCalUxModel.referenceTransition(
        previousProjection,
        projection,
        this.snapshot,
        nextSnapshot,
        this.analysis,
      );
      if (referenceTransition.clearHistory) {
        this.previousReferencePoints = [];
        this.chartHistoryVisible = false;
      } else if (referenceTransition.referenceChanged) {
        this.previousReferencePoints = referenceTransition.previousPoints;
      }
      if (referenceTransition.resetSelection) this.selectedReferenceIndex = null;
      this.snapshot = nextSnapshot || {};
      this.analysis = nextAnalysis;
      this.actionState = this.api.actionStatus() || {};
      this.operationalPending = this.actionState?.busy === true ||
        ['QUEUED', 'READING_BEFORE', 'SENDING_ACTION', 'READING_AFTER'].includes(String(this.actionState?.state || ''));
      this.sessionState = this.api.sessionStatus?.() || {};
      this.render();
    }

    loadSessions() {
      if (!this.api?.available?.()) return;
      const next = this.api.sessions?.();
      this.sessions = Array.isArray(next) ? next : [];
      this.renderSessionState();
    }

    requestRead() {
      if (!this.api?.available?.()) return;
      const result = this.api.startRead();
      this.readerState = result && typeof result === 'object'
        ? result
        : { state: 'UNAVAILABLE', error: 'Retorno da consulta AutoCal inválido.', reasonCode: 'AUTOCAL_PROJECTION_UNAVAILABLE' };
      if (result?.ok === false) {
        this.store.patch({ alert: { level: 'warning', message: result.error || 'Leitura AutoCal indisponível.' } });
      } else {
        this.store.patch({ alert: { level: 'ok', message: 'Consulta recebida. A tela acompanhará o reader até READY, parcial ou erro.' } });
      }
      this.renderReadState();
      this.refresh();
    }

    cancelRead() {
      if (!this.api?.available?.()) return;
      const result = this.api.cancelRead();
      if (result?.ok === false) {
        this.store.patch({ alert: { level: 'warning', message: result.error || 'Não foi possível cancelar a leitura.' } });
      } else {
        this.store.patch({ alert: { level: 'ok', message: 'Cancelamento da leitura solicitado. Nenhum dado foi gravado na ECU.' } });
      }
      this.refresh();
    }

    runOperational(action) {
      if (!['ENABLE_AUTO_CAL', 'DISABLE_AUTO_CAL'].includes(action) || !this.api?.available?.()) return;
      const enable = action === 'ENABLE_AUTO_CAL';
      this.operationalPending = true;
      this.render();
      const result = this.api.setAcquisitionEnabled?.(enable) || { ok: false, error: 'Ação operacional indisponível.' };
      if (result?.ok === false) {
        this.operationalPending = false;
        this.store.patch({ alert: { level: 'warning', message: result.error || 'Não foi possível alterar a aquisição AutoCal.' } });
      } else {
        this.store.patch({ alert: { level: 'ok', message: enable
          ? 'Início enviado. Confirmando ACK e leitura de volta da ECU…'
          : 'Pausa enviada. Confirmando ACK e leitura de volta da ECU…' } });
      }
      this.refresh();
    }

    prepare(action) {
      if (!action || !this.api?.available?.()) return;
      const result = this.api.prepare(action);
      if (!result?.ok || !result?.prepared) {
        this.store.patch({ alert: { level: 'warning', message: result?.error || 'A ação AutoCal não pôde ser preparada.' } });
        return;
      }
      this.prepared = result;
      this.renderReview();
    }

    cancelPrepared() {
      this.api?.cancelPreparation?.();
      this.prepared = null;
      const review = document.getElementById('autocalReview');
      if (review) { review.hidden = true; review.innerHTML = ''; }
      this.refresh();
    }

    confirmPrepared() {
      const prepared = this.prepared;
      if (!prepared?.preparationId) return;
      const result = this.api.execute(prepared.preparationId);
      if (result?.ok === false) {
        this.store.patch({ alert: { level: 'warning', message: result.error || 'A confirmação Android não pôde ser aberta.' } });
        return;
      }
      this.prepared = null;
      const review = document.getElementById('autocalReview');
      if (review) { review.hidden = true; review.innerHTML = ''; }
      this.store.patch({ alert: { level: 'warning', message: 'Confirmação Android aberta. O comando ainda não foi enviado até você confirmar lá.' } });
      this.refresh();
    }


    render() {
      const snapshot = this.snapshot || {};
      const state = this.state || {};
      const events = Array.isArray(this.projection?.correlation)
        ? this.projection.correlation
        : Array.isArray(snapshot.nativeMaturityEvents) ? snapshot.nativeMaturityEvents : [];
      const human = AutoCalUxModel.humanState(snapshot, state, this.projection);
      const acquisitionName = String(state.state || '').toUpperCase();
      const acquisitionLabel = acquisitionName === 'UNAVAILABLE' ? 'indisponível'
        : acquisitionName === 'WAITING_TELEMETRY_SETTLE' ? 'conectando'
        : acquisitionName === 'PROBE_FAILED' || acquisitionName === 'FAILED' ? 'erro'
        : human.enabled === 1 ? 'adquirindo'
        : human.enabled === 0 ? 'pausado'
        : acquisitionName === 'DISCONNECTED' ? 'desconectado'
        : snapshot.available ? 'pronto' : 'aguardando';

      this.text('autocalHumanTitle', human.title);
      this.text('autocalHumanProgress', human.progress);
      this.text('autocalHumanAction', human.nextAction);
      this.text('autocalHumanAutoMatch', human.autoMatch);
      this.text('autocalNativeState', 'Aquisição: ' + acquisitionLabel);
      this.text('autocalZoneSummary', human.gasZones + '/4 zonas GNV');
      this.text('autocalStateRaw', state.state || '—');
      this.text('autocalEnableRaw', human.enabled === 1 ? 'ATIVA' : human.enabled === 0 ? 'PAUSADA' : '—');
      this.text('autocalSnapshotHash', snapshot.snapshotHash ? String(snapshot.snapshotHash).slice(0, 10) : '—');
      this.text('autocalReferenceSource', AutoCalUxModel.referenceSourceLabel(this.projection));
      this.text('autocalMaturityRaw', events.length);
      this.renderZoneMeter(human);
      this.renderReadState();
      this.renderSessionState();
      this.renderLiveNarrative();

      const toggle = this.panel?.querySelector('[data-autocal-toggle]');
      if (toggle) {
        const action = AutoCalUxModel.toggleAction(human.enabled);
        toggle.dataset.action = action || '';
        toggle.disabled = !action || this.operationalPending;
        toggle.textContent = this.operationalPending
          ? 'Confirmando ECU…'
          : action === 'DISABLE_AUTO_CAL'
            ? 'Pausar aquisição'
            : action === 'ENABLE_AUTO_CAL' ? 'Iniciar aquisição' : 'Aguardando estado';
      }

      const history = this.panel?.querySelector('[data-autocal-history]');
      if (history) {
        history.disabled = this.previousReferencePoints.length === 0;
        history.textContent = this.chartHistoryVisible ? 'Ocultar anterior' : 'Leitura anterior';
      }

      this.renderReferenceChart(snapshot);
      this.renderBands(snapshot);
      this.renderEvents(events);
      this.renderActionState();
    }

    renderReadState() {
      const read = AutoCalUxModel.readNarrative(this.readerState || {});
      this.text('autocalReadState', read.title);
      this.text('autocalReadTitle', read.title);
      this.text('autocalReadDetail', read.detail);
      this.text('autocalReadNext', read.next);
      const pill = document.getElementById('autocalReadState');
      if (pill) pill.dataset.level = read.level;
      const context = this.panel?.querySelector('[data-autocal-read-context]');
      if (context) {
        context.dataset.readLevel = read.level;
        context.hidden = (read.level === 'neutral' || read.level === 'ok') && !read.busy;
      }
      const start = this.panel?.querySelector('[data-autocal-read]');
      const cancel = this.panel?.querySelector('[data-autocal-cancel-read]');
      if (start) {
        start.disabled = read.busy;
        start.textContent = read.busy ? 'Consultando ECU…' : 'Consultar ECU';
      }
      if (cancel) {
        cancel.hidden = !read.busy;
        cancel.disabled = !read.busy || read.cancelling === true;
      }
    }

    renderSessionState() {
      const narrative = AutoCalUxModel.sessionNarrative(this.sessionState || {});
      this.text('autocalSessionSummary', narrative.title);
      this.text('autocalSessionDetail', narrative.detail);
      this.text(
        'autocalSessionState',
        narrative.mirrorFailed
          ? 'Protegido na memória interna'
          : narrative.recording ? 'Salvando em Documentos/Omegas' : 'Salvo em Documentos/Omegas',
      );
      this.text('autocalSessionNext', narrative.next);
      const strip = this.panel?.querySelector('.autocal-session-strip');
      if (strip) strip.dataset.sessionLevel = narrative.level;

      const host = document.getElementById('autocalSessionList');
      if (!host || !this.sessionDrawerOpen) return;
      const sessions = Array.isArray(this.sessions) ? this.sessions.slice(0, 8) : [];
      if (!sessions.length) {
        host.innerHTML = '<p class="empty-copy">Nenhuma sessão gravada ainda.</p>';
        return;
      }
      host.innerHTML = sessions.map((item, index) => {
        const summary = item?.semanticSummary && typeof item.semanticSummary === 'object' ? item.semanticSummary : {};
        const autocal = summary?.autocal && typeof summary.autocal === 'object' ? summary.autocal : {};
        const when = finite(item.createdAt);
        const date = when === null ? 'Data indisponível' : new Date(when).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
        const duration = Math.max(0, finite(item.durationMs ?? summary.durationMs) ?? 0);
        const minutes = Math.max(0, Math.floor(duration / 60000));
        const regions = Array.isArray(autocal.correlatedRegions) ? autocal.correlatedRegions.length : 0;
        const gasZones = Math.max(0, Math.min(4, Math.round(finite(autocal.gasZones) ?? 0)));
        const active = item.active === true;
        const id = escapeHtml(item.id || '');
        return '<article class="autocal-session-item" data-active="' + (active ? 'true' : 'false') + '">' +
          '<div><small>' + (active ? 'AGORA' : date) + '</small><b>' + minutes + ' min · ' + regions + ' ' + (regions === 1 ? 'região' : 'regiões') + '</b><span>GNV ' + gasZones + '/4 · ' + escapeHtml(item.reason || 'Sessão MP48') + '</span></div>' +
          '<button type="button" class="secondary" data-autocal-export-session data-session-id="' + id + '">Exportar</button>' +
        '</article>';
      }).join('');
    }

    renderLiveNarrative() {
      const telemetry = this.store.get().telemetry || {};
      const live = AutoCalUxModel.livePoint(telemetry, this.projection);
      if (!live) {
        const ageMs = finite(telemetry.telemetryAgeMs ?? telemetry.ageMs);
        const stale = telemetry.valid === true && ageMs !== null && ageMs > AUTO_CAL_LIVE_STALE_MS;
        this.text('autocalLiveTitle', stale ? 'Telemetria com atraso' : 'Aguardando telemetria válida');
        this.text('autocalLiveFuel', '—');
        this.text('autocalLiveRpm', '—');
        this.text('autocalLivePetrol', '—');
        this.text('autocalLiveMap', '—');
        this.text('autocalLiveLevel', '—');
        this.text('autocalLiveNarrative', stale
          ? 'O último frame já passou de 2,5 s. AGORA foi ocultado até chegar uma leitura nova; a referência nativa não foi alterada.'
          : 'O cursor AGORA aparece quando RPM, Petrol Inj. e MAP chegam válidos. Ele nunca vira evidência adquirida.');
        return;
      }
      const rpmLabel = live.rpm === null ? 'RPM —' : Math.round(live.rpm).toLocaleString('pt-BR') + ' RPM';
      this.text('autocalLiveTitle', 'Motor nesta região agora');
      this.text('autocalLiveFuel', live.fuel);
      this.text('autocalLiveRpm', live.rpm === null ? '—' : Math.round(live.rpm).toLocaleString('pt-BR'));
      this.text('autocalLivePetrol', live.petrolMs.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }));
      this.text('autocalLiveMap', live.mapBar.toLocaleString('pt-BR', { minimumFractionDigits: 3, maximumFractionDigits: 3 }));
      this.text('autocalLiveLevel', live.levelRaw === null ? '—' : String(Math.round(live.levelRaw)));
      const enabled = AutoCalUxModel.humanState(this.snapshot || {}, this.acquisitionState || {}, this.projection).enabled;
      const acquisitionCopy = enabled === 1
        ? 'Aquisição nativa habilitada. Se a condição estabilizar, a ECU pode fortalecer esta região.'
        : enabled === 0 ? 'Aquisição pausada. O ponto AGORA é somente telemetria.' : 'Estado de aquisição ainda não confirmado.';
      this.text('autocalLiveNarrative', rpmLabel + ' · ' + live.petrolMs.toFixed(2) + ' ms · ' + live.mapBar.toFixed(3) + ' bar. ' + acquisitionCopy);
    }

    renderLiveCursor() {
      this.renderLiveNarrative();
      const live = AutoCalUxModel.livePoint(this.store.get().telemetry || {});
      const scale = this.chartScale;
      const layer = this.panel?.querySelector('.autocal-live-layer');
      if (!live) {
        if (layer) layer.setAttribute('display', 'none');
        return;
      }
      if (!scale || !layer) return;
      const projected = AutoCalUxModel.projectLive(live, scale);
      if (!projected) return;
      layer.removeAttribute('display');
      layer.setAttribute('data-out-of-range', projected.outOfRange ? 'true' : 'false');
      this.panel?.querySelectorAll('[data-autocal-live-point]').forEach(node => {
        node.setAttribute('cx', projected.x.toFixed(1));
        node.setAttribute('cy', projected.y.toFixed(1));
      });
      const label = this.panel?.querySelector('[data-autocal-live-label]');
      if (label) {
        label.setAttribute('x', (projected.x + 12).toFixed(1));
        label.setAttribute('y', (projected.y - 12).toFixed(1));
        label.textContent = projected.outOfRange ? 'AGORA · fora da escala' : 'AGORA';
      }
    }

    renderZoneMeter(human) {
      const meter = document.getElementById('autocalZoneMeter');
      if (!meter) return;
      const petrolFlags = Array.isArray(human?.petrolZoneFlags)
        ? human.petrolZoneFlags.slice(0, 4)
        : Array.from({ length: 4 }, (_, index) => index < (finite(human?.petrolZones) ?? 0));
      const gasFlags = Array.isArray(human?.gasZoneFlags)
        ? human.gasZoneFlags.slice(0, 4)
        : Array.from({ length: 4 }, (_, index) => index < (finite(human?.gasZones) ?? 0));
      const petrolZones = petrolFlags.filter(Boolean).length;
      const gasZones = gasFlags.filter(Boolean).length;
      meter.setAttribute('aria-label', 'Gasolina ' + petrolZones + ' de 4 zonas, GNV ' + gasZones + ' de 4 zonas');
      this.panel?.querySelectorAll('[data-autocal-zone-petrol]').forEach(node => {
        const index = Number(node.dataset.autocalZonePetrol);
        node.dataset.active = petrolFlags[index] === true ? 'true' : 'false';
      });
      this.panel?.querySelectorAll('[data-autocal-zone-gas]').forEach(node => {
        const index = Number(node.dataset.autocalZoneGas);
        node.dataset.active = gasFlags[index] === true ? 'true' : 'false';
      });
    }

    renderReferenceChart(snapshot) {
      const host = document.getElementById('autocalReferenceChart');
      if (!host) return;
      const points = AutoCalUxModel.referencePoints(snapshot, this.analysis || {});
      const live = AutoCalUxModel.livePoint(this.store.get().telemetry || {});
      this.currentReferencePoints = points;
      const timingKnown = this.projection?.referenceTimingKnown === true;
      const timingCoherent = this.projection?.referenceTimingCoherent === true;
      const timingSpanMs = finite(this.projection?.referenceTimingSpanMs);
      const timingLimitMs = finite(this.projection?.referenceTimingLimitMs);
      const timingProblem = timingKnown && !timingCoherent;

      if (!points.length || this.referenceUsable === false) {
        this.chartScale = null;
        if (timingProblem) {
          const spanLabel = timingSpanMs === null ? 'intervalo desconhecido' : Math.round(timingSpanMs) + ' ms';
          const limitLabel = timingLimitMs === null ? 'limite nativo' : 'limite ' + Math.round(timingLimitMs) + ' ms';
          this.text('autocalReferenceCount', points.length + ' ponto' + (points.length === 1 ? '' : 's') + ' · fora da janela');
          host.innerHTML = '<div class="chart-empty"><b>REFERÊNCIA FORA DA JANELA</b><span>Os vetores físicos foram lidos com ' + spanLabel + ' de diferença; ' + limitLabel + '. Consulte a ECU novamente. O AGORA continua vivo sem virar referência.</span></div>';
          this.text('autocalChartInspector', 'Referência física temporalmente incoerente. Consulte a ECU novamente; o cursor AGORA continua somente como telemetria.');
        } else {
          this.text('autocalReferenceCount', '0 pontos utilizáveis');
          host.innerHTML = '<div class="chart-empty"><b>SEM REFERÊNCIA</b><span>A ECU ainda não publicou uma referência física utilizável. O AGORA continua nos valores ao lado, sem inventar escala.</span></div>';
          this.text('autocalChartInspector', live
            ? 'AGORA: ' + live.petrolMs.toFixed(2) + ' ms · ' + live.mapBar.toFixed(3) + ' bar. Referência nativa indisponível.'
            : 'Aguardando Petrol Inj. e MAP nativos.');
        }
        this.renderLiveNarrative();
        return;
      }

      this.text('autocalReferenceCount', points.length + ' ponto' + (points.length === 1 ? '' : 's') + ' nativo' + (points.length === 1 ? '' : 's'));

      const width = 1000;
      const height = 240;
      const padLeft = 64;
      const padRight = 28;
      const padTop = 18;
      const padBottom = 44;
      const history = this.chartHistoryVisible ? this.previousReferencePoints : [];
      const domain = AutoCalUxModel.referenceDomain(points, history);
      if (!domain) {
        this.chartScale = null;
        host.innerHTML = '<div class="chart-empty"><b>SEM REFERÊNCIA</b><span>Os vetores recebidos não formam um domínio físico válido.</span></div>';
        return;
      }
      const { xMin, xMax, yMin, yMax } = domain;
      const xFor = value => padLeft + ((value - xMin) / (xMax - xMin)) * (width - padLeft - padRight);
      const yFor = value => height - padBottom - ((value - yMin) / (yMax - yMin)) * (height - padTop - padBottom);
      const scale = { xMin, xMax, yMin, yMax, xFor, yFor };
      this.chartScale = scale;
      const pathFor = (items, yKey, xKey = 'petrolMs') => items
        .filter(point => finite(point?.[xKey]) !== null && finite(point?.[yKey]) !== null)
        .map((point, index) => (index ? 'L' : 'M') + ' ' + xFor(point[xKey]).toFixed(1) + ' ' + yFor(point[yKey]).toFixed(1))
        .join(' ');

      const xTicks = Array.from({ length: 6 }, (_, index) => xMin + index * (xMax - xMin) / 5);
      const yTicks = Array.from({ length: 5 }, (_, index) => yMin + index * (yMax - yMin) / 4);
      const grid = yTicks.map(value => {
        const y = yFor(value);
        return '<line class="autocal-grid-line" x1="' + padLeft + '" y1="' + y.toFixed(1) + '" x2="' + (width - padRight) + '" y2="' + y.toFixed(1) + '"></line>' +
          '<text class="autocal-axis-tick-y" x="' + (padLeft - 8) + '" y="' + (y + 4).toFixed(1) + '" text-anchor="end">' + value.toFixed(2) + '</text>';
      }).join('') + xTicks.map(value => {
        const x = xFor(value);
        return '<line class="autocal-grid-line vertical" x1="' + x.toFixed(1) + '" y1="' + padTop + '" x2="' + x.toFixed(1) + '" y2="' + (height - padBottom) + '"></line>' +
          '<text class="autocal-axis-tick-x" x="' + x.toFixed(1) + '" y="' + (height - 23) + '" text-anchor="middle">' + value.toFixed(1) + '</text>';
      }).join('');

      const previous = history.length
        ? '<path class="autocal-reference-line previous petrol" d="' + pathFor(history, 'petrolMapBar') + '"></path>' +
          '<path class="autocal-reference-line previous gas" d="' + pathFor(history, 'gasMapBar') + '"></path>'
        : '';
      const equivalent = points.filter(point => finite(point.gasEquivalentMs) !== null);
      const equivalencePath = equivalent.length > 1
        ? '<path class="autocal-equivalence-line" d="' + pathFor(equivalent, 'petrolMapBar', 'gasEquivalentMs') + '"></path>'
        : '';

      const pointMarkup = points.map(point => {
        const x = xFor(point.petrolMs).toFixed(1);
        const petrolY = yFor(point.petrolMapBar).toFixed(1);
        const gasY = yFor(point.gasMapBar).toFixed(1);
        const equivalentX = finite(point.gasEquivalentMs) === null ? null : xFor(point.gasEquivalentMs).toFixed(1);
        return '<circle class="autocal-reference-hit" data-autocal-ref-index="' + point.index + '" cx="' + x + '" cy="' + petrolY + '" r="22"></circle>' +
          '<circle class="autocal-reference-point petrol" cx="' + x + '" cy="' + petrolY + '" r="5"></circle>' +
          '<circle class="autocal-reference-hit" data-autocal-ref-index="' + point.index + '" cx="' + x + '" cy="' + gasY + '" r="22"></circle>' +
          '<circle class="autocal-reference-point gas" cx="' + x + '" cy="' + gasY + '" r="5"></circle>' +
          (equivalentX === null ? '' : '<circle class="autocal-equivalence-point" cx="' + equivalentX + '" cy="' + petrolY + '" r="4"></circle>');
      }).join('');

      const projectedLive = AutoCalUxModel.projectLive(live, scale);
      const liveMarkup = live && projectedLive
        ? '<g class="autocal-live-layer" data-out-of-range="' + (projectedLive.outOfRange ? 'true' : 'false') + '" aria-label="Posição atual do motor">' +
            '<circle class="autocal-live-halo" data-autocal-live-point cx="' + projectedLive.x.toFixed(1) + '" cy="' + projectedLive.y.toFixed(1) + '" r="13"></circle>' +
            '<circle class="autocal-live-point" data-autocal-live-point cx="' + projectedLive.x.toFixed(1) + '" cy="' + projectedLive.y.toFixed(1) + '" r="6"></circle>' +
            '<text class="autocal-live-label" data-autocal-live-label x="' + (projectedLive.x + 12).toFixed(1) + '" y="' + (projectedLive.y - 12).toFixed(1) + '">' +
              (projectedLive.outOfRange ? 'AGORA · fora da escala' : 'AGORA') + '</text>' +
          '</g>'
        : '';

      host.innerHTML = '<svg class="autocal-reference-svg" viewBox="0 0 ' + width + ' ' + height + '" role="img" aria-label="Referência AutoCal gasolina, GNV, equivalência nativa e posição AGORA por Petrol Inj. e MAP">' +
        grid +
        '<text class="autocal-axis-title x" x="' + ((padLeft + width - padRight) / 2).toFixed(1) + '" y="' + (height - 5) + '" text-anchor="middle">Petrol Inj. (ms)</text>' +
        '<text class="autocal-axis-title y" x="14" y="' + (height / 2) + '" text-anchor="middle" transform="rotate(-90 14 ' + (height / 2) + ')">MAP (bar)</text>' +
        '<g>' +
        previous + equivalencePath +
        '<path class="autocal-reference-line petrol" d="' + pathFor(points, 'petrolMapBar') + '"></path>' +
        '<path class="autocal-reference-line gas" d="' + pathFor(points, 'gasMapBar') + '"></path>' +
        pointMarkup + liveMarkup +
        '</g></svg>';

      const selected = Number.isInteger(this.selectedReferenceIndex) ? this.selectedReferenceIndex : points[0].index;
      this.inspectReferencePoint(selected);
      this.renderLiveNarrative();
    }

    inspectReferencePoint(index) {
      const host = document.getElementById('autocalChartInspector');
      const point = this.currentReferencePoints.find(item => Number(item.index) === Number(index));
      if (!host || !point) return;
      this.selectedReferenceIndex = point.index;
      host.innerHTML = '<b>Ponto ' + (point.index + 1) + ' · ' + point.petrolMs.toFixed(2) + ' ms</b>' +
        '<span>MAP gasolina ' + point.petrolMapBar.toFixed(3) + ' bar · MAP GNV ' + point.gasMapBar.toFixed(3) + ' bar' +
        (finite(point.gasEquivalentMs) === null ? '' : ' · GNV equivalente ' + point.gasEquivalentMs.toFixed(2) + ' ms') + '</span>';
      document.querySelectorAll('#autocalReferenceChart [data-autocal-ref-index]').forEach(node => {
        node.classList.toggle('selected', Number(node.dataset.autocalRefIndex) === Number(point.index));
      });
    }

    renderBands(snapshot) {
      const host = document.getElementById('autocalBands');
      if (!host) return;
      const bands = AutoCalUxModel.bandStrip(snapshot, this.projection);
      host.innerHTML = bands.map(band => {
        const stateLabel = band.state === 'anchored' ? 'correlacionada'
          : band.state === 'mature' ? 'evento'
          : band.state === 'activity' ? 'atividade' : 'vazia';
        return '<button type="button" class="autocal-band-segment" data-autocal-band-index="' + band.index +
          '" data-state="' + band.state + '" data-zone-acquired="' + (band.zoneAcquired ? 'true' : 'false') +
          '" role="listitem" aria-pressed="false" aria-label="Região ' + (band.index + 1) + ' de 18, ' + stateLabel +
          '"><span>' + (band.index + 1) + '</span><i></i><small>' + (band.zoneAcquired ? 'zona ok' : stateLabel) + '</small></button>';
      }).join('');
      const preferred = Number.isInteger(this.selectedBandIndex)
        ? this.selectedBandIndex
        : (bands.find(item => item.state !== 'empty')?.index ?? 0);
      this.inspectBand(preferred);
    }

    inspectBand(index) {
      const band = AutoCalUxModel.bandStrip(this.snapshot || {}, this.projection).find(item => item.index === index);
      const host = document.getElementById('autocalBandInspector');
      if (!band || !host) return;
      this.selectedBandIndex = index;
      document.querySelectorAll('[data-autocal-band-index]').forEach(node => {
        const selected = Number(node.dataset.autocalBandIndex) === index;
        node.classList.toggle('selected', selected);
        node.setAttribute('aria-pressed', selected ? 'true' : 'false');
      });

      const message = AutoCalUxModel.bandNarrative(band);

      const zoneText = band.zoneAcquired
        ? 'Zona ' + (band.zone + 1) + ' confirmada pela ECU'
        : 'Zona ' + (band.zone + 1) + ' ainda não confirmada pela ECU';
      host.innerHTML = '<b>Região ' + (index + 1) + ' de 18 · ' + zoneText + '</b><span>' + message + '</span>';
    }

    renderEvents(events) {
      const host = document.getElementById('autocalEvents');
      if (!host) return;
      if (!events.length) {
        host.innerHTML = '<p class="empty-copy">Nenhum evento de maturidade foi gerado nesta leitura. Isso não apaga o que a ECU já acumulou.</p>';
        return;
      }
      host.innerHTML = events.slice(-6).reverse().map(event => {
        const correlated = String(event.correlationState || '') === 'CORRELATED';
        const rpm = finite(event.rpm);
        const confidence = Math.round((finite(event.correlationConfidence) || 0) * 100);
        return `<article data-state="${correlated ? 'correlated' : 'raw'}"><div><b>B${Number(event.bandIndex) + 1}</b><span>${escapeHtml(event.zone || 'zona')}</span></div><p>${correlated ? `${rpm === null ? 'RPM —' : `${Math.round(rpm).toLocaleString('pt-BR')} RPM`} · confiança ${confidence}%` : escapeHtml(event.correlationReason || 'NO_RELIABLE_CORRELATION')}</p><small>contador ${finite(event.counter) ?? '—'} · limiar ${finite(event.threshold) ?? '—'}</small></article>`;
      }).join('');
    }

    renderActionState() {
      const host = document.getElementById('autocalActionStatus');
      if (!host) return;
      const state = this.actionState || {};
      const name = String(state.state || 'IDLE');
      const message = String(state.message || 'Nenhuma ação preparada.');
      host.textContent = name === 'IDLE' ? message : name + ' · ' + message;
    }

    renderReview() {
      const review = document.getElementById('autocalReview');
      const prepared = this.prepared;
      if (!review || !prepared) return;
      review.hidden = false;
      review.innerHTML = `<div class="autocal-review-card"><header><div><small>REVISÃO ANTES DA ECU</small><h3>${escapeHtml(prepared.label || actionLabel(prepared.action))}</h3></div><button type="button" data-autocal-cancel class="icon-close" aria-label="Fechar revisão">×</button></header><p>${escapeHtml(prepared.description || '')}</p><div class="write-contract"><b>Nada foi enviado à ECU.</b><span>Continuar abre uma segunda confirmação Android. Só o botão positivo desse diálogo envia o comando.</span></div><details class="autocal-review-tech"><summary>Detalhes técnicos da ação</summary><dl><div><dt>Ação</dt><dd>${escapeHtml(actionLabel(prepared.action))}</dd></div><div><dt>Comando</dt><dd>${escapeHtml(prepared.commandHex || '—')}</dd></div><div><dt>Sessão</dt><dd>${escapeHtml(prepared.sessionId || '—')}</dd></div><div><dt>ECU pode alterar MUL_ACT</dt><dd>${prepared.mayChangeMulAct ? 'sim' : 'não'}</dd></div></dl></details><div class="operation-actions"><button type="button" data-autocal-cancel class="secondary">Cancelar</button><button type="button" data-autocal-confirm class="danger-primary">Continuar para confirmação Android</button></div></div>`;
    }

    renderUnavailable() {
      this.text('autocalNativeState', 'BRIDGE INDISPONÍVEL');
      this.text('autocalHumanTitle', 'AutoCal indisponível');
      this.text('autocalHumanProgress', 'A tela não recebeu o bridge nativo.');
      this.text('autocalHumanAction', 'Reconecte o serviço antes de tentar qualquer ação.');
      const host = document.getElementById('autocalReferenceChart');
      if (host) host.innerHTML = '<div class="chart-empty"><b>Sem ligação com a ECU</b><span>Nenhum dado foi inventado para preencher o gráfico.</span></div>';
    }

    text(id, value) {
      const node = document.getElementById(id);
      if (node && node.textContent !== String(value ?? '—')) node.textContent = String(value ?? '—');
    }
  }

  function boot() {
    const app = root.OmegasApp;
    if (!app?.store || !app?.scheduler || !ns.AutoCalApi) {
      root.setTimeout(boot, 25);
      return;
    }
    if (app.autoCalCockpit) return;
    app.autoCalCockpit = new AutoCalCockpit(app);
  }

  ns.AutoCalUxModel = AutoCalUxModel;
  ns.AutoCalCockpit = AutoCalCockpit;
  boot();
})(typeof window !== 'undefined' ? window : globalThis);