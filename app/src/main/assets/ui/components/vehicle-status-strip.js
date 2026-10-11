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

  // Formatadores criados uma vez (mesmo texto de toLocaleString; criar um por quadro custava ~25 µs cada).
  const RPM_FORMAT = new Intl.NumberFormat('pt-BR');
  const MS_FORMAT = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const AGE_FORMAT = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

  function ageLabel(ageMs) {
    const age = finite(ageMs);
    if (age === null || age < 0) return '—';
    if (age < 1000) return `${Math.round(age)} ms`;
    return `${AGE_FORMAT.format(age / 1000)} s`;
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
      const telemetryValid = telemetryRoot.valid === true;
      const live = telemetryRoot.live || telemetryRoot.data || telemetryRoot;
      const serviceRunning = status.serviceRunning === true;
      const ecuOnline = status.usbConnected === true && status.engineReady !== false;
      const rpm = telemetryValid ? finite(live.rpm ?? status.rpm) : null;
      const petrol = telemetryValid ? finite(live.petrol_ms ?? live.petrolMs ?? status.petrolMs) : null;
      const age = finite(telemetryRoot.ageMs ?? telemetryRoot.telemetryAgeMs ?? status.directTelemetryAgeMs);
      const fuel = telemetryValid ? fuelLabel(live.fuel ?? live.state ?? status.fuelState) : "—";

      this.fact('service', serviceRunning ? 'ATIVO' : 'PARADO', serviceRunning ? 'online' : 'offline');
      this.fact('ecu', ecuOnline ? 'ONLINE' : 'OFFLINE', ecuOnline ? 'online' : 'offline');
      this.fact('freshness', ageLabel(age), age !== null && age >= 0 ? 'measured' : 'unknown');
      this.fact('fuel', fuel, fuel === 'GNV' ? 'cng' : fuel === 'GASOLINA' ? 'petrol' : 'neutral');
      this.fact('rpm', rpm === null ? '—' : RPM_FORMAT.format(Math.round(rpm)), rpm === null ? 'unknown' : 'measured');
      this.fact('petrol', petrol === null ? '—' : `${MS_FORMAT.format(petrol)} ms`, petrol === null ? 'unknown' : 'measured');
    }

    /** Os 6 campos da faixa são fixos depois da injeção: guarda os nós em vez de procurar a cada quadro. */
    factNodes(key) {
      const cache = this.factCache || (this.factCache = new Map());
      let entry = cache.get(key);
      if (!entry || entry.node.isConnected === false) {
        const node = this.node?.querySelector(`[data-vehicle-fact="${key}"]`);
        if (!node) return null;
        entry = { node, target: node.querySelector('b') };
        cache.set(key, entry);
      }
      return entry;
    }

    fact(key, value, state) {
      const entry = this.factNodes(key);
      if (!entry) return;
      const { node, target } = entry;
      // Escreve só o que mudou: cada quadro novo da ECU chega aqui, e regravar o mesmo valor ainda custa ao DOM.
      const nextState = state || 'neutral';
      if (node.dataset.state !== nextState) node.dataset.state = nextState;
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
