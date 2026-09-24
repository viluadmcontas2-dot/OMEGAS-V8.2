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
    catch (error) { return { ok: false, error: error?.message || String(error), automatic: false, manualOnly: true }; }
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
    startRead: () => invoke('startRead', [], {}),
    cancelRead: () => invoke('cancelRead', [], {}),
    setAcquisitionEnabled: enabled => invoke('setAcquisitionEnabled', [!!enabled], {}),
    prepare: action => invoke('prepareNativeAction', [String(action || '')], {}),
    prepareKFactorReset: () => invoke('prepareNativeAction', ['RESET_K_FACTOR'], {}),
    execute: preparationId => invoke('executeNativeAction', [String(preparationId || '')], {}),
    cancelPreparation: () => invoke('clearNativeActionPreparation', [], {}),
  };
})(typeof window !== 'undefined' ? window : globalThis);