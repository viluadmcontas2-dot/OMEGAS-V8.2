'use strict';
// Regra 11 (AGENTS.md): navegação inferior = 8 abas de primeiro nível; AutoCal independente, nunca agrupado ou escondido.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const UI = path.resolve(__dirname, '../../app/src/main/assets/ui');
const html = fs.readFileSync(path.join(UI, 'index.html'), 'utf8');
const css = fs.readdirSync(UI).filter(f => f.endsWith('.css')).map(f => fs.readFileSync(path.join(UI, f), 'utf8')).join('\n');
const ORDER = ['dashboard', 'map', 'curve', 'autocal', 'refino', 'sessions', 'tools', 'diagnostico'];
const nav = html.slice(html.indexOf('<nav class="side-nav"'), html.indexOf('</nav>'));

test('nav: 8 botões data-route, nesta ordem', () => {
  assert.deepEqual([...nav.matchAll(/<button[^>]*data-route="([^"]+)"/g)].map(m => m[1]), ORDER);
});
test('nav: sem grupo Avançado', () => {
  assert.doesNotMatch(html, /nav-advanced|data-nav-advanced/);
  assert.doesNotMatch(nav, /Avançado/);
  assert.doesNotMatch(css, /nav-advanced/);
});
test('nav: AutoCal nunca escondido', () => {
  const tag = nav.match(/<button[^>]*data-route="autocal"[^>]*>/)[0];
  assert.doesNotMatch(tag, /\bhidden\b|aria-hidden|display\s*:\s*none/);
  assert.doesNotMatch(css, /data-route="autocal"\][^{]*\{[^}]*display\s*:\s*none/);
});
