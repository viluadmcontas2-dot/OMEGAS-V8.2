const test = require('node:test'), assert = require('node:assert'), fs = require('fs'), vm = require('vm');
const ctx = { console }; ctx.window = ctx; vm.createContext(ctx);
vm.runInContext(fs.readFileSync(__dirname + '/../../app/src/main/assets/ui/core/idle-mode.js', 'utf8'), ctx);
const M = ctx.OmegasUi.IdleMode;
const P = (f, g) => ({ FLAG_CONF1: { state: 'OK', values: f }, GIRI_PER_BENZINA: { state: 'OK', values: g } });
test('decodifica os três modos (bit 0x0100 da palavra 1)', () => {
  assert.equal(M.decode(P([0x2486, 0x1051], [0, 8500])), 'gas');
  assert.equal(M.decode(P([0x2486, 0x1051], [1100, 8500])), 'volta');
  assert.equal(M.decode(P([0x2486, 0x1151], [1100, 8500])), 'gasolina');
  assert.equal(M.decode(P([0x2486, 0x1151], [0, 8500])), 'incomum');
});
test('bit 0x0400 não conta; não lido é null, nunca gás', () => {
  assert.equal(M.decode(P([0, 0x0400], [1100, 0])), 'volta');
  assert.equal(M.decode({}), null);
  assert.equal(M.view({}).stateText, '—');
});
test('sugere "volta" só quando está em gás; nunca grava', () => {
  const v = M.view(P([0, 0x1051], [0, 8500]));
  assert.equal(v.options.find(o => o.suggested).id, 'volta');
  assert.equal(v.writeAvailable, false);
  assert.equal(M.view(P([0, 0x1051], [1100, 8500])).suggestionText, '');
});
