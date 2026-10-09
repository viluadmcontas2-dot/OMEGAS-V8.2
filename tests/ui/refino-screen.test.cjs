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

test('revisão propõe só pontos medidos que mudam; mantidos nunca são gravados', () => {
  const m = model();
  const points = m.proposedPoints(analysis([4, 5, 6]));
  assert.equal(points.length, 3);
  assert.equal(JSON.stringify(points[0]), JSON.stringify({ index: 4, currentRaw: 16384, targetRaw: 17000 }));
  const held = analysis([]);
  held.points[2] = { ...held.points[2], calculatedRaw: 17000, origin: 'HELD' };
  assert.equal(m.proposedPoints(held).length, 0);
});

test('o botão grava a proposta do cérebro quando a ação a traz (local ou curva inteira); sem ela, a análise do snapshot', () => {
  const m = model();
  const before = Array.from({ length: 30 }, () => 16384);
  const after = before.map((v, i) => ([4, 9].includes(i) ? v + 300 : v));
  const whole = { kind: 'APPLY', text: '2 pontos pobres · Aplicar ajuste', currentRaw: before, refinedRaw: after, pointIndexes: [4, 9, 11] };
  // 11 está na lista mas não muda: não entra; a análise (que proporia 20 e 21) é ignorada.
  const other = analysis([20, 21]);
  const expected = [{ index: 4, currentRaw: 16384, targetRaw: 16684 }, { index: 9, currentRaw: 16384, targetRaw: 16684 }];
  assert.equal(JSON.stringify(m.brainPoints(whole)), JSON.stringify(expected));
  assert.equal(JSON.stringify(m.readyPoints({ nextAction: whole }, other)), JSON.stringify(expected));
  // A proposta do cérebro vale mesmo com a análise do snapshot indisponível.
  const eq = { autopilot: { phase: 'PROPOSTA_PRONTA' }, nextAction: whole, refinoState: { canAct: true } };
  assert.equal(m.primaryAction(eq, { available: false }).kind, 'review');
  assert.equal(m.primaryAction(eq, null).label, 'Gravar 2 pontos');
  // Sem curva na ação (ponte antiga): a análise continua sendo a fonte.
  assert.equal(m.brainPoints({ kind: 'APPLY', text: 'x', pointIndexes: [1] }), null);
  assert.equal(m.readyPoints({ nextAction: { kind: 'APPLY', pointIndexes: [1] } }, other).length, 2);
  // Ação local sem curva válida não grava nada; COLLECT nunca traz curva.
  assert.equal(m.readyPoints({ nextAction: { kind: 'APPLY', local: true, pointIndexes: [1] } }, other).length, 0);
  assert.equal(m.brainPoints({ kind: 'COLLECT', currentRaw: before, refinedRaw: after, pointIndexes: [4] }), null);
  // Curva malformada (29 valores, raw zero) é recusada.
  assert.equal(m.brainPoints({ ...whole, refinedRaw: after.slice(1) }), null);
  assert.equal(m.brainPoints({ ...whole, currentRaw: before.map((v, i) => (i ? v : 0)) }), null);
});

