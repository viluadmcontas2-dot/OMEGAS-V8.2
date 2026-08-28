(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  class Store {
    constructor(initial) {
      this.state = Object.freeze({ ...(initial || {}) });
      this.listeners = new Set();
      this._revision = 0;
    }
    get() { return this.state; }
    revision() { return this._revision; }
    set(next) {
      const value = typeof next === 'function' ? next(this.state) : next;
      this.state = Object.freeze({ ...(value || {}) });
      this._revision += 1;
      this.emit();
      return this.state;
    }
    patch(partial) {
      const value = typeof partial === 'function' ? partial(this.state) : partial;
      if (!value || typeof value !== 'object') return this.state;
      this.state = Object.freeze({ ...this.state, ...value });
      this._revision += 1;
      this.emit();
      return this.state;
    }
    update(key, value) { return this.patch({ [key]: value }); }
    subscribe(listener, immediate) {
      if (typeof listener !== 'function') return () => {};
      this.listeners.add(listener);
      if (immediate) listener(this.state);
      return () => this.listeners.delete(listener);
    }
    subscribeSelected(selector, listener, immediate, equals) {
      if (typeof selector !== 'function' || typeof listener !== 'function') return () => {};
      const equality = typeof equals === 'function' ? equals : Object.is;
      let selected = selector(this.state);
      const wrapped = state => {
        const next = selector(state);
        if (equality(selected, next)) return;
        const previous = selected;
        selected = next;
        listener(next, previous, state);
      };
      this.listeners.add(wrapped);
      if (immediate) listener(selected, undefined, this.state);
      return () => this.listeners.delete(wrapped);
    }
    emit() {
      this.listeners.forEach(listener => {
        try { listener(this.state); } catch (error) { console.error('[OMEGAS store]', error); }
      });
    }
  }

  ns.Store = Store;
  ns.createInitialState = function () {
    return {
      route: 'dashboard',
      visible: true,
      status: {},
      telemetry: {},
      learning: {},
      learningStatus: {},
      learningDecision: {},
      learningTolerance: {},
      learningLayer: 'comparison',
      predictor: { state: 'idle', data: null, activeCell: null, inspector: null },
      obd: {},
      obdDevices: {},
      map: { state: 'idle', data: null, selection: 0, activeCell: null, review: null, operation: null },
      curve: { state: 'idle', data: null, activePoint: null, proposal: null, status: {} },
      sessionStatus: {},
      sessions: [],
      logs: [],
      suggestionsOpen: false,
      toolsOpen: false,
      alert: null,
      identity: {},
      demo: false,
    };
  };
})(typeof window !== 'undefined' ? window : globalThis);
