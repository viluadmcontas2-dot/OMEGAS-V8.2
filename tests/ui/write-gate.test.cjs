'use strict';
// Porta de gravação: o app não propõe/grava Curva K ou Mapa K enquanto a ECU ainda está no AutoMatch
// (a ECU aplicaria a correção dela em cima da nossa). Dono, 2026-10-13.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { check } = require(path.resolve(__dirname, '../../app/src/main/assets/ui/core/write-gate.js'));

// ECU ainda trabalhando: fecha e diz em que AutoMatch está.
const working = check({ ecuDone: false, autoMatchCount: 1, maxAutomatch: 3 });
assert.equal(working.ok, false);
assert.match(working.message, /AutoMatch \(1 de 3\)/);
assert.match(working.message, /em cima da minha/);
// Sem contador, a frase continua válida.
assert.match(check({ ecuDone: false }).message, /^A ECU ainda está no AutoMatch\. Espere/);
// ECU terminou (desligada, máximo atingido ou aquisição completa e quieta): abre.
assert.equal(check({ ecuDone: true, autoMatchCount: 3, maxAutomatch: 3 }).ok, true);
// Estado desconhecido nunca bloqueia (ponte fora, app novo): só o "não terminou" explícito fecha.
for (const unknown of [null, undefined, {}, { ecuDone: null }, { ecuDone: 'false' }]) assert.equal(check(unknown).ok, true, JSON.stringify(unknown));

// As duas entradas que o APP propõe passam pela porta; a edição manual da Curva K não.
const ui = path.resolve(__dirname, '../../app/src/main/assets/ui');
const map = fs.readFileSync(path.join(ui, 'screens/map.js'), 'utf8');
const refino = fs.readFileSync(path.join(ui, 'screens/refino.js'), 'utf8');
const curve = fs.readFileSync(path.join(ui, 'screens/curve.js'), 'utf8');
assert.match(map, /startCurveToMap\(\) \{\s*const gate = root\.OmegasWriteGate\?\.check/, 'exportação Curva K→Mapa K passa pela porta');
assert.match(refino, /mapProposal\(\) \{[\s\S]{0,260}OmegasWriteGate\.check\(this\.eq\?\.autopilot\)\.ok\) return null/, 'proposta do Refino para o Mapa K passa pela porta');
assert.equal(/OmegasWriteGate/.test(curve), false, 'edição manual da Curva K não é bloqueada');
// Contrato do carregamento por aba (core/router.js, ROUTE_SCRIPTS): a porta de gravação carrega ANTES da tela que grava,
// em toda aba cuja tela a usa; e o código dessas telas só a consulta com `?.`/`&&`, então ela nunca pode faltar.
const lazy = fs.readFileSync(path.join(ui, 'core/router.js'), 'utf8');
const grupo = rota => {
  const m = new RegExp('\\n    ' + rota + ': \\[([^\\]]*)\\]').exec(lazy);
  assert.ok(m, 'grupo de scripts da aba ' + rota);
  return [...m[1].matchAll(/'([^']+)'/g)].map(x => x[1]);
};
const telasQueGravam = fs.readdirSync(path.join(ui, 'screens')).filter(f => /OmegasWriteGate/.test(fs.readFileSync(path.join(ui, 'screens', f), 'utf8')));
assert.deepEqual(telasQueGravam.sort(), ['map.js', 'refino.js'], 'quais telas usam a porta');
for (const f of telasQueGravam) {
  const rota = f === 'map.js' ? 'map' : 'refino';
  const lista = grupo(rota);
  assert.ok(lista.includes('core/write-gate.js'), 'porta carregada com a aba ' + rota);
  assert.ok(lista.indexOf('core/write-gate.js') < lista.indexOf('screens/' + f), 'porta antes da tela ' + f);
}
assert.doesNotMatch(fs.readFileSync(path.join(ui, 'index.html'), 'utf8'), /screens\/(map|refino)\.js/, 'telas de gravação não carregam fora da aba');
console.log('WRITE_GATE=PASS');
