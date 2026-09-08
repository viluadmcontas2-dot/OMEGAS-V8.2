(() => {
  var __defProp = Object.defineProperty;
  var __getOwnPropSymbols = Object.getOwnPropertySymbols;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __propIsEnum = Object.prototype.propertyIsEnumerable;
  var __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : obj[key] = value;
  var __spreadValues = (a, b) => {
    for (var prop in b || (b = {}))
      if (__hasOwnProp.call(b, prop))
        __defNormalProp(a, prop, b[prop]);
    if (__getOwnPropSymbols)
      for (var prop of __getOwnPropSymbols(b)) {
        if (__propIsEnum.call(b, prop))
          __defNormalProp(a, prop, b[prop]);
      }
    return a;
  };
  (function(root) {
    "use strict";
    const ns = root.OmegasUi = root.OmegasUi || {};
    class Store {
      constructor(initial) {
        this.state = Object.freeze(__spreadValues({}, initial || {}));
        this.listeners = /* @__PURE__ */ new Set();
      }
      get() {
        return this.state;
      }
      set(next) {
        const value = typeof next === "function" ? next(this.state) : next;
        this.state = Object.freeze(__spreadValues({}, value || {}));
        this.emit();
        return this.state;
      }
      patch(partial) {
        const value = typeof partial === "function" ? partial(this.state) : partial;
        if (!value || typeof value !== "object") return this.state;
        this.state = Object.freeze(__spreadValues(__spreadValues({}, this.state), value));
        this.emit();
        return this.state;
      }
      update(key, value) {
        return this.patch({ [key]: value });
      }
      subscribe(listener, immediate) {
        if (typeof listener !== "function") return () => {
        };
        this.listeners.add(listener);
        if (immediate) listener(this.state);
        return () => this.listeners.delete(listener);
      }
      emit() {
        this.listeners.forEach((listener) => {
          try {
            listener(this.state);
          } catch (error) {
            console.error("[OMEGAS store]", error);
          }
        });
      }
    }
    ns.Store = Store;
    ns.createInitialState = function() {
      return {
        route: "dashboard",
        visible: true,
        status: {},
        telemetry: {},
        learning: {},
        learningStatus: {},
        learningDecision: {},
        learningTolerance: {},
        learningLayer: "comparison",
        calibrationState: {},
        obd: {},
        obdDevices: {},
        map: { state: "idle", data: null, selection: 0, activeCell: null, review: null, operation: null },
        curve: { state: "idle", data: null, activePoint: null, proposal: null, status: {} },
        sessionStatus: {},
        sessions: [],
        logs: [],
        suggestionsOpen: false,
        toolsOpen: false,
        alert: null,
        identity: {},
        demo: false
      };
    };
  })(typeof window !== "undefined" ? window : globalThis);
})();
