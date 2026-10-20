'use strict';
// Preferências do AutoCal: quadros provados (docs/progbase/protocolo/autocal-preferencias.md), faixa, readback.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const UI = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'assets', 'ui');

function fresh(bridge) {
  global.localStorage = { _m: {}, getItem(k) { return this._m[k] ?? null; }, setItem(k, v) { this._m[k] = v; } };
  global.OmegasAutoCal = bridge;
  delete require.cache[require.resolve(path.join(UI, 'core', 'autocal-preferencias.js'))];
  return require(path.join(UI, 'core', 'autocal-preferencias.js'));
}

test('quadros de escrita e leitura batem com o documento (checksum recalculado)', () => {
  const P = fresh(undefined);
  assert.equal(P.writeFrame(P.PARAMS.automatchLimit, 3), '13 65 01 02 03 7E');
  assert.equal(P.readFrame(P.PARAMS.automatchLimit), '0A 65 01 02 72');
  assert.equal(P.writeFrame(P.PARAMS.rpmLimit, 3000), '13 7A 01 B8 0B 51');
  assert.equal(P.readFrame(P.PARAMS.rpmLimit), '09 7A 01 84');
});

test('fora da faixa não gera quadro', () => {
  const P = fresh(undefined);
  assert.equal(P.writeFrame(P.PARAMS.automatchLimit, 0), null);
  assert.equal(P.writeFrame(P.PARAMS.automatchLimit, 100), null);
  assert.equal(P.writeFrame(P.PARAMS.rpmLimit, 399), null);
  assert.equal(P.writeFrame(P.PARAMS.rpmLimit, 8001), null);
});

test('sem ponte: leitura é null ("—") e gravação diz indisponível', () => {
  const P = fresh(undefined);
  assert.equal(P.read(P.PARAMS.rpmLimit), null);
  assert.equal(P.write(P.PARAMS.rpmLimit, 3000).state, 'unavailable');
});

test('"Gravado" só com ACK e readback igual', () => {
  const mk = r => ({ readAutoCalPreference: () => '{"ok":true,"raw":3000}', applyAutoCalPreference: () => JSON.stringify(r) });
  let P = fresh(mk({ ok: true, acked: true, readback: 3000 }));
  assert.equal(P.read(P.PARAMS.rpmLimit), 3000);
  assert.equal(P.write(P.PARAMS.rpmLimit, 3000).state, 'saved');
  P = fresh(mk({ ok: true, acked: true, readback: 2900 }));
  assert.equal(P.write(P.PARAMS.rpmLimit, 3000).state, 'mismatch');
  P = fresh(mk({ ok: true, acked: false }));
  assert.equal(P.write(P.PARAMS.rpmLimit, 3000).state, 'failed');
  P = fresh(mk({ ok: true, acked: true }));
  assert.equal(P.write(P.PARAMS.rpmLimit, 3000).state, 'unverified');
});

test('preferências locais: salvar, recarregar e limitar atualização a 200–2000', () => {
  const P = fresh(undefined);
  assert.equal(P.load().refreshMs, 300);
  P.save({ refreshMs: 5000, showZones: false, colorGas: '#00ff00', colorPetrol: 'x' });
  const l = P.load();
  assert.equal(l.refreshMs, 2000);
  assert.equal(l.showZones, false);
  assert.equal(l.colorGas, '#00ff00');
  assert.equal(l.colorPetrol, '');
});

test('tela: botão Preferências ao lado de Ações avançadas, sem @keyframes nem CHANGE_NETWORK_STATE', () => {
  const js = fs.readFileSync(path.join(UI, 'screens', 'autocal-cockpit.js'), 'utf8');
  assert.ok(js.indexOf('<summary>Preferências</summary>') > 0);
  assert.ok(js.indexOf('<summary>Preferências</summary>') < js.indexOf('<summary>Ações avançadas</summary>'));
  const mod = fs.readFileSync(path.join(UI, 'core', 'autocal-preferencias.js'), 'utf8');
  for (const bad of ['@keyframes', 'CHANGE_NETWORK_STATE']) assert.ok(!mod.includes(bad) && !js.includes(bad));
  const html = fs.readFileSync(path.join(UI, 'index.html'), 'utf8');
  assert.ok(html.includes('core/autocal-preferencias.js'));
});
