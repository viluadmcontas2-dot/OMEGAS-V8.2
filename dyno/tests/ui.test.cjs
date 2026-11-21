const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const base = require('node:path').join(__dirname, '../src/main/assets');
const html = fs.readFileSync(base + '/dashboard.html', 'utf8');
const script = fs.readFileSync(base + '/dashboard.js', 'utf8');

function load(api = {}) {
  const nodes = {};
  for (const [, id] of html.matchAll(/\bid="([^"]+)"/g)) {
    nodes[id] = {
      children: [], value: '', textContent: '', hidden: false,
      addEventListener(type, callback) { this[type] = callback; },
      setAttribute() {},
      replaceChildren(...items) { this.children = items; },
      append(item) { this.children.push(item); },
      getBoundingClientRect() { return { width: 0, height: 0 }; }
    };
  }
  const window = { DynoNative: api, addEventListener() {} };
  const document = {
    getElementById: id => nodes[id],
    createElement: () => ({ textContent: '', value: '' })
  };
  let tick;
  vm.runInNewContext(script, { window, document, setInterval(fn, ms) {
    assert.equal(ms, 500);
    tick = fn;
  } });
  return { nodes, tick };
}

test('renderiza telemetria real, preserva zero e limpa em caso de falha', () => {
  let good = true;
  const { nodes, tick } = load({
    getSnapshot: () => good ? JSON.stringify({
      mp48: { connected: true, rpm: 1234, gasMs: 0 },
      obd: { connected: false, tps: 47 },
      fusion: { lagMs: 0, advice: 'Avaliar curva' },
      status: { capturing: true }
    }) : '{'
  });
  assert.match(nodes['mp48-rpm'].textContent, /234/);
  assert.equal(nodes['mp48-gas'].textContent, '0 ms');
  assert.equal(nodes['obd-tps'].textContent, '—');
  assert.equal(nodes.lag.textContent, '0 ms');
  assert.equal(nodes.advice.textContent, 'Avaliar curva');
  assert.equal(nodes.start.disabled, true);
  good = false;
  tick();
  assert.equal(nodes['mp48-rpm'].textContent, '—');
  assert.match(nodes['snapshot-status'].textContent, /snapshot/i);
});

test('aciona conexões, captura, busca e exportação pelo bridge', () => {
  const calls = [];
  let capturing = false;
  const { nodes } = load({
    getSnapshot: () => JSON.stringify({ status: { capturing } }),
    listObdDevices: () => '[{"id":"a","name":"Adaptador A"}]',
    connectObd: id => { calls.push(id); return '{"ok":true}'; },
    connectMp48: () => { calls.push('mp48'); return '{"ok":false,"error":"USB indisponível"}'; },
    startCapture: () => { capturing = true; calls.push('start'); return '{"ok":true}'; },
    stopCapture: () => { capturing = false; calls.push('stop'); return '{"ok":true}'; },
    getVault: () => '[{"type":"Ignição"},{"type":"Troca GNV"}]',
    exportVault: () => { calls.push('export'); return '{"ok":true}'; }
  });
  nodes['scan-obd'].click();
  assert.equal(nodes.devices.children[1].textContent, 'Adaptador A');
  nodes.devices.value = 'a';
  nodes.devices.change();
  nodes['connect-obd'].click();
  nodes['connect-mp48'].click();
  assert.equal(nodes['action-status'].textContent, 'USB indisponível');
  nodes.start.click();
  nodes.stop.click();
  nodes['tab-vault'].click();
  nodes.search.value = 'ignicao';
  nodes.search.input();
  assert.equal(nodes.events.children.length, 1);
  assert.match(nodes.events.children[0].textContent, /Ignição/);
  nodes.export.click();
  assert.deepEqual(calls, ['a', 'mp48', 'start', 'stop', 'export']);
});
