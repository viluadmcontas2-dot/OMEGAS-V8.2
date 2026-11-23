const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.join(__dirname, '../src/main/assets');
const html = fs.readFileSync(path.join(root, 'dashboard.html'), 'utf8');
const script = fs.readFileSync(path.join(root, 'dashboard.js'), 'utf8');
function ui(api) {
  const nodes = {};
  function node() {
    return {
      hidden: false, disabled: false, value: '', children: [], attributes: {},
      set textContent(v) { this.content = v; this.children = []; },
      get textContent() { return (this.content || '') + this.children.map(n => n.textContent).join(' '); },
      addEventListener(k, fn) { this[k] = fn; },
      setAttribute(k, v) { this.attributes[k] = v; },
      append(...children) { this.children.push(...children); },
      replaceChildren(...children) { this.children = children; this.content = ''; },
      getBoundingClientRect() { return { width: 0, height: 0 }; }
    };
  }
  for (const [,id] of html.matchAll(/\bid="([^"]+)"/g)) nodes[id] = node();
  let tick = () => {};
  const window = { DynoNative: api, addEventListener() {} };
  const document = { getElementById: id => nodes[id], createElement: node };
  vm.runInNewContext(script, { document, window, setInterval(fn) { tick = fn; } });
  return { nodes, tick: () => tick() };
}
test('não oferece nem dispara captura sem RPM real recente', () => {
  let attempts = 0;
  const { nodes } = ui({
    getSnapshot: () => JSON.stringify({
      obd: { connected: false, rpm: '—' }, mp48: { connected: false, rpm: '—' },
      status: { capturing: false }
    }),
    startCapture: () => { attempts++; return '{"ok":true}'; }
  });
  assert.equal(nodes.start.disabled, true);
  assert.match(nodes['capture-status'].textContent, /aguardando|sem leitura|indisponível/i);
  nodes.start.click();
  assert.equal(attempts, 0);
});
test('somente RPM recente de fonte conectada habilita captura', () => {
  let payload = { obd: { connected: false, rpm: '—' }, mp48: { connected: false, rpm: '—' }, status: { capturing: false } };
  const { nodes, tick } = ui({ getSnapshot: () => JSON.stringify(payload) });
  assert.equal(nodes.start.disabled, true);
  payload = { ...payload, obd: { connected: true, rpm: 1890, ageMs: 120 } };
  tick();
  assert.equal(nodes.start.disabled, false);
  payload = { ...payload, obd: { connected: true, rpm: 1890, ageMs: 1601 } };
  tick();
  assert.equal(nodes.start.disabled, true);
});

test('botão OBD usa o adaptador pareado e botão MP48 solicita conexão USB', () => {
  const calls = [];
  const { nodes } = ui({
    getSnapshot: () => JSON.stringify({
      obd: { connected: false }, mp48: { connected: false },
      status: { capturing: false }
    }),
    listObdDevices: () => JSON.stringify([{ id: '00:11:22:33:44:55', name: 'OBDII' }]),
    connectObd: id => { calls.push('OBD:' + id); return '{"ok":true}'; },
    connectMp48: () => { calls.push('MP48'); return '{"ok":true}'; }
  });
  nodes['connect-obd'].click();
  nodes['connect-mp48'].click();
  assert.deepEqual(calls, ['OBD:00:11:22:33:44:55', 'MP48']);
  assert.equal(nodes.start.disabled, true);
});
test('sem adaptador Bluetooth pareado informa ausência e não simula conexão', () => {
  let connected = 0;
  const { nodes } = ui({
    getSnapshot: () => '{"status":{"capturing":false}}',
    listObdDevices: () => '[]',
    connectObd: () => { connected++; return '{"ok":true}'; }
  });
  nodes['connect-obd'].click();
  assert.match(nodes['action-status'].textContent, /adaptador OBD pareado/);
  assert.equal(connected, 0);
});

test('conexões ficam visíveis antes de detalhes técnicos e registro fica oculto sem RPM', () => {
  const firstDetails = html.indexOf('<details>');
  assert.ok(html.indexOf('id="connect-obd"') > 0 && html.indexOf('id="connect-obd"') < firstDetails);
  assert.ok(html.indexOf('id="connect-mp48"') > 0 && html.indexOf('id="connect-mp48"') < firstDetails);
  const { nodes } = ui({ getSnapshot: () => '{"status":{"capturing":false},"obd":{"connected":false},"mp48":{"connected":false}}' });
  assert.equal(nodes.start.hidden, true);
  assert.equal(nodes['connect-obd'].disabled, false);
  assert.equal(nodes['connect-mp48'].disabled, false);
});
test('mostra estado conectado, evita conectar repetidamente e habilita registro após RPM real', () => {
  let payload = {
    obd: { connected: false }, mp48: { connected: false }, status: { capturing: false }
  };
  const {nodes, tick} = ui({getSnapshot: () => JSON.stringify(payload)});
  payload = {
    obd: {connected: true, rpm: 1700, ageMs: 90}, mp48: {connected: false},
    status: {capturing: false, obd: 'conectado'}
  };
  tick();
  assert.equal(nodes.start.hidden, false);
  assert.equal(nodes['connect-obd'].disabled, true);
  assert.match(nodes['connect-obd'].textContent, /OBD conectado/);
  payload = {obd: {connected: false}, mp48: {connected: false}, status: {capturing: false, obd: 'conectando'}};
  tick();
  assert.equal(nodes.start.hidden, true);
  assert.equal(nodes['connect-obd'].disabled, true);
  assert.match(nodes['connect-obd'].textContent, /Conectando/);
});

test('MP48 sem USB apresenta erro confirmado, sem mensagem enganosa de solicitação', () => {
  let payload = {
    obd: { connected: false }, mp48: { connected: false },
    status: { capturing: false, mp48: 'desconectado', error: '—' }
  };
  const { nodes, tick } = ui({
    getSnapshot: () => JSON.stringify(payload),
    connectMp48: () => '{"ok":true,"pending":true}'
  });
  nodes['connect-mp48'].click();
  assert.equal(nodes['action-status'].hidden, true);
  payload = { ...payload,
    status: { capturing: false, mp48: 'desconectado',
      error: 'Adaptador USB MP48 não encontrado' } };
  tick();
  assert.match(nodes['snapshot-status'].textContent, /Conecte a interface USB/);
  assert.doesNotMatch(nodes['snapshot-status'].textContent, /solicitad/);
  assert.equal(nodes['connect-mp48'].disabled, false);
});

test('falha de socket OBD mostra recuperação em português, sem erro interno cru', () => {
  const { nodes } = ui({
    getSnapshot: () => JSON.stringify({
      obd: {connected: false}, mp48: {connected: false},
      status: {capturing: false, obd: 'desconectado',
        error: 'read failed, socket might closed or timeout, read ret: -1'}
    })
  });
  assert.match(nodes['snapshot-status'].textContent, /OBD Bluetooth não respondeu/);
  assert.match(nodes['snapshot-status'].textContent, /Reconexão automática em andamento/);
  assert.doesNotMatch(nodes['snapshot-status'].textContent, /socket might closed/);
  assert.equal(nodes['connect-obd'].disabled, false);
});
