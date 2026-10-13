'use strict';

// Paridade da tela AutoCal com o programa original (PROTOCOLO-LANDI docs 20, 21 e 25).
// Fixture: estado real da captura AutoCal no instante 1000 s (vetores físicos + telemetria).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-original.js'), 'utf8');
const context = { console };
context.globalThis = context;
vm.createContext(context);
vm.runInContext(source, context, { filename: 'autocal-original.js' });
const O = context.OmegasUi.AutoCalOriginal;
// O vm cria objetos de outro realm: normaliza para comparar com deepEqual.
const plain = value => JSON.parse(JSON.stringify(value));
const build = (snap, opts) => plain(O.build(snap, opts));
const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/autocal-captura-ac-t1000.json'), 'utf8'));
const snapshot = fixture.snapshot;
const key = name => snapshot.fields.find(f => f.key === name);
const phys = name => key(name).physicalValues;
const rawv = name => key(name).rawValues;

test('zonas por banda: 0–4, 5–8, 9–12, 13–17 (e não deslocadas uma banda)', () => {
  const zones = Array.from({ length: 18 }, (_, band) => O.zoneOfBand(band));
  assert.deepEqual(zones, [0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 3]);
  assert.deepEqual(plain(O.ZONE_EDGES), [0, 5, 9, 13, 17]);
});

test('fronteiras de zona no MAP = THD[5], THD[9], THD[13] (0,45 / 0,65 / 0,85 bar)', () => {
  const model = build(snapshot, { fuel: 'gas' });
  assert.equal(model.ok, true);
  const thd = phys('MNFLD_PRESS_THD');
  assert.deepEqual(model.zones.map(z => z.lower), [thd[0], thd[5], thd[9], thd[13]]);
  assert.deepEqual(model.zones.map(z => z.upper), [thd[5], thd[9], thd[13], thd[17]]);
  assert.ok(Math.abs(model.zones[1].lower - 0.45) < 0.01 && Math.abs(model.zones[2].lower - 0.65) < 0.01 && Math.abs(model.zones[3].lower - 0.85) < 0.01);
});

test('eixos fixos: X de 1 ao último nó de PETR_INJ_TBP, Y de 0,15 a 1,10', () => {
  const model = build(snapshot, { fuel: 'gas' });
  const tbp = phys('PETR_INJ_TBP');
  assert.deepEqual(model.axisX, { min: 1, max: tbp[tbp.length - 1] });
  assert.equal(model.axisX.max, 22);
  assert.deepEqual({ min: model.axisY.min, max: model.axisY.max }, { min: 0.15, max: 1.10 });
});

test('rótulo #n/d: n = entradas ≠ 0 do buffer MAP do combustível em uso na zona; d = U8_0 / CALIBRATION_VAL_1', () => {
  const buf = phys('MNFLD_PRESS_BUF_GAS');
  const count = (from, to) => buf.slice(from, to).filter(v => v !== 0).length;
  const model = build(snapshot, { fuel: 'gas' });
  const cal = rawv('CALIBRATION_VAL_1');
  assert.deepEqual(model.zones.map(z => z.label), [
    '#' + count(0, 5) + '/' + cal[4], '#' + count(5, 9) + '/' + cal[7], '#' + count(9, 13) + '/' + cal[7], '#' + count(13, 17) + '/' + cal[7],
  ]);
  const petrol = build(snapshot, { fuel: 'petrol' });
  const pbuf = phys('MNFLD_PRESS_BUF');
  assert.equal(petrol.zones[0].n, pbuf.slice(0, 5).filter(v => v !== 0).length);
  assert.equal(petrol.zones[0].d, rawv('VECT_AUTOCAL_U8_0')[0]);
  assert.equal(petrol.zones[1].d, cal[1]);
});

test('cor da zona segue ACQUIRED_ZONES do combustível em uso', () => {
  const gas = build(snapshot, { fuel: 'gas' }).zones.map(z => z.acquired);
  assert.deepEqual(gas, rawv('ACQUIRED_ZONES_GAS').map(v => v !== 0));
  const petrol = build(snapshot, { fuel: 'petrol' }).zones.map(z => z.acquired);
  assert.deepEqual(petrol, rawv('ACQUIRED_ZONES_PETROL').map(v => v !== 0));
});

