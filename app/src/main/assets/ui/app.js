(function (root) {
  'use strict';

  const ui = root.OmegasUi || {};
  if (!ui.Store || !ui.NativeApi || !ui.Router || !ui.Scheduler) {
    console.error('[OMEGAS] Fundação da UI não carregada.');
    return;
  }

  // As folhas de estilo são estáticas no index.html (ordem fixa: o acabamento do Lote F vem por último).
  if (!document.querySelector('link[data-refine-style]')) {
    const refinementStyle = document.createElement('link');
    refinementStyle.rel = 'stylesheet';
    refinementStyle.href = 'styles-refine.css';
    refinementStyle.dataset.refineStyle = 'true';
    document.head.appendChild(refinementStyle);
  }

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
    refino: ['AJUSTE GNV', 'Ajuste GNV'],
    sessions: ['SESSÕES', 'Sessões'],
    diagnostico: ['DIAGNÓSTICO', 'Diagnóstico'],
    tools: ['SISTEMA', 'Ferramentas'],
  };

  // Rotas dentro de "Avançado" (continuam todas acessíveis).
  const ADVANCED_ROUTES = ['map', 'curve', 'autocal', 'tools'];
  let renderedRoute = null;
  let previousGlobalSignature = '';
  let previousTelemetrySignature = '';
  // Sequência do último quadro pintado: a ponte responde {changed:false} sem serializar se nada mudou.
  let lastPresentSequence = -1;
  let telemetryPatchedAt = 0;
  let previousStatusSignature = '';
  let previousAlert = null;
  // Sem timer de UI: o aviso some quando o relógio do scheduler (refreshStatus) vê o prazo vencido.
  const TOAST_MS = 3600;
  const OVERLAY_PROMPT_DELAY_MS = 5000;
  // Na aba AutoCal o poll é de 50 ms: o caminho "nada mudou" de getPresentSnapshotIfChanged é quase de graça
  // (só devolve a idade) e o cursor suave precisa do quadro novo assim que ele chega. Nas outras abas, 200 ms.
  const AUTOCAL_CADENCE_MS = 50;
  const startedAt = Date.now();
  let overlayPromptPending = true;
  let toastUntil = 0;
  let routeButtons = [];
  let screenNodes = [];

  function byId(id) { return document.getElementById(id); }
  function setText(id, value) {
    const node = byId(id);
    if (!node) return;
    const next = value == null ? '—' : String(value);
    if (node.textContent !== next) node.textContent = next;
  }
  const { finite, escapeHtml } = ui.DisplayRules;
  function rounded(value, digits) {
    const number = finite(value);
    if (number === null) return '—';
    const factor = 10 ** digits;
    return String(Math.round(number * factor) / factor);
  }
  function fuelLabel(raw) {
    // Combustível desconhecido ("--" do Kotlin, vazio) mostra "—"; regra única em core/display-rules.js.
    const rules = (root.OmegasUi || ui).DisplayRules;
    return rules ? rules.fuelLabel(raw) : String(raw || '—').toUpperCase();
  }
  /** Esquece o último quadro: o próximo tick pede e pinta o quadro inteiro. */
  function resetPresentCursor() {
    previousTelemetrySignature = '';
    lastPresentSequence = -1;
  }
  function isLiveRoute(route) {
    return ((root.OmegasUi || ui).LIVE_ROUTES || ['dashboard', 'map', 'autocal', 'refino']).includes(route);
  }
  function liveFrom(state) {
    const telemetry = state.telemetry || {};
    return telemetry.live || telemetry.data || telemetry;
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
    if (route === 'diagnostico' && ui.DiagnosticoScreen) instances.diagnostico = new ui.DiagnosticoScreen({ store, api, router });
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
      // Rota escondida em "Avançado" acende o botão Avançado (o dono sabe onde está).
      byId('app')?.querySelector?.('[data-nav-advanced]')?.classList.toggle('active', ADVANCED_ROUTES.includes(state.route));
      setAdvancedOpen(false);
      screenNodes.forEach(screen => {
        const active = screen.dataset.screen === state.route;
        screen.classList.toggle('active', active);
        screen.setAttribute('aria-hidden', active ? 'false' : 'true');
      });
    }

    const status = state.status || {};
    // Uma regra de frescor para todo número ao vivo (core/live-store.js). Em rota sem bombeador (Curva K, Sessões,
    // Ferramentas) vale o status de 1 Hz: o trilho e a faixa nunca ficam "—" com a ECU enviando dados.
    const reading = ui.LiveStore.read(state, { fallback: true });
    const fuel = reading.level === 'fresh' || reading.level === 'late' ? fuelLabel(reading.fuel || status.fuelState) : '—';
    const link = (root.OmegasUi || ui).DisplayRules.connectionState(status, reading);
    const globalSignature = `${link.key}:${fuel}:${reading.level}`;
    if (globalSignature !== previousGlobalSignature) {
      previousGlobalSignature = globalSignature;
      const ecu = byId('globalEcu');
      if (ecu) {
        ecu.dataset.online = link.online ? 'true' : 'false';
        ecu.dataset.link = link.key;
        ecu.hidden = link.key === 'denied';
        setText('globalEcu', link.label);
        ecu.title = link.hint;
      }
      const allow = byId('globalUsbAllow');
      if (allow) allow.hidden = link.key !== 'denied';
      const fuelNode = byId('globalFuel');
      if (fuelNode) {
        fuelNode.dataset.fuel = fuel;
        fuelNode.hidden = link.key === 'denied';
        fuelNode.dataset.late = reading.level === 'late' ? 'true' : 'false';
        setText('globalFuel', fuel);
      }
    }
    noteLinkTransition(link, reading);

    if (state.alert && state.alert !== previousAlert) {
      previousAlert = state.alert;
      showAlert(state.alert);
    }
  }

  // Resgate pós-reconexão: quando os dados voltam (cabo/ECU), Mapa K e Curva K releem sozinhos.
  // Ler é automático (regra 1); nada grava. Só a transição "sem dados → com dados" dispara, uma vez.
  let linkWasOnline = null;
  function noteLinkTransition(link, reading) {
    const online = link.online === true && (reading.level === 'fresh' || reading.level === 'late');
    const before = linkWasOnline;
    linkWasOnline = online;
    if (before === false && online) {
      instances.map?.onReconnect?.();
      instances.curve?.onReconnect?.();
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
    // Meio segundo até 10 s; depois de 1 em 1 s (o "Sem dados há N s" continua andando em vez de congelar em 10 s).
    const freshnessBucket = freshnessAge === null || freshnessAge < 0 ? -1 : freshnessAge < 10000 ? Math.floor(freshnessAge / 500) : 20 + Math.min(3600, Math.floor(freshnessAge / 1000));
    // A sequência do quadro NÃO entra: ela muda a cada quadro e forçava redesenho mesmo com os números iguais.
    // Quadro novo já é rastreado por lastPresentSequence; aqui só o que o motorista vê (valores + frescor).
    if (route === 'dashboard') {
      return [
        source.valid === false ? 0 : 1,
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
      freshnessBucket,
      Math.round((finite(interpolation.rpm ?? live.rpm) || 0) / 25) * 25,
      Math.round((finite(interpolation.petrolMs ?? live.petrol_ms ?? live.petrolMs) || 0) * 20) / 20,
      Number.isFinite(Number(cell.row)) ? Number(cell.row) : '-',
      Number.isFinite(Number(cell.column)) ? Number(cell.column) : '-',
    ].join('|');
  }

  function renderLightLiveContext(state, route) {
    // Mesma regra de frescor do Agora: leitura velha (> 3 s) não aponta célula nenhuma ("—").
    const reading = ui.LiveStore.read(state);
    const shown = reading.level === 'fresh' || reading.level === 'late';
    const interpolation = state.telemetry?.interpolation || {};
    const cell = interpolation.cell || {};
    const interpolationValid = shown && interpolation.valid === true;
    const row = interpolationValid && Number.isFinite(Number(cell.row)) && Number(cell.row) >= 0 ? Number(cell.row) : null;
    const column = interpolationValid && Number.isFinite(Number(cell.column)) && Number(cell.column) >= 0 ? Number(cell.column) : null;
    if (route === 'map') ensureScreen('map')?.renderLiveContext?.({ row, column, level: reading.level });
  }

  /** Único pump de PresentSnapshot. Nenhum screen abre polling nativo próprio. */
  function refreshFast() {
    const route = store.get().route;
    if (isLiveRoute(route)) {
      const envelope = api.presentSnapshot(lastPresentSequence) || {};
      // Revisões por tipo vêm em todo quadro (e por empurrão): evidência/tabelas/sessão só são relidas quando andam.
      ui.Revisions?.noteAll(envelope.revisions || envelope.data?.revisions);
      let telemetry = envelope.data || {};
      if (envelope.changed === false && envelope.ok !== false) {
        // Nada novo no Kotlin: reaproveita o último quadro e atualiza só a idade.
        const age = Number(envelope.telemetryAgeMs);
        telemetry = Object.assign({}, store.get().telemetry || {}, Number.isFinite(age) ? { telemetryAgeMs: age, ageMs: age } : {});
      }
      const signature = `${route}:${telemetryVisualSignature(telemetry, route)}`;
      if (envelope.ok === false && store.get().telemetry?.valid !== false) {
        // A ponte falhou: o último valor não pode continuar com cara de ao vivo.
        resetPresentCursor();
        store.patch({ telemetry: { valid: false, ageMs: -1, telemetryAgeMs: -1 } });
      }
      if (envelope.ok !== false && envelope.changed !== false) {
        lastPresentSequence = Number.isFinite(Number(telemetry.sequence)) ? Number(telemetry.sequence) : -1;
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
      resetPresentCursor();
      store.patch({ telemetry: { valid: false, ageMs: -1, telemetryAgeMs: -1 } });
    }

    const state = store.get();
    if (route === 'map' && instances.map && (state.map?.state === 'writing' || state.map?.state === 'reading' || instances.map.releasing === true)) instances.map.poll();
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
    const curveNeedsOverview = route === 'curve' && curve?.needsOverview?.();
    const patch = {};

    if (route === 'tools') {
      patch.sessionStatus = api.sessionStatus() || {};
      patch.logs = api.logs() || [];
    }
    if (route === 'sessions') {
      patch.sessionStatus = api.sessionStatus() || {};
      // null = a lista ainda está sendo lida (a tela diz isso; não afirma "nenhuma sessão").
      const listed = api.sessions();
      patch.sessions = Array.isArray(listed) ? listed : null;
      // Falha de leitura ≠ "ainda lendo": a tela diz o que houve e o que fazer.
      patch.sessionsError = !Array.isArray(listed) && listed && listed.ok === false ? String(listed.error || 'sem detalhe') : '';
    }
    if (Object.keys(patch).length) store.patch(patch);
    const updated = store.get();
    if (curveNeedsOverview && curve) {
      if (curve.needsOverview?.()) curve.renderOverview(updated);
    }
    if (route === 'sessions') ensureScreen('sessions')?.render(updated);
    if (route === 'diagnostico') ensureScreen('diagnostico')?.refresh(false);
    if (route === 'tools' && !toolsEditing()) utilities?.render(updated);
  }

  /** Pinta cache primeiro; bridge/ciência só são consultadas depois de um paint. */
  function activateRoute(route, context) {
    scheduler.setCadenceMs(route === 'autocal' ? AUTOCAL_CADENCE_MS : 200);
    if (route === 'dashboard') {
      resetPresentCursor();
      ensureScreen('dashboard')?.render(store.get());
      afterPaint(refreshFast);
      return;
    }
    if (route === 'map') {
      resetPresentCursor();
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
      resetPresentCursor();
      // Um refresh só ao entrar: enter() não toca a ponte; a leitura vem depois do primeiro quadro pintado.
      root.OmegasApp?.autoCalCockpit?.enter?.();
      afterPaint(() => {
        refreshFast();
        root.OmegasApp?.autoCalCockpit?.refreshNow?.();
      });
      return;
    }
    if (route === 'refino') {
      afterPaint(() => root.OmegasApp?.refino?.refreshNow?.());
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
      return;
    }
    if (route === 'diagnostico') {
      ensureScreen('diagnostico')?.render();
      afterPaint(() => ensureScreen('diagnostico')?.refresh(true));
    }
  }

  router.onNavigate = (route, from, context) => activateRoute(route, context);

  const scheduler = new ui.Scheduler({
    intervalMs: 200,
    onFast: refreshFast,
    onStatus: refreshStatus,
    onContext: refreshContext,
  });

  /** Abre/fecha a lista "Avançado" da barra de navegação. */
  function setAdvancedOpen(open) {
    const nav = document.querySelector('.side-nav');
    const toggle = nav?.querySelector('[data-nav-advanced]');
    if (!nav || !toggle) return;
    const next = open ? 'true' : 'false';
    if (nav.dataset.advancedOpen !== next) nav.dataset.advancedOpen = next;
    if (toggle.getAttribute('aria-expanded') !== next) toggle.setAttribute('aria-expanded', next);
  }

  function bindGlobalEvents() {
    routeButtons.forEach(button => button.addEventListener('click', () => { setAdvancedOpen(false); router.navigate(button.dataset.route); }));
    document.querySelector('[data-nav-advanced]')?.addEventListener('click', event => {
      event.stopPropagation?.();
      setAdvancedOpen(document.querySelector('.side-nav')?.dataset.advancedOpen !== 'true');
    });
    // Tocar fora da lista fecha.
    document.addEventListener('click', event => {
      if (!event.target.closest || !event.target.closest('.side-nav')) setAdvancedOpen(false);
    });
    byId('alertToast')?.querySelector('button')?.addEventListener('click', () => byId('alertToast')?.classList.remove('show'));
    // "Permitir USB": o dono negou a permissão do Android; um toque pede de novo (ação humana explícita).
    document.addEventListener('click', event => {
      if (event.target.closest && event.target.closest('[data-usb-allow]')) api.connectUsb();
    });

    document.addEventListener('visibilitychange', () => {
      const visible = !document.hidden;
      store.patch({ visible });
      if (visible) {
        // Voltar do segundo plano não é "entrar na aba": a Curva K só retoma (mantém foto escolhida, prévia e Desfazer).
        if (store.get().route === 'curve') { instances.curve?.onResume?.(); afterPaint(refreshContext); }
        else activateRoute(store.get().route, store.get().routeContext);
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
          resetPresentCursor();
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
        <p>Um balão com combustível, RPM, Injeção, MAP e gás aparece quando você usa o mapa ou a música, e nunca cobre o OMEGAS. Só mostra números: não mexe na ECU.</p>
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