(() => {
  (function(root) {
    "use strict";
    const ns = root.OmegasUi = root.OmegasUi || {};
    function finite(value) {
      return Number.isFinite(Number(value)) ? Number(value) : null;
    }
    function fmt(value, digits) {
      const n = finite(value);
      return n === null ? "\u2014" : n.toLocaleString("pt-BR", { minimumFractionDigits: digits, maximumFractionDigits: digits });
    }
    function text(id, value) {
      const node = document.getElementById(id);
      if (!node) return;
      const next = value == null ? "\u2014" : String(value);
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
      const value = String(raw || "\u2014").toUpperCase();
      if (value.includes("PETROL") || value.includes("GASOLINA")) return "GASOLINA";
      if (value.includes("CNG") || value.includes("GNV") || value === "GAS") return "GNV";
      if (value.includes("CUTOFF")) return "CUTOFF";
      return value || "\u2014";
    }
    function ensureStyles() {
      if (document.querySelector("link[data-witness-multimedia]")) return;
      const link = document.createElement("link");
      link.rel = "stylesheet";
      link.href = "styles-witness-multimedia.css";
      link.dataset.witnessMultimedia = "true";
      document.head.appendChild(link);
    }
    class DashboardScreen {
      constructor() {
        ensureStyles();
        this.root = document.querySelector('[data-screen="dashboard"]');
        this.lastHealthSignature = "";
        this.installLayout();
      }
      installLayout() {
        if (!this.root) return;
        this.root.classList.add("multimedia-now-screen");
        this.root.innerHTML = '\n        <div class="now-page-intro">\n          <div><small>AGORA</small><h2>O que o motor est\xE1 fazendo</h2></div>\n          <p>Informa\xE7\xE3o essencial, grande e sem repeti\xE7\xE3o.</p>\n        </div>\n\n        <div class="now-dashboard-shell">\n          <section class="now-hero-card" aria-label="Leitura principal">\n            <div class="now-hero-copy">\n              <small class="now-hero-label">PETROL INJECTION</small>\n              <p id="dashHeroStatus" class="now-hero-status">Aguardando ECU</p>\n              <div class="now-hero-value"><strong id="dashHeroPetrol">\u2014</strong><em>ms</em></div>\n              <span class="now-hero-note">Leitura em tempo real da MP48 \xB7 sem duplicar telemetria</span>\n            </div>\n            <div class="now-hero-visual" aria-hidden="true"><span></span><i></i></div>\n          </section>\n\n          <section class="now-metric-grid" aria-label="Telemetria essencial">\n            <article class="now-metric-card"><small>RPM</small><b id="dashRpm">0</b><span>rota\xE7\xE3o</span></article>\n            <article class="now-metric-card"><small>MAP</small><b id="dashMap">\u2014</b><span>bar</span></article>\n            <article class="now-metric-card"><small>COMBUST\xCDVEL</small><b id="dashFuel">\u2014</b><span>MP48</span></article>\n            <article class="now-metric-card now-stft-card"><small>STFT</small><b id="dashStft">\u2014</b><span id="dashStftState">OBD offline</span></article>\n            <article class="now-metric-card"><small>C\xC9LULA</small><b id="dashCell">\u2014</b><span>posi\xE7\xE3o atual</span></article>\n          </section>\n\n          <section id="dashHealth" class="now-session-card" data-level="offline">\n            <span class="state-indicator"></span>\n            <div class="now-session-copy"><small>SESS\xC3O</small><b>MP48 desconectado</b><p data-health-detail>Conecte a ECU para iniciar a sess\xE3o</p></div>\n            <div class="now-session-facts"><span id="dashEcuStatus">ECU offline</span><span id="dashObdStatus">OBD offline</span><span id="dashAge">\u2014</span></div>\n          </section>\n        </div>';
      }
      render(state) {
        var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k;
        if (!this.root) return;
        const data = live(state);
        const status = state.status || {};
        const obd = state.obd || {};
        const interpolation = ((_a = state.telemetry) == null ? void 0 : _a.interpolation) || {};
        const cell = interpolation.cell || {};
        const rpm = finite((_b = data.rpm) != null ? _b : status.rpm) || 0;
        const petrol = (_d = (_c = data.petrol_ms) != null ? _c : data.petrolMs) != null ? _d : status.petrolMs;
        const map = (_g = (_f = (_e = data.load_bar) != null ? _e : data.map_bar) != null ? _f : data.mapBar) != null ? _g : status.mapBar;
        const fuel = fuelLabel(data.fuel || data.state || status.fuelState);
        const rawStft = obdValue(obd, ["stft", "shortTermFuelTrim", "short_term_fuel_trim"]);
        const age = finite((_k = (_j = (_h = state.telemetry) == null ? void 0 : _h.telemetryAgeMs) != null ? _j : (_i = state.telemetry) == null ? void 0 : _i.ageMs) != null ? _k : status.directTelemetryAgeMs);
        const connected = status.usbConnected === true;
        const obdState = String(obd.connectionStage || obd.state || obd.status || "").toUpperCase();
        const obdConnected = obd.connected === true || ["LIVE", "CONNECTED", "CONECTADO", "REMOTO AO VIVO"].includes(obdState);
        const stft = obdConnected ? rawStft : null;
        const stale = connected && age !== null && age > 2500;
        const expired = connected && age !== null && age > 8e3;
        const stuck = status.engineStuck === true;
        const row = Number.isFinite(Number(cell.row)) ? Number(cell.row) : null;
        const column = Number.isFinite(Number(cell.column)) ? Number(cell.column) : null;
        text("dashHeroPetrol", fmt(petrol, 2));
        text("dashRpm", Math.round(rpm).toLocaleString("pt-BR"));
        text("dashMap", fmt(map, 2));
        text("dashFuel", fuel);
        text("dashStft", stft === null ? "\u2014" : "".concat(stft > 0 ? "+" : "").concat(fmt(stft, 1), "%"));
        text("dashStftState", obdConnected ? stft === null ? "aguardando 0106" : "Bank 1 \xB7 0106" : "OBD offline");
        text("dashCell", row !== null && column !== null ? "".concat(row + 1, "\xD7").concat(column + 1) : "\u2014");
        text("dashEcuStatus", connected ? "ECU online" : "ECU offline");
        text("dashObdStatus", obdConnected ? "OBD online" : "OBD offline");
        const ageLabel = age === null || age < 0 ? "\u2014" : age < 1e3 ? "".concat(Math.round(age), " ms") : "".concat(fmt(age / 1e3, 1), " s");
        text("dashAge", ageLabel);
        let heroStatus = "Conecte a MP48 para iniciar a sess\xE3o";
        if (connected && stuck) heroStatus = "Comunica\xE7\xE3o da ECU exige aten\xE7\xE3o";
        else if (connected && expired) heroStatus = "Telemetria temporariamente expirada";
        else if (connected && stale) heroStatus = "Telemetria com atraso";
        else if (connected) heroStatus = "Leitura em tempo real \xB7 opera\xE7\xE3o est\xE1vel";
        text("dashHeroStatus", heroStatus);
        const health = document.getElementById("dashHealth");
        if (health) {
          let level = "ok";
          let message = "Leitura em tempo real";
          let detail = obdConnected ? "ECU, telemetria principal e witness OBD dispon\xEDveis" : "ECU e telemetria principal atualizadas \xB7 OBD opcional";
          if (!connected) {
            level = "offline";
            message = "MP48 desconectado";
            detail = "Conecte a ECU para iniciar a sess\xE3o";
          } else if (stuck) {
            level = "critical";
            message = "Comunica\xE7\xE3o travada";
            detail = "Ajustes permanecem bloqueados at\xE9 a condi\xE7\xE3o normalizar";
          } else if (expired) {
            level = "critical";
            message = "Telemetria expirada";
            detail = "Ajustes permanecem bloqueados at\xE9 a condi\xE7\xE3o normalizar";
          } else if (stale) {
            level = "warning";
            message = "Telemetria atrasada";
            detail = "Ajustes permanecem bloqueados at\xE9 a condi\xE7\xE3o normalizar";
          }
          const signature = "".concat(level, "|").concat(message, "|").concat(detail);
          if (signature !== this.lastHealthSignature) {
            this.lastHealthSignature = signature;
            health.dataset.level = level;
            const title = health.querySelector(".now-session-copy b");
            const copy = health.querySelector("[data-health-detail]");
            if (title) title.textContent = message;
            if (copy) copy.textContent = detail;
          }
        }
      }
    }
    ns.DashboardScreen = DashboardScreen;
  })(typeof window !== "undefined" ? window : globalThis);
})();
