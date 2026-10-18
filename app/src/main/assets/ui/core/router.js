(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  const ROUTES = ['autocal', 'refino', 'map', 'curve', 'sessions', 'tools'];
  const STORAGE_KEY = 'omegas-v8-route';

  // Cada script carrega uma vez só. Quem pede de novo espera o mesmo carregamento (nunca insere duas tags).
  const scripts = new Map();

  function loadOptionalScript(src, onload) {
    if (typeof document === 'undefined') return;
    const known = scripts.get(src);
    if (known) {
      if (typeof onload !== 'function') return;
      if (known.done) onload(); else known.waiters.push(onload);
      return;
    }
    const entry = { done: false, waiters: typeof onload === 'function' ? [onload] : [] };
    scripts.set(src, entry);
    const finish = (ok) => {
      entry.done = ok;
      if (!ok) scripts.delete(src); // Falha não pode ficar guardada como sucesso.
      entry.waiters.splice(0).forEach(fn => fn());
    };
    const script = document.createElement('script');
    script.src = src;
    script.dataset.omegasExtension = src;
    script.onload = () => finish(true);
    script.onerror = () => { console.error('[OMEGAS router] extensão não carregada:', src); finish(false); };
    document.head.appendChild(script);
  }

  /**
   * Telas pesadas só entram quando a aba é aberta (evento do operador), nunca no arranque: o app abre na AutoCal e
   * carregava Mapa K, Curva K, Ferramentas e Refino à toa (mais da metade do JavaScript). A ordem de cada grupo é a de
   * dependência; o que dois grupos dividem (write-gate, map-editor…) carrega uma vez só.
   */
  const SHARED_AUTOCAL = ['core/autocal-api.js', 'core/manual-automatch-advice.js', 'screens/autocal-original.js', 'screens/autocal-cockpit.js'];
  const ROUTE_SCRIPTS = {
    map: ['core/autocal-api.js', 'map-editor.js', 'core/curve-to-map.js', 'core/balance-runner.js', 'core/write-gate.js', 'screens/map.js'],
    curve: ['core/curve-smoothing.js', 'screens/curve.js'],
    tools: ['core/advanced-flags-data.js', 'core/advanced-flags.js', 'core/anti-apagao-valores.js', 'core/idle-mode.js', 'core/cartoes-motor.js', 'components/drawers.js', 'components/advanced.js'],
    sessions: ['components/drawers.js'],
    refino: [...SHARED_AUTOCAL, 'map-editor.js', 'core/stall-region.js', 'core/write-gate.js', 'core/map-fix.js', 'core/gasoline-reference.js', 'screens/refino.js'],
  };
  // Se a classe da tela já existe (testes que montam os arquivos à mão), não há o que carregar.
  const ROUTE_READY = { map: 'MapScreen', curve: 'CurveScreen', tools: 'AdvancedPanel', sessions: 'Drawers', refino: 'RefinoScreen' };

  /** Promessa que resolve quando a tela da rota está carregada; `null` se não há nada a esperar (já carregada). */
  function loadRoute(route) {
    if (typeof document === 'undefined' || !ROUTE_SCRIPTS[route]) return null;
    if (ns[ROUTE_READY[route]]) return null;
    const pending = ROUTE_SCRIPTS[route].filter(src => !(scripts.get(src) || {}).done);
    if (!pending.length) return null;
    return new Promise(resolve => {
      let index = 0;
      const next = () => (index < pending.length ? loadOptionalScript(pending[index++], next) : resolve());
      next();
    });
  }

  class Router {
    constructor(store) {
      this.store = store;
      this.onNavigate = null;
    }
    current() { return this.store.get().route; }
    navigate(route, context) {
      if (!ROUTES.includes(route)) return false;
      const previous = this.current();
      if (previous === route && context === undefined) return true;
      this.store.patch({ route, routeContext: context === undefined ? null : context });
      try { root.localStorage.setItem(STORAGE_KEY, route); } catch (_) {}
      if (typeof this.onNavigate === 'function') this.onNavigate(route, previous, context);
      return true;
    }
    restore() {
      let saved = 'autocal';
      try { saved = root.localStorage.getItem(STORAGE_KEY) || saved; } catch (_) {}
      if (!ROUTES.includes(saved)) saved = 'autocal';
      this.store.patch({ route: saved, routeContext: null });
      return saved;
    }
  }

  ns.Router = Router;
  ns.ROUTES = ROUTES;
  ns.loadRoute = loadRoute;
  // Nova tentativa apenas por toque do operador: não cria repetição automática.
  ns.retryRoute = function (route) {
    if (!ROUTE_SCRIPTS[route]) return null;
    ROUTE_SCRIPTS[route].forEach(src => scripts.delete(src));
    if (route === 'tools') delete ns.AdvancedPanel; // Painel incompleto não pode impedir nova carga.
    return loadRoute(route);
  };

  // Extensões visuais usam o mesmo Store/Router/Scheduler do shell; não criam polling próprio.
  loadOptionalScript('components/live-bar.js');
  // Predictor fora do produto (decisão do dono): não é carregado nem aparece na navegação.
  loadOptionalScript('core/autocal-api.js', () =>
    loadOptionalScript('core/manual-automatch-advice.js', () =>
    loadOptionalScript('screens/autocal-original.js', () =>
      loadOptionalScript('screens/autocal-cockpit.js'))));
})(typeof window !== 'undefined' ? window : globalThis);