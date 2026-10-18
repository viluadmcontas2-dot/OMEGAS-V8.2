'use strict';

// Ferramentas > Avançado, painel com CLIQUES DOM de verdade (DOM mínimo em vm, sem jsdom): o ouvinte de captura do
// documento e o ouvinte do painel rodam na mesma ordem do navegador. Cobre: ligar (2 toques), desfazer (2 toques
// realmente chama confirm) e toque fora cancelando a prévia. Sem relógio: a espera da ECU é o gancho de dados.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const UI = path.join(__dirname, '../../app/src/main/assets/ui');
const read = file => fs.readFileSync(path.join(UI, file), 'utf8');

function attrsOf(tag) {
  const attrs = {};
  for (const m of tag.matchAll(/\s(data-[a-z-]+|disabled)(?:="([^"]*)")?/g)) attrs[m[1]] = m[2] === undefined ? '' : m[2];
  return attrs;
}
function makeButton(tag) {
  const attrs = attrsOf(tag);
  const dataset = {};
  for (const [k, v] of Object.entries(attrs)) if (k.startsWith('data-')) dataset[k.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase())] = v;
  const hit = sel => sel.split(',').some(s => { s = s.trim(); return s === 'button' || (s.startsWith('[') && (s.slice(1, -1) in attrs)); });
  const node = {
    tag: 'button', attrs, dataset, disabled: 'disabled' in attrs, inHost: true,
    matches: hit, closest(sel) { return hit(sel) ? node : null; },
  };
  return node;
}

function ambiente({ words = [0, 0] } = {}) {
  const listeners = { document: [], host: [], toggle: [] };
  const chamadas = [];
  const hooks = new Set();
  const state = { status: { state: 'IDLE' }, words };
  const host = {
    dataset: {}, childElementCount: 0, buttons: [], _html: '',
    addEventListener(type, fn) { if (type === 'click') listeners.host.push(fn); },
    contains(node) { return !!node && node.inHost === true; },
    set innerHTML(html) {
      this._html = html;
      this.buttons = [...html.matchAll(/<button\b[^>]*>/g)].map(m => makeButton(m[0]));
      this.childElementCount = html ? 1 : 0;
    },
    get innerHTML() { return this._html; },
  };
  const toggle = { textContent: '', addEventListener(type, fn) { if (type === 'click') listeners.toggle.push(fn); } };
  const screen = { classList: { toggle() {} } };
  const elements = { toolsDrawer: screen, toolAdvanced: host, toolAdvancedToggle: toggle, toolsTitle: { textContent: '' } };
  const document = {
    getElementById: id => elements[id] || null,
    addEventListener(type, fn, capture) { if (type === 'click') listeners.document.push({ fn, capture: capture === true }); },
  };
  const bridge = {
    prepareAntiStalloFlag: enable => { chamadas.push(['prepare', enable]); return JSON.stringify({ ok: true, started: true }); },
    confirmAntiStalloFlag: token => { chamadas.push(['confirm', token]); return JSON.stringify({ ok: true, started: true }); },
    cancelAntiStalloFlag: () => { chamadas.push(['cancel']); return JSON.stringify({ ok: true }); },
    getAntiStalloFlagStatus: () => JSON.stringify(state.status),
    getAdvancedParams: () => JSON.stringify({ ok: true, read: true, params: { FLAG_CONF3: { state: 'OK', values: state.words } } }),
  };
  const context = { console, Math, JSON, Number, String, Array, Object, Set, Map, Proxy };
  context.window = context; context.globalThis = context; context.document = document;
  context.OmegasAutoCal = bridge;
  context.OmegasApp = { scheduler: { addHook: (name, fn) => { hooks.add(fn); return () => hooks.delete(fn); } } };
  vm.createContext(context);
  for (const file of ['core/advanced-flags-data.js', 'core/advanced-flags.js', 'components/advanced.js']) vm.runInContext(read(file), context, { filename: file });
  const store = { get: () => ({ route: 'tools', status: { usbConnected: true } }) };
  const panel = new context.OmegasUi.AdvancedPanel(store, {});
  const env = {
    panel, chamadas, hooks, host, state,
    count: name => chamadas.filter(c => c[0] === name).length,
    emit() { for (const fn of [...hooks]) fn(); },
    /** Clique como o navegador: captura no documento, depois o painel (botão desabilitado não dispara). */
    click(target) {
      const event = { target };
      for (const l of listeners.document.filter(x => x.capture)) l.fn(event);
      if (target.disabled) return;
      if (target.inHost) for (const fn of listeners.host) fn(event);
      for (const l of listeners.document.filter(x => !x.capture)) l.fn(event);
    },
    clickOn(attr) {
      const b = host.buttons.find(x => attr in x.attrs);
      assert.ok(b, `botão ${attr} não existe na tela: ${host.innerHTML.slice(0, 200)}`);
      env.click(b);
    },
    clickOutside() { env.click({ tag: 'div', inHost: false, matches: () => false, closest: () => null }); },
    open() { listeners.toggle[0]({}); },
  };
  return env;
}
const pronto = (A, before, enable, token) => {
  const plan = A.planAntiStallo(before, enable);
  return { state: 'READY', token, enable, before, after: plan.after, frameHex: plan.frameHex, noop: plan.noop };
};
const AF = e => e.panel.A;

