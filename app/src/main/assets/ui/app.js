(() => {
  (function(root) {
    "use strict";
    const ui = root.OmegasUi || {};
    if (!ui.Store || !ui.NativeApi || !ui.Router || !ui.Scheduler) {
      console.error("[OMEGAS] Funda\xE7\xE3o da UI n\xE3o carregada.");
      return;
    }
    const refinementStyle = document.createElement("link");
    refinementStyle.rel = "stylesheet";
    refinementStyle.href = "styles-refine.css";
    document.head.appendChild(refinementStyle);
    const api = new ui.NativeApi();
    const store = new ui.Store(ui.createInitialState());
    const router = new ui.Router(store);
    const instances = {};
    const utilities = ui.Drawers ? new ui.Drawers(store, router, api) : null;
    const selectedSuggestionIds = /* @__PURE__ */ new Set();
    const routeMeta = {
      dashboard: ["AGORA", "Agora"],
      learning: ["APRENDER", "Aprender"],
      map: ["AJUSTE LOCAL", "Ajuste local"],
      curve: ["AJUSTE GLOBAL", "Ajuste global"],
      obd: ["OBSERVAR", "OBD"],
      suggestions: ["DECIDIR", "Sugest\xF5es"],
      tools: ["SISTEMA", "Ferramentas"]
    };
    let renderedRoute = null;
    let previousGlobalSignature = "";
    let previousTelemetrySignature = "";
    let previousStatusSignature = "";
    let previousAlert = null;
    let previousLearningLayer = null;
    let toastTimer = null;
    let routeButtons = [];
    let screenNodes = [];
    let scienceRevision = 0;
    function byId(id) {
      return document.getElementById(id);
    }
    function setText(id, value) {
      const node = byId(id);
      if (!node) return;
      const next = value == null ? "\u2014" : String(value);
      if (node.textContent !== next) node.textContent = next;
    }
    function finite(value) {
      return Number.isFinite(Number(value)) ? Number(value) : null;
    }
    function rounded(value, digits) {
      const number = finite(value);
      if (number === null) return "\u2014";
      const factor = 10 ** digits;
      return String(Math.round(number * factor) / factor);
    }
    function escapeHtml(value) {
      return String(value != null ? value : "").replace(/[&<>\"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[char]);
    }
    function fuelLabel(raw) {
      const value = String(raw || "\u2014").toUpperCase();
      if (value.includes("PETROL") || value.includes("GASOLINA")) return "GASOLINA";
      if (value.includes("CNG") || value.includes("GNV") || value.includes("GAS")) return "GNV";
      if (value.includes("CUTOFF")) return "CUTOFF";
      return value || "\u2014";
    }
    function liveFrom(state) {
      const telemetry = state.telemetry || {};
      return telemetry.live || telemetry.data || telemetry;
    }
    function curveEvidenceVisible() {
      var _a;
      return ((_a = document.querySelector('[data-screen="curve"] .evidence-disclosure')) == null ? void 0 : _a.open) === true;
    }
    function afterPaint(task) {
      if (typeof root.requestAnimationFrame === "function") {
        root.requestAnimationFrame(() => root.setTimeout(task, 0));
      } else {
        root.setTimeout(task, 0);
      }
    }
    function learningDecisionFromTelemetry(telemetry) {
      var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k, _l, _m, _n, _o, _p, _q, _r, _s, _t;
      const source = telemetry || {};
      const live = source.live || source.data || source;
      const sample = live.sample && typeof live.sample === "object" ? live.sample : {};
      return {
        ok: source.ok !== false,
        state: sample.state || live.sample_state || "OBSERVING_ENGINE",
        reason: sample.reason || live.sample_reason || "Observando o motor",
        reason_code: sample.reason_code || sample.reasonCode || live.sample_state || "OBSERVING_ENGINE",
        frame_count: Number((_b = (_a = sample.frame_count) != null ? _a : live.sample_frame_count) != null ? _b : 0),
        minimum_frames: Number((_d = (_c = sample.minimum_frames) != null ? _c : live.sample_minimum_frames) != null ? _d : 0),
        desired_frames: Number((_f = (_e = sample.desired_frames) != null ? _e : live.sample_desired_frames) != null ? _f : 0),
        duration_ms: Number((_h = (_g = sample.duration_ms) != null ? _g : live.sample_duration_ms) != null ? _h : 0),
        median_interval_ms: Number((_i = sample.median_interval_ms) != null ? _i : 0),
        gap_ms: Number((_j = sample.gap_ms) != null ? _j : 0),
        learning_eligible: sample.learning_eligible === true,
        fuel_confirmed: (_l = (_k = sample.fuel_confirmed) != null ? _k : live.fuel) != null ? _l : null,
        window_age_ms: Number((_n = (_m = sample.window_age_ms) != null ? _m : sample.duration_ms) != null ? _n : 0),
        window_budget_ms: Number((_o = sample.window_budget_ms) != null ? _o : 0),
        frames_evicted: Number((_p = sample.frames_evicted) != null ? _p : 0),
        cell_key: sample.cell_key || "",
        cell_row: Number((_q = sample.cell_row) != null ? _q : -1),
        cell_column: Number((_r = sample.cell_column) != null ? _r : -1),
        quality: Number((_t = (_s = sample.quality) != null ? _s : live.learning_quality) != null ? _t : 0),
        plausibility_reasons: Array.isArray(sample.plausibility_reasons) ? sample.plausibility_reasons : [],
        live
      };
    }
    function ensureScreen(route) {
      if (instances[route]) return instances[route];
      if (route === "dashboard" && ui.DashboardScreen) instances.dashboard = new ui.DashboardScreen(store, api);
      if (route === "learning" && ui.LearningScreen) instances.learning = new ui.LearningScreen(store, router, api);
      if (route === "map" && ui.MapScreen) instances.map = new ui.MapScreen(store, api, router);
      if (route === "curve" && ui.CurveScreen) instances.curve = new ui.CurveScreen(store, api);
      if (route === "obd" && ui.ObdScreen) instances.obd = new ui.ObdScreen(store, api);
      return instances[route] || null;
    }
    function renderShell(state) {
      var _a, _b;
      if (state.route !== renderedRoute) {
        renderedRoute = state.route;
        if (document.body) document.body.dataset.omegasRoute = state.route;
        const meta = routeMeta[state.route] || routeMeta.dashboard;
        setText("routeEyebrow", meta[0]);
        setText("routeTitle", meta[1]);
        routeButtons.forEach((button) => {
          const active = button.dataset.route === state.route;
          button.classList.toggle("active", active);
          button.setAttribute("aria-current", active ? "page" : "false");
        });
        screenNodes.forEach((screen) => {
          const active = screen.dataset.screen === state.route;
          screen.classList.toggle("active", active);
          screen.setAttribute("aria-hidden", active ? "false" : "true");
        });
      }
      const status = state.status || {};
      const obdStatus = state.obd || {};
      const obdOnline = obdStatus.connected === true || ["CONNECTED", "CONECTADO", "REMOTO AO VIVO"].includes(String(obdStatus.state || obdStatus.status || "").toUpperCase());
      const fuel = fuelLabel(status.fuelState || liveFrom(state).fuel || liveFrom(state).state);
      const globalSignature = "".concat(status.usbConnected === true ? 1 : 0, ":").concat(obdOnline ? 1 : 0, ":").concat(fuel);
      if (globalSignature !== previousGlobalSignature) {
        previousGlobalSignature = globalSignature;
        const ecu = byId("globalEcu");
        if (ecu) {
          const online = status.usbConnected === true;
          ecu.dataset.online = online ? "true" : "false";
          setText("globalEcu", online ? "ECU online" : "ECU offline");
        }
        const obdNode = byId("globalObd");
        if (obdNode) {
          obdNode.dataset.online = obdOnline ? "true" : "false";
          setText("globalObd", obdOnline ? "OBD online" : "OBD offline");
        }
        const fuelNode = byId("globalFuel");
        if (fuelNode) {
          fuelNode.dataset.fuel = fuel;
          setText("globalFuel", fuel);
        }
      }
      const pending = Number(((_a = state.calibrationState) == null ? void 0 : _a.suggestionPending) || 0);
      setText("suggestionCount", pending);
      if (state.learningLayer !== previousLearningLayer && state.route === "learning") {
        previousLearningLayer = state.learningLayer;
        (_b = ensureScreen("learning")) == null ? void 0 : _b.render(state);
      }
      if (state.alert && state.alert !== previousAlert) {
        previousAlert = state.alert;
        showAlert(state.alert);
      }
    }
    function showAlert(alert) {
      const toast = byId("alertToast");
      if (!toast || !alert) return;
      toast.dataset.level = alert.level || "warning";
      const label = toast.querySelector("b");
      const message = alert.message || String(alert);
      if (label && label.textContent !== message) label.textContent = message;
      toast.classList.add("show");
      if (toastTimer) root.clearTimeout(toastTimer);
      toastTimer = root.setTimeout(() => toast.classList.remove("show"), 3600);
    }
    function telemetryVisualSignature(telemetry, route) {
      var _a, _b, _c, _d, _e, _f, _g;
      const source = telemetry || {};
      const live = source.live || source.data || source;
      if (route === "dashboard") {
        return [
          source.valid === false ? 0 : 1,
          rounded(live.rpm, 0),
          rounded((_a = live.petrol_ms) != null ? _a : live.petrolMs, 2),
          rounded((_b = live.gas_ms_diagnostic) != null ? _b : live.gasMs, 2),
          rounded((_d = (_c = live.load_bar) != null ? _c : live.map_bar) != null ? _d : live.mapBar, 2),
          String(live.fuel || live.state || "")
        ].join("|");
      }
      const interpolation = source.interpolation || {};
      const cell = interpolation.cell || {};
      return [
        source.valid === false ? 0 : 1,
        Math.round((finite((_e = interpolation.rpm) != null ? _e : live.rpm) || 0) / 25) * 25,
        Math.round((finite((_g = (_f = interpolation.petrolMs) != null ? _f : live.petrol_ms) != null ? _g : live.petrolMs) || 0) * 20) / 20,
        Number.isFinite(Number(cell.row)) ? Number(cell.row) : "-",
        Number.isFinite(Number(cell.column)) ? Number(cell.column) : "-"
      ].join("|");
    }
    function renderLightLiveContext(state, route) {
      var _a, _b, _c, _d, _e, _f;
      const interpolation = ((_a = state.telemetry) == null ? void 0 : _a.interpolation) || {};
      const cell = interpolation.cell || {};
      const rpm = finite((_b = interpolation.rpm) != null ? _b : liveFrom(state).rpm);
      const petrolMs = finite((_d = (_c = interpolation.petrolMs) != null ? _c : liveFrom(state).petrol_ms) != null ? _d : liveFrom(state).petrolMs);
      const row = Number.isFinite(Number(cell.row)) ? Number(cell.row) : null;
      const column = Number.isFinite(Number(cell.column)) ? Number(cell.column) : null;
      const position = row !== null && column !== null ? " \xB7 c\xE9lula ".concat(row + 1, "\xD7").concat(column + 1) : "";
      const label = rpm !== null && petrolMs !== null ? "".concat(Math.round(rpm).toLocaleString("pt-BR"), " RPM \xB7 ").concat(petrolMs.toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }), " ms").concat(position) : "Aguardando condi\xE7\xE3o v\xE1lida";
      if (route === "learning") setText("learningLiveLabel", label);
      if (route === "map") (_f = (_e = ensureScreen("map")) == null ? void 0 : _e.renderLiveContext) == null ? void 0 : _f.call(_e, { rpm, petrolMs, row, column, label });
    }
    function refreshFast() {
      var _a, _b, _c;
      const route = store.get().route;
      if (route === "dashboard" || route === "learning" || route === "map") {
        const envelope = api.presentSnapshot() || {};
        const telemetry = envelope.data || {};
        const signature = "".concat(route, ":").concat(telemetryVisualSignature(telemetry, route));
        if (envelope.ok !== false && signature !== previousTelemetrySignature) {
          previousTelemetrySignature = signature;
          store.patch({ telemetry, presentRevision: Number(envelope.revision || 0) });
          const state2 = store.get();
          if (route === "dashboard") (_a = ensureScreen("dashboard")) == null ? void 0 : _a.render(state2);
          if (route === "learning" || route === "map") renderLightLiveContext(state2, route);
        }
      }
      const state = store.get();
      if (route === "map" && instances.map && (((_b = state.map) == null ? void 0 : _b.state) === "writing" || ((_c = state.map) == null ? void 0 : _c.state) === "reading")) instances.map.poll();
      if (route === "curve" && instances.curve && (instances.curve.reading || instances.curve.writing)) instances.curve.poll();
    }
    function refreshStatus() {
      var _a, _b;
      const status = api.status() || {};
      const obdState = api.obd() || {};
      const route = store.get().route;
      const obdDevices = route === "obd" ? api.obdDevices() || {} : null;
      const signature = JSON.stringify({ status, obdState, obdDevices, demo: api.isDemo() });
      if (signature !== previousStatusSignature) {
        previousStatusSignature = signature;
        const patch = { status, obd: obdState, demo: api.isDemo() };
        if (obdDevices) patch.obdDevices = obdDevices;
        store.patch(patch);
      }
      const state = store.get();
      if (route === "dashboard") (_a = ensureScreen("dashboard")) == null ? void 0 : _a.render(state);
      if (route === "obd") (_b = ensureScreen("obd")) == null ? void 0 : _b.render(state);
    }
    function toolsEditing() {
      const host = byId("toolDiagnosticsWorkspace");
      return !!host && !!document.activeElement && host.contains(document.activeElement) && ["INPUT", "SELECT", "BUTTON"].includes(document.activeElement.tagName);
    }
    function refreshContext() {
      var _a, _b, _c, _d;
      const state = store.get();
      const route = state.route;
      const curve = route === "curve" ? ensureScreen("curve") : null;
      const curveNeedsLearning = route === "curve" && (curveEvidenceVisible() || ((_a = curve == null ? void 0 : curve.needsLearning) == null ? void 0 : _a.call(curve)));
      const patch = {};
      const needsScience = route === "learning" || route === "suggestions" || route === "map" || route === "tools" || curveNeedsLearning || route === "curve";
      if (needsScience) {
        const science = api.scienceSnapshotSince(scienceRevision) || {};
        const nextRevision = Number(science.revision || scienceRevision || 0);
        patch.scienceRefreshing = science.refreshing === true;
        if (science.changed === true && science.data && typeof science.data === "object") {
          scienceRevision = nextRevision;
          const data = science.data;
          if (data.learning) patch.learning = data.learning;
          if (data.calibrationState) patch.calibrationState = data.calibrationState;
          patch.scienceRevision = scienceRevision;
        }
      }
      if (route === "learning") {
        patch.learningStatus = api.learningStatus() || {};
        patch.learningDecision = learningDecisionFromTelemetry(state.telemetry);
      }
      if (route === "obd") patch.obdDevices = api.obdDevices() || {};
      if (route === "tools") {
        patch.sessionStatus = api.sessionStatus() || {};
        patch.sessions = api.sessions() || [];
        patch.logs = api.logs() || [];
      }
      if (Object.keys(patch).length) store.patch(patch);
      const updated = store.get();
      if (route === "learning") (_b = ensureScreen("learning")) == null ? void 0 : _b.render(updated);
      if (curveNeedsLearning && curve) {
        if (curveEvidenceVisible() && curve.data) curve.renderEvidence(updated);
        if ((_c = curve.needsLearning) == null ? void 0 : _c.call(curve)) curve.renderLearning(updated);
      }
      if (route === "obd") (_d = ensureScreen("obd")) == null ? void 0 : _d.render(updated);
      if (route === "suggestions") {
        utilities == null ? void 0 : utilities.render(updated);
        renderPersistentSuggestions(updated);
      }
      if (route === "tools" && !toolsEditing()) utilities == null ? void 0 : utilities.render(updated);
    }
    function suggestionTargetLabel(item) {
      if (item.target === "CURVE_K") return "Curva K";
      const change = Array.isArray(item.mapChanges) ? item.mapChanges[0] : null;
      return change ? "Mapa K \xB7 c\xE9lula ".concat(Number(change.row) + 1, "\xD7").concat(Number(change.column) + 1) : "Mapa K";
    }
    function suggestionMagnitude(item) {
      const mapChange = Array.isArray(item.mapChanges) ? item.mapChanges[0] : null;
      if (mapChange && Number.isFinite(Number(mapChange.before)) && Number(mapChange.before) !== 0) {
        const pct = (Number(mapChange.after) / Number(mapChange.before) - 1) * 100;
        return "".concat(pct >= 0 ? "+" : "").concat(pct.toFixed(1).replace(".", ","), "%");
      }
      const curve = Array.isArray(item.curveChanges) ? item.curveChanges : [];
      if (curve.length) {
        const mean = curve.reduce((sum, change) => sum + (Number(change.after) / Number(change.before) - 1) * 100, 0) / curve.length;
        return "".concat(mean >= 0 ? "+" : "").concat(mean.toFixed(1).replace(".", ","), "%");
      }
      return "observando";
    }
    function renderPersistentSuggestions(state) {
      const host = byId("suggestionList");
      const calibration = state.calibrationState || {};
      const items = Array.isArray(calibration.suggestionItems) ? calibration.suggestionItems : [];
      if (!host || !items.length) return;
      const current = items.filter((item) => ["PENDING", "OBSERVING"].includes(String(item.lifecycle || "")));
      const pendingMap = current.filter((item) => item.lifecycle === "PENDING" && item.target === "MAP_K" && item.actionable === true);
      const pendingCurve = current.filter((item) => item.lifecycle === "PENDING" && item.target === "CURVE_K" && item.actionable === true);
      const observing = current.filter((item) => item.lifecycle === "OBSERVING");
      const applied = items.filter((item) => item.lifecycle === "APPLIED").slice(-12).reverse();
      const validIds = new Set([...pendingMap, ...pendingCurve].map((item) => item.id));
      [...selectedSuggestionIds].forEach((id) => {
        if (!validIds.has(id)) selectedSuggestionIds.delete(id);
      });
      setText("suggestionCount", pendingMap.length + pendingCurve.length);
      const pendingRows = (list) => list.map((item) => '\n      <label class="suggestion-row" data-lifecycle="PENDING">\n        <input type="checkbox" data-suggestion-select="'.concat(escapeHtml(item.id), '" ').concat(selectedSuggestionIds.has(item.id) ? "checked" : "", '>\n        <span class="suggestion-row-main"><b>').concat(escapeHtml(suggestionTargetLabel(item)), "</b><span>").concat(escapeHtml(item.rationale || "Sugest\xE3o pronta para revis\xE3o humana."), '</span></span>\n        <span class="suggestion-row-meta"><b>').concat(escapeHtml(suggestionMagnitude(item)), "</b><small>").concat(Math.round(Number(item.confidence || 0) * 100), "% confian\xE7a</small></span>\n      </label>")).join("");
      const passiveRows = (list) => list.map((item) => '\n      <div class="suggestion-row" data-lifecycle="'.concat(escapeHtml(item.lifecycle), '">\n        <span></span><span class="suggestion-row-main"><b>').concat(escapeHtml(suggestionTargetLabel(item)), "</b><span>").concat(escapeHtml(item.rationale || ""), '</span></span>\n        <span class="suggestion-row-meta"><b>').concat(item.lifecycle === "APPLIED" ? "aplicada" : "observando", "</b><small>").concat(Math.round(Number(item.confidence || 0) * 100), "% confian\xE7a</small></span>\n      </div>")).join("");
      host.innerHTML = '\n      <div class="suggestion-queue-summary">\n        <div><small>PENDENTES</small><b>'.concat(pendingMap.length + pendingCurve.length, "</b></div>\n        <div><small>OBSERVANDO</small><b>").concat(observing.length, "</b></div>\n        <div><small>APLICADAS</small><b>").concat(applied.length, "</b></div>\n      </div>\n      ").concat(pendingMap.length ? '<section class="suggestion-group" data-suggestion-group="MAP_K"><header><div><small>AJUSTE LOCAL</small><h3>Mapa K \xB7 '.concat(pendingMap.length, ' prontas</h3></div><div class="suggestion-group-actions"><button type="button" class="quiet-button" data-select-ready="MAP_K">Selecionar prontas</button><button type="button" class="primary" data-review-selected="MAP_K">Revisar selecionadas</button></div></header>').concat(pendingRows(pendingMap), "</section>") : "", "\n      ").concat(pendingCurve.length ? '<section class="suggestion-group" data-suggestion-group="CURVE_K"><header><div><small>AJUSTE GLOBAL</small><h3>Curva K \xB7 '.concat(pendingCurve.length, " pronta").concat(pendingCurve.length === 1 ? "" : "s", '</h3></div><div class="suggestion-group-actions"><button type="button" class="quiet-button" data-select-ready="CURVE_K">Selecionar prontas</button><button type="button" class="primary" data-review-selected="CURVE_K">Revisar selecionadas</button></div></header>').concat(pendingRows(pendingCurve), "</section>") : "", "\n      ").concat(observing.length ? '<section class="suggestion-group"><header><div><small>OBSERVANDO</small><h3>Persistem sem valor antigo aplic\xE1vel</h3></div></header>'.concat(passiveRows(observing), "</section>") : "", "\n      ").concat(applied.length ? '<section class="suggestion-group"><header><div><small>HIST\xD3RICO</small><h3>Aplicadas ap\xF3s readback</h3></div></header>'.concat(passiveRows(applied), "</section>") : "", "\n    ");
      host.querySelectorAll("[data-suggestion-select]").forEach((input) => input.addEventListener("change", () => {
        if (input.checked) selectedSuggestionIds.add(input.dataset.suggestionSelect);
        else selectedSuggestionIds.delete(input.dataset.suggestionSelect);
      }));
      host.querySelectorAll("[data-select-ready]").forEach((button) => button.addEventListener("click", () => {
        const target = button.dataset.selectReady;
        const list = target === "MAP_K" ? pendingMap : pendingCurve;
        list.forEach((item) => selectedSuggestionIds.add(item.id));
        renderPersistentSuggestions(store.get());
      }));
      host.querySelectorAll("[data-review-selected]").forEach((button) => button.addEventListener("click", () => {
        const target = button.dataset.reviewSelected;
        const list = (target === "MAP_K" ? pendingMap : pendingCurve).filter((item) => selectedSuggestionIds.has(item.id));
        if (!list.length) {
          showAlert({ level: "warning", message: "Selecione ao menos uma sugest\xE3o pronta." });
          return;
        }
        if (target === "MAP_K") {
          const mapChanges = list.flatMap((item) => Array.isArray(item.mapChanges) ? item.mapChanges : []);
          router.navigate("map", { origin: "suggestions", suggestionIds: list.map((item) => item.id), suggestion: { target: "MAP_K", mapChanges } });
        } else {
          const curveChanges = list.flatMap((item) => Array.isArray(item.curveChanges) ? item.curveChanges : []);
          router.navigate("curve", { origin: "suggestions", suggestionIds: list.map((item) => item.id), suggestion: { target: "CURVE_K", curveChanges } });
        }
      }));
    }
    function activateRoute(route, context) {
      var _a, _b, _c, _d, _e;
      store.patch({ suggestionsOpen: route === "suggestions", toolsOpen: route === "tools" });
      if (route === "dashboard") {
        previousTelemetrySignature = "";
        (_a = ensureScreen("dashboard")) == null ? void 0 : _a.render(store.get());
        afterPaint(refreshFast);
        return;
      }
      if (route === "learning") {
        previousTelemetrySignature = "";
        (_b = ensureScreen("learning")) == null ? void 0 : _b.render(store.get());
        renderLightLiveContext(store.get(), "learning");
        afterPaint(() => {
          refreshFast();
          refreshContext();
        });
        return;
      }
      if (route === "map") {
        previousTelemetrySignature = "";
        (_c = ensureScreen("map")) == null ? void 0 : _c.onEnter(context || store.get().routeContext);
        renderLightLiveContext(store.get(), "map");
        afterPaint(() => {
          refreshFast();
          refreshContext();
        });
        return;
      }
      if (route === "curve") {
        (_d = ensureScreen("curve")) == null ? void 0 : _d.onEnter(context || store.get().routeContext);
        afterPaint(refreshContext);
        return;
      }
      if (route === "obd") {
        (_e = ensureScreen("obd")) == null ? void 0 : _e.render(store.get());
        afterPaint(() => {
          refreshStatus();
          refreshContext();
        });
        return;
      }
      if (route === "suggestions" || route === "tools") {
        if (route === "suggestions") renderPersistentSuggestions(store.get());
        if (route === "tools" && !toolsEditing()) utilities == null ? void 0 : utilities.render(store.get());
        afterPaint(refreshContext);
      }
    }
    router.onNavigate = (route, from, context) => activateRoute(route, context);
    const scheduler = new ui.Scheduler({
      intervalMs: 200,
      onFast: refreshFast,
      onStatus: refreshStatus,
      onContext: refreshContext
    });
    function bindGlobalEvents() {
      var _a, _b, _c;
      routeButtons.forEach((button) => button.addEventListener("click", () => router.navigate(button.dataset.route)));
      (_b = (_a = byId("alertToast")) == null ? void 0 : _a.querySelector("button")) == null ? void 0 : _b.addEventListener("click", () => {
        var _a2;
        return (_a2 = byId("alertToast")) == null ? void 0 : _a2.classList.remove("show");
      });
      (_c = document.querySelector('[data-screen="curve"] .evidence-disclosure')) == null ? void 0 : _c.addEventListener("toggle", (event) => {
        if (event.currentTarget.open && store.get().route === "curve") afterPaint(refreshContext);
      });
      document.addEventListener("visibilitychange", () => {
        const visible = !document.hidden;
        store.patch({ visible });
        if (visible) {
          activateRoute(store.get().route, store.get().routeContext);
          afterPaint(() => {
            refreshStatus();
            scheduler.start();
          });
        } else {
          scheduler.stop();
        }
      });
      root.addEventListener("omegas-refresh", () => {
        afterPaint(() => {
          var _a2, _b2;
          refreshStatus();
          refreshContext();
          const route = store.get().route;
          if (route === "dashboard" || route === "learning" || route === "map") {
            previousTelemetrySignature = "";
            refreshFast();
          }
          if (route === "map") (_a2 = instances.map) == null ? void 0 : _a2.poll();
          if (route === "curve") (_b2 = instances.curve) == null ? void 0 : _b2.poll();
        });
      });
    }
    function initialize() {
      routeButtons = [...document.querySelectorAll("[data-route]")];
      screenNodes = [...document.querySelectorAll("[data-screen]")];
      bindGlobalEvents();
      const identity = api.releaseIdentity() || {};
      store.patch({ identity, demo: api.isDemo() });
      setText("buildIdentity", "".concat(identity.engine || identity.product || "OMEGAS", " \xB7 ").concat(identity.versionName || identity.generation || "V8"));
      store.subscribe(renderShell, true);
      const route = router.restore();
      activateRoute(route, null);
      if (document.body) document.body.dataset.omegasBoot = "ready";
      afterPaint(() => {
        refreshStatus();
        scheduler.start();
      });
    }
    root.OmegasApp = { api, store, router, scheduler, screens: instances };
    initialize();
  })(typeof window !== "undefined" ? window : globalThis);
})();
