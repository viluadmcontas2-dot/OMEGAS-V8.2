(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

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

  function nativeZoneCount(snapshot, key) {
    return vector(snapshot, key).slice(0, 4).filter(value => value > 0).length;
  }

  function scalarValue(snapshot, key) {
    const item = field(snapshot, key);
    const values = Array.isArray(item?.rawValues) ? item.rawValues : [];
    return values.length === 1 ? finite(values[0]) : null;
  }

  const AutoCalUxModel = {
    humanState(snapshot = {}, state = {}) {
      const nativeSnapshot = state.latestSnapshot?.fields ? state.latestSnapshot : {};
      const evidenceSnapshot = nativeSnapshot.fields ? nativeSnapshot : snapshot;
      const enabled = finite(state.autoCalEnabled ?? nativeSnapshot.autoCalEnabled ?? scalarValue(nativeSnapshot, 'AUTO_CAL_ENABLE'));
      const petrolZones = nativeZoneCount(evidenceSnapshot, 'ACQUIRED_ZONES_PETROL');
      const gasZones = nativeZoneCount(evidenceSnapshot, 'ACQUIRED_ZONES_GAS');
      const nativeStatus = nativeSnapshot.nativeStatus || {};
      const autoMatchCount = finite(state.autoMatchCount ?? nativeStatus.autoMatchCount ?? scalarValue(nativeSnapshot, 'NUM_AUTOMATCH_EXECUTED'));
      const maxAutoMatch = finite(state.maxAutomatch ?? nativeSnapshot.maxAutomatch ?? scalarValue(nativeSnapshot, 'MAX_AUTOMATCH'));
      const acquisitionState = String(state.state || '').toUpperCase();
      const title = acquisitionState === 'PROBE_FAILED' || acquisitionState === 'FAILED'
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
      if (acquisitionState === 'PROBE_FAILED' || acquisitionState === 'FAILED') {
        nextAction = String(state.message || state.error || 'Não foi possível ler o estado nativo.') + ' · Verifique a conexão e tente consultar novamente.';
      } else if (enabled === 0) nextAction = 'Inicie a aquisição quando quiser continuar o aprendizado nativo.';
      else if (enabled === 1 && gasZones < 4) nextAction = 'Aquisição habilitada. Mantenha condições estáveis para visitar as regiões que ainda faltam.';
      else if (enabled === 1) nextAction = 'As 4 zonas GNV já foram marcadas pela ECU. Continue acompanhando sem resetar dados.';
      return { title, progress, autoMatch, nextAction, petrolZones, gasZones, enabled, autoMatchCount, maxAutoMatch };
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
      const warning = dropped > 0 || lastError.length > 0;
      const title = recording ? 'Sessão atual' : summary?.sessionId ? 'Última sessão' : 'Sessões prontas';
      const detail = (recording ? minutes + ' min' : 'histórico preservado') +
        ' · ' + regions + ' ' + (regions === 1 ? 'região correlacionada' : 'regiões correlacionadas') +
        ' · GNV ' + gasZones + '/4';
      const next = warning
        ? 'Há uma lacuna na gravação da evidência. Veja os detalhes antes de usar esta sessão em análise.'
        : recording ? 'Evidência AutoCal sendo preservada nesta sessão.' : 'Abra Sessões para revisar ou exportar o histórico.';
      return { title, detail, next, level: warning ? 'warning' : 'ok', recording, minutes, regions, gasZones, dropped };
    },

    livePoint(telemetry = {}) {
      const source = telemetry || {};
      if (source.valid === false) return null;
      const live = source.live || source.data || source;
      const petrolMs = finite(live.petrol_ms ?? live.petrolMs);
      const mapBar = finite(live.load_bar ?? live.map_bar ?? live.mapBar);
      const rpm = finite(live.rpm);
      if (petrolMs === null || mapBar === null) return null;
      return { petrolMs, mapBar, rpm, fuel: String(live.fuel || live.state || '—'), sequence: finite(source.sequence), ageMs: finite(source.telemetryAgeMs ?? source.ageMs) };
    },

    referencePoints(snapshot = {}) {
      const petrolMs = physicalVector(snapshot, 'PETR_INJ_TBP');
      const petrolMap = physicalVector(snapshot, 'PETR_MNFLD_PRESS_RV');
      const gasMap = physicalVector(snapshot, 'GAS_MNFLD_PRESS_RV');
      const count = Math.min(petrolMs.length, petrolMap.length, gasMap.length);
      const points = [];
      for (let index = 0; index < count; index += 1) {
        const x = finite(petrolMs[index]);
        const petrol = finite(petrolMap[index]);
        const gas = finite(gasMap[index]);
        if (x !== null && petrol !== null && gas !== null) {
          points.push({ index, petrolMs: x, petrolMapBar: petrol, gasMapBar: gas });
        }
      }
      return points;
    },

    bandStrip(snapshot = {}) {
      const counters = vector(snapshot, 'NUM_BUF_UPD_GAS');
      const zones = vector(snapshot, 'ACQUIRED_ZONES_GAS');
      const events = Array.isArray(snapshot.nativeMaturityEvents) ? snapshot.nativeMaturityEvents : [];
      const byBand = new Map(events.map(event => [Number(event?.bandIndex), event]));
      return Array.from({ length: 18 }, (_, index) => {
        const counter = finite(counters[index]) ?? 0;
        const zone = zoneForBand(index);
        const event = byBand.get(index) || null;
        const correlated = String(event?.correlationState || '') === 'CORRELATED';
        const stateName = correlated ? 'anchored' : event ? 'mature' : counter > 0 ? 'activity' : 'empty';
        return {
          index,
          zone,
          counter,
          zoneAcquired: (finite(zones[zone]) ?? 0) > 0,
          state: stateName,
          event,
        };
      });
    },

    toggleAction(enabled) {
      const value = finite(enabled);
      if (value === 1) return 'DISABLE_AUTO_CAL';
      if (value === 0) return 'ENABLE_AUTO_CAL';
      return null;
    },

    updateChartView(current, action, payload = {}) {
      const base = {
        zoom: Math.max(1, Math.min(3, finite(current?.zoom) ?? 1)),
        panX: finite(current?.panX) ?? 0,
        panY: finite(current?.panY) ?? 0,
      };
      if (action === 'fit') return { zoom: 1, panX: 0, panY: 0 };
      if (action === 'zoom-in') return { ...base, zoom: Math.min(3, base.zoom + 0.25) };
      if (action === 'zoom-out') {
        const zoom = Math.max(1, base.zoom - 0.25);
        return zoom === 1 ? { zoom: 1, panX: 0, panY: 0 } : { ...base, zoom };
      }
      if (action === 'pinch') {
        const zoom = Math.max(1, Math.min(3, finite(payload.zoom) ?? base.zoom));
        return zoom === 1 ? { zoom: 1, panX: 0, panY: 0 } : { ...base, zoom };
      }
      if (action === 'pan') {
        if (base.zoom <= 1) return base;
        const limitX = 240 * (base.zoom - 1);
        const limitY = 100 * (base.zoom - 1);
        const dx = finite(payload.dx) ?? 0;
        const dy = finite(payload.dy) ?? 0;
        return {
          ...base,
          panX: Math.max(-limitX, Math.min(limitX, base.panX + dx)),
          panY: Math.max(-limitY, Math.min(limitY, base.panY + dy)),
        };
      }
      return base;
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
      this.readerState = {};
      this.readerSnapshot = {};
      this.acquisitionState = {};
      this.acquisitionSnapshot = {};
      this.actionState = {};
      this.sessionState = {};
      this.sessions = [];
      this.sessionDrawerOpen = false;
      this.chartScale = null;
      this.chartView = AutoCalUxModel.updateChartView(null, 'fit');
      this.chartPointers = new Map();
      this.pinchBase = null;
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
            </header>

            <section class="autocal-session-strip" data-session-level="ok" aria-live="polite">
              <div class="autocal-session-copy">
                <small>SESSÃO</small>
                <b id="autocalSessionSummary">Sessões prontas</b>
                <span id="autocalSessionDetail">Histórico ainda sem dados desta conexão.</span>
              </div>
              <div class="autocal-session-actions">
                <span id="autocalSessionState">Persistência pronta</span>
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
                  <button type="button" data-autocal-chart-action="zoom-out" aria-label="Diminuir zoom">−</button>
                  <button type="button" data-autocal-chart-action="zoom-in" aria-label="Aumentar zoom">+</button>
                  <button type="button" data-autocal-chart-action="fit">Ver tudo</button>
                  <button type="button" data-autocal-history disabled aria-label="Mostrar leitura anterior">Leitura anterior</button>
                </div>
              </div>
              <div class="autocal-chart-legend"><span class="petrol">Gasolina</span><span class="gas">GNV</span><span class="live">AGORA</span><span id="autocalReferenceCount">0 pontos nativos</span></div>
              <div id="autocalReferenceChart" class="autocal-chart-host"><div class="chart-empty">Aguardando os vetores nativos da ECU.</div></div>
              <div id="autocalChartInspector" class="autocal-inline-inspector"><b>Toque em um ponto</b><span>Veja Petrol Inj. e MAP de gasolina/GNV sem alterar nada.</span></div>
            </section>

            <section class="autocal-now-card" aria-live="polite">
              <div class="autocal-section-head compact"><div><small>O QUE ESTÁ ACONTECENDO AGORA</small><h4 id="autocalLiveTitle">Aguardando telemetria</h4></div><span id="autocalLiveFuel">—</span></div>
              <div class="autocal-now-values"><div><small>RPM</small><b id="autocalLiveRpm">—</b></div><div><small>PETROL INJ.</small><b><span id="autocalLivePetrol">—</span> ms</b></div><div><small>MAP</small><b><span id="autocalLiveMap">—</span> bar</b></div></div>
              <p id="autocalLiveNarrative">O cursor AGORA aparece quando a telemetria MP48 é válida. Ele nunca vira evidência adquirida.</p>
            </section>

            <section class="autocal-read-card" data-read-level="neutral">
              <div><small>CONSULTA À ECU</small><b id="autocalReadTitle">Leitura pronta para iniciar</b><span id="autocalReadDetail">Nenhuma consulta manual em andamento.</span></div>
              <p id="autocalReadNext">Use Consultar ECU para obter um snapshot completo.</p>
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
        if (action) this.prepare(action);
      });
      this.panel?.querySelectorAll('[data-autocal-action]').forEach(button => {
        button.addEventListener('click', () => this.prepare(button.dataset.autocalAction));
      });
      this.panel?.querySelectorAll('[data-autocal-chart-action]').forEach(button => {
        button.addEventListener('click', () => {
          this.chartView = AutoCalUxModel.updateChartView(this.chartView, button.dataset.autocalChartAction);
          this.applyChartTransform();
        });
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
        if (this.sessionDrawerOpen) this.renderSessionState();
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
      this.readerState = this.api.readerStatus() || {};
      this.readerSnapshot = this.api.readerSnapshot() || {};
      const acquisitionStatus = this.api.acquisitionStatus() || {};
      this.acquisitionSnapshot = this.api.acquisitionSnapshot() || {};
      this.acquisitionState = {
        ...acquisitionStatus,
        latestSnapshot: this.acquisitionSnapshot?.available
          ? this.acquisitionSnapshot
          : acquisitionStatus.latestSnapshot,
      };
      this.state = this.acquisitionState;

      const readerStateName = String(this.readerState?.state || '').toUpperCase();
      const readerReady = ['READY', 'READY_PARTIAL'].includes(readerStateName) && this.readerSnapshot?.available !== false;
      const nextSnapshot = readerReady
        ? this.readerSnapshot
        : this.acquisitionSnapshot?.available
          ? this.acquisitionSnapshot
          : this.readerSnapshot?.available
            ? this.readerSnapshot
            : this.acquisitionSnapshot || {};

      const oldHash = String(this.snapshot?.snapshotHash || '');
      const nextHash = String(nextSnapshot?.snapshotHash || '');
      if (oldHash && nextHash && oldHash !== nextHash) {
        const previous = AutoCalUxModel.referencePoints(this.snapshot);
        if (previous.length) this.previousReferencePoints = previous;
      }
      this.snapshot = nextSnapshot || {};
      this.actionState = this.api.actionStatus() || {};
      this.sessionState = this.api.sessionStatus?.() || {};
      this.sessions = this.api.sessions?.() || [];
      this.render();
    }

    requestRead() {
      if (!this.api?.available?.()) return;
      const result = this.api.startRead();
      this.readerState = result && typeof result === 'object' ? result : this.api.readerStatus() || {};
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
      const events = Array.isArray(snapshot.nativeMaturityEvents) ? snapshot.nativeMaturityEvents : [];
      const human = AutoCalUxModel.humanState(snapshot, state);
      const acquisitionName = String(state.state || '').toUpperCase();
      const acquisitionLabel = acquisitionName === 'WAITING_TELEMETRY_SETTLE' ? 'conectando'
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
      this.text('autocalMaturityRaw', events.length);
      this.renderZoneMeter(human);
      this.renderReadState();
      this.renderSessionState();
      this.renderLiveNarrative();

      const toggle = this.panel?.querySelector('[data-autocal-toggle]');
      if (toggle) {
        const action = AutoCalUxModel.toggleAction(human.enabled);
        toggle.dataset.action = action || '';
        toggle.disabled = !action;
        toggle.textContent = action === 'DISABLE_AUTO_CAL'
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
      const card = this.panel?.querySelector('.autocal-read-card');
      if (card) card.dataset.readLevel = read.level;
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
      this.text('autocalSessionState', narrative.recording ? 'Salvando evidência' : 'Persistência pronta');
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
      const live = AutoCalUxModel.livePoint(this.store.get().telemetry || {});
      if (!live) {
        this.text('autocalLiveTitle', 'Aguardando telemetria válida');
        this.text('autocalLiveFuel', '—');
        this.text('autocalLiveRpm', '—');
        this.text('autocalLivePetrol', '—');
        this.text('autocalLiveMap', '—');
        this.text('autocalLiveNarrative', 'O cursor AGORA aparece quando RPM, Petrol Inj. e MAP chegam válidos. Ele nunca vira evidência adquirida.');
        return;
      }
      const rpmLabel = live.rpm === null ? 'RPM —' : Math.round(live.rpm).toLocaleString('pt-BR') + ' RPM';
      this.text('autocalLiveTitle', 'Motor nesta região agora');
      this.text('autocalLiveFuel', live.fuel);
      this.text('autocalLiveRpm', live.rpm === null ? '—' : Math.round(live.rpm).toLocaleString('pt-BR'));
      this.text('autocalLivePetrol', live.petrolMs.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }));
      this.text('autocalLiveMap', live.mapBar.toLocaleString('pt-BR', { minimumFractionDigits: 3, maximumFractionDigits: 3 }));
      const enabled = AutoCalUxModel.humanState(this.snapshot || {}, this.acquisitionState || {}).enabled;
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
      if (!scale || !layer) {
        this.renderReferenceChart(this.snapshot || {});
        return;
      }
      layer.removeAttribute('display');
      if (live.petrolMs < scale.xMin || live.petrolMs > scale.xMax || live.mapBar < scale.yMin || live.mapBar > scale.yMax) {
        this.renderReferenceChart(this.snapshot || {});
        return;
      }
      const x = scale.xFor(live.petrolMs);
      const y = scale.yFor(live.mapBar);
      this.panel?.querySelectorAll('[data-autocal-live-point]').forEach(node => {
        node.setAttribute('cx', x.toFixed(1));
        node.setAttribute('cy', y.toFixed(1));
      });
      const label = this.panel?.querySelector('[data-autocal-live-label]');
      if (label) {
        label.setAttribute('x', (x + 12).toFixed(1));
        label.setAttribute('y', (y - 12).toFixed(1));
      }
    }

    renderZoneMeter(human) {
      const meter = document.getElementById('autocalZoneMeter');
      if (!meter) return;
      const petrolZones = Math.max(0, Math.min(4, Math.round(finite(human?.petrolZones) ?? 0)));
      const gasZones = Math.max(0, Math.min(4, Math.round(finite(human?.gasZones) ?? 0)));
      meter.setAttribute('aria-label', 'Gasolina ' + petrolZones + ' de 4 zonas, GNV ' + gasZones + ' de 4 zonas');
      this.panel?.querySelectorAll('[data-autocal-zone-petrol]').forEach(node => {
        node.dataset.active = Number(node.dataset.autocalZonePetrol) < petrolZones ? 'true' : 'false';
      });
      this.panel?.querySelectorAll('[data-autocal-zone-gas]').forEach(node => {
        node.dataset.active = Number(node.dataset.autocalZoneGas) < gasZones ? 'true' : 'false';
      });
    }

    chartTransform() {
      const zoom = finite(this.chartView?.zoom) ?? 1;
      const panX = finite(this.chartView?.panX) ?? 0;
      const panY = finite(this.chartView?.panY) ?? 0;
      return 'translate(' + (500 + panX) + ' ' + (120 + panY) + ') scale(' + zoom + ') translate(-500 -120)';
    }

    renderReferenceChart(snapshot) {
      const host = document.getElementById('autocalReferenceChart');
      if (!host) return;
      const points = AutoCalUxModel.referencePoints(snapshot);
      const live = AutoCalUxModel.livePoint(this.store.get().telemetry || {});
      this.currentReferencePoints = points;
      this.text('autocalReferenceCount', points.length + ' ponto' + (points.length === 1 ? '' : 's') + ' nativo' + (points.length === 1 ? '' : 's'));

      if (!points.length && !live) {
        this.chartScale = null;
        host.innerHTML = '<div class="chart-empty"><b>Referência ainda indisponível</b><span>Aguardando vetores nativos e telemetria válida. Nenhum ponto é inventado.</span></div>';
        this.text('autocalChartInspector', 'Aguardando Petrol Inj. e MAP nativos.');
        return;
      }

      const width = 1000;
      const height = 240;
      const padX = 48;
      const padY = 24;
      const history = this.chartHistoryVisible ? this.previousReferencePoints : [];
      const yValues = points.flatMap(point => [point.petrolMapBar, point.gasMapBar])
        .concat(history.flatMap(point => [point.petrolMapBar, point.gasMapBar]))
        .concat(live ? [live.mapBar] : [])
        .filter(value => finite(value) !== null);
      const xValues = points.map(point => point.petrolMs).concat(live ? [live.petrolMs] : []);
      let xMin = Math.min(...xValues);
      let xMax = Math.max(...xValues);
      let yMin = Math.min(...yValues);
      let yMax = Math.max(...yValues);
      if (xMax - xMin < 0.01) { xMin -= Math.max(0.25, Math.abs(xMin) * 0.08); xMax += Math.max(0.25, Math.abs(xMax) * 0.08); }
      if (yMax - yMin < 0.01) { yMin -= 0.08; yMax += 0.08; }
      const yPad = Math.max(0.02, (yMax - yMin) * 0.12);
      yMin -= yPad;
      yMax += yPad;

      const xFor = value => padX + ((value - xMin) / (xMax - xMin)) * (width - padX * 2);
      const yFor = value => height - padY - ((value - yMin) / (yMax - yMin)) * (height - padY * 2);
      const pathFor = (items, key) => items.map((point, index) =>
        (index ? 'L' : 'M') + ' ' + xFor(point.petrolMs).toFixed(1) + ' ' + yFor(point[key]).toFixed(1)
      ).join(' ');

      const grid = Array.from({ length: 5 }, (_, index) => {
        const y = padY + index * ((height - padY * 2) / 4);
        return '<line class="autocal-grid-line" x1="' + padX + '" y1="' + y.toFixed(1) + '" x2="' + (width - padX) + '" y2="' + y.toFixed(1) + '"></line>';
      }).join('');

      const labels = points.map((point, index) => {
        if (index % 5 !== 0 && index !== points.length - 1) return '';
        return '<text class="autocal-axis-label" x="' + xFor(point.petrolMs).toFixed(1) + '" y="' + (height - 5) + '" text-anchor="middle">' + point.petrolMs.toFixed(1) + ' ms</text>';
      }).join('');

      const previous = history.length
        ? '<path class="autocal-reference-line previous petrol" d="' + pathFor(history, 'petrolMapBar') + '"></path>' +
          '<path class="autocal-reference-line previous gas" d="' + pathFor(history, 'gasMapBar') + '"></path>'
        : '';

      const pointMarkup = points.map(point => {
        const x = xFor(point.petrolMs).toFixed(1);
        const petrolY = yFor(point.petrolMapBar).toFixed(1);
        const gasY = yFor(point.gasMapBar).toFixed(1);
        return '<circle class="autocal-reference-hit" data-autocal-ref-index="' + point.index + '" cx="' + x + '" cy="' + petrolY + '" r="22"></circle>' +
          '<circle class="autocal-reference-point petrol" cx="' + x + '" cy="' + petrolY + '" r="5"></circle>' +
          '<circle class="autocal-reference-hit" data-autocal-ref-index="' + point.index + '" cx="' + x + '" cy="' + gasY + '" r="22"></circle>' +
          '<circle class="autocal-reference-point gas" cx="' + x + '" cy="' + gasY + '" r="5"></circle>';
      }).join('');
      const liveMarkup = live
        ? '<g class="autocal-live-layer" aria-label="Posição atual do motor">' +
            '<circle class="autocal-live-halo" data-autocal-live-point cx="' + xFor(live.petrolMs).toFixed(1) + '" cy="' + yFor(live.mapBar).toFixed(1) + '" r="13"></circle>' +
            '<circle class="autocal-live-point" data-autocal-live-point cx="' + xFor(live.petrolMs).toFixed(1) + '" cy="' + yFor(live.mapBar).toFixed(1) + '" r="6"></circle>' +
            '<text class="autocal-live-label" data-autocal-live-label x="' + (xFor(live.petrolMs) + 12).toFixed(1) + '" y="' + (yFor(live.mapBar) - 12).toFixed(1) + '">AGORA</text>' +
          '</g>'
        : '';
      this.chartScale = { xMin, xMax, yMin, yMax, xFor, yFor };

      host.innerHTML = '<svg class="autocal-reference-svg" viewBox="0 0 ' + width + ' ' + height + '" role="img" aria-label="Referência AutoCal gasolina, GNV e posição AGORA por Petrol Inj. e MAP">' +
        grid +
        '<g data-autocal-chart-group transform="' + this.chartTransform() + '">' +
        previous +
        '<path class="autocal-reference-line petrol" d="' + pathFor(points, 'petrolMapBar') + '"></path>' +
        '<path class="autocal-reference-line gas" d="' + pathFor(points, 'gasMapBar') + '"></path>' +
        pointMarkup + liveMarkup + labels +
        '</g></svg>';

      const svg = host.querySelector('svg');
      if (svg) this.bindChartGestures(svg);
      if (points.length) {
        const selected = Number.isInteger(this.selectedReferenceIndex) ? this.selectedReferenceIndex : points[0].index;
        this.inspectReferencePoint(selected);
      } else {
        this.text('autocalChartInspector', 'Somente o cursor AGORA está disponível; a ECU ainda não publicou a referência nativa.');
      }
      this.renderLiveNarrative();
    }

    bindChartGestures(svg) {
      const distance = values => {
        if (values.length < 2) return 0;
        const a = values[0];
        const b = values[1];
        return Math.hypot(a.x - b.x, a.y - b.y);
      };
      svg.addEventListener('pointerdown', event => {
        this.chartPointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
        try { svg.setPointerCapture(event.pointerId); } catch (_) {}
        if (this.chartPointers.size === 2) {
          this.pinchBase = { distance: distance([...this.chartPointers.values()]), zoom: this.chartView.zoom };
        }
      });
      svg.addEventListener('pointermove', event => {
        const previous = this.chartPointers.get(event.pointerId);
        if (!previous) return;
        this.chartPointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
        const values = [...this.chartPointers.values()];
        if (values.length >= 2 && this.pinchBase?.distance > 0) {
          const ratio = distance(values) / this.pinchBase.distance;
          this.chartView = AutoCalUxModel.updateChartView(this.chartView, 'pinch', { zoom: this.pinchBase.zoom * ratio });
          this.applyChartTransform();
          return;
        }
        if (values.length === 1) {
          this.chartView = AutoCalUxModel.updateChartView(this.chartView, 'pan', {
            dx: event.clientX - previous.x,
            dy: event.clientY - previous.y,
          });
          this.applyChartTransform();
        }
      });
      const end = event => {
        this.chartPointers.delete(event.pointerId);
        if (this.chartPointers.size < 2) this.pinchBase = null;
        try { svg.releasePointerCapture(event.pointerId); } catch (_) {}
      };
      svg.addEventListener('pointerup', end);
      svg.addEventListener('pointercancel', end);
    }

    applyChartTransform() {
      const group = document.querySelector('#autocalReferenceChart [data-autocal-chart-group]');
      if (group) group.setAttribute('transform', this.chartTransform());
    }

    inspectReferencePoint(index) {
      const host = document.getElementById('autocalChartInspector');
      const point = this.currentReferencePoints.find(item => Number(item.index) === Number(index));
      if (!host || !point) return;
      this.selectedReferenceIndex = point.index;
      host.innerHTML = '<b>Ponto ' + (point.index + 1) + ' · ' + point.petrolMs.toFixed(2) + ' ms</b>' +
        '<span>MAP gasolina ' + point.petrolMapBar.toFixed(3) + ' bar · MAP GNV ' + point.gasMapBar.toFixed(3) + ' bar</span>';
      document.querySelectorAll('#autocalReferenceChart [data-autocal-ref-index]').forEach(node => {
        node.classList.toggle('selected', Number(node.dataset.autocalRefIndex) === Number(point.index));
      });
    }

    renderBands(snapshot) {
      const host = document.getElementById('autocalBands');
      if (!host) return;
      const bands = AutoCalUxModel.bandStrip(snapshot);
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
      const band = AutoCalUxModel.bandStrip(this.snapshot || {}).find(item => item.index === index);
      const host = document.getElementById('autocalBandInspector');
      if (!band || !host) return;
      this.selectedBandIndex = index;
      document.querySelectorAll('[data-autocal-band-index]').forEach(node => {
        const selected = Number(node.dataset.autocalBandIndex) === index;
        node.classList.toggle('selected', selected);
        node.setAttribute('aria-pressed', selected ? 'true' : 'false');
      });

      let message = band.counter > 0 ? 'A ECU registrou atividade nesta região.' : 'Ainda não há atividade nesta região.';
      if (band.state === 'anchored') message = 'Nesta leitura, a região amadureceu e encontrou correlação física confiável.';
      else if (band.state === 'mature') message = 'Nesta leitura, a região amadureceu; a correlação física ainda não foi confirmada com confiança.';

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