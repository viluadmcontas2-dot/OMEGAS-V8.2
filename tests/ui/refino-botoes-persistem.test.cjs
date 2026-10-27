// Botões do Refino persistem (relato do dono, 2026-10-16: "clico em Ver alterações, não altero nada, e o botão some; ou troco de
// aba e o botão some"). Trocado em 2026-10-27 (contrato de fluxo): não existe mais "Ver alterações" nem cartão intermediário. O
// botão principal "Gravar correção" é FIXO (mesmo elemento, nunca hidden); sem proposta fica cinza com o motivo dentro.
// Trocar de aba e voltar (refresh) e um tick sem piloto não o tiram do lugar; só a evidência muda o estado (ativo/cinza).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { documentMinimo } = require('./helpers/dom-minimo.cjs');

const ui = path.resolve(__dirname, '../../app/src/main/assets/ui');
const read = file => fs.readFileSync(path.join(ui, file), 'utf8');

function harness() {
  const bar = documentMinimo().createElement('div');
  const byId = new Map([['refinoActions', bar]]);
  const bySel = new Map();
  const node = id => {
    const attrs = {};
    return { id, textContent: '', innerHTML: '', hidden: false, dataset: {}, isConnected: true, addEventListener() {}, querySelector() { return null; }, querySelectorAll() { return []; },
      setAttribute(k, v) { attrs[k] = String(v); }, getAttribute(k) { return attrs[k] ?? null; }, getBoundingClientRect() { return { width: 860, height: 300 }; } };
  };
  const document = {
    getElementById(id) { if (!byId.has(id)) byId.set(id, node(id)); return byId.get(id); },
    querySelector(sel) { if (!bySel.has(sel)) bySel.set(sel, node(null)); return bySel.get(sel); },
    querySelectorAll() { return []; },
  };
  const window = { document, console, setTimeout: () => 0 };
  window.window = window;
  window.globalThis = window;
  for (const file of ['core/write-gate.js', 'components/acao-principal.js', 'core/fluxo.js']) vm.runInNewContext(read(file), window);
  vm.runInNewContext(read('screens/refino.js'), window);
  let eq = null;
  window.OmegasUi.AutoCalApi = { available: () => true, equivalence: () => eq, projection: () => ({ ok: false }) };
  let state = { route: 'refino' };
  const store = { get: () => state, patch(p) { state = { ...state, ...p }; }, subscribe() { return () => {}; } };
  const native = { startCurveRead: () => ({ ok: false }), curveOperation: () => ({}) };
  const screen = new window.OmegasUi.RefinoScreen({ store, scheduler: { addHook: () => () => {} }, api: native });
  return { screen, setEq: value => { eq = value; }, button: () => bar.querySelector('[data-fluxo="principal"]'), first: bar.querySelector('[data-fluxo="principal"]') };
}

const proposalEq = () => ({ ok: true, autopilot: {
  phase: 'PROPOSTA_PRONTA', ecuDone: true, ecuOnline: true, action: { kind: 'review', label: 'Revisar e gravar 1 ponto', enabled: true },
  proposal: { known: true, count: 1, points: [{ index: 4, currentRaw: 16384, targetRaw: 17000, referenceTimeMs: 5.5 }], held: [], signature: '4:16384>17000' },
} });

test('"Gravar correção" é o mesmo botão, nunca hidden, ao trocar de aba e ao perder o piloto; só fica cinza (com motivo) quando a evidência some', () => {
  const { screen, setEq, button, first } = harness();
  setEq(proposalEq());
  screen.refresh(true);
  assert.equal(button().getAttribute('aria-disabled'), 'false', 'com proposta do piloto o botão está ativo');
  assert.equal(button().textContent, 'Gravar correção');
  // Trocar de aba e voltar = refresh(true) com a mesma evidência: nada muda.
  screen.refresh(true);
  screen.refresh(true);
  assert.equal(button(), first); assert.equal(button().hidden, false);
  assert.equal(button().getAttribute('aria-disabled'), 'false', 'trocar de aba não apaga a proposta');
  // Um tick sem piloto (ponte/serviço fora um instante): o botão continua lá, cinza e dizendo por quê.
  setEq({ ok: false });
  screen.refresh(true);
  assert.equal(button(), first); assert.equal(button().hidden, false);
  assert.match(button().textContent, /Gravar correção · Conecte a central/);
  setEq(proposalEq());
  screen.refresh(true);
  assert.equal(button().getAttribute('aria-disabled'), 'false');
  // Evidência mudou de verdade: o piloto não propõe mais (ex.: gravação confirmada, agora verificando).
  setEq({ ok: true, autopilot: { phase: 'VERIFICANDO', ecuDone: true, ecuOnline: true, action: { kind: 'none' }, proposal: { known: true, count: 0, points: [] } } });
  screen.refresh(true);
  assert.equal(button(), first); assert.equal(button().hidden, false, 'sem proposta o botão NÃO some');
  assert.equal(button().getAttribute('aria-disabled'), 'true');
  assert.match(button().textContent, /Gravar correção · Nada mais para gravar agora/);
});
