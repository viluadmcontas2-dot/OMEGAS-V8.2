import {
  CAPABILITY,
  NEXT_SCHEMA,
  capabilitySet,
  makeError,
  parseBridgeJson,
  requireCapability,
  revisionEvent,
} from './next-contract.js';

const MAP_READ_TIMEOUT_MS = 180_000;
const CURVE_READ_TIMEOUT_MS = 120_000;
const nativeRevisionListeners = new Set();
let nativeRefreshInstalled = false;

function bridge(name) {
  return globalThis?.[name] || null;
}

function hasMethod(name, method) {
  return typeof bridge(name)?.[method] === 'function';
}

function call(name, method, ...args) {
  const target = bridge(name);
  if (!target || typeof target[method] !== 'function') {
    throw makeError(
      'NATIVE_METHOD_UNAVAILABLE',
      'Esta função ainda não está disponível na ponte Android desta versão.',
      `${name}.${method}`,
      { source: 'ANDROID_BRIDGE', recoverable: true },
    );
  }
  return target[method](...args);
}

function installNativeRefreshMultiplexer() {
  if (nativeRefreshInstalled) return;
  nativeRefreshInstalled = true;
  const previous = typeof globalThis.__OMEGAS_NATIVE_REFRESH__ === 'function'
    ? globalThis.__OMEGAS_NATIVE_REFRESH__
    : null;
  globalThis.__OMEGAS_NATIVE_REFRESH__ = (snapshot) => {
    try { previous?.(snapshot); } catch (_) {}
    const root = snapshot && typeof snapshot === 'object' ? snapshot : {};
    const event = revisionEvent({
      type: 'NATIVE_REFRESH',
      sequence: Number(root.sequence ?? 0),
      sessionId: Number(root.nativeSessionId ?? root.sessionId ?? 0),
      updatedAt: Number(root.updatedAt ?? Date.now()),
      structural: false,
      reason: 'TelemetryForegroundService.stateChanged',
    });
    for (const listener of [...nativeRevisionListeners]) {
      try { listener(event); } catch (_) {}
    }
  };
}

function nextFrame() {
  return new Promise((resolve) => requestAnimationFrame(resolve));
}

function normalizeMapRows(root) {
  const rows = Array.isArray(root?.allRows) ? root.allRows : Array.isArray(root?.rows) ? root.rows : [];
  if (rows.length < 12) throw makeError('MAP_READ_INCOMPLETE', 'A ECU não devolveu as 12 linhas editáveis do Mapa K.', `rows=${rows.length}`, { source: 'ANDROID_BRIDGE' });
  return rows.slice(0, 12).map((row, rowIndex) => {
    if (!Array.isArray(row) || row.length !== 12) {
      throw makeError('MAP_ROW_INCOMPLETE', 'Uma linha do Mapa K veio incompleta.', `row=${rowIndex}; columns=${row?.length ?? 0}`, { source: 'ANDROID_BRIDGE' });
    }
    return row.map((value) => Number(value));
  });
}

function rawToFactor(raw) {
  const value = Number(raw);
  return Number.isFinite(value) ? value / 16384 : NaN;
}

function axisRawToPetrolMs(raw) {
  const value = Number(raw);
  return Number.isFinite(value) ? value * 0.05 : NaN;
}

function normalizeCurve(root) {
  const factorsRaw = root?.factorsRaw;
  const axisRaw = root?.axisRaw;
  if (!Array.isArray(factorsRaw) || factorsRaw.length !== 30 || !Array.isArray(axisRaw) || axisRaw.length !== 30) {
    throw makeError('CURVE_READ_INCOMPLETE', 'A ECU não devolveu os 30 pontos reais da Curva K.', `factors=${factorsRaw?.length ?? 0}; axis=${axisRaw?.length ?? 0}`, { source: 'ANDROID_BRIDGE' });
  }
  return factorsRaw.map((raw, index) => ({
    index,
    petrolMs: axisRawToPetrolMs(axisRaw[index]),
    factor: rawToFactor(raw),
    raw: Number(raw),
  }));
}

