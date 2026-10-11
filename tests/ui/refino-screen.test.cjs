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

test('revisão propõe só pontos medidos que mudam; mantidos nunca são gravados', () => {
  const m = model();
  const points = m.proposedPoints(analysis([4, 5, 6]));
  assert.equal(points.length, 3);
  assert.equal(JSON.stringify(points[0]), JSON.stringify({ index: 4, currentRaw: 16384, targetRaw: 17000 }));
  const held = analysis([]);
  held.points[2] = { ...held.points[2], calculatedRaw: 17000, origin: 'HELD' };
  assert.equal(m.proposedPoints(held).length, 0);
});

test('desfazer usa antes/depois do diário', () => {
  const m = model();
  const before = Array(30).fill(16384);
  const after = before.map((v, i) => (i === 10 ? 18000 : v));
  const points = m.undoPoints({ beforeRaw: before, afterRaw: after });
  assert.equal(points.length, 1);
  assert.equal(JSON.stringify(points[0]), JSON.stringify({ index: 10, currentRaw: 18000, targetRaw: 16384 }));
  assert.equal(m.undoPoints({}).length, 0);
});

// Ajustado na correção do ciclo (2026-10-11): antes a tela calculava "pronto" pela análise e podia
// discordar da fase (fase COLETANDO com botão de gravar; fase PROPOSTA_PRONTA sem botão; VERIFICANDO com
// botão "Medindo…" desabilitado para sempre). Agora a ação de cada fase vem do piloto (Kotlin) e a tela
// só a desenha: a análise sozinha nunca cria um botão.
test('uma ação principal por fase do piloto, decidida no Kotlin', () => {
  const m = model();
  const ready = analysis([4]);
  const pilot = (phase, action) => ({ autopilot: { phase, action } });
  const review = m.primaryAction(pilot('PROPOSTA_PRONTA', { kind: 'review', label: 'Revisar e gravar 1 ponto', enabled: true }), ready);
  assert.equal(review.kind, 'review');
  assert.equal(review.label, 'Revisar e gravar 1 ponto');
  assert.equal(review.enabled, true);
  const stable = m.primaryAction(pilot('ESTAVEL', { kind: 'stable', label: '✓ Estável · pode desconectar', enabled: false }), ready);
  assert.equal(stable.kind, 'stable');
  assert.equal(stable.enabled, false);
  const restore = m.primaryAction(pilot('RESTAURAR_TRECHO', { kind: 'restore', label: 'Restaurar 3 pontos que pioraram', enabled: true }), ready);
  assert.equal(restore.kind, 'restore');
  assert.match(restore.label, /^Restaurar 3 pontos que pioraram$/);
  // Durante o automático da ECU a proposta aparece desabilitada (a ECU sobrescreveria).
  const early = m.primaryAction(pilot('ECU_TRABALHANDO', { kind: 'waiting', label: 'Aguardando a ECU terminar o automático (2 de 3)', enabled: false }), ready);
  assert.equal(early.kind, 'waiting');
  assert.equal(early.enabled, false);
  assert.match(early.label, /2 de 3/);
  // Verificando: o OMEGAS observa sozinho, sem botão (antes: "Medindo…" desabilitado, às vezes para sempre).
  assert.equal(m.primaryAction(pilot('VERIFICANDO', { kind: 'none' }), ready).kind, 'none');
  // A tela nunca inventa "pronto": sem ação do Kotlin, nenhum botão, mesmo com a análise propondo.
  assert.equal(m.primaryAction({ autopilot: { phase: 'COLETANDO_NOSSOS' } }, ready).kind, 'none');
  assert.equal(m.primaryAction({ autopilot: { phase: 'PROPOSTA_PRONTA' } }, ready).kind, 'none');
  // ECU fora: a ação continua visível, mas desabilitada (erro de transporte não vira gravação).
  const offline = m.primaryAction(pilot('PROPOSTA_PRONTA', { kind: 'review', label: 'Conecte a ECU para gravar', enabled: false }), ready);
  assert.equal(offline.enabled, false);
});

