(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Números e palavras vêm das regras únicas (core/display-rules.js, core/live-store.js): a faixa não tem formato próprio.
  const rules = ns.DisplayRules;

  class VehicleStatusStrip {
    constructor(app) {
      this.app = app;
      this.store = app.store;
      this.node = this.inject();
      this.unsubscribe = this.store.subscribe(state => this.render(state), true);
    }

    inject() {
      if (!document.querySelector('link[data-vehicle-strip-style]')) {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = 'styles-shell-status.css';
        link.dataset.vehicleStripStyle = 'true';
        document.head.appendChild(link);
      }
      const existing = document.getElementById('vehicleStatusStrip');
      if (existing) return existing;
      const header = document.querySelector('.workspace-head');
      if (!header) return null;
      const strip = document.createElement('section');
      strip.id = 'vehicleStatusStrip';
      strip.className = 'vehicle-status-strip';
      strip.setAttribute('aria-label', 'Estado atual do veículo e da ECU');
      strip.innerHTML = `
        <div data-vehicle-fact="ecu"><small>ECU</small><b>—</b></div>
        <div data-vehicle-fact="fuel"><small>COMBUSTÍVEL</small><b><span id="dashFuel" data-reading-value>—</span></b></div>
        <div data-vehicle-fact="rpm"><small>RPM</small><b><span id="dashRpm" data-reading-value>—</span></b></div>
        <div data-vehicle-fact="petrol"><small>Inj. gasolina</small><b><span id="dashHeroPetrol" data-reading-value>—</span><em>ms</em></b></div>
        <div data-vehicle-fact="gas"><small>Inj. GNV</small><b><span id="dashGas" data-reading-value>—</span><em>ms</em></b></div>
        <div data-vehicle-fact="map"><small>MAP</small><b><span id="dashMap" data-reading-value>—</span><em>bar</em></b></div>
        <div data-vehicle-fact="age"><small>Leitura</small><b>—</b></div>`;
      header.appendChild(strip);
      return strip;
    }

    render(state) {
      if (!this.node) return;
      const status = state.status || {};
      // Mesma leitura do trilho e do Agora; em rota sem bombeador (Curva K, Ferramentas) vale o status de 1 Hz.
      const reading = ns.LiveStore.read(state, { fallback: true });
      const link = rules.connectionState(status, reading);
      const shown = reading.level === 'fresh' || reading.level === 'late';
      const late = reading.level === 'late';
      rules.setDataIfChanged(this.node, 'stale', reading.grey ? 'true' : 'false');
      const fuel = shown ? rules.fuelLabel(reading.fuel || status.fuelState) : '—';
      // ECU travada (sem resposta) não é "Conectada": mesma palavra do cartão do Agora e do glossário.
      const stuck = link.key === 'attention';
      const ecuOnline = link.online && !stuck && status.engineReady !== false;
      // A palavra vem de um lugar só (connectionState): "App travado", "ECU não responde", "USB recuperando", "Leitura pausada"…
      this.fact('ecu', stuck ? link.label : ecuOnline ? 'Conectada' : link.online ? 'Lendo…' : link.label, ecuOnline ? 'online' : link.key === 'connecting' || link.key === 'handshake' || link.key === 'recovering' ? 'connecting' : 'offline');
      this.fact('fuel', fuel, late ? 'late' : fuel === 'GNV' ? 'cng' : fuel === 'GASOLINA' ? 'petrol' : 'neutral');
      this.fact('rpm', rules.rpm(reading.rpm), reading.rpm === null ? 'unknown' : late ? 'late' : 'measured');
      this.fact('petrol', fuel === 'CORTE' ? '—' : rules.ms(reading.petrolMs), fuel === 'CORTE' || reading.petrolMs === null ? 'unknown' : late ? 'late' : 'measured');
      this.fact('gas', rules.ms(reading.gasMs), reading.gasMs === null ? 'unknown' : late ? 'late' : 'measured');
      this.fact('map', rules.bar(reading.mapBar), reading.mapBar === null ? 'unknown' : late ? 'late' : 'measured');
      // Idade na palavra do glossário ("agora", "há 2 s"); atrasado também aqui, não só no Agora.
      const age = reading.ageMs === null ? '—' : rules.ageSinceMs(reading.ageMs);
      this.fact('age', late ? `${age} · atrasado` : reading.level === 'lost' ? `${age} · sem dados` : age, reading.ageMs === null ? 'unknown' : reading.level === 'fresh' ? 'measured' : 'late');
    }

    fact(key, value, state) {
      const node = this.node?.querySelector(`[data-vehicle-fact="${key}"]`);
      if (!node) return;
      ns.DisplayRules.setDataIfChanged(node, 'state', state || 'neutral');
      const target = node.querySelector('[data-reading-value]') || node.querySelector('b');
      if (target && target.textContent !== String(value)) target.textContent = String(value);
      const unit = node.querySelector('em');
      const unitText = value === '—' ? '' : key === 'map' ? 'bar' : 'ms';
      if (unit && unit.textContent !== unitText) unit.textContent = unitText;
    }
  }

  function boot() {
    const app = root.OmegasApp;
    if (!app?.store) {
      if (typeof root.addEventListener === 'function') root.addEventListener('omegas-app-ready', boot, { once: true });
      return;
    }
    if (app.vehicleStatusStrip) return;
    app.vehicleStatusStrip = new VehicleStatusStrip(app);
  }

  ns.VehicleStatusStrip = VehicleStatusStrip;
  boot();
})(typeof window !== 'undefined' ? window : globalThis);
