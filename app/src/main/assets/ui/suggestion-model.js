'use strict';

(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.OmegasSuggestionModel = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const finite = value => Number.isFinite(Number(value));
  const clamp = value => Math.max(0, Math.min(1, Number(value) || 0));

  function normalize(raw = {}) {
    const type = raw.type === 'map' ? 'map' : raw.type === 'curve' ? 'curve' : null;
    const confidence = clamp(raw.confidence);
    const deltaSource = raw.suggestedDeltaPercent ?? raw.deltaPercent;
    const delta = finite(deltaSource) ? Number(deltaSource) : null;
    const actionable = raw.actionable === true && type !== null && delta !== null;
    const evidence = Math.max(0, Math.trunc(Number(raw.evidenceCount ?? raw.samples ?? raw.evidence ?? 0) || 0));
    const reason = String(raw.reason || raw.insufficientReason || raw.decisionReason || '').trim();

    return {
      id: String(raw.id || raw.suggestionId || ''),
      type,
      scope: type === 'curve' ? 'global' : type === 'map' ? 'local' : 'unknown',
      destination: type === 'curve' ? 'Curva K' : type === 'map' ? 'Mapa K' : 'Não identificado',
      confidence,
      confidencePercent: Math.round(confidence * 100),
      deltaPercent: delta,
      evidence,
      actionable,
      reason: reason || (actionable ? 'Evidência suficiente para revisão humana.' : 'Evidência insuficiente ou proposta inválida.'),
      index: finite(raw.index) ? Number(raw.index) : null,
      row: finite(raw.row) ? Number(raw.row) : null,
      column: finite(raw.column) ? Number(raw.column) : null,
      petrolMs: finite(raw.petrolMs) ? Number(raw.petrolMs) : null,
    };
  }

  function classify(raw = {}) {
    const item = normalize(raw);
    let confidenceLabel = 'baixa';
    if (item.confidence >= 0.8) confidenceLabel = 'alta';
    else if (item.confidence >= 0.55) confidenceLabel = 'média';

    const explanation = item.scope === 'global'
      ? 'Tendência global: revisar na Curva K; não transformar em correção de célula do Mapa K.'
      : item.scope === 'local'
        ? 'Erro residual local: revisar somente na região indicada do Mapa K.'
        : 'Destino não identificado; não abrir editor nem permitir aplicação.';

    return { ...item, confidenceLabel, explanation };
  }

  function split(advice = {}) {
    const curve = Array.isArray(advice.kFactorSuggestions) ? advice.kFactorSuggestions : [];
    const predictions = Array.isArray(advice.mapResidualPredictions) ? advice.mapResidualPredictions : [];
    const map = (predictions.length ? predictions : (Array.isArray(advice.mapResidualSuggestions) ? advice.mapResidualSuggestions : []))
      .filter(item => !predictions.length || ['DIRECT', 'NEAR'].includes(String(item.supportType || '')));
    const curveItems = curve.map(item => classify({ ...item, type: 'curve' }));
    const mapItems = map.map(item => classify({ ...item, type: 'map' }));
    return {
      curve: curveItems,
      map: mapItems,
      actionable: [...curveItems, ...mapItems]
        .filter(item => item.actionable)
        .sort((a, b) => b.confidence - a.confidence),
      insufficient: [...curveItems, ...mapItems].filter(item => !item.actionable),
    };
  }

  function reviewAction(item) {
    const suggestion = classify(item);
    if (!suggestion.actionable) return { allowed: false, action: 'none', reason: suggestion.reason };
    return {
      allowed: true,
      action: suggestion.type === 'curve' ? 'open-curve-editor' : 'open-map-editor',
      writesEcu: false,
      reason: 'Abrir para revisão manual não inicia escrita.',
    };
  }

  return { normalize, classify, split, reviewAction };
});
