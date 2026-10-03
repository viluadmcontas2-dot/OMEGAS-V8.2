'use strict';
// Fatia H-escrita. Classe de prova 1 (contrato) e 2 (sintético): Desfazer honesto, mapa sem "Gravado" falso,
// transporte × ECU, equivalência roteada, presentSnapshot sem serialização repetida, CSS sem filtros pesados.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = path.join(__dirname, '../..');
const UI = path.join(ROOT, 'app/src/main/assets/ui');
const read = rel => fs.readFileSync(path.join(UI, rel), 'utf8');

function fakeDom() {
  const nodes = new Map();
  const make = id => {
    const sub = new Map();
    const node = {
      id, textContent: '', hidden: false, disabled: false, value: '', innerHTML: '', dataset: {}, style: {},
      classes: new Set(),
      classList: {
        add: (...c) => c.forEach(x => node.classes.add(x)),
        remove: (...c) => c.forEach(x => node.classes.delete(x)),
        toggle: (c, on) => { if (on) node.classes.add(c); else node.classes.delete(c); },
      },
      addEventListener() {},
      querySelector: sel => { if (!sub.has(sel)) sub.set(sel, { textContent: '' }); return sub.get(sel); },
      querySelectorAll: () => [],
      setAttribute() {},
      removeAttribute() {},
    };
    return node;
  };
  const document = {
    getElementById: id => { if (!nodes.has(id)) nodes.set(id, make(id)); return nodes.get(id); },
    querySelector: () => null,
    querySelectorAll: () => [],
    createElement: () => make('created'),
    head: { appendChild() {} },
    addEventListener() {},
  };
  return { document, node: document.getElementById };
}

function loadInto(context, files) {
  vm.createContext(context);
  context.window = context;
  context.globalThis = context;
  for (const file of files) vm.runInContext(read(file), context, { filename: file });
  return context;
}

function fakeStore(extra) {
  const state = { alert: null, curve: {}, map: { state: 'ready' }, ...extra };
  return {
    state,
    get: () => state,
    patch(patch) { Object.assign(state, patch); },
  };
}

// ---------------------------------------------------------------- 1. regra 7: transporte ≠ ECU
test('failureText separa Cabo/USB de "A ECU recusou" e mantém a mensagem do app', () => {
  const ctx = loadInto({ console }, ['core/display-rules.js']);
  const R = ctx.OmegasUi.DisplayRules;
  assert.equal(R.failureText({ failureKind: 'TRANSPORTE', error: 'USB desconectado' }), 'Cabo/USB: USB desconectado');
  assert.equal(R.failureText({ failureKind: 'ECU', error: 'ECU retornou status 0xCA' }), 'A ECU recusou: ECU retornou status 0xCA');
  assert.equal(R.failureText({ failureKind: 'APP', error: 'A curva da ECU mudou' }), 'A curva da ECU mudou');
  assert.equal(R.failureText({ failure: { failureKind: 'TRANSPORTE', error: 'Timeout' } }), 'Cabo/USB: Timeout');
  assert.equal(R.failureText({}, 'Releitura obrigatória.'), 'Releitura obrigatória.');
  assert.equal(R.failureKind({ failureKind: 'qualquer' }), 'APP');
});

// ---------------------------------------------------------------- 2. Curva K: Desfazer restaura A FOTO desta operação
function curveHarness(api) {
  const { document, node } = fakeDom();
  const ctx = loadInto({ console, document }, ['core/display-rules.js', 'screens/curve.js']);
  const store = fakeStore();
  const screen = new ctx.OmegasUi.CurveScreen(store, api);
  screen.renderChart = () => {};
  screen.renderProposalList = () => {};
  screen.refreshBackups = () => {};
  return { screen, node, store, ctx };
}
const neutralPoints = () => Array.from({ length: 30 }, (_, index) => ({ index, petrolMs: 2 + index, factor: 1, factorRaw: 16384 }));
const bentPoints = () => neutralPoints().map(p => (p.index === 3 ? { ...p, factor: 1.2, factorRaw: 19661 } : p));

