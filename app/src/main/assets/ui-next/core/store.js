export const UI_STATE = Object.freeze({
  READY: 'READY',
  BUSY: 'BUSY',
  STALE: 'STALE',
  UNAVAILABLE: 'UNAVAILABLE',
  SUCCESS: 'SUCCESS',
  FAILURE: 'FAILURE',
});

const initialState = Object.freeze({
  revision: 0,
  route: 'agora',
  sessionId: 0,
  epoch: 0,
  telemetry: Object.freeze({ valid: false, ageMs: -1 }),
  learning: Object.freeze({ state: UI_STATE.UNAVAILABLE }),
  suggestions: Object.freeze({ state: UI_STATE.UNAVAILABLE, items: [], activeCount: 0, readyCount: 0 }),
  cellContext: null,
  contextualEditor: Object.freeze({ kind: null, open: false, originRoute: null }),
  mapK: Object.freeze({ state: UI_STATE.UNAVAILABLE, selection: [], proposal: null, sourceRevision: null, draftBlocked: false, confirmationBlockedReason: null }),
  curveK: Object.freeze({ state: UI_STATE.UNAVAILABLE, prepared: [], sourceRevision: null, draftBlocked: false, confirmationBlockedReason: null }),
  autocal: Object.freeze({ state: UI_STATE.UNAVAILABLE }),
  predictor: Object.freeze({ state: UI_STATE.UNAVAILABLE }),
  obd: Object.freeze({ state: UI_STATE.UNAVAILABLE }),
  visual: Object.freeze({ liveTracing: true, floating: false, splitCompact: false }),
  globalError: null,
});

function freezeState(next) {
  return Object.freeze({ ...next });
}

export class NextStore {
  #state = initialState;
  #listeners = new Set();

