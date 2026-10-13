'use strict';

// Tensão da ECU na faixa de status (pedido do dono, 2026-10-13): o GNV desliga quando a tensão passa de 14,1 V.
// A fonte é o byte 19 do canal 48 01 (0 na gasolina, ~224 no GNV; raw/16 ≈ 14 V). Escala PROVISÓRIA:
// o dono compara com a leitura do módulo OBD no celular; até lá a faixa avisa "provisório".
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const read = relative => fs.readFileSync(path.join(path.resolve('.'), 'app/src/main/assets/ui', relative), 'utf8');

function node(name) {
  return { name, dataset: {}, textContent: '', title: '', isConnected: true,
    setAttribute(k, v) { this[k] = v; }, querySelector: () => null };
}

function mount() {
  const facts = {};
  const strip = node('strip');
  strip.querySelector = selector => {
    const key = /data-vehicle-fact="([^"]+)"/.exec(selector)?.[1];
    if (!key) return null;
    facts[key] = facts[key] || (() => { const f = node('fact:' + key); const v = node('value:' + key); f.querySelector = () => v; f.value = v; return f; })();
    return facts[key];
  };
  const document = { getElementById: id => (id === 'vehicleStatusStrip' ? strip : null), querySelector: () => ({}) };
  const context = { console, document, setTimeout: () => 1 };
  context.window = context;
  vm.createContext(context);
  vm.runInContext(read('components/vehicle-status-strip.js'), context, { filename: 'vehicle-status-strip.js' });
  const store = { state: {}, get: () => store.state, subscribe: () => () => {} };
  return { component: new context.OmegasUi.VehicleStatusStrip({ store }), facts };
}

const frame = raw19 => ({
  status: { serviceRunning: true, usbConnected: true, engineReady: true },
  telemetry: { valid: true, ageMs: 40, live: { rpm: 2000, petrol_ms: 4, load_bar: 0.5, fuel: 'GNV', unknown_raw_19: raw19 } },
});

test('o HTML da faixa tem o campo de tensão da ECU, marcado como provisório', () => {
  assert.match(read('components/vehicle-status-strip.js'), /data-vehicle-fact="voltage"/);
  assert.match(read('components/vehicle-status-strip.js'), /provis/i);
});

test('tensão = byte 19 / 16, uma casa; 0 (gasolina) e ausente são "—", nunca 0 V', () => {
  const { component, facts } = mount();
  component.render(frame(224));
  assert.equal(facts.voltage.value.textContent, '14,0 V');
  component.render(frame(0));
  assert.equal(facts.voltage.value.textContent, '—');
  assert.equal(facts.voltage.dataset.state, 'unknown');
  component.render(frame(undefined));
  assert.equal(facts.voltage.value.textContent, '—');
});

test('a partir de 14,1 V o campo entra em alerta; abaixo disso não', () => {
  const { component, facts } = mount();
  component.render(frame(225));      // 14,06 V
  assert.notEqual(facts.voltage.dataset.state, 'alert');
  component.render(frame(226));      // 14,13 V
  assert.equal(facts.voltage.dataset.state, 'alert');
});

test('telemetria inválida não mostra tensão', () => {
  const { component, facts } = mount();
  component.render({ status: {}, telemetry: { valid: false } });
  assert.equal(facts.voltage.value.textContent, '—');
});
