// Mock native bridge. Real data: fixtures/autocal/real (snapshot + telemetry replay). SYNTHETIC: Mapa K, refino/equivalencia, sessoes.
(function () {
  window.__ifc = { same: 0, changed: 0 };
  const S = window.__S = Object.assign({ phase: 'COLETANDO_NOSSOS', resetHold: false, mapFail: false, curveNeutral: false, proposal: false }, window.__SCN || {});
  const D = window.__DATA, MODE = window.__MODE || 'connected';
  const T0 = performance.now();
  const frames = D.frames;
  const J = o => JSON.stringify(o);
  const snap = JSON.parse(JSON.stringify(D.snapshot));
  const SC = { PETR_INJ_TBP: 512, MNFLD_PRESS_THD: 1024, PETR_INJ_TBUF: 512, PETR_INJ_TBUF_GAS: 512, PETR_INJ_TBUF_GAS_PREV: 512, MNFLD_PRESS_BUF: 1024, MNFLD_PRESS_BUF_GAS: 1024, MNFLD_PRESS_BUF_GAS_PREV: 1024, PETR_MNFLD_PRESS_RV: 1024, GAS_MNFLD_PRESS_RV: 1024, MUL_ACT: 16384 };
  snap.available = true;
  snap.fields.forEach(f => { f.physicalValues = SC[f.key] ? f.rawValues.map(v => v / SC[f.key]) : f.rawValues.slice(); });
  // Cenários de preview (window.__SCN): zonesGas/zonesPetrol [0|1 x4], autoMatch, maxAutoMatch, noUndo, noBetween, noRefinoState, noStalls, paused.
  const setScalar = (key, v) => { const f = snap.fields.find(x => x.key === key); if (f) { f.rawValues = [v]; f.physicalValues = [v]; f.status = 'VALID'; } else snap.fields.push({ key, status: 'VALID', rawValues: [v], physicalValues: [v], capturedAtMs: Date.now() }); };
  setScalar('AUTO_CAL_ENABLE', S.paused ? 0 : 1);
  setScalar('MAX_AUTOMATCH', S.maxAutoMatch != null ? S.maxAutoMatch : 3);
  if (S.autoMatch != null) setScalar('NUM_AUTOMATCH_EXECUTED', S.autoMatch);
  const setVec = (key, v) => { const f = snap.fields.find(x => x.key === key); if (f) { f.rawValues = v.slice(); f.physicalValues = v.slice(); } };
  if (S.zonesGas) setVec('ACQUIRED_ZONES_GAS', S.zonesGas);
  if (S.zonesPetrol) setVec('ACQUIRED_ZONES_PETROL', S.zonesPetrol);
  const field = k => snap.fields.find(f => f.key === k);
  const axis = field('PETR_INJ_TBP').rawValues.map(v => v / 512);
  const mul = field('MUL_ACT').rawValues;

  let idx = 0, replayStart = null;
  function frameIdx() {
    if (replayStart === null) replayStart = performance.now();
    const el = (performance.now() - replayStart) * (window.__SPEED || 1);
    // frames sorted by dt; loop
    const total = frames[frames.length - 1].dt;
    const e = el % total;
    let lo = idx < frames.length && frames[idx].dt <= e ? idx : 0;
    while (lo + 1 < frames.length && frames[lo + 1].dt <= e) lo++;
    idx = lo; return lo;
  }
  window.__resetReplay = () => { replayStart = performance.now(); idx = 0; };
  function row(r, c) { return 116 + r * 2 + Math.round(c * 0.7); }
  function liveOf(f, i) {
    const gas = f.fuel === 'GNV';
    const map = f.load_bar;
    // rpm bins/ petrol bins for cell
    const rb = [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500];
    const pb = [2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18];
    const col = Math.max(0, rb.findIndex(v => v >= f.rpm)); const rw = Math.max(0, pb.findIndex(v => v >= (f.petrol_ms || 0)));
    return {
      rpm: f.rpm, petrol_ms: f.petrol_ms, gas_ms_diagnostic: f.gas_ms_diagnostic, load_bar: map, fuel: f.fuel,
      sample_state: 'FORMING_SAMPLE', sample_reason: 'Formando amostra 7/10 leituras', sample_frame_count: 7, sample_minimum_frames: 6, sample_desired_frames: 10,
      sample: { state: 'FORMING_SAMPLE', reason: 'Formando amostra 7/10 leituras', reason_code: 'FORMING_SAMPLE', frame_count: 7, minimum_frames: 6, desired_frames: 10, duration_ms: 1780, learning_eligible: false, fuel_confirmed: f.fuel, window_age_ms: 1780, window_budget_ms: 3000, cell_row: rw, cell_column: col, cell_key: rw + ':' + col, quality: 0.82 },
      _cell: { rw, col },
    };
  }
  function present() {
    if (MODE !== 'connected') return { ok: true, revision: 1, data: { valid: false, ageMs: -1, telemetryAgeMs: -1, live: {} } };
    const i = frameIdx(), f = frames[i], live = liveOf(f, i);
    const c = live._cell; delete live._cell;
    return { ok: true, revision: i + 1, data: { ok: true, valid: true, sequence: i + 1, ageMs: 60, telemetryAgeMs: 60, updatedAt: Date.now(), live,
      interpolation: { valid: f.petrol_ms > 0, educationalOnly: true, method: 'BILINEAR_RPM_X_PETROL_MS', rpm: f.rpm, petrolMs: f.petrol_ms, mapBar: f.load_bar, cell: { row: c.rw, column: c.col, continuousWeights: [{ row: c.rw, column: c.col, weight: 0.6 }, { row: c.rw, column: c.col + 1, weight: 0.4 }] } } } };
  }
  const status = () => MODE === 'connected'
    ? { serviceRunning: true, engineRunning: true, engineReady: true, engineStuck: false, usbConnected: true, usbPermissionPending: false, fuelState: frames[idx].fuel, rpm: frames[idx].rpm, petrolMs: frames[idx].petrol_ms, gasMs: frames[idx].gas_ms_diagnostic, mapBar: frames[idx].load_bar, directTelemetryAgeMs: 60, wakeLockHeld: true }
    : MODE === 'connecting'
      ? { serviceRunning: true, engineRunning: false, engineReady: false, engineStuck: false, usbConnected: false, usbPermissionPending: true, fuelState: '--' }
      : { serviceRunning: true, engineRunning: false, engineReady: false, engineStuck: false, usbConnected: false, usbPermissionPending: false, fuelState: '--' };

  // ---- Mapa K (SYNTHETIC) ----
  const PB = [2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18], RB = [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500];
  const mapRows = Array.from({ length: 12 }, (_, r) => Array.from({ length: 12 }, (_, c) => Math.min(255, row(r, c) + ((r * 7 + c * 3) % 5))));
  // ---- Curva K REAL + operações (SYNTHETIC state machine) ----
  const realPoints = axis.map((ms, i) => ({ index: i, petrolMs: ms, factor: mul[i] / 16384, factorRaw: mul[i] }));
  const neutralPoints = axis.map((ms, i) => ({ index: i, petrolMs: ms, factor: 1, factorRaw: 16384 }));
  const curPts = () => S.curveNeutral ? neutralPoints : realPoints;
  let op = { ok: true, state: 'IDLE', busy: false };
  const timers = [];
  const later = (ms, fn) => timers.push(setTimeout(fn, ms));
  window.__release = () => { S.resetHold = false; if (window.__heldFinish) { const f = window.__heldFinish; window.__heldFinish = null; f(); } };
  const curveRead = () => { op = { ok: true, state: 'READING', busy: true }; later(500, () => { const pts = curPts(); op = { ok: true, state: 'COMPLETED', busy: false, points: pts, pointCount: 30, minimumFactor: 0.6, maximumFactor: 3.99, hash: 'real-automatch-seq2262' }; }); return J({ ok: true, started: true, state: 'CURVE_READING' }); };
  const calibration = {
    startCurveRead: curveRead,
    getLastOperation: () => J(op),
    listCurveBackups: () => J([{ fileName: 'curve_antes_do_reset.json', label: 'Antes do reset', createdAt: Date.now() - 600000 }]),
    listMapBackups: () => '[]',
    startCurveBackup: label => { op = { ok: true, state: 'BACKUP', busy: true }; later(400, () => { op = { ok: true, state: 'COMPLETED', busy: false, hash: 'h123', publicPath: '/sdcard/omegas/curve_antes_do_reset.json', fileName: 'curve_antes_do_reset.json', curve: { points: curPts() } }; }); return J({ ok: true, started: true }); },
    startCurveReset: () => {
      op = { ok: true, state: 'BATCH_WRITING', busy: true, progress: 40, message: 'Escrita 12 de 30 · ACK' };
      const finish = () => { S.curveNeutral = true; op = { ok: true, state: 'BATCH_CONFIRMED', busy: false, readbackValid: true, progress: 100, photoFile: 'curve_antes_do_reset.json', details: { changedPoints: 30 } }; };
      if (S.resetHold) window.__heldFinish = finish; else later(1500, finish);
      return J({ ok: true, started: true });
    },
    startCurveRestorePrepare: f => { op = { ok: true, state: 'COMPLETED', busy: false, points: [] }; return J({ ok: true, started: true }); },
    startCurveRestoreWrite: () => { op = { ok: true, state: 'BATCH_CONFIRMED', busy: false, readbackValid: true }; return J({ ok: true, started: true }); },
    startCurveBatchWrite: () => { op = { ok: true, state: 'BATCH_CONFIRMED', busy: false, readbackValid: true, photoFile: 'curve_foto.json' }; return J({ ok: true, started: true }); },
    previewMapAdjustment: (cells, mode, adj) => { const items = JSON.parse(cells).map(c => { const cur = Number(c.current); const raw = mode === 'percent' ? cur * (1 + adj / 100) : mode === 'delta' ? cur + adj : adj; const t = Math.max(100, Math.min(255, Math.round(raw))); return { row: c.row, column: c.column, current: cur, target: t, changed: t !== cur }; }); return J({ ok: true, mode, adjustment: adj, minimumK: 100, maximumK: 255, automatic: false, requiresReview: true, items }); },
    startMapBatchWrite: () => {
      op = { ok: true, state: 'BATCH_WRITING', busy: true, progress: 60, confirmedCells: 5, totalCells: 12, writerMessage: 'Escrevendo células' };
      later(1200, () => { op = S.mapFail ? { ok: false, state: 'BATCH_PARTIAL_FAILED', busy: false, partial: true, ecuPartiallyChanged: true, mutationMayHaveStarted: true, confirmedCells: 5, totalCells: 12, failureKind: 'TRANSPORTE', error: 'a porta USB caiu na célula 6 (SINTÉTICO)', adjustmentIds: ['adj_1'] } : { ok: true, state: 'BATCH_CONFIRMED', busy: false, readbackValid: true, confirmedCells: 12, totalCells: 12, adjustmentIds: ['adj_1'] }; });
      return J({ ok: true, started: true });
    },
    startMapRestorePrepare: () => J({ ok: false }), startMapRestoreWrite: () => J({ ok: false }),
  };
  // ---- Refino / equivalencia (denseBands REAL-derived from replay frames; ratio/journal SYNTHETIC) ----
  function dense() {
    const bins = { GNV: {}, GASOLINA: {} };
    frames.forEach(f => { if (!bins[f.fuel] || !(f.petrol_ms > 0)) return; const k = Math.floor(f.load_bar / 0.025); (bins[f.fuel][k] = bins[f.fuel][k] || []).push(f); });
    const med = a => { const s = a.slice().sort((x, y) => x - y); return s[s.length >> 1]; };
    const lane = b => Object.entries(b).filter(([, v]) => v.length >= 5).map(([k, v]) => ({ mapBar: (+k + 0.5) * 0.025, tpetMs: med(v.map(x => x.petrol_ms)), samples: v.length, lastAtMs: Date.now() - 30000, rpmMedian: med(v.map(x => x.rpm)), idleShare: 0 }));
    return { petrol: lane(bins.GASOLINA), gas: lane(bins.GNV), binBar: 0.025, minSamples: 5 };
  }
  // betweenPoints (SINTÉTICO a partir da telemetria REAL): um item por intervalo entre bandas adjacentes da ECU (17).
  function betweenPoints() {
    const thd = field('MNFLD_PRESS_THD').physicalValues, d = dense();
    const rvMap = field('PETR_MNFLD_PRESS_RV').physicalValues;
    const msAt = bar => { for (let i = 1; i < rvMap.length; i++) { if (rvMap[i - 1] <= bar && bar <= rvMap[i]) { const t = (bar - rvMap[i - 1]) / ((rvMap[i] - rvMap[i - 1]) || 1); return axis[i - 1] + t * (axis[i] - axis[i - 1]); } } return axis[Math.min(axis.length - 1, 10)]; };
    const lane = (list, lo, hi) => { const m = list.filter(x => x.mapBar > lo && x.mapBar <= hi); const n = m.reduce((a, x) => a + x.samples, 0); return n >= 5 ? { ms: m.reduce((a, x) => a + x.tpetMs * x.samples, 0) / n, mapBar: m.reduce((a, x) => a + x.mapBar * x.samples, 0) / n, n } : null; };
    return Array.from({ length: 17 }, (_, i) => { const lo = thd[i], hi = thd[i + 1], mid = (lo + hi) / 2; const gas = lane(d.gas, lo, hi), petrol = lane(d.petrol, lo, hi); return { index: i, centerMs: msAt(mid), centerMapBar: mid, gas, petrol, n: (gas ? gas.n : 0) + (petrol ? petrol.n : 0), visits: gas ? 3 : 0, state: gas ? 'coletado' : 'falta' }; });
  }
  const RS = {
    COLETANDO_NOSSOS: { phase: 'Medindo o GNV', whatNow: 'Dirija normalmente: o app está medindo o GNV entre os pontos da ECU.', nextAction: '', reason: 'Ainda faltam medidas em alguns trechos.' },
    PROPOSTA_PRONTA: { phase: 'Ajuste pronto', whatNow: 'Falta 1 ajuste para o GNV chegar perto da gasolina.', nextAction: 'Aplicar ajuste', reason: '' },
    VERIFICANDO: { phase: 'Conferindo', whatNow: 'Ajuste aplicado. Dirija normalmente: o app confere se o GNV chegou perto da gasolina.', nextAction: '', reason: 'Nada a fazer agora.' },
    ESTAVEL: { phase: 'Estável', whatNow: 'GNV perto da gasolina em toda a curva. Pode desconectar.', nextAction: '', reason: '' },
    SEM_ECU: { phase: 'Sem ECU', whatNow: 'Conecte a ECU para o Refino medir o GNV.', nextAction: '', reason: 'O cabo USB não está conectado.' },
  };
  const NEXT = { COLETANDO_NOSSOS: { kind: 'COLLECT', text: 'Dirija no GNV: faltam 6 faixas para medir.', route: 'refino', subpage: '', pointIndexes: [12, 13, 14] }, PROPOSTA_PRONTA: { kind: 'REVIEW', text: 'A curva refinada está pronta. Revise e grave.', route: 'refino', subpage: '', pointIndexes: [3, 6, 9] } };
  const NOW = Date.now();
  function stallsBlock() {
    if (S.stalls === 'absent' || S.stalls === 'nodata') return undefined;
    if (S.stalls === 'none') return { count: 0, nearCount: 0, restartedCount: 0, regions: [], events: [] };
    return { count: 4, nearCount: 3, restartedCount: 1, events: [],
      regions: [
        { mapBar: 0.32, rpm: 1040, ms: 2.4, count: 3, firstAt: NOW - 25 * 60e3, lastAt: NOW - 4 * 60e3, curvePoints: [2, 3, 4], proposal: S.stalls === 'noproposal' ? undefined : { pointIndexes: [2, 3, 4], deltaPercent: 2.5 } },
        { mapBar: 0.55, rpm: 1210, ms: 3.1, count: 2, firstAt: NOW - 18 * 60e3, lastAt: NOW - 9 * 60e3, curvePoints: [5, 6] },
        { mapBar: 0.46, rpm: 1680, ms: 3.8, count: 1, firstAt: NOW - 40 * 60e3, lastAt: NOW - 40 * 60e3, curvePoints: [7] },
      ] };
  }
  const eq = () => S.stalls === 'nodata' ? { ok: false } : ({ ok: true, ...(S.noBetween ? {} : { betweenPoints: S.gasReset ? betweenPoints().map(b => ({ ...b, gas: null })) : betweenPoints() }), ...(S.noRefinoState ? {} : { refinoState: Object.assign({ technical: {} }, RS[S.phase] || RS.COLETANDO_NOSSOS, { counts: { intervalsTotal: 17, intervalsCollected: 12, intervalsMissing: 5, ecuAutoMatchCount: 1, ecuAutoMatchMax: 3, pointsToWrite: 3 } }) }), index: 0.74, coverage: 0.62, provisional: true, nextAction: NEXT[S.phase] || NEXT.COLETANDO_NOSSOS, points: Array.from({ length: 30 }, (_, i) => ({ index: i, axisMs: axis[i], state: i < 18 ? 'EQUIVALENTE' : 'SEM_DADOS', mixture: 1.0 + (i % 5) * 0.01 })), reference: { frozen: false, canFreeze: true }, ratio: 1.034, samples: 637, petrolObservations: 240, gasObservations: S.gasReset ? 0 : 397, gasEpochReason: '', gasEpochAt: 0, petrolReference: 'MISTA', ecuPetrolPoints: 14,
    bands: [], denseBands: S.gasReset ? { ...dense(), gas: [] } : dense(), restorePoints: [],
    refinement: { ok: true, count: 1, automatic: false, latest: S.noUndo ? { status: 'SEM_BASE', bands: [] } : { status: 'VERIFICANDO', photoFile: 'curve_foto.json', bands: [{ fromMs: 2, toMs: 3, ratioBefore: 1.06, ratioAfter: 1.02, verdict: 'CONFIRMADA' }, { fromMs: 3, toMs: 4, ratioBefore: 1.05, ratioAfter: 1.01, verdict: 'PASSOU' }, { fromMs: 4, toMs: 6, ratioBefore: 1.07, ratioAfter: null, verdict: 'COLETANDO' }], beforeRaw: mul, afterRaw: mul.map((v, i) => i % 3 ? v : v + 120) }, history: [{ id: 'x', appliedAt: Date.now() - 3600e3, status: 'VERIFICADO', ratioBefore: 1.06, ratioAfter: 1.02 }] },
    autopilot: { phase: S.phase, headline: 'Coletando os nossos pontos no GNV', next: 'Dirija normalmente: o app compara GNV com gasolina por faixa.', petrolValid: 14, gasValid: 11, autoMatchCount: 1, maxAutomatch: 3, ecuDoneReason: null, canDisconnect: false },
    stalls: stallsBlock(), fluidity: S.stalls === 'absent' ? undefined : { gasolina: { index: 0.94, jerks: 0, samples: 212 }, gnv: S.stalls === 'none' ? { index: 0.92, jerks: 0, samples: 305 } : { index: 0.71, jerks: 5, samples: 305 } } });
  const refined = () => ({ ok: true, available: true, refinementMode: 'ECU_E_CONDUCAO', evidenceSource: 'ECU_E_CONDUCAO', matureCommonPoints: 9, minimumMatureCommonPoints: 6, telemetryTargets: 5, rejectedBands: [{ fuel: 'GNV', band: 1, timeMs: 1.2, mapBar: 0.3 }], guards: { lowGuardMs: 2.0, maximumStepPercent: 4 }, elasticityLimit: 1.5,
    points: mul.map((v, i) => ({ index: i, referenceTimeMs: axis[i], currentRaw: v, calculatedRaw: (S.phase === 'PROPOSTA_PRONTA' && i % 3 === 0) ? v + 120 : v, origin: i % 3 ? 'HELD' : 'MEASURED' })) });

  const acq = k => field(k).rawValues.map(v => v > 0);
  const projection = () => ({ ok: true, source: 'NATIVE_MONITOR', freshness: 'FRESH', referenceUsable: true, snapshot: snap, analysis: {}, nativeSnapshot: snap, nativeStatus: { ok: true, state: 'RUNNING', enabled: true, message: 'Monitorando', latestSnapshot: snap }, manualStatus: {}, manualSnapshot: { available: false },
    acquisitionZones: { petrol: acq('ACQUIRED_ZONES_PETROL'), gas: acq('ACQUIRED_ZONES_GAS') }, correlation: [], correlationState: {} });

  const learning = () => { const cells = []; for (let r = 0; r < 12; r++) for (let c = 0; c < 12; c++) if ((r + c) % 3 === 0) cells.push({ row: r, column: c, key: r + ':' + c, samples: 12 + ((r * 7 + c * 5) % 55), visits: 2, sessions: 1, confidence: 0.6, stage: 'ACCEPTED' });
    return { ok: true, grid: { rows: 12, columns: 12, petrolBins: PB, rpmBins: RB }, cells, petrol: cells.map(x => ({ ...x, fuel: 'PETROL' })), cng: cells.map(x => ({ ...x, fuel: 'CNG', epoch: 1 })), comparisons: cells.map((x, i) => ({ ...x, errorPercent: ((i % 9) - 4) * 0.9 })), assistedCalibration: { comparisonCount: cells.length, uniqueVisitCount: 18, petrolCurve: [], cngCurve: [], kFactorSuggestions: [], reconciliation: { pending_cng_visits: 0 } }, current: { fuel: 'GNV', rpm: 2100, petrolMs: 4.2, mapBar: 0.56, cell: { row: 4, column: 3 } } }; };

  const native = {
    previewKFactorPoint: (i, t) => { const p = curPts()[i]; const raw = Math.round(Number(t) * 16384); return J({ ok: true, index: Number(i), petrolMs: p.petrolMs, currentFactor: p.factor, targetFactor: Number(t), currentRaw: p.factorRaw, targetRaw: raw, deltaPercent: (Number(t) / p.factor - 1) * 100, changed: raw !== p.factorRaw }); },
    getReleaseIdentity: () => J({ product: 'OMEGAS', generation: 'Platina', versionName: '8.2.0 (build 214)', engine: 'Motor V8' }),
    getStatus: () => J(status()), getPresentSnapshot: () => J(present()),
    getPresentSnapshotIfChanged: last => { const p = present(); const seq = p.data && p.data.sequence != null ? p.data.sequence : p.revision; if (Number(last) === seq) { window.__ifc.same++; return J({ ok: true, changed: false, revision: p.revision, telemetryAgeMs: 60 }); } window.__ifc.changed++; return J(Object.assign({ changed: true }, p)); },
    getScienceSnapshotSince: r => J({ ok: true, changed: Number(r) === 0, revision: 1, refreshing: false, data: { learning: learning(), calibrationState: { ready: true, suggestionItems: [], predictor: { ok: true, cells: [] } }, predictor: { ok: true, cells: [] } } }),
    getLiveTelemetry: () => J(present().data), getFullEngineSnapshot: () => J(present().data), getLearningMaps: () => J(learning()),
    getLearningSyncStatus: () => J({ live: { state: 'OBSERVING' } }), getLearningToleranceSettings: () => J({ ok: true, policy: {}, controlModel: { ok: true, levels: ['Muito rigoroso', 'Rigoroso', 'Equilibrado', 'Flexível', 'Muito flexível'], controls: [] } }),
    getSessionRecorderStatus: () => J({ recording: MODE === 'connected', events: 4210, megabytes: 3.2, settings: { autoStartOnUsb: true, telemetryEveryMs: 250, captureRawUsb: false, maxSessionMb: 256, keepSessions: 20 } }),
    listRecordedSessions: () => J(S.sessions === 'none' ? [] : [
      { id: 'session_2026-10-04_08-12-40', reason: 'Conexão da ECU', bytes: 1250000, durationMs: 640000, active: MODE === 'connected', cngTicks: 410, petrolTicks: 120, semanticSummary: { autoMatchExecuted: 1, blackouts: 0 } },
      { id: 'session_2026-10-03_17-45-02', reason: 'Conexão da ECU', bytes: 6800000, durationMs: 3120000, active: false, cngTicks: 2400, petrolTicks: 380, semanticSummary: { autoMatchExecuted: 2, blackouts: 2 }, index: { start: 0.52, end: 0.81 } },
      { id: 'session_2026-10-01_13-01-22', reason: 'Conexão da ECU', bytes: 3400000, durationMs: 1260000, active: false, cngTicks: 640, petrolTicks: 210, semanticSummary: { autoMatchExecuted: 0, blackouts: 1 }, index: { start: 0.41, end: 0.78 } },
      { id: 'session_2026-09-30_09-31-05', reason: 'Conexão da ECU', bytes: 2100000, durationMs: 900000, active: false, cngTicks: 120, petrolTicks: 300, semanticSummary: { autoMatchExecuted: 1 }, indexStart: 0.2, indexEnd: 0.41 },
      { id: 'session_2026-09-29_18-00-00', reason: 'Conexão da ECU', bytes: 800000, durationMs: 300000, active: false, cngTicks: 0, petrolTicks: 90, semanticSummary: { autoMatchExecuted: 0, blackouts: 0 } },
    ]),
    getLogs: () => J([{ time: '08:12:41', level: 'INFO', category: 'USB', message: 'ECU conectada' }, { time: '08:12:44', level: 'INFO', category: 'SESSÃO', message: 'Gravação iniciada' }, { time: '08:19:03', level: 'WARN', category: 'ECU', message: 'Leitura lenta; tentando de novo' }]), startKMapRead: () => J({ ok: true, started: true, state: 'READING' }),
    getKMapReadResult: () => J({ ok: true, state: 'COMPLETED', rows: mapRows, extraRow: Array(12).fill(0), axes: { petrolBins: PB, rpmBins: RB }, hash: 'synthetic', writableCells: 144, sessionConfirmed: true }),
    connectUsb: () => 'true', disconnectUsb: () => 'true', runEngineSelfTests: () => J({ ok: true }),
  };
  const autocal = {
    getIdentity: () => J({}), getStatus: () => J({ ok: true, state: 'IDLE' }), getSnapshot: () => J({ available: false }), getNativeMonitorStatus: () => J(projection().nativeStatus), getNativeMonitorSnapshot: () => J(snap),
    getUiProjection: () => J(projection()), getSessionLedgerStatus: () => J({}), listAutoCalSessions: () => '[]', getNativeActionStatus: () => J({}),
    resetGasLearning: () => { S.gasReset = true; S.phase = 'COLETANDO_NOSSOS'; return J({ ok: true, message: 'Aprendizado GNV reiniciado. A gasolina continua como referência.' }); },
    getRefinedAnalysis: () => J(refined()), getEquivalence: () => J(eq()), getEquivalenceFresh: () => J(eq()), getRefinementPhase: () => J({ ok: true, autopilot: eq().autopilot }), getEquivalenceResult: () => J({ ...eq(), available: true }),
  };
  const power = { getBatteryOptimizationStatus: () => J({ supported: true, ignoringOptimizations: true }), getOverlayStatus: () => J({ ok: true, supported: true, permissionGranted: true, requestedEnabled: false, visible: false }) };
  window.OmegasNative = native; window.OmegasAutoCal = autocal; window.OmegasPower = power; window.OmegasCalibration = calibration;
  window.__mockCalls = {};
  [['N', native], ['C', calibration], ['A', autocal], ['P', power]].forEach(([n, o]) => Object.keys(o).forEach(k => { const fn = o[k]; o[k] = function () { window.__mockCalls[n + '.' + k] = (window.__mockCalls[n + '.' + k] || 0) + 1; return fn.apply(this, arguments); }; }));
})();
