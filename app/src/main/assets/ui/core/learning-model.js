(function (root, factory) {
  'use strict';
  const api = factory();
  const commonJs = typeof module === 'object' && module.exports;
  if (commonJs) module.exports = api;
  const ns = root.OmegasUi = root.OmegasUi || {};
  ns.LearningModel = api;

  if (!commonJs && typeof root.setTimeout === 'function') {
    const installWhenReady = () => {
      const app = root.OmegasApp;
      if (!app?.store || !app?.api) {
        root.setTimeout(installWhenReady, 25);
        return;
      }
      const controller = api.installRuntimeEfficiency(app);
      if (controller && typeof root.addEventListener === 'function') {
        root.addEventListener('omegas-refresh', controller.invalidate);
      }
    };
    root.setTimeout(installWhenReady, 0);
  }
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  const STATES = Object.freeze({ EMPTY: 'empty', PETROL: 'petrol', CNG: 'cng', COMPARABLE: 'comparable' });
  const finite = (value, fallback = 0) => { const parsed = Number(value); return Number.isFinite(parsed) ? parsed : fallback; };
  const keyOf = (row, column) => `${row}:${column}`;

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

  /**
   * Reuses the live telemetry already fetched by the fast path instead of asking
   * Kotlin for a second full engine snapshot just to explain the current sample.
   */
  function decisionFromTelemetry(telemetry = {}) {
    const source = telemetry && typeof telemetry === 'object' ? telemetry : {};
    const live = source.live || source.data || source;
    const sample = live.sample && typeof live.sample === 'object' ? live.sample : {};
    return {
      ok: source.ok !== false,
      state: sample.state || live.sample_state || 'OBSERVING_ENGINE',
      reason: sample.reason || live.sample_reason || 'Observando o motor',
      reason_code: sample.reason_code || sample.reasonCode || live.sample_state || 'OBSERVING_ENGINE',
      frame_count: finite(sample.frame_count ?? live.sample_frame_count, 0),
      minimum_frames: finite(sample.minimum_frames ?? live.sample_minimum_frames, 0),
      desired_frames: finite(sample.desired_frames ?? live.sample_desired_frames, 0),
      duration_ms: finite(sample.duration_ms ?? live.sample_duration_ms, 0),
      median_interval_ms: finite(sample.median_interval_ms, 0),
      gap_ms: finite(sample.gap_ms, 0),
      learning_eligible: sample.learning_eligible === true,
      fuel_confirmed: sample.fuel_confirmed ?? live.fuel ?? null,
      window_age_ms: finite(sample.window_age_ms ?? sample.duration_ms, 0),
      window_budget_ms: finite(sample.window_budget_ms, 0),
      frames_evicted: finite(sample.frames_evicted, 0),
      cell_key: sample.cell_key || '',
      cell_row: finite(sample.cell_row, -1),
      cell_column: finite(sample.cell_column, -1),
      quality: finite(sample.quality ?? live.learning_quality, 0),
      plausibility_reasons: Array.isArray(sample.plausibility_reasons) ? sample.plausibility_reasons : [],
      live,
    };
  }

  /**
   * Structural science revision. Plain received-frame churn is intentionally not
   * part of this key; only events capable of changing the persisted Learning/UI
   * projection invalidate it. Explicit lifecycle/import/write refreshes also
   * invalidate the runtime cache through `omegas-refresh`.
   */
  function scienceRevisionSignature(status = {}) {
    const source = status && typeof status === 'object' ? status : {};
    const evidence = source.evidence_budget || source.evidenceBudget || {};
    const binding = source.calibration_binding || source.calibrationBinding || {};
    const reset = source.last_reset || source.lastReset || {};
    const restore = source.restore || {};
    return [
      source.session_id ?? source.sessionId ?? '',
      source.epoch ?? source.memory?.epoch ?? '',
      source.new_frames_absorbed ?? source.newFramesAbsorbed ?? 0,
      source.lifetime_new_frames_absorbed ?? source.lifetimeNewFramesAbsorbed ?? 0,
      source.advisor_revision ?? source.advisorRevision ?? 0,
      source.advisor_published_revision ?? source.advisorPublishedRevision ?? 0,
      source.comparison_count ?? source.comparisonCount ?? 0,
      source.unique_comparison_visits ?? source.uniqueComparisonVisits ?? 0,
      evidence.nativeBands ?? evidence.native_bands ?? 0,
      evidence.nativeAnchors ?? evidence.native_anchors ?? 0,
      evidence.visitAccumulators ?? evidence.visit_accumulators ?? 0,
      reset.resetAt ?? reset.reset_at ?? 0,
      binding.calibrationFingerprint ?? binding.calibration_fingerprint ?? '',
      binding.calibrationGeneration ?? binding.calibration_generation ?? binding.generation ?? '',
      binding.geometryFingerprint ?? binding.geometry_fingerprint ?? '',
      source.learning_data_revision ?? source.learningDataRevision ?? '',
      restore.state ?? source.restoreState ?? '',
    ].join('|');
  }

  /**
   * Installs two bounded UI-only efficiencies on the already-created NativeApi:
   * 1) one Learning status bridge call is shared inside the same refresh burst;
   * 2) the expensive persisted Learning projection is reused until its material
   *    science signature changes. No producer, timer, serial call or writer is added.
   */
  function installRuntimeEfficiency(app, options = {}) {
    if (!app?.api || !app?.store) return null;
    if (app.api.__omegasLearningEfficiencyController) return app.api.__omegasLearningEfficiencyController;
    if (typeof app.api.learning !== 'function' || typeof app.api.learningStatus !== 'function') return null;

    const originalLearning = app.api.learning.bind(app.api);
    const originalLearningStatus = app.api.learningStatus.bind(app.api);
    const now = typeof options.now === 'function' ? options.now : () => Date.now();
    const statusBurstMs = Math.max(0, finite(options.statusBurstMs, 300));
    let statusCache = null;
    let statusCachedAt = Number.NEGATIVE_INFINITY;
    let learningCache = null;
    let learningSignature = null;

    function statusSnapshot() {
      const at = now();
      if (statusCache !== null && at - statusCachedAt <= statusBurstMs) return statusCache;
      statusCache = originalLearningStatus() || {};
      statusCachedAt = at;
      return statusCache;
    }

    app.api.learningStatus = statusSnapshot;
    app.api.learning = function () {
      const nextSignature = scienceRevisionSignature(statusSnapshot());
      if (learningCache !== null && learningSignature === nextSignature) return learningCache;
      learningCache = originalLearning() || {};
      learningSignature = nextSignature;
      return learningCache;
    };
    app.api.learningDecision = function () {
      return decisionFromTelemetry(app.store.get()?.telemetry || {});
    };

    const controller = {
      invalidate: () => {
        statusCache = null;
        statusCachedAt = Number.NEGATIVE_INFINITY;
        learningSignature = null;
      },
    };
    app.api.__omegasLearningEfficiencyController = controller;
    return controller;
  }

  return {
    STATES,
    buildModel,
    normalizeFuel,
    keyOf,
    decisionFromTelemetry,
    scienceRevisionSignature,
    installRuntimeEfficiency,
  };
});
