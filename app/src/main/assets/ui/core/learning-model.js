(() => {
  var __defProp = Object.defineProperty;
  var __defProps = Object.defineProperties;
  var __getOwnPropDescs = Object.getOwnPropertyDescriptors;
  var __getOwnPropNames = Object.getOwnPropertyNames;
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
  var __commonJS = (cb, mod) => function __require() {
    try {
      return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
    } catch (e) {
      throw mod = 0, e;
    }
  };
  var require_learning_model = __commonJS({
    "app/src/main/assets/ui/core/learning-model.js"(exports, module) {
      (function(root, factory) {
        "use strict";
        const api = factory();
        if (typeof module === "object" && module.exports) module.exports = api;
        const ns = root.OmegasUi = root.OmegasUi || {};
        ns.LearningModel = api;
      })(typeof globalThis !== "undefined" ? globalThis : exports, function() {
        "use strict";
        const STATES = Object.freeze({ EMPTY: "empty", PETROL: "petrol", CNG: "cng", COMPARABLE: "comparable" });
        const finite = (value, fallback = 0) => {
          const parsed = Number(value);
          return Number.isFinite(parsed) ? parsed : fallback;
        };
        const keyOf = (row, column) => "".concat(row, ":").concat(column);
        function normalizeFuel(value) {
          const fuel = String(value || "").toUpperCase();
          if (fuel === "PETROL" || fuel === "GASOLINA") return "PETROL";
          if (fuel === "CNG" || fuel === "GNV") return "CNG";
          return "UNKNOWN";
        }
        function cellCoordinates(item = {}) {
          var _a, _b, _c, _d;
          const nested = item.cell || {};
          return {
            row: finite((_b = (_a = item.row) != null ? _a : item.cell_row) != null ? _b : nested.row, -1),
            column: finite((_d = (_c = item.column) != null ? _c : item.cell_column) != null ? _d : nested.column, -1)
          };
        }
        function indexComparisons(comparisons = []) {
          const result = /* @__PURE__ */ new Map();
          comparisons.forEach((item) => {
            const weights = Array.isArray(item.continuous_cell_weights) ? item.continuous_cell_weights : Array.isArray(item.continuousCellWeights) ? item.continuousCellWeights : [];
            weights.forEach((weight) => {
              const row = finite(weight.row, -1);
              const column = finite(weight.column, -1);
              if (row < 0 || column < 0) return;
              const key = keyOf(row, column);
              const current = result.get(key) || { count: 0, weight: 0 };
              current.count += 1;
              current.weight += finite(weight.weight, 0);
              result.set(key, current);
            });
            const direct = cellCoordinates(item);
            if (direct.row >= 0 && direct.column >= 0 && !result.has(keyOf(direct.row, direct.column))) {
              result.set(keyOf(direct.row, direct.column), { count: 1, weight: 1 });
            }
          });
          return result;
        }
        function reasonFor(cell) {
          if (cell.state === STATES.EMPTY) return "Ainda n\xE3o h\xE1 evid\xEAncia v\xE1lida nesta regi\xE3o.";
          if (cell.state === STATES.PETROL) return "H\xE1 refer\xEAncia de gasolina, mas falta evid\xEAncia GNV da \xE9poca atual.";
          if (cell.state === STATES.CNG) return "H\xE1 evid\xEAncia GNV da \xE9poca atual, mas falta refer\xEAncia de gasolina compat\xEDvel.";
          if (!cell.ready) return "Gasolina e GNV atuais existem, mas ainda falta compara\xE7\xE3o v\xE1lida do n\xFAcleo nesta regi\xE3o.";
          return "Gasolina e GNV da \xE9poca atual possuem evid\xEAncia compat\xEDvel para compara\xE7\xE3o.";
        }
        function normalizeCellSummary(item, fallbackEpoch) {
          var _a, _b, _c, _d, _e, _f, _g;
          return {
            samples: finite(item.samples, 0),
            visits: finite((_b = item.visit_count) != null ? _b : (_a = item.visits) == null ? void 0 : _a.length, 0),
            sessions: finite((_d = item.session_count) != null ? _d : (_c = item.sessions) == null ? void 0 : _c.length, 0),
            confidence: finite(item.confidence, 0),
            stage: String(item.stage || "OBSERVED").toUpperCase(),
            epoch: finite(item.epoch, fallbackEpoch),
            rpm: finite((_e = item.rpm) != null ? _e : item.rpm_mean, null),
            petrolMs: finite((_f = item.petrol_ms) != null ? _f : item.petrol_mean, null),
            mapBar: finite((_g = item.map_bar) != null ? _g : item.map_mean, null),
            petrolSpreadMs: finite(item.petrol_spread_ms, null),
            quality: finite(item.quality, null)
          };
        }
        function buildModel(payload = {}) {
          var _a, _b;
          const grid = payload.grid || {};
          const rows = Math.max(1, finite(grid.rows, 12));
          const columns = Math.max(1, finite(grid.columns, 12));
          const epoch = finite(payload.epoch, 1);
          const rpmBins = Array.isArray(grid.rpmBins) ? grid.rpmBins : [];
          const petrolBins = Array.isArray(grid.petrolBins) ? grid.petrolBins : [];
          const comparisons = Array.isArray(payload.comparisons) ? payload.comparisons : [];
          const comparisonIndex = indexComparisons(comparisons);
          const indexed = /* @__PURE__ */ new Map();
          for (let row = 0; row < rows; row += 1) {
            for (let column = 0; column < columns; column += 1) {
              indexed.set(keyOf(row, column), {
                key: keyOf(row, column),
                row,
                column,
                rpm: (_a = rpmBins[column]) != null ? _a : column + 1,
                petrolMs: (_b = petrolBins[row]) != null ? _b : row + 1,
                petrol: null,
                cng: null,
                previousCng: [],
                state: STATES.EMPTY,
                ready: false,
                comparisonCount: 0,
                comparisonWeight: 0
              });
            }
          }
          const mergedCells = [];
          if (Array.isArray(payload.cells)) mergedCells.push(...payload.cells);
          if (Array.isArray(payload.petrol)) mergedCells.push(...payload.petrol.map((item) => __spreadProps(__spreadValues({}, item), { fuel: item.fuel || "PETROL" })));
          if (Array.isArray(payload.cng)) mergedCells.push(...payload.cng.map((item) => __spreadProps(__spreadValues({}, item), { fuel: item.fuel || "CNG" })));
          mergedCells.forEach((item) => {
            const { row, column } = cellCoordinates(item);
            const target = indexed.get(keyOf(row, column));
            if (!target) return;
            const fuel = normalizeFuel(item.fuel);
            const itemEpoch = finite(item.epoch, fuel === "PETROL" ? 0 : epoch);
            const summary = normalizeCellSummary(item, itemEpoch);
            if (fuel === "PETROL") target.petrol = summary;
            if (fuel === "CNG" && itemEpoch === epoch) target.cng = summary;
            if (fuel === "CNG" && itemEpoch !== epoch) target.previousCng.push(summary);
          });
          const previousRegions = Array.isArray(payload.cngPreviousEpochs) ? payload.cngPreviousEpochs : [];
          previousRegions.forEach((item) => {
            const { row, column } = cellCoordinates(item);
            const target = indexed.get(keyOf(row, column));
            if (!target) return;
            target.previousCng.push(normalizeCellSummary(item, finite(item.epoch, 0)));
          });
          indexed.forEach((cell) => {
            const comparison = comparisonIndex.get(cell.key) || { count: 0, weight: 0 };
            cell.comparisonCount = comparison.count;
            cell.comparisonWeight = comparison.weight;
            if (cell.petrol && cell.cng) cell.state = STATES.COMPARABLE;
            else if (cell.petrol) cell.state = STATES.PETROL;
            else if (cell.cng) cell.state = STATES.CNG;
            cell.ready = cell.state === STATES.COMPARABLE && comparison.count > 0;
            cell.readinessReason = reasonFor(cell);
          });
          const all = [...indexed.values()];
          const counts = all.reduce((acc, cell) => {
            acc[cell.state] += 1;
            if (cell.ready) acc.ready += 1;
            if (cell.previousCng.length) acc.historical += 1;
            return acc;
          }, { empty: 0, petrol: 0, cng: 0, comparable: 0, ready: 0, historical: 0 });
          return {
            epoch,
            rows,
            columns,
            rpmBins,
            petrolBins,
            cells: all,
            counts,
            coveragePercent: Math.round((counts.petrol + counts.cng + counts.comparable) / Math.max(1, rows * columns) * 100),
            comparablePercent: Math.round(counts.ready / Math.max(1, rows * columns) * 100),
            integrity: payload.integrity || {},
            mapHash: String(payload.mapHash || "")
          };
        }
        return { STATES, buildModel, normalizeFuel, keyOf };
      });
    }
  });
  require_learning_model();
  if (typeof module === "object" && module.exports && typeof globalThis !== "undefined" && globalThis.OmegasUi) module.exports = globalThis.OmegasUi.LearningModel;

})();
