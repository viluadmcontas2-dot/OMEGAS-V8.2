(function (root, factory) {
  'use strict';
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  const ns = root.OmegasUi = root.OmegasUi || {};
  ns.LearningModel = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  const STATES = Object.freeze({ EMPTY: 'empty', PETROL: 'petrol', CNG: 'cng', COMPARABLE: 'comparable' });
  const finite = (value, fallback = 0) => { const parsed = Number(value); return Number.isFinite(parsed) ? parsed : fallback; };
  const keyOf = (row, column) => `${row}:${column}`;

  function localComparisonPrediction(prediction) {
    const supportType = String(prediction?.supportType || '').toUpperCase();
    return supportType === 'DIRECT' || supportType === 'NEAR' ? prediction : null;
  }

  function normalizeFuel(value) {
    const fuel = String(value || '').toUpperCase();
    if (fuel === 'PETROL' || fuel === 'GASOLINA') return 'PETROL';
    if (fuel === 'CNG' || fuel === 'GNV') return 'CNG';
    return 'UNKNOWN';
  }

  function cellCoordinates(item = {}) {
    const nested = item.cell || {};
    return {
      row: finite(item.row ?? item.cell_row ?? nested.row, -1),
      column: finite(item.column ?? item.cell_column ?? nested.column, -1),
    };
  }

  function indexComparisons(comparisons = []) {
    const result = new Map();
    comparisons.forEach(item => {
      const weights = Array.isArray(item.continuous_cell_weights)
        ? item.continuous_cell_weights
        : Array.isArray(item.continuousCellWeights)
          ? item.continuousCellWeights
          : [];
      weights.forEach(weight => {
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
    if (cell.state === STATES.EMPTY) return 'Ainda não há evidência válida nesta região.';
    if (cell.state === STATES.PETROL) return 'Há referência de gasolina, mas falta evidência GNV da época atual.';
    if (cell.state === STATES.CNG) return 'Há evidência GNV da época atual, mas falta referência de gasolina compatível.';
    if (!cell.ready) return 'Gasolina e GNV atuais existem, mas ainda falta comparação válida do núcleo nesta região.';
    return 'Gasolina e GNV da época atual possuem evidência compatível para comparação.';
  }

  function normalizeCellSummary(item, fallbackEpoch) {
    return {
      samples: finite(item.samples, 0),
      visits: finite(item.visit_count ?? item.visits?.length, 0),
      sessions: finite(item.session_count ?? item.sessions?.length, 0),
      confidence: finite(item.confidence, 0),
      stage: String(item.stage || 'OBSERVED').toUpperCase(),
      epoch: finite(item.epoch, fallbackEpoch),
      rpm: finite(item.rpm ?? item.rpm_mean, null),
      petrolMs: finite(item.petrol_ms ?? item.petrol_mean, null),
      mapBar: finite(item.map_bar ?? item.map_mean, null),
      petrolSpreadMs: finite(item.petrol_spread_ms, null),
      quality: finite(item.quality, null),
    };
  }

  function buildModel(payload = {}) {
    const grid = payload.grid || {};
    const rows = Math.max(1, finite(grid.rows, 12));
    const columns = Math.max(1, finite(grid.columns, 12));
    const epoch = finite(payload.epoch, 1);
    const rpmBins = Array.isArray(grid.rpmBins) ? grid.rpmBins : [];
    const petrolBins = Array.isArray(grid.petrolBins) ? grid.petrolBins : [];
    const comparisons = Array.isArray(payload.comparisons) ? payload.comparisons : [];
    const comparisonIndex = indexComparisons(comparisons);
    const indexed = new Map();

    for (let row = 0; row < rows; row += 1) {
      for (let column = 0; column < columns; column += 1) {
        indexed.set(keyOf(row, column), {
          key: keyOf(row, column), row, column,
          rpm: rpmBins[column] ?? column + 1,
          petrolMs: petrolBins[row] ?? row + 1,
          petrol: null, cng: null, previousCng: [],
          state: STATES.EMPTY, ready: false,
          comparisonCount: 0, comparisonWeight: 0,
        });
      }
    }

    const mergedCells = [];
    if (Array.isArray(payload.cells)) mergedCells.push(...payload.cells);
    if (Array.isArray(payload.petrol)) mergedCells.push(...payload.petrol.map(item => ({ ...item, fuel: item.fuel || 'PETROL' })));
    if (Array.isArray(payload.cng)) mergedCells.push(...payload.cng.map(item => ({ ...item, fuel: item.fuel || 'CNG' })));

    mergedCells.forEach(item => {
      const { row, column } = cellCoordinates(item);
      const target = indexed.get(keyOf(row, column));
      if (!target) return;
      const fuel = normalizeFuel(item.fuel);
      const itemEpoch = finite(item.epoch, fuel === 'PETROL' ? 0 : epoch);
      const summary = normalizeCellSummary(item, itemEpoch);
      if (fuel === 'PETROL') target.petrol = summary;
      if (fuel === 'CNG' && itemEpoch === epoch) target.cng = summary;
      if (fuel === 'CNG' && itemEpoch !== epoch) target.previousCng.push(summary);
    });

    const previousRegions = Array.isArray(payload.cngPreviousEpochs) ? payload.cngPreviousEpochs : [];
    previousRegions.forEach(item => {
      const { row, column } = cellCoordinates(item);
      const target = indexed.get(keyOf(row, column));
      if (!target) return;
      target.previousCng.push(normalizeCellSummary(item, finite(item.epoch, 0)));
    });

    indexed.forEach(cell => {
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
      epoch, rows, columns, rpmBins, petrolBins, cells: all, counts,
      coveragePercent: Math.round(((counts.petrol + counts.cng + counts.comparable) / Math.max(1, rows * columns)) * 100),
      comparablePercent: Math.round((counts.ready / Math.max(1, rows * columns)) * 100),
      integrity: payload.integrity || {},
      mapHash: String(payload.mapHash || ''),
    };
  }

  return { STATES, buildModel, normalizeFuel, keyOf, localComparisonPrediction };
});
