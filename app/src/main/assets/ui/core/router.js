(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  const ROUTES = ['dashboard', 'map', 'curve', 'autocal', 'refino', 'suggestions', 'tools'];
  const STORAGE_KEY = 'omegas-v8-route';
  // Rotas que mostram o AGORA ao vivo: só elas recebem o pump de telemetria (Refino incluído:
  // sem isso a bolinha AGORA do Refino ficava congelada no último valor da rota anterior).
  const LIVE_ROUTES = ['dashboard', 'map', 'autocal', 'refino'];

  function loadOptionalScript(src, onload) {
    if (typeof document === 'undefined') return;
    if (document.querySelector(`script[data-omegas-extension="${src}"]`)) return;
    const script = document.createElement('script');
    script.src = src;
    script.dataset.omegasExtension = src;
    script.onload = typeof onload === 'function' ? onload : null;
    script.onerror = () => console.error('[OMEGAS router] extensão não carregada:', src);
    document.head.appendChild(script);
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
      let saved = 'dashboard';
      try { saved = root.localStorage.getItem(STORAGE_KEY) || saved; } catch (_) {}
      if (!ROUTES.includes(saved)) saved = 'dashboard';
      this.store.patch({ route: saved, routeContext: null });
      return saved;
    }
  }

  ns.Router = Router;
  ns.ROUTES = ROUTES;
  ns.LIVE_ROUTES = LIVE_ROUTES;

  // Extensões visuais usam o mesmo Store/Router/Scheduler do shell; não criam polling próprio.
  loadOptionalScript('components/vehicle-status-strip.js');
  loadOptionalScript('components/split-layout.js');
  loadOptionalScript('core/autocal-api.js', () =>
    loadOptionalScript('screens/autocal-cockpit.js', () => loadOptionalScript('screens/refino.js')));
})(typeof window !== 'undefined' ? window : globalThis);