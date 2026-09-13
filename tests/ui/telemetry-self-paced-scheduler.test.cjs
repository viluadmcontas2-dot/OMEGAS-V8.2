const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const rootDir = path.resolve(__dirname, '../..');
const code = fs.readFileSync(path.join(rootDir, 'app/src/main/assets/ui/core/scheduler.js'), 'utf8');
const pending = [];
let intervalCalls = 0;
let nextHandle = 1;
const browser = {
  console,
  setTimeout(callback) {
    const handle = nextHandle++;
    pending.push({ handle, callback });
    return handle;
  },
  clearTimeout(handle) {
    const index = pending.findIndex((item) => item.handle === handle);
    if (index >= 0) pending.splice(index, 1);
  },
  setInterval() {
    intervalCalls += 1;
    throw new Error('setInterval is forbidden for live telemetry scheduling');
  },
  clearInterval() {},
};
browser.window = browser;
browser.globalThis = browser;
vm.runInNewContext(code, browser, { filename: 'scheduler.js' });

let fastTicks = 0;
const scheduler = new browser.OmegasUi.Scheduler({ intervalMs: 100, onFast: () => { fastTicks += 1; } });
scheduler.start();
assert.equal(intervalCalls, 0, 'scheduler must never arm setInterval');
assert.equal(fastTicks, 1, 'start must execute one immediate tick');
assert.equal(pending.length, 1, 'only one future tick may be queued');

const first = pending.shift();
first.callback();
assert.equal(fastTicks, 2, 'timeout callback executes the next tick');
assert.equal(pending.length, 1, 'self-paced scheduler queues exactly one successor');

scheduler.stop();
assert.equal(pending.length, 0, 'stop clears the pending timeout');
console.log('TELEMETRY_SELF_PACED_SCHEDULER=PASS');
