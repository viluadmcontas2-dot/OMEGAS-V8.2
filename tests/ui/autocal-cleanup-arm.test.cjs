'use strict';
// Desenho aprovado (dono, 2026-10-07): a limpeza automática começa DESARMADA em toda sessão USB. O rodapé do AutoCal
// ganha um botão de um toque ("Ativar limpeza automática" / "Desativar limpeza"); a linha discreta diz o estado.
// Classe de prova: 2 (ponte falsa, DOM real do app).
const test = require('node:test'), assert = require('node:assert/strict');
const L = require('./wiring/lib.cjs');

const TECH = /readback|ECU_|DELETE_POINT|OUTLIER|armed|enabled|pauseCode/i;

function toggle(app) { return app.$('.ar-act > .ar-buttons [data-autocal-cleanup-toggle]'); }
function line(app) { return app.byId('autocalAutoCleanLine'); }
function armCalls(app) { return app.world.calls.filter(c => c.method === 'setAutoCleanupArmed'); }

test('rodapé: botão de um toque arma e desarma pela ponte; a linha acompanha', () => {
  const app = L.boot(); try {
    app.go('autocal'); app.settle(3);
    const button = toggle(app);
    assert.ok(button, 'o botão fica no rodapé único do AutoCal');
    assert.equal(button.hidden, false);
    assert.equal(button.disabled, false, 'com sessão USB válida o botão está vivo');
    assert.equal(button.textContent, 'Ativar limpeza automática');
    assert.equal(line(app).textContent, 'Limpeza automática: desligada · o app não apaga nada sozinho');

    button.click(); app.settle(2);
    assert.equal(armCalls(app).length, 1, 'um toque = uma chamada');
    assert.deepEqual(armCalls(app)[0].args, [true]);
    assert.equal(app.world.autocalAutoCleanup.armed, true);
    assert.equal(button.textContent, 'Desativar limpeza');
    assert.equal(line(app).textContent, 'Limpeza automática: ligada · nenhum ponto reaprendido nesta sessão');
    assert.doesNotMatch(line(app).textContent + button.textContent, TECH);

    button.click(); app.settle(2);
    assert.equal(armCalls(app).length, 2);
    assert.deepEqual(armCalls(app)[1].args, [false]);
    assert.equal(button.textContent, 'Ativar limpeza automática');
    assert.equal(line(app).textContent, 'Limpeza automática: desligada · o app não apaga nada sozinho');
    L.assertClean(app, 'armar/desarmar');
  } finally { app.destroy(); }
});

test('sem sessão USB o botão não arma; ponte com falha vira aviso e nada muda', () => {
  const app = L.boot(); try {
    app.go('autocal'); app.settle(3);
    app.world.autocalAutoCleanup = { ...app.world.autocalAutoCleanup, active: false };
    app.win.OmegasApp.autoCalCockpit.refresh();
    assert.equal(toggle(app).disabled, true, 'sem conexão não há o que armar');
    assert.equal(line(app).hidden, true);
    app.world.autocalAutoCleanup = { ...app.world.autocalAutoCleanup, active: true };
    app.win.OmegasApp.autoCalCockpit.refresh();
    assert.equal(toggle(app).disabled, false);

    app.world.throwOn.add('setAutoCleanupArmed');
    const alerts = [];
    const store = app.win.OmegasApp.store;
    const patch = store.patch.bind(store);
    store.patch = value => { if (value && value.alert) alerts.push(value.alert); return patch(value); };
    toggle(app).click(); app.settle(2);
    assert.equal(app.world.autocalAutoCleanup.armed, false, 'falha da ponte não arma');
    assert.equal(toggle(app).textContent, 'Ativar limpeza automática');
    assert.equal(alerts.length, 1);
    assert.equal(alerts[0].level, 'warning');
    assert.doesNotMatch(alerts[0].message, TECH);
  } finally { app.destroy(); }
});

