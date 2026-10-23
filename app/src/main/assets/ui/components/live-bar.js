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
  // Nível do gás (byte 13 do 48 01 = level_raw), em bolinhas como o comutador físico. ORIENTAÇÃO MEDIDA NO CAMPO (101 sessões,
  // 210 mil quadros): o byte SOBE com o consumo (255 = vazio) e volta a ~31–78 ao abastecer; escada ≈ 75 / 125 / 177 / 253.
  // Zonas = ENTRE as 4 referências que a central guarda (Reserva 39, ¼ 93, ½ 143, ¾ 219, lidas no fio): < 39 cheio, 39–93 ¾,
  // 93–143 ½, 143–219 ¼, ≥ 219 reserva. (Antes: pontos no meio das marcas e sentido oposto, o que pintava tanque cheio de
  // vermelho.) ≤ 25 = sensor sem leitura (central sem motor dá 19–22): desconhecido, nunca "cheio". A escala será lida da
  // central (evento level_calibration da sessão) e regulável em Ferramentas.
  const LEVEL_MIN_VALID = 26;
  const LEVEL_CUTS = [[39, 'full'], [93, 'three'], [143, 'half'], [219, 'quarter']];
  const LEVEL_EMPTY = 'reserve';
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
    const levelRaw = finite(live.level_raw ?? live.levelRaw);
    const levelState = levelRaw === null || levelRaw < LEVEL_MIN_VALID ? 'unknown' : (LEVEL_CUTS.find(([cut]) => levelRaw < cut) || [0, LEVEL_EMPTY])[1];
    const fuel = String(live.fuel || '').toUpperCase();
    const sourceState = !fresh ? 'none' : fuel === 'GNV' ? 'gas' : fuel === 'GASOLINA' ? 'petrol' : 'unknown';
    return {
      levelState,
      levelText: levelState === 'unknown' ? '—' : '',
      sourceState,
      sourceText: { none: 'Sem central', gas: 'No gás', petrol: 'Na gasolina', unknown: '—' }[sourceState],
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
      this.verifyNode = document.getElementById('liveVerify');
      const Tracker = root.OmegasWriteVerify?.Tracker;
      if (Tracker) {
        this.verify = app.writeVerify = new Tracker(() => app.api?.writeVerify?.(), state => this.renderVerify(state));
        this.verifyNode?.addEventListener?.('click', () => {
          const detail = this.verify?.state?.detail;
          if (detail) app.store.patch?.({ alert: detail });
        });
      }
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

    renderVerify(view) {
      const node = this.verifyNode;
      if (!node) return;
      const idle = view.phase === 'idle';
      node.hidden = idle;
      node.dataset.phase = view.phase;
      node.title = view.detail || '';
      const label = node.querySelector('span');
      if (label) label.textContent = view.phase === 'failed' || view.phase === 'fixing' ? view.label : view.label;
    }

    render(state) {
      if (!this.node) return;
      if (this.verify?.armed) this.verify.tick(); // avança a conferência a cada resposta da ECU (telemetria), nunca por relógio
      const telemetry = state.telemetry;
      if (telemetry === this.last) return; // mesmo quadro: nada a fazer
      this.last = telemetry;
      const view = read(telemetry);
      this.put('voltage', view.voltage, view.voltageState);
      this.put('map', view.map);
      this.put('petrol', view.petrol);
      this.put('rpm', view.rpm);
      this.put('level', view.levelText, view.levelState);
      this.put('source', view.sourceText, view.sourceState);
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
  ns.LiveBarModel = { read, VOLTAGE_ALERT_V, LEVEL_CUTS };
  boot();
})(typeof window !== 'undefined' ? window : globalThis);
