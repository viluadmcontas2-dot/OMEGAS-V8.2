const { test } = require('node:test');
const assert = require('node:assert/strict');
const D = require('../../app/src/main/assets/ui/core/diario.js');
const event = (extra = {}) => ({ leituras: 1, quem: 'REFINO', oque: 'SUGERIU', alvo: 'CURVA_K', ...extra });
test('modelo rejeita campos obrigatórios e preserva desconhecidos', () => {
  const d = D.create();
  for (const e of [{}, event({ leituras: undefined }), event({ quem: undefined }), event({ quem: 'outro' })]) assert.throws(() => d.append(e));
  const e = d.append(event());
  assert.equal(e.seq, 1); assert.equal(e.antes, null); assert.equal(e.desfecho.veredito, null);
  assert.deepEqual(e.regiao, { banda: null, coluna: null, ponto: null });
  e.regiao.banda = 8; assert.equal(d.list()[0].regiao.banda, null);
});
test('ordem, consulta, resumo e contagem monotônica', () => {
  const d = D.create(); const regiao = { banda: 0 };
  d.append(event({ regiao })); d.append(event({ leituras: 2, regiao, oque: 'DESFECHO', desfecho: { veredito: 'CONFIRMADA' } }));
  assert.deepEqual(d.list().map(e => e.seq), [1, 2]);
  assert.equal(d.query({ regiao, por: 'REFINO', tipo: 'SUGERIU' }).length, 1);
  assert.equal(d.resumo(regiao).ultimoDesfecho.seq, 2);
  assert.throws(() => d.append(event({ leituras: 0 })));
});
test('poda em 2000 preserva último desfecho por região e seq após recarga', () => {
  let value; const storage = { load: () => value, save: v => { value = v; } };
  const d = D.create({ storage });
  d.append(event({ oque: 'DESFECHO', regiao: { banda: 0 } }));
  for (let i = 0; i < 2001; i++) d.append(event());
  assert.equal(d.list().length, 2000); assert.equal(d.list()[0].seq, 1);
  assert.equal(D.create({ storage }).append(event()).seq, 2003);
});
test('persistência inválida ou indisponível não apaga dados e informa erro', () => {
  let saves = 0;
  const d = D.create({ storage: { load: () => ({ format: 'futuro' }), save: () => saves++ } });
  assert.ok(d.loadError); d.append(event()); assert.equal(saves, 0);
  const adapter = D.localStorageAdapter({ getItem() { throw Error('negado'); }, setItem() { throw Error('quota'); } });
  const m = D.create({ storage: adapter }); m.append(event()); assert.ok(m.loadError); assert.equal(m.list().length, 1);
  const broken = D.create({ storage: { load: () => null, save: () => { throw Error('quota'); } } });
  broken.append(event()); assert.ok(broken.saveError);
});
test('adaptador localStorage recarrega array versionado', () => {
  const data = {}; const storage = D.localStorageAdapter({ getItem: k => data[k], setItem: (k,v) => { data[k] = v; } });
  D.create({ storage }).append(event()); assert.equal(D.create({ storage }).list().length, 1);
});
