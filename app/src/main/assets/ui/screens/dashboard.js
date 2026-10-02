(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  function finite(value) { return value != null && Number.isFinite(Number(value)) ? Number(value) : null; }
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
  const PILOT_TONE = { COLETANDO_NOSSOS: 'accent', PROPOSTA_PRONTA: 'accent', VERIFICANDO: 'accent', RESTAURAR_TRECHO: 'danger', ESTAVEL: 'ok' };
  const PILOT_POLL_MS = 3000;

  /** Cartão "Calibração" do Agora: fase do piloto do refino + índice GNV ÷ gasolina (puro). */
  function pilotCard(eq) {
    const pilot = eq && eq.autopilot;
    if (!pilot || !pilot.phase || pilot.phase === 'SEM_ECU') return null;
    const ratio = finite(eq.ratio);
    const index = ratio === null ? 'GNV ÷ gasolina: medindo' : `GNV ÷ gasolina: ${ratio >= 1 ? '+' : ''}${fmt((ratio - 1) * 100, 1)}% (${fmt(eq.samples, 0)} leituras de condução)`;
    return {
      tone: PILOT_TONE[pilot.phase] || 'muted',
      title: String(pilot.headline || ''),
      detail: String(pilot.next || ''),
      technical: index,
    };
  }

  class DashboardScreen {
    constructor() {
      this.root = document.querySelector('[data-screen="dashboard"]');
      this.lastHealthSignature = '';
      this.lastPilotAt = 0;
      this.lastPilotSignature = '';
      this.ensureLayout();
    }

    renderPilot() {
      const now = Date.now();
      if (now - this.lastPilotAt < PILOT_POLL_MS) return;
      this.lastPilotAt = now;
      const node = document.getElementById('dashCalibration');
      const api = ns.AutoCalApi;
      if (!node || !api || typeof api.equivalence !== 'function') return;
      const card = pilotCard(api.equivalence());
      const signature = card ? `${card.tone}|${card.title}|${card.detail}|${card.technical}` : '';
      if (signature === this.lastPilotSignature) return;
      this.lastPilotSignature = signature;
      node.hidden = !card;
      if (!card) return;
      node.dataset.tone = card.tone;
      node.querySelector('b').textContent = card.title;
      node.querySelector('span').textContent = card.detail;
      node.querySelector('[data-pilot-ratio]').textContent = card.technical;
    }

    ensureLayout() {
      const hero = this.root?.querySelector('.hero-reading');
      if (hero && !hero.classList.contains('refined-now')) {
        hero.classList.add('refined-now');
        hero.innerHTML = `
          <small>CONDIÇÃO DO MOTOR</small>
          <div class="hero-rpm"><strong id="dashHeroRpm">0</strong><em>RPM</em></div>
          <p id="dashHeroContext">Aguardando ECU</p>
          <details class="dashboard-details"><summary>Detalhes técnicos</summary><div class="hero-context-grid">
            <div><small>INJEÇÃO DE REFERÊNCIA</small><b id="dashPetrol">—</b></div>
            <div><small>MAP</small><b id="dashMap">—</b></div>
            <div><small>COMBUSTÍVEL</small><b id="dashFuel">—</b></div>
            <div><small>PONTO DO MAPA</small><b id="dashCell">—</b></div>
          </div></details>`;
      }
      const health = this.root?.querySelector('#dashHealth');
      if (health && !document.getElementById('dashCalibration')) {
        const card = document.createElement('section');
        card.id = 'dashCalibration';
        card.className = 'calibration-pilot';
        card.hidden = true;
        card.setAttribute('role', 'button');
        card.tabIndex = 0;
        card.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); card.click(); } });
        card.setAttribute('aria-label', 'Abrir Refino');
        card.innerHTML = '<small>CALIBRAÇÃO</small><b></b><span></span><span data-pilot-ratio></span>';
        card.addEventListener('click', () => root.OmegasApp?.router?.navigate('refino'));
        health.insertAdjacentElement('afterend', card);
      }
      const strip = this.root?.querySelector('.condition-strip');
      if (strip) {
        strip.innerHTML = `
          <details class="dashboard-details"><summary>Pulso de gás</summary><b><span id="dashGas">—</span> ms</b><span>diagnóstico técnico</span></details>
          <div><small>ECU</small><b id="dashEcuMini">offline</b></div>
          <div><small>TELEMETRIA</small><b id="dashAgeMini">—</b></div>`;
      }
    }

    render(state) {
      if (!this.root) return;
      const data = live(state);
      const status = state.status || {};
      const interpolation = state.telemetry?.interpolation || {};
      const cell = interpolation.cell || {};
      const rpm = finite(data.rpm ?? status.rpm) || 0;
      const petrol = data.petrol_ms ?? data.petrolMs ?? status.petrolMs;
      const gas = data.gas_ms_diagnostic ?? data.gasMs ?? status.gasMs;
      const map = data.load_bar ?? data.map_bar ?? data.mapBar ?? status.mapBar;
      const fuel = String(data.fuel || data.state || status.fuelState || '—').replace('PETROL', 'GASOLINA').replace('CNG', 'GNV');
      const age = finite(state.telemetry?.telemetryAgeMs ?? state.telemetry?.ageMs ?? status.directTelemetryAgeMs);
      const connected = status.usbConnected === true;
      const stale = connected && age !== null && age > 2500;
      const expired = connected && age !== null && age > 8000;
      const stuck = status.engineStuck === true;
      const row = Number.isFinite(Number(cell.row)) ? Number(cell.row) : null;
      const column = Number.isFinite(Number(cell.column)) ? Number(cell.column) : null;

      text('dashHeroRpm', Math.round(rpm).toLocaleString('pt-BR'));
      text('dashPetrol', `${fmt(petrol, 2)} ms`);
      text('dashGas', fmt(gas, 2));
      text('dashMap', `${fmt(map, 2)} bar`);
      text('dashFuel', fuel);
      text('dashCell', row !== null && column !== null ? `${row + 1}×${column + 1}` : '—');
      text('dashEcuStatus', connected ? 'ECU online' : 'ECU offline');
      text('dashEcuMini', connected ? 'online' : 'offline');
      const ageLabel = age === null ? '—' : age < 1000 ? `${Math.round(age)} ms` : `${fmt(age / 1000, 1)} s`;
      text('dashAge', ageLabel);
      text('dashAgeMini', ageLabel);
      text('dashHeroContext', connected
        ? `${fuel} · acompanhe a calibração abaixo`
        : 'Conecte a MP48 para iniciar a sessão');

      this.renderPilot();
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
  ns.DashboardModel = { pilotCard };
})(typeof window !== 'undefined' ? window : globalThis);
