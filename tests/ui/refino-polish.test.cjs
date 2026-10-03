const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = path.resolve(__dirname, '../..');
const read = relative => fs.readFileSync(path.join(ROOT, relative), 'utf8');
const SOURCE = read('app/src/main/assets/ui/screens/refino.js');

function model() {
  const window = { setTimeout: () => 0 };
  window.window = window;
  vm.runInNewContext(SOURCE, { window, globalThis: window, console });
  return window.OmegasUi.RefinoModel;
}

function analysis(changed) {
  return {
    available: true,
    points: Array.from({ length: 30 }, (_, index) => ({
      index,
      currentRaw: 16384,
      calculatedRaw: changed.includes(index) ? 17000 : 16384,
      origin: changed.includes(index) ? 'MEASURED' : 'HELD',
      referenceTimeMs: index + 1,
    })),
  };
}

// ---------------------------------------------------------------- Lote A: becos sem saída

test('A1/A2: o prazo vencido não apaga a proposta: o botão "Revisar e gravar" continua', () => {
  const m = model();
  const ready = analysis([4, 5]);
  for (const expiredFrom of ['PROPOSTA_PRONTA', 'ECU_TRABALHANDO']) {
    const action = m.primaryAction({ autopilot: { phase: 'TENTATIVA_ENCERRADA', expiredFrom } }, ready);
    assert.equal(action.kind, 'review', expiredFrom);
    assert.match(action.label, /Revisar e gravar 2 pontos/);
  }
  // Sem proposta, ou o prazo de leitura da ECU: nenhuma ação.
  assert.equal(m.primaryAction({ autopilot: { phase: 'TENTATIVA_ENCERRADA', expiredFrom: 'PROPOSTA_PRONTA' } }, analysis([])).kind, 'none');
  assert.equal(m.primaryAction({ autopilot: { phase: 'TENTATIVA_ENCERRADA', expiredFrom: 'LENDO_ECU' } }, ready).kind, 'none');
  // O aviso de revisão do automático da ECU usa a fase de origem (antes era código morto).
  assert.match(SOURCE, /pilot\.phase === 'TENTATIVA_ENCERRADA' \? pilot\.expiredFrom : pilot\.phase/);
});

test('A8: fase e proposta concordam nos textos', () => {
  const m = model();
  const none = m.agreedTexts({ autopilot: { phase: 'PROPOSTA_PRONTA' } }, analysis([]));
  assert.match(none.headline, /ainda não há uma correção segura/);
  assert.doesNotMatch(none.headline + none.next, /Revise e grave|Revisar e gravar/);
  const measuring = m.agreedTexts({ autopilot: { phase: 'COLETANDO_NOSSOS' } }, analysis([4]));
  assert.match(measuring.headline, /Revisar e gravar/);
  assert.doesNotMatch(measuring.headline, /medindo/i);
  assert.equal(m.agreedTexts({ autopilot: { phase: 'PROPOSTA_PRONTA' } }, analysis([4])), null);
});

test('A3/A4: Desfazer sai do <details>, vem do diário e falha parcial não diz "nada foi gravado"', () => {
  const m = model();
  assert.equal(m.undoSource({ photoFile: 'foto_B.json' }).photoFile, 'foto_B.json');
  assert.equal(m.undoSource({ status: 'FALHA_PARCIAL', photoFile: 'foto.json' }).available, true);
  assert.equal(m.undoSource({ status: 'FALHA_PARCIAL' }).available, false);
  assert.ok(SOURCE.indexOf('id="refinoUndo"') > 0 && SOURCE.indexOf('id="refinoUndo"') < SOURCE.indexOf('id="refinoResultDetails"'), 'Desfazer antes do <details>');
  assert.doesNotMatch(SOURCE, /lastPhotoFile/, 'a foto vem do ÚLTIMO experimento do diário, não de memória da tela');
  assert.match(SOURCE, /op\.partial/);
  assert.doesNotMatch(SOURCE, /Nada foi dado como gravado/);
});

test('A5: mínimo e máximo nunca devolvem infinito para lista vazia', () => {
  const m = model();
  assert.equal(m.safeMin([], 0.3), 0.3);
  assert.equal(m.safeMax([], 0.9), 0.9);
  assert.equal(m.safeMax([NaN, 2, 5], 0), 5);
  assert.doesNotMatch(SOURCE, /Math\.(min|max)\(\.\.\.(xs|ys)\)/);
});

test('A6: pontos da ECU e nossos pontos têm alvo de toque', () => {
  const hits = [...SOURCE.matchAll(/class="autocal-acquired-hit" data-refino-dot="(ecu|our):/g)].map(m => m[1]);
  assert.deepEqual(hits.sort(), ['ecu', 'our']);
  assert.match(SOURCE, /r="22"/);
});

test('A8: nenhum texto manda "sem sua confirmação" nem para "AutoCal → Refinar curva"', () => {
  const kotlin = read('app/src/main/java/com/omegas/prohub/autocal/EquivalencePhases.kt');
  assert.doesNotMatch(kotlin, /sem sua confirmação/);
  assert.doesNotMatch(kotlin, /AutoCal → Refinar curva/);
  assert.match(kotlin, /na aba Refino/);
});
