'use strict';
// Fix UI · AutoCal (achados 5 e 13). Teste de USO: botão que não pode reabilitar antes da resposta, texto congelado, jargão.
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const L = require('./wiring/lib.cjs');
const { assert } = L;
const W = require('./wiring/world.cjs');

const UI = path.join(__dirname, '../../app/src/main/assets/ui');
const FRAMES = W.realFrames('automatch_', f => f.fuel === 'GNV' && f.petrol_ms > 2);

function boot(enabled) {
  const w = new W.World();
  w.setFrame(FRAMES[10]);
  w.projection = W.realProjection('automatch_', 11);
  if (enabled !== undefined) w.projection.nativeStatus = { ...w.projection.nativeStatus, autoCalEnabled: enabled };
  const app = L.boot({ world: w });
  app.go('autocal');
  app.settle(4);
  return app;
}
const toggle = app => app.$('[data-autocal-toggle]');

test('5. Pausar/Iniciar leitura: depois do toque o botão fica em "Confirmando ECU…" até a ECU confirmar (não reabilita antes da resposta)', () => {
  const app = boot(1);
  assert.equal(toggle(app).textContent, 'Pausar leitura');
  assert.equal(toggle(app).hasAttribute('disabled'), false);
  const mark = app.world.mark();
  toggle(app).click(); app.flush();
  assert.equal(app.world.since(mark).filter(c => c.method === 'setAcquisitionEnabled').length, 1);
  // a resposta do envio foi "ok", mas a ECU ainda não confirmou: o botão continua travado, por vários ciclos
  app.advance(3000);
  assert.equal(toggle(app).hasAttribute('disabled'), true, 'não reabilita antes da conferência');
  assert.equal(toggle(app).textContent, 'Confirmando ECU…');
  // toque duplo no meio da espera não envia de novo
  toggle(app).click(); app.flush();
  assert.equal(app.world.since(mark).filter(c => c.method === 'setAcquisitionEnabled').length, 1);
  // a ECU confirma (novo estado chega pela projeção)
  app.world.projection = { ...app.world.projection, nativeStatus: { ...app.world.projection.nativeStatus, autoCalEnabled: 0 } };
  app.advance(2500);
  assert.equal(toggle(app).hasAttribute('disabled'), false);
  assert.equal(toggle(app).textContent, 'Iniciar leitura');
  app.destroy();
});

test('5. se a ECU não confirma em 10 s o botão volta (não fica preso para sempre)', () => {
  const app = boot(1);
  toggle(app).click(); app.flush();
  app.advance(11000);
  assert.equal(toggle(app).hasAttribute('disabled'), false);
  assert.equal(toggle(app).textContent, 'Pausar leitura');
  app.destroy();
});

test('13. "Lendo estado…" não fica eterno: sem estado da ECU o botão diz isso e deixa reler com um toque', () => {
  const app = boot();
  assert.equal(toggle(app).textContent, 'Lendo estado…');
  app.advance(8000);
  assert.match(toggle(app).textContent, /Estado não chegou · reler/);
  assert.equal(toggle(app).hasAttribute('disabled'), false);
  const mark = app.world.mark();
  toggle(app).click(); app.flush();
  assert.ok(app.world.since(mark).some(c => c.method === 'getUiProjection'), 'o toque relê o estado');
  assert.equal(app.world.since(mark).filter(c => c.method === 'setAcquisitionEnabled').length, 0, 'reler nunca muda a ECU');
  app.destroy();
});

test('5. "há N s" do AutoCal anda sozinho enquanto a leitura está atrasada (não congela no primeiro segundo)', () => {
  const app = boot(1);
  app.world.setFrame(FRAMES[12]);
  app.advance(2100);
  const narrative = () => app.byId('autocalLiveNarrative').textContent;
  assert.match(narrative(), /atrasada há 2 s/);
  app.advance(1000);
  assert.match(narrative(), /(atrasada há 3 s|há 3 s)/, `texto: ${narrative()}`);
  app.advance(2000);
  assert.match(app.byId('autocalLiveTitle').textContent + narrative(), /há [4-6] s/, 'depois de 3 s vira "com atraso há N s"');
  app.destroy();
});

test('13. jargão fora de "Detalhes técnicos", palavras do glossário, zonas só com "FALTA" e contexto fechado (sem rolagem)', () => {
  const app = boot(1);
  const panel = app.$('.autocal-route-panel') || app.byId('autocalScreenHost');
  const tech = app.byId('autocalTechnicalDetails');
  const visible = [];
  const walk = n => { if (n === tech) return; if (n.nodeType === 3) visible.push(n.data); else if (n.localName !== 'script' && n.localName !== 'style') n.childNodes.forEach(walk); };
  walk(panel);
  const text = visible.join(' ');
  for (const bad of ['AUTOMATCH NATIVO', 'EVIDÊNCIA CAUSAL', 'AJUSTE NATIVO', 'época da ECU', 'mesma época', 'regiões correlacionadas', 'leitura nativa', 'contador nativo', 'vetores nativos']) {
    assert.ok(!text.includes(bad), `jargão "${bad}" fora de Detalhes técnicos`);
  }
  assert.match(text, /AutoMatch/);
  const cells = app.$$('.autocal-zone-cell small').map(n => n.textContent);
  assert.ok(!cells.includes('OK'), `grade de zonas sem "OK" repetido: ${cells}`);
  assert.ok(cells.every(t => t === '' || t === 'FALTA' || t === '—'));
  const css0 = fs.readFileSync(path.join(UI, 'styles-lote-f.css'), 'utf8');
  assert.match(css0, /autocal-secondary-stack \{ display: grid; grid-template-columns: minmax\(0, 1fr\) minmax\(0, 1fr\)/, 'contexto em 2 colunas: menos rolagem, sem toque extra');
  const css = fs.readFileSync(path.join(UI, 'styles-autocal-cockpit.css'), 'utf8');
  for (const sel of ['.autocal-zone-fuel', '.autocal-zone-cell b', '.autocal-zone-cell small']) {
    const rule = css.split('\n').find(line => line.startsWith(sel + ' {'));
    assert.match(rule, /font-size: var\(--text-critical\)/, sel);
  }
  app.destroy();
});
