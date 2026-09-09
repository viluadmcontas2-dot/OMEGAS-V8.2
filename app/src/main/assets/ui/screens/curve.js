(() => {
  var __defProp = Object.defineProperty;
  var __defProps = Object.defineProperties;
  var __getOwnPropDescs = Object.getOwnPropertyDescriptors;
  var __getOwnPropSymbols = Object.getOwnPropertySymbols;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __propIsEnum = Object.prototype.propertyIsEnumerable;
  var __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : obj[key] = value;
  var __spreadValues = (a, b) => {
    for (var prop in b || (b = {}))
      if (__hasOwnProp.call(b, prop))
        __defNormalProp(a, prop, b[prop]);
    if (__getOwnPropSymbols)
      for (var prop of __getOwnPropSymbols(b)) {
        if (__propIsEnum.call(b, prop))
          __defNormalProp(a, prop, b[prop]);
      }
    return a;
  };
  var __spreadProps = (a, b) => __defProps(a, __getOwnPropDescs(b));
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
    function escapeHtml(value) {
      return String(value != null ? value : "").replace(/[&<>\"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[char]);
    }
    function comparisonTargetMs(item) {
      var _a, _b, _c;
      return finite((_c = (_b = (_a = item == null ? void 0 : item.observed_pair) == null ? void 0 : _a.petrol_target_ms) != null ? _b : item == null ? void 0 : item.petrol_target_ms) != null ? _c : item == null ? void 0 : item.petrolTargetMs);
    }
    function comparisonObservedMs(item) {
      var _a, _b, _c;
      return finite((_c = (_b = (_a = item == null ? void 0 : item.observed_pair) == null ? void 0 : _a.petrol_on_cng_ms) != null ? _b : item == null ? void 0 : item.petrol_on_cng_ms) != null ? _c : item == null ? void 0 : item.petrolOnCngMs);
    }
    function comparisonError(item) {
      var _a, _b, _c, _d;
      return finite((_d = (_c = (_b = (_a = item == null ? void 0 : item.observed_pair) == null ? void 0 : _a.error_percent) != null ? _b : item == null ? void 0 : item.error_percent) != null ? _c : item == null ? void 0 : item.errorPercent) != null ? _d : item == null ? void 0 : item.relativeErrorPercent);
    }
    function comparisonQuality(item) {
      var _a, _b, _c;
      const raw = finite((_c = (_b = (_a = item == null ? void 0 : item.observed_pair) == null ? void 0 : _a.quality) != null ? _b : item == null ? void 0 : item.quality) != null ? _c : item == null ? void 0 : item.confidence);
      if (raw === null) return 0;
      return raw > 1 ? Math.min(1, raw / 100) : Math.max(0, Math.min(1, raw));
    }
    class CurveScreen {
      constructor(store, api) {
        this.store = store;
        this.api = api;
        this.root = document.querySelector('[data-screen="curve"]');
        this.data = null;
        this.activeIndex = null;
        this.selectedIndices = /* @__PURE__ */ new Set();
        this.selectionMode = false;
        this.dragSelecting = false;
        this.dragMoved = false;
        this.dragStartIndex = null;
        this.dragStartWasSelected = false;
        this.proposals = /* @__PURE__ */ new Map();
        this.pendingSuggestion = null;
        this.reading = false;
        this.writing = false;
        this.view = "editor";
        this.learningSignature = "";
        this.bind();
      }
      bind() {
        var _a, _b, _c, _d, _e, _f, _g, _h, _i;
        (_a = document.getElementById("curveReadButton")) == null ? void 0 : _a.addEventListener("click", () => this.startRead());
        (_b = document.getElementById("curvePreparePoint")) == null ? void 0 : _b.addEventListener("click", () => this.prepareSelectionTarget());
        (_c = document.getElementById("curveSelectionMode")) == null ? void 0 : _c.addEventListener("click", () => this.setSelectionMode(!this.selectionMode));
        (_d = document.getElementById("curveClearSelection")) == null ? void 0 : _d.addEventListener("click", () => this.clearSelection());
        this.updateSelectionUi();
        document.querySelectorAll("[data-curve-view]").forEach((button) => button.addEventListener("click", () => this.setView(button.dataset.curveView || "editor")));
        document.querySelectorAll("[data-curve-nudge]").forEach((button) => button.addEventListener("click", () => this.nudgeSelection(Number(button.dataset.curveNudge) || 0)));
        (_e = document.getElementById("curveClearProposals")) == null ? void 0 : _e.addEventListener("click", () => {
          this.proposals.clear();
          this.renderChart();
          this.renderProposalList();
        });
        (_f = document.getElementById("curveReviewButton")) == null ? void 0 : _f.addEventListener("click", () => this.openReview());
        (_g = document.getElementById("curveReviewBack")) == null ? void 0 : _g.addEventListener("click", () => this.closeReview());
        (_h = document.getElementById("curveWriteButton")) == null ? void 0 : _h.addEventListener("click", () => this.writeReview());
        (_i = document.getElementById("curveDismissResult")) == null ? void 0 : _i.addEventListener("click", () => this.closeReview());
      }
      needsLearning() {
        return this.view === "learning";
      }
      setView(view) {
        if (view !== "learning" && view !== "editor") return false;
        this.view = view;
        document.querySelectorAll("[data-curve-view]").forEach((button) => button.classList.toggle("active", button.dataset.curveView === this.view));
        document.querySelectorAll("[data-curve-panel]").forEach((panel) => panel.classList.toggle("active", panel.dataset.curvePanel === this.view));
        if (this.view === "learning") this.renderLearning(this.store.get());
        else this.renderChart();
        return true;
      }
      onEnter(context) {
        const suggestion = context && context.suggestion;
        if (suggestion) {
          this.pendingSuggestion = suggestion;
          this.setView("editor");
          this.renderSuggestionFocus(suggestion);
        }
        if (!this.data && !this.reading) this.startRead(true);
        if (this.data && suggestion) {
          this.focusSuggestion(suggestion);
          this.prepareSuggestion(suggestion, true);
        }
        if (this.view === "learning") this.renderLearning(this.store.get());
      }
      startRead() {
        var _a;
        if (this.reading || this.writing) return;
        const result = this.api.startCurveRead();
        if (!(result == null ? void 0 : result.ok) || !(result == null ? void 0 : result.started)) {
          this.alert((result == null ? void 0 : result.error) || "N\xE3o foi poss\xEDvel iniciar a leitura da Curva K.");
          return;
        }
        this.reading = true;
        this.data = null;
        this.proposals.clear();
        this.selectedIndices.clear();
        this.selectionMode = false;
        this.activeIndex = null;
        this.updateSelectionUi();
        text("curveSourceStatus", "Lendo 30 pontos diretamente da ECU");
        (_a = this.root) == null ? void 0 : _a.classList.add("is-reading");
        if (this.pendingSuggestion) this.renderSuggestionFocus(this.pendingSuggestion);
      }
      poll() {
        var _a, _b, _c, _d, _e;
        if (!this.reading && !this.writing) return;
        const operation = this.api.curveOperation();
        if (!operation) return;
        if (this.reading && !operation.busy && (operation.state === "COMPLETED" || operation.demo)) {
          this.reading = false;
          (_a = this.root) == null ? void 0 : _a.classList.remove("is-reading");
          if (!operation.ok || !Array.isArray(operation.points) || operation.points.length !== 30) {
            this.alert(operation.error || "A Curva K n\xE3o retornou os 30 pontos v\xE1lidos.");
            text("curveSourceStatus", "Curva n\xE3o confirmada");
            return;
          }
          this.data = operation;
          text("curveSourceStatus", "ECU confirmada \xB7 30 pontos");
          this.renderChart();
          this.renderEvidence(this.store.get());
          if (this.pendingSuggestion) {
            this.renderSuggestionFocus(this.pendingSuggestion);
            this.focusSuggestion(this.pendingSuggestion);
            this.prepareSuggestion(this.pendingSuggestion, true);
          } else {
            this.selectPoint(0);
          }
          if (this.view === "learning") this.renderLearning(this.store.get());
          this.store.patch({ curve: __spreadProps(__spreadValues({}, this.store.get().curve), { state: "ready", data: operation, status: {} }) });
          return;
        }
        if (this.writing) {
          const progress = Math.max(0, Math.min(100, finite(operation.progress) || finite(operation.writerProgress) || 0));
          const bar = document.getElementById("curveOperationProgress");
          if (bar) bar.style.width = "".concat(progress, "%");
          text("curveOperationTitle", operation.message || operation.writerMessage || "Backup \xB7 escrita \xB7 ACK \xB7 readback");
          if (!operation.busy) {
            this.writing = false;
            if (operation.state === "BATCH_CONFIRMED" && operation.readbackValid === true) {
              (_b = this.root) == null ? void 0 : _b.classList.remove("is-writing");
              (_c = this.root) == null ? void 0 : _c.classList.add("has-result");
              const result = document.getElementById("curveOperationResult");
              if (result) {
                result.dataset.level = "ok";
                result.querySelector("b").textContent = "Curva K confirmada pela ECU";
                result.querySelector("span").textContent = "ACK e readback completos. A curva ser\xE1 relida.";
              }
              this.data = null;
              this.proposals.clear();
              this.pendingSuggestion = null;
              this.startRead(true);
            } else {
              (_d = this.root) == null ? void 0 : _d.classList.remove("is-writing");
              (_e = this.root) == null ? void 0 : _e.classList.add("has-result");
              const result = document.getElementById("curveOperationResult");
              if (result) {
                result.dataset.level = "critical";
                result.querySelector("b").textContent = "A Curva K n\xE3o foi confirmada";
                result.querySelector("span").textContent = operation.error || operation.message || "A ECU n\xE3o confirmou toda a opera\xE7\xE3o. Releitura obrigat\xF3ria.";
              }
              this.data = null;
            }
          }
        }
      }
      points() {
        var _a;
        return Array.isArray((_a = this.data) == null ? void 0 : _a.points) ? this.data.points : [];
      }
      pointByIndex(index) {
        return this.points().find((item) => Number(item.index) === Number(index)) || null;
      }
      selectedPointIndices() {
        if (this.selectedIndices.size) return [...this.selectedIndices].sort((a, b) => a - b);
        return this.activeIndex === null ? [] : [this.activeIndex];
      }
      updateSelectionUi() {
        const count = this.selectedIndices.size;
        const mode = document.getElementById("curveSelectionMode");
        const countNode = document.getElementById("curveSelectionCount");
        if (mode) {
          mode.setAttribute("aria-pressed", String(this.selectionMode));
          mode.textContent = this.selectionMode ? "Sele\xE7\xE3o ON" : "Ativar sele\xE7\xE3o";
        }
        if (countNode) countNode.textContent = "".concat(this.selectionMode ? "Sele\xE7\xE3o ON" : "Sele\xE7\xE3o OFF", " \xB7 ").concat(count, " selecionado").concat(count === 1 ? "" : "s");
      }
      setSelectionMode(enabled) {
        this.selectionMode = enabled === true;
        this.dragSelecting = false;
        this.dragMoved = false;
        this.dragStartIndex = null;
        this.dragStartWasSelected = false;
        if (!this.selectionMode && this.selectedIndices.size > 1) {
          const keep = this.activeIndex !== null && this.selectedIndices.has(this.activeIndex) ? this.activeIndex : [...this.selectedIndices].at(-1);
          this.selectedIndices.clear();
          if (keep !== void 0 && keep !== null) this.selectedIndices.add(Number(keep));
        }
        this.updateSelectionUi();
        this.renderChart();
        return this.selectionMode;
      }
      refreshActiveEditor() {
        var _a, _b, _c;
        const point = this.activeIndex === null ? null : this.pointByIndex(this.activeIndex);
        if (!point) {
          text("curveActivePoint", "Selecione um ou v\xE1rios pontos");
          text("curveCurrentFactor", "\u2014");
          text("curveTargetNormalized", "Pr\xE9via calculada pelo Kotlin");
          const input2 = document.getElementById("curveTargetFactor");
          if (input2) input2.value = "";
          return;
        }
        const count = this.selectedIndices.size || 1;
        const suffix = count > 1 ? " \xB7 ".concat(count, " selecionados") : "";
        text("curveActivePoint", "Ponto ".concat(this.activeIndex + 1, " \xB7 ").concat(fmt(point.petrolMs, 2), " ms").concat(suffix));
        text("curveCurrentFactor", fmt(point.factor, 4));
        const input = document.getElementById("curveTargetFactor");
        if (input) input.value = String((_c = finite((_b = (_a = this.proposals.get(this.activeIndex)) == null ? void 0 : _a.targetFactor) != null ? _b : point.factor)) != null ? _c : "");
        this.renderLearningPointContext(this.store.get(), this.activeIndex);
      }
      selectOnly(index) {
        const point = this.pointByIndex(index);
        if (!point) return;
        this.selectedIndices.clear();
        this.selectedIndices.add(Number(point.index));
        this.activeIndex = Number(point.index);
        this.refreshActiveEditor();
        this.updateSelectionUi();
        this.renderChart();
      }
      selectPoint(index) {
        this.selectOnly(index);
      }
      toggleSelection(index) {
        var _a;
        const point = this.pointByIndex(index);
        if (!point) return;
        if (!this.selectionMode) {
          this.selectOnly(index);
          return;
        }
        const key = Number(point.index);
        if (this.selectedIndices.has(key)) this.selectedIndices.delete(key);
        else this.selectedIndices.add(key);
        if (this.selectedIndices.has(key)) this.activeIndex = key;
        else if (this.activeIndex === key) this.activeIndex = (_a = [...this.selectedIndices].at(-1)) != null ? _a : null;
        this.refreshActiveEditor();
        this.updateSelectionUi();
        this.renderChart();
      }
      clearSelection() {
        this.selectedIndices.clear();
        this.activeIndex = null;
        this.refreshActiveEditor();
        this.updateSelectionUi();
        this.renderChart();
      }
      nudgeActive(delta) {
        this.nudgeSelection(delta);
      }
      nudgeSelection(delta) {
        var _a, _b;
        const indices = this.selectedPointIndices();
        if (!indices.length || !delta) return;
        for (const index of indices) {
          const point = this.pointByIndex(index);
          const current = finite((_b = (_a = this.proposals.get(index)) == null ? void 0 : _a.targetFactor) != null ? _b : point == null ? void 0 : point.factor);
          if (current === null) continue;
          const requested = Math.max(0.6, Math.min(4, current + delta));
          const preview = this.api.previewCurvePoint(index, requested);
          if (!(preview == null ? void 0 : preview.ok)) continue;
          this.acceptPreview(preview, true);
        }
        this.refreshActiveEditor();
        this.renderChart();
        this.renderProposalList();
      }
      focusSuggestion(suggestion) {
        const changes = Array.isArray(suggestion == null ? void 0 : suggestion.curveChanges) ? suggestion.curveChanges : [];
        if (changes.length) {
          this.selectPoint(Number(changes[0].index));
          return this.points().find((item) => Number(item.index) === Number(changes[0].index)) || null;
        }
        const point = this.resolveSuggestionPoint(suggestion);
        if (point) this.selectPoint(Number(point.index));
        return point;
      }
      resolveSuggestionPoint(suggestion) {
        if (!suggestion) return null;
        const explicitIndex = finite(suggestion.index);
        if (explicitIndex !== null) {
          const exact = this.points().find((item) => Number(item.index) === Number(explicitIndex));
          if (exact) return exact;
        }
        const targetMs = finite(suggestion.petrolMs);
        if (targetMs === null || !this.points().length) return null;
        return this.points().slice().sort(
          (a, b) => Math.abs(Number(a.petrolMs) - targetMs) - Math.abs(Number(b.petrolMs) - targetMs)
        )[0] || null;
      }
      prepareActivePoint() {
        return this.prepareSelectionTarget();
      }
      prepareSelectionTarget() {
        var _a;
        const targetFactor = finite((_a = document.getElementById("curveTargetFactor")) == null ? void 0 : _a.value);
        if (targetFactor === null) {
          this.alert("Informe o fator K desejado.");
          return false;
        }
        return this.assignSelectionTarget(targetFactor);
      }
      assignSelectionTarget(targetFactor) {
        const indices = this.selectedPointIndices();
        if (!indices.length) {
          this.alert("Selecione pelo menos um ponto da Curva K.");
          return false;
        }
        if (finite(targetFactor) === null) {
          this.alert("Informe um fator K v\xE1lido.");
          return false;
        }
        let prepared = 0;
        for (const index of indices) {
          const preview = this.api.previewCurvePoint(index, targetFactor);
          if (!(preview == null ? void 0 : preview.ok)) continue;
          this.acceptPreview(preview, true);
          prepared += 1;
        }
        this.refreshActiveEditor();
        this.renderChart();
        this.renderProposalList();
        if (!prepared) this.alert("Nenhum ponto selecionado produziu uma pr\xE9via v\xE1lida.");
        return prepared > 0;
      }
      prepareSuggestion(suggestion = this.pendingSuggestion, silent = false) {
        if (!suggestion || !this.data) {
          if (!silent) this.alert("Aguarde a leitura da Curva K antes de preparar a sugest\xE3o.");
          return false;
        }
        const persistentChanges = Array.isArray(suggestion.curveChanges) ? suggestion.curveChanges : [];
        if (persistentChanges.length) {
          let changed = false;
          persistentChanges.forEach((change) => {
            const index = Number(change.index);
            const requested = finite(change.targetFactor != null ? change.targetFactor : change.after);
            const normalizedRaw = finite(change.targetRaw);
            if (!Number.isInteger(index) || requested === null || normalizedRaw === null) return;
            const preview = this.api.previewCurvePoint(index, requested);
            if (!(preview == null ? void 0 : preview.ok)) return;
            preview.preparedFromSuggestion = true;
            preview.suggestionId = suggestion.id || "";
            preview.targetRaw = normalizedRaw;
            this.acceptPreview(preview, true);
            changed = changed || preview.changed === true;
          });
          this.renderChart();
          this.renderProposalList();
          this.renderSuggestionFocus(suggestion, { changed });
          return changed;
        }
        if (!silent) this.alert("A sugest\xE3o ainda n\xE3o possui alvo K exato calculado pelo Kotlin.");
        return false;
      }
      acceptPreview(preview, deferRender = false) {
        const index = Number(preview.index);
        if (!Number.isInteger(index)) {
          this.alert("Pr\xE9via da Curva K sem \xEDndice v\xE1lido.");
          return;
        }
        if (!preview.changed) this.proposals.delete(index);
        else this.proposals.set(index, preview);
        const input = document.getElementById("curveTargetFactor");
        if (input && index === this.activeIndex && finite(preview.targetFactor) !== null) input.value = String(preview.targetFactor);
        if (index === this.activeIndex) text("curveTargetNormalized", preview.changed ? "".concat(fmt(preview.currentFactor, 4), " \u2192 ").concat(fmt(preview.targetFactor, 4)) : "Sem altera\xE7\xE3o");
        if (!deferRender) {
          this.renderChart();
          this.renderProposalList();
        }
      }
      renderChart() {
        const host = document.getElementById("curveChart");
        const points = this.points();
        if (!host) return;
        if (!points.length) {
          host.innerHTML = '<div class="chart-empty">Leia a Curva K para visualizar os 30 pontos.</div>';
          return;
        }
        const validIndices = new Set(points.map((item) => Number(item.index)));
        for (const index of [...this.selectedIndices]) if (!validIndices.has(Number(index))) this.selectedIndices.delete(index);
        if (this.activeIndex !== null && !validIndices.has(Number(this.activeIndex))) this.activeIndex = null;
        this.updateSelectionUi();
        const width = 920;
        const height = 350;
        const padX = 42;
        const padY = 34;
        const factors = points.map((item) => {
          var _a, _b;
          return finite((_b = (_a = this.proposals.get(Number(item.index))) == null ? void 0 : _a.targetFactor) != null ? _b : item.factor) || 0;
        });
        const min = Math.max(0.55, Math.min(...factors, ...points.map((item) => finite(item.factor) || 0)) - 0.08);
        const max = Math.max(min + 0.2, Math.max(...factors, ...points.map((item) => finite(item.factor) || 0)) + 0.08);
        const xFor = (index) => padX + index / Math.max(1, points.length - 1) * (width - padX * 2);
        const yFor = (factor) => height - padY - (factor - min) / (max - min) * (height - padY * 2);
        const actualPath = points.map((point, index) => "".concat(index ? "L" : "M", " ").concat(xFor(index).toFixed(1), " ").concat(yFor(Number(point.factor)).toFixed(1))).join(" ");
        const proposalPath = points.map((point, index) => {
          const proposal = this.proposals.get(Number(point.index));
          const value = proposal ? proposal.targetFactor : point.factor;
          return "".concat(index ? "L" : "M", " ").concat(xFor(index).toFixed(1), " ").concat(yFor(Number(value)).toFixed(1));
        }).join(" ");
        host.innerHTML = '<svg class="curve-svg" viewBox="0 0 '.concat(width, " ").concat(height, '" role="img" aria-label="Curva K com 30 pontos edit\xE1veis"><path class="curve-line actual" d="').concat(actualPath, '"></path><path class="curve-line proposal" d="').concat(proposalPath, '"></path>').concat(points.map((point, index) => {
          const active = Number(point.index) === this.activeIndex;
          const selected = this.selectedIndices.has(Number(point.index));
          const proposed = this.proposals.has(Number(point.index));
          const y = yFor(proposed ? this.proposals.get(Number(point.index)).targetFactor : point.factor).toFixed(1);
          const x = xFor(index).toFixed(1);
          const label = index % 5 === 0 || index === points.length - 1 ? '<text class="curve-point-label" x="'.concat(x, '" y="').concat(height - 8, '" text-anchor="middle">').concat(fmt(point.petrolMs, 1), "</text>") : "";
          return '<circle class="curve-point-hit" data-curve-index="'.concat(point.index, '" cx="').concat(x, '" cy="').concat(y, '" r="22" tabindex="0" role="button" aria-pressed="').concat(selected, '" aria-label="Ponto ').concat(Number(point.index) + 1, ", ").concat(fmt(point.petrolMs, 2), ' ms"></circle><circle class="curve-point ').concat(active ? "active" : "", " ").concat(selected ? "selected" : "", " ").concat(proposed ? "proposed" : "", '" data-curve-point="').concat(point.index, '" cx="').concat(x, '" cy="').concat(y, '" r="').concat(selected ? 9 : 7, '"></circle>').concat(label);
        }).join(""), "</svg>");
        const syncSelectionClasses = () => {
          host.querySelectorAll("[data-curve-index]").forEach((hit) => {
            const index = Number(hit.dataset.curveIndex);
            const selected = this.selectedIndices.has(index);
            hit.setAttribute("aria-pressed", String(selected));
            const dot = host.querySelector('[data-curve-point="'.concat(index, '"]'));
            dot == null ? void 0 : dot.classList.toggle("selected", selected);
            dot == null ? void 0 : dot.classList.toggle("active", index === this.activeIndex);
            dot == null ? void 0 : dot.setAttribute("r", selected ? "9" : "7");
          });
        };
        const finishDrag = () => {
          var _a;
          if (!this.dragSelecting) return;
          if (!this.dragMoved && this.dragStartWasSelected && this.dragStartIndex !== null) {
            this.selectedIndices.delete(Number(this.dragStartIndex));
            if (this.activeIndex === Number(this.dragStartIndex)) this.activeIndex = (_a = [...this.selectedIndices].at(-1)) != null ? _a : null;
            this.refreshActiveEditor();
          }
          this.dragSelecting = false;
          this.dragMoved = false;
          this.dragStartIndex = null;
          this.dragStartWasSelected = false;
          this.updateSelectionUi();
          syncSelectionClasses();
        };
        host.querySelectorAll("[data-curve-index]").forEach((point) => {
          const index = () => Number(point.dataset.curveIndex);
          point.addEventListener("pointerdown", (event) => {
            if (typeof event.button === "number" && event.button !== 0) return;
            event.preventDefault();
            const key = index();
            if (!this.selectionMode) {
              this.selectedIndices.clear();
              this.selectedIndices.add(key);
              this.activeIndex = key;
              this.refreshActiveEditor();
              this.updateSelectionUi();
              syncSelectionClasses();
              return;
            }
            this.dragSelecting = true;
            this.dragMoved = false;
            this.dragStartIndex = key;
            this.dragStartWasSelected = this.selectedIndices.has(key);
            this.selectedIndices.add(key);
            this.activeIndex = key;
            this.refreshActiveEditor();
            this.updateSelectionUi();
            syncSelectionClasses();
          });
          point.addEventListener("pointerenter", () => {
            if (!this.selectionMode || !this.dragSelecting) return;
            const key = index();
            if (key !== this.dragStartIndex) this.dragMoved = true;
            this.selectedIndices.add(key);
            this.activeIndex = key;
            this.refreshActiveEditor();
            this.updateSelectionUi();
            syncSelectionClasses();
          });
          point.addEventListener("pointerup", finishDrag);
          point.addEventListener("pointercancel", finishDrag);
          point.addEventListener("keydown", (event) => {
            if (event.key === "Enter" || event.key === " ") {
              event.preventDefault();
              this.toggleSelection(index());
            }
          });
        });
        host.onpointerup = finishDrag;
        host.onpointercancel = finishDrag;
      }
      directComparisons(state) {
        const maps = state.learning || {};
        return (Array.isArray(maps.comparisons) ? maps.comparisons : []).map((item) => {
          var _a, _b, _c, _d, _e;
          return {
            raw: item,
            targetMs: comparisonTargetMs(item),
            observedMs: comparisonObservedMs(item),
            error: comparisonError(item),
            quality: comparisonQuality(item),
            rpm: finite((_b = (_a = item == null ? void 0 : item.observed_pair) == null ? void 0 : _a.rpm) != null ? _b : item == null ? void 0 : item.rpm),
            mapBar: finite((_e = (_d = (_c = item == null ? void 0 : item.observed_pair) == null ? void 0 : _c.map_bar) != null ? _d : item == null ? void 0 : item.map_bar) != null ? _e : item == null ? void 0 : item.mapBar)
          };
        }).filter((item) => item.targetMs !== null && item.observedMs !== null && item.error !== null).sort((a, b) => a.targetMs - b.targetMs);
      }
      renderLearning(state) {
        var _a, _b, _c;
        const host = document.getElementById("curveLearningChart");
        const summaryHost = document.getElementById("curveLearningSummary");
        if (!host || !summaryHost) return;
        const comparisons = this.directComparisons(state);
        const calibrationState = state.calibrationState || {};
        const latestComparison = calibrationState.latestComparison && typeof calibrationState.latestComparison === "object" ? calibrationState.latestComparison : null;
        const proposal = calibrationState.proposal && typeof calibrationState.proposal === "object" ? calibrationState.proposal : {};
        const currentPoints = this.points();
        const signature = JSON.stringify({
          comparisons: comparisons.map((item) => [item.targetMs, item.observedMs, item.error, item.quality]),
          latestComparison,
          proposal,
          current: currentPoints.map((item) => [item.index, item.petrolMs, item.factor])
        });
        if (signature === this.learningSignature) return;
        this.learningSignature = signature;
        const heading = (_a = this.root) == null ? void 0 : _a.querySelector(".global-learning-surface .surface-heading h3");
        if (heading) heading.textContent = "Desvio medido \xD7 Curva K";
        const legend = (_b = this.root) == null ? void 0 : _b.querySelector(".global-learning-surface .global-legend");
        if (legend) legend.innerHTML = "<span>pares f\xEDsicos medidos</span><span>Curva K atual da ECU</span>";
        const width = 920;
        const height = 180;
        const px = 42;
        const py = 22;
        const targetValues = comparisons.map((item) => item.targetMs).concat(currentPoints.map((item) => finite(item.petrolMs)).filter((value) => value !== null));
        const minMs = targetValues.length ? Math.min(...targetValues) : 0;
        const maxMs = targetValues.length ? Math.max(...targetValues) : 1;
        const xForMs = (value) => px + (Number(value) - minMs) / Math.max(0.01, maxMs - minMs) * (width - px * 2);
        const errors = comparisons.map((item) => item.error);
        const maxAbs = Math.max(3, ...errors.map(Math.abs));
        const errorY = (value) => height / 2 - Number(value || 0) / maxAbs * (height / 2 - py);
        const factorValues = currentPoints.map((item) => finite(item.factor)).filter((value) => value !== null);
        const minFactor = factorValues.length ? Math.min(...factorValues) - 0.05 : 0.8;
        const maxFactor = factorValues.length ? Math.max(...factorValues) + 0.05 : 1.2;
        const factorY = (value) => height - py - (Number(value) - minFactor) / Math.max(0.01, maxFactor - minFactor) * (height - py * 2);
        const actualPath = currentPoints.filter((item) => finite(item.petrolMs) !== null && finite(item.factor) !== null).map((item, pos) => "".concat(pos ? "L" : "M", " ").concat(xForMs(item.petrolMs).toFixed(1), " ").concat(factorY(item.factor).toFixed(1))).join(" ");
        host.innerHTML = '<div class="global-learning-stack">\n        <section class="global-error-chart"><small class="global-chart-label">DESVIO MEDIDO \xB7 alvo 0%</small><svg viewBox="0 0 '.concat(width, " ").concat(height, '" role="img" aria-label="Pares f\xEDsicos gasolina e GNV por Petrol Inj."><line x1="').concat(px, '" y1="').concat(height / 2, '" x2="').concat(width - px, '" y2="').concat(height / 2, '" class="learn-grid-line"></line>').concat(comparisons.map((item) => '<circle class="'.concat(Math.abs(item.error) <= 1.5 ? "learned-petrol-point" : "learned-cng-point", '" cx="').concat(xForMs(item.targetMs).toFixed(1), '" cy="').concat(errorY(item.error).toFixed(1), '" r="').concat(Math.max(3, 3 + item.quality * 3).toFixed(1), '"><title>').concat(fmt(item.targetMs, 2), " \u2192 ").concat(fmt(item.observedMs, 2), " ms \xB7 ").concat(item.error > 0 ? "+" : "").concat(fmt(item.error, 2), "%</title></circle>")).join(""), '</svg></section>\n        <section class="global-k-chart"><small class="global-chart-label">CURVA K \xB7 readback atual</small><svg viewBox="0 0 ').concat(width, " ").concat(height, '" role="img" aria-label="Curva K atual confirmada pela ECU">').concat(actualPath ? '<path class="curve-line actual" d="'.concat(actualPath, '"></path>') : "").concat(currentPoints.filter((item) => finite(item.petrolMs) !== null && finite(item.factor) !== null).map((item) => '<circle class="curve-point '.concat(Number(item.index) === this.activeIndex ? "active" : "", '" cx="').concat(xForMs(item.petrolMs).toFixed(1), '" cy="').concat(factorY(item.factor).toFixed(1), '" r="').concat(Number(item.index) === this.activeIndex ? 7 : 4, '"></circle>')).join(""), "</svg></section>\n      </div>");
        const gain = finite(proposal.actuatorGain);
        const multiplier = finite(proposal.correctionMultiplier);
        const proposalState = String(proposal.state || (proposal.available === false ? "WAITING_FOR_EQUIVALENT_FUEL_EVIDENCE" : "MEASURE_ACTUATOR_GAIN"));
        const proposalText = multiplier !== null ? "multiplicador ".concat(fmt(multiplier, 4), " dispon\xEDvel para revis\xE3o manual") : gain !== null ? "ganho ".concat(fmt(gain, 4), " observado; sem multiplicador emitido") : "ganho causal ainda n\xE3o medido; nenhum alvo K inventado";
        summaryHost.innerHTML = '<div class="editor-heading"><div><small>AUTORIDADE \xDANICA</small><h3>BlueCausalEngine</h3></div></div><div class="global-summary-grid"><div><small>PARES MEDIDOS</small><b>'.concat(comparisons.length, "</b></div><div><small>GASOLINA</small><b>").concat(Number(calibrationState.petrolEvidence || 0), "</b></div><div><small>GNV ATUAL</small><b>").concat(Number(calibrationState.activeCngEvidence || 0), "</b></div><div><small>ATIVOS BLUE</small><b>").concat(Number(calibrationState.activeComparisons || 0), '</b></div></div><div id="curveLearningPointContext" class="global-summary-list"></div><p class="empty-copy">').concat(escapeHtml(proposalState), " \xB7 ").concat(escapeHtml(proposalText), ". A medi\xE7\xE3o n\xE3o \xE9 convertida em corre\xE7\xE3o pela WebView.</p>");
        this.renderLearningPointContext(state, (_c = this.activeIndex) != null ? _c : 0);
      }
      renderLearningPointContext(state, index) {
        const host = document.getElementById("curveLearningPointContext");
        if (!host) return;
        const current = this.points().find((item) => Number(item.index) === Number(index)) || {};
        const targetMs = finite(current.petrolMs);
        const comparisons = this.directComparisons(state);
        const nearest = targetMs === null || !comparisons.length ? null : comparisons.slice().sort((a, b) => Math.abs(a.targetMs - targetMs) - Math.abs(b.targetMs - targetMs))[0];
        const manual = this.proposals.get(Number(index));
        const calibrationState = state.calibrationState || {};
        const latestComparison = calibrationState.latestComparison && typeof calibrationState.latestComparison === "object" ? calibrationState.latestComparison : null;
        const proposal = calibrationState.proposal && typeof calibrationState.proposal === "object" ? calibrationState.proposal : {};
        const multiplier = finite(proposal.correctionMultiplier);
        const nearestCopy = nearest ? "medido mais pr\xF3ximo ".concat(fmt(nearest.targetMs, 2), " \u2192 ").concat(fmt(nearest.observedMs, 2), " ms \xB7 ").concat(nearest.error > 0 ? "+" : "").concat(fmt(nearest.error, 2), "%") : "nenhum par f\xEDsico medido pr\xF3ximo";
        const blueCopy = latestComparison ? "\xFAltimo Blue ".concat(fmt(latestComparison.petrolReferenceMs, 2), " \u2192 ").concat(fmt(latestComparison.petrolOnCngMs, 2), " ms \xB7 ").concat(fmt(latestComparison.errorPercent, 2), "%") : "Blue ainda sem compara\xE7\xE3o reconciliada";
        host.innerHTML = "<div><span>Ponto ".concat(Number(index) + 1, " \xB7 ").concat(fmt(current.petrolMs, 2), " ms</span><b>").concat(escapeHtml(nearestCopy), "</b><small>").concat(escapeHtml(blueCopy), "</small></div><div><span>K atual</span><b>").concat(fmt(current.factor, 4), "</b><small>").concat(manual ? "pr\xE9via manual ".concat(fmt(manual.targetFactor, 4)) : multiplier !== null ? "multiplicador Blue ".concat(fmt(multiplier, 4), " \xB7 sem alvo de ponto autom\xE1tico") : "sem alvo autom\xE1tico", "</small></div>");
      }
      renderProposalList() {
        const host = document.getElementById("curveProposalList");
        if (!host) return;
        const items = [...this.proposals.values()].sort((a, b) => Number(a.index) - Number(b.index));
        host.innerHTML = items.length ? items.map((item) => "<div><span>".concat(fmt(item.petrolMs, 2), " ms</span><b>").concat(fmt(item.currentFactor, 4), " \u2192 ").concat(fmt(item.targetFactor, 4), "</b><small>").concat(item.deltaPercent > 0 ? "+" : "").concat(fmt(item.deltaPercent, 1), "%</small></div>")).join("") : "<p>Nenhum ponto preparado.</p>";
        const review = document.getElementById("curveReviewButton");
        if (review) {
          review.disabled = items.length === 0;
          review.textContent = items.length ? "Revisar ".concat(items.length, " ponto").concat(items.length === 1 ? "" : "s") : "Prepare pontos";
        }
      }
      renderEvidence(state) {
        const host = document.getElementById("curveEvidenceList");
        if (!host) return;
        const comparisons = this.directComparisons(state);
        const calibrationState = state.calibrationState || {};
        const latestComparison = calibrationState.latestComparison && typeof calibrationState.latestComparison === "object" ? calibrationState.latestComparison : null;
        const proposal = calibrationState.proposal && typeof calibrationState.proposal === "object" ? calibrationState.proposal : {};
        const multiplier = finite(proposal.correctionMultiplier);
        host.innerHTML = '<div class="curve-evidence-summary"><div class="evidence-stat"><b>'.concat(comparisons.length, '</b><span>pares f\xEDsicos medidos</span></div><div class="evidence-stat"><b>').concat(Number(calibrationState.petrolEvidence || 0), '</b><span>evid\xEAncias gasolina</span></div><div class="evidence-stat"><b>').concat(Number(calibrationState.activeCngEvidence || 0), '</b><span>evid\xEAncias GNV atuais</span></div><div class="evidence-stat"><b>').concat(Number(calibrationState.activeComparisons || 0), '</b><span>compara\xE7\xF5es Blue ativas</span></div></div><div class="curve-native-explanation"><header><div><small>EVID\xCANCIA F\xCDSICA</small><h3>Gasolina \xD7 GNV medidos</h3></div><span>BlueCausalEngine</span></header><div class="global-summary-list">').concat(comparisons.slice(-12).reverse().map((item) => "<div><span>".concat(fmt(item.rpm, 0), " RPM \xB7 MAP ").concat(fmt(item.mapBar, 3), " bar</span><b>").concat(fmt(item.targetMs, 2), " \u2192 ").concat(fmt(item.observedMs, 2), " ms</b><small>").concat(item.error > 0 ? "+" : "").concat(fmt(item.error, 2), "% \xB7 qualidade ").concat(Math.round(item.quality * 100), "%</small></div>")).join("") || '<p class="empty-copy">Ainda sem par f\xEDsico gasolina \xD7 GNV.</p>', "</div><p>").concat(latestComparison ? "\xDAltima compara\xE7\xE3o reconciliada: ".concat(fmt(latestComparison.errorPercent, 2), "%. ") : "").concat(multiplier !== null ? "O n\xFAcleo emitiu multiplicador ".concat(fmt(multiplier, 4), " para revis\xE3o manual.") : "Estado ".concat(escapeHtml(proposal.state || "MEASURE_ACTUATOR_GAIN"), ": nenhuma corre\xE7\xE3o exata \xE9 inventada pela interface."), "</p></div>");
      }
      renderSuggestionFocus(suggestion, preparedPreview = null) {
        var _a;
        const host = document.getElementById("curveSuggestionFocus");
        if (!host || !suggestion) return;
        this.pendingSuggestion = suggestion;
        host.hidden = false;
        const persistentChanges = Array.isArray(suggestion.curveChanges) ? suggestion.curveChanges : [];
        const prepared = (preparedPreview == null ? void 0 : preparedPreview.changed) === true || persistentChanges.some((change) => this.proposals.has(Number(change.index)));
        const label = persistentChanges.length ? "".concat(persistentChanges.length, " ponto").concat(persistentChanges.length === 1 ? "" : "s", " da Curva K") : "aguardando alvo exato do Kotlin";
        host.innerHTML = "<b>".concat(prepared ? "Sugest\xE3o preparada para revis\xE3o" : "Sugest\xE3o global em revis\xE3o", "</b><span>").concat(escapeHtml(label), "</span><small>").concat(escapeHtml(suggestion.rationale || suggestion.reason || suggestion.explanation || ""), '</small><button type="button" data-curve-prepare-suggestion ').concat(this.data && persistentChanges.length ? "" : "disabled", ">").concat(prepared ? "Repreparar sugest\xE3o" : "Preparar sugest\xE3o", "</button><small>").concat(prepared ? "Revise o antes/depois; nenhuma escrita foi iniciada." : "A pr\xE9via \xE9 normalizada pelo Kotlin. N\xE3o grava na ECU.", "</small>");
        (_a = host.querySelector("[data-curve-prepare-suggestion]")) == null ? void 0 : _a.addEventListener("click", () => this.prepareSuggestion(suggestion));
      }
      openReview() {
        var _a;
        if (!this.proposals.size) return;
        const host = document.getElementById("curveReviewList");
        const items = [...this.proposals.values()].sort((a, b) => Number(a.index) - Number(b.index));
        if (host) host.innerHTML = items.map((item) => "<div><span>Ponto ".concat(Number(item.index) + 1, " \xB7 ").concat(fmt(item.petrolMs, 2), " ms</span><b>").concat(fmt(item.currentFactor, 4), " \u2192 ").concat(fmt(item.targetFactor, 4), "</b></div>")).join("");
        text("curveReviewCount", "".concat(items.length, " ponto").concat(items.length === 1 ? "" : "s"));
        const button = document.getElementById("curveWriteButton");
        if (button) button.textContent = "Gravar ".concat(items.length, " ponto").concat(items.length === 1 ? "" : "s", " na ECU");
        (_a = this.root) == null ? void 0 : _a.classList.add("is-reviewing");
      }
      closeReview() {
        var _a;
        (_a = this.root) == null ? void 0 : _a.classList.remove("is-reviewing", "is-writing", "has-result");
      }
      writeReview() {
        var _a, _b;
        const points = [...this.proposals.values()].map((item) => ({ index: Number(item.index), currentRaw: Number(item.currentRaw), targetRaw: Number(item.targetRaw) }));
        if (!points.length) return;
        const result = this.api.writeCurve(points, "Ajuste manual confirmado na UI clean-slate");
        if (!(result == null ? void 0 : result.ok) || !(result == null ? void 0 : result.started)) {
          this.alert((result == null ? void 0 : result.error) || "A escrita da Curva K n\xE3o iniciou.");
          return;
        }
        this.writing = true;
        (_a = this.root) == null ? void 0 : _a.classList.remove("is-reviewing");
        (_b = this.root) == null ? void 0 : _b.classList.add("is-writing");
        text("curveOperationTitle", "Escrita manual da Curva K");
        const bar = document.getElementById("curveOperationProgress");
        if (bar) bar.style.width = "0%";
      }
      alert(message) {
        this.store.patch({ alert: { level: "warning", message: String(message || "Opera\xE7\xE3o indispon\xEDvel") } });
      }
    }
    ns.CurveScreen = CurveScreen;
  })(typeof window !== "undefined" ? window : globalThis);
})();
