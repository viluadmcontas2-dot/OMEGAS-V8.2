(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  // Tela de partida (casco). Será substituída pela tela completa; mantenha a classe, o host e o boot abaixo.
  class CarroScreen {
    constructor(app) {
      this.app = app;
      this.host = document.getElementById('carroHost');
    }
    enter() { this.render(); }
    render() {
      if (this.host) this.host.innerHTML = '<div class="nx-card"><h2>carro</h2></div>';
    }
  }
  ns.CarroScreen = CarroScreen;
  function boot() {
    const app = root.OmegasApp;
    if (!app) return;
    app.carro = new ns.CarroScreen(app);
    if (app.store.get().route === "carro") app.carro.enter();
  }
  if (root.OmegasApp) boot(); else root.addEventListener("omegas-app-ready", boot, { once: true });
})(typeof window !== "undefined" ? window : globalThis);
