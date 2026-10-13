'use strict';

// Tensão da ECU na barra do AutoCal (dono, 2026-10-13). Mesma regra e mesmas constantes da faixa de status.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const box = { dataset: {} };
const context = { console, setTimeout: () => 0, clearTimeout: () => {}, document: { getElementById: id => (id === 'autocalLiveVoltBox' ? box : null) } };
context.globalThis = context;
vm.createContext(context);
vm.runInContext(source, context, { filename: 'autocal-cockpit.js' });
const render = context.OmegasUi.AutoCalCockpit.prototype.renderVoltage;

function run(telemetry) {
  const texts = {};
  render.call({ text: (id, value) => { texts[id] = value; } }, telemetry);
  return { text: texts.autocalLiveVolt, state: box.dataset.state };
}
const tele = raw => ({ valid: true, live: { unknown_raw_19: raw } });

test('byte 19 ÷ 16, uma casa, vírgula decimal', () => {
  assert.deepEqual(run(tele(224)), { text: '14,0', state: 'measured' });
  assert.deepEqual(run(tele(205)), { text: '12,8', state: 'measured' });
});

test('alerta a partir de 14,1 V', () => {
  assert.equal(run(tele(225)).state, 'measured');   // 14,06 V
  assert.equal(run(tele(226)).state, 'alert');      // 14,13 V
});

test('0 (gasolina), ausente e telemetria inválida são "—", nunca 0 V', () => {
  for (const telemetry of [tele(0), tele(undefined), { valid: false }, {}, null]) {
    assert.deepEqual(run(telemetry), { text: '—', state: 'unknown' });
  }
});

test('as mesmas constantes da faixa de status (÷16 e 14,1 V)', () => {
  const strip = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/assets/ui/components/vehicle-status-strip.js'), 'utf8');
  assert.match(strip, /VOLTAGE_ALERT_V = 14\.1/);
  assert.match(strip, /VOLTAGE_RAW_PER_VOLT = 16/);
  assert.match(source, /raw \/ 16/);
  assert.match(source, /volts >= 14\.1/);
  assert.match(source, /<small>Tensão ECU\*<\/small>/);
});