function normalizeFast(root) {
  const live = root?.live || root?.telemetry || {};
  const interpolation = root?.interpolation || {};
  const cell = interpolation?.cell || {};
  const weights = Array.isArray(cell?.continuousWeights) ? cell.continuousWeights.slice(0, 4) : [];
  return {
    schema: NEXT_SCHEMA.fastTelemetry,
    sequence: Number(root?.sequence ?? 0),
    capturedAtMs: Number(root?.updatedAt ?? 0),
    ageMs: Number(root?.ageMs ?? root?.telemetryAgeMs ?? -1),
    valid: root?.valid === true,
    sessionId: Number(root?.sessionId ?? root?.nativeSessionId ?? 0),
    rpm: Number(live?.rpm ?? 0),
    petrolMs: Number(live?.petrol_ms ?? live?.petrolMs ?? 0),
    gasMsDiagnostic: live?.gas_ms_diagnostic == null ? null : Number(live.gas_ms_diagnostic),
    mapBar: Number(live?.load_bar ?? live?.map_bar ?? live?.mapBar ?? 0),
    fuel: String(live?.fuel ?? live?.state ?? 'DESCONHECIDO'),
    engineState: String(live?.state ?? live?.link ?? 'SEM_DADO'),
    liveTrace: {
      valid: interpolation?.valid === true,
      sequence: Number(root?.sequence ?? 0),
      updatedAt: Number(root?.updatedAt ?? 0),
      row: Number(cell?.row ?? -1),
      column: Number(cell?.column ?? -1),
      weights: weights.map((item) => ({ row: Number(item.row), column: Number(item.column), weight: Number(item.weight) })),
      method: 'BILINEAR_RPM_X_PETROL_MS',
      educationalOnly: true,
      affectsLearning: false,
      affectsCalibration: false,
    },
  };
}

function humanSuggestion(item) {
  const lifecycle = ({
    PENDING: 'PENDENTE',
    OBSERVING: 'OBSERVANDO',
    APPLIED: 'APLICADA',
    SUPERSEDED: 'SUPERADA',
  })[item?.lifecycle] || 'OBSERVANDO';
  const actionable = item?.actionable === true;
  return {
    id: String(item?.id || ''),
    createdAt: Number(item?.createdAt ?? 0),
    updatedAt: Number(item?.updatedAt ?? 0),
    target: String(item?.target || ''),
    targetLabel: item?.target === 'CURVE_K' ? 'Curva K global' : 'Mapa K local',
    lifecycle,
    actionable,
    confidence: Number(item?.confidence ?? 0),
    supportState: String(item?.stabilityState || ''),
    reason: String(item?.rationale || ''),
    whatIsMissing: actionable ? 'Pronta para revisão humana; nunca para escrita automática.' : 'A evidência atual ainda não autoriza uma alteração.',
    mapChanges: Array.isArray(item?.mapChanges) ? item.mapChanges : [],
    curveChanges: Array.isArray(item?.curveChanges) ? item.curveChanges : [],
    automaticWrite: false,
    requiresReview: lifecycle === 'PENDENTE',
  };
}

export class NativeNextAdapter {
  #lastMap = null;
  #lastCurve = null;

  identity() {
    return Object.freeze({
      schema: NEXT_SCHEMA.adapter,
      mode: 'ANDROID',
      source: 'ECU_NATIVE',
      dataFictional: false,
      native: true,
      transport: 'LEGACY_BRIDGES_ENCAPSULATED',
      product: 'OMEGAS V8.2 NEXT',
    });
  }