test('pausa por falha desarma: o botão volta a "Ativar" e a linha pede o toque; sessão nova volta desarmada', () => {
  const app = L.boot(); try {
    app.go('autocal'); app.settle(3);
    toggle(app).click(); app.settle(2);
    assert.equal(toggle(app).textContent, 'Desativar limpeza');
    app.world.autocalAutoCleanup = { ok: true, active: true, armed: false, enabled: false, pauseCode: 'REPEATED_FAILURES', relearnedThisSession: 1, recentDeletes: [] };
    app.win.OmegasApp.autoCalCockpit.refresh();
    assert.equal(toggle(app).textContent, 'Ativar limpeza automática');
    assert.equal(toggle(app).disabled, false, 'rearmar é um toque');
    assert.equal(line(app).dataset.level, 'warn');
    assert.match(line(app).textContent, /^Limpeza automática pausada: a ECU não respondeu bem várias vezes seguidas\. Toque em Ativar limpeza automática para tentar de novo\.$/);
    // Reconectou: o serviço publica a sessão nova desarmada e sem pausa.
    app.world.autocalAutoCleanup = { ok: true, active: true, armed: false, enabled: true, pauseCode: null, relearnedThisSession: 0, recentDeletes: [] };
    app.win.OmegasApp.autoCalCockpit.refresh();
    assert.equal(toggle(app).textContent, 'Ativar limpeza automática');
    assert.equal(line(app).textContent, 'Limpeza automática: desligada · o app não apaga nada sozinho');
    L.assertClean(app, 'pausa e sessão nova');
  } finally { app.destroy(); }
});

test('o botão fica na barra do rodapé, depois de Reler gasolina, e nenhum botão existente sumiu', () => {
  const app = L.boot(); try {
    app.go('autocal'); app.settle(3);
    const bar = app.$('.ar-act > .ar-buttons');
    const labels = [...bar.querySelectorAll('button')].filter(b => !b.hidden && !b.closest('[hidden]')).map(b => b.textContent.trim());
    const at = name => labels.findIndex(l => l === name);
    assert.ok(at('Reler GNV') >= 0 && at('Reler gasolina') >= 0, labels.join(' | '));
    assert.equal(at('Ativar limpeza automática'), at('Reler gasolina') + 1, labels.join(' | '));
  } finally { app.destroy(); }
});

// Revisão 2026-10-07 (achado 3): armada, a linha diz o que a limpeza está esperando, em português simples.
test('armada: a linha traz o motivo de espera sem jargão; desarmada/pausada não mostra motivo', () => {
  const app = L.boot(); try {
    app.go('autocal'); app.settle(3);
    app.world.autocalAutoCleanup = { ok: true, active: true, armed: true, enabled: true, pauseCode: null, relearnedThisSession: 0, recentDeletes: [], waitReason: 'Aguardando o carro rodar na gasolina' };
    app.win.OmegasApp.autoCalCockpit.refresh();
    assert.equal(line(app).textContent, 'Limpeza automática: ligada · nenhum ponto reaprendido nesta sessão · aguardando o carro rodar na gasolina');
    assert.doesNotMatch(line(app).textContent, TECH);
    app.world.autocalAutoCleanup = { ...app.world.autocalAutoCleanup, armed: false, waitReason: 'Limpeza automática desligada: toque em Ativar limpeza automática' };
    app.win.OmegasApp.autoCalCockpit.refresh();
    assert.equal(line(app).textContent, 'Limpeza automática: desligada · o app não apaga nada sozinho');
    app.world.autocalAutoCleanup = { ...app.world.autocalAutoCleanup, armed: true, waitReason: '' };
    app.win.OmegasApp.autoCalCockpit.refresh();
    assert.equal(line(app).textContent, 'Limpeza automática: ligada · nenhum ponto reaprendido nesta sessão');
    L.assertClean(app, 'motivo de espera');
  } finally { app.destroy(); }
});