test('Desfazer após gravar restaura a foto de lastOperation, não o backup mais novo', () => {
  const calls = { restorePrepared: [] };
  const operation = { busy: false, state: 'BATCH_CONFIRMED', readbackValid: true, photoFile: 'MANUAL-111-aaaa1111.json' };
  const api = {
    curveOperation: () => operation,
    startCurveRead: () => ({ ok: true, started: true }),
    // um backup MAIS NOVO e sem relação existe: o Desfazer antigo o escolheria
    curveBackups: () => [{ fileName: 'MANUAL-999-zzzz9999.json', createdAt: 999 }, { fileName: 'MANUAL-111-aaaa1111.json', createdAt: 111 }],
    prepareCurveRestore: file => { calls.restorePrepared.push(file); return { ok: true, started: true }; },
  };
  const { screen, node } = curveHarness(api);
  screen.writing = true;
  screen.writeKind = 'write';
  screen.poll();
  assert.equal(screen.undoFile, 'MANUAL-111-aaaa1111.json');
  assert.equal(node('curveUndoButton').hidden, false);
  screen.reading = false; // a releitura que segue a gravação já terminou quando o dono toca
  screen.undoLast();
  assert.deepEqual(calls.restorePrepared, ['MANUAL-111-aaaa1111.json']);
});

test('sem photoFile (Kotlin antigo) não há Desfazer: nunca restaura uma foto qualquer', () => {
  const api = {
    curveOperation: () => ({ busy: false, state: 'BATCH_CONFIRMED', readbackValid: true }),
    startCurveRead: () => ({ ok: true, started: true }),
    curveBackups: () => [{ fileName: 'MANUAL-999-zzzz9999.json', createdAt: 999 }],
    prepareCurveRestore: () => { throw new Error('não deveria restaurar'); },
  };
  const { screen, node } = curveHarness(api);
  screen.writing = true;
  screen.writeKind = 'write';
  screen.poll();
  assert.equal(node('curveUndoButton').hidden, true);
  screen.undoLast();
});

test('reset em curva já neutra: sem foto e sem reset; curva torta: foto → reset; Desfazer = foto do reset', () => {
  const calls = { photo: 0, reset: 0, prepared: [] };
  let operation = { busy: false, state: 'COMPLETED', ok: true, hash: 'h', publicPath: 'Download/Omegas/x', fileName: 'MANUAL-5-bbbb2222.json' };
  const api = {
    startCurveBackup: () => { calls.photo += 1; return { ok: true, started: true }; },
    resetCurve: () => { calls.reset += 1; return { ok: true, started: true }; },
    curveOperation: () => operation,
    startCurveRead: () => ({ ok: true, started: true }),
    curveBackups: () => [],
    prepareCurveRestore: file => { calls.prepared.push(file); return { ok: true, started: true }; },
  };
  const { screen } = curveHarness(api);

  screen.data = { points: neutralPoints() };
  screen.resetCurve();
  assert.equal(calls.photo, 0, 'curva neutra: nada a zerar, nenhuma foto');
  assert.equal(calls.reset, 0);

  screen.data = { points: bentPoints() };
  screen.resetCurve();
  assert.equal(calls.photo, 1);
  assert.equal(screen.backupTask, 'reset-photo');
  screen.poll(); // foto confirmada → só então zera
  assert.equal(screen.resetPhotoFile, 'MANUAL-5-bbbb2222.json');
  assert.equal(calls.reset, 1);
  assert.equal(screen.writing, true);

  // o Kotlin também fotografa a curva no escritor; mesmo assim o Desfazer do reset é a foto do reset
  operation = { busy: false, state: 'BATCH_CONFIRMED', readbackValid: true, photoFile: 'MANUAL-6-cccc3333.json' };
  screen.poll();
  assert.equal(screen.undoFile, 'MANUAL-5-bbbb2222.json');
  screen.reading = false;
  screen.undoLast();
  assert.deepEqual(calls.prepared, ['MANUAL-5-bbbb2222.json']);
});

test('reset sem fileName na foto não zera nada', () => {
  const calls = { reset: 0 };
  const api = {
    startCurveBackup: () => ({ ok: true, started: true }),
    resetCurve: () => { calls.reset += 1; return { ok: true, started: true }; },
    curveOperation: () => ({ busy: false, state: 'COMPLETED', ok: true, hash: 'h', publicPath: 'p' }),
    curveBackups: () => [],
  };
  const { screen } = curveHarness(api);
  screen.data = { points: bentPoints() };
  screen.resetCurve();
  screen.poll();
  assert.equal(calls.reset, 0);
});

