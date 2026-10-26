(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  // Tela de partida (casco). Será substituída pela tela completa; mantenha a classe, o host e o boot abaixo.
  class RegularScreen {
    constructor(app) {
      this.app = app;
      this.host = document.getElementById('regularHost');
    }
    enter() { this.render(); }
    render() {
      if (this.host) this.host.innerHTML = '<div class="nx-card"><h2>regular</h2></div>';
    }
  }
  ns.RegularScreen = RegularScreen;
  function boot() {
    const app = root.OmegasApp;
    if (!app) return;
    app.regular = new ns.RegularScreen(app);
    if (app.store.get().route === "regular") app.regular.enter();
  }
  if (root.OmegasApp) boot(); else root.addEventListener("omegas-app-ready", boot, { once: true });
})(typeof window !== "undefined" ? window : globalThis);
