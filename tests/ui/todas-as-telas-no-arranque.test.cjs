'use strict';
// Dono (2026-10-19): carregar tela sob demanda (ROUTE_SCRIPTS/loadRoute) é errado. Todos os scripts entram no index.html, em ordem
// de dependência, como a AutoCal; falha de tela nunca deixa a aba vazia (cartão com "Toque para tentar de novo").
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ui = path.resolve(__dirname, '../../app/src/main/assets/ui');
const html = fs.readFileSync(path.join(ui, 'index.html'), 'utf8');
const router = fs.readFileSync(path.join(ui, 'core/router.js'), 'utf8');
const app = fs.readFileSync(path.join(ui, 'app.js'), 'utf8');
const scripts = [...html.matchAll(/<script src="([^"]+)"/g)].map(m => m[1]);
const pos = s => { const i = scripts.indexOf(s); assert.ok(i >= 0, `${s} falta no index.html`); return i; };

test('nenhum carregamento sob demanda no router nem no app', () => {
  for (const [nome, texto] of [['router.js', router], ['app.js', app]]) {
    assert.doesNotMatch(texto, /ROUTE_SCRIPTS|loadRoute|loadOptionalScript|retryRoute|createElement\('script'\)/, `${nome} não carrega script por aba`);
  }
});

test('todas as telas e módulos estão no index.html (nada fora do arranque)', () => {
  const todos = fs.readdirSync(path.join(ui, 'screens')).map(f => 'screens/' + f)
    .concat(fs.readdirSync(path.join(ui, 'components')).map(f => 'components/' + f), ['map-editor.js']);
  for (const f of todos) pos(f);
  for (const f of ['core/autocal-api.js', 'core/manual-automatch-advice.js', 'core/curve-to-map.js', 'core/balance-runner.js', 'core/write-gate.js',
    'core/curve-smoothing.js', 'core/stall-region.js', 'core/map-fix.js', 'core/gasoline-reference.js']) pos(f);
  assert.equal(new Set(scripts).size, scripts.length, 'nenhum script repetido');
});

test('ordem de dependência (a mesma dos antigos grupos por aba) e app.js por último', () => {
  const antes = (a, b) => assert.ok(pos(a) < pos(b), `${a} antes de ${b}`);
  antes('core/autocal-api.js', 'core/manual-automatch-advice.js');
  antes('core/manual-automatch-advice.js', 'screens/autocal-original.js');
  antes('screens/autocal-original.js', 'screens/autocal-cockpit.js');
  antes('map-editor.js', 'core/curve-to-map.js');
  antes('core/curve-to-map.js', 'core/balance-runner.js');
  antes('core/balance-runner.js', 'core/write-gate.js');
  antes('core/write-gate.js', 'screens/map.js');
  antes('core/write-gate.js', 'core/map-fix.js');
  antes('core/map-fix.js', 'screens/refino.js');
  antes('core/stall-region.js', 'screens/refino.js');
  antes('core/gasoline-reference.js', 'screens/refino.js');
  antes('core/curve-smoothing.js', 'screens/curve.js');
  antes('core/advanced-flags-data.js', 'core/advanced-flags.js');
  antes('core/anti-apagao-valores.js', 'components/advanced.js');
  antes('core/cartoes-motor.js', 'components/advanced.js');
  antes('core/scheduler.js', 'components/live-bar.js');
  assert.equal(scripts[scripts.length - 1], 'app.js');
  assert.ok(scripts.every(s => s === 'app.js' || pos(s) < pos('app.js')));
});

test('tela que não abre mostra cartão com retentativa por toque (nunca vazio)', () => {
  assert.match(app, /Não foi possível abrir esta parte\. Toque para tentar de novo\./);
  assert.match(app, /data-route-retry/);
});
