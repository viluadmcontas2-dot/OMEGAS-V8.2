'use strict';
// "Mundo" roteirizável: as quatro pontes (OmegasNative, OmegasCalibration, OmegasPower, OmegasAutoCal)
// com a MESMA superfície @JavascriptInterface do Kotlin (lida do código-fonte), respostas em JSON como
// a WebView entrega (string) e um modelo simples de ECU: cada operação de longa duração completa depois
// de N consultas a getLastOperation, com o desfecho escolhido pelo teste.
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');

const ROOT = path.resolve(__dirname, '../../..');
const KT = path.join(ROOT, 'app/src/main/java/com/omegas/prohub');
const BRIDGE_FILES = {
  OmegasNative: 'web/HubJavascriptBridge.kt',
  OmegasCalibration: 'web/CalibrationOperationsBridge.kt',
  OmegasPower: 'web/PowerJavascriptBridge.kt',
  OmegasAutoCal: 'autocal/AutoCalJavascriptBridge.kt',
};
const methodCache = {};
function methodNames(bridge) {
  if (!methodCache[bridge]) {
    const text = fs.readFileSync(path.join(KT, BRIDGE_FILES[bridge]), 'utf8');
    methodCache[bridge] = [...new Set([...text.matchAll(/@JavascriptInterface\s+fun\s+(\w+)/g)].map(m => m[1]))];
  }
  return methodCache[bridge];
}

// ---------------------------------------------------------------- fixtures reais
const REAL_DIR = path.join(ROOT, 'fixtures/autocal/real');
const realCache = {};
function realSession(name) {
  if (!realCache[name]) {
    const file = fs.readdirSync(REAL_DIR).find(f => f.startsWith(name));
    realCache[name] = JSON.parse(zlib.gunzipSync(fs.readFileSync(path.join(REAL_DIR, file))).toString('utf8'));
  }
  return realCache[name];
}
/** Quadros reais de telemetria (rpm, load_bar, petrol_ms, gas_ms_diagnostic, fuel). */
function realFrames(name, filter) {
  const frames = realSession(name).telemetry;
  return filter ? frames.filter(filter) : frames;
}

const PETROL_BINS = [2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18];
const RPM_BINS = [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500];
const Q = 16384;

function curvePoints(raws) {
  return raws.map((raw, index) => ({ index, petrolMs: 1.5 + index * 0.35, factor: raw / Q, factorRaw: raw }));
}
const neutralRaws = () => Array(30).fill(Q);
const bentRaws = () => neutralRaws().map((v, i) => (i >= 8 && i <= 12 ? Math.round(Q * 1.12) : i >= 20 && i <= 22 ? Math.round(Q * 0.95) : Q + i * 40));

/** Frame de telemetria no formato de TelemetryStateStore.liveJson() a partir de um quadro real. */
function liveFromFrame(frame) {
  const fuel = frame.fuel;
  return {
    rpm: frame.rpm, load_bar: frame.load_bar, petrol_ms: frame.petrol_ms, gas_ms_diagnostic: frame.gas_ms_diagnostic,
    petrol_2_ms_diagnostic: frame.petrol_2_ms_diagnostic, fuel, level_raw: 2048,
    sample: { state: 'FORMING_SAMPLE', reason: 'Formando amostra', reason_code: 'FORMING_SAMPLE', frame_count: 4, minimum_frames: 3, desired_frames: 6, duration_ms: 900, learning_eligible: true, fuel_confirmed: fuel, cell_row: 3, cell_column: 2, cell_key: '3:2', quality: 0.8 },
  };
}

