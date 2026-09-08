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
      if (node) node.textContent = value == null ? "\u2014" : String(value);
    }
    class MapScreen {
      constructor(store, api, router) {
        this.store = store;
        this.api = api;
        this.router = router || null;
        this.root = document.querySelector('[data-screen="map"]');
        this.host = document.getElementById("mapGrid");
        this.editor = new root.OmegasMapEditor.MapEditor();
        this.cells = /* @__PURE__ */ new Map();
        this.rowHeaders = [];
        this.columnHeaders = [];
        this.reading = false;
        this.readRequested = false;
        this.dragStart = null;
        this.review = null;
        this.lastOperationState = "";
        this.pendingContext = null;
        this.liveContext = null;
        this.ensureContextChrome();
        this.bind();
      }
      key(row, column) {
        return "".concat(row, ":").concat(column);
      }
      ensureContextChrome() {
        var _a;
        const intro = (_a = this.root) == null ? void 0 : _a.querySelector(".page-intro.action-intro");
        const actions = intro == null ? void 0 : intro.querySelector(".inline-actions");
        if (intro && !document.getElementById("mapLiveLabel")) {
          const live = document.createElement("div");
          live.className = "map-live-condition";
          live.innerHTML = '<small>AGORA</small><b id="mapLiveLabel">Aguardando condi\xE7\xE3o v\xE1lida</b><span id="mapLiveCell">c\xE9lula \u2014</span>';
          intro.insertBefore(live, actions || null);
        }
        if (actions && !document.getElementById("mapBackToLearning")) {
          const back = document.createElement("button");
          back.id = "mapBackToLearning";
          back.type = "button";
          back.className = "quiet-button";
          back.textContent = "Voltar ao aprendizado";
          actions.prepend(back);
        }
      }
      bind() {
        var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j;
        (_a = document.getElementById("mapReadButton")) == null ? void 0 : _a.addEventListener("click", () => this.startRead());
        (_b = document.getElementById("mapBackToLearning")) == null ? void 0 : _b.addEventListener("click", () => {
          var _a2;
          return (_a2 = this.router) == null ? void 0 : _a2.navigate("learning");
        });
        (_c = document.getElementById("mapSelectAll")) == null ? void 0 : _c.addEventListener("click", () => {
          try {
            this.editor.selectAll();
            this.review = null;
            this.renderEditor();
            this.refreshSelectionPreview();
          } catch (error) {
            this.alert(error.message);
          }
        });
        (_d = document.getElementById("mapClearSelection")) == null ? void 0 : _d.addEventListener("click", () => {
          this.editor.clearSelection();
          this.review = null;
          this.renderEditor();
          this.renderGrid();
        });
        (_e = document.getElementById("mapAdjustmentMode")) == null ? void 0 : _e.addEventListener("change", () => this.applyAdjustment());
        (_f = document.getElementById("mapAdjustmentValue")) == null ? void 0 : _f.addEventListener("input", () => this.applyAdjustment());
        document.querySelectorAll("[data-map-nudge]").forEach((button) => button.addEventListener("click", () => {
          const input = document.getElementById("mapAdjustmentValue");
          if (!input) return;
          input.value = String((finite(input.value) || 0) + Number(button.dataset.mapNudge || 0));
          this.applyAdjustment();
        }));
        (_g = document.getElementById("mapReviewButton")) == null ? void 0 : _g.addEventListener("click", () => this.openReview());
        (_h = document.getElementById("mapReviewBack")) == null ? void 0 : _h.addEventListener("click", () => this.closeReview());
        (_i = document.getElementById("mapWriteButton")) == null ? void 0 : _i.addEventListener("click", () => this.writeReview());
        (_j = document.getElementById("mapDismissResult")) == null ? void 0 : _j.addEventListener("click", () => this.closeReview());
      }
      onEnter(context) {
        var _a;
        this.pendingContext = context || null;
        (_a = this.root) == null ? void 0 : _a.classList.toggle("from-learning", (context == null ? void 0 : context.origin) === "learning");
        if (!this.editor.hasMap() && !this.reading) {
          this.startRead(true);
          return;
        }
        if (this.editor.hasMap()) this.applyContext(this.pendingContext);
      }
      startRead(automatic) {
        if (this.reading) return;
        const result = this.api.startMapRead();
        if (!(result == null ? void 0 : result.ok) || !(result == null ? void 0 : result.started)) {
          this.alert((result == null ? void 0 : result.error) || "N\xE3o foi poss\xEDvel iniciar a leitura do Mapa K.");
          return;
        }
        this.reading = true;
        this.readRequested = true;
        this.review = null;
        this.editor.reset();
        this.cells.clear();
        this.rowHeaders = [];
        this.columnHeaders = [];
        if (this.host) this.host.innerHTML = '<div class="map-empty-state"><div class="spinner"></div><b>Lendo Mapa K da ECU</b><span>13 linhas f\xEDsicas \xB7 somente leitura</span></div>';
        text("mapSourceStatus", automatic ? "Leitura autom\xE1tica em andamento" : "Relendo diretamente da ECU");
        this.store.patch({ map: __spreadProps(__spreadValues({}, this.store.get().map), { state: "reading", data: null, selection: 0, review: null }) });
      }
      poll() {
        if (this.reading) {
          const result = this.api.mapReadResult();
          if (!(result == null ? void 0 : result.busy) && (result == null ? void 0 : result.state) !== "READING") {
            this.reading = false;
            if (!(result == null ? void 0 : result.ok) || (result == null ? void 0 : result.state) === "FAILED") {
              this.editor.reset();
              this.alert((result == null ? void 0 : result.error) || "Falha ao ler o Mapa K.");
              text("mapSourceStatus", "Mapa n\xE3o confirmado");
            } else {
              try {
                this.editor.load(result);
                this.buildGrid();
                this.renderGrid();
                this.renderEditor();
                text("mapSourceStatus", "ECU confirmada \xB7 ".concat(result.writableCells || 144, " c\xE9lulas grav\xE1veis"));
                this.store.patch({ map: __spreadProps(__spreadValues({}, this.store.get().map), { state: "ready", data: result, selection: 0, review: null }) });
                this.applyContext(this.pendingContext || this.store.get().routeContext);
              } catch (error) {
                this.alert(error.message);
              }
            }
          }
        }
        this.pollWrite();
      }
      buildGrid() {
        if (!this.host || !this.editor.hasMap()) return;
        this.host.innerHTML = "";
        this.cells.clear();
        this.rowHeaders = [];
        this.columnHeaders = [];
        const snapshot = this.editor.snapshot();
        const table = document.createElement("div");
        table.className = "map-k-grid map-k-grid-with-axes";
        table.style.setProperty("--map-columns", "12");
        const corner = document.createElement("div");
        corner.className = "map-axis-corner";
        corner.innerHTML = "<small>Petrol Inj.</small><b>ms \\ RPM</b>";
        table.appendChild(corner);
        for (let column = 0; column < 12; column += 1) {
          const header = document.createElement("button");
          header.type = "button";
          header.className = "map-axis-header map-rpm-header";
          header.dataset.selectColumn = String(column);
          header.innerHTML = "<small>RPM</small><b>".concat(Math.round(snapshot.axes.rpmBins[column] || 0).toLocaleString("pt-BR"), "</b>");
          header.title = "Selecionar ou desmarcar toda esta faixa de RPM";
          this.columnHeaders.push(header);
          table.appendChild(header);
        }
        for (let row = 0; row < 12; row += 1) {
          const rowHeader = document.createElement("button");
          rowHeader.type = "button";
          rowHeader.className = "map-axis-header map-ms-header";
          rowHeader.dataset.selectRow = String(row);
          rowHeader.innerHTML = "<small>Petrol Inj.</small><b>".concat(fmt(snapshot.axes.petrolBins[row], 1), " ms</b>");
          rowHeader.title = "Selecionar ou desmarcar toda esta faixa de Petrol Inj.";
          this.rowHeaders.push(rowHeader);
          table.appendChild(rowHeader);
          for (let column = 0; column < 12; column += 1) {
            const cell = document.createElement("button");
            cell.type = "button";
            cell.className = "map-k-cell";
            cell.dataset.row = String(row);
            cell.dataset.column = String(column);
            cell.dataset.key = this.key(row, column);
            cell.setAttribute("aria-label", "".concat(fmt(snapshot.axes.petrolBins[row], 1), " ms, ").concat(Math.round(snapshot.axes.rpmBins[column] || 0), " RPM, K ").concat(snapshot.rows[row][column]));
            cell.innerHTML = "<b>".concat(snapshot.rows[row][column], "</b><span></span>");
            this.cells.set(this.key(row, column), cell);
            table.appendChild(cell);
          }
        }
        this.host.appendChild(table);
        const technical = document.createElement("div");
        technical.className = "technical-row-note";
        technical.innerHTML = "<b>Linha t\xE9cnica 0C protegida</b><span>Vis\xEDvel ao protocolo, fora de qualquer sele\xE7\xE3o em massa e fora da escrita manual.</span>";
        this.host.appendChild(technical);
        table.addEventListener("click", (event) => {
          const columnHeader = event.target.closest("[data-select-column]");
          const rowHeader = event.target.closest("[data-select-row]");
          if (columnHeader) {
            try {
              this.editor.toggleColumn(Number(columnHeader.dataset.selectColumn));
              this.review = null;
              this.renderEditor();
              this.refreshSelectionPreview();
            } catch (error) {
              this.alert(error.message);
            }
            return;
          }
          if (rowHeader) {
            try {
              this.editor.toggleRow(Number(rowHeader.dataset.selectRow));
              this.review = null;
              this.renderEditor();
              this.refreshSelectionPreview();
            } catch (error) {
              this.alert(error.message);
            }
          }
        });
        table.addEventListener("pointerdown", (event) => {
          var _a;
          const cell = event.target.closest(".map-k-cell");
          if (!cell) return;
          this.dragStart = { row: Number(cell.dataset.row), column: Number(cell.dataset.column) };
          try {
            (_a = cell.setPointerCapture) == null ? void 0 : _a.call(cell, event.pointerId);
          } catch (_) {
          }
        });
        table.addEventListener("pointerup", (event) => {
          const cell = event.target.closest(".map-k-cell");
          if (!cell || !this.dragStart) return;
          const end = { row: Number(cell.dataset.row), column: Number(cell.dataset.column) };
          try {
            if (end.row === this.dragStart.row && end.column === this.dragStart.column) this.editor.toggle(end.row, end.column);
            else this.editor.selectRange(this.dragStart.row, this.dragStart.column, end.row, end.column, true);
            this.review = null;
            this.renderEditor(end.row, end.column);
            this.refreshSelectionPreview();
          } catch (error) {
            this.alert(error.message);
          }
          this.dragStart = null;
        });
      }
      refreshSelectionPreview() {
        var _a;
        if (!this.editor.selectionCount()) {
          this.renderGrid();
          return;
        }
        const value = finite((_a = document.getElementById("mapAdjustmentValue")) == null ? void 0 : _a.value);
        if (value === null) {
          this.renderGrid();
          return;
        }
        this.applyAdjustment();
      }
      applyAdjustment() {
        var _a, _b;
        if (!this.editor.hasMap()) return;
        const mode = ((_a = document.getElementById("mapAdjustmentMode")) == null ? void 0 : _a.value) || "percent";
        const value = finite((_b = document.getElementById("mapAdjustmentValue")) == null ? void 0 : _b.value);
        if (value === null) return;
        try {
          this.editor.setAdjustment(mode, value);
          if (this.editor.selectionCount() > 0) {
            const preview = this.api.previewMapAdjustment(this.editor.selectedCells(), mode, value);
            if (!(preview == null ? void 0 : preview.ok) || !Array.isArray(preview.items)) throw new Error((preview == null ? void 0 : preview.error) || "Pr\xE9via Kotlin do Mapa K indispon\xEDvel.");
            this.editor.applyNativePreview(preview.items);
          }
          this.review = null;
          this.renderGrid();
          this.renderEditor();
        } catch (error) {
          this.alert(error.message);
        }
      }
      renderGrid() {
        if (!this.editor.hasMap()) return;
        const snapshot = this.editor.snapshot();
        let previewItems = /* @__PURE__ */ new Map();
        if (this.editor.selectionCount() > 0) {
          try {
            previewItems = new Map(this.editor.buildReview().items.map((item) => [this.key(item.row, item.column), item]));
          } catch (_) {
          }
        }
        this.cells.forEach((cell, cellKey) => {
          const row = Number(cell.dataset.row);
          const column = Number(cell.dataset.column);
          const selected = this.editor.isSelected(row, column);
          const preview = previewItems.get(cellKey);
          cell.classList.toggle("selected", selected);
          cell.classList.toggle("preview", !!preview);
          const valueNode = cell.querySelector("b");
          const deltaNode = cell.querySelector("span");
          if (valueNode) valueNode.textContent = preview ? String(preview.target) : String(snapshot.rows[row][column]);
          if (deltaNode) deltaNode.textContent = preview ? "".concat(preview.current, "\u2192").concat(preview.target) : "";
        });
        this.columnHeaders.forEach((header, column) => {
          const all = Array.from({ length: 12 }, (_, row) => this.editor.isSelected(row, column)).every(Boolean);
          header.classList.toggle("selected", all);
        });
        this.rowHeaders.forEach((header, row) => {
          const all = Array.from({ length: 12 }, (_, column) => this.editor.isSelected(row, column)).every(Boolean);
          header.classList.toggle("selected", all);
        });
      }
      renderEditor(activeRow, activeColumn) {
        const count = this.editor.selectionCount();
        text("mapSelectionCount", "".concat(count, " selecionada").concat(count === 1 ? "" : "s"));
        const button = document.getElementById("mapReviewButton");
        if (button) {
          button.disabled = count === 0;
          button.textContent = count ? "Revisar ".concat(count, " altera\xE7\xE3o").concat(count === 1 ? "" : "\xF5es") : "Selecione c\xE9lulas";
        }
        if (Number.isInteger(activeRow) && Number.isInteger(activeColumn) && this.editor.hasMap()) {
          const snapshot = this.editor.snapshot();
          text("mapActiveCell", "".concat(fmt(snapshot.axes.petrolBins[activeRow], 1), " ms \xB7 ").concat(snapshot.axes.rpmBins[activeColumn], " RPM"));
        }
        this.store.patch({ map: __spreadProps(__spreadValues({}, this.store.get().map), { selection: count, review: this.review }) });
      }
      renderLiveContext(context) {
        this.liveContext = context || null;
        text("mapLiveLabel", (context == null ? void 0 : context.label) || "Aguardando condi\xE7\xE3o v\xE1lida");
        const cell = context && Number.isInteger(context.row) && Number.isInteger(context.column) ? "c\xE9lula ".concat(context.row + 1, "\xD7").concat(context.column + 1) : "c\xE9lula \u2014";
        text("mapLiveCell", cell);
      }
      openReview() {
        var _a, _b;
        try {
          if (((_a = this.editor.targetOverrides) == null ? void 0 : _a.size) !== this.editor.selectionCount()) this.applyAdjustment();
          this.review = this.editor.buildReview();
        } catch (error) {
          this.alert(error.message);
          return;
        }
        const reviewHost = document.getElementById("mapReviewList");
        if (reviewHost) {
          const first = this.review.items.slice(0, 16);
          reviewHost.innerHTML = first.map((item) => "<div><span>".concat(fmt(item.petrolMs, 1), " ms \xB7 ").concat(Math.round(item.rpm).toLocaleString("pt-BR"), " RPM</span><b>").concat(item.current, " \u2192 ").concat(item.target, "</b></div>")).join("") + (this.review.items.length > first.length ? "<p>+ ".concat(this.review.items.length - first.length, " altera\xE7\xF5es na mesma inten\xE7\xE3o humana</p>") : "");
        }
        text("mapReviewCount", "".concat(this.review.count, " altera\xE7\xE3o").concat(this.review.count === 1 ? "" : "\xF5es"));
        const write = document.getElementById("mapWriteButton");
        if (write) write.textContent = "Gravar ".concat(this.review.count, " altera\xE7\xE3o").concat(this.review.count === 1 ? "" : "\xF5es", " na ECU");
        (_b = this.root) == null ? void 0 : _b.classList.add("is-reviewing");
        this.store.patch({ map: __spreadProps(__spreadValues({}, this.store.get().map), { review: this.review }) });
      }
      closeReview() {
        var _a;
        (_a = this.root) == null ? void 0 : _a.classList.remove("is-reviewing", "is-writing", "has-result");
        this.review = null;
        this.renderEditor();
      }
      writeReview() {
        var _a, _b, _c, _d;
        if (!((_b = (_a = this.review) == null ? void 0 : _a.items) == null ? void 0 : _b.length)) return;
        const result = this.api.writeMap(this.review.items, 3, 150, "Ajuste manual confirmado na UI clean-slate");
        if (!(result == null ? void 0 : result.ok) || !(result == null ? void 0 : result.started)) {
          this.alert((result == null ? void 0 : result.error) || "A escrita n\xE3o iniciou.");
          return;
        }
        (_c = this.root) == null ? void 0 : _c.classList.remove("is-reviewing");
        (_d = this.root) == null ? void 0 : _d.classList.add("is-writing");
        this.lastOperationState = "";
        text("mapOperationTitle", "Escrita manual em andamento");
        text("mapOperationMessage", "0 de ".concat(this.review.count, " c\xE9lulas confirmadas"));
        this.store.patch({ map: __spreadProps(__spreadValues({}, this.store.get().map), { state: "writing", operation: result }) });
      }
      pollWrite() {
        var _a, _b, _c, _d, _e;
        const operation = this.api.mapWriteOperation();
        if (!operation || operation.state === "IDLE" || operation.state === "UNAVAILABLE") return;
        if (operation.state === this.lastOperationState && !operation.busy) return;
        this.lastOperationState = operation.state;
        const progress = Math.max(0, Math.min(100, finite(operation.progress) || 0));
        const bar = document.getElementById("mapOperationProgress");
        if (bar) bar.style.width = "".concat(progress, "%");
        text("mapOperationMessage", "".concat(operation.confirmedCells || 0, " de ").concat(operation.totalCells || ((_a = this.review) == null ? void 0 : _a.count) || 0, " c\xE9lulas confirmadas"));
        if (operation.busy) {
          text("mapOperationTitle", operation.writerMessage || "Checkpoint \xB7 escrita \xB7 ACK \xB7 readback");
          return;
        }
        if (operation.state === "BATCH_CONFIRMED" && operation.readbackValid === true) {
          (_b = this.root) == null ? void 0 : _b.classList.remove("is-writing");
          (_c = this.root) == null ? void 0 : _c.classList.add("has-result");
          const result = document.getElementById("mapOperationResult");
          if (result) {
            result.dataset.level = "ok";
            result.querySelector("b").textContent = "".concat(operation.confirmedCells || operation.totalCells, " altera\xE7\xF5es confirmadas pela ECU");
            result.querySelector("span").textContent = "ACK e readback conclu\xEDdos. O mapa ser\xE1 relido para atualizar a tela.";
          }
          this.editor.reset();
          this.startRead(true);
        } else if (operation.state === "BATCH_PARTIAL_FAILED" || operation.ok === false) {
          (_d = this.root) == null ? void 0 : _d.classList.remove("is-writing");
          (_e = this.root) == null ? void 0 : _e.classList.add("has-result");
          const failure = operation.failure || {};
          const result = document.getElementById("mapOperationResult");
          if (result) {
            result.dataset.level = "critical";
            result.querySelector("b").textContent = "A ECU n\xE3o confirmou toda a opera\xE7\xE3o";
            result.querySelector("span").textContent = "".concat(operation.confirmedCells || 0, " c\xE9lulas foram confirmadas antes da falha. ").concat(failure.error || operation.error || "Releitura obrigat\xF3ria.");
          }
          this.editor.reset();
        }
      }
      applyContext(context) {
        var _a, _b, _c, _d, _e, _f;
        if (!context || !this.editor.hasMap()) return;
        const suggestion = context.suggestion;
        const changes = Array.isArray(suggestion == null ? void 0 : suggestion.mapChanges) ? suggestion.mapChanges : [];
        try {
          if (changes.length) {
            this.editor.setTargetOverrides(changes);
            const first = changes[0];
            this.renderGrid();
            this.renderEditor(Number(first.row), Number(first.column));
            return;
          }
          if (suggestion) {
            const row2 = Number(suggestion.row);
            const column2 = Number(suggestion.column);
            if (Number.isInteger(row2) && Number.isInteger(column2)) {
              this.editor.selectOnly(row2, column2);
              document.getElementById("mapAdjustmentMode").value = "percent";
              document.getElementById("mapAdjustmentValue").value = String(Number(suggestion.deltaPercent || 0));
              this.applyAdjustment();
              this.renderEditor(row2, column2);
              return;
            }
          }
          const row = Number((_b = (_a = context.cell) == null ? void 0 : _a.row) != null ? _b : context.row);
          const column = Number((_d = (_c = context.cell) == null ? void 0 : _c.column) != null ? _d : context.column);
          if (Number.isInteger(row) && Number.isInteger(column)) {
            this.editor.selectOnly(row, column);
            this.renderGrid();
            this.renderEditor(row, column);
            (_f = (_e = document.getElementById("mapAdjustmentValue")) == null ? void 0 : _e.focus) == null ? void 0 : _f.call(_e);
          }
        } catch (error) {
          this.alert(error.message);
        }
      }
      alert(message) {
        this.store.patch({ alert: { level: "warning", message: String(message || "Opera\xE7\xE3o indispon\xEDvel") } });
      }
    }
    ns.MapScreen = MapScreen;
  })(typeof window !== "undefined" ? window : globalThis);
})();
