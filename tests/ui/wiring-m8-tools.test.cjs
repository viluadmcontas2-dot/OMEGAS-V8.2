'use strict';
// M8 · Ferramentas: cada botão/toggle/seletor + o convite de primeiro uso do balão flutuante.
const test = require('node:test');
const L = require('./wiring/lib.cjs');
const { assert } = L;
const { todo } = require('./wiring/registry.cjs');
const W = require('./wiring/world.cjs');

const SCREEN = '[data-screen="tools"]';
const LOGS = [
  { time: '10:00:01', level: 'ERROR', category: 'USB', message: 'cabo caiu' },
  { time: '10:00:02', level: 'INFO', category: 'ECU', message: 'leitura ok' },
  { time: '10:00:03', level: 'WARN', category: 'ECU', message: 'demora' },
];

function toolsApp({ overlay, battery, setup, openDetails = true, route = 'tools' } = {}) {
  const w = new W.World();
  w.logs = LOGS;
  if (overlay) w.overlay = { ...w.overlay, ...overlay };
  if (battery) w.battery = { ...w.battery, ...battery };
  if (setup) setup(w);
  const app = L.boot({ world: w });
  app.go(route);
  app.settle(6);
  if (openDetails) app.$$('details').forEach(d => d.setAttribute('open', ''));
  return app;
}
const call = (app, mark, method) => app.world.since(mark).filter(c => c.method === method);

test('M8 abre limpo: saúde do app, bateria, balão, retenção e logs presentes', () => {
  const app = toolsApp();
  L.assertClean(app, 'M8/limpo');
  const t = app.$(SCREEN).textContent;
  assert.match(t, /Tudo funcionando/i);
  assert.match(t, /SEGUNDO PLANO/);
  assert.match(t, /TELEMETRIA FLUTUANTE/);
  assert.match(t, /Reten[cç][aã]o/i);
  assert.equal(app.byId('toolExportData').localName, 'button');
});

test('M8 serviço parado / ECU travada: a saúde diz a verdade (sem "Funcionando")', () => {
  const stopped = toolsApp({ setup: w => { w.status.serviceRunning = false; } });
  assert.doesNotMatch(stopped.$(SCREEN).textContent, /Tudo funcionando/);
  assert.match(stopped.$(SCREEN).textContent, /n[aã]o est[aá] ativo/i);
  const stuck = toolsApp({ setup: w => { w.status.engineStuck = true; } });
  assert.doesNotMatch(stuck.$(SCREEN).textContent, /Tudo funcionando/);
  assert.match(stuck.$(SCREEN).textContent, /aten[cç][aã]o/i);
});

test('M8 Exportar backup completo: UMA chamada exportData', () => {
  const app = toolsApp();
  const mark = app.world.mark();
  app.byId('toolExportData').click(); app.flush();
  assert.equal(call(app, mark, 'exportData').length, 1);
});

test('M8 Exportar logs e Autoteste: chamam a ponte; autoteste mostra o resultado (ok e falha)', () => {
  const app = toolsApp();
  let mark = app.world.mark();
  app.$('[data-tool-export-logs]').click(); app.flush();
  assert.equal(call(app, mark, 'exportLogs').length, 1);
  mark = app.world.mark();
  app.$('[data-tool-selftest]').click(); app.flush();
  assert.equal(call(app, mark, 'runEngineSelfTests').length, 1);
  assert.match(app.state().alert.message, /Autoteste conclu/i);
  const bad = toolsApp();
  bad.world.raw.runEngineSelfTests = JSON.stringify({ ok: false, error: 'ECU sem resposta' });
  bad.$('[data-tool-selftest]').click(); bad.flush();
  assert.match(bad.state().alert.message, /ECU sem resposta/);
  assert.equal(bad.state().alert.level, 'warning');
  const thrown = toolsApp();
  thrown.world.throwOn.add('runEngineSelfTests');
  thrown.$('[data-tool-selftest]').click(); thrown.flush();
  assert.ok(thrown.state().alert && thrown.state().alert.level === 'warning', 'exceção da ponte vira aviso legível');
  L.assertClean(thrown, 'M8/autoteste lança');
});

