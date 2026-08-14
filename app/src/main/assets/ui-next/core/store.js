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
  mapK: Object.freeze({ state: UI_STATE.UNAVAILABLE, selection: [], proposal: null, sourceRevision: null }),
  curveK: Object.freeze({ state: UI_STATE.UNAVAILABLE, prepared: [], sourceRevision: null }),
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

  get() {
    return this.#state;
  }

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
      mapK: { state: state.mapK.state, selectionCount: state.mapK.selection.length, hasProposal: !!state.mapK.proposal },
      curveK: { state: state.curveK.state, preparedCount: state.curveK.prepared.length },
      autocal: state.autocal,
      predictor: state.predictor,
      obd: state.obd,
    };
  }
}

function reduce(state, event) {
  switch (event.type) {
    case 'TELEMETRY_UPDATED':
      return { ...state, sessionId: event.payload?.sessionId ?? state.sessionId, telemetry: Object.freeze({ ...event.payload }) };
    case 'TELEMETRY_INVALIDATED':
      return { ...state, telemetry: Object.freeze({ valid: false, ageMs: -1, reason: event.reason || 'SEM_DADO' }), cellContext: null };
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
        mapK: Object.freeze({ state: UI_STATE.STALE, selection: [], proposal: null, sourceRevision: null }),
        curveK: Object.freeze({ state: UI_STATE.STALE, prepared: [], sourceRevision: null }),
        suggestions: Object.freeze({ ...state.suggestions, state: UI_STATE.STALE }),
        contextualEditor: Object.freeze({ kind: null, open: false, originRoute: null }),
        cellContext: null,
      };
    case 'MAP_K_STATE':
      return { ...state, mapK: Object.freeze({ ...state.mapK, ...event.payload }) };
    case 'CURVE_K_STATE':
      return { ...state, curveK: Object.freeze({ ...state.curveK, ...event.payload }) };
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

export const store = new NextStore();
