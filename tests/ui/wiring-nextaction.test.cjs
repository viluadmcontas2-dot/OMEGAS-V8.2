'use strict';
// Próxima ação do Refino: TODO tipo de NextActionKind leva a algo que existe e que dá para fazer ali (sem beco sem saída).
// Classe 2 (ponte falsa que grava as chamadas). A faixa não grava a ECU sozinha: congelar a Referência é um toque (não escreve na ECU).
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const L = require('./wiring/lib.cjs');
const { assert } = L;
const W = require('./wiring/world.cjs');

const ROOT = path.resolve(__dirname, '..', '..');
const ENGINE = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/omegas/prohub/equivalence/EquivalenceEngine.kt'), 'utf8');
const TYPES = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/omegas/prohub/equivalence/EquivalenceTypes.kt'), 'utf8');
const ROUTER = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/core/router.js'), 'utf8');
const ROUTES = [...ROUTER.match(/const ROUTES = \[([^\]]*)\]/)[1].matchAll(/'(\w+)'/g)].map(m => m[1]);
const SUBPAGES = { curve: ['overview', 'editor'] };
const KINDS = TYPES.match(/enum class NextActionKind \{([^}]*)\}/)[1].split(',').map(s => s.trim()).filter(Boolean);

test('Kotlin: toda rota/subpágina emitida pelo cérebro existe no roteador', () => {
  const emitted = [...ENGINE.matchAll(/NextAction\(\s*NextActionKind\.(\w+),[^\n]*?(?:"(\w+)"|null), (?:"(\w+)"|null)/g)];
  assert.ok(emitted.length >= 5, 'achou as emissões de NextAction com rota');
  for (const m of emitted) {
    const [, kind, route, subpage] = m;
    if (route) assert.ok(ROUTES.includes(route), `${kind}: rota ${route} existe`);
    if (subpage) assert.ok((SUBPAGES[route] || []).includes(subpage), `${kind}: subpágina ${route}/${subpage} existe`);
  }
  assert.doesNotMatch(ENGINE, /"equivalencia"|"referencia"|"pontos"/, 'nenhuma subpágina fantasma');
});

function app(kind, { phase = 'PROPOSTA_PRONTA', previousId = null, photo = true } = {}) {
  const w = new W.World();
  w.setFrame(W.realFrames('automatch_', f => f.fuel === 'GNV' && f.petrol_ms > 2)[10]);
  const eq = W.equivalenceFor(phase, photo ? {} : { latest: null });
  const own = ['OPERATION', 'NOTHING'].includes(kind) ? null : 'refino';
  eq.nextAction = { kind, text: `texto ${kind}`, route: own, subpage: null, pointIndexes: [8, 9, 10] };
  eq.reference = { frozen: kind !== 'FREEZE_REFERENCE', canFreeze: true, previousId };
  w.equivalence = eq;
  w.refined = W.refinedAnalysis(kind === 'APPLY', w.curve);
  w.projection = W.realProjection('automatch_', 11);
  const a = L.boot({ world: w });
  a.go('refino');
  a.settle(4);
  return a;
}
const go = a => a.$('[data-refino-primary]');
const undoBtn = a => a.$('[data-refino-undo]');
const shown = el => !!el && !el.hasAttribute('hidden');

test('FREEZE_REFERENCE: um toque congela (sem diálogo), mostra o resultado, e a ECU não recebe escrita', () => {
  const a = app('FREEZE_REFERENCE');
  assert.equal(a.byId('refinoHeadline').textContent, 'Salvar a curva atual da ECU como referência');
  assert.match(a.byId('refinoNext').textContent, /régua para comparar o GNV com a gasolina/);
  assert.ok(shown(go(a)), 'o botão existe e não está escondido');
  assert.equal(go(a).hasAttribute('disabled'), false, 'o botão não está congelado');
  const mark = a.world.mark();
  go(a).click(); a.flush();
  assert.equal(a.world.since(mark).filter(c => c.method === 'freezeReference').length, 1);
  assert.deepEqual(L.actionCalls(a, mark).filter(m => /Write|Reset|Restore/.test(m)), [], 'congelar não escreve na ECU');
  assert.match(a.byId('refinoHeadline').textContent, /Referência congelada\./);
});

test('FREEZE_REFERENCE: Desfazer só com referência anterior (senão falharia); depois de congelar, volta à anterior', () => {
  const b = app('FREEZE_REFERENCE', { previousId: 'REF-1' });
  assert.equal(b.byId('refinoEqUnfreeze').hasAttribute('hidden'), true, 'sem ter congelado nesta sessão não há o que desfazer');
  const mark = b.world.mark();
  go(b).click(); b.flush();
  assert.ok(shown(b.byId('refinoEqUnfreeze')), 'depois de congelar, Desfazer fica à vista');
  b.byId('refinoEqUnfreeze').click(); b.flush();
  assert.equal(b.world.since(mark).filter(c => c.method === 'restorePreviousReference').length, 1);
  const c = app('FREEZE_REFERENCE');
  go(c).click(); c.flush();
  assert.equal(c.byId('refinoEqUnfreeze').hasAttribute('hidden'), true, 'primeira referência: sem anterior, não oferece um Desfazer que falharia');
});

test('APPLY: o botão da faixa grava em UM toque (sem modal): lê, confere e grava', () => {
  const a = app('APPLY');
  assert.ok(shown(go(a)), 'botão presente');
  assert.equal(go(a).textContent.trim(), 'Aplicar ajuste', 'botão = verbo do efeito');
  assert.equal(a.byId('refinoReview'), null, 'não existe modal de revisão');
  assert.doesNotMatch(a.byId('refinoNext').textContent, /mudança média|\d+ pontos/, 'sem regras internas');
  const mark = a.world.mark();
  go(a).click(); a.flush();
  assert.deepEqual(L.actionCalls(a, mark), ['startCurveRead'], 'um toque já inicia a leitura de conferência/gravação')
});

test('APPLY sem proposta liberada (ECU no automático): sem botão morto na faixa', () => {
  const a = app('APPLY', { phase: 'ECU_TRABALHANDO' });
  assert.equal(go(a).hasAttribute('hidden'), true);
});

test('CONTESTED: o botão da faixa abre o Desfazer da gravação (há foto); sem foto, não há botão morto', () => {
  const a = app('CONTESTED', { phase: 'VERIFICANDO' });
  assert.ok(undoBtn(a) && !undoBtn(a).closest('[hidden]'), 'Desfazer discreto à vista');
  assert.equal(undoBtn(a).textContent.trim(), 'Desfazer');
  const mark = a.world.mark();
  undoBtn(a).click(); a.flush();
  assert.ok(a.world.since(mark).some(c => c.method === 'startCurveRestorePrepare'), 'leva ao fluxo de Desfazer');
  const b = app('CONTESTED', { phase: 'VERIFICANDO', photo: false });
  assert.ok(!undoBtn(b) || undoBtn(b).closest('[hidden]'), 'sem foto, sem botão morto');
});

for (const kind of ['OPERATION', 'PROVING', 'COLLECT', 'NOTHING']) {
  test(`${kind}: só informa (o Refino já mostra o que fazer) e não tem botão que leve a lugar nenhum`, () => {
    const a = app(kind);
    assert.equal(a.byId('refinoHeadline').textContent, `texto ${kind}`);
    assert.equal(go(a).hasAttribute('hidden'), true);
  });
}

test('todos os NextActionKind do Kotlin foram cobertos por este teste', () => {
  assert.deepEqual(KINDS.slice().sort(), ['APPLY', 'COLLECT', 'CONTESTED', 'FREEZE_REFERENCE', 'NOTHING', 'OPERATION', 'PROVING']);
});