test('Refino é um destino próprio logo abaixo do AutoCal e grava só com conferência e readback', () => {
  const index = read('app/src/main/assets/ui/index.html');
  const router = read('app/src/main/assets/ui/core/router.js');
  assert.ok(index.indexOf('data-route="autocal"') < index.indexOf('data-route="refino"'));
  assert.match(index, /data-screen="refino"/);
  assert.match(router, /screens\/refino\.js/);
  assert.match(SOURCE, /startCurveRead/);
  assert.match(SOURCE, /A Curva K da ECU mudou/);
  assert.match(SOURCE, /done\.state === 'BATCH_CONFIRMED' && done\.readbackValid === true/);
  assert.doesNotMatch(SOURCE, /setInterval/);
  // Mesma linguagem visual do AutoCal da Platina: mesmo gráfico e mesmas classes.
  assert.match(SOURCE, /autocal-reference-svg/);
  assert.match(SOURCE, /AutoCalUxModel/);
});

// ---------------------------------------------------------------- ciclo do refino (2026-10-11)

test('Desfazer some depois de usado: sem lista quando o Kotlin diz que não há o que desfazer', () => {
  const m = model();
  const before = Array(30).fill(16384);
  const bad = before.map((v, i) => (i === 10 ? 18000 : v));
  // Último registro é o próprio desfazer (antes = valor ruim): o Kotlin marca undoable=false e a lista some.
  assert.equal(m.undoPoints({ beforeRaw: bad, afterRaw: before, undoable: false }).length, 0);
  assert.equal(m.reviewList({ refinement: { latest: { undoable: false, undoPoints: [{ index: 10, currentRaw: 16384, targetRaw: 18000 }] } } }, 'undo').points.length, 0);
  // Proposta recém-gravada: o Kotlin dá a lista (atual ← anterior) e a revisão usa exatamente ela.
  const latest = { undoable: true, undoPoints: [{ index: 10, currentRaw: 18000, targetRaw: 16384, referenceTimeMs: 5.5 }] };
  const list = m.reviewList({ refinement: { latest } }, 'undo');
  assert.equal(list.points.length, 1);
  assert.equal(list.points[0].targetRaw, 16384);
});

test('ações a mais vêm do Kotlin: Revisar junto de Restaurar e Desfazer; nada de Entendi', () => {
  const m = model();
  assert.equal(m.secondaryActions({ autopilot: { secondary: [] } }).length, 0);
  const both = m.secondaryActions({ autopilot: { secondary: [
    { kind: 'review', label: 'Revisar e gravar 4 pontos', enabled: true },
    { kind: 'undo', label: 'Desfazer última gravação', experimentId: 'EXP-1' },
    { kind: 'ack', label: 'Entendi' },
    { kind: 'qualquer', label: 'ignorado' },
  ] } });
  assert.equal(JSON.stringify(both.map(s => s.kind)), JSON.stringify(['review', 'undo']));
  assert.equal(both[0].label, 'Revisar e gravar 4 pontos');
});

test('revisão nunca grava lista velha: mudou atualiza, sumiu não grava', () => {
  const m = model();
  const eqWith = points => ({ autopilot: { proposal: { known: true, count: points.length, points, signature: points.map(p => `${p.index}:${p.currentRaw}>${p.targetRaw}`).join(';') } } });
  const first = eqWith([{ index: 4, currentRaw: 16384, targetRaw: 17000 }]);
  const displayed = m.reviewList(first, 'apply');
  assert.equal(m.reviewGate(displayed, first), 'write');
  const changed = eqWith([{ index: 4, currentRaw: 16384, targetRaw: 17100 }]);
  assert.equal(m.reviewGate(displayed, changed), 'changed');
  assert.equal(m.reviewGate(displayed, eqWith([])), 'gone');
  // Restaurar também confere a lista no toque.
  const restore = m.reviewList({ restorePoints: [{ index: 7, currentRaw: 18000, targetRaw: 16384 }] }, 'restore');
  assert.equal(m.reviewGate(restore, { restorePoints: [{ index: 7, currentRaw: 18000, targetRaw: 16384 }] }), 'write');
  assert.equal(m.reviewGate(restore, { restorePoints: [] }), 'gone');
});

test('resultado por ponto nas palavras do dono: melhorou, piorou, ainda medindo', () => {
  const m = model();
  const result = { status: 'VERIFICANDO', points: [
    { index: 10, ms: 5.5, beforeRaw: 16384, afterRaw: 17000, verdict: 'CONFIRMADA', outcome: 'MELHOROU' },
    { index: 14, ms: 7.5, beforeRaw: 16384, afterRaw: 17000, verdict: 'PIOROU', outcome: 'PIOROU' },
    { index: 18, ms: 9.5, beforeRaw: 16384, afterRaw: 17000, verdict: 'COLETANDO', outcome: 'MEDINDO' },
  ] };
  assert.equal(JSON.stringify(m.resultRows(result).map(r => r.label)), JSON.stringify(['melhorou', 'piorou', 'ainda medindo']));
  assert.equal(JSON.stringify(m.resultSummary(result)), JSON.stringify({ improved: 1, worse: 1, measuring: 1, none: 0, total: 3 }));
});

