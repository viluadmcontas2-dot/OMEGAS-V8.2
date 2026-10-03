(() => {
  (function(root) {
    "use strict";
    const ns = root.OmegasUi = root.OmegasUi || {};
    function finite(value) {
      if (value === null || value === undefined || value === "") return null;
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
    function fuelLabel(raw) {
      const value = String(raw || "\u2014").toUpperCase();
      if (value.includes("PETROL") || value.includes("GASOLINA")) return "GASOLINA";
      if (value.includes("CNG") || value.includes("GNV") || value === "GAS") return "GNV";
      if (value.includes("CUTOFF")) return "CUTOFF";
      return value || "\u2014";
    }
    function ensureStyles() {
      if (document.querySelector("link[data-dashboard-now]")) return;
      const link = document.createElement("link");
      link.rel = "stylesheet";
      link.href = "styles-dashboard-now.css";
      link.dataset.dashboardNow = "true";
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
        if (!this.refinoClickBound) {
          this.refinoClickBound = true;
          this.root.addEventListener("click", (event) => {
            if (event.target.closest && event.target.closest("[data-dash-refino]")) {
              const app = root.OmegasApp;
              if (app && app.router) app.router.navigate("refino");
            }
          });
        }
        // Agora e para dirigir (D1): 4 valores de peso igual, lidos a bra\xe7o esticado; o resto \xe9 uma faixa fina embaixo.
        this.root.innerHTML = '\n        <div class="now-dashboard-shell">\n          <section class="now-tile-grid" aria-label="Leitura principal">\n            <article class="now-tile" data-tile="petrol"><small>PETROL INJECTION</small><b><span id="dashHeroPetrol">\u2014</span><em>ms</em></b></article>\n            <article class="now-tile" data-tile="rpm"><small>RPM</small><b><span id="dashRpm">\u2014</span><em>rpm</em></b></article>\n            <article class="now-tile" data-tile="map"><small>MAP</small><b><span id="dashMap">\u2014</span><em>bar</em></b></article>\n            <article class="now-tile" data-tile="fuel"><small>COMBUST\xcdVEL</small><b><span id="dashFuel">\u2014</span></b></article>\n          </section>\n\n          <section class="now-quiet-row" aria-label="Condi\xe7\xe3o e apoio">\n            <div id="dashHealth" class="now-session-card" data-level="offline">\n              <span class="state-indicator"></span>\n              <div class="now-session-copy"><b>MP48 desconectado</b><p data-health-detail>Conecte a ECU para iniciar a sess\xe3o</p></div>\n            </div>\n            <article class="now-quiet-tile"><small>LEVELS RAW</small><b id="dashLevelsRaw">\u2014</b></article>\n            <article class="now-quiet-tile"><small>C\xc9LULA</small><b id="dashCell">\u2014</b></article>\n            <article class="now-quiet-tile now-refino-card" role="button" data-dash-refino><small>REFINO</small><b id="dashRefino">\u2014</b></article>\n          </section>\n        </div>';
      }
      /** Fase do refino (o nosso AutoCal) em uma linha; consulta a cada 3 s, no máximo. */
      renderRefino() {
        const now = Date.now();
        if (this.refinoAt && now - this.refinoAt < 3e3) return;
        this.refinoAt = now;
        const api = root.OmegasUi && root.OmegasUi.AutoCalApi;
        const eq = api && typeof api.refinementPhase === "function" ? api.refinementPhase() : null;
        const pilot = eq && eq.autopilot || {};
        const labels = { SEM_ECU: "Sem ECU", ECU_TRABALHANDO: "ECU auto", COLETANDO_NOSSOS: "Coletando", PROPOSTA_PRONTA: "Pronta", VERIFICANDO: "Medindo", RESTAURAR_TRECHO: "Piorou", ESTAVEL: "Est\xE1vel", LENDO_ECU: "Lendo ECU", TENTATIVA_ENCERRADA: "Pausado" };
        // Prazo vencido com proposta pronta: a proposta continua válida (o botão no Refino continua).
        const expiredReady = pilot.phase === "TENTATIVA_ENCERRADA" && pilot.expiredFrom === "PROPOSTA_PRONTA";
        text("dashRefino", expiredReady ? "Pronta" : labels[pilot.phase] || "\u2014");
      }
      render(state) {
        var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k;
        if (!this.root) return;
        const data = live(state);
        const telemetryRoot = state.telemetry || {};
        const telemetryValid = telemetryRoot.valid === true;
        const status = state.status || {};
        const interpolation = ((_a = state.telemetry) == null ? void 0 : _a.interpolation) || {};
        const interpolationValid = interpolation.valid === true;
        const cell = interpolation.cell || {};
        const rpm = telemetryValid ? finite((_b = data.rpm) != null ? _b : status.rpm) : null;
        const petrol = telemetryValid ? ((_d = (_c = data.petrol_ms) != null ? _c : data.petrolMs) != null ? _d : status.petrolMs) : null;
        const map = telemetryValid ? ((_g = (_f = (_e = data.load_bar) != null ? _e : data.map_bar) != null ? _f : data.mapBar) != null ? _g : status.mapBar) : null;
        const fuel = telemetryValid ? fuelLabel(data.fuel || data.state || status.fuelState) : "\u2014";
        const levelsRaw = telemetryValid ? finite(data.level_raw != null ? data.level_raw : data.levelRaw) : null;
        const age = finite((_k = (_j = (_h = state.telemetry) == null ? void 0 : _h.telemetryAgeMs) != null ? _j : (_i = state.telemetry) == null ? void 0 : _i.ageMs) != null ? _k : status.directTelemetryAgeMs);
        const connected = status.usbConnected === true;
        const stale = connected && age !== null && age > 2500;
        const expired = connected && age !== null && age > 8e3;
        const stuck = status.engineStuck === true;
        const row = interpolationValid && Number.isFinite(Number(cell.row)) && Number(cell.row) >= 0 ? Number(cell.row) : null;
        const column = interpolationValid && Number.isFinite(Number(cell.column)) && Number(cell.column) >= 0 ? Number(cell.column) : null;
        text("dashHeroPetrol", fmt(petrol, 2));
        text("dashRpm", rpm === null ? "\u2014" : Math.round(rpm).toLocaleString("pt-BR"));
        text("dashMap", fmt(map, 2));
        text("dashFuel", fuel);
        text("dashLevelsRaw", levelsRaw === null ? "\u2014" : Math.round(levelsRaw).toLocaleString("pt-BR"));
        this.renderRefino();
        text("dashCell", row !== null && column !== null ? "".concat(row + 1, "\xD7").concat(column + 1) : "\u2014");
        const tiles = this.root.querySelector(".now-tile-grid");
        if (tiles) {
          const staleTiles = connected && telemetryValid && age !== null && age > 1500 ? "true" : "false";
          if (tiles.dataset.stale !== staleTiles) tiles.dataset.stale = staleTiles;
        }
        const health = document.getElementById("dashHealth");
        if (health) {
          let level = "ok";
          let message = "Leitura em tempo real";
          let detail = "ECU e telemetria principal atualizadas";
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