test('M8 balão flutuante: precisa de permissão → Autorizar; autorizado → Ativar → Desativar + tamanhos', () => {
  const need = toolsApp({ overlay: { permissionGranted: false, requestedEnabled: false } });
  assert.ok(need.$('[data-tool-overlay-request]'), 'sem permissão deve oferecer Autorizar');
  assert.ok(need.$('[data-tool-overlay-enable]') === null);
  let mark = need.world.mark();
  need.$('[data-tool-overlay-request]').click(); need.flush();
  assert.equal(call(need, mark, 'requestOverlayPermissionAndEnable').length, 1);

  const off = toolsApp({ overlay: { permissionGranted: true, requestedEnabled: false } });
  assert.ok(off.$('[data-tool-overlay-enable]'));
  assert.ok(off.$('[data-tool-overlay-disable]') === null);
  mark = off.world.mark();
  off.$('[data-tool-overlay-enable]').click(); off.settle(2);
  assert.deepEqual(call(off, mark, 'setOverlayEnabled').map(c => c.args[0]), [true]);
  off.$$('details').forEach(d => d.setAttribute('open', ''));
  assert.ok(off.$('[data-tool-overlay-disable]'), 'depois de ativar a tela oferece Desativar');
  assert.equal(off.$$('[data-tool-overlay-scale]').length, 3, 'três tamanhos do balão');
  mark = off.world.mark();
  off.$('[data-tool-overlay-scale="1.6"]').click(); off.flush();
  assert.deepEqual(call(off, mark, 'setOverlayScale').map(c => c.args[0]), [1.6]);
  mark = off.world.mark();
  off.$('[data-tool-overlay-disable]').click(); off.settle(2);
  assert.deepEqual(call(off, mark, 'setOverlayEnabled').map(c => c.args[0]), [false]);
  assert.ok(off.$('[data-tool-overlay-enable]'), 'depois de desativar volta o Ativar');
});

test('M8 balão indisponível neste Android: sem botão de ação, com explicação', () => {
  const app = toolsApp({ overlay: { supported: false } });
  assert.ok(app.$('[data-tool-overlay-request]') === null);
  assert.ok(app.$('[data-tool-overlay-enable]') === null);
  assert.match(app.$(SCREEN).textContent, /Indispon[ií]vel neste Android/);
});

test('M8 bateria: Permitir chama a ponte; quando o Android libera, o botão some', () => {
  const app = toolsApp({ battery: { ignoringOptimizations: false } });
  const btn = app.$('[data-tool-battery-request]');
  assert.ok(btn);
  const mark = app.world.mark();
  btn.click(); app.flush();
  assert.equal(call(app, mark, 'requestBatteryOptimizationExemption').length, 1);
  app.world.battery = { supported: true, ignoringOptimizations: true };
  app.settle(8);
  app.$$('details').forEach(d => d.setAttribute('open', ''));
  assert.ok(app.$('[data-tool-battery-request]') === null);
  assert.match(app.$(SCREEN).textContent, /n[aã]o pausa o app/);
});

test('M8 Retenção das sessões: Aplicar envia EXATAMENTE os valores dos campos (mín. 20 sessões) e mostra o resultado', () => {
  const app = toolsApp();
  const set = (sel, value) => { const el = app.$(sel); el.value = String(value); el.dispatchEvent(new app.win.Event('change', { bubbles: true })); };
  set('[data-session-telemetry]', 1000);
  set('[data-session-maxmb]', 128);
  set('[data-session-keep]', 10);
  const raw = app.$('[data-session-rawusb]'); raw.checked = true; raw.dispatchEvent(new app.win.Event('change', { bubbles: true }));
  const mark = app.world.mark();
  app.$('[data-session-settings]').click(); app.flush();
  const calls = call(app, mark, 'setSessionRecorderSettings');
  assert.equal(calls.length, 1);
  assert.deepEqual(calls[0].args, [1000, 128, 20, true, true], 'telemetria, MB, manter (mín. 20), auto-iniciar, USB bruto');
  assert.match(app.$(SCREEN).textContent, /Aplicado: 1000 ms · 128 MB · 20 sess/);
  assert.match(app.state().alert.message, /Pol[ií]tica de logs atualizada/);
});

test('M8 Retenção com erro da ponte: mostra a falha, não "Aplicado"', () => {
  const app = toolsApp();
  app.world.raw.setSessionRecorderSettings = JSON.stringify({ ok: false, error: 'Disco cheio' });
  app.$('[data-session-settings]').click(); app.flush();
  assert.match(app.state().alert.message, /Disco cheio/);
  assert.doesNotMatch(app.$(SCREEN).textContent, /Aplicado:/);
});

test('M8 filtros de log: nível e categoria realmente filtram a lista', () => {
  const app = toolsApp();
  const lines = () => app.$$('.log-lines > div').map(d => d.textContent);
  assert.equal(lines().length, 3);
  const level = app.$('[data-log-level]');
  level.value = 'ERROR'; level.dispatchEvent(new app.win.Event('change', { bubbles: true })); app.flush();
  assert.equal(lines().length, 1);
  assert.match(lines()[0], /cabo caiu/);
  app.$$('details').forEach(d => d.setAttribute('open', ''));
  const level2 = app.$('[data-log-level]');
  level2.value = 'ALL'; level2.dispatchEvent(new app.win.Event('change', { bubbles: true })); app.flush();
  const cat = app.$('[data-log-category]');
  cat.value = 'ECU'; cat.dispatchEvent(new app.win.Event('change', { bubbles: true })); app.flush();
  assert.equal(lines().length, 2);
});

