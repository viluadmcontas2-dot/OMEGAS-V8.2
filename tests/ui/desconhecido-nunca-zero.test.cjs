// Auditoria de fluxo (2026-10-11): Number(null) === 0 fazia "ainda não há" virar "0,0% · equivalente", "0,00 ms",
// "qualidade 0%" e alvo K 0. Trava: toda função `finite` da interface devolve desconhecido para null/vazio.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const UI = path.resolve(__dirname, '../../app/src/main/assets/ui');
const files = [];
(function walk(dir) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    if (fs.statSync(full).isDirectory()) walk(full);
    else if (name.endsWith('.js')) files.push(full);
  }
})(UI);

/** Extrai a definição de `finite` (função ou arrow) e a executa isolada. */
function extractFinite(source) {
  const fn = source.match(/function finite\(value(?:, fallback = [^)]*)?\) \{[\s\S]*?\n\s*\}|function finite\(value\) \{[^\n]*\}/);
  const arrow = source.match(/const finite = \([^)]*\) => \{[^\n]*\};|const finite = value => [^\n]*;/);
  const code = fn ? fn[0] : arrow ? arrow[0] : null;
  if (!code) return null;
  const context = { Number };
  vm.createContext(context);
  vm.runInContext(code + '\n;globalThis.__finite = finite;', context);
  return context.__finite;
}

test('nenhuma função finite da interface transforma null/vazio em 0', () => {
  let checked = 0;
  for (const file of files) {
    const source = fs.readFileSync(file, 'utf8');
    if (!/(function finite\(|const finite = )/.test(source)) continue;
    const finite = extractFinite(source);
    assert.ok(finite, `não consegui ler finite em ${path.relative(UI, file)}`);
    checked += 1;
    if (finite(1) === true) { // forma predicado (suggestion-model): desconhecido não é número
      for (const unknown of [null, undefined, '']) assert.equal(finite(unknown), false, `${path.relative(UI, file)}: finite(${JSON.stringify(unknown)}) aceitou desconhecido`);
      assert.equal(finite(0), true);
      continue;
    }
    // Com valor padrão explícito (learning-model), desconhecido devolve o padrão de quem chama, não 0 escondido.
    const sentinel = finite.length >= 2 || /fallback/.test(String(finite)) ? 'PADRAO' : undefined;
    for (const unknown of [null, undefined, '']) {
      const value = sentinel ? finite(unknown, sentinel) : finite(unknown);
      assert.notEqual(value, 0, `${path.relative(UI, file)}: finite(${JSON.stringify(unknown)}) virou 0`);
      if (sentinel) assert.equal(value, sentinel, `${path.relative(UI, file)}: finite(${JSON.stringify(unknown)}) ignorou o padrão`);
    }
    assert.equal(finite(0), 0, `${path.relative(UI, file)}: 0 de verdade continua 0`);
    assert.equal(finite('1.5'), 1.5);
  }
  assert.ok(checked >= 10, `esperava ao menos 10 funções finite, achei ${checked}`);
});
