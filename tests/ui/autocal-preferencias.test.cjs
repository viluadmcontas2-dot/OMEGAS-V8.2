'use strict';
// Preferências do AutoCal: catálogo, quadros (oráculo do doc), validação, "Gravado" só com readback igual.
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
const S = (P, id) => P.BY_ID[id];

test('quadros de escrita batem com o oráculo do documento', () => {
  const P = fresh(undefined);
  const w = (id, raw) => P.writeFrame(S(P, id), raw);
  assert.equal(w('VECT_AUTOCAL_U8_2', 3), '13 65 01 02 03 7E');
  assert.equal(w('MAX_RPM_FOR_AUTOCAL', 3000), '13 7A 01 B8 0B 51');
  assert.equal(w('DISABLE_ACQ_BAND', 1), '12 8B 01 01 9F');
  assert.equal(w('AUTO_CAL_ENABLE', 1), '12 4A 01 01 5E');
  assert.equal(w('LIMIT_PRESSURE_MIN', 51), '13 69 01 33 00 B0');
  assert.equal(w('LIMIT_PRESSURE_MAX', 972), '13 6A 01 CC 03 4D');
  assert.equal(w('EN_CDN_T_THD', 1024), '14 67 01 01 00 04 81');
  assert.equal(w('DIFF_ENG_SPD_THD', 400), '13 83 01 90 01 28');
  assert.equal(w('CALIBRATION_VAL_1:3', 4), '13 72 01 03 04 8D');
  assert.equal(w('MNFLD_PRESS_THD:5', 512), '14 4C 01 05 00 02 68');
  assert.equal(w('PETR_INJ_TBP:5', 1024), '14 4B 01 05 00 04 69');
});

test('catálogo: 12 CalibrationVal, 18+18 pontos, 4 grupos, sem MUL_ACT, ids únicos', () => {
  const P = fresh(undefined);
  assert.equal(P.CAL_NAMES.length, 12);
  assert.equal(P.CATALOG.filter(s => s.group === 'qualidade' && /^(VECT|CALIBRATION)/.test(s.id)).length, 12);
  assert.equal(P.CATALOG.filter(s => s.sc === 332).length, 18);
  assert.equal(P.CATALOG.filter(s => s.sc === 331).length, 18);
  assert.equal(P.GROUPS.length, 4);
  assert.ok(!P.CATALOG.some(s => /MUL_ACT/.test(s.id)));
  assert.equal(new Set(P.CATALOG.map(s => s.id)).size, P.CATALOG.length);
});

test('fora da faixa não gera quadro; validação de ordem, mínimo/máximo e desligar', () => {
  const P = fresh(undefined);
  assert.equal(P.writeFrame(S(P, 'MAX_RPM_FOR_AUTOCAL'), 399), null);
  assert.equal(P.writeFrame(S(P, 'VECT_AUTOCAL_U8_2'), 0), null);
  assert.ok(P.validate(S(P, 'AUTO_CAL_ENABLE'), 0, () => null));
  assert.equal(P.validate(S(P, 'AUTO_CAL_ENABLE'), 1, () => null), null);
  const known = id => ({ 'MNFLD_PRESS_THD:4': 100, 'MNFLD_PRESS_THD:6': 200, LIMIT_PRESSURE_MAX: 900 }[id] ?? null);
  assert.equal(P.validate(S(P, 'MNFLD_PRESS_THD:5'), 150, known), null);
  assert.ok(P.validate(S(P, 'MNFLD_PRESS_THD:5'), 100, known));
  assert.ok(P.validate(S(P, 'MNFLD_PRESS_THD:5'), 200, known));
  assert.ok(P.validate(S(P, 'LIMIT_PRESSURE_MIN'), 950, known));
});

test('exibição: bruto e mostrado, desconhecido é "—"', () => {
  const P = fresh(undefined);
  assert.equal(P.fromDisplay(S(P, 'MNFLD_PRESS_THD:1'), '1'), 1024);
  assert.equal(P.fromDisplay(S(P, 'MNFLD_PRESS_THD:1'), ''), null);
  assert.equal(P.read(['MAX_RPM_FOR_AUTOCAL']).MAX_RPM_FOR_AUTOCAL, null);
});

test('sem ponte: gravação diz indisponível', () => {
  const P = fresh(undefined);
  assert.equal(P.supported(), false);
  assert.equal(P.write('MAX_RPM_FOR_AUTOCAL', 3000, () => null).state, 'unavailable');
});

test('"Gravado" só com CONFIRMED e leitura de volta igual', () => {
  const mk = r => ({ readAutoCalPreference: () => JSON.stringify({ ok: true, values: { MAX_RPM_FOR_AUTOCAL: 2500 } }), applyAutoCalPreference: () => JSON.stringify(r) });
  const run = r => fresh(mk(r)).write('MAX_RPM_FOR_AUTOCAL', 3000, () => null);
  assert.equal(run({ ok: true, outcome: 'CONFIRMED', before: 2500, after: 3000 }).state, 'saved');
  assert.notEqual(run({ ok: true, outcome: 'CONFIRMED', before: 2500, after: 2900 }).state, 'saved');
  assert.equal(run({ ok: false, outcome: 'READBACK_DIVERGED', before: 2500, after: 2900 }).state, 'mismatch');
  assert.equal(run({ ok: false, outcome: 'NOT_ACKED' }).state, 'failed');
  assert.equal(run({ ok: true, outcome: 'NOTHING_TO_DO', before: 3000, after: 3000 }).state, 'same');
  assert.equal(fresh(mk({})).read(['MAX_RPM_FOR_AUTOCAL']).MAX_RPM_FOR_AUTOCAL, 2500);
});

test('preferências locais: 200 a 2000, cores e linha K', () => {
  const P = fresh(undefined);
  assert.equal(P.load().refreshMs, 300);
  assert.equal(P.load().showKLine, true);
  P.save({ refreshMs: 5000, showZones: false, showKLine: false, colorGas: '#00ff00', colorPetrol: 'x' });
  const l = P.load();
  assert.equal(l.refreshMs, 2000);
  assert.equal(l.showZones, false);
  assert.equal(l.showKLine, false);
  assert.equal(l.colorGas, '#00ff00');
  assert.equal(l.colorPetrol, '');
});

test('tela: Preferências antes de Ações avançadas; sem @keyframes nem CHANGE_NETWORK_STATE; selo visível', () => {
  const js = fs.readFileSync(path.join(UI, 'screens', 'autocal-cockpit.js'), 'utf8');
  assert.ok(js.indexOf('<summary>Preferências</summary>') > 0);
  assert.ok(js.indexOf('<summary>Preferências</summary>') < js.indexOf('<summary>Ações avançadas</summary>'));
  assert.ok(js.includes('data-pref-body'));
  const mod = fs.readFileSync(path.join(UI, 'core', 'autocal-preferencias.js'), 'utf8');
  const css = fs.readFileSync(path.join(UI, 'styles-autocal-cockpit.css'), 'utf8');
  for (const bad of ['@keyframes', 'CHANGE_NETWORK_STATE']) assert.ok(!mod.includes(bad) && !js.includes(bad));
  assert.ok(mod.includes('não comprovado') && css.includes('.pref-seal'));
  assert.ok(fs.readFileSync(path.join(UI, 'index.html'), 'utf8').includes('core/autocal-preferencias.js'));
});
