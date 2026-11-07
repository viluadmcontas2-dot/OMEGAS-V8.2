'use strict';
// Contrato de navegação do proprietário: cinco áreas visíveis; Sessões interna.
// Teste estático de regressão, não substitui navegador ou validação física.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ui = path.resolve(__dirname, '../../app/src/main/assets/ui');
const html = fs.readFileSync(path.join(ui, 'index.html'), 'utf8');
const router = fs.readFileSync(path.join(ui, 'core/router.js'), 'utf8');
const nav = html.match(/<nav\b[^>]*class="side-nav"[^>]*>([\s\S]*?)<\/nav>/);
test('somente cinco áreas no menu principal, na ordem aprovada', () => {
  assert.ok(nav, 'menu principal ausente');
  const routes = [...nav[1].matchAll(/data-route="([^"]+)"/g)].map(m => m[1]);
  assert.deepEqual(routes, ['autocal', 'refino', 'map', 'curve', 'tools']);
});
test('Sessões permanece acessível internamente, sem sexta aba', () => {
  assert.match(html, /data-screen="sessions"/);
  assert.match(router, /['"]sessions['"]/);
  assert.doesNotMatch(nav[1], /data-route="sessions"/);
});
test('rotas alternativas de quatro áreas não entram no menu', () => {
  assert.doesNotMatch(nav[1], /data-route="(?:regular|carro)"/);
});
