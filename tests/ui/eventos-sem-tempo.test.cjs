const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ui = 'app/src/main/assets/ui/';
test('scheduler espera dado sem criar timer por operação e remove listener', async () => {
  const root = { console, setInterval() { throw Error('operação não pode criar timer'); } };
  vm.runInNewContext(fs.readFileSync(ui + 'core/scheduler.js', 'utf8'), root);
  const scheduler = new root.OmegasUi.Scheduler();
  let completed = false;
  const promise = scheduler.nextEvent().then(() => { completed = true; });
  await Promise.resolve();
  assert.equal(completed, false);
  scheduler.emitHooks('fast');
  await promise;
  assert.equal(completed, true);
  assert.equal(scheduler.hooks.fast.size, 0);
});
test('releituras e término das operações não dependem de prazo JS', () => {
  for (const file of ['app.js', 'core/map-fix.js', 'core/balance-runner.js', 'screens/refino.js', 'screens/map.js']) {
    const source = fs.readFileSync(ui + file, 'utf8');
    assert.doesNotMatch(source, /AUTO_SYNC_MS|OPERATION_TIMEOUT_MS|TIMEOUT_MS|Date\.now\(\)\s*-\s*(?:started|pending\.startedAt)/, file);
  }
});