test('falha parcial mostra Desfazer (foto de antes) e o texto distingue cabo × ECU', () => {
  const operation = {
    busy: false, state: 'CURVE_WRITE_FAILED', ok: false, partial: true, mutationMayHaveStarted: true,
    photoFile: 'MANUAL-7-dddd4444.json', failureKind: 'TRANSPORTE', error: 'USB desconectado',
  };
  const api = { curveOperation: () => operation, curveBackups: () => [], startCurveRead: () => ({ ok: true, started: true }) };
  const { screen, node } = curveHarness(api);
  screen.writing = true;
  screen.writeKind = 'write';
  screen.poll();
  assert.equal(node('curveUndoButton').hidden, false);
  assert.equal(screen.undoFile, 'MANUAL-7-dddd4444.json');
  const result = node('curveOperationResult');
  assert.equal(result.querySelector('b').textContent, 'ECU parcialmente alterada');
  assert.equal(result.querySelector('span').textContent, 'Cabo/USB: USB desconectado');

  // falha sem nenhuma alteração possível: sem Desfazer
  const clean = { busy: false, state: 'CURVE_WRITE_FAILED', ok: false, failureKind: 'ECU', error: 'ECU retornou status 0xCA' };
  const second = curveHarness({ curveOperation: () => clean, curveBackups: () => [] });
  second.screen.writing = true;
  second.screen.poll();
  assert.equal(second.node('curveUndoButton').hidden, true);
  assert.equal(second.node('curveOperationResult').querySelector('span').textContent, 'A ECU recusou: ECU retornou status 0xCA');
});

test('restauração grava pelo caminho que confere a foto (restoreCurve), não pelo lote comum', () => {
  const calls = { restore: [], write: 0 };
  const api = {
    writeCurve: () => { calls.write += 1; return { ok: true, started: true }; },
    restoreCurve: (points, file) => { calls.restore.push([points.length, file]); return { ok: true, started: true }; },
  };
  const { screen } = curveHarness(api);
  screen.restoreContext = { fileName: 'MANUAL-5-bbbb2222.json' };
  screen.proposals.set(3, { index: 3, currentRaw: 19661, targetRaw: 16384 });
  screen.writePrepared();
  assert.deepEqual(calls.restore, [[1, 'MANUAL-5-bbbb2222.json']]);
  assert.equal(calls.write, 0);
});

test('AutoCal "Resetar Curva K" abre a Curva K com resetNow (foto antes, um toque) e a Curva K só zera depois da leitura', () => {
  const cockpit = read('screens/autocal-cockpit.js');
  assert.match(cockpit, /action === 'RESET_K_FACTOR' && this\.resetViaCurve\(\)/);
  assert.match(cockpit, /router\.open\('curve', 'editor', \{ resetNow: true \}\)/);
  const calls = { photo: 0 };
  const api = {
    startCurveBackup: () => { calls.photo += 1; return { ok: true, started: true }; },
    curveBackups: () => [],
  };
  const { screen } = curveHarness(api);
  screen.data = { points: bentPoints() };
  screen.onEnter({ resetNow: true, subpage: 'editor' });
  assert.equal(calls.photo, 1, 'a foto é o primeiro passo, no mesmo toque');
  assert.equal(screen.pendingReset, false);
});

// ---------------------------------------------------------------- 3. Mapa K
function mapHarness(api) {
  const { document, node } = fakeDom();
  const ctx = loadInto({ console, document }, ['map-editor.js', 'core/display-rules.js', 'screens/map.js']);
  const store = fakeStore();
  const screen = new ctx.OmegasUi.MapScreen(store, api, null);
  screen.renderEditor = () => {};
  return { screen, node, store };
}

test('Mapa: slot compartilhado com BATCH_CONFIRMED velho NÃO vira "Gravado" fora do estado writing', () => {
  const calls = { read: 0 };
  const stale = { ok: true, state: 'BATCH_CONFIRMED', readbackValid: true, busy: false, confirmedCells: 5, totalCells: 5, adjustmentIds: ['ADJ-1'] };
  const api = { mapWriteOperation: () => stale, startMapRead: () => { calls.read += 1; return { ok: true, started: true }; } };
  const { screen, node, store } = mapHarness(api);
  store.state.map = { state: 'ready' };
  screen.pollWrite();
  assert.equal(calls.read, 0);
  assert.equal(node('mapOperationResult').querySelector('b').textContent, '');
  assert.equal(screen.undoId, '');

  store.state.map = { state: 'writing' };
  screen.pollWrite();
  assert.equal(calls.read, 1, 'em writing o resultado é processado');
  assert.equal(screen.undoId, 'ADJ-1');
  assert.equal(node('mapUndoButton').hidden, false);
});

