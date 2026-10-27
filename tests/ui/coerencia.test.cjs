const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const C = require('../../app/src/main/assets/ui/core/coerencia.js');
const r = { banda: 2, coluna: 4, ponto: 13 };
const e = (oque, leituras, antes = 100, depois = 103, extra = {}) => ({ seq: leituras + 1, oque, leituras, antes, depois, quem: 'REFINO', alvo: 'CURVA_K', regiao: r, ...extra });
const run = (events, context = {}) => C.avaliar(events, { leituras: 600, ...context });
const has = (result, rule) => result.avisos.some(a => a.regra === rule);
test('R1 alternância congela; mesma direção, outra região, passo curto e janela vencida não', () => {
  const events = [e('GRAVOU', 0), e('ECU_MUDOU', 100, 103, 100), e('GRAVOU', 200)];
  assert.equal(run(events).avisos.find(a => a.regra === 'R1').ate.leituras, 1700);
  assert.ok(!has(run([e('GRAVOU', 0), e('GRAVOU', 100), e('GRAVOU', 200)]), 'R1'));
  assert.ok(!has(run(events.map((v,i) => ({ ...v, regiao: { banda: i } }))), 'R1'));
  assert.ok(!has(run(events.map(v => ({ ...v, depois: v.antes + 1 }))), 'R1'));
  assert.ok(!has(run(events, { leituras: 2201 }), 'R1'));
});
test('R2 só AutoCal desfazendo Refino no mesmo ponto, passo >= metade, até 600 leituras', () => {
  const first = e('GRAVOU', 0);
  const second = e('ECU_MUDOU', 300, 103, 101, { quem: 'AUTOCAL_ECU' });
  assert.ok(has(run([first, second], { leituras: 300 }), 'R2'));
  for (const changed of [{ depois: 102.5 }, { leituras: 601 }, { regiao: { ponto: 12 } }, { quem: 'VOCE' }]) assert.ok(!has(run([first, { ...second, ...changed }], { leituras: 601 }), 'R2'));
  assert.ok(!has(run([{ ...first, quem: 'VOCE' }, second]), 'R2'));
  assert.ok(!has(run([first, second]), 'R2')); // 300 novas já medidas
});
test('R3 agrupa repetição; gravação zera e direção/janela diferente não contam', () => {
  const events = [0, 100, 200].map(n => e('SUGERIU', n));
  assert.equal(run(events).avisos.filter(a => a.regra === 'R3').length, 1);
  assert.ok(!has(run([events[0], e('GRAVOU', 50), ...events.slice(1)]), 'R3'));
  assert.ok(!has(run(events, { leituras: 1201 }), 'R3'));
  assert.ok(!has(run([events[0], events[1], e('SUGERIU', 200, 103, 100)]), 'R3'));
});
test('R4 mapa tratado aguarda três tropeços novos da mesma região', () => {
  const events = [e('GRAVOU', 100, 100, 105, { alvo: 'MAPA_K' })];
  const tropecos = [50, 101, 102].map(leituras => ({ regiao: r, leituras }));
  assert.ok(has(run(events, { tropecos }), 'R4'));
  assert.ok(!has(run(events, { tropecos: [...tropecos, { regiao: r, leituras: 103 }] }), 'R4'));
  assert.ok(!has(run([], { tropecos }), 'R4'));
});
test('R5 mantém caminho do localLatch, sem inventar caminho quando desconhecido', () => {
  const events = [e('SUGERIU', 0), e('SUGERIU', 100, 100, 103, { alvo: 'MAPA_K' })];
  for (const local of [true, false]) {
    const a = run(events, { localLatch: [{ regiao: r, local }] }).avisos.find(a => a.regra === 'R5');
    assert.equal(a.manter, local ? 'MAPA_K' : 'CURVA_K');
  }
  assert.ok(!has(run(events), 'R5'));
  assert.ok(!has(run(events, { leituras: 1101, localLatch: [{ regiao: r, local: true }] }), 'R5'));
});
test('R6 libera só com 1500 leituras E três regiões RPM×MAP distintas; LIBEROU encerra', () => {
  const events = [e('CONGELOU', 0)];
  const cobertura = [{ rpm: 1, map: 1 }, { rpm: 1, map: 2 }, { rpm: 2, map: 1 }].map(v => ({ ...v, leituras: 1400 }));
  assert.equal(run(events, { leituras: 1500, cobertura }).liberacoes[0].oque, 'LIBEROU');
  assert.equal(run(events, { leituras: 1499, cobertura }).liberacoes.length, 0);
  assert.equal(run(events, { leituras: 1500, cobertura: [cobertura[0], cobertura[0], cobertura[1]] }).liberacoes.length, 0);
  assert.equal(run([...events, e('LIBEROU', 1500)], { leituras: 1600, cobertura }).liberacoes.length, 0);
});
test('baseline sintético, determinismo, isolamento e UMD sem relógio/rede/ponte', () => {
  const events = [e('GRAVOU', 0), e('GRAVOU', 100), e('GRAVOU', 200)];
  const before = JSON.stringify(events);
  assert.deepEqual(run(events), { avisos: [], liberacoes: [] });
  assert.deepEqual(run(events), run(events)); assert.equal(JSON.stringify(events), before);
  const denied = () => { throw Error('efeito externo proibido'); };
  const sandbox = { Date: denied, performance: { now: denied }, fetch: denied, OmegasNative: new Proxy({}, { get: denied }) };
  vm.runInNewContext(fs.readFileSync(require.resolve('../../app/src/main/assets/ui/core/coerencia.js'), 'utf8'), sandbox);
  assert.equal(sandbox.OmegasCoerencia.avaliar(events, { leituras: 600 }).avisos.length, 0);
});
