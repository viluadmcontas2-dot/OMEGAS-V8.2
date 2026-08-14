export const NEXT_SCHEMA = Object.freeze({
  adapter: 'omegas-next-adapter-v1',
  fastTelemetry: 'omegas-next-fast-v1',
  revisionEvent: 'omegas-next-revision-event-v1',
  cellSemantics: 'omegas-next-cell-semantics-v1',
  predictor: 'omegas-next-predictor-v1',
  mapK: 'omegas-next-map-k-v1',
  curveK: 'omegas-next-curve-k-v1',
  autocal: 'omegas-next-autocal-v1',
  obd: 'omegas-next-obd-v1',
  suggestions: 'omegas-next-suggestions-v1',
  error: 'omegas-next-error-v1',
});

export const CAPABILITY = Object.freeze({
  FAST_TELEMETRY: 'fastTelemetry',
  LEARNING_STATUS: 'learningStatus',
  CELL_SEMANTICS: 'cellSemantics',
  PREDICTOR: 'predictor',
  MAP_READ: 'mapRead',
  MAP_PREVIEW: 'mapPreview',
  MAP_WRITE: 'mapWrite',
  CURVE_READ: 'curveRead',
  CURVE_PREVIEW: 'curvePreview',
  CURVE_WRITE: 'curveWrite',
  AUTOCAL_STATUS: 'autocalStatus',
  AUTOCAL_ACTIONS: 'autocalActions',
  OBD_WITNESS: 'obdWitness',
  SUGGESTIONS: 'suggestions',
  REVISION_EVENTS: 'revisionEvents',
});

export function makeError(code, message, technical = '', options = {}) {
  return Object.freeze({
    schema: NEXT_SCHEMA.error,
    ok: false,
    code: String(code || 'UNKNOWN_ERROR'),
    message: String(message || 'Não foi possível concluir a operação.'),
    technical: technical ? String(technical) : null,
    recoverable: options.recoverable !== false,
    action: options.action || null,
    source: options.source || 'NEXT_ADAPTER',
  });
}

export function capabilitySet(values = {}) {
  const result = {};
  Object.values(CAPABILITY).forEach((key) => {
    const raw = values[key];
    result[key] = Object.freeze({
      available: raw?.available === true,
      reason: raw?.reason || (raw?.available === true ? '' : 'Indisponível neste ambiente.'),
    });
  });
  return Object.freeze(result);
}

export function assertNextAdapter(adapter) {
  const required = [
    'identity', 'capabilities', 'subscribeRevisions', 'fastTelemetry', 'learningStatus', 'cellContext',
    'predictorSnapshot', 'readMapK', 'previewMapK', 'readCurveK', 'previewCurveK',
    'autoCalStatus', 'obdSnapshot', 'suggestionsSnapshot',
  ];
  for (const method of required) {
    if (typeof adapter?.[method] !== 'function') {
      throw new Error(`NEXT adapter inválido: ${method} ausente`);
    }
  }
  return adapter;
}

export function requireCapability(adapter, capability) {
  const status = adapter.capabilities()?.[capability];
  if (status?.available) return null;
  return makeError(
    'CAPABILITY_UNAVAILABLE',
    status?.reason || 'Esta função não está disponível neste ambiente.',
    `capability=${capability}`,
    { recoverable: true, action: null, source: adapter.identity()?.mode || 'UNKNOWN' },
  );
}

export function revisionEvent(payload = {}) {
  return Object.freeze({
    schema: NEXT_SCHEMA.revisionEvent,
    type: payload.type || 'NATIVE_REFRESH',
    sequence: Number(payload.sequence ?? 0),
    sessionId: Number(payload.sessionId ?? 0),
    updatedAt: Number(payload.updatedAt ?? Date.now()),
    structural: payload.structural === true,
    reason: payload.reason || '',
  });
}

export function parseBridgeJson(raw, fallbackMessage = 'Resposta nativa inválida.') {
  try {
    if (raw == null || raw === '') return {};
    if (typeof raw === 'object') return raw;
    return JSON.parse(String(raw));
  } catch (error) {
    throw makeError('INVALID_NATIVE_JSON', fallbackMessage, error?.message || String(error), { recoverable: true, source: 'ANDROID_BRIDGE' });
  }
}