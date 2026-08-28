(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  function asNumber(value) {
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }

  function metadata(source) {
    return {
      key: String(source.key || ''),
      row: Number(source.row ?? -1),
      column: Number(source.column ?? -1),
      rpm: asNumber(source.rpm),
      petrolMs: asNumber(source.petrolMs),
      nativeAnchorCount: Number(source.nativeAnchorCount || 0),
      distinctTrajectories: Number(source.distinctTrajectories || 0),
      provenance: Array.isArray(source.provenance) ? source.provenance.slice() : [],
    };
  }

  function failClosed(source) {
    return {
      ok: false,
      ...metadata(source),
      state: 'DESCONHECIDO',
      visualState: 'DESCONHECIDO',
      scientificState: 'UNKNOWN_ABSTAIN',
      stateLabel: 'Sem previsão',
      targetLabel: 'ESTIMATIVA — PRECISA DE CONFIRMAÇÃO',
      reason: 'Estado científico humano indisponível; a UI não reconstrói authority, risco ou target.',
      disclosure: null,
      confidence: 0,
      currentK: null,
      targetK: null,
      targetEstimateK: null,
      intervalLowerK: null,
      intervalUpperK: null,
      intervalBasis: null,
      authority: 'UNKNOWN',
      riskState: 'BLOCKED',
      actionState: 'ABSTAIN',
      predicted: false,
      directObservation: false,
      automaticWrite: false,
      requiresHumanReview: false,
    };
  }

  function explainCell(cell) {
    const source = cell && typeof cell === 'object' ? cell : {};
    const human = source.humanState && typeof source.humanState === 'object' ? source.humanState : null;
    if (!human) return failClosed(source);

    const currentK = asNumber(human.currentK);
    const targetK = asNumber(human.targetEstimateK);
    const confidenceNumber = asNumber(human.confidence);
    const confidence = confidenceNumber === null ? 0 : Math.max(0, Math.min(1, confidenceNumber));
    const visualState = String(human.visualState || 'DESCONHECIDO');
    const scientificState = String(human.scientificState || 'UNKNOWN_ABSTAIN');
    const actionState = String(human.actionState || 'ABSTAIN');
    return {
      ok: true,
      ...metadata(source),
      state: visualState,
      visualState,
      scientificState,
      stateLabel: String(human.stateLabel || 'Sem previsão'),
      targetLabel: String(human.targetLabel || 'ESTIMATIVA — PRECISA DE CONFIRMAÇÃO'),
      reason: String(human.reason || 'Estado científico humano sem explicação.'),
      disclosure: human.disclosure == null ? null : String(human.disclosure),
      confidence,
      currentK,
      targetK,
      targetEstimateK: targetK,
      intervalLowerK: asNumber(human.intervalLowerK),
      intervalUpperK: asNumber(human.intervalUpperK),
      intervalBasis: human.intervalBasis == null ? null : String(human.intervalBasis),
      authority: String(human.authority || 'UNKNOWN'),
      riskState: String(human.riskState || 'BLOCKED'),
      actionState,
      deltaK: currentK !== null && targetK !== null ? targetK - currentK : null,
      predicted: human.predicted === true,
      directObservation: human.directObservation === true,
      automaticWrite: false,
      requiresHumanReview: human.requiresHumanReview === true,
    };
  }

  function openMapReview(router, cell) {
    const explanation = explainCell(cell);
    if (!router || typeof router.navigate !== 'function') return false;
    if (!explanation.requiresHumanReview) return false;
    if (explanation.actionState !== 'ACTIONABLE' && explanation.actionState !== 'REVIEWABLE') return false;
    if (explanation.row < 0 || explanation.column < 0 || explanation.currentK === null || explanation.targetK === null) return false;
    return router.navigate('map', {
      origin: 'predictor',
      source: 'predictor',
      intent: 'review-only',
      row: explanation.row,
      column: explanation.column,
      predictedTargetK: explanation.targetK,
      currentK: explanation.currentK,
      confidence: explanation.confidence,
      scientificState: explanation.scientificState,
      authority: explanation.authority,
      riskState: explanation.riskState,
      actionState: explanation.actionState,
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