// ---------------------------------------------------------------- equivalência por fase
const PHASES = ['SEM_ECU', 'LENDO_ECU', 'ECU_TRABALHANDO', 'COLETANDO_NOSSOS', 'PROPOSTA_PRONTA', 'VERIFICANDO', 'RESTAURAR_TRECHO', 'ESTAVEL', 'TENTATIVA_ENCERRADA'];
function equivalenceFor(phase, opts = {}) {
  const pilot = {
    phase, headline: `Fase ${phase}`, next: 'Siga dirigindo.', petrolValid: 8, gasValid: 6, autoMatchCount: 2, maxAutomatch: 3,
    ecuDoneReason: phase === 'ECU_TRABALHANDO' ? null : 'contador nativo', canDisconnect: phase === 'ESTAVEL',
  };
  if (phase === 'TENTATIVA_ENCERRADA') pilot.expiredFrom = opts.expiredFrom || 'PROPOSTA_PRONTA';
  const before = bentRaws();
  const after = before.map((v, i) => (i === 10 ? v + 400 : v));
  return {
    ok: true, available: true, index: 0.82, coverage: 0.6, provisional: false, ratio: 1.021, samples: 120,
    autopilot: pilot,
    denseBands: { petrol: [{ tpetMs: 3.1, mapBar: 0.52, samples: 20, rpmMedian: 2200, idleShare: 0, lastAtMs: 1 }], gas: [{ tpetMs: 3.2, mapBar: 0.5, samples: 14, rpmMedian: 2300, idleShare: 0, lastAtMs: 1 }] },
    stalls: { count: 0, nearCount: 0, regions: [], events: [] },
    refinement: { latest: opts.latest === undefined ? { status: 'VERIFICANDO', photoFile: 'foto-1.json', beforeRaw: before, afterRaw: after, bands: [] } : opts.latest, history: [] },
    restorePoints: phase === 'RESTAURAR_TRECHO' ? [{ index: 10, currentRaw: after[10], targetRaw: before[10] }] : [],
    points: [], reference: { frozen: false, canFreeze: true }, gasEpochReason: null, gasEpochAt: null,
  };
}
function refinedAnalysis(withProposal, curveRaws) {
  const raws = curveRaws || bentRaws();
  return {
    ok: true, available: withProposal, refinementMode: 'ECU_E_CONDUCAO', message: withProposal ? '' : 'aguardando evidência',
    matureCommonPoints: 6, minimumMatureCommonPoints: 4, telemetryTargets: 3, elasticityLimit: 1.5, guards: { maximumStepPercent: 6, lowGuardMs: 2 },
    rejectedBands: [],
    points: raws.map((raw, index) => ({
      index, currentRaw: raw, calculatedRaw: withProposal && index >= 8 && index <= 10 ? raw + 300 : raw,
      origin: withProposal && index >= 8 && index <= 10 ? 'MEASURED' : 'HELD', referenceTimeMs: 1.5 + index * 0.35,
    })),
  };
}

