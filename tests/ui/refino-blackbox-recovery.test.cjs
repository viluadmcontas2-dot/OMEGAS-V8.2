const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/assets/ui/screens/refino.js'), 'utf8');

test('watchdog encerrado bloqueia revisão de proposta antiga', () => {
  const window = require('./_support.cjs').freshContext({ console });
  vm.runInContext(source, window);
  const action = window.OmegasUi.RefinoModel.primaryAction(
    { autopilot: { phase: 'TENTATIVA_ENCERRADA' } },
    { available: true, points: [{ index: 1, origin: 'MEASURED', currentRaw: 16384, calculatedRaw: 17000 }] }
  );
  assert.equal(action.kind, 'none');
});
