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

// Nível do gás em bolinhas + lâmpada "No gás / Na gasolina" (dono, 2026-10-20; mockup v2/01-barras). level_raw = byte 13 do 48 01.
// Zonas = entre as referências da central (39/93/143/219, lidas no fio); o byte sobe com o consumo (255 = vazio), medido no campo.
const withLevel = (level, extra = {}) => ({ valid: true, ageMs: 40, live: { rpm: 2000, petrol_ms: 4, load_bar: 0.5, fuel: 'GNV', unknown_raw_19: 224, level_raw: level, ...extra } });

test('o HTML tem a pílula Nível com 5 bolinhas (reserva, ¼, ½, ¾, cheio) e a lâmpada da fonte', () => {
  const html = read('index.html');
  assert.match(html, /data-live="level"[^>]*data-state="unknown"/);
  assert.equal((/data-live="level"[\s\S]*?<\/div>/.exec(html)[0].match(/<i>/g) || []).length, 5);
  assert.match(html, /data-live="source"/);
  assert.match(html, /<small>Nível<\/small>/);
});

test('nível: no campo o byte SOBE com o consumo (255 = vazio; abastecer volta a ~31–78): a escada 75/125/177/253 não pode mostrar tanque cheio como reserva', () => {
  const { model } = mount();
  const state = raw => model.read(withLevel(raw)).levelState;
  // Dados reais (101 sessões, 210 mil quadros): degraus ≈ 75, 125, 177, 253 e < 39 logo após abastecer; zonas = entre as referências 39/93/143/219 lidas da ECU.
  assert.equal(state(31), 'full', 'recém abastecido (31–38) = cheio');
  assert.equal(state(75), 'three');
  assert.equal(state(125), 'half');
  assert.equal(state(177), 'quarter');
  assert.equal(state(253), 'reserve', 'byte alto = vazio = reserva (vermelho)');
  assert.equal(state(254), 'reserve');
  // limites entre zonas = as referências da central
  assert.equal(state(38), 'full'); assert.equal(state(39), 'three');
  assert.equal(state(92), 'three'); assert.equal(state(93), 'half');
  assert.equal(state(142), 'half'); assert.equal(state(143), 'quarter');
  assert.equal(state(218), 'quarter'); assert.equal(state(219), 'reserve');
  // 19–22 = sensor sem leitura (central sem motor); ≤ 25 nunca vira "cheio"
  for (const noReading of [19, 21, 22, 25]) assert.equal(state(noReading), 'unknown', 'sem leitura: ' + noReading);
  for (const unknown of [0, -1, undefined, null, 'x']) assert.equal(state(unknown), 'unknown', String(unknown));
  assert.equal(model.read({ valid: false }).levelState, 'unknown');
  assert.equal(model.read({ ...withLevel(143), ageMs: 5000 }).levelState, 'unknown', 'quadro velho não mostra o último nível');
});

test('a pílula e a lâmpada seguem o quadro: nível por bolinhas, "No gás" no GNV, "Na gasolina" na gasolina, "Sem central" sem quadro', () => {
  const { component, cells } = mount();
  component.render({ telemetry: withLevel(125) });
  assert.equal(cells.level.dataset.state, 'half');
  assert.equal(cells.level.value.textContent, '');
  assert.equal(cells.source.dataset.state, 'gas');
  assert.equal(cells.source.value.textContent, 'No gás');
  component.render({ telemetry: withLevel(253, { fuel: 'GASOLINA' }) });
  assert.equal(cells.level.dataset.state, 'reserve');
  assert.equal(cells.source.dataset.state, 'petrol');
  assert.equal(cells.source.value.textContent, 'Na gasolina');
  component.render({ telemetry: { valid: false } });
  assert.equal(cells.level.dataset.state, 'unknown');
  assert.equal(cells.level.value.textContent, '—');
  assert.equal(cells.source.dataset.state, 'none');
  assert.equal(cells.source.value.textContent, 'Sem central');
});

test('a assinatura do quadro inclui o nível (senão a barra não acompanha o medidor)', () => {
  assert.match(read('app.js'), /live\.level_raw \?\? live\.levelRaw/);
});
