'use strict';
const fs = require('fs');
const vm = require('vm');
const path = require('path');
const assert = require('assert');

const predictorSource = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/assets/ui/screens/predictor.js'), 'utf8');
const appSource = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/assets/ui/app.js'), 'utf8');
assert(!predictorSource.includes('scheduler.addHook'), 'Predictor screen must not own scheduler hook');
assert(!predictorSource.includes('api.v7'), 'Predictor screen must not read native V7 directly');
assert(!predictorSource.includes('.getState()'), 'Predictor screen must consume Store state only');
assert(appSource.includes("route === 'predictor'"), 'central visual scheduler must include predictor calibration projection');
assert(appSource.includes('predictor:'), 'central Store patch must publish predictor state');

class FakeStore {
  constructor() { this.state = { route: 'dashboard', predictor: { state: 'idle', data: null, activeCell: null } }; this.listeners = []; }
  get() { return this.state; }
  patch(value) { this.state = { ...this.state, ...value }; this.listeners.forEach(fn => fn(this.state)); }
  subscribeSelected(selector, listener, immediate) {
    let old = selector(this.state);
    const fn = state => { const next = selector(state); if (next !== old) { const prev = old; old = next; listener(next, prev, state); } };
    this.listeners.push(fn); if (immediate) listener(old, undefined, this.state); return () => {};
  }
}
const store = new FakeStore();
let nativeReads = 0;
const root = {
  OmegasUi: {
    PredictorModel: { explainCell() { return {}; }, openMapReview() { return false; } },
  },
  OmegasApp: {
    store,
    router: { navigate() { return true; } },
    api: { v7: { getState() { nativeReads += 1; return '{}'; } } },
    screens: {},
  },
  document: { querySelector() { return null; }, createElement() { return {}; }, getElementById() { return null; } },
  setTimeout() { throw new Error('Predictor screen must not create retry/poll timers once dependencies exist'); },
  globalThis: null,
};
root.globalThis = root;
vm.runInContext(predictorSource, vm.createContext(root), { filename: 'predictor.js' });
assert(root.OmegasApp.screens.predictor, 'PredictorScreen should mount');
for (let i = 0; i < 100; i += 1) {
  store.patch({ route: 'predictor' });
  store.patch({ route: 'dashboard' });
}
assert.strictEqual(nativeReads, 0, '100 route mounts must not initiate native Predictor reads');
store.patch({ predictor: { state: 'ready', data: { revisionToken: 'abc', cells: [] }, activeCell: null } });
assert.strictEqual(root.OmegasApp.screens.predictor.data.revisionToken, 'abc', 'screen must follow Store predictor data');
console.log('STEP158_ROUTE_STORE_PASS');
