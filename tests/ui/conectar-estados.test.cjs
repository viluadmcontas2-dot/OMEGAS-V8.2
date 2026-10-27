'use strict';
// I6: tela Conectar (mockups 01–01d). Um leiaute só; o botão principal não some nem troca de lugar.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const { documentMinimo } = require('./helpers/dom-minimo.cjs');
const file = path.resolve(__dirname, '../../app/src/main/assets/ui/screens/conectar.js');
function load() {
  assert.ok(fs.existsSync(file), 'API ausente: screens/conectar.js');
  const ctx = {};
  vm.runInNewContext(fs.readFileSync(file, 'utf8'), ctx);
  return ctx.OmegasConectar;
}
const FASES = ['PROCURANDO', 'PERMISSAO', 'CONECTANDO', 'LENDO', 'PRONTO', 'RETOMANDO', 'PROBLEMA'];
test('cada fase tem título, linha e botão com verbo + objeto, sem relógio', () => {
  const C = load();
  for (const fase of FASES) {
    const d = C.describe({ fase, causa: fase === 'PROBLEMA' ? 'SEM_PERMISSAO' : null, tentativas: 2 });
    assert.ok(d.titulo && d.linha && d.botao, fase);
    assert.doesNotMatch(JSON.stringify(d), /\b\d+ ?(s|seg|min)\b/i, 'nada de minutos/segundos');
  }
});
test('problema traz causa e ação em palavras simples, uma por causa', () => {
  const C = load();
  const m = { SEM_PERMISSAO: /Permitir/, CABO_SEM_CENTRAL: /ignição/i, CENTRAL_NAO_RESPONDE: /recoloque o cabo/i, APARELHO_INCOMPATIVEL: /OMEGAS/ };
  for (const [causa, re] of Object.entries(m)) {
    const d = C.describe({ fase: 'PROBLEMA', causa });
    assert.match(d.causa + ' ' + d.acao, re, causa);
    assert.equal(d.botao, 'Tentar de novo');
    assert.equal(d.habilitado, true);
  }
});
test('retomando e pronto: botão cinza com o motivo dentro; pendência oferece Concluir sem perder trabalho', () => {
  const C = load();
  const r = C.describe({ fase: 'RETOMANDO', tentativas: 3 });
  assert.equal(r.habilitado, false); assert.match(r.botao, /Reconectando/); assert.match(r.linha, /3/);
  const p = C.describe({ fase: 'PRONTO', pendencia: { feito: 'Mapa K', falta: 'Curva K' } });
  assert.match(p.retomada, /Mapa K gravado\. Curva K ainda não\./);
  assert.equal(p.concluir, true);
  assert.equal(C.describe({ fase: 'PRONTO' }).concluir, false);
  assert.equal(C.describe({ fase: 'RETOMANDO', pendencia: { feito: 'Mapa K', falta: 'Curva K' } }).concluir, false, 'só oferece Concluir ao vivo');
});
test('mount: mesmos controles em todas as fases, principal é o mesmo elemento e não some', () => {
  const C = load();
  const host = documentMinimo().createElement('section');
  const tela = C.mount(host, {});
  const principal = host.querySelector('[data-fluxo="principal"]');
  assert.ok(principal);
  const ids = () => ['principal', 'concluir', 'deixar', 'detalhes', 'como-funciona', 'demonstracao'].map(k => !!host.querySelector('[data-fluxo="' + k + '"]'));
  const base = ids(); assert.ok(base.every(Boolean), 'todos os controles existem desde o início');
  for (const fase of FASES) {
    tela.set({ fase, causa: 'SEM_PERMISSAO', tentativas: 1, pendencia: fase === 'PRONTO' ? { feito: 'Mapa K', falta: 'Curva K' } : null });
    assert.equal(host.querySelector('[data-fluxo="principal"]'), principal, fase);
    assert.equal(principal.hidden, false); assert.deepEqual(ids(), base, fase);
  }
});
test('botão chama a ação certa; desabilitado não chama; estado desconhecido vira procurando', () => {
  const C = load();
  const host = documentMinimo().createElement('section');
  const calls = [];
  const tela = C.mount(host, { onRetry: () => calls.push('retry'), onFinish: () => calls.push('finish'), onKeep: () => calls.push('keep') });
  const principal = host.querySelector('[data-fluxo="principal"]');
  tela.set({ fase: 'PROBLEMA', causa: 'SEM_PERMISSAO' }); principal.click();
  tela.set({ fase: 'RETOMANDO', tentativas: 1 }); principal.click();
  assert.deepEqual(calls, ['retry']);
  tela.set({ fase: 'PRONTO', pendencia: { feito: 'Mapa K', falta: 'Curva K' } });
  host.querySelector('[data-fluxo="concluir"]').click(); host.querySelector('[data-fluxo="deixar"]').click();
  assert.deepEqual(calls, ['retry', 'finish', 'keep']);
  tela.set(null); assert.match(host.textContent, /Procurando/);
  tela.set({ fase: 'XYZ' }); assert.match(host.textContent, /Procurando/);
});
test('tela lê o estado da ponte (getConnection) sem quebrar com JSON ruim', () => {
  const C = load();
  assert.equal(C.parse('{"fase":"LENDO","tentativas":0}').fase, 'LENDO');
  assert.equal(C.parse('lixo').fase, 'PROCURANDO');
  assert.equal(C.parse(null).fase, 'PROCURANDO');
});
