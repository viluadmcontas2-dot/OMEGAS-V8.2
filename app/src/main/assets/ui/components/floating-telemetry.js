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

  function freshness(ageMs) {
    const age = finite(ageMs);
    if (age === null || age < 0) return '—';
    if (age < 1000) return `${Math.round(age)} ms`;
    return `${(age / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} s`;
  }

  class FloatingTelemetry {
    constructor(app) {
      this.app = app;
      this.store = app.store;
      this.collapsed = false;
      this.node = this.inject();
      this.bind();
      this.unsubscribe = this.store.subscribe(state => this.render(state), true);
    }

    inject() {
      if (!document.querySelector('link[data-floating-telemetry-style]')) {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = 'styles-floating-telemetry.css';
        link.dataset.floatingTelemetryStyle = 'true';
        document.head.appendChild(link);
      }
      const existing = document.getElementById('floatingTelemetry');
      if (existing) return existing;
      const node = document.createElement('aside');
      node.id = 'floatingTelemetry';
      node.className = 'floating-telemetry';
      node.setAttribute('aria-label', 'Telemetria flutuante observacional');
      node.innerHTML = `
        <header><div><small>TELEMETRIA</small><b data-float-fuel>—</b></div><button type="button" data-float-toggle aria-label="Recolher telemetria">−</button></header>
        <div class="floating-telemetry-grid">
          <div><small>RPM</small><b data-float-rpm>—</b></div>
          <div><small>PETROL</small><b data-float-petrol>—</b></div>
          <div><small>GAS</small><b data-float-gas>—</b></div>
          <div><small>CÉLULA</small><b data-float-cell>—</b></div>
          <div><small>FRESCOR</small><b data-float-freshness>—</b></div>
        </div>
        <footer><span data-float-ecu>ECU —</span><span>somente observação</span></footer>`;
      document.body.appendChild(node);
      return node;
    }

    bind() {
      this.node?.querySelector('[data-float-toggle]')?.addEventListener('click', () => {
        this.collapsed = !this.collapsed;
        this.node.classList.toggle('collapsed', this.collapsed);
        const button = this.node.querySelector('[data-float-toggle]');
        if (button) {
          button.textContent = this.collapsed ? '+' : '−';
          button.setAttribute('aria-label', this.collapsed ? 'Abrir telemetria' : 'Recolher telemetria');
        }
      });
    }

    render(state) {
      if (!this.node) return;
      const status = state.status || {};
      const telemetryRoot = state.telemetry || {};
      const live = telemetryRoot.live || telemetryRoot.data || telemetryRoot;
      const interpolation = telemetryRoot.interpolation || {};
      const cell = interpolation.cell || {};
      const decision = state.learningDecision || {};
      const rpm = finite(live.rpm ?? status.rpm);
      const petrol = finite(live.petrol_ms ?? live.petrolMs ?? status.petrolMs);
      const gas = finite(live.gas_ms_diagnostic ?? live.gasMsDiagnostic ?? live.gas_ms ?? live.gasMs ?? status.gasMsDiagnostic);
      const age = finite(telemetryRoot.ageMs ?? telemetryRoot.telemetryAgeMs ?? status.directTelemetryAgeMs);
      const row = finite(cell.row ?? decision.cell_row);
      const column = finite(cell.column ?? decision.cell_column);
      const cellLabel = row !== null && column !== null ? `R${Math.round(row) + 1} · C${Math.round(column) + 1}` : '—';
      const ecuOnline = status.usbConnected === true && status.engineReady !== false;

      this.text('fuel', fuelLabel(live.fuel ?? live.state ?? status.fuelState));
      this.text('rpm', rpm === null ? '—' : Math.round(rpm).toLocaleString('pt-BR'));
      this.text('petrol', petrol === null ? '—' : `${petrol.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ms`);
      this.text('gas', gas === null ? '—' : `${gas.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ms`);
      this.text('cell', cellLabel);
      this.text('freshness', freshness(age));
      this.text('ecu', ecuOnline ? 'ECU ONLINE' : 'ECU OFFLINE');
      this.node.dataset.ecu = ecuOnline ? 'online' : 'offline';
    }

    text(key, value) {
      const target = this.node?.querySelector(`[data-float-${key}]`);
      if (target && target.textContent !== String(value)) target.textContent = String(value);
    }
  }

  function boot() {
    const app = root.OmegasApp;
    if (!app?.store) {
      root.setTimeout(boot, 25);
      return;
    }
    if (app.floatingTelemetry) return;
    app.floatingTelemetry = new FloatingTelemetry(app);
  }

  ns.FloatingTelemetry = FloatingTelemetry;
  boot();
})(typeof window !== 'undefined' ? window : globalThis);
