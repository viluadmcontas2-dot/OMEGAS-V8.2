'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { verificar } = require('../../tools/design/contrato_dc.cjs');
test('duas páginas: detecta deslocamento, tamanho, ausência e invisibilidade; tolera 2px', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'contrato-'));
  const html = (style = '', extra = '') => `<div data-fluxo="ultima">Última</div><button data-fluxo="desfazer">Desfazer</button><button data-fluxo="principal" style="position:absolute;left:20px;top:100px;width:200px;height:58px;${style}">Gravar</button>${extra}`;
  const a = path.join(dir, 'a.html'), b = path.join(dir, 'b.html');
  fs.writeFileSync(a, html());
  try {
    for (const [style, bad] of [['left:22px', false], ['left:23px', true], ['width:203px', true], ['display:none', true], ['opacity:0', true]]) {
      fs.writeFileSync(b, html(style));
      const result = await verificar([a, b]);
      assert.equal(result.violacoes.length > 0, bad, style);
    }
    fs.writeFileSync(b, html().replace('data-fluxo="desfazer"', 'data-outro="desfazer"'));
    assert.ok((await verificar([a, b])).violacoes.length);
    fs.writeFileSync(b, html('', '<button data-fluxo="novo">Novo</button>'));
    assert.ok((await verificar([a, b])).violacoes.length);
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});
const vm = require('node:vm');
const project = path.resolve(__dirname, '../../docs/produto/mockups/v3/project');
const telas = fs.readdirSync(project).filter(f => /^05.*\.dc\.html$/.test(f)).map(f => path.join(project, f));
test('quatro telas e transições mantêm todos os retângulos', async () => {
  const steps = {
    '05-Refino.dc.html': ['[data-fluxo="principal"]', '[data-fluxo="principal"]', '[data-fluxo="desfazer"]', '[data-fluxo="principal"]'],
    '05c-Refino-piorou.dc.html': ['[data-fluxo="desfazer"]'],
    '05d-Refino-central-aprendendo.dc.html': ['[data-fluxo="conta"]', '[data-fluxo="principal"]', '[data-fluxo="principal"]', '[data-fluxo="desfazer"]']
  };
  assert.equal(telas.length, 4);
  assert.deepEqual((await verificar(telas, steps)).violacoes, []);
});
function componente(file) {
  const html = fs.readFileSync(file, 'utf8');
  const script = html.match(/<script type="text\/x-dc"[^>]*>([\s\S]*?)<\/script>/)[1];
  class DCLogic { setState(patch) { Object.assign(this.state, patch); } }
  return vm.runInNewContext(script + '\nnew Component()', { DCLogic });
}
test('mockups: duas gravações sem desfazer, histórico, desfazer e gravar novamente', () => {
  for (const file of telas) {
    const c = componente(file);
    let v = c.renderVals();
    assert.equal(typeof v.fluxoGravar, 'function', file);
    if (/05b|05c/.test(path.basename(file))) {
      assert.equal(v.principalBloqueado, true);
      if (/05c/.test(file)) { assert.equal(v.desfazerBloqueado, false); v.fluxoDesfazer(); assert.equal(c.renderVals().desfazerBloqueado, true); }
      continue;
    }
    if (/05d/.test(file)) { assert.equal(v.principalBloqueado, true); v.fluxoAssumir(); v = c.renderVals(); }
    assert.equal(v.desfazerBloqueado, true);
    assert.match(v.previa, /200 → 210/);
    v.fluxoGravar(); v = c.renderVals();
    assert.equal(v.principalBloqueado, false);
    assert.match(v.ultimaTexto, /Mapa K/);
    assert.equal(v.seloTexto, 'Gravado e conferido');
    v.fluxoGravar(); v = c.renderVals();
    assert.match(v.ultimaTexto, /Curva K/);
    assert.match(v.historicoTexto, /Mapa K.*Curva K/);
    assert.equal(v.principalBloqueado, true);
    v.fluxoDesfazer(); v = c.renderVals();
    assert.equal(v.principalBloqueado, false);
    v.fluxoGravar();
    assert.match(c.renderVals().ultimaTexto, /Curva K/);
  }
});
