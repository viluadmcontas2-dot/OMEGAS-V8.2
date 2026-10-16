// Botões do Refino persistem (relato do dono, 2026-10-16: "clico em Ver alterações, não altero nada, e o botão some; ou troco de
// aba e o botão some"). Causa: render() zerava a barra sempre que o cartão estava aberto ("a barra fica quieta") e o estado do
// cartão vive na instância única da tela — então o botão sumia ao abrir o cartão e continuava sumido ao trocar de aba e voltar;
// e um tick sem piloto (ponte fora um instante) era tratado como "sumiu" e esvaziava o cartão. Agora a proposta e o botão só
// somem quando a evidência muda (piloto deixa de propor, gravação confirmada, porta fecha).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ui = path.resolve(__dirname, '../../app/src/main/assets/ui');
const read = file => fs.readFileSync(path.join(ui, file), 'utf8');

function harness() {
  const byId = new Map();
  const bySel = new Map();
  const node = id => ({
    id, textContent: '', innerHTML: '', hidden: false, dataset: {}, isConnected: true,
    addEventListener() {}, querySelector() { return null; }, querySelectorAll() { return []; },
    getBoundingClientRect() { return { width: 860, height: 300 }; },
  });
  const document = {
    getElementById(id) { if (!byId.has(id)) byId.set(id, node(id)); return byId.get(id); },
    querySelector(sel) { if (!bySel.has(sel)) bySel.set(sel, node(null)); return bySel.get(sel); },
    querySelectorAll() { return []; },
  };
  const window = { document, console, setTimeout: () => 0 };
  window.window = window;
  window.globalThis = window;
  vm.runInNewContext(read('core/write-gate.js'), window);
  vm.runInNewContext(read('screens/refino.js'), window);
  let eq = null;
  window.OmegasUi.AutoCalApi = { available: () => true, equivalence: () => eq, projection: () => ({ ok: false }) };
  let state = { route: 'refino' };
  const store = { get: () => state, patch(p) { state = { ...state, ...p }; }, subscribe() { return () => {}; } };
  const native = { startCurveRead: () => ({ ok: false }), curveOperation: () => ({}) };
  const screen = new window.OmegasUi.RefinoScreen({ store, scheduler: { addHook: () => () => {} }, api: native });
  return { screen, setEq: value => { eq = value; }, button: document.querySelector('[data-refino-preview]'), card: document.getElementById('refinoReview') };
}

const proposalEq = () => ({ ok: true, autopilot: {
  phase: 'PROPOSTA_PRONTA', ecuDone: true, ecuOnline: true, action: { kind: 'review', label: 'Revisar e gravar 1 ponto', enabled: true },
  proposal: { known: true, count: 1, points: [{ index: 4, currentRaw: 16384, targetRaw: 17000, referenceTimeMs: 5.5 }], held: [], signature: '4:16384>17000' },
} });

test('"Ver alterações" e a proposta ficam ao abrir/fechar o cartão e ao trocar de aba; somem só quando o piloto deixa de propor', () => {
  const { screen, setEq, button, card } = harness();
  setEq(proposalEq());
  screen.refresh(true);
  assert.equal(button.hidden, false, 'com proposta do piloto o botão existe');
  screen.openPreview();
  assert.equal(card.hidden, false);
  assert.match(card.innerHTML, /Gravar na Curva K/);
  assert.equal(button.hidden, false, 'abrir o cartão não some com o botão');
  // Trocar de aba e voltar = refresh(true) com a mesma evidência: nada muda.
  screen.refresh(true);
  screen.refresh(true);
  assert.equal(button.hidden, false, 'trocar de aba não some com o botão');
  assert.match(card.innerHTML, /Gravar na Curva K/, 'o cartão continua com a proposta');
  // Um tick sem piloto (ponte/serviço fora um instante) não é evidência: o cartão fica como está e não avisa "mudou".
  setEq({ ok: false });
  screen.refresh(true);
  assert.match(card.innerHTML, /Gravar na Curva K/, 'leitura indisponível não esvazia o cartão');
  setEq(proposalEq());
  screen.refresh(true);
  assert.doesNotMatch(card.innerHTML, /A proposta mudou|Nada para gravar agora/);
  screen.closePreview();
  assert.equal(card.hidden, true);
  assert.equal(button.hidden, false, 'fechar o cartão não some com o botão');
  // Evidência mudou de verdade: o piloto não propõe mais (ex.: gravação confirmada, agora verificando).
  setEq({ ok: true, autopilot: { phase: 'VERIFICANDO', ecuDone: true, ecuOnline: true, action: { kind: 'none' }, proposal: { known: true, count: 0, points: [] } } });
  screen.refresh(true);
  assert.equal(button.hidden, true, 'sem proposta do piloto o botão some');
});