test('M8 convite do balão (primeiro uso): aparece uma vez; Autorizar chama a ponte; "Agora não" não chama; não volta', () => {
  const make = () => {
    const w = new W.World();
    w.overlay = { ...w.overlay, permissionGranted: false, requestedEnabled: false };
    const app = L.boot({ world: w });
    app.advance(6000); app.flush();
    return app;
  };
  const yes = make();
  const box = yes.byId('overlayPrompt');
  assert.ok(box, 'o convite do balão não apareceu depois de 5 s');
  const mark = yes.world.mark();
  box.querySelector('[data-overlay-prompt="yes"]').click(); yes.flush();
  assert.equal(call(yes, mark, 'requestOverlayPermissionAndEnable').length, 1);
  assert.equal(yes.byId('overlayPrompt'), null, 'o convite some depois da resposta');
  const no = make();
  const mark2 = no.world.mark();
  no.byId('overlayPrompt').querySelector('[data-overlay-prompt="no"]').click(); no.flush();
  assert.equal(call(no, mark2, 'requestOverlayPermissionAndEnable').length, 0);
  assert.equal(no.byId('overlayPrompt'), null);
  no.advance(20000);
  assert.equal(no.byId('overlayPrompt'), null, 'não pode voltar depois de recusado');
  // quem já decidiu antes não é incomodado
  const w = new W.World(); w.overlay = { ...w.overlay, permissionGranted: false };
  const seen = L.boot({ world: w, storage: { 'omegas-overlay-prompt-v1': '1' } });
  seen.advance(8000); seen.flush();
  assert.equal(seen.byId('overlayPrompt'), null);
});

test('M8 idempotência: estado igual duas vezes não muda o DOM; A→B→A restaura; listeners estáveis', () => {
  const app = toolsApp({ openDetails: false });
  // a idade da telemetria anda com o relógio; o resto da tela tem de ficar idêntico
  const snap = () => L.serialize(app.$(SCREEN)).replace(/Último dado [^<]*</, 'Último dado #<');
  const sched = app.win.OmegasApp.scheduler;
  sched.run();
  const dom = snap();
  sched.run(); sched.run();
  assert.equal(snap(), dom);
  app.go('sessions'); app.go('tools');
  assert.equal(snap(), dom);
  const base = app.listenerTotal();
  for (let i = 0; i < 6; i += 1) { app.go('sessions'); app.go('tools'); }
  assert.equal(app.listenerTotal(), base, 'reentrar em Ferramentas empilha listeners');
});

const STATES = {
  'padrão (detalhes abertos)': () => toolsApp(),
  'balão ligado': () => toolsApp({ overlay: { permissionGranted: true, requestedEnabled: true, visible: true } }),
  'balão sem permissão': () => toolsApp({ overlay: { permissionGranted: false } }),
  'balão desligado': () => toolsApp({ overlay: { permissionGranted: true, requestedEnabled: false } }),
};
for (const [name, make] of Object.entries(STATES)) {
  test(`M8 ${name}: nenhum botão/seletor/campo de Ferramentas está congelado`, () => {
    const r = L.sweep({ prepare: make, within: SCREEN });
    assert.deepEqual(r.failures, [], `${r.exercised}/${r.total}`);
    assert.ok(r.total >= 8, `elementos enxergados: ${r.total}`);
  });
}

test('M8 fuzz de logs/sessão/balão/bateria: nunca exceção/NaN, recupera', () => {
  const { cases, effective, failures } = L.fuzz({
    prepare: () => toolsApp({ openDetails: false }),
    methods: ['getLogs', 'getSessionRecorderStatus', 'getOverlayStatus', 'getBatteryOptimizationStatus', 'getStatus'],
    maxPathsPerMethod: 30,
    perTick: app => { app.world.logs = app.world.logs.map(l => ({ ...l })); app.advance(1900); },
  });
  assert.ok(cases > 100, `casos ${cases}`);
  assert.ok(effective > cases * 0.2, `fuzz inócuo ${effective}/${cases}`);
  assert.deepEqual(failures.slice(0, 6), [], `${failures.length}/${cases}`);
});

test('M8 retenção de sessões não numérica vinda da ponte não vira NaN/Infinity na tela', todo('DEFECT-21'), () => {
  const app = toolsApp({ setup: w => { w.sessionStatus = { ...w.sessionStatus, settings: { telemetryEveryMs: 250, maxSessionMb: 256, keepSessions: 'abc' } }; } });
  assert.doesNotMatch(app.$(SCREEN).textContent, /NaN|Infinity/);
});
