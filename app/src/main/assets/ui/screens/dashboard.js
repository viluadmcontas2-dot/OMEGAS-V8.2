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
  function fuelLabel(raw) {
    const value = String(raw || '—').toUpperCase();
    if (value.includes('PETROL') || value.includes('GASOLINA')) return 'GASOLINA';
    if (value.includes('CNG') || value.includes('GNV') || value === 'GAS') return 'GNV';
    if (value.includes('CUTOFF')) return 'CUTOFF';
    return value || '—';
  }
  function ensureStyles() {
    if (document.querySelector('link[data-witness-multimedia]')) return;
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = 'styles-witness-multimedia.css';
    link.dataset.witnessMultimedia = 'true';
    document.head.appendChild(link);
  }

  class DashboardScreen {
    constructor() {
      ensureStyles();
      this.root = document.querySelector('[data-screen="dashboard"]');
      this.lastHealthSignature = '';
      this.installLayout();
    }

    installLayout() {
      if (!this.root) return;
      this.root.classList.add('multimedia-now-screen');
      this.root.innerHTML = `
        <div class="now-page-intro">
          <div><small>AGORA</small><h2>O que o motor está fazendo</h2></div>
          <p>Informação essencial, grande e sem repetição.</p>
        </div>

        <div class="now-dashboard-shell">
          <section class="now-hero-card" aria-label="Leitura principal">
            <div class="now-hero-copy">
              <small class="now-hero-label">PETROL INJECTION</small>
              <p id="dashHeroStatus" class="now-hero-status">Aguardando ECU</p>
              <div class="now-hero-value"><strong id="dashHeroPetrol">—</strong><em>ms</em></div>
              <span class="now-hero-note">Leitura em tempo real da MP48 · sem duplicar telemetria</span>
            </div>
            <div class="now-hero-visual" aria-hidden="true"><span></span><i></i></div>
          </section>

          <section class="now-metric-grid" aria-label="Telemetria essencial">
            <article class="now-metric-card"><small>RPM</small><b id="dashRpm">0</b><span>rotação</span></article>
            <article class="now-metric-card"><small>MAP</small><b id="dashMap">—</b><span>bar</span></article>
            <article class="now-metric-card"><small>COMBUSTÍVEL</small><b id="dashFuel">—</b><span>MP48</span></article>
            <article class="now-metric-card now-stft-card"><small>STFT</small><b id="dashStft">—</b><span id="dashStftState">OBD offline</span></article>
            <article class="now-metric-card"><small>CÉLULA</small><b id="dashCell">—</b><span>posição atual</span></article>
          </section>

          <section id="dashHealth" class="now-session-card" data-level="offline">
            <span class="state-indicator"></span>
            <div class="now-session-copy"><small>SESSÃO</small><b>MP48 desconectado</b><p data-health-detail>Conecte a ECU para iniciar a sessão</p></div>
            <div class="now-session-facts"><span id="dashEcuStatus">ECU offline</span><span id="dashObdStatus">OBD offline</span><span id="dashAge">—</span></div>
          </section>
        </div>`;
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
      const map = data.load_bar ?? data.map_bar ?? data.mapBar ?? status.mapBar;
      const fuel = fuelLabel(data.fuel || data.state || status.fuelState);
      const rawStft = obdValue(obd, ['stft', 'shortTermFuelTrim', 'short_term_fuel_trim']);
      const age = finite(state.telemetry?.telemetryAgeMs ?? state.telemetry?.ageMs ?? status.directTelemetryAgeMs);
      const connected = status.usbConnected === true;
      const obdState = String(obd.connectionStage || obd.state || obd.status || '').toUpperCase();
      const obdConnected = obd.connected === true || ['LIVE', 'CONNECTED', 'CONECTADO', 'REMOTO AO VIVO'].includes(obdState);
      const stft = obdConnected ? rawStft : null;
      const stale = connected && age !== null && age > 2500;
      const expired = connected && age !== null && age > 8000;
      const stuck = status.engineStuck === true;
      const row = Number.isFinite(Number(cell.row)) ? Number(cell.row) : null;
      const column = Number.isFinite(Number(cell.column)) ? Number(cell.column) : null;

      text('dashHeroPetrol', fmt(petrol, 2));
      text('dashRpm', Math.round(rpm).toLocaleString('pt-BR'));
      text('dashMap', fmt(map, 2));
      text('dashFuel', fuel);
      text('dashStft', stft === null ? '—' : `${stft > 0 ? '+' : ''}${fmt(stft, 1)}%`);
      text('dashStftState', obdConnected ? (stft === null ? 'aguardando 0106' : 'Bank 1 · 0106') : 'OBD offline');
      text('dashCell', row !== null && column !== null ? `${row + 1}×${column + 1}` : '—');
      text('dashEcuStatus', connected ? 'ECU online' : 'ECU offline');
      text('dashObdStatus', obdConnected ? 'OBD online' : 'OBD offline');
      const ageLabel = age === null || age < 0 ? '—' : age < 1000 ? `${Math.round(age)} ms` : `${fmt(age / 1000, 1)} s`;
      text('dashAge', ageLabel);

      let heroStatus = 'Conecte a MP48 para iniciar a sessão';
      if (connected && stuck) heroStatus = 'Comunicação da ECU exige atenção';
      else if (connected && expired) heroStatus = 'Telemetria temporariamente expirada';
      else if (connected && stale) heroStatus = 'Telemetria com atraso';
      else if (connected) heroStatus = 'Leitura em tempo real · operação estável';
      text('dashHeroStatus', heroStatus);

      const health = document.getElementById('dashHealth');
      if (health) {
        let level = 'ok';
        let message = 'Leitura em tempo real';
        let detail = obdConnected
          ? 'ECU, telemetria principal e witness OBD disponíveis'
          : 'ECU e telemetria principal atualizadas · OBD opcional';
        if (!connected) { level = 'offline'; message = 'MP48 desconectado'; detail = 'Conecte a ECU para iniciar a sessão'; }
        else if (stuck) { level = 'critical'; message = 'Comunicação travada'; detail = 'Ajustes permanecem bloqueados até a condição normalizar'; }
        else if (expired) { level = 'critical'; message = 'Telemetria expirada'; detail = 'Ajustes permanecem bloqueados até a condição normalizar'; }
        else if (stale) { level = 'warning'; message = 'Telemetria atrasada'; detail = 'Ajustes permanecem bloqueados até a condição normalizar'; }
        const signature = `${level}|${message}|${detail}`;
        if (signature !== this.lastHealthSignature) {
          this.lastHealthSignature = signature;
          health.dataset.level = level;
          const title = health.querySelector('.now-session-copy b');
          const copy = health.querySelector('[data-health-detail]');
          if (title) title.textContent = message;
          if (copy) copy.textContent = detail;
        }
      }
    }
  }

  ns.DashboardScreen = DashboardScreen;
})(typeof window !== 'undefined' ? window : globalThis);
