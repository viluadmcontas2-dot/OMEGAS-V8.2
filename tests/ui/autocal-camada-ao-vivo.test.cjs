'use strict';

// Gráfico do AutoCal em tempo real (dono, 2026-10-13: "o cursor está extremamente lento / as zonas não ficam em tempo real").
// Só a camada AO VIVO (faixa de MAP atual + cursor) anda a cada amostra; o gráfico inteiro só é refeito quando a central manda
// dado novo. Aqui: a camada leve é IDÊNTICA ao que o desenho inteiro produziria, a chave do gráfico não depende do AGORA,
// e a camada leve é muito menor que o desenho inteiro.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const context = { console };
context.globalThis = context;
vm.createContext(context);
vm.runInContext(fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-original.js'), 'utf8'), context, { filename: 'autocal-original.js' });
const O = context.OmegasUi.AutoCalOriginal;
const snapshot = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/autocal-captura-ac-t1000.json'), 'utf8')).snapshot;
const SIZES = { sideWidth: 150, mainWidth: 720, mainHeight: 220, kHeight: 70 };
const group = (html, name) => (html.match(new RegExp('<g data-ao-live="' + name + '">([\\s\\S]*?)</g>')) || [])[1];
const liveSample = (petrolMs, mapBar) => ({ petrolMs, mapBar });

test('a camada leve é idêntica ao que o desenho inteiro produz para o mesmo AGORA', () => {
  for (const [ms, map] of [[3.8, 0.42], [6.1, 0.71], [2.0, 0.2], [12.5, 0.93]]) {
    const model = O.build(snapshot, { fuel: 'gas', live: liveSample(ms, map) });
    assert.equal(model.ok, true);
    const main = O.renderMain(model, SIZES.mainWidth, SIZES.mainHeight);
    const acq = O.renderAcquisition(model, SIZES.sideWidth, SIZES.mainHeight);
    const k = O.renderK(model, SIZES.mainWidth, SIZES.kHeight);
    const live = O.renderLive(model, SIZES);
    assert.equal(group(main, 'band'), live.band, `faixa de MAP em ${ms} ms / ${map} bar`);
    assert.equal(group(main, 'run'), live.run, 'cursor');
    assert.equal(group(acq, 'acq'), live.acq, 'MAP no painel Acquisition');
    assert.equal(group(k, 'k'), live.k, 'linha do cursor na curva K');
  }
});

test('o AGORA muda a camada leve mas NÃO a chave do gráfico inteiro; dado novo da central muda a chave', () => {
  const a = O.build(snapshot, { fuel: 'gas', live: liveSample(3.0, 0.40) });
  const b = O.build(snapshot, { fuel: 'gas', live: liveSample(3.2, 0.55) });
  assert.notEqual(JSON.stringify(a), JSON.stringify(b), 'o modelo completo muda com o AGORA');
  assert.equal(JSON.stringify(O.staticModel(a)), JSON.stringify(O.staticModel(b)), 'a chave estática não muda');
  assert.notDeepEqual(O.renderLive(a, SIZES).run, O.renderLive(b, SIZES).run);
  const moved = JSON.parse(JSON.stringify(snapshot));
  const field = moved.fields.find(f => f.key === 'MNFLD_PRESS_BUF_GAS');
  field.physicalValues = field.physicalValues.map((v, i) => (i === 3 ? v + 0.05 : v));
  const c = O.build(moved, { fuel: 'gas', live: liveSample(3.0, 0.40) });
  assert.notEqual(JSON.stringify(O.staticModel(a)), JSON.stringify(O.staticModel(c)), 'ponto novo adquirido muda a chave');
});

test('liveState dá faixa, ponto e MAP agora; sem AGORA, nada', () => {
  const model = O.build(snapshot, { fuel: 'gas' });
  const state = O.liveState(model, liveSample(4.0, 0.5));
  assert.equal(state.mapNow, 0.5);
  assert.deepEqual(JSON.parse(JSON.stringify(state.run)), { x: 4.0, y: 0.5 });
  assert.ok(state.band && state.band.lower <= 0.5 && state.band.upper >= 0.5);
  assert.deepEqual(JSON.parse(JSON.stringify(O.liveState(model, null))), { band: null, run: null, mapNow: null });
});

test('a camada leve é pelo menos 10 vezes menor que o gráfico inteiro (o que se refaz por amostra)', () => {
  const model = O.build(snapshot, { fuel: 'gas', live: liveSample(3.8, 0.42) });
  const full = O.renderMain(model, SIZES.mainWidth, SIZES.mainHeight).length + O.renderAcquisition(model, SIZES.sideWidth, SIZES.mainHeight).length + O.renderK(model, SIZES.mainWidth, SIZES.kHeight).length;
  const live = O.renderLive(model, SIZES);
  const light = live.band.length + live.run.length + live.acq.length + live.k.length;
  assert.ok(full / light >= 10, `inteiro ${full} caracteres × leve ${light}`);
});
