(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  function finite(value) {
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }

  function fuelLabel(raw) {
    const value = String(raw || '—').toUpperCase();
    if (value.includes('PETROL') || value.includes('GASOLINA')) return 'GASOLINA';
    if (value.includes('CNG') || value.includes('GNV') || value === 'GAS') return 'GNV';
    if (value.includes('CUTOFF')) return 'CUTOFF';
    if (value.includes('TRANS')) return 'TRANSIÇÃO';
    if (value.includes('OFF') || value.includes('DESLIG')) return 'DESLIGADO';
    return value || '—';
  }

  function ageLabel(ageMs) {
    const age = finite(ageMs);
    if (age === null || age < 0) return '—';
    if (age < 1000) return `${Math.round(age)} ms`;
    return `${(age / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} s`;
  }

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
        <div data-vehicle-fact="service"><small>SERVIÇO</small><b>—</b></div>
        <div data-vehicle-fact="ecu"><small>ECU</small><b>—</b></div>
        <div data-vehicle-fact="freshness"><small>FRESCOR</small><b>—</b></div>
        <div data-vehicle-fact="fuel"><small>COMBUSTÍVEL</small><b>—</b></div>
        <div data-vehicle-fact="rpm"><small>RPM</small><b>—</b></div>
        <div data-vehicle-fact="petrol"><small>PETROL INJ.</small><b>—</b></div>`;
      header.appendChild(strip);
      return strip;
    }

    render(state) {
      if (!this.node) return;
      const status = state.status || {};
      const telemetryRoot = state.telemetry || {};
      const live = telemetryRoot.live || telemetryRoot.data || telemetryRoot;
      const serviceRunning = status.serviceRunning === true;
      const ecuOnline = status.usbConnected === true && status.engineReady !== false;
      const rpm = finite(live.rpm ?? status.rpm);
      const petrol = finite(live.petrol_ms ?? live.petrolMs ?? status.petrolMs);
      const age = finite(telemetryRoot.ageMs ?? telemetryRoot.telemetryAgeMs ?? status.directTelemetryAgeMs);
      const fuel = fuelLabel(live.fuel ?? live.state ?? status.fuelState);

      this.fact('service', serviceRunning ? 'ATIVO' : 'PARADO', serviceRunning ? 'online' : 'offline');
      this.fact('ecu', ecuOnline ? 'ONLINE' : 'OFFLINE', ecuOnline ? 'online' : 'offline');
      this.fact('freshness', ageLabel(age), age !== null && age >= 0 ? 'measured' : 'unknown');
      this.fact('fuel', fuel, fuel === 'GNV' ? 'cng' : fuel === 'GASOLINA' ? 'petrol' : 'neutral');
      this.fact('rpm', rpm === null ? '—' : Math.round(rpm).toLocaleString('pt-BR'), rpm === null ? 'unknown' : 'measured');
      this.fact('petrol', petrol === null ? '—' : `${petrol.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ms`, petrol === null ? 'unknown' : 'measured');
    }

    fact(key, value, state) {
      const node = this.node?.querySelector(`[data-vehicle-fact="${key}"]`);
      if (!node) return;
      node.dataset.state = state || 'neutral';
      const target = node.querySelector('b');
      if (target && target.textContent !== String(value)) target.textContent = String(value);
    }
  }

  function boot() {
    const app = root.OmegasApp;
    if (!app?.store) {
      root.setTimeout(boot, 25);
      return;
    }
    if (app.vehicleStatusStrip) return;
    app.vehicleStatusStrip = new VehicleStatusStrip(app);
  }

  ns.VehicleStatusStrip = VehicleStatusStrip;
  boot();
})(typeof window !== 'undefined' ? window : globalThis);
