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
  // Ferramentas (cartões e Avançado): todos os scripts já vieram do index.html; os objetos são criados na primeira visita da aba.
  let utilities = null;
  let advanced = null;
  function ensureTools() {
    // Cada parte é independente: uma falha nas ferramentas auxiliares não pode esconder o anti-apagão.
    if (!advanced && ui.AdvancedPanel) {
      try {
        const panel = new ui.AdvancedPanel(store, api);
        if (panel.flag) advanced = panel;
        else toolsUnavailable(new Error('Parte do painel não carregou'));
      } catch (error) { toolsUnavailable(error); }
    }
    if (!utilities && ui.Drawers) {
      try { utilities = new ui.Drawers(store, router, api); }
      catch (error) { console.error('[OMEGAS] Falha ao abrir App e arquivos', error); }
    }
  }

  const failedRoutes = new Set();
  function toolsUnavailable(error) {
    if (error) console.error('[OMEGAS] Ferramentas não abriram', error);
    const host = byId('toolAdvanced');
    if (host && !host.querySelector('[data-card="anti"]')) {
      // Estado claro, sem botão: o app tenta de novo sozinho no próximo dado válido (retryFailedRoutes).
      host.innerHTML = '<article class="tool-tile" data-card="unavailable" data-route-failed="tools" role="status"><h3>Ferramentas indisponíveis</h3><p class="tile-line reason">O aplicativo não conseguiu abrir este painel. Reconectando…</p></article>';
      failedRoutes.add('tools');
    }
  }

  const routeMeta = {
    map: ['AJUSTE LOCAL', 'Ajuste local'],
    curve: ['AJUSTE GLOBAL', 'Ajuste global'],
    autocal: ['AUTO-CAL', 'AutoCal'],
    refino: ['REFINO', 'Refino'],
    sessions: ['SESSÕES', 'Sessões'],
    tools: ['SISTEMA', 'Ferramentas'],
  };

  let renderedRoute = null;
  let previousGlobalSignature = '';
  let previousTelemetrySignature = '';
  let previousStatusSignature = '';
  let previousAlert = null;
  let toastTimer = null;
  let routeButtons = [];
  let screenNodes = [];
  let scienceRevision = 0;

  function byId(id) { return document.getElementById(id); }
  function setText(id, value) {
    const node = byId(id);
    if (!node) return;
    const next = value == null ? '—' : String(value);
    if (node.textContent !== next) node.textContent = next;
  }
  // Desconhecido (null/vazio) fica desconhecido: Number(null) seria 0 e viraria "0,0% · equivalente".
  function finite(value) { return value === null || value === undefined || value === '' || !Number.isFinite(Number(value)) ? null : Number(value); }
  // Formatadores criados uma vez (mesmo texto de toLocaleString, sem recriar a cada quadro).
  const RPM_FORMAT = new Intl.NumberFormat('pt-BR');
  const MS_FORMAT = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
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
    if (value.includes('CUTOFF')) return 'CORTE';
    if (value.includes('TRANSIC')) return 'TRANSIÇÃO';
    if (value.includes('DESLIG')) return 'DESLIGADO';
    if (value.includes('DESCONHEC') || value === '--') return '—';
    return value || '—';
  }
  /**
   * A fila V7 (sugestões de Mapa K/Curva K e a "estabilidade" por região) não tem alimentador desde a base: o que vem é
   * um arquivo antigo do aparelho, às vezes com o sinal oposto ao que o editor gravaria. Não mostrar dado congelado como
   * vivo; quem propõe a curva é o Refino (dono, 2026-10-11: "se o refino bastar, use só o refino").
   */
  function withoutFrozenQueue(calibration) {
    const { suggestionItems, learningStability, suggestionPending, ...rest } = calibration || {};
    return { ...rest, suggestionItems: [], learningStability: { map: [] }, suggestionPending: 0 };
  }

  /** A barra do topo é persistente (dono, 2026-10-13): o quadro rápido (PresentSnapshot) chega em todas as abas. */
  function isTelemetryRoute() {
    return true;
  }
  function liveFrom(state) {
    const telemetry = state.telemetry || {};
    return telemetry.live || telemetry.data || telemetry;
  }
  function afterPaint(task) {
    if (typeof root.requestAnimationFrame === 'function') {
      root.requestAnimationFrame(() => root.setTimeout(task, 0));
    } else {
      root.setTimeout(task, 0);
    }
  }

  function ensureScreen(route) {
    if (instances[route]) return instances[route];
    if (route === 'map' && ui.MapScreen) instances.map = new ui.MapScreen(store, api, router);
    if (route === 'curve' && ui.CurveScreen) instances.curve = new ui.CurveScreen(store, api);
    return instances[route] || null;
  }

  function renderShell(state) {
    if (state.route !== renderedRoute) {
      renderedRoute = state.route;
      const meta = routeMeta[state.route] || routeMeta.autocal;
      document.getElementById('app')?.classList.toggle('autocal-focus', state.route === 'autocal' || state.route === 'refino');
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
    // Combustível só com leitura recente da ECU (o status chega a cada 1 s em todas as abas): sem cabo ou sem quadro novo
    // há mais de 2,5 s mostra "—", nunca o último. O quadro rápido só vale nas abas que o recebem.
    const telemetryAge = Number(status.directTelemetryAgeMs);
    const fuelFresh = status.usbConnected === true && Number.isFinite(telemetryAge) && telemetryAge >= 0 && telemetryAge <= 2500;
    const pumped = isTelemetryRoute(state.route);
    const fuel = fuelLabel(fuelFresh ? (pumped ? liveFrom(state).fuel || liveFrom(state).state : null) || status.fuelState : null);
    const globalSignature = `${status.usbConnected === true ? 1 : 0}:${fuel}`;
    if (globalSignature !== previousGlobalSignature) {
      previousGlobalSignature = globalSignature;
      const ecu = byId('globalEcu');
      if (ecu) {
        const online = status.usbConnected === true;
        ecu.dataset.online = online ? 'true' : 'false';
        setText('globalEcu', online ? 'Central conectada' : 'Sem central');
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
    if (toastTimer) root.clearTimeout(toastTimer);
    toastTimer = root.setTimeout(() => toast.classList.remove('show'), 3600);
  }

  function telemetryVisualSignature(telemetry, route) {
    const source = telemetry || {};
    const live = source.live || source.data || source;
    const freshnessAge = finite(source.telemetryAgeMs ?? source.ageMs);
    // Meio segundo até 10 s; depois de 1 em 1 s: sem quadro novo, a idade continua andando em vez de congelar em 10 s.
    const freshnessBucket = freshnessAge === null || freshnessAge < 0 ? -1
      : freshnessAge < 10000 ? Math.floor(freshnessAge / 500) : 20 + Math.min(3600, Math.floor(freshnessAge / 1000));
    const sourceSequence = Number.isFinite(Number(source.sequence)) ? Number(source.sequence) : -1;
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
      // O que a barra do topo mostra (RPM de 10 em 10, Petrol Inj. e MAP com 2 casas, tensão com 1) em todas as abas.
      rounded(Math.round((finite(live.rpm) ?? NaN) / 10) * 10, 0),
      rounded(live.petrol_ms ?? live.petrolMs, 2),
      rounded(live.load_bar ?? live.map_bar ?? live.mapBar, 2),
      rounded(live.unknown_raw_19 ?? live.unknownRaw19, 0),
      rounded(live.level_raw ?? live.levelRaw, 0),
      String(live.fuel || live.state || ''),
    ].join('|');
  }

  function renderLightLiveContext(state, route) {
    const interpolation = state.telemetry?.interpolation || {};
    // Mesma regra do Agora: sem quadro novo há mais de 2,5 s, o AGORA não mostra o último RPM como se fosse vivo.
    const age = finite(state.telemetry?.telemetryAgeMs ?? state.telemetry?.ageMs);
    const stale = age !== null && age > 2500;
    const interpolationValid = interpolation.valid === true && !stale;
    const cell = interpolation.cell || {};
    const rpm = stale ? null : finite(interpolation.rpm ?? liveFrom(state).rpm);
    const petrolMs = stale ? null : finite(interpolation.petrolMs ?? liveFrom(state).petrol_ms ?? liveFrom(state).petrolMs);
    const row = interpolationValid && Number.isFinite(Number(cell.row)) && Number(cell.row) >= 0 ? Number(cell.row) : null;
    const column = interpolationValid && Number.isFinite(Number(cell.column)) && Number(cell.column) >= 0 ? Number(cell.column) : null;
    const position = row !== null && column !== null ? ` · célula ${row + 1}×${column + 1}` : '';
    const label = interpolationValid && rpm !== null && petrolMs !== null
      ? `${RPM_FORMAT.format(Math.round(rpm))} RPM · ${MS_FORMAT.format(petrolMs)} ms${position}`
      : stale ? `Sem dados há ${Math.round(age / 1000)} s` : 'Aguardando condição válida';
    if (route === 'map') ensureScreen('map')?.renderLiveContext?.({ rpm, petrolMs, row, column, label });
  }

  /** Único pump de PresentSnapshot. Nenhum screen abre polling nativo próprio. */
  function refreshFast() {
    retryFailedRoutes();
    const route = store.get().route;
    if (isTelemetryRoute(route)) {
      const envelope = api.presentSnapshot() || {};
      const telemetry = envelope.data || {};
      const signature = `${route}:${telemetryVisualSignature(telemetry, route)}`;
      if (envelope.ok !== false && signature !== previousTelemetrySignature) {
        previousTelemetrySignature = signature;
        store.patch({ telemetry, presentRevision: Number(envelope.revision || 0) });
        const state = store.get();
        if (route === 'map') renderLightLiveContext(state, route);
      } else if (envelope.ok === false && previousTelemetrySignature !== 'ponte-fora') {
        // Ponte/serviço fora: o último quadro não pode ficar na tela como se fosse vivo (com idade "40 ms" congelada).
        previousTelemetrySignature = 'ponte-fora';
        store.patch({ telemetry: {} });
        const state = store.get();
        if (route === 'map') renderLightLiveContext(state, route);
      }
    }

    const state = store.get();
    if (route === 'map' && instances.map && (state.map?.state === 'writing' || state.map?.state === 'reading' || instances.map.curveImport)) instances.map.poll();
    if (route === 'curve' && instances.curve && (instances.curve.reading || instances.curve.writing)) instances.curve.poll();
  }

  // Reconexão é evento; entrar na aba já solicita a leitura daquela tela.
  let lastUsb = null;
  function autoSyncEcu(route, online) {
    const justConnected = online && lastUsb !== true;
    lastUsb = online;
    if (justConnected && (route === 'curve' || route === 'map')) instances[route]?.autoSync?.(true);
  }

  function refreshStatus() {
    const status = api.status() || {};
    const route = store.get().route;
    autoSyncEcu(route, status.usbConnected === true);
    // Sugestão do Mapa K: redesenhada pela amostra de status da rota (não só durante leitura/gravação).
    if (route === 'map' && instances.map && !instances.map.reading) instances.map.refreshSuggestion?.();
    const signature = JSON.stringify({ status, demo: api.isDemo() });
    if (signature !== previousStatusSignature) {
      previousStatusSignature = signature;
      store.patch({ status, demo: api.isDemo() });
    }
    const state = store.get();
  }

  function toolsEditing() {
    const host = byId('toolDiagnosticsWorkspace');
    const sessionsHost = byId('sessionsHost');
    const active = document.activeElement;
    if (sessionsHost && active && sessionsHost.contains(active) && ['INPUT', 'SELECT'].includes(active.tagName)) return true;
    return !!host && !!document.activeElement && host.contains(document.activeElement) &&
      ['INPUT', 'SELECT', 'BUTTON'].includes(document.activeElement.tagName);
  }

  /** Único pump de ScienceSnapshot; rebuild pesado acontece fora da WebView no Android. */
  function refreshContext() {
    const state = store.get();
    const route = state.route;
    const patch = {};
    // Só Ferramentas lê a ciência (cartão Aprendizado); Mapa K e Curva K não precisam dela (poda, 2026-10-13).
    const needsScience = route === 'tools';

    if (needsScience) {
      let science = {};
      try { science = api.scienceSnapshotSince(scienceRevision) || {}; }
      catch (error) { console.error('[OMEGAS] Leitura auxiliar indisponível', error); }
      const nextRevision = Number(science.revision || scienceRevision || 0);
      // Ciência igual (changed:false) e mesmo estado de recálculo: nada a gravar no store, nenhum redesenho.
      const refreshing = science.refreshing === true;
      if (refreshing !== (state.scienceRefreshing === true)) patch.scienceRefreshing = refreshing;
      if (science.changed === true && science.data && typeof science.data === 'object') {
        scienceRevision = nextRevision;
        const data = science.data;
        if (data.learning) patch.learning = data.learning;
        if (data.calibrationState) patch.calibrationState = withoutFrozenQueue(data.calibrationState);
        const predictor = data.predictor || data.calibrationState?.predictor;
        if (predictor) {
          patch.predictor = { ...(state.predictor || {}), state: predictor.ok === false ? 'error' : 'ready', data: predictor };
        }
        patch.scienceRevision = scienceRevision;
      }
    }

    if (route === 'tools' || route === 'sessions') {
      try { patch.sessionStatus = api.sessionStatus() || {}; }
      catch (error) { console.error('[OMEGAS] Situação das gravações indisponível', error); }
      try { patch.logs = api.logs() || []; }
      catch (error) { console.error('[OMEGAS] Registro indisponível', error); }
      // Listar as gravações lê o disco (manifesto e tamanho de cada uma): não a cada 2 s. Só ao entrar na aba, quando
      // a gravação começa/para, quando algo pede atualização (omegas-refresh).
      const sessionsKey = `${patch.sessionStatus.recording === true}:${patch.sessionStatus.sessionId ?? ''}`;
      if (!Array.isArray(state.sessions) || sessionsKey !== toolsSessionsKey || toolsSessionsStale) {
        try { patch.sessions = api.sessions() || []; }
        catch (error) { console.error('[OMEGAS] Lista de gravações indisponível', error); }
        toolsSessionsKey = sessionsKey;
        toolsSessionsStale = false;
      }
    }
    if (Object.keys(patch).length) store.patch(patch);
    const updated = store.get();
    if ((route === 'tools' || route === 'sessions') && !toolsEditing()) {
      try { utilities?.render(updated); }
      catch (error) { console.error('[OMEGAS] Ferramentas auxiliares indisponíveis', error); }
    }
    if (route === 'tools') {
      try { if (advanced) advanced.refresh(); else toolsUnavailable(); }
      catch (error) { toolsUnavailable(error); }
    } else if (advanced?.open) {
      try { advanced.refresh(); } catch (error) { console.error('[OMEGAS] Falha ao fechar Ferramentas', error); }
    }
  }

  let toolsSessionsKey = '';
  /** Relistar gravações no próximo contexto (entrar na aba, omegas-refresh). */
  let toolsSessionsStale = true;
  const ROUTE_CLASS = { map: 'MapScreen', curve: 'CurveScreen', refino: 'RefinoScreen', tools: 'AdvancedPanel', sessions: 'Drawers' };
  const ROUTE_HOST = { map: '[data-screen="map"]', curve: '[data-screen="curve"]', refino: '#refinoScreenHost', tools: '#toolAdvanced', sessions: '#sessionsScreen' };
  /**
   * Todos os scripts já estão carregados (index.html). Se a parte desta aba não existe ou falha ao abrir, a aba mostra um
   * cartão com a retentativa por TOQUE (nunca tela vazia, nunca repetição automática).
   */
  function routeFailureCard(route, error) {
    if (error) console.error('[OMEGAS] Tela não abriu', route, error);
    failedRoutes.add(route);
    const host = document.querySelector(ROUTE_HOST[route] || '#toolAdvanced');
    if (!host || host.querySelector('[data-route-failed]')) return;
    const card = document.createElement('article');
    card.className = 'tool-tile route-failure';
    card.dataset.routeFailed = route;
    card.setAttribute('role', 'status');
    card.innerHTML = '<h3 class="tile-line reason">Não foi possível abrir esta parte. Reconectando…</h3>';
    host.prepend(card);
  }
  /** O app tenta de novo sozinho, por EVENTO (próximo dado válido que chega), sem botão e sem relógio. */
  function retryFailedRoutes() {
    if (!failedRoutes.size) return;
    const route = store.get().route;
    if (!failedRoutes.has(route)) return;
    failedRoutes.delete(route);
    document.querySelectorAll('[data-route-failed="' + route + '"]').forEach(n => n.remove());
    activateRoute(route, store.get().routeContext);
  }
  function activateRoute(route, context) {
    try {
      if (ROUTE_CLASS[route] && !ui[ROUTE_CLASS[route]]) throw new Error('Parte ausente: ' + ROUTE_CLASS[route]);
      enterRoute(route, context);
    } catch (error) {
      if (route === 'tools') toolsUnavailable(error); else routeFailureCard(route, error);
    }
  }

  /** Pinta cache primeiro; bridge/ciência só são consultadas depois de um paint. */
  function enterRoute(route, context) {
    // Cursor em tempo real nos dois gráficos de aquisição (AutoCal e Refino, dono 2026-10-13); demais abas a 200 ms.
    scheduler.setCadenceMs(route === 'autocal' || route === 'refino' ? 50 : 200);
    store.patch({ toolsOpen: route === 'tools' });
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
    if (route === 'tools' || route === 'sessions') {
      // Estado USB nativo no próprio toque de entrada (AutoCal e Ferramentas não podem divergir).
      // No WebView, onStatus pode ainda não ter rodado desde a conexão física.
      refreshStatus();
      ensureTools();
      toolsSessionsStale = true; // entrar na aba relista as gravações
      // Anti-apagão aparece no próprio toque, sem depender de leituras auxiliares/arquivos.
      if (route === 'tools') {
        try { if (advanced) advanced.refresh(); else toolsUnavailable(); }
        catch (error) { toolsUnavailable(error); }
      }
      try { if (!toolsEditing()) utilities?.render(store.get()); }
      catch (error) { console.error('[OMEGAS] App e arquivos não abriu', error); if (route === 'tools' && !advanced) toolsUnavailable(error); }
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
    // Sem teclado obrigatório (dono, 2026-10-20): campo numérico nasce inputmode="none"; só o lápis abre o teclado, e ao sair volta a "none".
    document.addEventListener('click', event => {
      const pen = event.target.closest?.('[data-kbd-open], svg.pencil');
      if (!pen) return;
      const input = pen.closest('.num-box, .kbd-box, label')?.querySelector('input:not([type=checkbox]):not([readonly])');
      if (!input || input.disabled) return;
      input.setAttribute('inputmode', input.dataset.kbd || 'decimal');
      input.focus();
      try { input.select?.(); } catch (_) { /* campo sem seleção */ }
    });
    document.addEventListener('focusout', event => {
      const el = event.target;
      if (el?.matches?.('input[data-kbd]')) el.setAttribute('inputmode', 'none');
    });
    byId('alertToast')?.querySelector('button')?.addEventListener('click', () => byId('alertToast')?.classList.remove('show'));

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
      toolsSessionsStale = true; // algo mudou (gravação apagada, exportada…): relista ao atualizar
      afterPaint(() => {
        refreshStatus();
        refreshContext();
        const route = store.get().route;
        if (isTelemetryRoute(route)) {
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

  root.OmegasApp = { api, store, router, scheduler, screens: instances };
  initialize();
  if (typeof root.dispatchEvent === 'function' && typeof Event === 'function') root.dispatchEvent(new Event('omegas-app-ready'));
})(typeof window !== 'undefined' ? window : globalThis);