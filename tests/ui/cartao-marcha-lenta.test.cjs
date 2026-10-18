'use strict';
// Cartão "Marcha lenta": modo lido por core/idle-mode.js (bit 0x0100 desta central), só leitura, com sugestão e sem botão de gravar.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const UI = path.join(__dirname, '../../app/src/main/assets/ui');
function load() {
  const ctx = { console, Math, JSON, Number, String, Array, Object, Set, Map, Intl }; ctx.window = ctx; ctx.globalThis = ctx; vm.createContext(ctx);
  for (const f of ['core/advanced-flags-data.js', 'core/advanced-flags.js', 'core/anti-apagao-valores.js', 'core/idle-mode.js', 'core/cartoes-motor.js']) vm.runInContext(fs.readFileSync(path.join(UI, f), 'utf8'), ctx);
  return ctx.OmegasUi;
}
const ok = values => ({ state: 'OK', values });
test('FLAG_CONF1 = 2486 1051 e GIRI = 0 8500 mostra "Só gás" com a sugestão', () => {
  const U = load();
  const card = U.CartoesMotor.build({ FLAG_CONF1: ok([0x2486, 0x1051]), GIRI_PER_BENZINA: ok([0, 8500]) }).find(c => c.id === 'lenta');
  assert.equal(card.stateText, 'Só gás');
  assert.match(card.note, /Segurar com gasolina na parada/);
  assert.equal(card.tech.some(t => /0x0400/.test(t)), false, 'o bit 0x0400 não é o desta central');
  assert.equal(card.tech.some(t => /0x0100/.test(t)), true);
});
test('sem leitura é "—" e o cartão é só leitura (nenhum botão de gravar)', () => {
  const U = load();
  const card = U.CartoesMotor.build({}).find(c => c.id === 'lenta');
  assert.equal(card.stateText, '—'); assert.equal(card.kind, 'open');
  const src = fs.readFileSync(path.join(UI, 'components/advanced.js'), 'utf8');
  assert.doesNotMatch(src, /data-adv-idle|prepareIdle/);
});