test('painel Acquisition: quadrado só com contador ≥ limiar, no MAP do buffer; limiar muda na banda 5', () => {
  const model = build(snapshot, { fuel: 'gas' });
  const counters = rawv('NUM_BUF_UPD_GAS');
  const cal = rawv('CALIBRATION_VAL_1');
  const expected = counters.map((c, band) => c >= (band <= 5 ? cal[5] : cal[8]) && c > 0).map((v, band) => (v ? band : -1)).filter(v => v >= 0);
  assert.deepEqual(model.gasSquares.filter(s => s.shown).map(s => s.band), expected);
  assert.ok(expected.length > 0);
  const buf = phys('MNFLD_PRESS_BUF_GAS');
  for (const s of model.gasSquares.filter(x => x.shown)) assert.equal(s.y, buf[s.band]);
});

test('nós com Tinj ou MAP = 0 são omitidos; nós abaixo do eixo MAP ficam invisíveis', () => {
  const model = build(snapshot, { fuel: 'gas' });
  const rv = phys('PETR_MNFLD_PRESS_RV');
  assert.equal(model.petrolCurve.length, rv.filter(v => v !== 0).length);
  const hidden = model.petrolCurve.filter(n => !n.visible);
  assert.ok(hidden.length >= 1 && hidden.every(n => n.y < 0.15 || n.y > 1.10));
  assert.ok(model.petrolCurve.filter(n => n.visible).every(n => n.y >= 0.15 && n.y <= 1.10));
});

test('curva K = MUL_ACT por nó de PETR_INJ_TBP, eixo Y 0,4–2,0 (original 0,4–1,6)', () => {
  const model = build(snapshot, { fuel: 'gas' });
  assert.equal(model.kNodes.length, 30);
  assert.deepEqual(model.kNodes.map(n => n.y), phys('MUL_ACT'));
  // Original: 0,4–1,6. Aqui 0,4–2,0 para um nó acima do teto de gravação (1,8) aparecer, com a linha do teto.
  assert.deepEqual({ min: model.kAxisY.min, max: model.kAxisY.max }, { min: 0.4, max: 2.0 });
});

test('banda atual e ponto de operação vêm do MAP/Tinj vivos', () => {
  const thd = phys('MNFLD_PRESS_THD');
  const model = build(snapshot, { fuel: 'gas', live: { mapBar: 0.894, petrolMs: 12.2 } });
  assert.equal(model.band.index, thd.findIndex((v, i) => i < 17 && 0.894 > v && 0.894 <= thd[i + 1]));
  assert.deepEqual(model.run, { x: 12.2, y: 0.894 });
  assert.equal(build(snapshot, { fuel: 'gas' }).band, null, 'sem telemetria não há banda inventada');
});

test('sem leitura anterior (regra do dono): o modelo e o desenho não têm GasPointPrev', () => {
  const model = build(snapshot, { fuel: 'gas', live: { mapBar: 0.5, petrolMs: 5 } });
  assert.equal(Object.keys(model).some(k => /prev/i.test(k)), false);
  const svg = O.renderMain(model, 700, 420) + O.renderAcquisition(model, 140, 420) + O.renderK(model, 700, 160);
  assert.equal(/prev/i.test(svg), false);
});

test('desenho: rótulos #n/d, curvas, limites, banda e curva K presentes', () => {
  const model = build(snapshot, { fuel: 'gas', live: { mapBar: 0.894, petrolMs: 12.2 } });
  const main = O.renderMain(model, 700, 420);
  for (const zone of model.zones) assert.ok(main.includes('>' + zone.label + '<'), 'rótulo ' + zone.label);
  assert.match(main, /data-series="petrol-curve"/);
  assert.match(main, /data-series="gas-curve"/);
  assert.match(main, /data-limit="min"/);
  assert.match(main, /data-limit="max"/);
  assert.match(main, /data-current-band=/);
  assert.match(O.renderAcquisition(model, 140, 420), /class="ao-square gas"/);
  assert.match(O.renderK(model, 700, 160), /data-series="k-line"/);
});

test('tudo é "—" quando falta o eixo da ECU: nenhum desenho inventado', () => {
  const empty = build({ fields: [] }, { fuel: 'gas' });
  assert.equal(empty.ok, false);
  assert.equal(O.renderMain(empty, 700, 420), '');
});

