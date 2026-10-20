'use strict';
// Harness SEM DOM: o fluxo de toque REAL do Avançado (core/advanced-flags.js, carregado em vm) falando com a bancada de ECU.
// "ciclo" = chegou um quadro de dados + o agendador do app roda os ganchos 'fast' (o que o relógio dele faria).
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createBench } = require('./ecu-bench.js');

const UI = path.join(__dirname, '../../../app/src/main/assets/ui');
const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, '../fixtures/ecu-estado-lognovo.json'), 'utf8'));

function loadFlags(mutations = []) {
  const ctx = { console, Math, JSON, Number, String, Array, Object, Set, Map };
  ctx.window = ctx; ctx.globalThis = ctx;
  vm.createContext(ctx);
  for (const file of ['core/advanced-flags-data.js', 'core/advanced-flags.js']) {
    let src = fs.readFileSync(path.join(UI, file), 'utf8');
    for (const [f, from, to] of mutations) if (f === file) { if (!src.includes(from)) throw new Error('mutação sem alvo: ' + from); src = src.replace(from, to); }
    vm.runInContext(src, ctx, { filename: file });
  }
  return ctx.OmegasUi.AdvancedFlags;
}

function makeRig({ mutations = [], bench: benchOpts, legacy = false } = {}) {
  const A = loadFlags(mutations);
  const bench = createBench(fixture, benchOpts);
  // legacy = ponte antiga em dois passos (prepare -> confirm), mantida só como reserva; o normal é a chamada única.
  if (legacy) { delete bench.autocalExtra.applyAntiStalloFlag; delete bench.autocalExtra.applyAntiStalloValues; }
  const hooks = new Set();
  const state = { route: 'tools', changes: 0 };
  const flow = A.createWriteFlow({
    bridge: bench.autocalExtra,
    addHook: (_, fn) => { hooks.add(fn); return () => hooks.delete(fn); },
    onChange: () => { state.changes += 1; },
  });
  const rig = {
    A, bench, flow, state, hooks,
    entry: () => bench.params.FLAG_CONF3,
    /** Um ciclo de dados: chega quadro (se USB ligado) e os ganchos rápidos rodam. */
    cycle(n = 1) { for (let i = 0; i < n; i += 1) { bench.deliver(); [...hooks].forEach(h => h()); } },
    observe() { flow.observe({ route: state.route, usbConnected: bench.usb }); },
    tap() { flow.tapSwitch(rig.entry(), bench.usb); },
    undo() { flow.tapUndo(); },
    writes: () => bench.ecu.log.filter(f => f.kind !== 'READ').map(f => f.hex),
    view: () => flow.view(),
    stateText() { const w = A.wordsOf(rig.entry()); return w === null ? '—' : (w[0] & A.ANTI_MASK) ? 'Ligado' : 'Desligado'; },
    flag3: () => bench.ecu.get('29 FA 00 23'),
  };
  return rig;
}
module.exports = { makeRig, loadFlags, fixture };
