(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  /**
   * Barra do topo, persistente em TODAS as abas (dono, 2026-10-13): Tensão da ECU, MAP, Petrol Inj. e RPM, no mesmo lugar
   * e com o mesmo desenho da barra do AutoCal. É a única tela "Agora" que existe. Só lê o Store (nada de polling nem de ponte
   * própria) e só escreve no DOM o que mudou.
   */
  const STALE_MS = 2500;
  // Tensão da ECU: byte 19 do 48 01 ÷ 16 (escala PROVISÓRIA, PROTOCOLO-LANDI doc 18; 0/ausente = sem leitura).
  // Acima de 14,1 V o GNV desliga (relato de campo): o campo entra em alerta.
  const VOLTAGE_ALERT_V = 14.1;
  const VOLTAGE_RAW_PER_VOLT = 16;
  const finite = value => value === null || value === undefined || value === '' || !Number.isFinite(Number(value)) ? null : Number(value);
  const RPM_FORMAT = new Intl.NumberFormat('pt-BR');
  const TWO = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const ONE = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

  /** Valores prontos para mostrar (puro: testado sem tela). Desconhecido, inválido ou velho = "—", nunca 0. */
  function read(telemetry) {
    const source = telemetry || {};
    const age = finite(source.telemetryAgeMs ?? source.ageMs);
    const fresh = source.valid === true && !(age !== null && age > STALE_MS);
    const live = fresh ? (source.live || source.data || {}) : {};
    const rpm = finite(live.rpm);
    const petrol = finite(live.petrol_ms ?? live.petrolMs);
    const map = finite(live.load_bar ?? live.map_bar ?? live.mapBar);
    const raw = finite(live.unknown_raw_19 ?? live.unknownRaw19);
    const volts = raw !== null && raw > 0 ? raw / VOLTAGE_RAW_PER_VOLT : null;
    return {
      voltage: volts === null ? '—' : ONE.format(volts),
      voltageState: volts === null ? 'unknown' : volts >= VOLTAGE_ALERT_V ? 'alert' : 'measured',
      map: map === null ? '—' : TWO.format(map),
      petrol: petrol === null ? '—' : TWO.format(petrol),
      rpm: rpm === null ? '—' : RPM_FORMAT.format(Math.round(rpm)),
    };
  }

  class LiveBar {
    constructor(app) {
      this.node = document.getElementById('liveBar');
      this.cells = new Map();
      this.last = null;
      this.unsubscribe = app.store.subscribe(state => this.render(state), true);
    }

    cell(key) {
      let entry = this.cells.get(key);
      if (!entry || entry.node.isConnected === false) {
        const node = this.node?.querySelector(`[data-live="${key}"]`);
        if (!node) return null;
        entry = { node, value: node.querySelector('b') };
        this.cells.set(key, entry);
      }
      return entry;
    }

    render(state) {
      if (!this.node) return;
      const telemetry = state.telemetry;
      if (telemetry === this.last) return; // mesmo quadro: nada a fazer
      this.last = telemetry;
      const view = read(telemetry);
      this.put('voltage', view.voltage, view.voltageState);
      this.put('map', view.map);
      this.put('petrol', view.petrol);
      this.put('rpm', view.rpm);
    }

    put(key, value, state) {
      const entry = this.cell(key);
      if (!entry) return;
      if (state !== undefined && entry.node.dataset.state !== state) entry.node.dataset.state = state;
      if (entry.value.textContent !== value) entry.value.textContent = value;
    }
  }

  function boot() {
    const app = root.OmegasApp;
    if (!app?.store) {
      root.addEventListener?.('omegas-app-ready', boot, { once: true });
      return;
    }
    if (app.liveBar) return;
    app.liveBar = new LiveBar(app);
  }

  ns.LiveBar = LiveBar;
  ns.LiveBarModel = { read, VOLTAGE_ALERT_V };
  boot();
})(typeof window !== 'undefined' ? window : globalThis);
