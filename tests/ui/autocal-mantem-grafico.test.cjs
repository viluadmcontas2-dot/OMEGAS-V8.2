'use strict';

// Dono, 2026-10-13: no original o gráfico NÃO se apaga nem se refaz ao resetar, adquirir zona ou esperar leitura:
// segue mostrando o último estado válido (o AGORA continua vivo) até a próxima leitura boa substituir tudo.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const host = { innerHTML: '', getBoundingClientRect: () => ({ width: 860, height: 300 }), querySelectorAll: () => [] };
const context = { console, setTimeout: () => 0, clearTimeout: () => {}, document: { getElementById: id => (id === 'autocalReferenceChart' ? host : null) } };
context.globalThis = context;
vm.createContext(context);
vm.runInContext(source, context, { filename: 'autocal-cockpit.js' });
const builds = [];
context.OmegasUi.AutoCalOriginal = {
  build: (snapshot) => { builds.push(snapshot.tag); return snapshot.good ? { ok: true, tag: snapshot.tag, zones: [{ label: 'Z1' }] } : { ok: false, reason: 'Aguardando eixo e limiares da central.' }; },
  staticModel: model => model, renderLive: () => ({ band: '', run: '', acq: '', k: '' }), liveState: () => ({ band: null, run: null, mapNow: null }),
  renderAcquisition: model => '<acq ' + model.tag + '>', renderMain: model => '<main ' + model.tag + '>', renderK: model => '<k ' + model.tag + '>',
};
const render = context.OmegasUi.AutoCalCockpit.prototype.renderOriginalChart;
function run(self, snapshot) { return render.call(self, snapshot); }
const makeSelf = () => ({ liveTelemetry: () => null, paintOriginalLive() {}, text: () => {}, selectedAcquiredPoints: new Set(), renderedChartHost: null, chartRenderKey: null });

test('snapshot ruim depois de um bom (reset/zona nova): mantém o gráfico, sem apagar', () => {
  const self = makeSelf();
  assert.equal(run(self, { tag: 'A', good: true }), true);
  assert.match(host.innerHTML, /<main A>/);
  assert.equal(run(self, { tag: 'B', good: false }), true, 'segue desenhando');
  assert.match(host.innerHTML, /<main A>/, 'o último estado válido continua na tela');
  assert.equal(run(self, { tag: 'C', good: true }), true);
  assert.match(host.innerHTML, /<main C>/, 'a próxima leitura boa substitui tudo');
});

test('sem nenhum estado válido ainda: devolve false (a tela mostra o aviso honesto)', () => {
  assert.equal(run(makeSelf(), { tag: 'X', good: false }), false);
});
