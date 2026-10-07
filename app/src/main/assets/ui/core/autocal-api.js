(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  function parse(value, fallback) {
    if (value == null || value === '') return fallback;
    if (typeof value !== 'string') return value;
    try { return JSON.parse(value); } catch (_) { return fallback; }
  }

  function invoke(name, args, fallback) {
    const bridge = root.OmegasAutoCal;
    const fn = bridge && bridge[name];
    if (typeof fn !== 'function') return fallback;
    try { return parse(fn.apply(bridge, args || []), fallback); }
    catch (error) {
      // Nunca engolir calado: o console diz QUAL chamada da ponte falhou.
      console.warn(`[OMEGAS AutoCalApi] ${name} falhou:`, error);
      return { ok: false, error: error?.message || String(error), automatic: false, manualOnly: true };
    }
  }

  ns.AutoCalApi = {
    available: () => !!root.OmegasAutoCal,
    identity: () => invoke('getIdentity', [], {}),
    readerStatus: () => invoke('getStatus', [], { ok: false, state: 'UNAVAILABLE', error: 'Reader AutoCal indisponível' }),
    readerSnapshot: () => invoke('getSnapshot', [], { available: false }),
    acquisitionStatus: () => invoke('getNativeMonitorStatus', [], { ok: false, state: 'UNAVAILABLE', error: 'Monitor AutoCal nativo indisponível' }),
    acquisitionSnapshot: () => invoke('getNativeMonitorSnapshot', [], { available: false }),
    projection: () => invoke('getUiProjection', [], { ok: false, source: 'NONE', referenceUsable: false, snapshot: { available: false } }),
    sessionStatus: () => invoke('getSessionLedgerStatus', [], {}),
    sessions: () => invoke('listAutoCalSessions', [], []),
    exportSession: sessionId => invoke('exportAutoCalSession', [String(sessionId || '')], false),
    actionStatus: () => invoke('getNativeActionStatus', [], {}),
    // Limpeza automática do GNV (pontos aprendidos na lenta): ligada/pausada, motivo e apagamentos recentes.
    autoCleanup: () => invoke('getAutoCleanupStatus', [], { ok: false }),
    startRead: () => invoke('startRead', [], {}),
    cancelRead: () => invoke('cancelRead', [], {}),
    setAcquisitionEnabled: enabled => invoke('setAcquisitionEnabled', [!!enabled], {}),
    prepare: action => invoke('prepareNativeAction', [String(action || '')], {}),
    preparePointDelete: (fuel, index) => invoke('preparePointDelete', [String(fuel || ''), Number(index)], {}),
    preparePointDeleteBatch: targets => invoke('preparePointDeleteBatch', [JSON.stringify(Array.isArray(targets) ? targets : [])], {}),
    execute: preparationId => invoke('executeNativeAction', [String(preparationId || '')], {}),
    cancelPreparation: () => invoke('clearNativeActionPreparation', [], {}),
    // Refino OMEGAS (somente leitura; a gravação usa o fluxo da Curva K).
    refinedAnalysis: () => invoke('getRefinedAnalysis', [], { ok: false, available: false }),
    equivalence: () => invoke('getEquivalence', [], { ok: false }),
    // Descarta o valor pronto: depois de gravar, desfazer ou restaurar o Refino lê o estado novo.
    equivalenceFresh: () => invoke('getEquivalenceFresh', [], { ok: false }),
    // Só a fase do piloto: barata, para Agora e Sugestões (equivalence() recalcula milhares de pontos).
    refinementPhase: () => invoke('getRefinementPhase', [], { ok: false }),
    // Cérebro único: resultado completo, congelar a Referência (toque do dono, sem escrita na ECU) e o Desfazer dele.
    equivalenceResult: () => invoke('getEquivalenceResult', [], { ok: false, available: false }),
    freezeReference: () => invoke('freezeReference', [], { ok: false }),
    resetGasEvidence: () => invoke('resetGasEvidence', [], { ok: false, message: 'Reinício do aprendizado indisponível.' }),
    restorePreviousReference: () => invoke('restorePreviousReference', [], { ok: false }),
  };
})(typeof window !== 'undefined' ? window : globalThis);