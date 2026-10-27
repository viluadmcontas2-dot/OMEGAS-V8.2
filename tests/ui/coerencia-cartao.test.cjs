const { test } = require('node:test');
const assert = require('node:assert/strict');
const D = require('../../app/src/main/assets/ui/core/diario.js');
const Card = require('../../app/src/main/assets/ui/components/coerencia-cartao.js');
const regiao = { banda: 2, coluna: 4, ponto: 13 };
const sug = (leituras, depois) => ({ leituras, quem: 'REFINO', oque: 'SUGERIU', alvo: 'CURVA_K', regiao, antes: 100, depois });

test('ponte do diário: usa load/save nativos e cai para memória sem ponte', () => {
  const saved = [];
  const v7 = { loadDiario: () => '', saveDiario: s => { saved.push(JSON.parse(s)); return '{"ok":true}'; } };
  const d = D.create({ storage: D.bridgeAdapter(v7) });
  d.append(sug(1, 103));
  assert.equal(saved.length, 1); assert.equal(saved[0].format, D.FORMAT); assert.equal(saved[0].eventos.length, 1);
  assert.equal(D.bridgeAdapter({}), null);
  assert.equal(D.bridgeAdapter(null), null);
  assert.equal(D.create({ storage: D.bridgeAdapter({}) }).append(sug(1, 103)).seq, 1);
  const again = D.create({ storage: D.bridgeAdapter({ loadDiario: () => JSON.stringify(saved[0]), saveDiario: () => '{}' }) });
  assert.equal(again.list().length, 1);
});

test('ponte com falha de gravação não derruba o diário', () => {
  const d = D.create({ storage: D.bridgeAdapter({ loadDiario: () => '', saveDiario: () => { throw Error('x'); } }) });
  d.append(sug(1, 103));
  assert.ok(d.saveError);
});

test('registrar: gravação do histórico, sugestão do Refino e curva do AutoCal viram eventos', () => {
  const d = D.create();
  Card.registrar(d, 'gravacao', { by: 'você', diario: { alvo: 'MAPA_K', regiao, antes: 100, depois: 104 } }, 10);
  Card.registrar(d, 'sugestao', { alvo: 'CURVA_K', regiao, antes: 100, depois: 103 }, 11);
  Card.registrar(d, 'autocal', { alvo: 'CURVA_K', regiao, antes: 103, depois: 100 }, 12);
  const l = d.list();
  assert.deepEqual(l.map(e => [e.quem, e.oque]), [['VOCE', 'GRAVOU'], ['REFINO', 'SUGERIU'], ['AUTOCAL_ECU', 'ECU_MUDOU']]);
  assert.equal(Card.registrar(d, 'gravacao', { by: 'você' }, 13), null, 'sem dados da região não inventa evento');
  assert.equal(d.list().length, 3);
});

test('textos simples, sem relógio, com contagem de leituras', () => {
  const d = D.create();
  [1, 2, 3].forEach(n => d.append(sug(n * 10, 100 + n)));
  const view = Card.avaliar(d, { leituras: 40 });
  const r3 = view.itens.find(i => i.regra === 'R3');
  assert.match(r3.texto, /sugestão mudou 3 vezes/);
  assert.match(r3.detalhe, /leituras/);
  for (const i of view.itens) assert.doesNotMatch(i.texto + i.detalhe, /minuto|segundo|hora|\bms\b/i);
});

test('AutoCal divergiu da sugestão e liberação aparecem em palavras simples', () => {
  const d = D.create();
  d.append({ leituras: 0, quem: 'REFINO', oque: 'GRAVOU', alvo: 'CURVA_K', regiao, antes: 100, depois: 104 });
  d.append({ leituras: 50, quem: 'AUTOCAL_ECU', oque: 'ECU_MUDOU', alvo: 'CURVA_K', regiao, antes: 104, depois: 100 });
  const v = Card.avaliar(d, { leituras: 60 });
  assert.match(v.itens.find(i => i.regra === 'R2').texto, /AutoCal divergiu/);
  const f = D.create();
  f.append({ leituras: 0, quem: 'REFINO', oque: 'CONGELOU', alvo: 'CURVA_K', regiao });
  const lib = Card.avaliar(f, { leituras: 1600, cobertura: [{ rpm: 1, map: 1, leituras: 100 }, { rpm: 2, map: 1, leituras: 200 }, { rpm: 3, map: 1, leituras: 300 }] }, { registrar: true });
  assert.match(lib.liberacoes[0], /Liberado/);
  assert.equal(f.list().filter(e => e.oque === 'LIBEROU').length, 1, 'liberação registrada uma vez');
  Card.avaliar(f, { leituras: 1700, cobertura: [] }, { registrar: true });
  assert.equal(f.list().filter(e => e.oque === 'LIBEROU').length, 1);
});

test('cartão: desenha e fica discreto (some o conteúdo, não o cartão) sem avisos', () => {
  const nodes = [];
  const mk = tag => { const el = { tag, children: [], attrs: {}, set textContent(v) { this._t = v; }, get textContent() { return this._t || this.children.map(c => c.textContent).join(' '); }, setAttribute(k, v) { this.attrs[k] = v; }, appendChild(c) { this.children.push(c); return c; }, replaceChildren() { this.children = []; } }; nodes.push(el); return el; };
  const host = mk('div'); host.ownerDocument = { createElement: mk };
  Card.render(host, { itens: [], liberacoes: [] });
  assert.match(host.textContent, /Coerência/); assert.match(host.textContent, /Tudo coerente/);
  Card.render(host, { itens: [{ regra: 'R3', texto: 'A sugestão mudou 3 vezes aqui', detalhe: 'Faltam 10 leituras' }], liberacoes: [] });
  assert.match(host.textContent, /mudou 3 vezes/); assert.doesNotMatch(host.textContent, /Tudo coerente/);
});

test('native-api expõe a ponte do diário (linha única) e não mexe em index.html', () => {
  const src = require('node:fs').readFileSync(require('node:path').join(__dirname, '../../app/src/main/assets/ui/core/native-api.js'), 'utf8');
  assert.match(src, /diarioBridge\(\)/);
});
