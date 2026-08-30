(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  function asNumber(value) {
    if (value === null || value === undefined || value === '') return null;
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }

  function explainCell(cell) {
    const source = cell && typeof cell === 'object' ? cell : {};
    const state = String(source.state || 'DESCONHECIDO').toUpperCase();
    const currentK = asNumber(source.currentK);
    const targetK = asNumber(source.targetK);
    const confidence = Math.max(0, Math.min(1, asNumber(source.predictionConfidence ?? source.confidence) ?? 0));
    const provenance = Array.isArray(source.provenance) ? source.provenance.slice() : [];
    return {
      ok: true,
      key: String(source.key || ''),
      row: Number(source.row ?? -1),
      column: Number(source.column ?? -1),
      rpm: asNumber(source.rpm),
      petrolMs: asNumber(source.petrolMs),
      state,
      stateLabel: state === 'VALIDADO' ? 'Validado' : state === 'OBSERVADO' ? 'Observado' : state === 'PREVISTO' ? 'Previsto' : 'Sem previsão',
      reason: String(source.stateReason || source.predictionReason || 'Sem suporte científico suficiente'),
      confidence,
      currentK,
      targetK,
      deltaK: currentK !== null && targetK !== null ? targetK - currentK : null,
      predicted: source.predicted === true,
      directObservation: source.directObservation === true,
      nativeAnchorCount: Number(source.nativeAnchorCount || 0),
      distinctTrajectories: Number(source.distinctTrajectories || 0),
      provenance,
      automaticWrite: false,
      requiresHumanReview: currentK !== null && targetK !== null,
    };
  }

  function openMapReview(router, cell) {
    const explanation = explainCell(cell);
    if (!router || typeof router.navigate !== 'function') return false;
    if (explanation.row < 0 || explanation.column < 0 || explanation.currentK === null || explanation.targetK === null) return false;
    if (explanation.targetK < 100 || explanation.targetK > 180) return false;
    return router.navigate('map', {
      origin: 'predictor',
      source: 'predictor',
      intent: 'review-only',
      row: explanation.row,
      column: explanation.column,
      predictedTargetK: explanation.targetK,
      currentK: explanation.currentK,
      confidence: explanation.confidence,
      state: explanation.state,
      automaticWrite: false,
      requiresHumanReview: true,
      suggestion: {
        target: 'MAP_K',
        mapChanges: [{
          row: explanation.row,
          column: explanation.column,
          before: explanation.currentK,
          after: explanation.targetK,
          source: 'PREDICTOR_REVIEW_ONLY',
        }],
      },
    });
  }

  ns.PredictorModel = {
    explainCell,
    openMapReview,
  };
})(typeof window !== 'undefined' ? window : globalThis);