test('Desfazer some depois de um Desfazer e sobrevive a Mapa K gravado; só AutoMatch/curva externa o invalidam', () => {
  const m = model();
  const before = Array.from({ length: 30 }, () => 16384), after = before.map((v, i) => (i === 3 ? v + 200 : v));
  const base = { status: 'VERIFICADO', photoFile: 'foto.json', beforeRaw: before, afterRaw: after, appliedAt: 1000, source: 'KF-123' };
  assert.equal(m.undoSource(base, 2000).available, true);
  assert.equal(m.undoSource({ ...base, restore: true }, 2000).available, false, 'depois de desfazer não há o que desfazer');
  assert.equal(m.undoSource({ ...base, status: 'INTERROMPIDO', interruptReason: 'MAPA_K_GRAVADO' }, 2000).available, true, 'Mapa K não muda a Curva K');
  assert.equal(m.undoSource({ ...base, status: 'INTERROMPIDO', interruptReason: 'AUTOMATCH_NATIVO' }, 2000).available, false);
  assert.equal(m.undoSource({ ...base, status: 'INTERROMPIDO', interruptReason: 'CURVA_K_MUDOU_FORA_DO_APP' }, 2000).available, false);
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

test('uma ação principal por fase do piloto', () => {
  const m = model();
  const ready = analysis([4]);
  assert.equal(m.primaryAction({ autopilot: { phase: 'PROPOSTA_PRONTA' } }, ready).kind, 'review');
  assert.equal(m.primaryAction({ autopilot: { phase: 'ESTAVEL' } }, ready).kind, 'stable');
  assert.equal(m.primaryAction({ autopilot: { phase: 'RESTAURAR_TRECHO' }, restorePoints: [{ index: 1 }] }, ready).kind, 'restore');
  // Durante o automático da ECU o refino calcula, mas não oferece gravar (a ECU sobrescreveria).
  const early = m.primaryAction({ autopilot: { phase: 'ECU_TRABALHANDO', autoMatchCount: 2, maxAutomatch: 3 } }, ready);
  assert.equal(early.kind, 'waiting');
  assert.match(early.label, /2 de 3/);
  assert.equal(m.primaryAction({ autopilot: { phase: 'VERIFICANDO' } }, ready).kind, 'waiting');
  assert.equal(m.primaryAction({ autopilot: { phase: 'COLETANDO_NOSSOS' } }, ready).kind, 'review');
  assert.equal(m.primaryAction({ autopilot: { phase: 'COLETANDO_NOSSOS' } }, analysis([])).kind, 'none');
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
  // Mesma linguagem visual do AutoCal: o MESMO componente de gráfico (components/curve-chart.js), montado nos dois quadros.
  const chart = read('app/src/main/assets/ui/components/curve-chart.js');
  assert.match(chart, /autocal-reference-svg/);
  assert.match(SOURCE, /chart\.mount\(host/);
  assert.match(read('app/src/main/assets/ui/screens/autocal-cockpit.js'), /chart\.mount\(host/);
});

// ---------------------------------------------------------------- UX: o ponto tocado e o histórico

test('idade do dado: sem data conhecida é "—", nunca "há 0 s"', () => {
  const m = model();
  const now = 1_790_000_000_000;
  for (const unknown of [null, undefined, 0, NaN, 'x']) assert.equal(m.ageText(unknown, now), '—');
  assert.equal(m.ageText(now - 1_000, now), 'agora');
  assert.equal(m.ageText(now - 12_000, now), 'há 12 s');
  assert.equal(m.ageText(now - 180_000, now), 'há 3 min');
  assert.equal(m.ageText(now - 7_200_000, now), 'há 2 h');
});

test('ponto nosso na marcha lenta conta apenas na curva propria da lenta', () => {
  const m = model();
  const now = 1_790_000_000_000;
  const idle = m.explainPoint('our', { fuel: 'GAS', mapBar: 0.54, tpetMs: 4.5, samples: 30, rpmMedian: 872, idleShare: 1, lastAtMs: now - 8_000 }, { now });
  assert.equal(idle.counts, true);
  assert.match(idle.title, /Medido pelo OMEGAS · GNV/);
  assert.match(idle.lines.join('\n'), /medido pelo OMEGAS na sua condução/);
  assert.match(idle.lines.join('\n'), /há 8 s/);
  assert.match(idle.lines.join('\n'), /curva própria da lenta/);
  assert.match(idle.lines.join('\n'), /nunca é misturada com condução/);
  const driving = m.explainPoint('our', { fuel: 'GAS', mapBar: 0.7, tpetMs: 8, samples: 12, rpmMedian: 2800, idleShare: 0, lastAtMs: now - 120_000 }, { now });
  assert.equal(driving.counts, true);
  assert.match(driving.lines.join('\n'), /forma par com a gasolina/);
  assert.match(driving.lines.join('\n'), /há 2 min/);
  const petrol = m.explainPoint('our', { fuel: 'PETROL', mapBar: 0.7, tpetMs: 8, samples: 12, rpmMedian: 2800, idleShare: 0 }, { now });
  assert.match(petrol.lines.join('\n'), /referência da gasolina/);
  assert.match(petrol.lines.join('\n'), /Quando: última leitura —/);
});

test('ponto da ECU: de quem é, quando, e se foi descartado como anomalia', () => {
  const m = model();
  const now = 1_790_000_000_000;
  const point = { fuel: 'GAS', fuelLabel: 'GNV', index: 6, point: 7, mapBar: 0.5, petrolMs: 4.4, counter: 10, threshold: 10, acquisitionState: 'ACQUIRED' };
  const ok = m.explainPoint('ecu', point, { now, capturedAtMs: now - 3_000, rejected: [] });
  assert.equal(ok.counts, true);
  assert.match(ok.lines.join('\n'), /medido pela ECU \(AutoCal da ECU\)/);
  assert.match(ok.lines.join('\n'), /faixa lida pela ECU/);
  const rejected = m.explainPoint('ecu', point, { now, capturedAtMs: now - 3_000, rejected: [{ fuel: 'GNV', band: 6 }] });
  assert.equal(rejected.counts, false);
  assert.match(rejected.lines.join('\n'), /NÃO conta[\s\S]*anomalia/);
  const otherFuel = m.explainPoint('ecu', point, { now, rejected: [{ fuel: 'GASOLINA', band: 6 }] });
  assert.equal(otherFuel.counts, true, 'a faixa 7 da gasolina descartada não descarta a do GNV');
  const collecting = m.explainPoint('ecu', { ...point, acquisitionState: 'COLLECTING', counter: 4 }, { now });
  assert.equal(collecting.counts, false);
  assert.match(collecting.lines.join('\n'), /ainda lendo/);
  assert.match(collecting.lines.join('\n'), /\(4\/10\)/);
});

test('histórico: todo estado de fechamento do diário tem palavras simples', () => {
  const m = model();
  for (const status of ['VERIFICADO', 'PIOROU_EM_PARTE', 'VERIFICANDO', 'SEM_BASE', 'INCONCLUSIVO', 'INTERROMPIDO']) {
    assert.ok(m.STATUS_WORDS[status] && m.STATUS_WORDS[status].length >= 7, status);
    assert.doesNotMatch(m.STATUS_WORDS[status], /[A-Z_]{6,}/, `${status} não pode mostrar o código ao motorista`);
  }
});

test('a legenda fica ACIMA do gráfico, fora do bloco que rola junto', () => {
  const source = SOURCE;
  assert.ok(source.indexOf('id="refinoLegend"') > 0);
  assert.ok(source.indexOf('id="refinoLegend"') < source.indexOf('id="refinoChart"'), 'legenda antes do gráfico');
  assert.equal([...source.matchAll(/class="ar-legend"/g)].length, 1, 'uma legenda só');
});

test('LENDO_ECU não oferece ação nem afirma estado: a ECU ainda não entregou nada', () => {
  const m = model();
  const action = m.primaryAction({ autopilot: { phase: 'LENDO_ECU' } }, null);
  assert.equal(action.kind, 'none');
  assert.equal(action.label, '');
});
