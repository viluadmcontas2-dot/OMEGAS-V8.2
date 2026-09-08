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
    function escapeHtml(value) {
      return String(value != null ? value : "").replace(/[&<>\"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[char]);
    }
    function bytesLabel(bytes) {
      const n = finite(bytes) || 0;
      if (n >= 1024 * 1024) return "".concat(fmt(n / 1024 / 1024, 1), " MB");
      if (n >= 1024) return "".concat(fmt(n / 1024, 0), " KB");
      return "".concat(Math.round(n), " B");
    }
    function durationLabel(ms) {
      const value = Math.max(0, finite(ms) || 0);
      const minutes = Math.floor(value / 6e4);
      const seconds = Math.floor(value % 6e4 / 1e3);
      if (minutes >= 60) return "".concat(Math.floor(minutes / 60), "h ").concat(minutes % 60, "m");
      return "".concat(minutes, "m ").concat(seconds, "s");
    }
    function ageLabel(ms) {
      const value = finite(ms);
      if (value === null || value < 0) return "sem telemetria";
      if (value < 1e3) return "".concat(Math.round(value), " ms");
      return "".concat(fmt(value / 1e3, 1), " s");
    }
    class Drawers {
      constructor(store, router, api) {
        this.store = store;
        this.router = router;
        this.api = api;
        this.tools = document.getElementById("toolsDrawer");
        this.logLevel = "ALL";
        this.logCategory = "ALL";
        this.ensureToolsExpansion();
        this.bind();
      }
      ensureToolsExpansion() {
        if (!this.tools || document.getElementById("toolDiagnosticsWorkspace")) return;
        const host = document.createElement("div");
        host.id = "toolDiagnosticsWorkspace";
        host.className = "tool-diagnostics-workspace";
        this.tools.appendChild(host);
      }
      bind() {
        var _a, _b, _c, _d, _e, _f;
        (_a = document.getElementById("toolExportLearning")) == null ? void 0 : _a.addEventListener("click", () => this.api.exportLearning());
        (_b = document.getElementById("toolImportLearning")) == null ? void 0 : _b.addEventListener("click", () => this.api.importLearning());
        (_c = document.getElementById("toolExportLogs")) == null ? void 0 : _c.addEventListener("click", () => this.api.exportLogs());
        (_d = document.getElementById("toolSelfTest")) == null ? void 0 : _d.addEventListener("click", () => {
          const result = this.api.selfTest();
          this.store.patch({ alert: { level: (result == null ? void 0 : result.ok) ? "ok" : "warning", message: (result == null ? void 0 : result.ok) ? "Autoteste conclu\xEDdo." : (result == null ? void 0 : result.error) || "Autoteste n\xE3o conclu\xEDdo." } });
        });
        (_e = document.getElementById("toolDiagnosticsWorkspace")) == null ? void 0 : _e.addEventListener("click", (event) => this.handleToolClick(event));
        (_f = document.getElementById("toolDiagnosticsWorkspace")) == null ? void 0 : _f.addEventListener("change", (event) => this.handleToolChange(event));
      }
      handleToolClick(event) {
        const target = event.target.closest("button");
        if (!target) return;
        if (target.matches("[data-session-start]")) {
          const result = this.api.startSession("registro manual pela interface");
          this.notifyResult(result, "Grava\xE7\xE3o de diagn\xF3stico iniciada.");
        } else if (target.matches("[data-session-stop]")) {
          const result = this.api.stopSession("parada manual pela interface");
          this.notifyResult(result, "Grava\xE7\xE3o de diagn\xF3stico encerrada.");
        } else if (target.matches("[data-session-settings]")) {
          this.applySessionSettings();
        } else if (target.matches("[data-export-session]")) {
          this.api.exportSession(target.dataset.exportSession || "");
        }
      }
      handleToolChange(event) {
        if (event.target.matches("[data-log-level]")) {
          this.logLevel = event.target.value || "ALL";
          this.renderTools(this.store.get());
        }
        if (event.target.matches("[data-log-category]")) {
          this.logCategory = event.target.value || "ALL";
          this.renderTools(this.store.get());
        }
      }
      notifyResult(result, successMessage) {
        if ((result == null ? void 0 : result.ok) === false) {
          this.store.patch({ alert: { level: "warning", message: result.error || "Opera\xE7\xE3o n\xE3o conclu\xEDda." } });
        } else {
          this.store.patch({ alert: { level: "ok", message: successMessage } });
        }
      }
      applySessionSettings() {
        var _a, _b, _c, _d, _e;
        const host = document.getElementById("toolDiagnosticsWorkspace");
        if (!host) return;
        const settings = {
          telemetryEveryMs: Number(((_a = host.querySelector("[data-session-telemetry]")) == null ? void 0 : _a.value) || 500),
          maxSessionMb: Number(((_b = host.querySelector("[data-session-maxmb]")) == null ? void 0 : _b.value) || 64),
          keepSessions: Number(((_c = host.querySelector("[data-session-keep]")) == null ? void 0 : _c.value) || 30),
          autoStartOnUsb: ((_d = host.querySelector("[data-session-autostart]")) == null ? void 0 : _d.checked) === true,
          captureRawUsb: ((_e = host.querySelector("[data-session-rawusb]")) == null ? void 0 : _e.checked) === true
        };
        const result = this.api.setSessionSettings(settings);
        this.notifyResult(result, "Pol\xEDtica de logs atualizada.");
      }
      render(state) {
        if (state.route === "tools") this.renderTools(state);
        const demo = document.getElementById("toolEnvironment");
        if (demo) demo.textContent = state.demo ? "Simula\xE7\xE3o de interface \xB7 nenhuma escrita real" : "APK/WebView \xB7 ponte nativa ativa";
      }
      renderTools(state) {
        var _a, _b, _c;
        const host = document.getElementById("toolDiagnosticsWorkspace");
        if (!host) return;
        const status = state.sessionStatus || {};
        const settings = status.settings || {};
        const sessions = Array.isArray(state.sessions) ? state.sessions : [];
        const logs = Array.isArray(state.logs) ? state.logs : [];
        const appStatus = state.status || {};
        const learning = state.learning || {};
        const petrolCount = Array.isArray(learning.petrol) ? learning.petrol.length : 0;
        const cngCount = Array.isArray(learning.cng) ? learning.cng.length : 0;
        const comparisonCount = (_a = finite(learning.comparisonCount)) != null ? _a : Array.isArray(learning.comparisons) ? learning.comparisons.length : 0;
        const categories = [...new Set(logs.map((item) => String(item.category || "OUTROS").toUpperCase()))].sort();
        const filteredLogs = logs.filter((item) => {
          const level = String(item.level || "").toUpperCase();
          const category = String(item.category || "OUTROS").toUpperCase();
          return (this.logLevel === "ALL" || level === this.logLevel) && (this.logCategory === "ALL" || category === this.logCategory);
        }).slice(-24).reverse();
        const recording = status.recording === true;
        const mb = finite(status.megabytes) || 0;
        const limitMb = finite((_b = status.limitMb) != null ? _b : settings.maxSessionMb) || 0;
        const fullness = limitMb > 0 ? Math.min(100, mb / limitMb * 100) : 0;
        const serviceHealthy = appStatus.serviceRunning === true && appStatus.engineStuck !== true;
        const diagnosticSettingsOpen = ((_c = host.querySelector(".diagnostic-settings")) == null ? void 0 : _c.open) === true;
        host.innerHTML = '\n        <section class="background-health-card" data-healthy="'.concat(serviceHealthy ? "true" : "false", '">\n          <header><div><small>SEGUNDO PLANO</small><h3>').concat(appStatus.serviceRunning ? "Servi\xE7o Android ativo" : "Servi\xE7o Android n\xE3o est\xE1 ativo", "</h3></div><span>").concat(serviceHealthy ? "MONITORANDO" : "ATEN\xC7\xC3O", '</span></header>\n          <div class="background-health-grid">\n            <span>Engine <b>').concat(appStatus.engineRunning ? "ativa" : "parada", "</b></span>\n            <span>USB <b>").concat(appStatus.usbConnected ? "conectado" : "desconectado", "</b></span>\n            <span>Telemetria <b>").concat(ageLabel(appStatus.directTelemetryAgeMs), '</b></span>\n            <span>Foreground <b>connectedDevice</b></span>\n          </div>\n          <p>Ao apagar a tela, a WebView para de redesenhar, mas o ForegroundService continua respons\xE1vel pela ECU e pelo aprendizado. O aplicativo n\xE3o pede exclus\xE3o da otimiza\xE7\xE3o de bateria automaticamente.</p>\n          <small class="background-validation-note">Valida\xE7\xE3o real ainda exige teste com tela apagada e pol\xEDtica de bateria do aparelho.</small>\n        </section>\n\n        <section class="learning-portability-card">\n          <header><div><small>APRENDIZADO .OMEGAS</small><h3>O que ser\xE1 levado no arquivo</h3></div><span>').concat(petrolCount + cngCount + comparisonCount, ' evid\xEAncias indexadas</span></header>\n          <div class="learning-portability-grid">\n            <span><b>').concat(petrolCount, "</b> regi\xF5es gasolina</span>\n            <span><b>").concat(cngCount, "</b> regi\xF5es GNV</span>\n            <span><b>").concat(comparisonCount, "</b> compara\xE7\xF5es</span>\n            <span><b>").concat(escapeHtml(learning.telemetryScaleSchema || "MP48"), '</b> escala</span>\n          </div>\n          <p><b>Exportar</b> preserva a evid\xEAncia de aprendizado e o hist\xF3rico K confirmado. <b>Importar</b> valida formato e escala antes de aceitar dados.</p>\n          <small>Os bot\xF5es Exportar/Importar acima apenas abrem o seletor de arquivo; nenhuma importa\xE7\xE3o escreve na ECU.</small>\n        </section>\n\n        <section class="diagnostic-recorder-card" data-recording="').concat(recording ? "true" : "false", '">\n          <header><div><small>DIAGN\xD3STICO ESTRUTURADO</small><h3>').concat(recording ? "Gravando sess\xE3o" : "Gravador parado", "</h3></div><span>").concat(recording ? "ATIVO" : "INATIVO", '</span></header>\n          <div class="recorder-metrics">\n            <span><b>').concat(status.events || 0, "</b> eventos</span>\n            <span><b>").concat(fmt(mb, 1), " MB</b> usados</span>\n            <span><b>").concat(status.droppedEvents || 0, "</b> descartados</span>\n            <span><b>").concat(durationLabel(status.durationMs), '</b> dura\xE7\xE3o</span>\n          </div>\n          <div class="recorder-space"><i style="width:').concat(fullness.toFixed(1), '%"></i></div>\n          <div class="recorder-actions">\n            <button type="button" class="').concat(recording ? "quiet-button" : "primary", '" data-session-start ').concat(recording ? "disabled" : "", '>Iniciar sess\xE3o</button>\n            <button type="button" class="').concat(recording ? "secondary" : "quiet-button", '" data-session-stop ').concat(recording ? "" : "disabled", '>Encerrar</button>\n          </div>\n        </section>\n\n        <details class="diagnostic-settings">\n          <summary>Reten\xE7\xE3o e tamanho dos logs</summary>\n          <div class="diagnostic-settings-grid">\n            <label><span>Telemetria salva</span><select data-session-telemetry>\n              ').concat([200, 500, 1e3, 2e3, 5e3].map((value) => '<option value="'.concat(value, '" ').concat(Number(settings.telemetryEveryMs) === value ? "selected" : "", ">").concat(value < 1e3 ? "".concat(value, " ms") : "".concat(value / 1e3, " s"), "</option>")).join(""), '\n            </select></label>\n            <label><span>Limite por sess\xE3o</span><input data-session-maxmb type="number" min="4" max="1024" step="4" value="').concat(Number(settings.maxSessionMb || status.limitMb || 64), '"><small>MB</small></label>\n            <label><span>Manter sess\xF5es \xFAteis</span><input data-session-keep type="number" min="20" max="100" step="1" value="').concat(Number(settings.keepSessions || 30), '"></label>\n            <label class="check-setting"><input data-session-autostart type="checkbox" ').concat(settings.autoStartOnUsb !== false ? "checked" : "", '><span>Iniciar ao conectar MP48</span></label>\n            <label class="check-setting"><input data-session-rawusb type="checkbox" ').concat(settings.captureRawUsb === true ? "checked" : "", '><span>Capturar USB bruto</span></label>\n          </div>\n          <p>Sess\xF5es \xFAteis nunca devem ser expulsas por reconex\xF5es curtas. O n\xFAcleo classifica PROBE, VALID e PROTECTED.</p>\n          <button type="button" class="secondary wide" data-session-settings>Aplicar pol\xEDtica de logs</button>\n        </details>\n\n        <section class="recorded-sessions">\n          <header><div><small>SESS\xD5ES</small><h3>').concat(sessions.length, " armazenada").concat(sessions.length === 1 ? "" : "s", '</h3></div></header>\n          <div class="recorded-session-list">\n            ').concat(sessions.length ? sessions.slice(0, 20).map((item) => "\n              <article>\n                <div><b>".concat(escapeHtml(item.reason || "Sess\xE3o"), "</b><span>").concat(durationLabel(item.durationMs), " \xB7 ").concat(bytesLabel(item.bytes)).concat(item.active ? " \xB7 ativa" : "").concat(item.relevance ? " \xB7 ".concat(escapeHtml(item.relevance)) : "", '</span></div>\n                <button type="button" class="quiet-button" data-export-session="').concat(escapeHtml(item.id), '">Exportar ZIP</button>\n              </article>')).join("") : '<p class="empty-copy">Nenhuma sess\xE3o gravada.</p>', '\n          </div>\n        </section>\n\n        <section class="live-log-console">\n          <header><div><small>LOG DE SISTEMA</small><h3>\xDAltimos eventos</h3></div><span>').concat(logs.length, '</span></header>\n          <div class="log-filters">\n            <select data-log-level>\n              ').concat(["ALL", "ERROR", "WARN", "INFO"].map((value) => '<option value="'.concat(value, '" ').concat(this.logLevel === value ? "selected" : "", ">").concat(value === "ALL" ? "Todos n\xEDveis" : value, "</option>")).join(""), '\n            </select>\n            <select data-log-category>\n              <option value="ALL">Todas categorias</option>\n              ').concat(categories.map((value) => '<option value="'.concat(escapeHtml(value), '" ').concat(this.logCategory === value ? "selected" : "", ">").concat(escapeHtml(value), "</option>")).join(""), '\n            </select>\n          </div>\n          <div class="log-lines">\n            ').concat(filteredLogs.length ? filteredLogs.map((item) => '<div data-level="'.concat(escapeHtml(String(item.level || "INFO").toLowerCase()), '"><time>').concat(escapeHtml(item.time || ""), "</time><b>").concat(escapeHtml(item.category || "LOG"), "</b><span>").concat(escapeHtml(item.message || ""), "</span></div>")).join("") : '<p class="empty-copy">Nenhum evento neste filtro.</p>', "\n          </div>\n        </section>\n      ");
        const diagnosticSettings = host.querySelector(".diagnostic-settings");
        if (diagnosticSettings) diagnosticSettings.open = diagnosticSettingsOpen;
      }
    }
    ns.Drawers = Drawers;
  })(typeof window !== "undefined" ? window : globalThis);
})();
