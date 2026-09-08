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
    function cellPosition(item) {
      var _a, _b, _c, _d, _e, _f, _g, _h;
      const cell = item && item.cell;
      const row = finite((_d = (_c = (_b = (_a = item == null ? void 0 : item.row) != null ? _a : item == null ? void 0 : item.cell_row) != null ? _b : item == null ? void 0 : item.cng_cell_row) != null ? _c : item == null ? void 0 : item.cngCellRow) != null ? _d : cell == null ? void 0 : cell.row);
      const column = finite((_h = (_g = (_f = (_e = item == null ? void 0 : item.column) != null ? _e : item == null ? void 0 : item.cell_column) != null ? _f : item == null ? void 0 : item.cng_cell_column) != null ? _g : item == null ? void 0 : item.cngCellColumn) != null ? _h : cell == null ? void 0 : cell.column);
      return row === null || column === null ? null : { row: Math.trunc(row), column: Math.trunc(column) };
    }
    function key(row, column) {
      return "".concat(row, ":").concat(column);
    }
    function indexByCell(items) {
      const map = /* @__PURE__ */ new Map();
      (Array.isArray(items) ? items : []).forEach((item) => {
        var _a, _b, _c, _d, _e, _f, _g, _h;
        const pos = cellPosition(item);
        if (!pos) return;
        const itemKey = key(pos.row, pos.column);
        const previous = map.get(itemKey);
        const score = (_d = (_c = (_b = (_a = finite(item.confidence)) != null ? _a : finite(item.quality)) != null ? _b : finite(item.samples)) != null ? _c : finite(item.weight)) != null ? _d : 0;
        const previousScore = previous ? (_h = (_g = (_f = (_e = finite(previous.confidence)) != null ? _e : finite(previous.quality)) != null ? _f : finite(previous.samples)) != null ? _g : finite(previous.weight)) != null ? _h : 0 : -1;
        if (!previous || score >= previousScore) map.set(itemKey, item);
      });
      return map;
    }
    function comparisonError(item) {
      var _a, _b, _c, _d, _e, _f, _g;
      return finite((_g = (_f = (_e = (_d = (_c = (_b = (_a = item == null ? void 0 : item.observed_pair) == null ? void 0 : _a.error_percent) != null ? _b : item == null ? void 0 : item.errorPercent) != null ? _c : item == null ? void 0 : item.error_pct) != null ? _d : item == null ? void 0 : item.error_percent) != null ? _e : item == null ? void 0 : item.relativeErrorPercent) != null ? _f : item == null ? void 0 : item.differencePercent) != null ? _g : item == null ? void 0 : item.error);
    }
    function comparisonTargetMs(item) {
      var _a, _b, _c, _d;
      return finite((_d = (_c = (_b = item == null ? void 0 : item.petrolReferenceMs) != null ? _b : (_a = item == null ? void 0 : item.observed_pair) == null ? void 0 : _a.petrol_target_ms) != null ? _c : item == null ? void 0 : item.petrol_target_ms) != null ? _d : item == null ? void 0 : item.petrolTargetMs);
    }
    function comparisonObservedMs(item) {
      var _a, _b, _c;
      return finite((_c = (_b = (_a = item == null ? void 0 : item.observed_pair) == null ? void 0 : _a.petrol_on_cng_ms) != null ? _b : item == null ? void 0 : item.petrol_on_cng_ms) != null ? _c : item == null ? void 0 : item.petrolOnCngMs);
    }
    function confidence(item) {
      var _a;
      const raw = finite((_a = item == null ? void 0 : item.confidence) != null ? _a : item == null ? void 0 : item.quality);
      if (raw !== null) return raw > 1 ? Math.min(1, raw / 100) : Math.max(0, Math.min(1, raw));
      const samples = finite(item == null ? void 0 : item.samples);
      return samples === null ? 0 : Math.min(1, samples / 50);
    }
    function evidenceIndex(model) {
      return new Map(((model == null ? void 0 : model.cells) || []).map((cell) => [cell.key, cell]));
    }
    function fuelLabel(value) {
      const fuel = String(value || "").toUpperCase();
      if (fuel.includes("PETROL") || fuel.includes("GASOLINA")) return "Gasolina";
      if (fuel.includes("CNG") || fuel.includes("GNV") || fuel === "GAS") return "GNV";
      return fuel || "\u2014";
    }
    function stateLabel(decision) {
      const state = String((decision == null ? void 0 : decision.state) || "OBSERVING_ENGINE").toUpperCase();
      if ((decision == null ? void 0 : decision.learning_eligible) === true || state === "SAMPLE_ACCEPTED") return "Evid\xEAncia aceita";
      if (state === "FORMING_SAMPLE") return "Formando evid\xEAncia";
      if (state === "FUEL_VERIFYING") return "Confirmando combust\xEDvel";
      if (state === "FUEL_STABLE") return "Combust\xEDvel confirmado";
      if (state === "ENGINE_WARMING") return "Aguardando temperatura";
      if (state === "SAMPLE_REJECTED") return "Janela recusada";
      if (state === "WINDOW_TIMEOUT") return "Janela reiniciada";
      if (state === "TELEMETRY_GAP") return "Telemetria interrompida";
      if (state === "CUTOFF") return "Aprendizado pausado";
      return state.replace(/_/g, " ").toLowerCase().replace(/^./, (char) => char.toUpperCase());
    }
    function blueProposalSummary(calibrationState) {
      const blue = calibrationState || {};
      const proposal = blue.proposal && typeof blue.proposal === "object" ? blue.proposal : {};
      const multiplier = finite(proposal.correctionMultiplier);
      if (proposal.available === true && multiplier !== null) {
        return { available: true, label: "Blue: multiplicador ".concat(fmt(multiplier, 4)), detail: "proposta separada da medi\xE7\xE3o" };
      }
      return {
        available: false,
        label: "Blue: aguardando ganho causal",
        detail: proposal.state || blue.reason || "sem alvo K inventado"
      };
    }
    class LearningScreen {
      constructor(store, router, api) {
        this.store = store;
        this.router = router;
        this.api = api;
        this.root = document.querySelector('[data-screen="learning"]');
        this.host = document.getElementById("learningGrid");
        this.detail = document.getElementById("learningCellDetail");
        this.selectedCell = null;
        this.inspectorPane = "collection";
        this.decisionHistory = [];
        this.lastDecisionHistorySignature = "";
        this.grid = this.host && ns.PhysicalGrid ? new ns.PhysicalGrid(this.host, {
          onCell: (row, column) => {
            this.selectedCell = { row, column };
            this.setInspectorPane("cell");
            this.renderDetail(this.store.get(), row, column);
          }
        }) : null;
        this.ensureInspector();
        this.bind();
      }
      ensureInspector() {
        if (!this.detail) return;
        this.detail.innerHTML = '\n        <div class="learning-inspector-tabs" role="tablist" aria-label="Detalhes do aprendizado">\n          <button type="button" data-learning-inspector="cell">C\xE9lula</button>\n          <button type="button" data-learning-inspector="collection" class="active">Coleta</button>\n        </div>\n        <div id="learningCellPane" class="learning-inspector-pane" data-pane="cell">\n          <div class="detail-empty"><b>Toque em uma c\xE9lula</b><span>Veja gasolina, GNV e o desvio realmente medido. A proposta Blue aparece separada.</span></div>\n        </div>\n        <div id="learningCollectionPane" class="learning-inspector-pane active" data-pane="collection"></div>\n      ';
        this.cellPane = document.getElementById("learningCellPane");
        this.collectionPane = document.getElementById("learningCollectionPane");
        this.detail.querySelectorAll("[data-learning-inspector]").forEach((button) => {
          button.addEventListener("click", () => this.setInspectorPane(button.dataset.learningInspector));
        });
      }
      setInspectorPane(pane) {
        var _a, _b;
        this.inspectorPane = pane || "collection";
        (_a = this.detail) == null ? void 0 : _a.querySelectorAll("[data-learning-inspector]").forEach((button) => {
          const active = button.dataset.learningInspector === this.inspectorPane;
          button.classList.toggle("active", active);
          button.setAttribute("aria-selected", active ? "true" : "false");
        });
        (_b = this.detail) == null ? void 0 : _b.querySelectorAll(".learning-inspector-pane").forEach((node) => {
          node.classList.toggle("active", node.dataset.pane === this.inspectorPane);
        });
      }
      bind() {
        var _a;
        (_a = this.root) == null ? void 0 : _a.querySelectorAll("[data-learning-layer]").forEach((button) => {
          button.addEventListener("click", () => this.store.patch({ learningLayer: button.dataset.learningLayer }));
        });
      }
      buildEvidenceModel(maps) {
        var _a;
        return ((_a = ns.LearningModel) == null ? void 0 : _a.buildModel) ? ns.LearningModel.buildModel(maps || {}) : null;
      }
      observeDecision(decision) {
        const state = String((decision == null ? void 0 : decision.state) || "").toUpperCase();
        const code = String((decision == null ? void 0 : decision.reason_code) || state || "").toUpperCase();
        const meaningful = (decision == null ? void 0 : decision.learning_eligible) === true || [
          "SAMPLE_ACCEPTED",
          "SAMPLE_REJECTED",
          "WINDOW_TIMEOUT",
          "TELEMETRY_GAP",
          "FUEL_STABLE",
          "ENGINE_WARMING",
          "INVALID",
          "PLAUSIBILITY_REJECTED"
        ].includes(state) || ["SAMPLE_ACCEPTED", "SAMPLE_ACCEPTED_EARLY", "PLAUSIBILITY_REJECTED"].includes(code);
        if (!meaningful) return;
        const row = finite(decision == null ? void 0 : decision.cell_row);
        const column = finite(decision == null ? void 0 : decision.cell_column);
        const cell = row !== null && column !== null && row >= 0 && column >= 0 ? "".concat(row + 1, "\xD7").concat(column + 1) : "\u2014";
        const signature = [state, code, (decision == null ? void 0 : decision.learning_eligible) === true, (decision == null ? void 0 : decision.reason) || "", (decision == null ? void 0 : decision.fuel_confirmed) || "", cell].join("|");
        if (!signature || signature === this.lastDecisionHistorySignature) return;
        this.lastDecisionHistorySignature = signature;
        const level = (decision == null ? void 0 : decision.learning_eligible) === true || state === "SAMPLE_ACCEPTED" ? "accepted" : ["SAMPLE_REJECTED", "WINDOW_TIMEOUT", "TELEMETRY_GAP", "INVALID"].includes(state) || code === "PLAUSIBILITY_REJECTED" ? "rejected" : "info";
        this.decisionHistory.unshift({
          time: (/* @__PURE__ */ new Date()).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit", second: "2-digit" }),
          level,
          label: stateLabel(decision),
          reason: (decision == null ? void 0 : decision.reason) || code || "Decis\xE3o observada",
          code,
          fuel: fuelLabel(decision == null ? void 0 : decision.fuel_confirmed),
          cell
        });
        if (this.decisionHistory.length > 6) this.decisionHistory.length = 6;
      }
      render(state) {
        var _a, _b;
        if (!this.root || !this.grid) return;
        this.observeDecision(state.learningDecision || {});
        const maps = state.learning || {};
        const axes = maps.grid || {};
        (_b = (_a = this.grid).setAxes) == null ? void 0 : _b.call(_a, axes.rpmBins || [], axes.petrolBins || []);
        const model = this.buildEvidenceModel(maps);
        const evidence = evidenceIndex(model);
        const layer = ["petrol", "cng", "comparison"].includes(state.learningLayer) ? state.learningLayer : "comparison";
        this.root.querySelectorAll("[data-learning-layer]").forEach((button) => button.classList.toggle("active", button.dataset.learningLayer === layer));
        const comparisons = indexByCell(maps.comparisons);
        this.grid.cells.forEach((cell, cellKey) => {
          var _a2, _b2, _c, _d;
          const learned = evidence.get(cellKey);
          let source = null;
          let cellText = "\xB7";
          let subtext = "";
          let heat = 0;
          let tone = "neutral";
          if (layer === "petrol") {
            source = (learned == null ? void 0 : learned.petrol) || null;
            if (source) {
              const meanMs = finite(source.petrolMs);
              cellText = meanMs === null ? "\u2022" : fmt(meanMs, 2);
              subtext = meanMs === null ? "".concat(Math.round(source.samples || 0), " am.") : "ms gasolina";
              heat = confidence(source);
              tone = "petrol";
            }
          } else if (layer === "cng") {
            source = (learned == null ? void 0 : learned.cng) || null;
            if (source) {
              const meanMs = finite(source.petrolMs);
              cellText = meanMs === null ? "\u2022" : fmt(meanMs, 2);
              subtext = meanMs === null ? "".concat(Math.round(source.samples || 0), " am.") : "ms no GNV";
              heat = confidence(source);
              tone = "cng";
            }
          } else if (layer === "comparison") {
            source = comparisons.get(cellKey) || null;
            const error = comparisonError(source);
            if (error !== null) {
              cellText = "".concat(error > 0 ? "+" : "").concat(fmt(error, 1), "%");
              subtext = "par medido";
              heat = Math.min(1, Math.abs(error) / 8);
              tone = Math.abs(error) <= 1.5 ? "good" : error > 0 ? "high" : "low";
            } else if ((learned == null ? void 0 : learned.state) === ((_b2 = (_a2 = ns.LearningModel) == null ? void 0 : _a2.STATES) == null ? void 0 : _b2.COMPARABLE)) {
              cellText = "\u2026";
              subtext = "sem par medido";
              heat = 0.15;
            }
          }
          this.grid.updateCell(Number(cell.dataset.row), Number(cell.dataset.column), {
            text: cellText,
            subtext,
            heat,
            tone,
            hasData: !!source || !!learned,
            state: (learned == null ? void 0 : learned.state) || "",
            selected: ((_c = this.selectedCell) == null ? void 0 : _c.row) === Number(cell.dataset.row) && ((_d = this.selectedCell) == null ? void 0 : _d.column) === Number(cell.dataset.column)
          });
        });
        const coverage = document.getElementById("learningCoverageSummary");
        if (coverage && model) {
          const petrolCount = model.counts.petrol + model.counts.comparable;
          const cngCount = model.counts.cng + model.counts.comparable;
          coverage.textContent = "".concat(petrolCount, " gasolina \xB7 ").concat(cngCount, " GNV atual \xB7 ").concat(comparisons.size, " pares medidos");
        }
        const proposalSummary = blueProposalSummary(state.calibrationState);
        const summary = document.getElementById("learningSuggestionSummary");
        if (summary) summary.textContent = proposalSummary.label;
        this.renderCollection(state);
        if (this.selectedCell) this.renderDetail(state, this.selectedCell.row, this.selectedCell.column);
      }
      renderCollection(state) {
        var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k;
        if (!this.collectionPane) return;
        const decision = state.learningDecision || {};
        const learningStatus = state.learningStatus || {};
        const restoring = learningStatus.restoring === true || String(learningStatus.state || "").toUpperCase() === "LEARNING_RESTORING";
        const live = ((_a = state.telemetry) == null ? void 0 : _a.live) || {};
        const interpolation = ((_b = state.telemetry) == null ? void 0 : _b.interpolation) || {};
        const cell = interpolation.cell || {};
        const count = Math.max(0, finite(decision.frame_count) || 0);
        const desired = Math.max(0, finite(decision.desired_frames) || 0);
        const minimum = Math.max(0, finite(decision.minimum_frames) || 0);
        const progress = desired > 0 ? Math.min(100, count / desired * 100) : 0;
        const eligible = decision.learning_eligible === true;
        const fuel = fuelLabel(decision.fuel_confirmed || live.fuel);
        const reason = restoring ? learningStatus.reason || "Restaurando conhecimento persistido em segundo plano." : decision.reason || live.sample_reason || "Aguardando decis\xE3o do n\xFAcleo.";
        const quality = finite(decision.quality);
        const row = finite((_c = decision.cell_row) != null ? _c : cell.row);
        const column = finite((_d = decision.cell_column) != null ? _d : cell.column);
        const rpm = finite((_e = interpolation.rpm) != null ? _e : live.rpm);
        const petrolMs = finite((_g = (_f = interpolation.petrolMs) != null ? _f : live.petrol_ms) != null ? _g : live.petrolMs);
        const mapBar = finite((_i = (_h = interpolation.mapBar) != null ? _h : live.load_bar) != null ? _i : live.map_bar);
        const pressure = finite((_j = live.pressure_diff_bar) != null ? _j : live.gas_pressure_abs_bar);
        const water = finite((_k = live.water_c) != null ? _k : live.waterC);
        const timeout = finite(decision.window_budget_ms);
        const age = finite(decision.window_age_ms);
        const historyRows = this.decisionHistory.length ? this.decisionHistory.map((item) => '<div data-level="'.concat(escapeHtml(item.level), '"><time>').concat(escapeHtml(item.time), "</time><b>").concat(escapeHtml(item.label), "</b><span>").concat(escapeHtml(item.reason), "</span><small>").concat(escapeHtml(item.fuel), " \xB7 c\xE9lula ").concat(escapeHtml(item.cell), " \xB7 ").concat(escapeHtml(item.code), "</small></div>")).join("") : '<p class="empty-copy">Nenhum aceite, descarte ou transi\xE7\xE3o relevante observado desde que esta tela foi aberta.</p>';
        this.collectionPane.innerHTML = '\n        <section class="learning-decision-card" data-eligible="'.concat(eligible ? "true" : "false", '">\n          <div class="decision-top"><div><small>DECIS\xC3O DO N\xDACLEO</small><h3>').concat(restoring ? "Learning restaurando" : escapeHtml(stateLabel(decision)), "</h3></div><span>").concat(restoring ? "EM SEGUNDO PLANO" : eligible ? "CONTA" : "N\xC3O CONTA AINDA", "</span></div>\n          <p>").concat(escapeHtml(reason), '</p>\n          <div class="collection-progress"><i style="width:').concat(progress.toFixed(1), '%"></i></div>\n          <div class="collection-facts"><span><b>').concat(count, "/").concat(desired || "\u2014", "</b> leituras</span><span>m\xEDnimo ").concat(minimum || "\u2014", "</span><span>").concat(fuel, "</span><span>").concat(quality === null ? "qualidade \u2014" : "qualidade ".concat(Math.round(quality * 100), "%"), '</span></div>\n          <small class="reason-code">').concat(restoring ? "LEARNING_RESTORE_PENDING" : escapeHtml(decision.reason_code || decision.state || "OBSERVING_ENGINE"), '</small>\n        </section>\n        <section class="learning-current-condition">\n          <header><div><small>CONDI\xC7\xC3O AGORA</small><h3>O que o n\xFAcleo est\xE1 observando</h3></div>').concat(row !== null && column !== null && row >= 0 && column >= 0 ? "<span>C\xE9lula ".concat(row + 1, "\xD7").concat(column + 1, "</span>") : "", '</header>\n          <div class="condition-grid">\n            <div><small>RPM</small><b>').concat(rpm === null ? "\u2014" : Math.round(rpm).toLocaleString("pt-BR"), "</b></div>\n            <div><small>Petrol Inj.</small><b>").concat(fmt(petrolMs, 2), " ms</b></div>\n            <div><small>MAP</small><b>").concat(fmt(mapBar, 3), " bar</b></div>\n            <div><small>Press\xE3o GNV</small><b>").concat(fmt(pressure, 3), " bar</b></div>\n            <div><small>\xC1gua</small><b>").concat(water === null ? "\u2014" : "".concat(fmt(water, 0), " \xB0C"), "</b></div>\n            <div><small>Janela</small><b>").concat(age === null ? "\u2014" : "".concat(fmt(age / 1e3, 1), " s")).concat(timeout ? " / ".concat(fmt(timeout / 1e3, 1), " s") : "", '</b></div>\n          </div>\n          <p class="learning-light-note">A posi\xE7\xE3o ao vivo \xE9 somente contexto. Gasolina \xE9 a refer\xEAncia; GNV s\xF3 \xE9 comparado quando existe par f\xEDsico equivalente.</p>\n        </section>\n        <section class="learning-decision-history">\n          <header><div><small>\xDALTIMAS DECIS\xD5ES OBSERVADAS</small><h3>O que acabou de acontecer com a coleta</h3></div><span>').concat(this.decisionHistory.length, '/6</span></header>\n          <div class="decision-history-list">').concat(historyRows, '</div>\n          <p>O registro persistente completo continua no SessionRecorder em Ferramentas.</p>\n        </section>\n        <section class="learning-policy-summary">\n          <header><small>ESTABILIDADE DA EVID\xCANCIA</small><span>AUTOM\xC1TICA</span></header>\n          <div class="policy-grid"><span>RPM <b>interno</b></span><span>MAP <b>interno</b></span><span>Petrol Inj. <b>interno</b></span><span>Continuidade <b>protegida</b></span></div>\n          <p>O n\xFAcleo decide automaticamente se RPM, MAP e Petrol Inj. representam a mesma condi\xE7\xE3o f\xEDsica. N\xE3o existe perfil do usu\xE1rio para afrouxar ou apertar a ci\xEAncia.</p>\n        </section>\n      ');
      }
      renderDetail(state, row, column) {
        var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k, _l, _m, _n, _o, _p, _q, _r, _s, _t, _u;
        if (!this.cellPane) return;
        const maps = state.learning || {};
        const model = this.buildEvidenceModel(maps);
        const learned = evidenceIndex(model).get(key(row, column));
        const comparison = indexByCell(maps.comparisons).get(key(row, column));
        const measuredError = comparisonError(comparison);
        const targetMs = comparisonTargetMs(comparison);
        const observedMs = comparisonObservedMs(comparison);
        const observedPair = (comparison == null ? void 0 : comparison.observed_pair) || comparison || null;
        const referenceSupport = (comparison == null ? void 0 : comparison.reference_support) || null;
        const petrolSamples = (_b = finite((_a = learned == null ? void 0 : learned.petrol) == null ? void 0 : _a.samples)) != null ? _b : 0;
        const cngSamples = (_d = finite((_c = learned == null ? void 0 : learned.cng) == null ? void 0 : _c.samples)) != null ? _d : 0;
        const petrolVisits = (_f = finite((_e = learned == null ? void 0 : learned.petrol) == null ? void 0 : _e.visits)) != null ? _f : 0;
        const cngVisits = (_h = finite((_g = learned == null ? void 0 : learned.cng) == null ? void 0 : _g.visits)) != null ? _h : 0;
        const petrolMeanMs = finite((_i = learned == null ? void 0 : learned.petrol) == null ? void 0 : _i.petrolMs);
        const cngMeanMs = finite((_j = learned == null ? void 0 : learned.cng) == null ? void 0 : _j.petrolMs);
        const petrolRpm = finite((_k = learned == null ? void 0 : learned.petrol) == null ? void 0 : _k.rpm);
        const cngRpm = finite((_l = learned == null ? void 0 : learned.cng) == null ? void 0 : _l.rpm);
        const petrolMap = finite((_m = learned == null ? void 0 : learned.petrol) == null ? void 0 : _m.mapBar);
        const cngMap = finite((_n = learned == null ? void 0 : learned.cng) == null ? void 0 : _n.mapBar);
        const historicalEpochs = [...new Set(((learned == null ? void 0 : learned.previousCng) || []).map((item) => item.epoch))].sort((a, b) => b - a);
        const axes = maps.grid || {};
        const axisRpm = finite((_o = axes.rpmBins) == null ? void 0 : _o[column]);
        const axisPetrol = finite((_p = axes.petrolBins) == null ? void 0 : _p[row]);
        const rpmLabel = (_q = finite(learned == null ? void 0 : learned.rpm)) != null ? _q : axisRpm;
        const petrolLabel = (_r = finite(learned == null ? void 0 : learned.petrolMs)) != null ? _r : axisPetrol;
        const blue = state.calibrationState || {};
        const latestComparison = blue.latestComparison && typeof blue.latestComparison === "object" ? blue.latestComparison : null;
        const proposal = blue.proposal && typeof blue.proposal === "object" ? blue.proposal : {};
        const multiplier = finite(proposal.correctionMultiplier);
        const comparisonText = measuredError !== null && targetMs !== null && observedMs !== null ? "".concat(fmt(targetMs, 2), " \u2192 ").concat(fmt(observedMs, 2), " ms \xB7 ").concat(measuredError > 0 ? "+" : "").concat(fmt(measuredError, 1), "%") : "ainda n\xE3o existe par equivalente v\xE1lido";
        const pairCondition = observedPair ? "".concat(finite(observedPair.rpm) === null ? "RPM \u2014" : "".concat(Math.round(finite(observedPair.rpm)).toLocaleString("pt-BR"), " RPM"), " \xB7 MAP ").concat(fmt((_s = observedPair.map_bar) != null ? _s : observedPair.mapBar, 3), " bar \xB7 qualidade ").concat(Math.round((finite(observedPair.quality) || 0) * 100), "%") : "\u2014";
        const supportType = String((referenceSupport == null ? void 0 : referenceSupport.support_type) || "UNKNOWN").toUpperCase();
        const supportLabel = supportType === "DIRECT" ? "direto" : supportType === "NEAR" ? "vizinho f\xEDsico" : "n\xE3o informado";
        const supportText = referenceSupport ? "".concat(supportLabel, " \xB7 ").concat(Math.round(finite(referenceSupport.selected_candidates) || 0), " refer\xEAncia(s) \xB7 dispers\xE3o ").concat(fmt(referenceSupport.spread_ms, 3), " ms") : "proced\xEAncia n\xE3o dispon\xEDvel";
        const blueText = proposal.available === true && multiplier !== null ? "BlueCausalEngine prop\xF5e multiplicador ".concat(fmt(multiplier, 4), "; medir e corrigir continuam etapas separadas.") : "BlueCausalEngine: ".concat(escapeHtml(proposal.state || "sem ganho causal suficiente"), ". Nenhum alvo K \xE9 inventado.");
        const latestText = latestComparison ? "\xFAltimo erro Blue ".concat(fmt(latestComparison.errorPercent, 2), "% \xB7 qualidade ").concat(Math.round((finite(latestComparison.quality) || 0) * 100), "%") : "nenhuma compara\xE7\xE3o Blue reconciliada ainda";
        this.cellPane.innerHTML = '\n        <div class="detail-eyebrow">C\xC9LULA '.concat(row + 1, " \xD7 ").concat(column + 1, "</div>\n        <h3>").concat(rpmLabel === null ? "RPM \u2014" : "".concat(Math.round(rpmLabel).toLocaleString("pt-BR"), " RPM"), " \xB7 ").concat(fmt(petrolLabel, 1), ' ms</h3>\n        <p class="learning-reason"><b>Gasolina \xE9 a refer\xEAncia.</b> Esta tela mostra evid\xEAncia f\xEDsica e o desvio medido; proposta de corre\xE7\xE3o \xE9 sa\xEDda separada do Blue.</p>\n        <dl class="detail-list enhanced-detail-list">\n          <div><dt>Gasolina \u2014 refer\xEAncia agregada</dt><dd>').concat((learned == null ? void 0 : learned.petrol) ? "".concat(fmt(petrolMeanMs, 2), " ms \xB7 ").concat(petrolRpm === null ? "RPM \u2014" : "".concat(Math.round(petrolRpm).toLocaleString("pt-BR"), " RPM"), " \xB7 MAP ").concat(fmt(petrolMap, 3), " bar") : "sem evid\xEAncia gasolina nesta c\xE9lula", "</dd></div>\n          <div><dt>Qualidade da refer\xEAncia</dt><dd>").concat((learned == null ? void 0 : learned.petrol) ? "".concat(Math.round(petrolSamples), " amostras \xB7 ").concat(petrolVisits, " visita(s) \xB7 qualidade ").concat(Math.round(confidence(learned.petrol) * 100), "%") : "\u2014", "</dd></div>\n          <div><dt>GNV atual \u2014 Petrol Inj.</dt><dd>").concat((learned == null ? void 0 : learned.cng) ? "".concat(fmt(cngMeanMs, 2), " ms \xB7 ").concat(cngRpm === null ? "RPM \u2014" : "".concat(Math.round(cngRpm).toLocaleString("pt-BR"), " RPM"), " \xB7 MAP ").concat(fmt(cngMap, 3), " bar") : "sem evid\xEAncia GNV atual nesta c\xE9lula", "</dd></div>\n          <div><dt>Qualidade do GNV</dt><dd>").concat((learned == null ? void 0 : learned.cng) ? "".concat(Math.round(cngSamples), " amostras \xB7 ").concat(cngVisits, " visita(s) \xB7 qualidade ").concat(Math.round(confidence(learned.cng) * 100), "% \xB7 \xE9poca ").concat((_t = model == null ? void 0 : model.epoch) != null ? _t : "\u2014") : "\u2014", "</dd></div>\n          <div><dt>Desvio medido</dt><dd>").concat(comparisonText, "<br>").concat(pairCondition, "</dd></div>\n          <div><dt>Suporte da refer\xEAncia</dt><dd>").concat(supportText, "</dd></div>\n          <div><dt>Hist\xF3rico GNV</dt><dd>").concat(historicalEpochs.length ? "\xE9pocas ".concat(historicalEpochs.join(", "), " \xB7 somente consulta") : "nenhum", "</dd></div>\n          <div><dt>Corre\xE7\xE3o Blue \u2014 separada da medi\xE7\xE3o</dt><dd>").concat(blueText, "<br>").concat(latestText, '</dd></div>\n        </dl>\n        <button class="primary wide" type="button" data-edit-learning-cell>Editar esta c\xE9lula manualmente</button>\n        <small class="manual-edit-contract">Abrir o editor n\xE3o escreve na ECU. Revis\xE3o, confirma\xE7\xE3o, ACK e readback continuam obrigat\xF3rios.</small>\n      ');
        (_u = this.cellPane.querySelector("[data-edit-learning-cell]")) == null ? void 0 : _u.addEventListener("click", () => {
          this.router.navigate("map", {
            origin: "learning",
            cell: { row, column },
            physical: { rpm: rpmLabel, petrolMs: petrolLabel }
          });
        });
      }
    }
    ns.LearningScreen = LearningScreen;
  })(typeof window !== "undefined" ? window : globalThis);
})();


