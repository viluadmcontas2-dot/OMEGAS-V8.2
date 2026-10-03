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
            const next = event.target.closest && event.target.closest("[data-dash-next]");
            if (next) {
              // "Agora" só leva: abre a aba certa já posicionada; nunca executa nada.
              const app = root.OmegasApp;
              if (app && app.router && next.dataset.route) app.router.open(next.dataset.route, next.dataset.subpage || "");
              return;
            }
            if (event.target.closest && event.target.closest("[data-dash-refino]")) {
              const app = root.OmegasApp;
              if (app && app.router) app.router.navigate("refino");
            }
          });
        }
        this.root.innerHTML = '\n        <div class="now-page-intro">\n          <div><small>AGORA</small><h2>O que o motor est\xE1 fazendo</h2></div>\n          <p>Informa\xE7\xE3o essencial, grande e sem repeti\xE7\xE3o.</p>\n        </div>\n\n        <div class="now-dashboard-shell">\n          <section id="dashEquivalence" class="now-equivalence-card" aria-label="Equivalência com a gasolina" hidden>\n            <div class="now-index"><strong id="dashIndex">\u2014</strong><span>da sua condu\xE7\xE3o equivalente \xE0 gasolina</span><em id="dashIndexNote" hidden>provis\xF3rio</em></div>\n            <div class="now-next"><small>PR\xD3XIMA A\xC7\xC3O</small><p id="dashNextText">\u2014</p><button type="button" id="dashNextButton" class="primary" data-dash-next hidden>Abrir</button></div>\n          </section>\n          <section class="now-hero-card" aria-label="Leitura principal">\n            <div class="now-hero-copy">\n              <small class="now-hero-label">PETROL INJECTION</small>\n              <p id="dashHeroStatus" class="now-hero-status">Aguardando ECU</p>\n              <div class="now-hero-value"><strong id="dashHeroPetrol">\u2014</strong><em>ms</em></div>\n              <span class="now-hero-note">Leitura em tempo real da MP48 \xB7 sem duplicar telemetria</span>\n            </div>\n            <div class="now-hero-visual" aria-hidden="true"><span></span><i></i></div>\n          </section>\n\n          <section class="now-metric-grid" aria-label="Telemetria essencial">\n            <article class="now-metric-card"><small>RPM</small><b id="dashRpm">\u2014</b><span>rota\xE7\xE3o</span></article>\n            <article class="now-metric-card"><small>MAP</small><b id="dashMap">\u2014</b><span>bar</span></article>\n            <article class="now-metric-card"><small>COMBUST\xCDVEL</small><b id="dashFuel">\u2014</b><span>MP48</span></article>\n            <article class="now-metric-card"><small>LEVELS RAW</small><b id="dashLevelsRaw">\u2014</b><span>MP48 bruto \xB7 sem %</span></article>\n            <article class="now-metric-card now-refino-card" role="button" data-dash-refino><small>REFINO</small><b id="dashRefino">\u2014</b><span id="dashRefinoNext">toque para abrir</span></article>\n            <article class="now-metric-card"><small>C\xC9LULA</small><b id="dashCell">\u2014</b><span>posi\xE7\xE3o atual</span></article>\n          </section>\n\n          <section id="dashHealth" class="now-session-card" data-level="offline">\n            <span class="state-indicator"></span>\n            <div class="now-session-copy"><small>SESS\xC3O</small><b>MP48 desconectado</b><p data-health-detail>Conecte a ECU para iniciar a sess\xE3o</p></div>\n            <div class="now-session-facts"><span id="dashEcuStatus">ECU offline</span><span id="dashAge">\u2014</span></div>\n          </section>\n        </div>';
      }
      /**
       * Bloco do cérebro de equivalência. Sem dado (eq == null) o bloco fica oculto e o Agora é o layout de sempre.
       * Lê só o que o Kotlin entregou; não calcula índice nem decide ação.
       */
      renderEquivalence(eq) {
        const block = document.getElementById("dashEquivalence");
        if (!block) return;
        const valid = eq && eq.index && Number.isFinite(Number(eq.index.value));
        if (!valid) {
          if (!block.hidden) block.hidden = true;
          block.parentElement && block.parentElement.classList.remove("has-equivalence");
          return;
        }
        block.hidden = false;
        block.parentElement && block.parentElement.classList.add("has-equivalence");
        const percent = Math.max(0, Math.min(100, Math.round(Number(eq.index.value) * (Number(eq.index.value) <= 1 ? 100 : 1))));
        text("dashIndex", percent + "%");
        const note = document.getElementById("dashIndexNote");
        if (note) note.hidden = eq.index.provisional !== true;
        const action = eq.nextAction || null;
        text("dashNextText", action && action.text ? action.text : "Nada a fazer agora.");
        const button = document.getElementById("dashNextButton");
        if (!button) return;
        const routes = (root.OmegasUi && root.OmegasUi.ROUTES) || [];
        const ok = !!(action && action.route && action.route !== "dashboard" && routes.includes(action.route));
        button.hidden = !ok;
        if (ok) {
          const names = { map: "Mapa K", curve: "Curva K", autocal: "AutoCal", refino: "Refino", sessions: "Sess\xF5es", tools: "Ferramentas" };
          button.dataset.route = action.route;
          button.dataset.subpage = action.subpage || "";
          const label = "Ir para " + (names[action.route] || action.route);
          if (button.textContent !== label) button.textContent = label;
        }
      }
      /** Fase do refino (o nosso AutoCal) em uma linha; consulta a cada 3 s, no máximo. */
      renderRefino() {
        const now = Date.now();
        if (this.refinoAt && now - this.refinoAt < 3e3) return;
        this.refinoAt = now;
        const api = root.OmegasUi && root.OmegasUi.AutoCalApi;
        const eq = api && typeof api.refinementPhase === "function" ? api.refinementPhase() : null;
        const pilot = eq && eq.autopilot || {};
        const labels = { SEM_ECU: "Sem ECU", ECU_TRABALHANDO: "ECU auto", COLETANDO_NOSSOS: "Coletando", PROPOSTA_PRONTA: "Pronta", VERIFICANDO: "Medindo", RESTAURAR_TRECHO: "Piorou", ESTAVEL: "Est\xE1vel" };
        text("dashRefino", labels[pilot.phase] || "\u2014");
        text("dashRefinoNext", pilot.phase === "ESTAVEL" ? "pode desconectar" : pilot.phase === "PROPOSTA_PRONTA" ? "toque para revisar" : "toque para abrir");
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
        this.renderEquivalence(state.equivalence || null);
        text("dashCell", row !== null && column !== null ? "".concat(row + 1, "\xD7").concat(column + 1) : "\u2014");
        text("dashEcuStatus", connected ? "ECU online" : "ECU offline");
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
