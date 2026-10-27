'use strict';
// Guarda da ferramenta que renderiza as telas do canvas de design (tools/design/render_dc.cjs):
// ela precisa ACUSAR texto cortado e texto em cima de texto, e NÃO acusar tela limpa.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');

const RUNNER = path.join(__dirname, '..', '..', 'tools', 'design', 'render_dc.cjs');

function tela(dir, nome, corpo) {
  fs.writeFileSync(path.join(dir, nome), `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><title>t</title><script src="./support.js"></script></head><body><x-dc><div style="position:relative;width:400px;height:200px;overflow:hidden;font:16px sans-serif">${corpo}</div></x-dc><script type="text/x-dc" data-dc-script data-props='{"$preview":{"width":400,"height":200}}'>class Component extends DCLogic { state = { n: 0 }; renderVals() { const n = (this.state || {}).n; return { n, mais: () => this.setState({ n: n + 1 }) }; } }</script></body></html>`);
}
function rodar(corpo, extra = []) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'dc-'));
  tela(dir, '01-teste.dc.html', corpo);
  const r = spawnSync('node', [RUNNER, dir, '--out', path.join(dir, 'out'), '--quiet', ...extra], { encoding: 'utf8' });
  const rel = fs.existsSync(path.join(dir, 'out', 'RELATORIO.md')) ? fs.readFileSync(path.join(dir, 'out', 'RELATORIO.md'), 'utf8') : '';
  return { status: r.status, rel, dir };
}

test('tela limpa passa', () => {
  const r = rodar('<p style="margin:20px">Tudo certo</p>');
  assert.strictEqual(r.status, 0, r.rel);
});

test('texto cortado por caixa estreita é acusado', () => {
  const r = rodar('<div style="margin:20px;width:60px;overflow:hidden;white-space:nowrap">texto bem comprido que corta</div>');
  assert.strictEqual(r.status, 1);
  assert.match(r.rel, /texto cortado/);
});

test('texto em cima de texto é acusado', () => {
  const r = rodar('<span style="position:absolute;left:20px;top:20px">Primeiro rótulo</span><span style="position:absolute;left:30px;top:22px">Segundo rótulo</span>');
  assert.strictEqual(r.status, 1);
  assert.match(r.rel, /em cima de texto/);
});

test('buracos {{ }} e clique funcionam (sem erro de script)', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'dc-'));
  tela(dir, '01-teste.dc.html', '<button type="button" onClick="{{ mais }}" style="margin:20px;height:44px">mais</button><span style="margin-left:20px">valor {{ n }}</span>');
  const steps = path.join(dir, 'steps.json');
  fs.writeFileSync(steps, JSON.stringify({ '01-teste.dc.html': [{ nome: 'clicou', click: 'button' }] }));
  const r = spawnSync('node', [RUNNER, dir, '--out', path.join(dir, 'out'), '--steps', steps, '--quiet'], { encoding: 'utf8' });
  assert.strictEqual(r.status, 0, r.stdout + r.stderr);
  assert.ok(fs.existsSync(path.join(dir, 'out', '01-teste__clicou.png')));
});
