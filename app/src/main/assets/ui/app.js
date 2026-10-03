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

  const routeMeta = {
    dashboard: ['AGORA', 'Agora'],
    map: ['MAPA K', 'Mapa K'],
    curve: ['CURVA K', 'Curva K'],
    autocal: ['AUTO-CAL', 'AutoCal'],
    refino: ['REFINO', 'Refino'],
    sessions: ['SESSÕES', 'Sessões'],
    tools: ['SISTEMA', 'Ferramentas'],
  };

  let renderedRoute = null;
  let previousGlobalSignature = '';
  let previousTelemetrySignature = '';
  let telemetryPatchedAt = 0;
  let previousStatusSignature = '';
  let previousAlert = null;
  // Sem timer de UI: o aviso some quando o relógio do scheduler (refreshStatus) vê o prazo vencido.
  const TOAST_MS = 3600;
  const OVERLAY_PROMPT_DELAY_MS = 5000;
  const startedAt = Date.now();
  let overlayPromptPending = true;
  let toastUntil = 0;
  let previousEquivalenceSignature = '';
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
    // Combustível desconhecido ("--" do Kotlin, vazio) mostra "—"; regra única em core/display-rules.js.
    const rules = (root.OmegasUi || ui).DisplayRules;
    return rules ? rules.fuelLabel(raw) : String(raw || '—').toUpperCase();
  }
  function isLiveRoute(route) {
    return ((root.OmegasUi || ui).LIVE_ROUTES || ['dashboard', 'map', 'autocal', 'refino']).includes(route);
  }
  function liveFrom(state) {
    const telemetry = state.telemetry || {};
    return telemetry.live || telemetry.data || telemetry;
  }
  function curveEvidenceVisible() {
    return document.querySelector('[data-screen="curve"] .evidence-disclosure')?.open === true;
  }
  /** Depois de um quadro pintado, sem timer: dois requestAnimationFrame seguidos. */
  function afterPaint(task) {
    if (typeof root.requestAnimationFrame === 'function') {
      root.requestAnimationFrame(() => root.requestAnimationFrame(task));
    } else {
      task();
    }
  }

  function ensureScreen(route) {
    if (instances[route]) return instances[route];
    if (route === 'dashboard' && ui.DashboardScreen) instances.dashboard = new ui.DashboardScreen(store, api);
    if (route === 'map' && ui.MapScreen) instances.map = new ui.MapScreen(store, api, router);
    if (route === 'curve' && ui.CurveScreen) instances.curve = new ui.CurveScreen(store, api);
    if (route === 'sessions' && ui.SessionsScreen) instances.sessions = new ui.SessionsScreen(store, api);
    return instances[route] || null;
  }

  function renderShell(state) {
    if (state.route !== renderedRoute) {
      renderedRoute = state.route;
      const meta = routeMeta[state.route] || routeMeta.dashboard;
      document.getElementById('app')?.classList.toggle('autocal-focus', state.route === 'autocal' || state.route === 'refino');
      document.body.dataset.omegasRoute = state.route;
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
    const fuel = fuelLabel(liveFrom(state).fuel || liveFrom(state).state || status.fuelState);
    const globalSignature = `${status.usbConnected === true ? 1 : 0}:${fuel}`;
    if (globalSignature !== previousGlobalSignature) {
      previousGlobalSignature = globalSignature;
      const ecu = byId('globalEcu');
      if (ecu) {
        const online = status.usbConnected === true;
        ecu.dataset.online = online ? 'true' : 'false';
        setText('globalEcu', online ? 'ECU online' : 'ECU offline');
      }
      const fuelNode = byId('globalFuel');
      if (fuelNode) {
        fuelNode.dataset.fuel = fuel;
        setText('globalFuel', fuel);
      }
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
    toastUntil = Date.now() + TOAST_MS;
  }

  function telemetryVisualSignature(telemetry, route) {
    const source = telemetry || {};
    const live = source.live || source.data || source;
    const freshnessAge = finite(source.telemetryAgeMs ?? source.ageMs);
    const freshnessBucket = freshnessAge === null || freshnessAge < 0 ? -1 : Math.min(20, Math.floor(freshnessAge / 500));
    const sourceSequence = Number.isFinite(Number(source.sequence)) ? Number(source.sequence) : -1;
    if (route === 'dashboard') {
      return [
        source.valid === false ? 0 : 1,
        sourceSequence,
        freshnessBucket,
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
      sourceSequence,
      freshnessBucket,
      Math.round((finite(interpolation.rpm ?? live.rpm) || 0) / 25) * 25,
      Math.round((finite(interpolation.petrolMs ?? live.petrol_ms ?? live.petrolMs) || 0) * 20) / 20,
      Number.isFinite(Number(cell.row)) ? Number(cell.row) : '-',
      Number.isFinite(Number(cell.column)) ? Number(cell.column) : '-',
    ].join('|');
  }

  function renderLightLiveContext(state, route) {
    const interpolation = state.telemetry?.interpolation || {};
    const interpolationValid = interpolation.valid === true;
    const cell = interpolation.cell || {};
    const rpm = finite(interpolation.rpm ?? liveFrom(state).rpm);
    const petrolMs = finite(interpolation.petrolMs ?? liveFrom(state).petrol_ms ?? liveFrom(state).petrolMs);
    const row = interpolationValid && Number.isFinite(Number(cell.row)) && Number(cell.row) >= 0 ? Number(cell.row) : null;
    const column = interpolationValid && Number.isFinite(Number(cell.column)) && Number(cell.column) >= 0 ? Number(cell.column) : null;
    const position = row !== null && column !== null ? ` · célula ${row + 1}×${column + 1}` : '';
    const label = interpolationValid && rpm !== null && petrolMs !== null
      ? `${Math.round(rpm).toLocaleString('pt-BR')} RPM · ${petrolMs.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ms${position}`
      : 'Aguardando condição válida';
    if (route === 'map') ensureScreen('map')?.renderLiveContext?.({ rpm, petrolMs, row, column, label });
  }

  /** Único pump de PresentSnapshot. Nenhum screen abre polling nativo próprio. */
  function refreshFast() {
    const route = store.get().route;
    if (isLiveRoute(route)) {
      const envelope = api.presentSnapshot() || {};
      const telemetry = envelope.data || {};
      const signature = `${route}:${telemetryVisualSignature(telemetry, route)}`;
      if (envelope.ok === false && store.get().telemetry?.valid !== false) {
        // A ponte falhou: o último valor não pode continuar com cara de ao vivo.
        previousTelemetrySignature = '';
        store.patch({ telemetry: { valid: false, ageMs: -1, telemetryAgeMs: -1 } });
      }
      if (envelope.ok !== false && signature !== previousTelemetrySignature) {
        previousTelemetrySignature = signature;
        telemetryPatchedAt = Date.now();
        store.patch({ telemetry, presentRevision: Number(envelope.revision || 0) });
        const state = store.get();
        if (route === 'dashboard') ensureScreen('dashboard')?.render(state);
        if (route === 'map') renderLightLiveContext(state, route);
      }
    }

    // Rota sem pump (Curva K, Sessões, Ferramentas): o último valor não pode ficar na barra
    // de status e no painel flutuante como se fosse de agora. Vencido, vira desconhecido (—).
    const rules = (root.OmegasUi || ui).DisplayRules;
    if (rules?.offRouteTelemetryExpired(isLiveRoute(route), store.get().telemetry?.valid, telemetryPatchedAt, Date.now())) {
      previousTelemetrySignature = '';
      store.patch({ telemetry: { valid: false, ageMs: -1, telemetryAgeMs: -1 } });
    }

    const state = store.get();
    if (route === 'map' && instances.map && (state.map?.state === 'writing' || state.map?.state === 'reading')) instances.map.poll();
    if (route === 'curve' && instances.curve && (instances.curve.reading || instances.curve.writing || instances.curve.backupTask)) instances.curve.poll();
  }

  function refreshStatus() {
    const status = api.status() || {};
    const route = store.get().route;
    const signature = JSON.stringify({ status, demo: api.isDemo() });
    if (signature !== previousStatusSignature) {
      previousStatusSignature = signature;
      store.patch({ status, demo: api.isDemo() });
    }
    if (toastUntil && Date.now() >= toastUntil) {
      toastUntil = 0;
      byId('alertToast')?.classList.remove('show');
    }
    if (overlayPromptPending && Date.now() - startedAt >= OVERLAY_PROMPT_DELAY_MS) {
      overlayPromptPending = false;
      maybePromptOverlay(false);
    }
    const state = store.get();
    if (route === 'dashboard') ensureScreen('dashboard')?.render(state);
  }

  /** Cérebro de equivalência: null até o Kotlin expor `getEquivalence`; o Agora só mostra, nunca executa. */
  function refreshEquivalence() {
    const eq = api.equivalence ? api.equivalence() : null;
    const signature = JSON.stringify(eq);
    if (signature === previousEquivalenceSignature) return;
    previousEquivalenceSignature = signature;
    store.patch({ equivalence: eq });
  }

  function toolsEditing() {
    const host = byId('toolDiagnosticsWorkspace');
    return !!host && !!document.activeElement && host.contains(document.activeElement) &&
      ['INPUT', 'SELECT', 'BUTTON'].includes(document.activeElement.tagName);
  }

  /** Contexto lento da rota (sessões, logs, Sugestões); a telemetria viva vem do presentSnapshot. */
  function refreshContext() {
    const state = store.get();
    const route = state.route;
    const curve = route === 'curve' ? ensureScreen('curve') : null;
    const curveNeedsLearning = route === 'curve' && (curveEvidenceVisible() || curve?.needsLearning?.());
    const patch = {};

    if (route === 'dashboard') refreshEquivalence();
    if (route === 'tools') {
      patch.sessionStatus = api.sessionStatus() || {};
      patch.logs = api.logs() || [];
    }
    if (route === 'sessions') {
      patch.sessionStatus = api.sessionStatus() || {};
      // null = a lista ainda está sendo lida (a tela diz isso; não afirma "nenhuma sessão").
      const listed = api.sessions();
      patch.sessions = Array.isArray(listed) ? listed : null;
    }
    if (Object.keys(patch).length) store.patch(patch);
    const updated = store.get();
    if (curveNeedsLearning && curve) {
      if (curveEvidenceVisible() && curve.data) curve.renderEvidence(updated);
      if (curve.needsLearning?.()) curve.renderLearning(updated);
    }
    if (route === 'sessions') ensureScreen('sessions')?.render(updated);
    if (route === 'tools' && !toolsEditing()) utilities?.render(updated);
  }

  /** Pinta cache primeiro; bridge/ciência só são consultadas depois de um paint. */
  function activateRoute(route, context) {
    scheduler.setCadenceMs(route === 'autocal' ? 50 : 200);
    if (route === 'dashboard') {
      previousTelemetrySignature = '';
      refreshEquivalence();
      ensureScreen('dashboard')?.render(store.get());
      afterPaint(refreshFast);
      return;
    }
    if (route === 'map') {
      previousTelemetrySignature = '';
      ensureScreen('map')?.onEnter(context || store.get().routeContext);
      renderLightLiveContext(store.get(), 'map');
      afterPaint(() => { refreshFast(); refreshContext(); });
      return;
    }
    if (route === 'curve') {
      ensureScreen('curve')?.onEnter(context || store.get().routeContext);
      afterPaint(refreshContext);
      return;
    }
    if (route === 'autocal') {
      previousTelemetrySignature = '';
      root.OmegasApp?.autoCalCockpit?.enter?.();
      afterPaint(() => {
        refreshFast();
        root.OmegasApp?.autoCalCockpit?.refresh?.();
      });
      return;
    }
    if (route === 'refino') {
      afterPaint(() => root.OmegasApp?.refino?.refresh?.(true));
      return;
    }
    if (route === 'sessions') {
      ensureScreen('sessions')?.render(store.get());
      afterPaint(refreshContext);
      return;
    }
    if (route === 'tools') {
      if (!toolsEditing()) utilities?.render(store.get());
      afterPaint(refreshContext);
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
      if (event.currentTarget.open && store.get().route === 'curve') afterPaint(refreshContext);
    });

    document.addEventListener('visibilitychange', () => {
      const visible = !document.hidden;
      store.patch({ visible });
      if (visible) {
        activateRoute(store.get().route, store.get().routeContext);
        afterPaint(() => {
          refreshStatus();
          scheduler.start();
        });
      } else {
        scheduler.stop();
      }
    });

    root.addEventListener('omegas-refresh', () => {
      afterPaint(() => {
        refreshStatus();
        refreshContext();
        const route = store.get().route;
        if (isLiveRoute(route)) {
          previousTelemetrySignature = '';
          refreshFast();
        }
        if (route === 'map') instances.map?.poll();
        if (route === 'curve') instances.curve?.poll();
        if (route === 'autocal') root.OmegasApp?.autoCalCockpit?.refresh?.();
      });
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
    const route = router.restore();
    activateRoute(route, null);
    afterPaint(() => {
      refreshStatus();
      scheduler.start();
    });
  }

  /**
   * Primeiro uso: oferece ligar a telemetria flutuante e, se aceitar, abre direto a tela do Android onde
   * se autoriza (a pergunta aparece uma única vez; depois fica em Ferramentas).
   */
  const OVERLAY_PROMPT_KEY = 'omegas-overlay-prompt-v1';
  function maybePromptOverlay(force) {
    try {
      if (api.isDemo() && force !== true) return;
      const rules = (root.OmegasUi || ui).DisplayRules;
      let prompted = false;
      try { prompted = root.localStorage.getItem(OVERLAY_PROMPT_KEY) === '1'; } catch (_) {}
      if (force !== true && !rules?.shouldPromptOverlay(api.overlayStatus?.() || {}, prompted)) return;
      if (document.getElementById('overlayPrompt')) return;
      try { root.localStorage.setItem(OVERLAY_PROMPT_KEY, '1'); } catch (_) {}
      const box = document.createElement('div');
      box.id = 'overlayPrompt';
      box.className = 'overlay-prompt';
      box.setAttribute('role', 'dialog');
      box.setAttribute('aria-modal', 'true');
      box.innerHTML = `<div class="overlay-prompt-card">
        <small>TELEMETRIA FLUTUANTE</small>
        <h3>Ver a telemetria por cima de outros apps?</h3>
        <p>Um balão com combustível, RPM, Petrol Inj., MAP e gás aparece quando você usa o mapa ou a música, e nunca cobre o OMEGAS. Só mostra números: não mexe na ECU.</p>
        <p>Ao tocar em <b>Autorizar agora</b>, o Android abre a tela certa: marque o OMEGAS e volte.</p>
        <div class="overlay-prompt-actions">
          <button type="button" class="primary" data-overlay-prompt="yes">Autorizar agora</button>
          <button type="button" class="quiet-button" data-overlay-prompt="no">Agora não</button>
        </div>
      </div>`;
      box.addEventListener('click', event => {
        const choice = event.target.closest('[data-overlay-prompt]')?.dataset.overlayPrompt;
        if (!choice) return;
        box.remove();
        if (choice === 'yes') api.requestOverlayPermissionAndEnable?.();
      });
      document.body.appendChild(box);
    } catch (error) {
      console.error('[OMEGAS overlay prompt]', error);
    }
  }

  root.OmegasApp = { api, store, router, scheduler, screens: instances, promptOverlay: maybePromptOverlay };
  initialize();
  // Extensões carregadas depois (AutoCal, Refino, faixa de status) assinam este evento em vez de sondar com timer.
  try { root.dispatchEvent(new root.Event('omegas-app-ready')); } catch (_) {}
})(typeof window !== 'undefined' ? window : globalThis);