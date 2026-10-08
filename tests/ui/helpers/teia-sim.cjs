'use strict';
// TEIA DE ARANHA do AutoCal: simulador de ECU + ponte que REGISTRA cada chamada. SIMULADO (classe 4: Chromium, nao o carro).
// Entra depois do mock-bridge.js e troca a projecao/acoes do AutoCal por um estado vivo controlado pelo teste.
module.exports.SIM_SCRIPT = `(function () {
  const A = window.OmegasAutoCal, J = JSON.stringify;
  const orig = JSON.parse(A.getUiProjection());
  const fld = k => orig.snapshot.fields.find(x => x.key === k);
  const thd = fld('MNFLD_PRESS_THD').physicalValues.slice();
  const axis = fld('PETR_INJ_TBP').rawValues.map(v => v / 512);
  const rv = fld('PETR_MNFLD_PRESS_RV').rawValues.map(v => v / 1024);
  const refMs = bar => { for (let i = 1; i < rv.length; i++) if (rv[i - 1] <= bar && bar <= rv[i]) { const t = (bar - rv[i - 1]) / ((rv[i] - rv[i - 1]) || 1); return axis[i - 1] + t * (axis[i] - axis[i - 1]); } return axis[axis.length - 1]; };
  const mapC = Array.from({ length: 18 }, (_, i) => i < 17 ? (thd[i] + thd[i + 1]) / 2 : thd[17] + 0.03);
  const BYTES = { RESET_PETROL: '02 24 04 01', RESET_GAS: '02 24 04 02', DISABLE_AUTO_CAL: '12 4A 01 00', ENABLE_AUTO_CAL: '12 4A 01 01' };
  const ZONE = i => i < 6 ? 0 : i < 10 ? 1 : i < 14 ? 2 : 3;
  const S = window.__ss = { p: Array(18).fill(0), g: Array(18).fill(0), enabled: 1, pGen: 1, gGen: 1, pPend: false, gPend: false, rev: 1000, act: {}, auto: 1, prep: null, fail: null, pendingTimer: null, points: [] };
  window.__step = 'init'; window.__cmds = []; window.__polls = 0;
  const pad = a => a.slice();
  function snapshot() {
    const snap = JSON.parse(J(orig.snapshot));
    const set = (k, raw, phys) => { const f = snap.fields.find(x => x.key === k); if (f) { f.rawValues = raw; f.physicalValues = phys || raw; f.status = 'VALID'; } };
    const ms = (arr, k) => arr.map((c, i) => c > 0 ? refMs(mapC[i]) * k : 0);
    const pm = ms(S.p, 1), gm = ms(S.g, 1.07);
    set('PETR_INJ_TBUF', pm.map(v => Math.round(v * 512)), pm);
    set('MNFLD_PRESS_BUF', S.p.map((c, i) => c > 0 ? Math.round(mapC[i] * 1024) : 0), S.p.map((c, i) => c > 0 ? mapC[i] : 0));
    set('PETR_INJ_TBUF_GAS', gm.map(v => Math.round(v * 512)), gm);
    set('MNFLD_PRESS_BUF_GAS', S.g.map((c, i) => c > 0 ? Math.round(mapC[i] * 1024) : 0), S.g.map((c, i) => c > 0 ? mapC[i] : 0));
    set('PETR_INJ_TBUF_GAS_PREV', Array(18).fill(0)); set('MNFLD_PRESS_BUF_GAS_PREV', Array(18).fill(0));
    set('NUM_BUF_UPD_PETR', pad(S.p)); set('NUM_BUF_UPD_GAS', pad(S.g));
    const zf = arr => [0, 1, 2, 3].map(z => arr.every((c, i) => ZONE(i) !== z || c >= 3) ? 1 : 0);
    S.zp = zf(S.p); S.zg = zf(S.g);
    set('ACQUIRED_ZONES_PETROL', S.zp.slice()); set('ACQUIRED_ZONES_GAS', S.zg.slice());
    set('AUTO_CAL_ENABLE', [S.enabled]); set('NUM_AUTOMATCH_EXECUTED', [S.auto]);
    snap.sequence = S.rev; snap.capturedAtMs = Date.now();
    return snap;
  }
  function projection() {
    const snap = snapshot();
    return Object.assign({}, orig, { ok: true, revision: S.rev, sessionId: 'sim-1', snapshot: snap, nativeSnapshot: snap,
      nativeStatus: Object.assign({}, orig.nativeStatus, { latestSnapshot: snap }),
      acquisitionZones: { petrol: S.zp.map(v => v > 0), gas: S.zg.map(v => v > 0) },
      liveAcquisitionEpoch: { petrolGeneration: S.pGen, gasGeneration: S.gGen, petrolPending: S.pPend, gasPending: S.gPend, petrolReferencePending: S.pPend, gasReferencePending: S.gPend, referencePending: false },
      referenceUsable: true, referenceTimingKnown: true, referenceTimingCoherent: true });
  }
  const bump = () => { S.rev++; };
  const finish = (action, fail) => {
    if (fail) { S.act = { ok: false, action, state: 'FAILED', busy: false, mutationMayHaveStarted: true, message: 'a porta USB caiu no meio (SIMULADO)', error: 'transporte' }; }
    else {
      if (action === 'RESET_PETROL') { S.p.fill(0); S.pGen++; S.pPend = true; }
      if (action === 'RESET_GAS') { S.g.fill(0); S.gGen++; S.gPend = true; }
      if (action === 'DISABLE_AUTO_CAL') S.enabled = 0; else S.enabled = 1; // regra 14: reset termina com AUTO_CAL_ENABLE=1
      if (action === 'DELETE_POINT') S.points.forEach(t => { (t.fuel === 'GNV' || t.fuel === 'GAS' ? S.g : S.p)[t.index] = 0; });
      S.act = { ok: true, action, state: 'COMPLETED', busy: false };
    }
    if (fail && action.startsWith('RESET')) S.enabled = 1; // falha de transporte: readback religa (contrato da regra 14)
    bump();
  };
  window.__sim = {
    acquire(fuel, bands, count) { const a = fuel === 'gas' ? S.g : S.p; bands.forEach(b => { a[b] = count == null ? 10 : count; }); if (fuel === 'gas') S.gPend = false; else S.pPend = false; bump(); },
    clear() { S.p.fill(0); S.g.fill(0); S.pPend = S.gPend = false; bump(); },
    hold(on) { S.act = on ? { ok: true, action: 'RESET_GAS', state: 'READING_AFTER', busy: true } : {}; bump(); },
    failNext(on) { S.fail = on; }, automatch() { S.g.fill(0); S.gGen++; S.gPend = true; S.auto++; bump(); },
    state() { return JSON.parse(J({ p: S.p, g: S.g, enabled: S.enabled, pGen: S.pGen, gGen: S.gGen, auto: S.auto })); },
    zoneOf: ZONE, mapC,
  };
  const N = window.OmegasNative, presentOrig = N.getPresentSnapshot;
  const present = () => { const p = JSON.parse(presentOrig.call(N)); if (p.data) p.data.sequence = S.rev; p.revision = S.rev; return p; };
  Object.assign(N, {
    getPresentSnapshot: () => J(present()),
    getPresentSnapshotIfChanged: last => { if (Number(last) === S.rev) return J({ ok: true, changed: false, revision: S.rev, telemetryAgeMs: 60 }); return J(Object.assign({ changed: true }, present())); },
  });
  Object.assign(A, {
    getUiProjection: () => { window.__polls++; return J(projection()); },
    getNativeActionStatus: () => J(S.act),
    prepareNativeAction: action => { S.prep = { action }; return J({ ok: true, prepared: true, preparationId: 'prep-' + action, action, label: action, commandHex: BYTES[action] || '??', sessionId: 'sim-1' }); },
    preparePointDelete: (fuel, index) => { S.points = [{ fuel, index }]; S.prep = { action: 'DELETE_POINT' }; return J({ ok: true, prepared: true, preparationId: 'prep-point', action: 'DELETE_POINT' }); },
    preparePointDeleteBatch: t => { S.points = JSON.parse(t); S.prep = { action: 'DELETE_POINT' }; return J({ ok: true, prepared: true, preparationId: 'prep-point', action: 'DELETE_POINT' }); },
    executeNativeAction: id => {
      const action = (S.prep && S.prep.action) || String(id).replace('prep-', '');
      if (S.act && S.act.busy) return J({ ok: false, error: 'ECU ocupada com outra operação' });
      const fail = S.fail === true; S.fail = false;
      S.act = { ok: true, action, state: 'READING_AFTER', busy: true }; bump();
      setTimeout(() => finish(action, fail), 600);
      return J({ ok: true, started: true });
    },
    setAcquisitionEnabled: enable => {
      if (S.act && S.act.busy) return J({ ok: false, error: 'ECU ocupada com outra operação' });
      const action = enable ? 'ENABLE_AUTO_CAL' : 'DISABLE_AUTO_CAL';
      const fail = S.fail === true; S.fail = false;
      S.act = { ok: true, action, state: 'SENDING_ACTION', busy: true }; bump();
      setTimeout(() => finish(action, fail), 600);
      return J({ ok: true, started: true });
    },
    clearNativeActionPreparation: () => { S.prep = null; return J({ ok: true }); },
  });
  // Registro: cada chamada de ponte que e comando (nao leitura periodica) entra no log com o passo corrente.
  const CMD = /^(prepare|execute|clear|set|start|reset|connect|disconnect|freeze|restore|run)/;
  [['OmegasAutoCal', window.OmegasAutoCal], ['OmegasCalibration', window.OmegasCalibration], ['OmegasNative', window.OmegasNative]].forEach(([bn, o]) => {
    Object.keys(o).forEach(k => { if (!CMD.test(k)) return; const fn = o[k]; o[k] = function () { const r = fn.apply(this, arguments); let ret = r; try { ret = typeof r === 'string' ? r.slice(0, 160) : r; } catch (_) {} window.__cmds.push({ step: window.__step, bridge: bn, fn: k, args: [].slice.call(arguments).map(a => typeof a === 'string' ? a.slice(0, 120) : a), ret, bytes: k === 'prepareNativeAction' ? (BYTES[arguments[0]] || null) : k === 'setAcquisitionEnabled' ? BYTES[arguments[0] ? 'ENABLE_AUTO_CAL' : 'DISABLE_AUTO_CAL'] : null }); return r; }; });
  });
})();`;
