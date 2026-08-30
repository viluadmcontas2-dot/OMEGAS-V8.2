const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');

const router = read('app/src/main/assets/ui/core/router.js');
const store = read('app/src/main/assets/ui/core/store.js');
const scheduler = read('app/src/main/assets/ui/core/scheduler.js');
const predictor = read('app/src/main/assets/ui/screens/predictor.js');
const map = read('app/src/main/assets/ui/screens/map.js');

assert.match(router, /'predictor'/);
assert.match(router, /screens\/predictor\.js/);
assert.match(store, /predictor:\s*\{\s*state:\s*'idle'/);
assert.match(scheduler, /addHook\(cadence, listener\)/);
assert.match(predictor, /app\.store/);
assert.match(predictor, /app\.router/);
assert.match(predictor, /this\.store\.subscribe/);
assert.equal(predictor.includes("addHook('context'"), false);
assert.equal(predictor.includes('setInterval'), false);
assert.equal(predictor.includes('new Store'), false);
assert.equal(predictor.includes('new Router'), false);
assert.equal(predictor.includes('writeMap'), false);
assert.equal(predictor.includes('startMapBatchWrite'), false);
assert.equal(predictor.includes('OmegasNative'), false);
assert.match(map, /context\.suggestion/);
assert.match(map, /setTargetOverrides\(changes\)/);
assert.match(map, /openReview\(\)/);
assert.match(map, /writeReview\(\)/);
console.log('PREDICTOR_ROUTE_CONTRACT=PASS');
