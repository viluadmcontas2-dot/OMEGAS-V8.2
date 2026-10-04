const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = path.resolve(__dirname, '../..');
const read = relative => fs.readFileSync(path.join(ROOT, relative), 'utf8');
const SOURCE = read('app/src/main/assets/ui/screens/refino.js');

function model() {
  const window = require('./_support.cjs').freshContext({ console });
  vm.runInContext(SOURCE, window);
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

test('A1/A2: o prazo vencido não apaga a proposta: o botão "Gravar N pontos" continua', () => {
  const m = model();
  const ready = analysis([4, 5]);
  for (const expiredFrom of ['PROPOSTA_PRONTA', 'ECU_TRABALHANDO']) {
    const action = m.primaryAction({ autopilot: { phase: 'TENTATIVA_ENCERRADA', expiredFrom } }, ready);
    assert.equal(action.kind, 'review', expiredFrom);
    assert.match(action.label, /Gravar 2 pontos/);
  }
  // Sem proposta, ou o prazo de leitura da ECU: nenhuma ação.
  assert.equal(m.primaryAction({ autopilot: { phase: 'TENTATIVA_ENCERRADA', expiredFrom: 'PROPOSTA_PRONTA' } }, analysis([])).kind, 'none');
  assert.equal(m.primaryAction({ autopilot: { phase: 'TENTATIVA_ENCERRADA', expiredFrom: 'LENDO_ECU' } }, ready).kind, 'none');
  // O aviso de revisão do automático da ECU usa a fase de origem (antes era código morto).
  assert.match(SOURCE, /pilot\.phase === 'TENTATIVA_ENCERRADA' \? pilot\.expiredFrom : pilot\.phase/);
});

test('P1: uma fala só por tela: o texto do cérebro (nextAction); sem ele, aviso neutro e nenhuma ação derivada da fase', () => {
  const m = model();
  assert.equal(m.agreedTexts, undefined, 'a tabela de textos por fase saiu');
  const eq = { autopilot: { phase: 'PROPOSTA_PRONTA', headline: 'texto velho do piloto', next: 'outro texto velho' }, nextAction: { text: 'Revise e grave a curva pronta', route: 'refino' } };
  const strip = m.equivalenceStrip(eq, ['refino']);
  assert.equal(strip.nextText, 'Revise e grave a curva pronta');
  assert.doesNotMatch(strip.nextText, /velho/);
  const none = m.equivalenceStrip({ autopilot: { phase: 'PROPOSTA_PRONTA', headline: 'x', next: 'y' } }, ['refino']);
  assert.equal(none.nextText, 'Aguardando dados da ECU');
  assert.equal(none.route, '', 'sem cérebro, sem botão');
  assert.doesNotMatch(SOURCE, /pilot\.headline|pilot\.next/, 'o texto do piloto não é mais renderizado');
});

test('A3/A4: Desfazer sai do <details>, vem do diário e falha parcial não diz "nada foi gravado"', () => {
  const m = model();
  assert.equal(m.undoSource({ photoFile: 'foto_B.json' }).photoFile, 'foto_B.json');
  assert.equal(m.undoSource({ status: 'FALHA_PARCIAL', photoFile: 'foto.json' }).available, true);
  assert.equal(m.undoSource({ status: 'FALHA_PARCIAL' }).available, false);
  const L = require('./wiring/lib.cjs');
  const app = L.boot(); app.go('refino'); app.settle(3);
  assert.ok(app.byId('refinoUndo'));
  assert.equal(app.byId('refinoUndo').closest('details'), null, 'Desfazer fora de qualquer details');
  app.destroy();
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

test('A6: pontos da ECU e marcadores do OMEGAS têm alvo de toque (círculo invisível de 44 px no desenho compartilhado)', () => {
  const chart = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/components/curve-chart.js'), 'utf8');
  assert.match(chart, /class="autocal-acquired-hit[^"]*"[^>]*data-refino-dot="ecu:/, 'pontos da ECU');
  assert.match(chart, /data-refino-dot="ourb:\$\{i\}"/, 'bolinhas do OMEGAS');
  assert.match(chart, /r="22"/);
});

test('A8: nenhum texto manda "sem sua confirmação" nem para "AutoCal → Refinar curva"', () => {
  const kotlin = read('app/src/main/java/com/omegas/prohub/autocal/EquivalencePhases.kt');
  assert.doesNotMatch(kotlin, /sem sua confirmação/);
  assert.doesNotMatch(kotlin, /AutoCal → Refinar curva/);
  assert.match(kotlin, /Abra o Refino e toque em Gravar/);
});