  capabilities() {
    return capabilitySet({
      [CAPABILITY.FAST_TELEMETRY]: { available: hasMethod('OmegasNative', 'getLiveTelemetry') },
      [CAPABILITY.LEARNING_STATUS]: { available: hasMethod('OmegasNative', 'getLearningSyncStatus') },
      [CAPABILITY.CELL_SEMANTICS]: { available: false, reason: 'A fachada Kotlin de semântica da célula ainda não foi publicada nesta ponte.' },
      [CAPABILITY.PREDICTOR]: { available: hasMethod('OmegasV7', 'getState') },
      [CAPABILITY.MAP_READ]: { available: hasMethod('OmegasNative', 'startKMapRead') && hasMethod('OmegasNative', 'getKMapReadResult') },
      [CAPABILITY.MAP_PREVIEW]: { available: hasMethod('OmegasV7', 'previewMapAdjustment') },
      [CAPABILITY.MAP_WRITE]: { available: false, reason: 'Writer NEXT permanece fechado até o gate de confirmação/readback.' },
      [CAPABILITY.CURVE_READ]: { available: hasMethod('OmegasV7', 'startCurveRead') && hasMethod('OmegasV7', 'getLastOperation') },
      [CAPABILITY.CURVE_PREVIEW]: { available: hasMethod('OmegasNative', 'previewKFactorPoint') },
      [CAPABILITY.CURVE_WRITE]: { available: false, reason: 'Writer NEXT permanece fechado até o gate de confirmação/readback.' },
      [CAPABILITY.AUTOCAL_STATUS]: { available: false, reason: 'Estado AutoCal limpo ainda não foi publicado na fachada NEXT.' },
      [CAPABILITY.AUTOCAL_ACTIONS]: { available: false, reason: 'Ações AutoCal permanecem bloqueadas até a fachada NEXT com revisão crítica.' },
      [CAPABILITY.OBD_WITNESS]: { available: hasMethod('OmegasNative', 'getObdStatus') },
      [CAPABILITY.SUGGESTIONS]: { available: hasMethod('OmegasV7', 'getState') },
      [CAPABILITY.REVISION_EVENTS]: { available: true },
    });
  }

  subscribeRevisions(listener) {
    if (typeof listener !== 'function') return () => {};
    installNativeRefreshMultiplexer();
    nativeRevisionListeners.add(listener);
    return () => nativeRevisionListeners.delete(listener);
  }

  async fastTelemetry() {
    const unavailable = requireCapability(this, CAPABILITY.FAST_TELEMETRY);
    if (unavailable) throw unavailable;
    return normalizeFast(parseBridgeJson(call('OmegasNative', 'getLiveTelemetry')));
  }

  async learningStatus() {
    const unavailable = requireCapability(this, CAPABILITY.LEARNING_STATUS);
    if (unavailable) throw unavailable;
    return parseBridgeJson(call('OmegasNative', 'getLearningSyncStatus'));
  }

  async cellContext() {
    throw requireCapability(this, CAPABILITY.CELL_SEMANTICS);
  }

  async predictorSnapshot() {
    const unavailable = requireCapability(this, CAPABILITY.PREDICTOR);
    if (unavailable) throw unavailable;
    const root = parseBridgeJson(call('OmegasV7', 'getState'));
    const predictor = root?.predictor;
    if (!predictor || typeof predictor !== 'object') {
      throw makeError('PREDICTOR_UNAVAILABLE', 'O Predictor nativo ainda não está disponível nesta sessão.', 'calibration.predictor ausente', { source: 'ANDROID_BRIDGE' });
    }
    return { ...predictor, schema: NEXT_SCHEMA.predictor, revision: JSON.stringify(root?.revision || {}), automaticWrite: false };
  }

  async readMapK() {
    const unavailable = requireCapability(this, CAPABILITY.MAP_READ);
    if (unavailable) throw unavailable;
    const started = parseBridgeJson(call('OmegasNative', 'startKMapRead'));
    if (started?.ok !== true) throw makeError('MAP_READ_START_FAILED', started?.error || 'Não foi possível iniciar a leitura do Mapa K.', '', { source: 'ANDROID_BRIDGE' });
    const result = await this.#waitForOperation('OmegasNative', 'getKMapReadResult', MAP_READ_TIMEOUT_MS);
    if (result?.ok !== true) throw makeError('MAP_READ_FAILED', result?.error || 'Falha ao ler o Mapa K.', '', { source: 'ANDROID_BRIDGE' });
    const map = normalizeMapRows(result);
    this.#lastMap = map;
    return {
      schema: NEXT_SCHEMA.mapK,
      state: 'READY',
      map,
      selection: [],
      proposal: null,
      sourceRevision: String(result?.hash || result?.updatedAt || result?.finishedAt || ''),
      technicalRowProtected: true,
      writableCells: 144,
      source: 'ECU_ACK_READBACK',
    };
  }