test('ligar: 1º toque prepara, 2º toque confirma com o token da prévia', () => {
  const e = ambiente({ words: [0, 0] });
  e.open();
  e.clickOn('data-adv-confirm');
  assert.deepEqual(e.chamadas, [['prepare', true]]);
  e.state.status = pronto(AF(e), [0, 0], true, 'tk1');
  e.emit();
  assert.equal(e.panel.flow.view().phase, 'preview');
  e.clickOn('data-adv-confirm');
  assert.deepEqual(e.chamadas.filter(c => c[0] === 'confirm'), [['confirm', 'tk1']]);
  assert.equal(e.panel.flow.view().phase, 'writing');
});

test('desfazer: o 2º toque REALMENTE confirma (não cai em cancelar nem reinicia a prévia)', () => {
  const e = ambiente({ words: [0, 0] });
  e.open();
  e.clickOn('data-adv-confirm');
  e.state.status = pronto(AF(e), [0, 0], true, 'tk1');
  e.emit();
  e.clickOn('data-adv-confirm');
  e.state.words = [0x20, 0];
  e.state.status = { state: 'DONE', outcome: 'CONFIRMED', readback: [0x20, 0], before: [0, 0], after: [0x20, 0], undo: { available: true, enable: false } };
  e.emit();
  assert.equal(e.panel.flow.view().phase, 'done');
  e.chamadas.length = 0;
  e.clickOn('data-adv-undo'); // 1º toque do desfazer: prévia
  assert.deepEqual(e.chamadas, [['prepare', false]]);
  e.state.status = pronto(AF(e), [0x20, 0], false, 'tk2');
  e.emit();
  assert.equal(e.panel.flow.view().phase, 'preview');
  e.clickOn('data-adv-undo'); // 2º toque: grava
  assert.deepEqual(e.chamadas, [['prepare', false], ['confirm', 'tk2']]);
  assert.equal(e.count('cancel'), 0, 'o 2º toque do desfazer não pode cancelar');
  assert.equal(e.panel.flow.view().phase, 'writing');
});

test('toque fora do botão de gravar cancela a prévia (e o botão volta ao rótulo normal)', () => {
  const e = ambiente({ words: [0, 0] });
  e.open();
  e.clickOn('data-adv-confirm');
  e.state.status = pronto(AF(e), [0, 0], true, 'tk1');
  e.emit();
  assert.equal(e.panel.flow.view().phase, 'preview');
  e.clickOutside();
  assert.equal(e.count('cancel'), 1);
  assert.equal(e.panel.flow.view().phase, 'idle');
  assert.equal(e.count('confirm'), 0);
});

test('toque fora na prévia do desfazer também cancela', () => {
  const e = ambiente({ words: [0x20, 0] });
  e.open();
  e.panel.flow.adopt();
  e.state.status = { state: 'IDLE', undo: { available: true, enable: false } };
  e.panel.flow.adopt();
  e.panel.refresh();
  e.clickOn('data-adv-undo');
  e.state.status = pronto(AF(e), [0x20, 0], false, 'tk3');
  e.emit();
  assert.equal(e.panel.flow.view().phase, 'preview');
  e.clickOutside();
  assert.equal(e.panel.flow.view().phase, 'idle');
  assert.equal(e.count('confirm'), 0);
});

test('tocar em Desfazer durante a prévia de LIGAR cancela a prévia antiga e começa a do desfazer', () => {
  const e = ambiente({ words: [0, 0] });
  e.open();
  e.panel.flow.adopt();
  e.state.status = { state: 'IDLE', undo: { available: true, enable: true } };
  e.panel.flow.adopt();
  e.panel.refresh();
  e.clickOn('data-adv-confirm');
  e.state.status = pronto(AF(e), [0, 0], true, 'tk1');
  e.emit();
  e.chamadas.length = 0;
  e.clickOn('data-adv-undo');
  assert.deepEqual(e.chamadas, [['cancel'], ['prepare', true]]);
  assert.equal(e.count('confirm'), 0);
});
