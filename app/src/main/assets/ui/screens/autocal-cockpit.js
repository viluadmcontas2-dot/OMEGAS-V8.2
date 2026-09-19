(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  function finite(value) {
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

  const AutoCalUxModel = {
    humanState(snapshot = {}, state = {}) {
      const enabled = finite(snapshot.autoCalEnabled ?? state.autoCalEnabled);
      const petrolZones = nativeZoneCount(snapshot, 'ACQUIRED_ZONES_PETROL');
      const gasZones = nativeZoneCount(snapshot, 'ACQUIRED_ZONES_GAS');
      const nativeStatus = snapshot.nativeStatus || state.latestSnapshot?.nativeStatus || {};
      const autoMatchCount = finite(nativeStatus.autoMatchCount ?? state.autoMatchCount);
      const maxAutoMatch = finite(snapshot.maxAutomatch ?? state.maxAutomatch);
      const title = enabled === 1 ? 'AutoCal ativo'
        : enabled === 0 ? 'AutoCal pausado'
        : snapshot.available ? 'AutoCal aguardando estado' : 'Aguardando AutoCal';
      const progress = 'Gasolina ' + petrolZones + '/4 zonas · GNV ' + gasZones + '/4 zonas';
      const autoMatch = autoMatchCount === null
        ? 'AutoMatch ainda sem contador válido'
        : Math.round(autoMatchCount) + ' AutoMatch ' + (Math.round(autoMatchCount) === 1 ? 'executado' : 'executados') +
          (maxAutoMatch === null ? '' : ' · limite configurado ' + Math.round(maxAutoMatch));
      let nextAction = 'Atualize a leitura para receber o estado nativo da ECU.';
      if (enabled === 0) nextAction = 'Retome a coleta quando quiser continuar o aprendizado nativo.';
      else if (enabled === 1 && gasZones < 4) nextAction = 'Continue dirigindo normalmente para a ECU visitar as zonas que ainda faltam.';
      else if (enabled === 1) nextAction = 'As 4 zonas GNV já foram marcadas pela ECU. Acompanhe os AutoMatch; não é necessário resetar nada.';
      return { title, progress, autoMatch, nextAction, petrolZones, gasZones, enabled, autoMatchCount, maxAutoMatch };
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
      this.actionState = {};
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
        if (this.store.get().route === 'curve' && this.active) this.refresh();
      });
    }

    inject() {
      if (!document.querySelector('link[data-autocal-cockpit-style]')) {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = 'styles-autocal-cockpit.css';
        link.dataset.autocalCockpitStyle = 'true';
        document.head.appendChild(link);
      }
      const switcher = document.getElementById('curveViewSwitch');
      if (switcher && !switcher.querySelector('[data-curve-view="autocal"]')) {
        const button = document.createElement('button');
        button.type = 'button';
        button.dataset.curveView = 'autocal';
        button.textContent = 'AutoCal';
        switcher.appendChild(button);
        this.button = button;
      } else {
        this.button = switcher?.querySelector('[data-curve-view="autocal"]') || null;
      }

      const stack = document.querySelector('[data-screen="curve"] .curve-view-stack');
      if (stack && !stack.querySelector('[data-curve-panel="autocal"]')) {
        const panel = document.createElement('div');
        panel.className = 'curve-view autocal-cockpit-view';
        panel.dataset.curvePanel = 'autocal';
        panel.innerHTML = `
          <section class="autocal-cockpit" aria-label="Auto Calibration nativa">
            <header class="autocal-hero" aria-live="polite">
              <div class="autocal-human-copy">
                <small>AGORA</small>
                <h3 id="autocalHumanTitle">Aguardando AutoCal</h3>
                <p id="autocalHumanProgress">Gasolina 0/4 zonas · GNV 0/4 zonas</p>
                <strong id="autocalHumanAction">Atualize a leitura para receber o estado nativo da ECU.</strong>
              </div>
              <div class="autocal-hero-actions">
                <span id="autocalNativeState" class="source-status">Aguardando ECU</span>
                <button type="button" data-autocal-read class="secondary">Atualizar leitura</button>
              </div>
            </header>

            <section class="autocal-reference-card">
              <div class="autocal-section-head">
                <div><small>REFERÊNCIA NATIVA</small><h4>Gasolina × GNV</h4><p>Petrol Inj. no eixo horizontal e MAP no vertical. A tela só desenha o que a ECU publicou.</p></div>
                <div class="autocal-chart-tools" aria-label="Controles do gráfico">
                  <button type="button" data-autocal-chart-action="zoom-out" aria-label="Diminuir zoom">−</button>
                  <button type="button" data-autocal-chart-action="zoom-in" aria-label="Aumentar zoom">+</button>
                  <button type="button" data-autocal-chart-action="fit">Ajustar</button>
                  <button type="button" data-autocal-history disabled>Anterior</button>
                </div>
              </div>
              <div class="autocal-chart-legend"><span class="petrol">Gasolina</span><span class="gas">GNV</span><span id="autocalReferenceCount">0 pontos nativos</span></div>
              <div id="autocalReferenceChart" class="autocal-chart-host"><div class="chart-empty">Aguardando os vetores nativos da ECU.</div></div>
              <div id="autocalChartInspector" class="autocal-inline-inspector"><b>Toque em um ponto</b><span>Veja Petrol Inj. e MAP de gasolina/GNV sem alterar nada.</span></div>
            </section>

            <section class="autocal-bands-card">
              <div class="autocal-section-head compact">
                <div><small>18 FAIXAS DE AQUISIÇÃO GNV</small><h4>Onde a ECU já passou</h4></div>
                <span id="autocalZoneSummary">0/4 zonas GNV</span>
              </div>
              <div id="autocalBands" class="autocal-band-strip" role="list"></div>
              <div id="autocalBandInspector" class="autocal-inline-inspector"><b>Toque numa faixa</b><span>Os detalhes aparecem aqui; a faixa não é um comando.</span></div>
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
        this.panel = stack?.querySelector('[data-curve-panel="autocal"]') || null;
      }
    }

    bind() {
      this.button?.addEventListener('click', () => this.open());
      document.querySelectorAll('#curveViewSwitch [data-curve-view="learning"], #curveViewSwitch [data-curve-view="editor"]').forEach(button => {
        button.addEventListener('click', () => { this.active = false; });
      });
      this.panel?.querySelector('[data-autocal-read]')?.addEventListener('click', () => this.requestRead());
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
      this.panel?.addEventListener('click', event => {
        if (event.target.closest('[data-autocal-cancel]')) this.cancelPrepared();
        if (event.target.closest('[data-autocal-confirm]')) this.confirmPrepared();
        const band = event.target.closest('[data-autocal-band-index]');
        if (band) this.inspectBand(Number(band.dataset.autocalBandIndex));
        const point = event.target.closest('[data-autocal-ref-index]');
        if (point) this.inspectReferencePoint(Number(point.dataset.autocalRefIndex));
      });
    }

    open() {
      this.active = true;
      document.querySelectorAll('#curveViewSwitch [data-curve-view]').forEach(button => button.classList.toggle('active', button === this.button));
      document.querySelectorAll('[data-screen="curve"] [data-curve-panel]').forEach(panel => panel.classList.toggle('active', panel === this.panel));
      this.refresh();
    }

    refresh() {
      if (!this.api?.available?.()) {
        this.renderUnavailable();
        return;
      }
      this.state = this.api.status() || {};
      const nextSnapshot = this.api.snapshot() || {};
      const oldHash = String(this.snapshot?.snapshotHash || '');
      const nextHash = String(nextSnapshot?.snapshotHash || '');
      if (oldHash && nextHash && oldHash !== nextHash) {
        const previous = AutoCalUxModel.referencePoints(this.snapshot);
        if (previous.length) this.previousReferencePoints = previous;
      }
      this.snapshot = nextSnapshot;
      this.actionState = this.api.actionStatus() || {};
      this.render();
    }

    requestRead() {
      if (!this.api?.available?.()) return;
      const result = this.api.startRead();
      if (result?.ok === false) {
        this.store.patch({ alert: { level: 'warning', message: result.error || 'Leitura AutoCal indisponível.' } });
      } else {
        this.store.patch({ alert: { level: 'ok', message: 'Snapshot AutoCal solicitado. A telemetria continua sob a mesma engine MP48.' } });
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

      this.text('autocalHumanTitle', human.title);
      this.text('autocalHumanProgress', human.progress);
      this.text('autocalHumanAction', human.nextAction);
      this.text('autocalHumanAutoMatch', human.autoMatch);
      this.text('autocalNativeState', state.state || (snapshot.available ? 'READY' : 'AGUARDANDO'));
      this.text('autocalZoneSummary', human.gasZones + '/4 zonas GNV');
      this.text('autocalStateRaw', state.state || '—');
      this.text('autocalEnableRaw', human.enabled === 1 ? 'ATIVA' : human.enabled === 0 ? 'PAUSADA' : '—');
      this.text('autocalSnapshotHash', snapshot.snapshotHash ? String(snapshot.snapshotHash).slice(0, 10) : '—');
      this.text('autocalMaturityRaw', events.length);

      const toggle = this.panel?.querySelector('[data-autocal-toggle]');
      if (toggle) {
        const action = AutoCalUxModel.toggleAction(human.enabled);
        toggle.dataset.action = action || '';
        toggle.disabled = !action;
        toggle.textContent = action === 'DISABLE_AUTO_CAL'
          ? 'Pausar coleta'
          : action === 'ENABLE_AUTO_CAL' ? 'Retomar coleta' : 'Aguardando estado';
      }

      const history = this.panel?.querySelector('[data-autocal-history]');
      if (history) {
        history.disabled = this.previousReferencePoints.length === 0;
        history.textContent = this.chartHistoryVisible ? 'Ocultar anterior' : 'Anterior';
      }

      this.renderReferenceChart(snapshot);
      this.renderBands(snapshot);
      this.renderEvents(events);
      this.renderActionState();
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
      this.currentReferencePoints = points;
      this.text('autocalReferenceCount', points.length + ' ponto' + (points.length === 1 ? '' : 's') + ' nativo' + (points.length === 1 ? '' : 's'));

      if (!points.length) {
        host.innerHTML = '<div class="chart-empty"><b>Referência ainda indisponível</b><span>A ECU ainda não publicou os vetores físicos necessários neste snapshot.</span></div>';
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
        .filter(value => finite(value) !== null);
      const xValues = points.map(point => point.petrolMs);
      let xMin = Math.min(...xValues);
      let xMax = Math.max(...xValues);
      let yMin = Math.min(...yValues);
      let yMax = Math.max(...yValues);
      if (xMax - xMin < 0.01) { xMin -= 0.1; xMax += 0.1; }
      if (yMax - yMin < 0.01) { yMin -= 0.02; yMax += 0.02; }
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
        return '<circle class="autocal-reference-hit" data-autocal-ref-index="' + point.index + '" cx="' + x + '" cy="' + petrolY + '" r="16"></circle>' +
          '<circle class="autocal-reference-point petrol" cx="' + x + '" cy="' + petrolY + '" r="5"></circle>' +
          '<circle class="autocal-reference-hit" data-autocal-ref-index="' + point.index + '" cx="' + x + '" cy="' + gasY + '" r="16"></circle>' +
          '<circle class="autocal-reference-point gas" cx="' + x + '" cy="' + gasY + '" r="5"></circle>';
      }).join('');

      host.innerHTML = '<svg class="autocal-reference-svg" viewBox="0 0 ' + width + ' ' + height + '" role="img" aria-label="Referência AutoCal gasolina e GNV por Petrol Inj. e MAP">' +
        grid +
        '<g data-autocal-chart-group transform="' + this.chartTransform() + '">' +
        previous +
        '<path class="autocal-reference-line petrol" d="' + pathFor(points, 'petrolMapBar') + '"></path>' +
        '<path class="autocal-reference-line gas" d="' + pathFor(points, 'gasMapBar') + '"></path>' +
        pointMarkup + labels +
        '</g></svg>';

      const svg = host.querySelector('svg');
      if (svg) this.bindChartGestures(svg);
      const selected = Number.isInteger(this.selectedReferenceIndex) ? this.selectedReferenceIndex : points[0].index;
      this.inspectReferencePoint(selected);
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
          '" role="listitem" aria-label="Faixa B' + String(band.index + 1).padStart(2, '0') + ', ' + stateLabel +
          ', contador ' + Math.round(band.counter) + '"><span>B' + String(band.index + 1).padStart(2, '0') +
          '</span><i></i><small>' + Math.round(band.counter) + '</small></button>';
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
        node.classList.toggle('selected', Number(node.dataset.autocalBandIndex) === index);
      });

      let message = band.counter > 0 ? 'A ECU registrou atividade nesta faixa.' : 'Ainda não há atividade nesta faixa.';
      if (band.state === 'anchored') message = 'Nesta leitura, a faixa amadureceu e encontrou correlação física confiável.';
      else if (band.state === 'mature') message = 'Nesta leitura, a faixa amadureceu, mas a posição física ainda não foi correlacionada com confiança.';

      const zoneText = band.zoneAcquired
        ? 'Zona ' + (band.zone + 1) + ' marcada pela ECU'
        : 'Zona ' + (band.zone + 1) + ' ainda não marcada pela ECU';
      const event = band.event;
      const detail = event
        ? ' · contador ' + (finite(event.counter) ?? Math.round(band.counter)) + ' / limiar ' + (finite(event.threshold) ?? '—')
        : ' · contador ' + Math.round(band.counter);
      host.innerHTML = '<b>B' + String(index + 1).padStart(2, '0') + ' · ' + zoneText + '</b><span>' + message + detail + '</span>';
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
      review.innerHTML = `<div class="autocal-review-card"><header><div><small>REVISÃO ANTES DA ECU</small><h3>${escapeHtml(prepared.label || actionLabel(prepared.action))}</h3></div><button type="button" data-autocal-cancel class="icon-close">×</button></header><p>${escapeHtml(prepared.description || '')}</p><dl><div><dt>Ação</dt><dd>${escapeHtml(actionLabel(prepared.action))}</dd></div><div><dt>Comando</dt><dd>${escapeHtml(prepared.commandHex || '—')}</dd></div><div><dt>Sessão</dt><dd>${escapeHtml(prepared.sessionId || '—')}</dd></div><div><dt>ECU pode alterar MUL_ACT</dt><dd>${prepared.mayChangeMulAct ? 'sim' : 'não'}</dd></div></dl><div class="write-contract"><b>Ainda não foi enviado.</b><span>Continuar abre uma segunda confirmação Android. Só o botão positivo desse diálogo envia o comando.</span></div><div class="operation-actions"><button type="button" data-autocal-cancel class="secondary">Cancelar</button><button type="button" data-autocal-confirm class="danger-primary">Continuar para confirmação Android</button></div></div>`;
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