test('Mapa: falha parcial diz "ECU parcialmente alterada", conta células com ACK e oferece reler e desfazer', () => {
  const operation = {
    ok: false, state: 'BATCH_PARTIAL_FAILED', busy: false, confirmedCells: 37, totalCells: 144,
    ecuPartiallyChanged: true, partial: true, failureKind: 'ECU', error: 'ECU retornou status 0xCA',
    adjustmentIds: ['ADJ-9'],
  };
  const api = { mapWriteOperation: () => operation };
  const { screen, node, store } = mapHarness(api);
  store.state.map = { state: 'writing' };
  screen.pollWrite();
  const result = node('mapOperationResult');
  assert.equal(result.querySelector('b').textContent, 'ECU parcialmente alterada');
  assert.match(result.querySelector('span').textContent, /37 células já receberam o novo valor/);
  assert.match(result.querySelector('span').textContent, /A ECU recusou: ECU retornou status 0xCA/);
  assert.equal(node('mapRereadButton').hidden, false);
  assert.equal(node('mapUndoButton').hidden, false);
  assert.equal(screen.undoId, 'ADJ-9');
  assert.equal(store.state.map.state, 'failed');
});

test('Mapa: Desfazer relê, calcula o que volta e grava pelo escritor em lote (um toque)', () => {
  const calls = { prepared: [], restored: [] };
  let operation = { ok: true, state: 'COMPLETED', busy: false, cells: [{ row: 1, column: 2, current: 130, target: 120 }] };
  const api = {
    prepareMapRestore: id => { calls.prepared.push(id); return { ok: true, started: true }; },
    restoreMap: (cells, id) => { calls.restored.push([cells.length, id]); return { ok: true, started: true }; },
    mapWriteOperation: () => operation,
  };
  const { screen, store } = mapHarness(api);
  screen.undoId = 'ADJ-3';
  screen.undoLast();
  assert.deepEqual(calls.prepared, ['ADJ-3']);
  assert.equal(store.state.map.state, 'writing');
  screen.pollWrite();
  assert.deepEqual(calls.restored, [[1, 'ADJ-3']]);
  assert.equal(screen.restorePhase, 'writing');
  operation = { ok: true, state: 'BATCH_CONFIRMED', readbackValid: true, busy: false, confirmedCells: 1, totalCells: 1, adjustmentIds: ['ADJ-4'] };
  api.startMapRead = () => ({ ok: true, started: true });
  screen.pollWrite();
  assert.equal(screen.undoId, '', 'depois de desfazer não se oferece desfazer o desfazer com a foto antiga');
});

// ---------------------------------------------------------------- 4. PresentSnapshot e equivalência
function nativeApi(extraGlobals) {
  const ctx = loadInto({ console, ...extraGlobals }, ['core/native-api.js']);
  return new ctx.OmegasUi.NativeApi();
}

test('presentSnapshot: sem mudança usa getPresentSnapshotIfChanged e nunca monta o quadro completo', () => {
  const counts = { full: 0, changed: 0, args: [] };
  const native = {
    getPresentSnapshot: () => { counts.full += 1; return JSON.stringify({ ok: true, revision: 1, data: { sequence: 7 } }); },
    getPresentSnapshotIfChanged: seq => {
      counts.changed += 1; counts.args.push(seq);
      return seq === 7 ? JSON.stringify({ ok: true, changed: false, sequence: 7, telemetryAgeMs: 120 }) : JSON.stringify({ ok: true, revision: 2, data: { sequence: 8 } });
    },
  };
  const api = nativeApi({ OmegasNative: native });
  const first = api.presentSnapshot(-1);
  assert.equal(first.data.sequence, 7);
  assert.equal(counts.full, 1, 'primeira vez: quadro inteiro');
  const unchanged = api.presentSnapshot(7);
  assert.equal(unchanged.changed, false);
  assert.equal(unchanged.telemetryAgeMs, 120);
  assert.equal(counts.full, 1, 'quadro igual: nada de serializar de novo');
  assert.deepEqual(counts.args, [7]);
  assert.equal(api.presentSnapshot(3).data.sequence, 8);
});

test('app.js: pede o quadro com a última sequência, reaproveita o anterior quando nada mudou e AutoCal faz poll de 50 ms (caminho sem mudança quase de graça)', () => {
  const app = read('app.js');
  assert.match(app, /api\.presentSnapshot\(lastPresentSequence\)/);
  assert.match(app, /envelope\.changed === false/);
  assert.match(app, /const AUTOCAL_CADENCE_MS = 50;/);
  assert.match(app, /setCadenceMs\(route === 'autocal' \? AUTOCAL_CADENCE_MS : 200\)/);
  assert.doesNotMatch(app, /\? 50 : 200/);
});

