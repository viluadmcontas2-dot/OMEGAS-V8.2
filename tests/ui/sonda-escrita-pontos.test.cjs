'use strict';

// Sonda de escrita única nos pontos do GNV (dono, 2026-10-17): toque duplo em Ferramentas, uma escrita,
// restaura o original. Aqui: marcação, texto em português claro e o toque duplo; a lógica da sonda tem ECU falsa
// em BufferWriteProbeCoreTest.kt.

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const ROOT = path.join(__dirname, '../..');
const UI = path.join(ROOT, 'app/src/main/assets/ui');
const read = file => fs.readFileSync(path.join(UI, file), 'utf8');

function load() {
  const label = { textContent: '1 valor, banda cheia, volta ao original (carro parado)' };
  const button = { querySelector: () => label };
  const context = {
    console, Date, Math, JSON, Intl, Set, Map, Number, String, Array, Object, setTimeout, clearTimeout,
    document: { getElementById: id => (id === 'toolBufferProbe' ? button : null), querySelectorAll: () => [] },
  };
  context.window = context;
  context.globalThis = context;
  context.OmegasUi = {};
  vm.createContext(context);
  vm.runInContext(read('components/drawers.js'), context, { filename: 'drawers.js' });
  return { context, label };
}

test('botão da sonda existe em Ferramentas e fala em 1 valor / carro parado', () => {
  assert.match(read('index.html'), /id="toolBufferProbe"[\s\S]*?Testar escrita nos pontos[\s\S]*?carro parado/);
});

test('a ponte nativa expõe a sonda e a sessão trata o evento como crítico', () => {
  const bridge = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt'), 'utf8');
  assert.match(bridge, /fun startBufferWriteProbe\(\)/);
  assert.match(bridge, /fun getBufferWriteProbeStatus\(\)/);
  const recorder = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt'), 'utf8');
  assert.match(recorder, /"buffer_write_probe"/);
});

test('só a sonda escreve no GNV: um único valor com SetNumber indexado e restauração', () => {
  const core = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/omegas/prohub/autocal/BufferWriteProbeCore.kt'), 'utf8');
  const probe = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/omegas/prohub/autocal/BufferWriteProbe.kt'), 'utf8');
  assert.match(core, /STEP_RAW = 8/);
  assert.match(core, /FULL_COUNT = 10/);
  assert.match(probe, /AutoCalProtocol\.writeIndexedU16\(AutoCalProtocol\.PETR_INJ_TBUF_GAS\.address/);
  assert.doesNotMatch(probe, /writeVectorU16/, 'a sonda não usa vetor: o quadro indexado já grava a curva K na ECU real');
});

test('descrições dos desfechos em português claro', () => {
  const { context } = load();
  const D = context.OmegasUi.Drawers;
  assert.match(D.describeBufferProbe({ outcome: 'REFUSED' }), /recusou.*Nada mudou/);
  assert.match(D.describeBufferProbe({ outcome: 'ACCEPTED_AND_HELD', band: 4, restoreNeeded: true, restored: true }), /aceitou e o valor ficou.*restaurado/);
  assert.match(D.describeBufferProbe({ outcome: 'ACCEPTED_THEN_OVERWRITTEN' }), /desfez sozinha/);
  assert.match(D.describeBufferProbe({ outcome: 'ACCEPTED_BUT_DIFFERENT', restoreNeeded: true, restored: false }), /NÃO restaurado.*Reset GNV/);
  assert.match(D.describeBufferProbe({ outcome: 'NO_STABLE_BAND' }), /nada foi escrito/);
  assert.match(D.describeBufferProbe({ outcome: 'ERROR', message: 'cabo' }), /sem resultado: cabo/);
});

test('toque duplo: o primeiro só arma, o segundo chama a ponte', () => {
  const { context, label } = load();
  let calls = 0;
  context.OmegasAutoCal = {
    startBufferWriteProbe: () => { calls += 1; return JSON.stringify({ ok: true, started: true }); },
    getBufferWriteProbeStatus: () => JSON.stringify({ state: 'DONE', outcome: 'REFUSED' }),
  };
  const drawers = Object.create(context.OmegasUi.Drawers.prototype);
  drawers.store = { patch() {} };
  drawers.runBufferProbe();
  assert.equal(calls, 0, 'primeiro toque não escreve');
  assert.match(label.textContent, /Toque de novo/);
  drawers.runBufferProbe();
  assert.equal(calls, 1, 'segundo toque inicia a sonda');
  assert.equal(label.textContent, 'Rodando…');
  clearTimeout(drawers.bufferProbeTimer);
  drawers.bufferProbeRunning = false;
});
