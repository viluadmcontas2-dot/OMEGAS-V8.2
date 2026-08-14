(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  const ROUTES = ['dashboard', 'learning', 'predictor', 'map', 'curve', 'obd', 'suggestions', 'tools'];
  const STORAGE_KEY = 'omegas-v8-route';

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

  // Extensões visuais usam o mesmo Store/Router/Scheduler do shell; não criam polling próprio.
  loadOptionalScript('components/vehicle-status-strip.js');
  loadOptionalScript('core/predictor-model.js', () => loadOptionalScript('screens/predictor.js'));
  loadOptionalScript('core/autocal-api.js', () => loadOptionalScript('screens/autocal-cockpit.js'));
})(typeof window !== 'undefined' ? window : globalThis);