test('motivos da gravação do Refino batem com a classificação do Kotlin (proposta, restaurar, desfazer)', () => {
  const m = model();
  const journal = read('app/src/main/java/com/omegas/prohub/autocal/RefinementJournal.kt');
  const prefix = journal.match(/REASON_PREFIX = "([^"]+)"/)?.[1];
  assert.equal(prefix, 'Refino OMEGAS:');
  assert.ok(m.REASONS.apply.startsWith(prefix) && !/^Refino OMEGAS:\s*(desfazer|restaurar)/i.test(m.REASONS.apply));
  assert.match(m.REASONS.restore, /^Refino OMEGAS:\s*restaurar/i);
  assert.match(m.REASONS.undo, /^Refino OMEGAS:\s*desfazer/i);
  assert.match(journal, /verb\.startsWith\("desfazer"\) -> KIND_DESFAZER/);
  assert.match(journal, /verb\.startsWith\("restaurar"\) -> KIND_RESTAURAR/);
});

test('barra única embaixo: botão do estado, Revisar, Desfazer e Detalhes; sem Abas nem Entendi; frase sob o título', () => {
  const bar = SOURCE.match(/<div class="refino-action-bar"[\s\S]*?<\/div>/)?.[0] || '';
  assert.ok(bar, 'a tela monta a barra de ações');
  const order = ['data-refino-primary', 'data-refino-review', 'data-refino-undo', 'data-refino-details'].map(attr => bar.indexOf(attr));
  assert.ok(order.every(i => i >= 0), 'todos os botões estão na barra');
  assert.equal(JSON.stringify([...order].sort((a, b) => a - b)), JSON.stringify(order), 'ordem: estado, Revisar, Desfazer, Detalhes');
  // Decisão do dono: a navegação inferior nunca some (sem botão Abas) e não existe "Entendi" no ciclo.
  assert.doesNotMatch(SOURCE, /toggleNav|data-refino-tabs|data-refino-ack|acknowledge/);
  assert.doesNotMatch(bar, /Entendi|Abas/);
  // A barra vem depois do gráfico e dos detalhes (embaixo); o cabeçalho não tem mais botões.
  assert.ok(SOURCE.indexOf('refino-action-bar') > SOURCE.indexOf('data-refino-panel="details"'));
  const header = SOURCE.match(/<header class="autocal-focus-toolbar">[\s\S]*?<\/header>/)?.[0] || '';
  assert.doesNotMatch(header, /<button/);
  assert.match(header, /<h3>Refino · GNV = Gasolina<\/h3>[\s\S]*<p id="refinoHeadline">/);
  // "Detalhes" troca o gráfico pelas seções no mesmo lugar (sem rolagem).
  assert.match(SOURCE, /data-refino-panel="chart"/);
  assert.match(SOURCE, /panel\.hidden = panel\.dataset\.refinoPanel !== this\.view/);
  // Botões de 56 px: todos usam a classe que o Refino já dimensiona (min-height 56px).
  const css = read('app/src/main/assets/ui/styles-autocal-cockpit.css');
  assert.match(css, /\.refino-cockpit \.autocal-primary-action \{ min-height: 56px;/);
  assert.equal((bar.match(/autocal-primary-action/g) || []).length, 4);
});

test('depois de gravar não há botão extra: o piloto já está medindo e a tela volta a ele', () => {
  assert.doesNotMatch(SOURCE, /phase: 'done'/);
  assert.match(SOURCE, /done\.state === 'BATCH_CONFIRMED' && done\.readbackValid === true\) \{[\s\S]{0,200}this\.operation = \{ phase: 'idle' \};\s*this\.refresh\(true\);/);
  // Falha vira estado legível com próxima ação ("Tentar de novo"), sem dar nada como gravado.
  assert.match(SOURCE, /Nada foi dado como gravado/);
  assert.match(SOURCE, /textContent = 'Tentar de novo'/);
});
