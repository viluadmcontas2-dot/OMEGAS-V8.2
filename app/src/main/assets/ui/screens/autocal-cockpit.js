(() => {
  (function(root) {
    "use strict";
    const ns = root.OmegasUi = root.OmegasUi || {};
    function finite(value) {
      const number = Number(value);
      return Number.isFinite(number) ? number : null;
    }
    function escapeHtml(value) {
      return String(value != null ? value : "").replace(/[&<>"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[char]);
    }
    function field(snapshot, key) {
      const fields = Array.isArray(snapshot == null ? void 0 : snapshot.fields) ? snapshot.fields : [];
      return fields.find((item) => String((item == null ? void 0 : item.key) || "") === key && String((item == null ? void 0 : item.status) || "") === "VALID") || null;
    }
    function vector(snapshot, key) {
      const item = field(snapshot, key);
      return Array.isArray(item == null ? void 0 : item.rawValues) ? item.rawValues.map((value) => {
        var _a;
        return (_a = finite(value)) != null ? _a : 0;
      }) : [];
    }
    function actionLabel(action) {
      return {
        ENABLE_AUTO_CAL: "Ativar coleta da ECU",
        DISABLE_AUTO_CAL: "Pausar coleta da ECU",
        RESET_PETROL: "Apagar aprendizado de gasolina",
        RESET_GAS: "Apagar aprendizado de GNV",
        RESET_ALL: "Apagar tudo e come\xE7ar de novo"
      }[action] || action;
    }
    class AutoCalCockpit {
      constructor(app) {
        this.app = app;
        this.store = app.store;
        this.scheduler = app.scheduler;
        this.api = ns.AutoCalApi;
        this.active = false;
        this.prepared = null;
        this.state = {};
        this.snapshot = {};
        this.actionState = {};
        this.inject();
        this.bind();
        this.unsubscribeContext = this.scheduler.addHook("context", () => {
          if (this.store.get().route === "curve" && this.active) this.refresh();
        });
      }
      inject() {
        if (!document.querySelector("link[data-autocal-cockpit-style]")) {
          const link = document.createElement("link");
          link.rel = "stylesheet";
          link.href = "styles-autocal-cockpit.css";
          link.dataset.autocalCockpitStyle = "true";
          document.head.appendChild(link);
        }
        const switcher = document.getElementById("curveViewSwitch");
        if (switcher && !switcher.querySelector('[data-curve-view="autocal"]')) {
          const button = document.createElement("button");
          button.type = "button";
          button.dataset.curveView = "autocal";
          button.textContent = "AutoCal";
          switcher.appendChild(button);
          this.button = button;
        } else {
          this.button = (switcher == null ? void 0 : switcher.querySelector('[data-curve-view="autocal"]')) || null;
        }
        const stack = document.querySelector('[data-screen="curve"] .curve-view-stack');
        if (stack && !stack.querySelector('[data-curve-panel="autocal"]')) {
          const panel = document.createElement("div");
          panel.className = "curve-view autocal-cockpit-view";
          panel.dataset.curvePanel = "autocal";
          panel.innerHTML = '\n          <section class="autocal-cockpit" aria-label="Auto-Cal da ECU">\n            <header class="autocal-head">\n              <div><small>APRENDIZADO NATIVO</small><h3>Auto-Cal da ECU</h3><p>A ECU coleta condi\xE7\xF5es do motor e tenta ajustar o pr\xF3prio AutoMatch. O OMEGAS mostra o progresso e mant\xE9m qualquer comando sob sua confirma\xE7\xE3o.</p></div>\n              <div class="autocal-head-actions"><span id="autocalNativeState" class="source-status">Aguardando ECU</span><button type="button" data-autocal-read class="secondary">Atualizar leitura</button></div>\n            </header>\n            <section class="autocal-next-step" aria-live="polite"><div><small>ORIENTA\xC7\xC3O</small><h4>O que fazer agora</h4></div><p id="autocalGuidance">Atualize a leitura para saber se a coleta da ECU est\xE1 ativa.</p></section>\n            <div class="autocal-live-strip">\n              <div><small>Coleta da ECU</small><b id="autocalEnable">\u2014</b></div>\n              <div><small>Progresso do AutoMatch</small><b id="autocalMatchCount">\u2014</b></div>\n              <div><small>Limite configurado</small><b id="autocalMaxMatch">\u2014</b></div>\n              <div><small>Bandas prontas</small><b id="autocalMatureCount">0</b></div>\n            </div>\n            <div class="autocal-layout">\n              <section class="autocal-bands-card">\n                <div class="autocal-section-head"><div><small>18 FAIXAS DE APRENDIZADO</small><h4>Progresso por faixa</h4></div><span>somente leitura</span></div>\n                <div id="autocalBands" class="autocal-bands"></div>\n                <p class="autocal-note">Cada faixa representa uma condi\xE7\xE3o interna da ECU. Ela s\xF3 vira refer\xEAncia para o mapa quando existe uma posi\xE7\xE3o f\xEDsica confi\xE1vel.</p>\n              </section>\n              <aside class="autocal-side">\n                <section class="autocal-events-card"><div class="autocal-section-head"><div><small>RESULTADOS RECENTES</small><h4>Onde a ECU conseguiu aprender</h4></div></div><div id="autocalEvents" class="autocal-events"></div></section>\n                <section class="autocal-actions-card">\n                  <div class="autocal-section-head"><div><small>CONTROLE DA COLETA</small><h4>Escolha uma a\xE7\xE3o</h4></div><span>sempre confirmada</span></div>\n                  <p class="autocal-action-help">Ativar ou pausar n\xE3o aplica um mapa sugerido pelo OMEGAS. Apenas controla a aquisi\xE7\xE3o nativa da ECU.</p>\n                  <div class="autocal-actions autocal-primary-actions"><button type="button" data-autocal-action="ENABLE_AUTO_CAL">Ativar coleta</button><button type="button" data-autocal-action="DISABLE_AUTO_CAL">Pausar coleta</button></div>\n                  <details class="autocal-advanced"><summary>Reiniciar aprendizado (avan\xE7ado)</summary><p>Estas a\xE7\xF5es apagam dados nativos j\xE1 coletados. Use apenas quando voc\xEA decidiu come\xE7ar uma nova aquisi\xE7\xE3o.</p><div class="autocal-actions"><button type="button" data-autocal-action="RESET_PETROL">Apagar gasolina</button><button type="button" data-autocal-action="RESET_GAS">Apagar GNV</button><button type="button" data-autocal-action="RESET_ALL" class="critical">Apagar tudo</button></div></details>\n                  <div id="autocalActionStatus" class="autocal-action-status">Nenhuma a\xE7\xE3o preparada.</div>\n                  <details class="autocal-advanced autocal-diagnostic"><summary>Diagn\xF3stico t\xE9cnico</summary><p>O estado bruto, os comandos e a sess\xE3o aparecem na revis\xE3o antes de qualquer envio.</p></details>\n                </section>\n              </aside>\n            </div>\n            <div id="autocalReview" class="autocal-review" hidden></div>\n          </section>';
          stack.appendChild(panel);
          this.panel = panel;
        } else {
          this.panel = (stack == null ? void 0 : stack.querySelector('[data-curve-panel="autocal"]')) || null;
        }
      }
      bind() {
        var _a, _b, _c, _d;
        const switcher = document.getElementById("curveViewSwitch");
        switcher == null ? void 0 : switcher.addEventListener("click", (event) => {
          var _a2, _b2;
          const target = (_b2 = (_a2 = event.target).closest) == null ? void 0 : _b2.call(_a2, '[data-curve-view="autocal"]');
          if (!target) return;
          this.button = target;
          this.open();
        });
        document.querySelectorAll('#curveViewSwitch [data-curve-view="learning"], #curveViewSwitch [data-curve-view="editor"]').forEach((button) => {
          button.addEventListener("click", () => {
            this.active = false;
          });
        });
        (_b = (_a = this.panel) == null ? void 0 : _a.querySelector("[data-autocal-read]")) == null ? void 0 : _b.addEventListener("click", () => this.requestRead());
        (_c = this.panel) == null ? void 0 : _c.querySelectorAll("[data-autocal-action]").forEach((button) => {
          button.addEventListener("click", () => this.prepare(button.dataset.autocalAction));
        });
        (_d = this.panel) == null ? void 0 : _d.addEventListener("click", (event) => {
          if (event.target.closest("[data-autocal-cancel]")) this.cancelPrepared();
          if (event.target.closest("[data-autocal-confirm]")) this.confirmPrepared();
        });
      }
      open() {
        this.active = true;
        document.querySelectorAll("#curveViewSwitch [data-curve-view]").forEach((button) => button.classList.toggle("active", button === this.button));
        document.querySelectorAll('[data-screen="curve"] [data-curve-panel]').forEach((panel) => panel.classList.toggle("active", panel === this.panel));
        this.refresh();
      }
      refresh() {
        var _a, _b;
        if (!((_b = (_a = this.api) == null ? void 0 : _a.available) == null ? void 0 : _b.call(_a))) {
          this.renderUnavailable();
          return;
        }
        this.state = this.api.status() || {};
        this.snapshot = this.api.snapshot() || {};
        this.actionState = this.api.actionStatus() || {};
        this.render();
      }
      requestRead() {
        var _a, _b;
        if (!((_b = (_a = this.api) == null ? void 0 : _a.available) == null ? void 0 : _b.call(_a))) return;
        const result = this.api.startRead();
        if ((result == null ? void 0 : result.ok) === false) {
          this.store.patch({ alert: { level: "warning", message: result.error || "Leitura AutoCal indispon\xEDvel." } });
        } else {
          this.store.patch({ alert: { level: "ok", message: "Snapshot AutoCal solicitado. A telemetria continua sob a mesma engine MP48." } });
        }
        this.refresh();
      }
      prepare(action) {
        var _a, _b;
        if (!action || !((_b = (_a = this.api) == null ? void 0 : _a.available) == null ? void 0 : _b.call(_a))) return;
        const result = this.api.prepare(action);
        if (!(result == null ? void 0 : result.ok) || !(result == null ? void 0 : result.prepared)) {
          this.store.patch({ alert: { level: "warning", message: (result == null ? void 0 : result.error) || "A a\xE7\xE3o AutoCal n\xE3o p\xF4de ser preparada." } });
          return;
        }
        this.prepared = result;
        this.renderReview();
      }
      cancelPrepared() {
        var _a, _b;
        (_b = (_a = this.api) == null ? void 0 : _a.cancelPreparation) == null ? void 0 : _b.call(_a);
        this.prepared = null;
        const review = document.getElementById("autocalReview");
        if (review) {
          review.hidden = true;
          review.innerHTML = "";
        }
        this.refresh();
      }
      confirmPrepared() {
        const prepared = this.prepared;
        if (!(prepared == null ? void 0 : prepared.preparationId)) return;
        const result = this.api.execute(prepared.preparationId);
        if ((result == null ? void 0 : result.ok) === false) {
          this.store.patch({ alert: { level: "warning", message: result.error || "A confirma\xE7\xE3o Android n\xE3o p\xF4de ser aberta." } });
          return;
        }
        this.prepared = null;
        const review = document.getElementById("autocalReview");
        if (review) {
          review.hidden = true;
          review.innerHTML = "";
        }
        this.store.patch({ alert: { level: "warning", message: "Confirma\xE7\xE3o Android aberta. O comando ainda n\xE3o foi enviado at\xE9 voc\xEA confirmar l\xE1." } });
        this.refresh();
      }
      render() {
        var _a, _b, _c, _d;
        const state = this.state || {};
        const snapshot = this.snapshot || {};
        const nativeStatus = snapshot.nativeStatus || ((_a = state.latestSnapshot) == null ? void 0 : _a.nativeStatus) || {};
        const enabled = finite((_b = snapshot.autoCalEnabled) != null ? _b : state.autoCalEnabled);
        const autoMatchCount = finite((_c = nativeStatus.autoMatchCount) != null ? _c : state.autoMatchCount);
        const maxAutoMatch = finite((_d = snapshot.maxAutomatch) != null ? _d : state.maxAutomatch);
        const events = Array.isArray(snapshot.nativeMaturityEvents) ? snapshot.nativeMaturityEvents : [];
        this.text("autocalNativeState", state.state || (snapshot.available ? "READY" : "AGUARDANDO"));
        this.text("autocalState", state.state || "\u2014");
        this.text("autocalEnable", enabled === 1 ? "ATIVA" : enabled === 0 ? "PAUSADA" : "\u2014");
        this.text("autocalMatchCount", autoMatchCount != null ? autoMatchCount : "\u2014");
        this.text("autocalMaxMatch", maxAutoMatch != null ? maxAutoMatch : "\u2014");
        this.text("autocalMatureCount", events.length);
        this.renderGuidance(enabled, events, snapshot.available === true);
        this.renderBands(snapshot, events);
        this.renderEvents(events);
        this.renderActionState();
      }
      renderGuidance(enabled, events, available) {
        const message = !available ? "Conecte a ECU e toque em Atualizar leitura." : enabled === 0 ? "A coleta est\xE1 pausada. Toque em Ativar coleta quando quiser continuar." : enabled === 1 && events.length === 0 ? "A coleta est\xE1 ativa. Dirija normalmente; o progresso aparece nas faixas abaixo." : enabled === 1 ? "A coleta est\xE1 ativa e j\xE1 produziu resultados. Revise as faixas prontas abaixo." : "Toque em Atualizar leitura para confirmar o estado da coleta.";
        this.text("autocalGuidance", message);
      }
      renderBands(snapshot, events) {
        const host = document.getElementById("autocalBands");
        if (!host) return;
        const counters = vector(snapshot, "NUM_BUF_UPD_GAS");
        const matured = new Map(events.map((event) => [Number(event.bandIndex), event]));
        if (!counters.length) {
          host.innerHTML = '<div class="detail-empty"><b>Sem contador v\xE1lido</b><span>A ECU ainda n\xE3o publicou NUM_BUF_UPD_GAS neste snapshot.</span></div>';
          return;
        }
        host.innerHTML = counters.slice(0, 18).map((count, index) => {
          const event = matured.get(index);
          const threshold = finite(event == null ? void 0 : event.threshold);
          const ratio = threshold && threshold > 0 ? Math.min(100, count / threshold * 100) : 0;
          const correlated = String((event == null ? void 0 : event.correlationState) || "") === "CORRELATED";
          const state = event ? correlated ? "anchored" : "mature" : count > 0 ? "collecting" : "empty";
          return '<div class="autocal-band" data-state="'.concat(state, '"><header><span>B').concat(String(index + 1).padStart(2, "0"), "</span><b>").concat(Math.round(count), '</b></header><i style="--progress:').concat(ratio, '%"></i><small>').concat(event ? correlated ? "\xE2ncora correlacionada" : "madura \xB7 sem posi\xE7\xE3o confi\xE1vel" : count > 0 ? "coletando" : "sem dados", "</small></div>");
        }).join("");
      }
      renderEvents(events) {
        const host = document.getElementById("autocalEvents");
        if (!host) return;
        if (!events.length) {
          host.innerHTML = '<p class="empty-copy">Nenhuma banda rec\xE9m-amadurecida neste snapshot.</p>';
          return;
        }
        host.innerHTML = events.slice(-6).reverse().map((event) => {
          var _a, _b;
          const correlated = String(event.correlationState || "") === "CORRELATED";
          const rpm = finite(event.rpm);
          const confidence = Math.round((finite(event.correlationConfidence) || 0) * 100);
          return '<article data-state="'.concat(correlated ? "correlated" : "raw", '"><div><b>B').concat(Number(event.bandIndex) + 1, "</b><span>").concat(escapeHtml(event.zone || "zona"), "</span></div><p>").concat(correlated ? "".concat(rpm === null ? "RPM \u2014" : "".concat(Math.round(rpm).toLocaleString("pt-BR"), " RPM"), " \xB7 precis\xE3o da correla\xE7\xE3o ").concat(confidence, "%") : escapeHtml(event.correlationReason || "NO_RELIABLE_CORRELATION"), "</p><small>contador ").concat((_a = finite(event.counter)) != null ? _a : "\u2014", " \xB7 limiar ").concat((_b = finite(event.threshold)) != null ? _b : "\u2014", "</small></article>");
        }).join("");
      }
      renderActionState() {
        const host = document.getElementById("autocalActionStatus");
        if (!host) return;
        const state = this.actionState || {};
        const name = String(state.state || "IDLE");
        const progress = finite(state.progress);
        host.innerHTML = "<b>".concat(escapeHtml(name), "</b><span>").concat(escapeHtml(state.message || "Nenhuma a\xE7\xE3o preparada."), "</span>").concat(progress === null ? "" : '<i style="--progress:'.concat(Math.max(0, Math.min(100, progress)), '%"></i>'));
      }
      renderReview() {
        const review = document.getElementById("autocalReview");
        const prepared = this.prepared;
        if (!review || !prepared) return;
        review.hidden = false;
        review.innerHTML = '<div class="autocal-review-card"><header><div><small>REVISE ANTES DE CONTINUAR</small><h3>'.concat(escapeHtml(prepared.label || actionLabel(prepared.action)), '</h3></div><button type="button" data-autocal-cancel class="icon-close" aria-label="Fechar revis\xE3o">\xD7</button></header><p>').concat(escapeHtml(prepared.description || ""), '</p><div class="write-contract"><b>Nada foi enviado.</b><span>O pr\xF3ximo bot\xE3o abre a confirma\xE7\xE3o final do Android. O comando s\xF3 sai depois da sua confirma\xE7\xE3o positiva.</span></div><details class="autocal-advanced"><summary>Diagn\xF3stico t\xE9cnico</summary><dl><div><dt>A\xE7\xE3o</dt><dd>').concat(escapeHtml(prepared.action), "</dd></div><div><dt>Comando</dt><dd>").concat(escapeHtml(prepared.commandHex || "\u2014"), "</dd></div><div><dt>Sess\xE3o</dt><dd>").concat(escapeHtml(prepared.sessionId || "\u2014"), "</dd></div><div><dt>ECU pode alterar MUL_ACT</dt><dd>").concat(prepared.mayChangeMulAct ? "sim" : "n\xE3o", '</dd></div></dl></details><div class="operation-actions"><button type="button" data-autocal-cancel class="secondary">Cancelar</button><button type="button" data-autocal-confirm class="danger-primary">Abrir confirma\xE7\xE3o final</button></div></div>');
      }
      renderUnavailable() {
        this.text("autocalNativeState", "BRIDGE INDISPON\xCDVEL");
        const host = document.getElementById("autocalBands");
        if (host) host.innerHTML = '<div class="detail-empty"><b>AutoCal indispon\xEDvel</b><span>O bridge nativo ainda n\xE3o foi anexado \xE0 WebView.</span></div>';
      }
      text(id, value) {
        const node = document.getElementById(id);
        if (node && node.textContent !== String(value != null ? value : "\u2014")) node.textContent = String(value != null ? value : "\u2014");
      }
    }
    function boot() {
      const app = root.OmegasApp;
      if (!(app == null ? void 0 : app.store) || !(app == null ? void 0 : app.scheduler) || !ns.AutoCalApi) {
        root.setTimeout(boot, 25);
        return;
      }
      if (app.autoCalCockpit) return;
      app.autoCalCockpit = new AutoCalCockpit(app);
    }
    ns.AutoCalCockpit = AutoCalCockpit;
    boot();
  })(typeof window !== "undefined" ? window : globalThis);
})();
