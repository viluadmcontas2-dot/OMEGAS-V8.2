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

test('mantém a UI passiva e preserva captura, busca e exportação pelo bridge', () => {
  const calls = [];
  let capturing = false;
  const { nodes, tick } = load({
    getSnapshot: () => JSON.stringify({ status: { capturing } }),
    listObdDevices: () => { calls.push('listObdDevices'); return '[]'; },
    connectObd: () => { calls.push('connectObd'); return '{"ok":true,"pending":true,"error":"—"}'; },
    connectMp48: () => { calls.push('connectMp48'); return '{"ok":true,"pending":true,"error":"—"}'; },
    startCapture: () => { capturing = true; calls.push('start'); return '{"ok":true,"pending":true,"error":"—"}'; },
    stopCapture: () => { capturing = false; calls.push('stop'); return '{"ok":true,"pending":true,"error":"—"}'; },
    getVault: () => '[{"type":"Ignição"},{"type":"Troca GNV"}]',
    exportVault: () => { calls.push('export'); return '{"ok":true}'; }
  });
  for (const id of ['connect-obd', 'connect-mp48']) {
    assert.match(html, new RegExp('<button\\b[^>]*id="' + id + '"[^>]*\\bdisabled\\b'));
    assert.equal(nodes[id].click, undefined);
  }
  assert.match(html, /Somente recepção passiva/);
  assert.match(html, /Sem enviar comandos, o painel não consulta OBD nem MP48/);
  assert.match(html, /fonte externa que já envie os dados/);
  assert.equal(nodes['mp48-rpm'].textContent, '—');
  assert.equal(nodes['obd-rpm'].textContent, '—');
  assert.equal(nodes['scan-obd'], undefined);
  tick();
  nodes['tab-compare'].click();
  nodes['tab-live'].click();
  nodes.start.click();
  assert.equal(nodes['action-status'].textContent, 'Solicitação de captura enviada');
  nodes.stop.click();
  nodes['tab-vault'].click();
  nodes.search.value = 'ignicao';
  nodes.search.input();
  assert.equal(nodes.events.children.length, 1);
  assert.match(nodes.events.children[0].textContent, /Ignição/);
  nodes.export.click();
  assert.equal(nodes['action-status'].textContent, 'Solicitação de exportação enviada');
  assert.deepEqual(calls, ['start', 'stop', 'export']);
});
