(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  const ROUTES = ['regular', 'carro', 'autocal', 'refino', 'map', 'curve', 'sessions', 'tools'];
  // Ajuste fino agrupa quatro visões (Aprendizado, Automático, Região, Geral). 'ajuste' não é tela: abre a última visão usada.
  const AJUSTE_VIEWS = ['autocal', 'refino', 'map', 'curve'];
  const STORAGE_KEY = 'omegas-v9-route';
  const AJUSTE_KEY = 'omegas-v9-ajuste';
  const DEFAULT_ROUTE = 'regular';
  function lastAjuste() {
    let saved = 'autocal';
    try { saved = root.localStorage.getItem(AJUSTE_KEY) || saved; } catch (_) {}
    return AJUSTE_VIEWS.includes(saved) ? saved : 'autocal';
  }
  /** Grupo da aba inferior que contém a rota. */
  function groupOf(route) {
    if (AJUSTE_VIEWS.includes(route)) return 'ajuste';
    if (route === 'sessions') return 'tools';
    return route;
  }

  // Todos os scripts entram no index.html, em ordem de dependência (como a AutoCal). O router só navega.
  class Router {
    constructor(store) {
      this.store = store;
      this.onNavigate = null;
    }
    current() { return this.store.get().route; }
    navigate(route, context) {
      if (route === 'ajuste') route = lastAjuste();
      if (!ROUTES.includes(route)) return false;
      const previous = this.current();
      if (previous === route && context === undefined) return true;
      this.store.patch({ route, routeContext: context === undefined ? null : context });
      try {
        root.localStorage.setItem(STORAGE_KEY, route);
        if (AJUSTE_VIEWS.includes(route)) root.localStorage.setItem(AJUSTE_KEY, route);
      } catch (_) {}
      if (typeof this.onNavigate === 'function') this.onNavigate(route, previous, context);
      return true;
    }
    restore() {
      let saved = DEFAULT_ROUTE;
      try { saved = root.localStorage.getItem(STORAGE_KEY) || saved; } catch (_) {}
      if (!ROUTES.includes(saved)) saved = DEFAULT_ROUTE;
      this.store.patch({ route: saved, routeContext: null });
      return saved;
    }
  }

  ns.Router = Router;
  ns.ROUTES = ROUTES;
  ns.AJUSTE_VIEWS = AJUSTE_VIEWS;
  ns.routeGroup = groupOf;
})(typeof window !== 'undefined' ? window : globalThis);