  async previewMapK(selection, delta) {
    const unavailable = requireCapability(this, CAPABILITY.MAP_PREVIEW);
    if (unavailable) throw unavailable;
    if (!this.#lastMap) throw makeError('MAP_READ_REQUIRED', 'Leia o Mapa K real antes de preparar uma alteração.', '', { action: 'Reler ECU', source: 'ANDROID_BRIDGE' });
    const cells = (selection || []).map(({ row, column }) => ({ row, column, current: this.#lastMap?.[row]?.[column] }));
    const root = parseBridgeJson(call('OmegasV7', 'previewMapAdjustment', JSON.stringify(cells), 'delta', Number(delta)));
    if (root?.ok !== true) throw makeError('MAP_PREVIEW_FAILED', root?.error || 'Não foi possível preparar a alteração do Mapa K.', '', { source: 'ANDROID_BRIDGE' });
    const items = Array.isArray(root?.items) ? root.items : [];
    return {
      summary: `${items.length} célula(s) • delta ${Number(delta) >= 0 ? '+' : ''}${Number(delta)} • nenhuma escrita`,
      delta: Number(delta),
      changes: items.map((item) => ({ row: Number(item.row), column: Number(item.column), before: Number(item.current), after: Number(item.target) })),
      automaticWrite: false,
      humanConfirmationRequired: true,
      simulatedOnly: false,
      source: 'KOTLIN_MAP_MANUAL_PLANNER',
    };
  }

  async readCurveK() {
    const unavailable = requireCapability(this, CAPABILITY.CURVE_READ);
    if (unavailable) throw unavailable;
    const started = parseBridgeJson(call('OmegasV7', 'startCurveRead'));
    if (started?.ok !== true) throw makeError('CURVE_READ_START_FAILED', started?.error || 'Não foi possível iniciar a leitura da Curva K.', '', { source: 'ANDROID_BRIDGE' });
    const result = await this.#waitForOperation('OmegasV7', 'getLastOperation', CURVE_READ_TIMEOUT_MS);
    if (result?.ok !== true) throw makeError('CURVE_READ_FAILED', result?.error || 'Falha ao ler a Curva K.', '', { source: 'ANDROID_BRIDGE' });
    const points = normalizeCurve(result);
    this.#lastCurve = points;
    return {
      schema: NEXT_SCHEMA.curveK,
      state: 'READY',
      revision: String(result?.updatedAt || result?.finishedAt || ''),
      perspective: 'adjust',
      points,
      prepared: [],
      sourceConfirmed: true,
      pointCount: 30,
      source: 'ECU_ACK_READBACK',
    };
  }

  async previewCurveK(index, delta) {
    const unavailable = requireCapability(this, CAPABILITY.CURVE_PREVIEW);
    if (unavailable) throw unavailable;
    const point = this.#lastCurve?.[index];
    if (!point) throw makeError('CURVE_READ_REQUIRED', 'Leia a Curva K real antes de preparar uma alteração.', '', { action: 'Reler ECU', source: 'ANDROID_BRIDGE' });
    const requested = Number(point.factor) + Number(delta);
    const root = parseBridgeJson(call('OmegasNative', 'previewKFactorPoint', Number(index), requested));
    if (root?.ok !== true) throw makeError('CURVE_PREVIEW_FAILED', root?.error || 'Não foi possível preparar o ponto da Curva K.', '', { source: 'ANDROID_BRIDGE' });
    return {
      index: Number(root.index),
      petrolMs: Number(root.petrolMs),
      before: Number(root.currentFactor),
      after: Number(root.targetFactor),
      automaticWrite: false,
      humanConfirmationRequired: true,
      simulatedOnly: false,
      source: 'KOTLIN_K_FACTOR_MANUAL_PLANNER',
    };
  }

  async autoCalStatus() {
    throw requireCapability(this, CAPABILITY.AUTOCAL_STATUS);
  }

  async curveComparison() {
    const learning = await this.learningStatus();
    return {
      state: learning?.reference_count > 0 ? 'READY' : 'UNAVAILABLE',
      globalErrorPct: null,
      confidence: Number(learning?.reference_confidence ?? learning?.quality ?? 0),
      direction: 'UNKNOWN',
      gasolineCoverage: 0,
      cngCoverage: 0,
      localResidualNote: 'Comparação global completa aguarda a fachada NEXT estrutural; resíduos locais permanecem no Mapa K.',
      source: 'LEARNING_STATUS_ONLY',
    };
  }

  async obdSnapshot() {
    const unavailable = requireCapability(this, CAPABILITY.OBD_WITNESS);
    if (unavailable) throw unavailable;
    const raw = parseBridgeJson(call('OmegasNative', 'getObdStatus'));
    const updatedAt = Number(raw?.updatedAt ?? 0);
    const ageMs = updatedAt > 0 ? Math.max(0, Date.now() - updatedAt) : null;
    const stale = ageMs != null && ageMs > 5000;
    return {
      schema: NEXT_SCHEMA.obd,
      state: String(raw?.mode || 'off').toLowerCase() === 'off' ? 'OFF' : stale ? 'STALE' : raw?.connected === true ? 'VALIDO' : 'CONECTANDO',
      connected: raw?.connected === true,
      mode: String(raw?.mode || 'off'),
      updatedAt: updatedAt || null,
      ageMs,
      fuel: raw?.fuel || null,
      stftPct: stale || raw?.stft == null ? null : Number(raw.stft),
      ltftPct: stale || raw?.ltft == null ? null : Number(raw.ltft),
      rpm: stale || raw?.rpm == null ? null : Number(raw.rpm),
      mapKpa: stale || raw?.mapKpa == null ? null : Number(raw.mapKpa),
      loadPct: stale || raw?.load == null ? null : Number(raw.load),
      coolantC: stale || raw?.coolant == null ? null : Number(raw.coolant),
      closedLoop: raw?.closedLoop === true,
      pidAvailability: {},
      observationalOnly: true,
      ecuAuthority: false,
      learningAuthority: false,
      automaticCalibration: false,
      layers: raw?.layers || {},
      source: 'LEGACY_OBD_STATUS_ENCAPSULATED',
    };
  }

  async suggestionsSnapshot() {
    const unavailable = requireCapability(this, CAPABILITY.SUGGESTIONS);
    if (unavailable) throw unavailable;
    const state = parseBridgeJson(call('OmegasV7', 'getState'));
    const items = Array.isArray(state?.suggestionItems) ? state.suggestionItems.map(humanSuggestion) : [];
    return {
      schema: NEXT_SCHEMA.suggestions,
      state: 'READY',
      items,
      activeCount: items.filter((item) => item.lifecycle === 'PENDENTE' || item.lifecycle === 'OBSERVANDO').length,
      readyCount: items.filter((item) => item.actionable).length,
      automaticWrite: false,
      humanSelectionRequired: true,
      source: 'V7_SESSION_RUNTIME',
    };
  }

  async #waitForOperation(bridgeName, method, timeoutMs) {
    const startedAt = performance.now();
    while (performance.now() - startedAt < timeoutMs) {
      const result = parseBridgeJson(call(bridgeName, method));
      if (result?.busy !== true && !['READING_CURVE', 'READING_MAP', 'READING'].includes(result?.state)) return result;
      await nextFrame();
    }
    throw makeError('NATIVE_OPERATION_TIMEOUT', 'A operação nativa não respondeu dentro do limite.', `${bridgeName}.${method}; timeoutMs=${timeoutMs}`, { action: 'Reler ECU', source: 'ANDROID_BRIDGE' });
  }
}

export const nativeNextAdapter = new NativeNextAdapter();