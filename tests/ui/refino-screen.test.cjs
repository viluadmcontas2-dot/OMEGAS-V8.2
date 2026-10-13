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
  // Dono (2026-10-13): o gráfico do Refino é O MESMO objeto do AutoCal (autocal-original.js), sem o painel de aquisição,
  // sempre visível (sem botão "Ver o gráfico") e sem cartão de engasgo próprio (a correção fica no Refino local).
  assert.match(SOURCE, /O\.build\(/);
  assert.match(SOURCE, /O\.renderMain\(/);
  assert.match(SOURCE, /O\.renderK\(/);
  assert.doesNotMatch(SOURCE, /renderAcquisition/);
  assert.doesNotMatch(SOURCE, /refinoGraphToggle|refinoStalls|data-refino-stall|refinoInspector/);
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

// Ajustado na rodada 2 (revisão, itens 13 e 14): o portão conferia a assinatura exata (com o alvo do motor),
// que muda em 18–20 de cada 20 ticks rodando, e recusava "Gravar na ECU" em série; e não olhava se a ação
// ainda estava liberada. Agora confere elegibilidade (mesmos pontos, mesmo valor atual, nada retido) e grava a
// lista revisada; com a ação desligada (ECU no automático, sem cabo) não grava.
test('revisão nunca grava lista velha: mesmos pontos gravam o revisado, ponto mudou atualiza, sumiu não grava', () => {
  const m = model();
  const review = { kind: 'review', label: 'Revisar e gravar 1 ponto', enabled: true };
  const eqWith = (points, extra = {}) => ({ autopilot: { action: review, proposal: { known: true, count: points.length, points, held: [], signature: points.map(p => `${p.index}:${p.currentRaw}>${p.targetRaw}`).join(';'), ...extra } } });
  const first = eqWith([{ index: 4, currentRaw: 16384, targetRaw: 17000 }]);
  const displayed = m.reviewList(first, 'apply');
  assert.equal(m.reviewGate(displayed, first), 'write');
  // Alvo do motor andou com leituras novas: mesmos pontos, mesmo valor atual → grava a lista revisada.
  assert.equal(m.reviewGate(displayed, eqWith([{ index: 4, currentRaw: 16384, targetRaw: 17100 }])), 'write');
  // Valor atual diferente ou outro conjunto de pontos → atualiza e avisa.
  assert.equal(m.reviewGate(displayed, eqWith([{ index: 4, currentRaw: 16500, targetRaw: 17100 }])), 'changed');
  assert.equal(m.reviewGate(displayed, eqWith([{ index: 4, currentRaw: 16384, targetRaw: 17000 }, { index: 5, currentRaw: 16384, targetRaw: 17000 }])), 'changed');
  assert.equal(m.reviewGate(displayed, eqWith([])), 'gone');
  // Ponto retido agora (em medição) não grava.
  assert.equal(m.reviewGate(displayed, eqWith([{ index: 4, currentRaw: 16384, targetRaw: 17000 }], { held: [{ index: 4, reason: 'MEDINDO' }] })), 'changed');
  // Ação desligada (ECU voltou ao automático / sem cabo): o cartão aberto não grava.
  const waiting = eqWith([{ index: 4, currentRaw: 16384, targetRaw: 17000 }]);
  waiting.autopilot.action = { kind: 'waiting', label: 'Aguardando a ECU terminar o automático', enabled: false };
  assert.equal(m.reviewGate(displayed, waiting), 'blocked');
  const offline = eqWith([{ index: 4, currentRaw: 16384, targetRaw: 17000 }]);
  offline.autopilot.action = { ...review, enabled: false, label: 'Conecte a ECU para gravar' };
  assert.equal(m.reviewGate(displayed, offline), 'blocked');
  // Revisar ao lado de Restaurar (secundária) também libera.
  const beside = eqWith([{ index: 4, currentRaw: 16384, targetRaw: 17000 }]);
  beside.autopilot.action = { kind: 'restore', label: 'Restaurar 2 pontos que pioraram', enabled: true };
  beside.autopilot.secondary = [{ kind: 'review', label: 'Revisar e gravar 1 ponto', enabled: true }];
  assert.equal(m.reviewGate(displayed, beside), 'write');
  // Restaurar e Desfazer também conferem a lista e a ação no toque.
  const restoreAction = { autopilot: { action: { kind: 'restore', label: 'Restaurar 1 ponto que piorou', enabled: true } } };
  const restore = m.reviewList({ ...restoreAction, restorePoints: [{ index: 7, currentRaw: 18000, targetRaw: 16384 }] }, 'restore');
  assert.equal(m.reviewGate(restore, { ...restoreAction, restorePoints: [{ index: 7, currentRaw: 18000, targetRaw: 16384 }] }), 'write');
  assert.equal(m.reviewGate(restore, { ...restoreAction, restorePoints: [] }), 'gone');
  assert.equal(m.reviewGate(restore, { autopilot: { action: { kind: 'review', enabled: true } }, restorePoints: [{ index: 7, currentRaw: 18000, targetRaw: 16384 }] }), 'blocked');
  const undoLatest = { refinement: { latest: { undoable: true, undoPoints: [{ index: 10, currentRaw: 18000, targetRaw: 16384 }] } } };
  const undo = m.reviewList(undoLatest, 'undo');
  assert.equal(m.reviewGate(undo, { ...undoLatest, autopilot: { secondary: [{ kind: 'undo', enabled: true }] } }), 'write');
  assert.equal(m.reviewGate(undo, { ...undoLatest, autopilot: { secondary: [{ kind: 'undo', enabled: false }] } }), 'blocked');
});

test('pontos da ECU desconhecidos mostram —, nunca Gas 0 · GNV 0 (rodada 2, item 21)', () => {
  const m = model();
  assert.equal(m.ecuPointsText({ petrolValid: null, gasValid: null }), '—');
  assert.equal(m.ecuPointsText({}), '—');
  assert.equal(m.ecuPointsText({ petrolValid: 12, gasValid: null }), '—');
  assert.equal(m.ecuPointsText({ petrolValid: 12, gasValid: 0 }), 'Gasolina 12 · GNV 0'); // (dono, 2026-10-11: texto para leigo): "Gas" se lê gás
});

test('nossos pontos desconhecidos mostram —, nunca Gasolina 0 · GNV 0 (dono, 2026-10-11)', () => {
  const m = model();
  assert.equal(m.ourPointsText(undefined), '—');
  assert.equal(m.ourPointsText({}), '—');
  assert.equal(m.ourPointsText({ petrol: [1, 2] }), '—', 'sem a lista do GNV não se inventa 0');
  assert.equal(m.ourPointsText({ petrol: [1, 2, 3], gas: [] }), 'Gasolina 3 · GNV 0', 'lista vazia medida é 0 de verdade');
  assert.doesNotMatch(SOURCE, /`Gas \$\{/, 'sem "Gas" no lugar de gasolina');
  // Refino reformulado (dono, 2026-10-13): sem fases 1–5 nem caixas de números; só frase, ação e botões.
  assert.doesNotMatch(SOURCE, /refino-steps|refinoSteps|<small>GNV vs gasolina<\/small>/);
});

test('a tela não pede a análise do motor (a ponte não recalcula; rodada 2, item 19)', () => {
  assert.doesNotMatch(SOURCE, /refinedAnalysis/);
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

test('barra única embaixo: Gravar na Curva K, Gravar no Mapa K e Desfazer; sem Detalhes nem Abas; nenhum botão "Aguardando"', () => {
  const bar = SOURCE.match(/<div class="refino-action-bar"[\s\S]*?<\/div>/)?.[0] || '';
  assert.ok(bar, 'a tela monta a barra de ações');
  // Dono, 2026-10-13: "um botão para gravar na curva e um botão para gravar no mapa; as interações precisam ser claras".
  const order = ['data-refino-curve', 'data-refino-map', 'data-refino-undo'].map(attr => bar.indexOf(attr));
  assert.ok(order.every(i => i >= 0), 'todos os botões estão na barra');
  assert.equal(JSON.stringify([...order].sort((a, b) => a - b)), JSON.stringify(order), 'ordem: Curva K, Mapa K, Desfazer');
  assert.match(bar, />Gravar na Curva K</);
  assert.match(bar, />Gravar no Mapa K</);
  assert.doesNotMatch(bar, /Aguardando|Revisar e gravar|Detalhes|Abas/);
  assert.doesNotMatch(SOURCE, /toggleNav|data-refino-tabs|data-refino-ack|acknowledge/);
  assert.doesNotMatch(SOURCE, /data-refino-details|data-refino-panel="details"|toggleDetails/);
  // Nada de botão desabilitado: sem ação possível o botão some e a frase explica.
  assert.doesNotMatch(SOURCE, /node\.disabled|button\.disabled|\.disabled = /);
  const header = SOURCE.match(/<header class="autocal-focus-toolbar">[\s\S]*?<\/header>/)?.[0] || '';
  assert.doesNotMatch(header, /<button/);
  assert.match(header, /<h3>Refino · GNV = Gasolina<\/h3>[\s\S]*<p id="refinoHeadline">/);
  const css = read('app/src/main/assets/ui/styles-autocal-cockpit.css');
  assert.match(css, /\.refino-cockpit \.autocal-primary-action \{ min-height: 56px;/);
});

test('depois de gravar não há botão extra: o piloto já está medindo e a tela volta a ele', () => {
  assert.doesNotMatch(SOURCE, /phase: 'done'/);
  assert.match(SOURCE, /done\.state === 'BATCH_CONFIRMED' && done\.readbackValid === true\) \{[\s\S]{0,200}this\.operation = \{ phase: 'idle' \};\s*this\.refresh\(true\);/);
  // Falha vira estado legível com próxima ação ("Tentar de novo"), sem dar nada como gravado.
  assert.match(SOURCE, /Nada foi dado como gravado/);
  assert.match(SOURCE, /label\.ok = 'Tentar de novo'/);
});

// ---------------------------------------------------- ajuste local no Mapa K (dono, 2026-10-11: a curva aprende tudo)

const LOCAL_CELLS = [{ row: 3, column: 0, deltaPercent: 15 }, { row: 4, column: 0, deltaPercent: 15 }, { row: 5, column: 0, deltaPercent: 15 }];
const localEq = (extra = {}) => ({ autopilot: { phase: 'ESTAVEL', action: { kind: 'stable', enabled: false }, secondary: [
  { kind: 'local', enabled: true, label: 'Revisar ajuste local no Mapa K (3 células)', cells: LOCAL_CELLS, ...extra },
] } });

test('botão do ajuste local só existe com a ação local do Kotlin (com células)', () => {
  const m = model();
  assert.equal(m.localAction({ autopilot: { secondary: [] } }).kind, 'none');
  assert.equal(m.localAction({}).kind, 'none');
  assert.equal(m.localAction(localEq({ cells: [] })).kind, 'none');
  // A análise da curva nunca cria o botão local: só o piloto.
  assert.equal(m.localAction({ autopilot: { proposal: { points: [{ index: 4 }] }, secondary: [{ kind: 'review', enabled: true }] } }).kind, 'none');
  const local = m.localAction(localEq());
  assert.equal(local.kind, 'local');
  assert.equal(local.label, 'Revisar ajuste local no Mapa K (3 células)');
  assert.equal(local.enabled, true);
  assert.equal(JSON.stringify(local.cells), JSON.stringify(LOCAL_CELLS));
  assert.equal(m.localAction(localEq({ enabled: false })).enabled, false);
  // Célula sem número (desconhecido) nunca vira 0: fica fora.
  assert.equal(m.localAction(localEq({ cells: [...LOCAL_CELLS, { row: 6, column: 0, deltaPercent: null }] })).cells.length, 3);
  // Ação local não libera gravar a curva.
  assert.equal(m.actionEnabled(localEq(), 'apply'), false);
});

test('o Mapa K recebe UMA proposta por vez: engasgo primeiro, depois a diferença por RPM; sem nenhuma, sem botão', () => {
  const window = { setTimeout: () => 0 };
  window.window = window;
  vm.runInNewContext(SOURCE, { window, globalThis: window, console });
  const proto = window.OmegasUi.RefinoScreen.prototype;
  const stalls = { count: 1, events: [{ kind: 'STALL', petrolMs: 2.2, mapBar: 0.3 }], regions: [{ fromMs: 2.0, toMs: 2.5, count: 1, rpmLow: 1400, rpmHigh: 1600 }], nearCount: 0 };
  window.OmegasStallRegion = { suggest: () => ({ ok: true, cells: [{ row: 0, column: 1 }], percent: 5, region: { fromMs: 2, toMs: 2.5 } }), topRegion: () => ({ fromMs: 2 }) };
  const both = proto.mapProposal.call({ eq: { ...localEq(), stalls } });
  assert.equal(both.sub, 'reforço do engasgo');
  const plan = both.plan({ axes: { petrolBins: [], rpmBins: [] } });
  assert.equal(plan.ok, true);
  assert.equal(JSON.stringify(plan.cells), JSON.stringify([{ row: 0, column: 1, deltaPercent: 5 }]));
  assert.match(plan.summary(1), /Reforço do GNV em 1 célula do Mapa K \(\+5%\)/);
  const onlyLocal = proto.mapProposal.call({ eq: localEq() });
  assert.equal(onlyLocal.sub, 'diferença por RPM');
  assert.equal(JSON.stringify(onlyLocal.plan().cells), JSON.stringify(LOCAL_CELLS));
  assert.equal(proto.mapProposal.call({ eq: localEq({ enabled: false }) }), null, 'ação desabilitada não vira botão');
  assert.equal(proto.mapProposal.call({ eq: {} }), null);
  // O Refino não grava o Mapa K por conta própria: só pelo fluxo com toque em "Gravar" (core/map-fix.js).
  assert.doesNotMatch(SOURCE, /writeMap\(|startMapBatchWrite/);
});
