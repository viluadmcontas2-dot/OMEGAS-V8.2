(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  function finite(value) { return Number.isFinite(Number(value)) ? Number(value) : null; }
  function fmt(value, digits) {
    const n = finite(value);
    return n === null ? '—' : n.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  }
  function text(id, value) {
    const node = document.getElementById(id);
    if (!node) return;
    const next = value == null ? '—' : String(value);
    if (node.textContent !== next) node.textContent = next;
  }
  function live(state) {
    const telemetry = state.telemetry || {};
    return telemetry.live || telemetry.data || telemetry;
  }
  function obdValue(obd, names) {
    for (const name of names) {
      const value = obd && obd[name];
      if (finite(value) !== null) return finite(value);
    }
    return null;
  }

  class DashboardScreen {
    constructor() {
      this.root = document.querySelector('[data-screen="dashboard"]');
      this.lastHealthSignature = '';
      this.ensureLayout();
    }

    ensureLayout() {
      const hero = this.root?.querySelector('.hero-reading');
      if (hero && !hero.classList.contains('refined-now')) {
        hero.classList.add('refined-now');
        hero.innerHTML = `
          <small>CONDIÇÃO DO MOTOR</small>
          <div class="hero-rpm"><strong id="dashHeroRpm">0</strong><em>RPM</em></div>
          <p id="dashHeroContext">Aguardando ECU</p>
          <div class="hero-context-grid">
            <div><small>PETROL INJ.</small><b id="dashPetrol">—</b></div>
            <div><small>MAP</small><b id="dashMap">—</b></div>
            <div><small>COMBUSTÍVEL</small><b id="dashFuel">—</b></div>
            <div><small>CÉLULA</small><b id="dashCell">—</b></div>
          </div>`;
      }
      const strip = this.root?.querySelector('.condition-strip');
      if (strip) {
        strip.innerHTML = `
          <div class="diagnostic"><small>GAS INJ.</small><b><span id="dashGas">—</span> ms</b><span>pulso GNV · diagnóstico</span></div>
          <div><small>ECU</small><b id="dashEcuMini">offline</b></div>
          <div><small>OBD</small><b id="dashObdMini">offline</b></div>
          <div><small>TELEMETRIA</small><b id="dashAgeMini">—</b></div>`;
      }
    }

    render(state) {
      if (!this.root) return;
      const data = live(state);
      const status = state.status || {};
      const obd = state.obd || {};
      const interpolation = state.telemetry?.interpolation || {};
      const cell = interpolation.cell || {};
      const rpm = finite(data.rpm ?? status.rpm) || 0;
      const petrol = data.petrol_ms ?? data.petrolMs ?? status.petrolMs;
      const gas = data.gas_ms_diagnostic ?? data.gasMs ?? status.gasMs;
      const map = data.load_bar ?? data.map_bar ?? data.mapBar ?? status.mapBar;
      const fuel = String(data.fuel || data.state || status.fuelState || '—').replace('PETROL', 'GASOLINA').replace('CNG', 'GNV');
      const stft = obdValue(obd, ['stft', 'shortTermFuelTrim', 'short_term_fuel_trim']);
      const ltft = obdValue(obd, ['ltft', 'longTermFuelTrim', 'long_term_fuel_trim']);
      const age = finite(state.telemetry?.telemetryAgeMs ?? state.telemetry?.ageMs ?? status.directTelemetryAgeMs);
      const connected = status.usbConnected === true;
      const obdConnected = obd.connected === true || ['CONNECTED', 'CONECTADO'].includes(String(obd.state || obd.status || '').toUpperCase());
      const stale = connected && age !== null && age > 2500;
      const expired = connected && age !== null && age > 8000;
      const stuck = status.engineStuck === true;
      const row = Number.isFinite(Number(cell.row)) ? Number(cell.row) : null;
      const column = Number.isFinite(Number(cell.column)) ? Number(cell.column) : null;

      text('dashHeroRpm', Math.round(rpm).toLocaleString('pt-BR'));
      text('dashPetrol', `${fmt(petrol, 2)} ms`);
      text('dashGas', fmt(gas, 2));
      text('dashMap', `${fmt(map, 2)} bar`);
      text('dashStft', stft === null ? '—' : `${stft > 0 ? '+' : ''}${fmt(stft, 1)}%`);
      text('dashLtft', ltft === null ? '—' : `${ltft > 0 ? '+' : ''}${fmt(ltft, 1)}%`);
      text('dashFuel', fuel);
      text('dashCell', row !== null && column !== null ? `${row + 1}×${column + 1}` : '—');
      text('dashEcuStatus', connected ? 'ECU online' : 'ECU offline');
      text('dashObdStatus', obdConnected ? 'OBD online' : 'OBD offline');
      text('dashEcuMini', connected ? 'online' : 'offline');
      text('dashObdMini', obdConnected ? 'online' : 'offline');
      const ageLabel = age === null ? '—' : age < 1000 ? `${Math.round(age)} ms` : `${fmt(age / 1000, 1)} s`;
      text('dashAge', ageLabel);
      text('dashAgeMini', ageLabel);
      text('dashHeroContext', connected
        ? `${fuel} · Petrol Inj. ${fmt(petrol, 2)} ms · MAP ${fmt(map, 2)} bar`
        : 'Conecte a MP48 para iniciar a sessão');

      const health = document.getElementById('dashHealth');
      if (health) {
        let level = 'ok';
        let message = 'Leitura em tempo real';
        let detail = 'ECU e telemetria principal atualizadas';
        if (!connected) { level = 'offline'; message = 'MP48 desconectado'; detail = 'Conecte a ECU para iniciar a sessão'; }
        else if (stuck) { level = 'critical'; message = 'Comunicação travada'; detail = 'Ajustes permanecem bloqueados até a condição normalizar'; }
        else if (expired) { level = 'critical'; message = 'Telemetria expirada'; detail = 'Ajustes permanecem bloqueados até a condição normalizar'; }
        else if (stale) { level = 'warning'; message = 'Telemetria atrasada'; detail = 'Ajustes permanecem bloqueados até a condição normalizar'; }
        const signature = `${level}|${message}|${detail}`;
        if (signature !== this.lastHealthSignature) {
          this.lastHealthSignature = signature;
          health.dataset.level = level;
          const title = health.querySelector('b');
          const copy = health.querySelector('[data-health-detail]');
          if (title) title.textContent = message;
          if (copy) copy.textContent = detail;
        }
      }
    }
  }

  ns.DashboardScreen = DashboardScreen;
})(typeof window !== 'undefined' ? window : globalThis);
