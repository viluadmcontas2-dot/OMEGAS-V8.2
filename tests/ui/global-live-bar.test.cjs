'use strict';

// Barra do topo persistente (dono, 2026-10-13): Tensão da ECU, MAP, Petrol Inj. e RPM em todas as abas; substitui a aba "Agora".
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const UI = path.resolve(__dirname, '../../app/src/main/assets/ui');
const read = relative => fs.readFileSync(path.join(UI, relative), 'utf8');

function node(name) {
  return { name, dataset: {}, textContent: '', isConnected: true, setAttribute() {}, querySelector: () => null };
}
function mount() {
  const cells = {};
  const bar = node('bar');
  bar.querySelector = selector => {
    const key = /data-live="([^"]+)"/.exec(selector)?.[1];
    if (!key) return null;
    if (!cells[key]) { const cell = node('cell:' + key); const value = node('value:' + key); cell.querySelector = () => value; cell.value = value; cells[key] = cell; }
    return cells[key];
  };
  const context = { console, document: { getElementById: id => (id === 'liveBar' ? bar : null) }, setTimeout: () => 1 };
  context.window = context;
  vm.createContext(context);
  vm.runInContext(read('components/live-bar.js'), context, { filename: 'live-bar.js' });
  const store = { state: {}, get: () => store.state, subscribe: () => () => {} };
  return { component: new context.OmegasUi.LiveBar({ store }), model: context.OmegasUi.LiveBarModel, cells };
}
const frame = (raw19, extra = {}) => ({ valid: true, ageMs: 40, live: { rpm: 2000, petrol_ms: 4, load_bar: 0.5, fuel: 'GNV', unknown_raw_19: raw19 }, ...extra });

test('o HTML tem a barra com tensão (provisória), MAP, Petrol Inj. e RPM, e ela fica acima das telas', () => {
  const html = read('index.html');
  assert.match(html, /<header id="liveBar"[^>]*class="workspace-head live-bar"/);
  for (const key of ['voltage', 'map', 'petrol', 'rpm']) assert.match(html, new RegExp('data-live="' + key + '"'));
  assert.match(html, /<small>Tensão<\/small>/); assert.match(html, /title="Tensão da central/);
  assert.ok(html.indexOf('id="liveBar"') < html.indexOf('<main class="screen-host">'));
  assert.doesNotMatch(html, /data-route="(dashboard|suggestions)"|data-screen="(dashboard|suggestions)"/);
});

test('valores: tensão = byte 19 ÷ 16 (1 casa), MAP e Petrol Inj. com 2 casas, RPM inteiro com separador', () => {
  const { component, cells } = mount();
  component.render({ telemetry: frame(224) });
  assert.equal(cells.voltage.value.textContent, '14,0');
  assert.equal(cells.map.value.textContent, '0,50');
  assert.equal(cells.petrol.value.textContent, '4,00');
  assert.equal(cells.rpm.value.textContent, '2.000');
});

test('0 (gasolina), ausente, inválido e velho são "—", nunca 0', () => {
  const { component, cells } = mount();
  component.render({ telemetry: frame(0) });
  assert.equal(cells.voltage.value.textContent, '—');
  assert.equal(cells.voltage.dataset.state, 'unknown');
  component.render({ telemetry: frame(undefined) });
  assert.equal(cells.voltage.value.textContent, '—');
  component.render({ telemetry: { valid: false } });
  for (const key of ['voltage', 'map', 'petrol', 'rpm']) assert.equal(cells[key].value.textContent, '—', key);
  component.render({ telemetry: frame(224, { ageMs: 5000 }) });
  assert.equal(cells.rpm.value.textContent, '—', 'sem quadro novo há mais de 2,5 s não mostra o último RPM');
});

test('a partir de 14,1 V o campo entra em alerta; abaixo disso não', () => {
  const { component, cells } = mount();
  component.render({ telemetry: frame(225) }); // 14,06 V
  assert.notEqual(cells.voltage.dataset.state, 'alert');
  component.render({ telemetry: frame(226) }); // 14,13 V
  assert.equal(cells.voltage.dataset.state, 'alert');
});

test('só redesenha com quadro novo e só escreve o que mudou; sem polling nem escrita na ECU', () => {
  const { component, cells } = mount();
  const telemetry = frame(224);
  component.render({ telemetry });
  cells.rpm.value.textContent = 'marca';
  component.render({ telemetry }); // mesmo objeto: nada
  assert.equal(cells.rpm.value.textContent, 'marca');
  const source = read('components/live-bar.js');
  assert.doesNotMatch(source, /setInterval|protocolTransaction|writeMap|writeCurve/);
});

test('o quadro rápido chega em todas as abas (a barra é persistente) e a assinatura inclui o que a barra mostra', () => {
  const app = read('app.js');
  assert.match(app, /function isTelemetryRoute\(\) \{\s*return true;/);
  assert.match(app, /unknown_raw_19 \?\? live\.unknownRaw19/);
  assert.match(read('index.html'), /components\/live-bar\.js/);
});