test('depois de um reset (vetores zerados) o modelo continua válido e vazio, sem NaN', () => {
  const zeroed = JSON.parse(JSON.stringify(snapshot));
  for (const f of zeroed.fields) {
    if (['NUM_BUF_UPD_PETR', 'NUM_BUF_UPD_GAS', 'PETR_INJ_TBUF', 'MNFLD_PRESS_BUF', 'PETR_INJ_TBUF_GAS', 'MNFLD_PRESS_BUF_GAS',
      'PETR_MNFLD_PRESS_RV', 'GAS_MNFLD_PRESS_RV', 'ACQUIRED_ZONES_PETROL', 'ACQUIRED_ZONES_GAS'].includes(f.key)) {
      f.rawValues = f.rawValues.map(() => 0); f.physicalValues = f.physicalValues.map(() => 0);
    }
  }
  const model = build(zeroed, { fuel: 'gas', live: { mapBar: 0.4, petrolMs: 4 } });
  assert.equal(model.ok, true);
  assert.equal(model.gasSquares.some(s => s.shown), false);
  assert.deepEqual(model.zones.map(z => z.label), ['#0/3', '#0/3', '#0/3', '#0/3']);
  const svg = O.renderMain(model, 700, 420) + O.renderAcquisition(model, 140, 420) + O.renderK(model, 700, 160);
  assert.equal(/NaN|Infinity|undefined/.test(svg), false);
});

test('bolinhas por zona: verde = completa, azul = coletando, laranja = falta, cinza = sem leitura', () => {
  const states = plain(O.zoneStates(snapshot, 'gas')).map(z => z.state);
  const flags = rawv('ACQUIRED_ZONES_GAS');
  const buf = phys('MNFLD_PRESS_BUF_GAS');
  const expected = [0, 1, 2, 3].map(zone => {
    if (flags[zone] !== 0) return 'done';
    const edges = [0, 5, 9, 13, 17];
    return buf.slice(edges[zone], edges[zone + 1]).some(v => v !== 0) ? 'collecting' : 'missing';
  });
  assert.deepEqual(states, expected);
  assert.ok(states.includes('done') && states.includes('collecting'));
  assert.deepEqual(plain(O.zoneStates({ fields: [] }, 'gas')).map(z => z.state), ['unknown', 'unknown', 'unknown', 'unknown']);
  const zeroed = JSON.parse(JSON.stringify(snapshot));
  for (const f of zeroed.fields) if (['ACQUIRED_ZONES_GAS', 'MNFLD_PRESS_BUF_GAS'].includes(f.key)) { f.rawValues = f.rawValues.map(() => 0); f.physicalValues = f.physicalValues.map(() => 0); }
  assert.deepEqual(plain(O.zoneStates(zeroed, 'gas')).map(z => z.state), ['missing', 'missing', 'missing', 'missing'], 'depois de um reset tudo falta');
});

test('bolinhas dos ajustes automáticos: uma por AutoMatch da cota, cheia = feito, sem contador = nada', () => {
  assert.deepEqual(plain(O.automatchDots(2, 3)), [{ index: 0, done: true }, { index: 1, done: true }, { index: 2, done: false }]);
  assert.deepEqual(plain(O.automatchDots(0, 3)).map(d => d.done), [false, false, false]);
  assert.deepEqual(plain(O.automatchDots(null, 3)), []);
  assert.deepEqual(plain(O.automatchDots(1, null)), []);
  assert.deepEqual(plain(O.automatchDots(1, 99)), [], 'cota absurda não vira 99 bolinhas');
});

test('pontos têm área de toque de pelo menos 16 px de raio (celular) e o ponto visível por cima', () => {
  const model = build(snapshot, { fuel: 'gas' });
  const main = O.renderMain(model, 700, 420);
  const hits = (main.match(/class="ao-hit"[^>]*r="(\d+)"/g) || []);
  const visible = (main.match(/class="ao-point (petrol|gas)"/g) || []);
  assert.ok(hits.length > 0 && hits.length === visible.length, 'um toque para cada ponto visível');
  assert.ok(hits.every(h => Number(/r="(\d+)"/.exec(h)[1]) >= 16));
});

test('curva K: linha do teto 1,8 e nó acima do teto em vermelho (nunca some do gráfico)', () => {
  const high = JSON.parse(JSON.stringify(snapshot));
  const k = high.fields.find(f => f.key === 'MUL_ACT');
  k.physicalValues[10] = 1.95; k.rawValues[10] = Math.round(1.95 * 16384);
  const svg = O.renderK(build(high, { fuel: 'gas' }), 700, 160);
  assert.match(svg, /data-k-ceiling="1.8"/);
  assert.match(svg, /class="ao-k-node over" data-node="10"/);
  assert.equal((O.renderK(build(snapshot, { fuel: 'gas' }), 700, 160).match(/ao-k-node over/g) || []).length, 0);
});
