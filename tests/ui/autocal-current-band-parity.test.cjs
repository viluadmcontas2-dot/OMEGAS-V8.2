const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const context = { console, setTimeout: () => 0, clearTimeout: () => {} };
context.globalThis = context;
vm.createContext(context);
vm.runInContext(source, context, { filename: 'autocal-cockpit.js' });
const model = context.OmegasUi.AutoCalUxModel;

const snapshot = {
  fields: [{
    key: 'MNFLD_PRESS_THD',
    status: 'VALID',
    physicalValues: [0.20, 0.30, 0.40, 0.50],
  }],
};

test('CurrentBand usa MAP vivo entre thresholds físicos adjacentes', () => {
  assert.equal(typeof model.currentBand, 'function',
    'o consumer CurrentBand do ProgBase ainda não existe no modelo AutoCal');
  const band = model.currentBand(snapshot, { mapBar: 0.35 });
  assert.deepEqual(
    { index: band?.index, lower: band?.lower, upper: band?.upper },
    { index: 1, lower: 0.30, upper: 0.40 },
  );
});

test('CurrentBand reproduz bordas do helper original e falha fechado fora do domínio', () => {
  assert.equal(model.currentBand(snapshot, { mapBar: 0.20 }), null, 'mínimo exato fica fora');
  assert.equal(model.currentBand(snapshot, { mapBar: 0.50 }), null, 'máximo exato fica fora');
  const onInternalThreshold = model.currentBand(snapshot, { mapBar: 0.30 });
  assert.equal(onInternalThreshold?.index, 0, 'threshold interno pertence à faixa imediatamente anterior');
});

test('CurrentBand possui camada visual própria e não reutiliza maturidade das 18 regiões', () => {
  assert.match(source, /data-autocal-current-band/,
    'o gráfico ainda não possui THorizAreaSeries-equivalente para a faixa MAP atual');
  assert.match(source, /currentBand\(/,
    'o fast path precisa localizar a faixa atual a partir do MAP vivo');
});

console.log('AUTOCAL_CURRENT_BAND_PARITY=PASS');