test('equivalência: lê pela ponte AutoCal e, sem nextAction, deriva a ação da fase (botão leva ao Refino)', () => {
  const body = { ok: true, available: true, autopilot: { phase: 'PROPOSTA_PRONTA' }, ratio: 1.02, refinement: {} };
  const api = nativeApi({ OmegasNative: {}, OmegasAutoCal: { getEquivalence: () => JSON.stringify(body) } });
  const eq = api.equivalence();
  assert.ok(eq, 'a ponte AutoCal alimenta o cartão do Agora');
  assert.equal(eq.index.value, null, 'sem índice do Kotlin, nenhum número inventado');
  assert.equal(eq.nextAction.route, 'refino');
  assert.match(eq.nextAction.text, /Revise e grave no Refino/);

  const withAction = { ...body, nextAction: { kind: 'COLLECT', text: 'Rode no GNV', route: 'refino', subpage: '', pointIndexes: [] }, index: { value: 0.5, coverage: 4, provisional: true } };
  const real = nativeApi({ OmegasNative: {}, OmegasAutoCal: { getEquivalence: () => JSON.stringify(withAction) } }).equivalence();
  assert.equal(real.nextAction.text, 'Rode no GNV', 'o nextAction do Kotlin prevalece sobre o derivado');
  assert.equal(real.index.value, 0.5);

  assert.equal(nativeApi({ OmegasNative: {}, OmegasAutoCal: { getEquivalence: () => JSON.stringify({ ok: true, available: false }) } }).equivalence(), null);
  assert.equal(nativeApi({ OmegasNative: {} }).equivalence(), null);
});

test('Agora mostra o cartão com "—" quando só há a ação do piloto', () => {
  const { document, node } = fakeDom();
  const dash = loadInto({ console, document, OmegasApp: { router: { open() {} } } }, ['core/router.js']);
  vm.runInContext(read('screens/dashboard.js'), dash, { filename: 'screens/dashboard.js' });
  const screen = Object.create(dash.OmegasUi.DashboardScreen.prototype);
  const eq = { index: { value: null, coverage: null, provisional: false }, nextAction: { kind: 'REVIEW', text: 'A curva refinada está pronta.', route: 'refino', subpage: '', pointIndexes: [] } };
  screen.renderEquivalence(eq);
  assert.equal(node('dashEquivalence').hidden, false);
  assert.equal(node('dashIndex').textContent, '—');
  assert.equal(node('dashNextText').textContent, 'A curva refinada está pronta.');
  assert.equal(node('dashNextButton').dataset.route, 'refino');
});

// ---------------------------------------------------------------- 5. CSS sem filtros caros
function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    if (fs.statSync(full).isDirectory()) walk(full, out); else out.push(full);
  }
  return out;
}
test('CSS da UI não usa drop-shadow nem backdrop-filter (fundos planos)', () => {
  for (const file of walk(UI).filter(f => f.endsWith('.css'))) {
    const css = fs.readFileSync(file, 'utf8');
    assert.doesNotMatch(css, /drop-shadow\(/, path.basename(file));
    assert.doesNotMatch(css, /backdrop-filter/, path.basename(file));
  }
});

test('cursor ao vivo do AutoCal não repinta quando nada mudou', () => {
  const cockpit = read('screens/autocal-cockpit.js');
  const body = cockpit.slice(cockpit.indexOf('renderLiveCursor() {'), cockpit.indexOf('renderZoneMeter(human) {'));
  assert.match(body, /this\.cursorSeen/);
  assert.ok(body.indexOf('this.cursorSeen') < body.indexOf('this.renderLiveNarrative()'), 'a assinatura vem antes de qualquer trabalho');
});

// ---------------------------------------------------------------- 6. sessões e ferramentas
test('Sessões lê blackouts que o Kotlin emite e Ferramentas não amarra ids que não existem', () => {
  const ctx = loadInto({ console }, ['core/display-rules.js', 'screens/sessions.js']);
  const row = ctx.OmegasUi.SessionsModel.sessionRow({ id: 's', semanticSummary: { blackouts: 3 } });
  assert.equal(row.blackouts, 3);
  const drawers = read('components/drawers.js');
  assert.doesNotMatch(drawers, /getElementById\('toolExportLogs'\)|getElementById\('toolSelfTest'\)/);
  assert.match(drawers, /data-tool-export-logs/);
  assert.match(drawers, /data-tool-selftest/);
});
