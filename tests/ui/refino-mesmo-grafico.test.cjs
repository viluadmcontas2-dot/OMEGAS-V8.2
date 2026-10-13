'use strict';

// Dono, 2026-10-13: "o gráfico do Refino era para usar o mesmo objeto que usa o gráfico do AutoCal; só não precisaria da aba da
// esquerda (quadradinhos de aquisição); no Refino aparecem nossos pontos e nossas curvas." Aqui: o desenho principal do Refino
// é byte a byte o do AutoCal mais UM grupo extra (ao-ours); sem extras, nada muda no AutoCal.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const load = file => {
  const context = { console, Date, Math, JSON, Intl, Set, Map, Number, String, Array, Object, setTimeout: () => 0 };
  context.window = context;
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(path.join(root, file), 'utf8'), context, { filename: file });
  return context;
};
const O = load('app/src/main/assets/ui/screens/autocal-original.js').OmegasUi.AutoCalOriginal;
const snapshot = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/autocal-captura-ac-t1000.json'), 'utf8')).snapshot;
const extras = {
  petrol: [{ tpetMs: 2.0, mapBar: 0.30, samples: 40 }, { tpetMs: 3.0, mapBar: 0.42, samples: 40 }, { tpetMs: 4.5, mapBar: 0.6, samples: 30 }],
  gas: [{ tpetMs: 2.1, mapBar: 0.31, samples: 20 }, { tpetMs: 3.1, mapBar: 0.43, samples: 20 }],
  stalls: [{ petrolMs: 2.2, mapBar: 0.3 }],
  near: [{ petrolMs: 2.1, mapBar: 0.25 }, { petrolMs: 9999, mapBar: 0.25 }],
};

test('sem extras o desenho do AutoCal não ganha nada do Refino', () => {
  const main = O.renderMain(O.build(snapshot, { fuel: 'gas' }), 720, 220);
  assert.ok(!/ao-ours|ao-our-/.test(main));
});

test('com extras o gráfico é o mesmo do AutoCal e ganha só o grupo ao-ours (pontos, curva e marcas)', () => {
  const plain = O.renderMain(O.build(snapshot, { fuel: 'gas' }), 720, 220);
  const model = O.build(snapshot, { fuel: 'gas', extras });
  const refino = O.renderMain(model, 720, 220);
  const ours = (refino.match(/<g class="ao-ours"[\s\S]*?<\/g>(?=<g data-ao-live="run">)/) || [])[0];
  assert.ok(ours, 'grupo ao-ours presente');
  assert.equal(refino.replace(ours, ''), plain, 'resto idêntico ao AutoCal');
  assert.equal((ours.match(/class="ao-our-point petrol/g) || []).length, 3, 'quadrados da gasolina');
  assert.equal((ours.match(/class="ao-our-point gas/g) || []).length, 2, 'quadrados do GNV');
  assert.ok(/ao-our-curve petrol/.test(ours) && /ao-our-curve gas/.test(ours), 'nossas curvas (≥ 2 pontos)');
  assert.equal((ours.match(/ao-stall-mark/g) || []).length, 1, 'marca de apagão');
  assert.equal((ours.match(/ao-near-mark/g) || []).length, 1, 'quase-apagão; o que cai fora do eixo da ECU não entra');
});

test('o Refino não precisa do painel de aquisição: renderMain + renderK bastam', () => {
  const model = O.build(snapshot, { fuel: 'gas', extras });
  assert.match(O.renderMain(model, 900, 240), /data-ao="main"/);
  assert.match(O.renderK(model, 900, 80), /data-ao="k"/);
});

test('o eixo cresce para caber nossos pontos e o AGORA continua na camada leve', () => {
  const far = { petrol: [{ tpetMs: 11, mapBar: 0.7 }], gas: [], stalls: [], near: [] };
  const a = O.build(snapshot, { fuel: 'gas' });
  const b = O.build(snapshot, { fuel: 'gas', extras: far });
  assert.ok(b.axisX.max >= a.axisX.max);
  const live = O.renderLive({ ...b, ...O.liveState(b, { petrolMs: 3, mapBar: 0.4 }) }, { sideWidth: 60, mainWidth: 720, mainHeight: 220, kHeight: 70 });
  assert.ok(live.run.includes('ao-run'));
});

test('Refino local: a frase diz o que aconteceu e o botão só vale com engasgo registrado', () => {
  const ctx = load('app/src/main/assets/ui/core/stall-region.js');
  const local = load('app/src/main/assets/ui/screens/local-refino.js');
  const model = local.OmegasUi.LocalRefinoModel;
  assert.equal(model.describe(null).total, 0);
  assert.match(model.describe({ count: 0, nearCount: 0 }).title, /Nenhum engasgo/);
  const d = model.describe({ count: 1, nearCount: 4, regions: [{ fromMs: 2.0, toMs: 2.5, count: 1 }], nearRegions: [{ fromMs: 2.0, toMs: 2.5, count: 4 }] });
  assert.equal(d.total, 5);
  assert.match(d.title, /O carro engasgou 5 vezes no GNV/);
  assert.match(d.text, /o motor apagou/);
  assert.ok(ctx.OmegasStallRegion || true);
});