  get() { return this.#state; }

  subscribe(listener) {
    if (typeof listener !== 'function') throw new TypeError('listener obrigatório');
    this.#listeners.add(listener);
    listener(this.#state, { type: 'INITIAL' });
    return () => this.#listeners.delete(listener);
  }

  dispatch(event) {
    if (!event || typeof event.type !== 'string') throw new TypeError('evento canônico inválido');
    const previous = this.#state;
    const next = reduce(previous, event);
    if (next === previous) return previous;
    this.#state = freezeState({ ...next, revision: previous.revision + 1 });
    for (const listener of [...this.#listeners]) listener(this.#state, event);
    return this.#state;
  }

  debugSnapshot() {
    const state = this.#state;
    return {
      revision: state.revision,
      route: state.route,
      sessionId: state.sessionId,
      epoch: state.epoch,
      telemetry: state.telemetry,
      learning: state.learning,
      suggestions: { state: state.suggestions.state, activeCount: state.suggestions.activeCount, readyCount: state.suggestions.readyCount },
      cellContext: state.cellContext,
      contextualEditor: state.contextualEditor,
      visual: state.visual,
      globalError: state.globalError,
      mapK: { state: state.mapK.state, selectionCount: state.mapK.selection.length, hasProposal: !!state.mapK.proposal, draftBlocked: state.mapK.draftBlocked },
      curveK: { state: state.curveK.state, preparedCount: state.curveK.prepared.length, draftBlocked: state.curveK.draftBlocked },
      autocal: state.autocal,
      predictor: state.predictor,
      obd: state.obd,
    };
  }
}

function reduce(state, event) {
  switch (event.type) {
    case 'TELEMETRY_UPDATED':
      return reduceTelemetryUpdated(state, event.payload || {});
    case 'TELEMETRY_INVALIDATED': {
      const reason = event.reason || 'ECU sem telemetria válida';
      const mapHasContext = !!state.mapK.map || !!state.mapK.proposal || state.mapK.selection.length > 0;
      const curveHasContext = !!state.curveK.points || state.curveK.prepared.length > 0;
      return {
        ...state,
        telemetry: Object.freeze({ valid: false, ageMs: -1, reason }),
        cellContext: null,
        mapK: Object.freeze({
          ...state.mapK,
          state: mapHasContext ? UI_STATE.STALE : state.mapK.state,
          draftBlocked: mapHasContext,
          confirmationBlockedReason: mapHasContext ? `${reason}; releia a ECU antes de confirmar.` : state.mapK.confirmationBlockedReason,
        }),
        curveK: Object.freeze({
          ...state.curveK,
          state: curveHasContext ? UI_STATE.STALE : state.curveK.state,
          draftBlocked: curveHasContext,
          confirmationBlockedReason: curveHasContext ? `${reason}; releia a ECU antes de confirmar.` : state.curveK.confirmationBlockedReason,
        }),
      };
    }
    case 'LEARNING_UPDATED':
      return { ...state, learning: Object.freeze({ ...event.payload }) };
    case 'SUGGESTIONS_STATE':
      return { ...state, suggestions: Object.freeze({ ...state.suggestions, ...event.payload }) };
    case 'CELL_CONTEXT_UPDATED':
      return { ...state, cellContext: event.payload ? Object.freeze({ ...event.payload }) : null };
    case 'CONTEXT_EDITOR_CHANGED':
      return { ...state, contextualEditor: Object.freeze({ ...state.contextualEditor, ...event.payload }) };
    case 'CALIBRATION_EPOCH_CHANGED':
      return {
        ...state,
        epoch: event.epoch,
        mapK: Object.freeze({ ...state.mapK, state: UI_STATE.STALE, selection: [], proposal: null, draftBlocked: false, confirmationBlockedReason: 'A calibração mudou; releia o Mapa K real.' }),
        curveK: Object.freeze({ ...state.curveK, state: UI_STATE.STALE, prepared: [], draftBlocked: false, confirmationBlockedReason: 'A calibração mudou; releia a Curva K real.' }),
        predictor: Object.freeze({ ...state.predictor, state: UI_STATE.STALE, staleReason: 'Nova epoch de calibração; a previsão precisa ser revalidada.' }),
        suggestions: Object.freeze({ ...state.suggestions, state: UI_STATE.STALE, staleReason: 'Nova epoch de calibração; sugestões serão reconciliadas sem apagar o histórico.' }),
        contextualEditor: Object.freeze({ kind: null, open: false, originRoute: null }),
        cellContext: null,
      };
    case 'MAP_K_STATE':
      return { ...state, mapK: reduceMapKState(state.mapK, event.payload || {}) };
    case 'CURVE_K_STATE':
      return { ...state, curveK: reduceCurveKState(state.curveK, event.payload || {}) };
    case 'AUTOCAL_STATE':
      return { ...state, autocal: Object.freeze({ ...event.payload }) };
    case 'PREDICTOR_STATE':
      return { ...state, predictor: Object.freeze({ ...event.payload }) };
    case 'OBD_STATE':
      return { ...state, obd: Object.freeze({ ...event.payload }) };
    case 'VISUAL_PREFERENCE_CHANGED':
      return { ...state, visual: Object.freeze({ ...state.visual, ...event.payload }) };
    case 'ROUTE_CHANGED':
      return { ...state, route: event.route };
    case 'GLOBAL_ERROR':
      return { ...state, globalError: event.payload ? Object.freeze({ ...event.payload }) : null };
    default:
      return state;
  }
}

function reduceTelemetryUpdated(state, payload) {
  const incomingSessionId = Number(payload?.sessionId || 0);
  const previousSessionId = Number(state.sessionId || 0);
  const sessionReplaced = incomingSessionId > 0 && previousSessionId > 0 && incomingSessionId !== previousSessionId;
  if (!sessionReplaced) {
    return { ...state, sessionId: incomingSessionId || previousSessionId, telemetry: Object.freeze({ ...payload }) };
  }

  const reason = `Nova sessão ECU/USB ${previousSessionId} → ${incomingSessionId}; releitura obrigatória.`;
  return {
    ...state,
    sessionId: incomingSessionId,
    telemetry: Object.freeze({ ...payload }),
    cellContext: null,
    contextualEditor: Object.freeze({ kind: null, open: false, originRoute: null }),
    mapK: Object.freeze({
      ...state.mapK,
      state: UI_STATE.STALE,
      selection: [],
      proposal: null,
      draftBlocked: false,
      confirmationBlockedReason: reason,
    }),
    curveK: Object.freeze({
      ...state.curveK,
      state: UI_STATE.STALE,
      prepared: [],
      draftBlocked: false,
      confirmationBlockedReason: reason,
    }),
    predictor: Object.freeze({ ...state.predictor, state: UI_STATE.STALE, staleReason: reason }),
    suggestions: Object.freeze({ ...state.suggestions, state: UI_STATE.STALE, staleReason: reason }),
    autocal: Object.freeze({ state: UI_STATE.STALE, staleReason: reason }),
    globalError: Object.freeze({
      code: 'NATIVE_SESSION_REPLACED',
      message: 'A ECU reconectou em uma nova sessão. A interface foi preservada; dados estruturais precisam ser relidos.',
      recoverable: true,
    }),
  };
}

function reduceMapKState(current, payload) {
  const preserveDraft = payload.state === UI_STATE.BUSY || payload.state === UI_STATE.FAILURE;
  const next = { ...current, ...payload };
  if (preserveDraft) {
    next.map = current.map;
    next.selection = current.selection;
    next.proposal = current.proposal;
    next.sourceRevision = current.sourceRevision;
  }
  if (payload.state === UI_STATE.FAILURE) {
    next.draftBlocked = !!current.map || !!current.proposal || current.selection.length > 0;
    next.confirmationBlockedReason = payload.error || 'Falha recuperável; releia a ECU antes de confirmar.';
  } else if (payload.state === UI_STATE.READY) {
    next.draftBlocked = false;
    next.confirmationBlockedReason = null;
  }
  return Object.freeze(next);
}

function reduceCurveKState(current, payload) {
  const preserveDraft = payload.state === UI_STATE.BUSY || payload.state === UI_STATE.FAILURE;
  const next = { ...current, ...payload };
  if (preserveDraft) {
    next.points = current.points;
    next.prepared = current.prepared;
    next.sourceRevision = current.sourceRevision;
  }
  if (payload.state === UI_STATE.FAILURE) {
    next.draftBlocked = !!current.points || current.prepared.length > 0;
    next.confirmationBlockedReason = payload.error || 'Falha recuperável; releia a ECU antes de confirmar.';
  } else if (payload.state === UI_STATE.READY) {
    next.draftBlocked = false;
    next.confirmationBlockedReason = null;
  }
  return Object.freeze(next);
}

export const store = new NextStore();