// ---------------------------------------------------------------- snapshot AutoCal (real)
// O que foi gravado nas fixtures só tem rawValues; o Kotlin acrescenta physicalValues pela unidade do campo
// (AutoCalProtocol.Field(..., "MS"|"BAR"|"FACTOR")). Reproduz a mesma conta (AutoCalScale).
let unitCache = null;
function fieldUnits() {
  if (!unitCache) {
    const text = fs.readFileSync(path.join(KT, 'ecu/AutoCalProtocol.kt'), 'utf8');
    unitCache = {};
    for (const m of text.matchAll(/Field\("(\w+)",\s*0x[0-9A-Fa-f]+,\s*Encoding\.\w+,\s*Shape\.\w+,\s*\d+,\s*"(\w+)"/g)) unitCache[m[1]] = m[2];
  }
  return unitCache;
}
function physicalize(snapshot) {
  const units = fieldUnits();
  const scale = { MS: 512, BAR: 1024, FACTOR: 16384 };
  return {
    ...snapshot,
    fields: snapshot.fields.map(f => {
      if (f.physicalValues || !Array.isArray(f.rawValues)) return f;
      const k = scale[units[f.key]];
      return { ...f, physicalValues: f.rawValues.map(v => (k ? v / k : v)) };
    }),
  };
}
function realProjection(name, index) {
  const session = realSession(name);
  const snapshot = physicalize(session.snapshots[Math.min(index == null ? session.snapshots.length - 1 : index, session.snapshots.length - 1)]);
  return {
    ok: true, source: 'NATIVE', sessionId: 'sess-real', referenceUsable: true, referenceAvailable: true, snapshotAvailable: true,
    snapshot: { available: true, ...snapshot },
    analysis: {},
    nativeStatus: { state: 'MONITORING', enabled: true, ok: true, message: 'Monitorando a ECU', latestSnapshot: { available: true, ...snapshot } },
    nativeSnapshot: { available: true, ...snapshot },
    manualStatus: { state: 'IDLE', ok: true },
    manualSnapshot: { available: false },
    liveAcquisitionEpoch: { available: false }, acquisitionZones: {}, correlation: [], correlationState: {},
  };
}

// ---------------------------------------------------------------- o mundo
class World {
  constructor(opts = {}) {
    this.clock = opts.clock || { now: 1_790_000_000_000 };
    this.calls = [];
    this.throwOn = new Set();
    this.raw = {}; // método -> valor bruto (string/qualquer) que sobrepõe a resposta
    this.seen = {}; // maior resposta já vista por método (base do fuzz)
    this.mutateResponse = null; // (bridge, method, obj) => obj, para o fuzz
    this.opPolls = opts.opPolls == null ? 1 : opts.opPolls;
    this.outcome = { curveRead: 'ok', curveWrite: 'ok', curveBackup: 'ok', curveReset: 'ok', restorePrepare: 'ok', mapRead: 'ok', mapWrite: 'ok', mapRestore: 'ok' };
    this.status = {
      serviceRunning: true, engineRunning: true, engineReady: true, engineStuck: false, usbConnected: true, usbDevice: 'MP48', usbPermissionPending: false,
      ecuState: 'RUNNING', fuelState: 'GASOLINA', rpm: 1800, petrolMs: 3.2, gasMs: 0, mapBar: 0.45, directTelemetryAgeMs: 100, lastError: '', calibrationBusy: false,
    };
    this.telemetry = { sequence: 1, updatedAt: this.clock.now, valid: true, live: liveFromFrame({ rpm: 1800, load_bar: 0.45, petrol_ms: 3.2, gas_ms_diagnostic: null, fuel: 'GASOLINA' }) };
    this.sessions = [];
    this.sessionStatus = { recording: false, events: 0, megabytes: 0, settings: { telemetryEveryMs: 250, maxSessionMb: 256, keepSessions: 20, autoStartOnUsb: true, captureRawUsb: false } };
    this.logs = [];
    this.curve = bentRaws();
    this.photos = [];
    this.map = { rows: Array.from({ length: 12 }, (_, r) => Array.from({ length: 12 }, (_, c) => 120 + r * 2 + c)), photos: [] };
    this.op = { idle: true };
    this.lastOperation = { ok: true, state: 'IDLE', busy: false };
    this.mapReadResult = { ok: true, state: 'IDLE', busy: false };
    this.mapPhotoSeq = 0;
    this.equivalence = equivalenceFor('ESTAVEL');
    this.refined = refinedAnalysis(false, this.curve);
    this.projection = realProjection('ref_', 0);
    this.autocalActionStatus = { busy: false, state: 'IDLE' };
    this.autocalAutoCleanup = { ok: true, active: true, enabled: true, pauseCode: null, relearnedThisSession: 0, recentDeletes: [] };
    this.autocalAvailable = true;
    this.overlay = { ok: true, supported: true, permissionGranted: true, requestedEnabled: false, visible: false, observationalOnly: true };
    this.battery = { supported: true, ignoringOptimizations: false };
    this.releaseIdentity = { product: 'OMEGAS', engine: 'MP48', versionName: 'teste', generation: 'V8' };
  }

  // ---- telemetria
  setFrame(frame, extra) {
    this.telemetry = { sequence: this.telemetry.sequence + 1, updatedAt: this.clock.now, valid: true, live: { ...liveFromFrame(frame), ...(extra || {}) } };
    this.status = { ...this.status, fuelState: frame.fuel, rpm: frame.rpm, petrolMs: frame.petrol_ms, gasMs: frame.gas_ms_diagnostic, mapBar: frame.load_bar };
  }
  /**
   * Revisões por tipo como o Kotlin as publica (RuntimeSnapshotBus): só sobem quando o dado muda de verdade.
   * Aqui "mudou" = outra referência de objeto (os testes atribuem objetos novos) ou outro conteúdo da curva.
   */
  revisions() {
    const sig = {
      evidence: [this.equivalence, this.refined],
      tables: [this.projection, this.curve.join(','), this.photos.length, this.map],
      session: [this.sessions, this.sessionStatus, this.logs],
    };
    this._rev = this._rev || { evidence: 1, tables: 1, session: 1 };
    this._sig = this._sig || {};
    for (const kind of Object.keys(sig)) {
      const before = this._sig[kind];
      if (!before || before.some((v, i) => v !== sig[kind][i])) { if (before) this._rev[kind] += 1; this._sig[kind] = sig[kind]; }
    }
    return { live: this.telemetry.sequence, evidence: this._rev.evidence, tables: this._rev.tables, session: this._rev.session };
  }
  ageMs() { return this.telemetry.updatedAt ? Math.max(0, this.clock.now - this.telemetry.updatedAt) : -1; }
  presentEnvelope() {
    const t = this.telemetry;
    const age = this.ageMs();
    const live = t.live;
    const data = {
      sequence: t.sequence, updatedAt: t.updatedAt, ageMs: age, valid: t.valid, sessionId: 1, live, runtime: {}, ok: true, telemetryAgeMs: age, revisions: this.revisions(),
      interpolation: { valid: t.valid, educationalOnly: true, method: 'BILINEAR', rpm: live.rpm, petrolMs: live.petrol_ms, mapBar: live.load_bar, cell: { row: 3, column: 2, continuousWeights: [{ row: 3, column: 2, weight: 1 }] } },
    };
    return { ok: true, revision: t.sequence, data };
  }

  // ---- operações de longa duração
  startOp(kind, queuedState, extra) {
    if (this.op.busy) return { ok: false, busy: true, error: 'Outra operação V8 está em andamento' };
    this.op = { busy: true, kind, polls: this.opPolls, extra: extra || {} };
    this.lastOperation = { ok: true, state: queuedState, busy: true, progress: 0 };
    return { ok: true, started: true, state: queuedState, busy: true };
  }
  finishOp() {
    const { kind, extra } = this.op;
    this.op = { idle: true };
    const out = this.outcome[kind.group] || 'ok';
    const failure = (state, extraKeys) => ({ ok: false, state, busy: false, ...(out === 'transport' ? { failureKind: 'TRANSPORTE', error: 'USB desconectado' } : out === 'nack' ? { failureKind: 'ECU', error: 'ECU retornou status 0xCA' } : { failureKind: 'APP', error: 'Falha do app' }), ...(extraKeys || {}) });
    switch (kind.name) {
      case 'curveRead': {
        if (out !== 'ok') return failure('CURVE_READ_FAILED');
        return { ok: true, state: 'COMPLETED', busy: false, points: curvePoints(this.curve), pointCount: 30, hash: 'hash-read', minimumFactor: 0.6, maximumFactor: 3.99 };
      }
      case 'curveBackup': {
        if (out !== 'ok') return failure('CURVE_BACKUP_FAILED');
        const fileName = `curva-${this.photos.length + 1}.json`;
        this.photos.unshift({ fileName, label: extra.label, createdAt: this.clock.now, type: 'MANUAL_SNAPSHOT', raws: this.curve.slice() });
        return { ok: true, state: 'COMPLETED', busy: false, hash: 'abcdef123456', publicPath: 'Download/Omegas/' + fileName, fileName, curve: { points: curvePoints(this.curve) } };
      }
      case 'restorePrepare': {
        if (out !== 'ok') return failure('CURVE_RESTORE_PREPARE_FAILED');
        const photo = this.photos.find(p => p.fileName === extra.fileName) || { raws: neutralRaws(), label: 'x' };
        const points = photo.raws.map((raw, index) => ({ index, petrolMs: 1.5 + index * 0.35, currentRaw: this.curve[index], targetRaw: raw, currentFactor: this.curve[index] / Q, targetFactor: raw / Q, deltaPercent: (raw / this.curve[index] - 1) * 100 })).filter(p => p.currentRaw !== p.targetRaw);
        return { ok: true, state: 'COMPLETED', busy: false, points, currentCurve: { points: curvePoints(this.curve) }, fileName: extra.fileName, hash: 'h', createdAt: this.clock.now, label: photo.label };
      }
      case 'curveWrite': case 'curveReset': {
        const photoFile = `foto-${this.photos.length + 1}.json`;
        const target = kind.name === 'curveReset' ? neutralRaws() : this.curve.map((v, i) => { const p = (extra.points || []).find(x => x.index === i); return p ? p.targetRaw : v; });
        if (out === 'ok') {
          const changed = target.filter((v, i) => v !== this.curve[i]).length;
          this.photos.unshift({ fileName: photoFile, label: 'Antes da gravação', createdAt: this.clock.now, type: 'AUTO', raws: this.curve.slice() });
          this.curve = target;
          return { ok: true, state: 'BATCH_CONFIRMED', busy: false, progress: 100, readbackValid: true, photoFile, details: { changedPoints: changed, readbackValid: true } };
        }
        if (out === 'partial') {
          this.photos.unshift({ fileName: photoFile, label: 'Antes da gravação', createdAt: this.clock.now, type: 'AUTO', raws: this.curve.slice() });
          const upTo = kind.name === 'curveReset' ? 5 : Math.max(1, Math.floor((extra.points || []).length / 2) || 1);
          const touched = kind.name === 'curveReset' ? new Set([0, 1, 2, 3, 4]) : new Set((extra.points || []).slice(0, upTo).map(x => x.index));
          this.curve = this.curve.map((v, i) => (touched.has(i) ? target[i] : v));
          return { ok: false, state: 'CURVE_WRITE_FAILED', busy: false, partial: true, mutationMayHaveStarted: true, photoFile, failureKind: 'TRANSPORTE', error: 'USB desconectado no ponto 5' };
        }
        if (out === 'readback') return { ok: false, state: 'CURVE_WRITE_FAILED', busy: false, readbackValid: false, failureKind: 'APP', error: 'O readback não confirmou a gravação' };
        return failure(kind.name === 'curveReset' ? 'CURVE_RESET_FAILED' : 'CURVE_WRITE_FAILED');
      }
      case 'mapWrite': case 'mapRestore': {
        const id = `adj-${++this.mapPhotoSeq}`;
        const cells = extra.cells || [];
        if (out === 'ok') {
          const before = this.map.rows.map(r => r.slice());
          this.map.photos.unshift({ id, before });
          cells.forEach(c => { this.map.rows[c.row][c.column] = c.target; });
          return { ok: true, state: 'BATCH_CONFIRMED', busy: false, progress: 100, readbackValid: true, confirmedCells: cells.length, totalCells: cells.length, adjustmentIds: [id] };
        }
        if (out === 'partial') { const before = this.map.rows.map(r => r.slice()); this.map.photos.unshift({ id, before }); cells.slice(0, 2).forEach(c => { this.map.rows[c.row][c.column] = c.target; }); }
        if (out === 'partial') return { ok: false, state: 'BATCH_PARTIAL_FAILED', busy: false, partial: true, ecuPartiallyChanged: true, confirmedCells: 2, totalCells: cells.length, adjustmentIds: [id], failureKind: 'TRANSPORTE', error: 'USB desconectado' };
        return { ok: false, state: 'BATCH_PARTIAL_FAILED', busy: false, confirmedCells: 0, totalCells: cells.length, ...(out === 'nack' ? { failureKind: 'ECU', error: 'ECU retornou status 0xCA' } : { failureKind: 'TRANSPORTE', error: 'USB desconectado' }) };
      }
      case 'mapRestorePrepare': {
        if (out !== 'ok') return failure('MAP_RESTORE_PREPARE_FAILED');
        const photo = this.map.photos.find(p => p.id === extra.id);
        const cells = [];
        if (photo) photo.before.forEach((row, r) => row.forEach((v, c) => { if (this.map.rows[r][c] !== v) cells.push({ row: r, column: c, current: this.map.rows[r][c], target: v }); }));
        return { ok: true, state: 'COMPLETED', busy: false, cells };
      }
      default: return { ok: false, state: 'UNKNOWN', busy: false };
    }
  }
  pollOperation() {
    if (this.op.busy) {
      this.op.polls -= 1;
      if (this.op.polls <= 0) {
        const done = this.finishOp();
        if (this.pendingMapRead) { this.pendingMapRead = false; }
        this.lastOperation = done;
      } else {
        this.lastOperation = { ...this.lastOperation, busy: true, progress: 40 };
      }
    }
    return { ...this.lastOperation, busy: !!this.op.busy };
  }
  mapReadPoll() {
    if (this.mapRead && this.mapRead.polls > 0) {
      this.mapRead.polls -= 1;
      if (this.mapRead.polls === 0) {
        const out = this.outcome.mapRead;
        this.mapReadResult = out === 'ok'
          ? { ok: true, state: 'COMPLETED', busy: false, rows: this.map.rows.map(r => r.slice()), extraRow: Array(12).fill(0), axes: { petrolBins: PETROL_BINS, rpmBins: RPM_BINS }, hash: 'maphash', writableCells: 144, sessionConfirmed: true }
          : { ok: false, state: 'FAILED', busy: false, failureKind: out === 'nack' ? 'ECU' : 'TRANSPORTE', error: out === 'nack' ? 'ECU retornou status 0xCA' : 'USB desconectado' };
      }
    }
    return this.mapReadResult;
  }

  // ---- respostas por método (o objeto devolvido vira JSON)
  handlers() {
    const w = this;
    const kind = (name, group) => ({ name, group });
    const native = {
      getReleaseIdentity: () => w.releaseIdentity,
      getStatus: () => ({ ...w.status, directTelemetryAgeMs: w.status.usbConnected ? w.ageMs() : -1 }),
      getPresentSnapshot: () => w.presentEnvelope(),
      getPresentSnapshotIfChanged: last => (Number(last) === w.telemetry.sequence ? { ok: true, changed: false, sequence: w.telemetry.sequence, telemetryAgeMs: w.ageMs(), revisions: w.revisions() } : w.presentEnvelope()),
      getLiveTelemetry: () => w.presentEnvelope().data,
      getFullEngineSnapshot: () => w.presentEnvelope().data,
      connectUsb: () => true,
      disconnectUsb: () => null,
      runEngineSelfTests: () => ({ ok: true, passed: 12 }),
      startKMapRead: () => { w.mapRead = { polls: w.opPolls }; w.mapReadResult = { ok: true, state: 'READING', busy: true }; return { ok: true, started: true, state: 'READING' }; },
      getKMapReadResult: () => w.mapReadPoll(),
      previewKFactorPoint: (index, target) => {
        const raw = w.curve[Number(index)];
        if (raw === undefined) return { ok: false, error: 'Ponto inválido' };
        const targetRaw = Math.round(Number(target) * Q);
        return { ok: true, index: Number(index), petrolMs: 1.5 + Number(index) * 0.35, currentFactor: raw / Q, targetFactor: Number(target), currentRaw: raw, targetRaw, deltaPercent: (Number(target) / (raw / Q) - 1) * 100, changed: targetRaw !== raw };
      },
      getSessionRecorderStatus: () => w.sessionStatus,
      listRecordedSessions: () => w.sessions,
      setSessionRecorderSettings: (every, max, keep, auto, raw) => { w.sessionStatus = { ...w.sessionStatus, settings: { telemetryEveryMs: every, maxSessionMb: max, keepSessions: keep, autoStartOnUsb: auto, captureRawUsb: raw } }; return { ok: true, ...w.sessionStatus }; },
      startSessionRecording: () => ({ ok: true, recording: true }),
      stopSessionRecording: () => ({ ok: true, recording: false }),
      exportSession: () => true,
      getLogs: () => w.logs,
      exportLogs: () => true,
      exportData: () => true,
    };
    const calibration = {
      getLastOperation: () => w.pollOperation(),
      previewMapAdjustment: (cellsJson, mode, adj) => {
        const items = JSON.parse(cellsJson).map(c => {
          const current = Number(c.current);
          const raw = mode === 'percent' ? current * (1 + Number(adj) / 100) : mode === 'delta' ? current + Number(adj) : Number(adj);
          const target = Math.max(100, Math.min(255, Math.round(raw)));
          return { row: c.row, column: c.column, current, target, changed: target !== current };
        });
        return { ok: true, mode, adjustment: Number(adj), minimumK: 100, maximumK: 255, automatic: false, requiresReview: true, items };
      },
      startCurveRead: () => w.startOp(kind('curveRead', 'curveRead'), 'CURVE_READING'),
      startCurveBackup: label => w.startOp(kind('curveBackup', 'curveBackup'), 'CURVE_BACKUP_SAVING', { label }),
      listCurveBackups: () => w.photos.map(p => ({ fileName: p.fileName, label: p.label, createdAt: p.createdAt, type: p.type })),
      startCurveRestorePrepare: fileName => w.startOp(kind('restorePrepare', 'restorePrepare'), 'CURVE_RESTORE_PREPARING', { fileName }),
      listMapBackups: () => w.map.photos.map(p => ({ id: p.id })),
      startMapRestorePrepare: id => w.startOp(kind('mapRestorePrepare', 'mapRestore'), 'MAP_RESTORE_PREPARING', { id }),
      startMapRestoreWrite: (cellsJson) => w.startOp(kind('mapRestore', 'mapRestore'), 'MAP_K_QUEUED', { cells: JSON.parse(cellsJson) }),
      startCurveReset: () => w.startOp(kind('curveReset', 'curveReset'), 'CURVE_RESET_QUEUED'),
      startCurveBatchWrite: (pointsJson) => w.startOp(kind('curveWrite', 'curveWrite'), 'CURVE_WRITE_QUEUED', { points: JSON.parse(pointsJson) }),
      startCurveRestoreWrite: (pointsJson) => w.startOp(kind('curveWrite', 'curveWrite'), 'CURVE_WRITE_QUEUED', { points: JSON.parse(pointsJson) }),
      startMapBatchWrite: (cellsJson) => w.startOp(kind('mapWrite', 'mapWrite'), 'MAP_K_QUEUED', { cells: JSON.parse(cellsJson) }),
    };
    const power = {
      getBatteryOptimizationStatus: () => w.battery,
      requestBatteryOptimizationExemption: () => ({ ok: true, supported: true, launched: true }),
      getOverlayStatus: () => w.overlay,
      requestOverlayPermissionAndEnable: () => ({ ok: true, permissionRequired: !w.overlay.permissionGranted, launched: true }),
      setOverlayEnabled: enabled => { w.overlay = { ...w.overlay, requestedEnabled: !!enabled, visible: !!enabled }; return w.overlay; },
      setOverlayScale: scale => ({ ok: true, scale }),
    };
    const autocal = {
      getIdentity: () => ({ ok: true, name: 'AutoCal' }),
      getStatus: () => w.projection.manualStatus,
      getSnapshot: () => w.projection.manualSnapshot,
      getNativeMonitorStatus: () => w.projection.nativeStatus,
      getNativeMonitorSnapshot: () => w.projection.nativeSnapshot,
      getUiProjection: () => w.projection,
      getSessionLedgerStatus: () => ({ ok: true }),
      listAutoCalSessions: () => [],
      exportAutoCalSession: () => true,
      getNativeActionStatus: () => w.autocalActionStatus,
      getAutoCleanupStatus: () => w.autocalAutoCleanup,
      startRead: () => ({ ok: true, started: true }),
      cancelRead: () => ({ ok: true }),
      setAcquisitionEnabled: enabled => ({ ok: true, enabled }),
      prepareNativeAction: action => ({ ok: true, prepared: true, preparationId: 'prep-1', action, label: action, description: 'Prévia', commandHex: '00', sessionId: 's' }),
      executeNativeAction: () => ({ ok: true, started: true }),
      clearNativeActionPreparation: () => ({ ok: true }),
      preparePointDelete: () => ({ ok: true, prepared: true, preparationId: 'prep-2' }),
      preparePointDeleteBatch: () => ({ ok: true, prepared: true, preparationId: 'prep-3' }),
      getRefinedAnalysis: () => w.refined,
      getEquivalence: () => w.equivalence,
      getEquivalenceFresh: () => w.equivalence,
      getRefinementPhase: () => ({ ok: true, autopilot: w.equivalence && w.equivalence.autopilot }),
      getEquivalenceResult: () => w.equivalence,
      freezeReference: () => ({ ok: true }),
      restorePreviousReference: () => ({ ok: true }),
    };
    return { OmegasNative: native, OmegasCalibration: calibration, OmegasPower: power, OmegasAutoCal: autocal };
  }

  /** Objetos de ponte como a WebView injeta: só métodos @JavascriptInterface; devolvem string JSON (ou primitivo). */
  makeBridges() {
    const handlers = this.handlers();
    const bridges = {};
    for (const [bridgeName, table] of Object.entries(handlers)) {
      if (bridgeName === 'OmegasAutoCal' && !this.autocalAvailable) continue;
      const obj = {};
      for (const method of methodNames(bridgeName)) {
        obj[method] = (...args) => {
          this.calls.push({ bridge: bridgeName, method, args, at: this.clock.now });
          if (this.throwOn.has(method) || this.throwOn.has(`${bridgeName}.${method}`)) throw new Error(`falha injetada em ${method}`);
          if (Object.prototype.hasOwnProperty.call(this.raw, method)) return this.raw[method];
          const fn = table[method];
          if (!fn) return '{}';
          let result = fn(...args);
          if (typeof result === 'boolean') return result;
          if (result === null || result === undefined) return undefined;
          const json = JSON.stringify(result);
          if (!this.seen[method] || json.length > JSON.stringify(this.seen[method]).length) this.seen[method] = JSON.parse(json);
          if (this.mutateResponse) result = this.mutateResponse(bridgeName, method, JSON.parse(json));
          return JSON.stringify(result);
        };
      }
      bridges[bridgeName] = obj;
    }
    return bridges;
  }
  callsOf(method) { return this.calls.filter(c => c.method === method); }
  mark() { return this.calls.length; }
  since(mark) { return this.calls.slice(mark); }
}

module.exports = { World, realFrames, realSession, realProjection, equivalenceFor, refinedAnalysis, PHASES, curvePoints, bentRaws, neutralRaws, methodNames, PETROL_BINS, RPM_BINS, Q, liveFromFrame };
