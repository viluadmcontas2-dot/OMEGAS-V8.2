const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const base = require('node:path').join(__dirname, '../src/main/assets');
const html = fs.readFileSync(base + '/dashboard.html', 'utf8');
const script = fs.readFileSync(base + '/dashboard.js', 'utf8');

function load(api = {}) {
  function element() {
    return {
      children: [], value: '', hidden: false, attributes: {},
      set textContent(value) { this.content = value; this.children = []; },
      get textContent() { return (this.content || '') + this.children.map(child => child.textContent).join(' '); },
      addEventListener(type, callback) { this[type] = callback; },
      setAttribute(name, value) { this.attributes[name] = value; },
      replaceChildren(...items) { this.children = items; this.content = ''; },
      append(...items) { this.children.push(...items); },
      getBoundingClientRect() { return { width: 0, height: 0 }; }
    };
  }
  const nodes = {};
  for (const [, id] of html.matchAll(/\bid="([^"]+)"/g)) {
    nodes[id] = element();
  }
  const window = { DynoNative: api, addEventListener() {} };
  const document = {
    getElementById: id => nodes[id],
    createElement: element
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
      fusion: { matchedEvents: 1, lagMs: 0, advice: 'Avaliar curva' },
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

test('preserva captura, busca e exportação pelo bridge', () => {
  const calls = [];
  let capturing = false;
  const { nodes, tick } = load({
    getSnapshot: () => JSON.stringify({ status: { capturing }, obd: { connected: true, rpm: 1400, ageMs: 100 } }),
    listObdDevices: () => { calls.push('listObdDevices'); return '[]'; },
    connectObd: () => { calls.push('connectObd'); return '{"ok":true,"pending":true,"error":"—"}'; },
    connectMp48: () => { calls.push('connectMp48'); return '{"ok":true,"pending":true,"error":"—"}'; },
    startCapture: () => { capturing = true; calls.push('start'); return '{"ok":true,"pending":true,"error":"—"}'; },
    stopCapture: () => { capturing = false; calls.push('stop'); return '{"ok":true,"pending":true,"error":"—"}'; },
    getVault: () => '[{"type":"Ignição"},{"type":"Troca GNV"}]',
    exportVault: () => { calls.push('export'); return '{"ok":true}'; }
  });
  for (const id of ['connect-obd', 'connect-mp48']) {
    assert.equal(typeof nodes[id].click, 'function');
  }
  assert.match(html, /Leitura ativa sob demanda/);
  assert.match(html, /sem alterar mapas ou calibração/);
  assert.equal(nodes['mp48-rpm'].textContent, '—');
  assert.equal(nodes['obd-rpm'].textContent, '1.400 rpm');
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

test('lista sessões reais com evidências, estado humano e busca por palavras', () => {
  const { nodes } = load({
    getSnapshot: () => '{"mp48":{"connected":false}}',
    getVault: () => JSON.stringify([
      { id: 987654, started_at_ms: new Date(2026, 10, 21, 12).getTime(), ended_at_ms: new Date(2026, 10, 21, 13).getTime(),
        state: 'complete', observation_count: 4 },
      { id: 765432, state: 'capturing_passive', observation_count: 0 }
    ])
  });
  nodes['tab-vault'].click();
  assert.match(nodes.events.textContent, /Esta viagem foi observada.*21\/11\/2026.*4 amostras.*Encerrada/);
  assert.match(nodes.events.textContent, /Em andamento.*Escuta passiva/);
  assert.match(nodes.events.textContent, /Ainda não há amostras/);
  const first = nodes.events.children[0];
  assert.equal(first.children[0].children[0].textContent.includes('987654'), false);
  assert.match(first.textContent, /Identificador da captura.*987654/);
  nodes.search.value = 'viagem encerrada';
  nodes.search.input();
  assert.equal(nodes.events.children.length, 1);
  nodes.search.value = '987654';
  nodes.search.input();
  assert.equal(nodes.events.children.length, 0);
});

test('distingue vazio, histórias de leitura pendente e relações conceituais interativas', () => {
  const { nodes } = load({
    getSnapshot: () => '{"mp48":{"connected":false},"obd":{"connected":false}}',
    getVault: () => '[]'
  });
  nodes['tab-vault'].click();
  assert.match(nodes['vault-status'].textContent, /Nenhuma captura salva/);
  assert.equal(nodes.events.children.length, 0);
  assert.match(nodes.stories.textContent, /A central do gás ainda não transmitiu/);
  assert.match(nodes.stories.textContent, /Aguardando leitura comparável/);
  assert.equal(nodes['graph-title'].textContent, 'Como analisaremos, não é medição');
  const edge = nodes['graph-edges'].children[0].children[0];
  assert.match(edge.textContent, /Gasolina.*→.*Resposta da aceleração/);
  edge.click();
  assert.match(nodes['graph-detail'].textContent, /Conceito.*Gasolina.*Resposta da aceleração/);
});

test('apresenta o grafo da ponte com evidência explícita e mantém relações como relações', () => {
  const { nodes } = load({
    getSnapshot: () => '{}',
    getVault: () => '[]',
    getKnowledgeGraph: () => JSON.stringify({
      summary: 'Uma captura analisada',
      nodes: [
        { id: 'a', title: 'Rotação', description: 'Registrada na viagem', evidenceCount: 2, observed: true },
        { id: 'b', title: 'Curva K', description: 'Ainda em avaliação', observed: false }
      ],
      edges: [{ from: 'a', to: 'b', label: 'Ajuda a avaliar' }]
    })
  });
  nodes['tab-vault'].click();
  assert.match(nodes['graph-summary'].textContent, /Uma captura analisada/);
  nodes['graph-nodes'].children[0].click();
  assert.match(nodes['graph-detail'].textContent, /Observado.*2 evidências.*Registrada na viagem/);
  nodes['graph-edges'].children[0].children[0].click();
  assert.match(nodes['graph-detail'].textContent, /Conceito.*Ajuda a avaliar/);
});

test('uma leitura pareada sem trecho comparável não libera sugestão de Curva K', () => {
  const { nodes } = load({
    getSnapshot: () => JSON.stringify({
      fusion: { matchedEvents: 0, comparison: { fuel: 'GNV', obdRpm: 1500 }, advice: 'Ajustar' }
    })
  });
  assert.match(nodes['comparison-title'].textContent, /Aguardando leitura comparável/);
  assert.match(nodes.advice.textContent, /Ainda não podemos recomendar/);
});
