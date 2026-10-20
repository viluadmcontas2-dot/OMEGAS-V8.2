'use strict';

// Sonda de escrita única nos pontos do GNV (dono, 2026-10-17). Saiu do que o leigo vê em Ferramentas (2026-10-20): pedia
// dois toques (arma e confirma) e escrevia na central, contra a regra "um toque = a ação". A lógica nativa continua provada
// com ECU falsa em BufferWriteProbeCoreTest.kt; aqui: a tela não a oferece e a ponte/registro nativos seguem de pé.

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const ROOT = path.join(__dirname, '../..');
const UI = path.join(ROOT, 'app/src/main/assets/ui');
const read = file => fs.readFileSync(path.join(UI, file), 'utf8');

test('o leigo não vê a sonda: Ferramentas sem "Teste de gravação", sem toque duplo e sem chamar a escrita', () => {
  const src = read('components/drawers.js');
  assert.doesNotMatch(src, /toolBufferProbe|Teste de gravação|startBufferWriteProbe/, 'a sonda saiu da tela');
  assert.doesNotMatch(src, /Toque de novo|Armed\b/, 'nenhum botão de Ferramentas pede dois toques');
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
