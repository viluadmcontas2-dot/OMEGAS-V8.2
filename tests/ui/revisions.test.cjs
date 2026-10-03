'use strict';
// Guardião (item 3): a UI relê evidência/tabelas/sessão só quando a revisão do tipo andou, com vigia de >= 2 s.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = path.resolve(__dirname, '../..');
const read = relative => fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui', relative), 'utf8');

function load() {
  const window = { console };
  window.window = window;
  window.globalThis = window;
  vm.createContext(window);
  vm.runInContext(read('core/revisions.js'), window, { filename: 'core/revisions.js' });
  return window;
}

test('revisão que não andou não pede releitura; o vigia vence em 2,5 s', () => {
  const w = load();
  const R = w.OmegasUi.Revisions;
  assert.ok(R.WATCHDOG_MS >= 2000, 'o poll de vigia nunca fica abaixo de 2 s');
  R.noteAll({ live: 10, evidence: 1, tables: 1, session: 1 });
  const gate = R.gate(R.SLOW_KINDS, R.WATCHDOG_MS);
  assert.equal(gate.due(false, 0), true, 'primeira vez sempre relê');
  gate.mark(0);
  R.noteAll({ live: 11, evidence: 1, tables: 1, session: 1 });
  assert.equal(gate.due(false, 500), false, 'só o ao vivo andou: não relê evidência');
  assert.equal(gate.due(false, 2499), false);
  assert.equal(gate.due(false, 2500), true, 'o vigia relê mesmo sem revisão nova (empurrão perdido)');
});

test('qualquer revisão lenta que anda libera a releitura; ocupado relê sempre', () => {
  const w = load();
  const R = w.OmegasUi.Revisions;
  R.noteAll({ live: 1, evidence: 1, tables: 1, session: 1 });
  for (const kind of ['evidence', 'tables', 'session']) {
    const gate = R.gate(R.SLOW_KINDS, 2500);
    gate.mark(0);
    assert.equal(gate.due(false, 10), false);
    R.note(kind, 99);
    assert.equal(gate.due(false, 20), true, `${kind} andou`);
    R.note(kind, 99);
  }
  const gate = R.gate(R.SLOW_KINDS, 2500);
  gate.mark(0);
  assert.equal(gate.due(true, 1), true, 'com operação em curso relê sempre');
});

test('empurrão OmegasOnRevision anota a revisão e avisa só quando mudou; lixo é ignorado', () => {
  const w = load();
  const R = w.OmegasUi.Revisions;
  const seen = [];
  R.subscribe((kind, revision) => seen.push(`${kind}:${revision}`));
  w.OmegasOnRevision('evidence', 5);
  w.OmegasOnRevision('evidence', 5);
  w.OmegasOnRevision('nada', 1);
  w.OmegasOnRevision('tables', 'x');
  w.OmegasOnRevision('session', 2);
  assert.deepEqual(seen, ['evidence:5', 'session:2']);
  assert.equal(R.noteAll(null), false);
  assert.equal(R.noteAll({ evidence: 5 }), false, 'a mesma revisão não é mudança (idempotente)');
});

test('as telas ligam o portão e o quadro da ponte alimenta as revisões', () => {
  const app = read('app.js');
  assert.match(app, /Revisions\?\.noteAll\(envelope\.revisions \|\| envelope\.data\?\.revisions\)/);
  assert.match(read('index.html'), /core\/revisions\.js/);
  for (const file of ['screens/refino.js', 'screens/autocal-cockpit.js']) {
    const source = read(file);
    assert.match(source, /Revisions/, `${file} usa as revisões`);
    assert.match(source, /dataGate\.due\(/, `${file} relê pelo portão`);
    assert.match(source, /dataGate\.mark\(/, `${file} marca a releitura`);
  }
});
