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
    status: () => invoke('getNativeMonitorStatus', [], { ok: false, error: 'AutoCal nativo indisponível' }),
    snapshot: () => invoke('getNativeMonitorSnapshot', [], { available: false }),
    actionStatus: () => invoke('getNativeActionStatus', [], {}),
    startRead: () => invoke('startRead', [], {}),
    prepare: action => invoke('prepareNativeAction', [String(action || '')], {}),
    execute: preparationId => invoke('executeNativeAction', [String(preparationId || '')], {}),
    cancelPreparation: () => invoke('clearNativeActionPreparation', [], {}),
  };
})(typeof window !== 'undefined' ? window : globalThis);
