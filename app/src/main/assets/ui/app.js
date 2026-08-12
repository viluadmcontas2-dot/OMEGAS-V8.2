(function (root) {
  'use strict';

  const ui = root.OmegasUi || {};
  if (!ui.Store || !ui.NativeApi || !ui.Router || !ui.Scheduler) {
    console.error('[OMEGAS] Fundação da UI não carregada.');
    return;
  }

  const refinementStyle = document.createElement('link');
  refinementStyle.rel = 'stylesheet';
  refinementStyle.href = 'styles-refine.css';
  document.head.appendChild(refinementStyle);

  const api = new ui.NativeApi();
  const store = new ui.Store(ui.createInitialState());
  const router = new ui.Router(store);
  const instances = {};
  const utilities = ui.Drawers ? new ui.Drawers(store, router, api) : null;
  const selectedSuggestionIds = new Set();

  const routeMeta = {
    dashboard: ['AGORA', 'Agora'],
    learning: ['APRENDER', 'Aprender'],
    map: ['AJUSTE LOCAL', 'Ajuste local'],
    curve: ['AJUSTE GLOBAL', 'Ajuste global'],
    obd: ['OBSERVAR', 'OBD'],
    suggestions: ['DECIDIR', 'Sugestões'],
    tools: ['SISTEMA', 'Ferramentas'],
  };

  let renderedRoute = null;
  let previousGlobalSignature = '';
  let previousTelemetrySignature = '';
  let previousStatusSignature = '';
  let previousAlert = null;
  let previousLearningLayer = null;
  let toastTimer = null;
  let routeButtons = [];
  let screenNodes = [];

  function byId(id) { return document.getElementById(id); }
  function setText(id, value) {
    const node = byId(id);
    if (!node) return;
    const next = value == null ? '—' : String(value);
    if (node.textContent !== next) node.textContent = next;
  }
  function finite(value) { return Number.isFinite(Number(value)) ? Number(value) : null; }
  function rounded(value, digits) {
    const number = finite(value);
    if (number === null) return '—';
    const factor = 10 ** digits;
    return String(Math.round(number * factor) / factor);
  }
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[char]));
  }
  function fuelLabel(raw) {
    const value = String(raw || '—').toUpperCase();
    if (value.includes('PETROL') || value.includes('GASOLINA')) return 'GASOLINA';
    if (value.includes('CNG') || value.includes('GNV') || value.includes('GAS')) return 'GNV';
    if (value.includes('CUTOFF')) return 'CUTOFF';
    return value || '—';
  }
  function liveFrom(state) {
    const telemetry = state.telemetry || {};
    return telemetry.live || telemetry.data || telemetry;
  }
  function curveEvidenceVisible() {
    return document.querySelector('[data-screen="curve"] .evidence-disclosure')?.open === true;
  }
  function readCalibrationState() {
    if (api.isDemo()) return { ready: true, suggestionItems: [], suggestionPending: 0, suggestionObserving: 0, suggestionApplied: 0, suggestionSuperseded: 0 };
    const bridge = api.v7;
    if (!bridge || typeof bridge.getState !== 'function') return {};
    try {
      const raw = bridge.getState();
      return typeof raw === 'string' ? JSON.parse(raw) : (raw || {});
    } catch (_) {
      return {};
    }
  }

  function ensureScreen(route) {
    if (instances[route]) return instances[route];
    if (route === 'dashboard' && ui.DashboardScreen) instances.dashboard = new ui.DashboardScreen(store, api);
    if (route === 'learning' && ui.LearningScreen) instances.learning = new ui.LearningScreen(store, router, api);
    if (route === 'map' && ui.MapScreen) instances.map = new ui.MapScreen(store, api, router);
    if (route === 'curve' && ui.CurveScreen) instances.curve = new ui.CurveScreen(store, api);
    if (route === 'obd' && ui.ObdScreen) instances.obd = new ui.ObdScreen(store, api);
    return instances[route] || null;
  }

  function renderShell(state) {
    if (state.route !== renderedRoute) {
      renderedRoute = state.route;
      const meta = routeMeta[state.route] || routeMeta.dashboard;
      setText('routeEyebrow', meta[0]);
      setText('routeTitle', meta[1]);
      routeButtons.forEach(button => {
        const active = button.dataset.route === state.route;
        button.classList.toggle('active', active);
        button.setAttribute('aria-current', active ? 'page' : 'false');
      });
      screenNodes.forEach(screen => {
        const active = screen.dataset.screen === state.route;
        screen.classList.toggle('active', active);
        screen.setAttribute('aria-hidden', active ? 'false' : 'true');
      });
    }

    const status = state.status || {};
    const obdStatus = state.obd || {};
    const obdOnline = obdStatus.connected === true || ['CONNECTED', 'CONECTADO', 'REMOTO AO VIVO'].includes(String(obdStatus.state || obdStatus.status || '').toUpperCase());
    const fuel = fuelLabel(status.fuelState || liveFrom(state).fuel || liveFrom(state).state);
    const globalSignature = `${status.usbConnected === true ? 1 : 0}:${obdOnline ? 1 : 0}:${fuel}`;
    if (globalSignature !== previousGlobalSignature) {
      previousGlobalSignature = globalSignature;
      const ecu = byId('globalEcu');
      if (ecu) {
        const online = status.usbConnected === true;
        ecu.dataset.online = online ? 'true' : 'false';
        setText('globalEcu', online ? 'ECU online' : 'ECU offline');
      }
      const obdNode = byId('globalObd');
      if (obdNode) {
        obdNode.dataset.online = obdOnline ? 'true' : 'false';
        setText('globalObd', obdOnline ? 'OBD online' : 'OBD offline');
      }
      const fuelNode = byId('globalFuel');
      if (fuelNode) {
        fuelNode.dataset.fuel = fuel;
        setText('globalFuel', fuel);
      }
    }

    const pending = Number(state.calibrationState?.suggestionPending || 0);
    setText('suggestionCount', pending);

    if (state.learningLayer !== previousLearningLayer && state.route === 'learning') {
      previousLearningLayer = state.learningLayer;
      ensureScreen('learning')?.render(state);
    }

    if (state.alert && state.alert !== previousAlert) {
      previousAlert = state.alert;
      showAlert(state.alert);
    }
  }

  function showAlert(alert) {
    const toast = byId('alertToast');
    if (!toast || !alert) return;
    toast.dataset.level = alert.level || 'warning';
    const label = toast.querySelector('b');
    const message = alert.message || String(alert);
    if (label && label.textContent !== message) label.textContent = message;
    toast.classList.add('show');
    if (toastTimer) root.clearTimeout(toastTimer);
    toastTimer = root.setTimeout(() => toast.classList.remove('show'), 3600);
  }

  function telemetryVisualSignature(telemetry, route) {
    const source = telemetry || {};
    const live = source.live || source.data || source;
    if (route === 'dashboard') {
      return [
        source.valid === false ? 0 : 1,
        rounded(live.rpm, 0),
        rounded(live.petrol_ms ?? live.petrolMs, 2),
        rounded(live.gas_ms_diagnostic ?? live.gasMs, 2),
        rounded(live.load_bar ?? live.map_bar ?? live.mapBar, 2),
        String(live.fuel || live.state || ''),
      ].join('|');
    }
    const interpolation = source.interpolation || {};
    const cell = interpolation.cell || {};
    return [
      source.valid === false ? 0 : 1,
      Math.round((finite(interpolation.rpm ?? live.rpm) || 0) / 25) * 25,
      Math.round((finite(interpolation.petrolMs ?? live.petrol_ms ?? live.petrolMs) || 0) * 20) / 20,
      Number.isFinite(Number(cell.row)) ? Number(cell.row) : '-',
      Number.isFinite(Number(cell.column)) ? Number(cell.column) : '-',
    ].join('|');
  }

  function renderLightLiveContext(state, route) {
    const interpolation = state.telemetry?.interpolation || {};
    const cell = interpolation.cell || {};
    const rpm = finite(interpolation.rpm ?? liveFrom(state).rpm);
    const petrolMs = finite(interpolation.petrolMs ?? liveFrom(state).petrol_ms ?? liveFrom(state).petrolMs);
    const row = Number.isFinite(Number(cell.row)) ? Number(cell.row) : null;
    const column = Number.isFinite(Number(cell.column)) ? Number(cell.column) : null;
    const position = row !== null && column !== null ? ` · célula ${row + 1}×${column + 1}` : '';
    const label = rpm !== null && petrolMs !== null
      ? `${Math.round(rpm).toLocaleString('pt-BR')} RPM · ${petrolMs.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ms${position}`
      : 'Aguardando condição válida';
    if (route === 'learning') setText('learningLiveLabel', label);
    if (route === 'map') ensureScreen('map')?.renderLiveContext?.({ rpm, petrolMs, row, column, label });
  }

  function refreshFast() {
    const route = store.get().route;
    if (route === 'dashboard' || route === 'learning' || route === 'map') {
      const telemetry = api.telemetry() || {};
      const signature = `${route}:${telemetryVisualSignature(telemetry, route)}`;
      if (signature !== previousTelemetrySignature) {
        previousTelemetrySignature = signature;
        store.patch({ telemetry });
        const state = store.get();
        if (route === 'dashboard') ensureScreen('dashboard')?.render(state);
        if (route === 'learning' || route === 'map') renderLightLiveContext(state, route);
      }
    }

    const state = store.get();
    if (instances.map && (state.map?.state === 'writing' || state.map?.state === 'reading')) instances.map.poll();
    if (instances.curve && (instances.curve.reading || instances.curve.writing)) instances.curve.poll();
  }

  function refreshStatus() {
    const status = api.status() || {};
    const obdState = api.obd() || {};
    const route = store.get().route;
    const obdDevices = route === 'obd' ? (api.obdDevices() || {}) : null;
    const signature = JSON.stringify({ status, obdState, obdDevices, demo: api.isDemo() });
    if (signature !== previousStatusSignature) {
      previousStatusSignature = signature;
      const patch = { status, obd: obdState, demo: api.isDemo() };
      if (obdDevices) patch.obdDevices = obdDevices;
      store.patch(patch);
    }
    const state = store.get();
    if (route === 'dashboard') ensureScreen('dashboard')?.render(state);
    if (route === 'obd') ensureScreen('obd')?.render(state);
  }

  function toolsEditing() {
    const host = byId('toolDiagnosticsWorkspace');
    return !!host && !!document.activeElement && host.contains(document.activeElement) &&
      ['INPUT', 'SELECT', 'BUTTON'].includes(document.activeElement.tagName);
  }

  function refreshContext() {
    const state = store.get();
    const route = state.route;
    const curve = route === 'curve' ? ensureScreen('curve') : null;
    const curveNeedsLearning = route === 'curve' && (curveEvidenceVisible() || curve?.needsLearning?.());
    const patch = {};
    if (route === 'learning' || curveNeedsLearning || route === 'suggestions' || route === 'tools') {
      patch.learning = api.learning() || {};
      patch.learningStatus = api.learningStatus() || {};
    }
    if (route === 'learning') {
      patch.learningDecision = api.learningDecision() || {};
      patch.learningTolerance = api.learningToleranceSettings() || {};
    }
    if (route === 'learning' || route === 'suggestions' || route === 'map' || route === 'curve') {
      patch.calibrationState = readCalibrationState();
    }
    if (route === 'obd') patch.obdDevices = api.obdDevices() || {};
    if (route === 'tools') {
      patch.sessionStatus = api.sessionStatus() || {};
      patch.sessions = api.sessions() || [];
      patch.logs = api.logs() || [];
    }
    if (Object.keys(patch).length) store.patch(patch);
    const updated = store.get();
    if (route === 'learning') ensureScreen('learning')?.render(updated);
    if (curveNeedsLearning && curve) {
      if (curveEvidenceVisible() && curve.data) curve.renderEvidence(updated);
      if (curve.needsLearning?.()) curve.renderLearning(updated);
    }
    if (route === 'obd') ensureScreen('obd')?.render(updated);
    if (route === 'suggestions') {
      utilities?.render(updated);
      renderPersistentSuggestions(updated);
    }
    if (route === 'tools' && !toolsEditing()) utilities?.render(updated);
  }

  function suggestionTargetLabel(item) {
    if (item.target === 'CURVE_K') return 'Curva K';
    const change = Array.isArray(item.mapChanges) ? item.mapChanges[0] : null;
    return change ? `Mapa K · célula ${Number(change.row) + 1}×${Number(change.column) + 1}` : 'Mapa K';
  }

  function suggestionMagnitude(item) {
    const mapChange = Array.isArray(item.mapChanges) ? item.mapChanges[0] : null;
    if (mapChange && Number.isFinite(Number(mapChange.before)) && Number(mapChange.before) !== 0) {
      const pct = (Number(mapChange.after) / Number(mapChange.before) - 1) * 100;
      return `${pct >= 0 ? '+' : ''}${pct.toFixed(1).replace('.', ',')}%`;
    }
    const curve = Array.isArray(item.curveChanges) ? item.curveChanges : [];
    if (curve.length) {
      const mean = curve.reduce((sum, change) => sum + ((Number(change.after) / Number(change.before) - 1) * 100), 0) / curve.length;
      return `${mean >= 0 ? '+' : ''}${mean.toFixed(1).replace('.', ',')}%`;
    }
    return 'observando';
  }

  function renderPersistentSuggestions(state) {
    const host = byId('suggestionList');
    const calibration = state.calibrationState || {};
    const items = Array.isArray(calibration.suggestionItems) ? calibration.suggestionItems : [];
    if (!host || !items.length) return;
    const current = items.filter(item => ['PENDING', 'OBSERVING'].includes(String(item.lifecycle || '')));
    const pendingMap = current.filter(item => item.lifecycle === 'PENDING' && item.target === 'MAP_K' && item.actionable === true);
    const pendingCurve = current.filter(item => item.lifecycle === 'PENDING' && item.target === 'CURVE_K' && item.actionable === true);
    const observing = current.filter(item => item.lifecycle === 'OBSERVING');
    const applied = items.filter(item => item.lifecycle === 'APPLIED').slice(-12).reverse();
    const validIds = new Set([...pendingMap, ...pendingCurve].map(item => item.id));
    [...selectedSuggestionIds].forEach(id => { if (!validIds.has(id)) selectedSuggestionIds.delete(id); });
    setText('suggestionCount', pendingMap.length + pendingCurve.length);

    const pendingRows = list => list.map(item => `
      <label class="suggestion-row" data-lifecycle="PENDING">
        <input type="checkbox" data-suggestion-select="${escapeHtml(item.id)}" ${selectedSuggestionIds.has(item.id) ? 'checked' : ''}>
        <span class="suggestion-row-main"><b>${escapeHtml(suggestionTargetLabel(item))}</b><span>${escapeHtml(item.rationale || 'Sugestão pronta para revisão humana.')}</span></span>
        <span class="suggestion-row-meta"><b>${escapeHtml(suggestionMagnitude(item))}</b><small>${Math.round(Number(item.confidence || 0) * 100)}% confiança</small></span>
      </label>`).join('');
    const passiveRows = list => list.map(item => `
      <div class="suggestion-row" data-lifecycle="${escapeHtml(item.lifecycle)}">
        <span></span><span class="suggestion-row-main"><b>${escapeHtml(suggestionTargetLabel(item))}</b><span>${escapeHtml(item.rationale || '')}</span></span>
        <span class="suggestion-row-meta"><b>${item.lifecycle === 'APPLIED' ? 'aplicada' : 'observando'}</b><small>${Math.round(Number(item.confidence || 0) * 100)}% confiança</small></span>
      </div>`).join('');

    host.innerHTML = `
      <div class="suggestion-queue-summary">
        <div><small>PENDENTES</small><b>${pendingMap.length + pendingCurve.length}</b></div>
        <div><small>OBSERVANDO</small><b>${observing.length}</b></div>
        <div><small>APLICADAS</small><b>${applied.length}</b></div>
      </div>
      ${pendingMap.length ? `<section class="suggestion-group" data-suggestion-group="MAP_K"><header><div><small>AJUSTE LOCAL</small><h3>Mapa K · ${pendingMap.length} prontas</h3></div><div class="suggestion-group-actions"><button type="button" class="quiet-button" data-select-ready="MAP_K">Selecionar prontas</button><button type="button" class="primary" data-review-selected="MAP_K">Revisar selecionadas</button></div></header>${pendingRows(pendingMap)}</section>` : ''}
      ${pendingCurve.length ? `<section class="suggestion-group" data-suggestion-group="CURVE_K"><header><div><small>AJUSTE GLOBAL</small><h3>Curva K · ${pendingCurve.length} pronta${pendingCurve.length === 1 ? '' : 's'}</h3></div><div class="suggestion-group-actions"><button type="button" class="quiet-button" data-select-ready="CURVE_K">Selecionar prontas</button><button type="button" class="primary" data-review-selected="CURVE_K">Revisar selecionadas</button></div></header>${pendingRows(pendingCurve)}</section>` : ''}
      ${observing.length ? `<section class="suggestion-group"><header><div><small>OBSERVANDO</small><h3>Persistem sem valor antigo aplicável</h3></div></header>${passiveRows(observing)}</section>` : ''}
      ${applied.length ? `<section class="suggestion-group"><header><div><small>HISTÓRICO</small><h3>Aplicadas após readback</h3></div></header>${passiveRows(applied)}</section>` : ''}
    `;

    host.querySelectorAll('[data-suggestion-select]').forEach(input => input.addEventListener('change', () => {
      if (input.checked) selectedSuggestionIds.add(input.dataset.suggestionSelect);
      else selectedSuggestionIds.delete(input.dataset.suggestionSelect);
    }));
    host.querySelectorAll('[data-select-ready]').forEach(button => button.addEventListener('click', () => {
      const target = button.dataset.selectReady;
      const list = target === 'MAP_K' ? pendingMap : pendingCurve;
      list.forEach(item => selectedSuggestionIds.add(item.id));
      renderPersistentSuggestions(store.get());
    }));
    host.querySelectorAll('[data-review-selected]').forEach(button => button.addEventListener('click', () => {
      const target = button.dataset.reviewSelected;
      const list = (target === 'MAP_K' ? pendingMap : pendingCurve).filter(item => selectedSuggestionIds.has(item.id));
      if (!list.length) {
        showAlert({ level: 'warning', message: 'Selecione ao menos uma sugestão pronta.' });
        return;
      }
      if (target === 'MAP_K') {
        const mapChanges = list.flatMap(item => Array.isArray(item.mapChanges) ? item.mapChanges : []);
        router.navigate('map', { origin: 'suggestions', suggestionIds: list.map(item => item.id), suggestion: { target: 'MAP_K', mapChanges } });
      } else {
        const curveChanges = list.flatMap(item => Array.isArray(item.curveChanges) ? item.curveChanges : []);
        router.navigate('curve', { origin: 'suggestions', suggestionIds: list.map(item => item.id), suggestion: { target: 'CURVE_K', curveChanges } });
      }
    }));
  }

  function activateRoute(route, context) {
    store.patch({ suggestionsOpen: route === 'suggestions', toolsOpen: route === 'tools' });
    if (route === 'dashboard') {
      previousTelemetrySignature = '';
      refreshFast();
      ensureScreen('dashboard')?.render(store.get());
    }
    if (route === 'learning') {
      previousTelemetrySignature = '';
      ensureScreen('learning');
      refreshFast();
      refreshContext();
      renderLightLiveContext(store.get(), 'learning');
    }
    if (route === 'map') {
      previousTelemetrySignature = '';
      ensureScreen('map')?.onEnter(context || store.get().routeContext);
      refreshFast();
      refreshContext();
    }
    if (route === 'curve') {
      ensureScreen('curve')?.onEnter(context || store.get().routeContext);
      refreshContext();
    }
    if (route === 'obd') {
      ensureScreen('obd');
      refreshStatus();
      refreshContext();
    }
    if (route === 'suggestions' || route === 'tools') {
      refreshContext();
      if (route === 'suggestions') renderPersistentSuggestions(store.get());
      if (route === 'tools' && !toolsEditing()) utilities?.render(store.get());
    }
  }

  router.onNavigate = (route, from, context) => activateRoute(route, context);

  const scheduler = new ui.Scheduler({
    intervalMs: 200,
    onFast: refreshFast,
    onStatus: refreshStatus,
    onContext: refreshContext,
  });

  function bindGlobalEvents() {
    routeButtons.forEach(button => button.addEventListener('click', () => router.navigate(button.dataset.route)));
    byId('alertToast')?.querySelector('button')?.addEventListener('click', () => byId('alertToast')?.classList.remove('show'));
    document.querySelector('[data-screen="curve"] .evidence-disclosure')?.addEventListener('toggle', event => {
      if (event.currentTarget.open && store.get().route === 'curve') refreshContext();
    });

    document.addEventListener('visibilitychange', () => {
      const visible = !document.hidden;
      store.patch({ visible });
      if (visible) {
        scheduler.start();
        refreshStatus();
        activateRoute(store.get().route, store.get().routeContext);
      } else {
        scheduler.stop();
      }
    });

    root.addEventListener('omegas-refresh', () => {
      refreshStatus();
      refreshContext();
      const route = store.get().route;
      if (route === 'dashboard' || route === 'learning' || route === 'map') {
        previousTelemetrySignature = '';
        refreshFast();
      }
      if (route === 'map') instances.map?.poll();
      if (route === 'curve') instances.curve?.poll();
    });
  }

  function initialize() {
    routeButtons = [...document.querySelectorAll('[data-route]')];
    screenNodes = [...document.querySelectorAll('[data-screen]')];
    bindGlobalEvents();
    const identity = api.releaseIdentity() || {};
    store.patch({ identity, demo: api.isDemo() });
    setText('buildIdentity', `${identity.engine || identity.product || 'OMEGAS'} · ${identity.versionName || identity.generation || 'V8'}`);
    store.subscribe(renderShell, true);
    refreshStatus();
    const route = router.restore();
    activateRoute(route, null);
    scheduler.start();
  }

  root.OmegasApp = { api, store, router, scheduler, screens: instances };
  initialize();
})(typeof window !== 'undefined' ? window : globalThis);
