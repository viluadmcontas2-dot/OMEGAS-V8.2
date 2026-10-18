'use strict';

// Ferramentas > Avançado, ESCRITA MANUAL (dono autorizou, 2026-10-18): só o bit 0x20 da palavra 0 de FLAG_CONF3
// ('Abilitazione anti-stallo'). Toque -> prévia -> toque duplo -> SetVector 0x35 -> ACK -> leitura de volta -> só então "gravado".
// Nunca grava sozinho; sem escrita fora do toque; sem relógio (esperar = resposta da ECU, via gancho de dados).
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
// Objetos criados no contexto vm têm outro Array: compara por valor (JSON).
const deepEqual = (a, b, m) => assert.deepEqual(JSON.parse(JSON.stringify(a)), b, m);
const vm = require('node:vm');

const UI = path.join(__dirname, '../../app/src/main/assets/ui');
const read = file => fs.readFileSync(path.join(UI, file), 'utf8');

function load() {
  const context = { console, Math, JSON, Number, String, Array, Object, Set, Map };
  context.window = context;
  context.globalThis = context;
  vm.createContext(context);
  for (const file of ['core/advanced-flags-data.js', 'core/advanced-flags.js']) vm.runInContext(read(file), context, { filename: file });
  return context.OmegasUi.AdvancedFlags;
}
const A = load();
const hexOf = bytes => bytes.map(b => b.toString(16).toUpperCase().padStart(2, '0')).join(' ');

// ---------- quadro SetVector ----------
test('quadro de ligar com tudo zero é o derivado do código: 35 FA 00 20 00 00 00 4F', () => {
  const plan = A.planAntiStallo([0, 0], true);
  assert.equal(plan.ok, true);
  assert.equal(plan.frameHex, '35 FA 00 20 00 00 00 4F');
  deepEqual(plan.before, [0, 0]);
  deepEqual(plan.after, [0x0020, 0]);
  assert.equal(plan.changedMask, 0x20);
  assert.equal(plan.noop, false);
});

test('o mesmo montador reproduz o quadro REAL visto no fio para FLAG_CONF1 (35 03 00 86 2C 51 10 4B)', () => {
  assert.equal(hexOf(A.frameForVector(3, [0x2C86, 0x1051])), '35 03 00 86 2C 51 10 4B');
});

test('ordem de bytes: palavra 0 primeiro, cada palavra little-endian (eco real da leitura 53 04 20 00 00 00 77)', () => {
  assert.equal(hexOf(A.frameForVector(250, [0x1234, 0xABCD])), '35 FA 00 34 12 CD AB ' + ((0x35 + 0xFA + 0x34 + 0x12 + 0xCD + 0xAB) & 0xFF).toString(16).toUpperCase());
  // a leitura real devolve os mesmos 4 bytes de dados na mesma ordem
  deepEqual(A.wordsFromBytes([0x20, 0x00, 0x00, 0x00]), [0x0020, 0]);
  deepEqual(A.wordsFromBytes([0x34, 0x12, 0xCD, 0xAB]), [0x1234, 0xABCD]);
});

test('caso do código original (OBS-COD, cpu-setter-original): 0C10 ABCD + marcar = 35 FA 00 30 0C CD AB E3', () => {
  const plan = A.planAntiStallo([0x0C10, 0xABCD], true);
  assert.equal(plan.frameHex, '35 FA 00 30 0C CD AB E3');
  deepEqual(plan.after, [0x0C30, 0xABCD]);
});

test('desligar: só o bit 0x20 apaga (35 FA 00 00 00 00 00 2F)', () => {
  const plan = A.planAntiStallo([0x0020, 0], false);
  assert.equal(plan.frameHex, '35 FA 00 00 00 00 00 2F');
  deepEqual(plan.after, [0, 0]);
});

test('ler-modificar-escrever: todos os OUTROS bits das duas palavras são preservados', () => {
  const antes = [0xDFCF, 0xA5A5];
  const ligado = A.planAntiStallo(antes, true);
  deepEqual(ligado.after, [0xDFEF, 0xA5A5]);
  assert.equal((ligado.before[0] ^ ligado.after[0]), 0x20, 'só 1 bit muda');
  assert.equal(ligado.before[1], ligado.after[1]);
  const desligado = A.planAntiStallo([0xFFFF, 0xFFFF], false);
  deepEqual(desligado.after, [0xFFDF, 0xFFFF]);
  assert.equal(desligado.frameHex.split(' ').slice(3, 7).join(' '), 'DF FF FF FF');
});

test('já no estado pedido: nada a gravar (noop), sem quadro a enviar', () => {
  const plan = A.planAntiStallo([0x0020, 7], true);
  assert.equal(plan.noop, true);
  assert.equal(plan.changedMask, 0);
});

test('valor atual desconhecido NÃO gera plano (nunca assume 0)', () => {
  for (const words of [null, undefined, [], [0], [0, 0, 0], [0, -1], [0, 65536], [NaN, 0], ['1', 0]]) {
    const plan = A.planAntiStallo(words, true);
    assert.equal(plan.ok, false, JSON.stringify(words));
    assert.match(plan.error, /desconhecid|lid/i);
  }
});

// ---------- leitura de volta ----------
test('leitura de volta: só vale se for IGUAL ao esperado (as duas palavras)', () => {
  const plan = A.planAntiStallo([0x4000, 5], true);
  assert.equal(A.verifyReadback(plan, [0x4020, 5]).ok, true);
  const bit = A.verifyReadback(plan, [0x4000, 5]);
  assert.equal(bit.ok, false);
  assert.match(bit.message, /esperado 4020 0005/);
  assert.match(bit.message, /veio 4000 0005/);
  assert.equal(A.verifyReadback(plan, [0x4020, 4]).ok, false, 'outra palavra diferente também reprova');
  assert.equal(A.verifyReadback(plan, [0x4030, 5]).ok, false, 'outro bit mudou: reprova');
  assert.equal(A.verifyReadback(plan, null).ok, false);
  assert.equal(A.verifyReadback(plan, [0x4020]).ok, false);
});

// ---------- fluxo de toque ----------
function ambiente(opcoes = {}) {
  const chamadas = [];
  const hooks = new Set();
  let status = { state: 'IDLE' };
  const bridge = {
    prepareAntiStalloFlag(enable) { chamadas.push(['prepare', enable]); return JSON.stringify(opcoes.prepareReply || { ok: true, started: true }); },
    confirmAntiStalloFlag(token) { chamadas.push(['confirm', token]); return JSON.stringify(opcoes.confirmReply || { ok: true, started: true }); },
    cancelAntiStalloFlag() { chamadas.push(['cancel']); return JSON.stringify({ ok: true }); },
    getAntiStalloFlagStatus() { return JSON.stringify(status); },
  };
  const env = {
    chamadas, bridge,
    setStatus(next) { status = next; },
    emit() { for (const fn of [...hooks]) fn(); },
    addHook(name, fn) { assert.equal(name, 'fast'); hooks.add(fn); return () => hooks.delete(fn); },
    hooks,
    count(tipo) { return chamadas.filter(c => c[0] === tipo).length; },
  };
  env.flow = A.createWriteFlow({ bridge, addHook: env.addHook });
  return env;
}
const lido = words => ({ state: 'OK', values: words });
const pronto = (before, enable, token = 'tk1') => {
  const plan = A.planAntiStallo(before, enable);
  return { state: 'READY', token, enable, before, after: plan.after, frameHex: plan.frameHex, noop: plan.noop };
};

test('1º toque só PREPARA (lê a ECU e mostra a prévia); nada é gravado', () => {
  const e = ambiente();
  e.flow.tapSwitch(lido([0, 0]), true);
  deepEqual(e.chamadas, [['prepare', true]]);
  assert.equal(e.flow.view().phase, 'preparing');
  e.setStatus(pronto([0, 0], true));
  e.emit();
  const v = e.flow.view();
  assert.equal(v.phase, 'preview');
  assert.equal(v.preview.beforeHex, '0000 0000');
  assert.equal(v.preview.afterHex, '0020 0000');
  assert.match(v.preview.text, /Só o anti-apagão muda; o resto fica igual/);
  assert.match(v.buttonLabel, /Toque de novo/);
  assert.equal(e.count('confirm'), 0, 'prévia nunca grava');
});

test('NUNCA grava sozinho: muitos dados novos na prévia, nenhuma confirmação enviada', () => {
  const e = ambiente();
  e.flow.tapSwitch(lido([0, 0]), true);
  e.setStatus(pronto([0, 0], true));
  for (let i = 0; i < 500; i += 1) e.emit();
  assert.equal(e.count('confirm'), 0);
  assert.equal(e.flow.view().phase, 'preview');
});

test('2º toque (confirmação) envia UMA vez a confirmação com o token da prévia', () => {
  const e = ambiente();
  e.flow.tapSwitch(lido([0, 0]), true);
  e.setStatus(pronto([0, 0], true, 'abc'));
  e.emit();
  e.flow.tapSwitch(lido([0, 0]), true);
  e.flow.tapSwitch(lido([0, 0]), true); // toque repetido enquanto grava: ignorado
  e.flow.tapSwitch(lido([0, 0]), true);
  deepEqual(e.chamadas.filter(c => c[0] === 'confirm'), [['confirm', 'abc']]);
  assert.equal(e.flow.view().phase, 'writing');
});

test('sucesso só depois da leitura de volta IGUAL; mostra o valor devolvido pela ECU e guarda o anterior p/ desfazer', () => {
  const e = ambiente();
  e.flow.tapSwitch(lido([0, 0]), true);
  e.setStatus(pronto([0, 0], true));
  e.emit();
  e.flow.tapSwitch(lido([0, 0]), true);
  e.setStatus({ state: 'WRITING' });
  e.emit();
  assert.equal(e.flow.view().phase, 'writing', 'sem resposta final ainda: continua esperando');
  e.setStatus({ state: 'DONE', outcome: 'CONFIRMED', readback: [0x20, 0], before: [0, 0], after: [0x20, 0], undo: { available: true, enable: false } });
  e.emit();
  const v = e.flow.view();
  assert.equal(v.phase, 'done');
  assert.match(v.message, /Pronto\. Anti-apagão ligado e conferido na central\./);
  assert.equal(v.undo.available, true);
  assert.equal(e.hooks.size, 0, 'terminou: o gancho de dados é solto');
});

test('leitura de volta DIVERGENTE: erro honesto, nunca "gravado"', () => {
  const e = ambiente();
  e.flow.tapSwitch(lido([0, 0]), true);
  e.setStatus(pronto([0, 0], true));
  e.emit();
  e.flow.tapSwitch(lido([0, 0]), true);
  e.setStatus({ state: 'DONE', outcome: 'READBACK_DIVERGED', readback: [0, 0], message: 'x' });
  e.emit();
  const v = e.flow.view();
  assert.equal(v.phase, 'error');
  assert.doesNotMatch(v.message, /Gravado e conferido/);
  assert.match(v.message, /Não sei como ficou na central/);
  assert.equal(v.uncertain, true);
});

test('o JS reconfere a leitura de volta: Kotlin diz CONFIRMED mas o valor lido não bate -> erro', () => {
  const e = ambiente();
  e.flow.tapSwitch(lido([0, 0]), true);
  e.setStatus(pronto([0, 0], true));
  e.emit();
  e.flow.tapSwitch(lido([0, 0]), true);
  e.setStatus({ state: 'DONE', outcome: 'CONFIRMED', readback: [0x30, 0] });
  e.emit();
  assert.equal(e.flow.view().phase, 'error');
});

test('o JS reconfere o quadro: prévia com quadro diferente do calculado aqui é recusada', () => {
  const e = ambiente();
  e.flow.tapSwitch(lido([0, 0]), true);
  e.setStatus({ ...pronto([0, 0], true), frameHex: '35 FA 00 10 00 00 00 3F' });
  e.emit();
  assert.equal(e.flow.view().phase, 'error');
  assert.match(e.flow.view().message, /não bate.*Nada foi gravado/);
  assert.equal(e.count('confirm'), 0);
});

test('ECU não confirmou (sem ACK / recusa): erro, sem sucesso', () => {
  for (const outcome of ['NOT_ACKED', 'CHANGED_MEANWHILE', 'ERROR', 'READBACK_FAILED']) {
    const e = ambiente();
    e.flow.tapSwitch(lido([0, 0]), true);
    e.setStatus(pronto([0, 0], true));
    e.emit();
    e.flow.tapSwitch(lido([0, 0]), true);
    e.setStatus({ state: 'DONE', outcome, message: 'detalhe ' + outcome });
    e.emit();
    assert.equal(e.flow.view().phase, 'error', outcome);
    assert.match(e.flow.view().message, outcome === 'NOT_ACKED' ? /A central não aceitou\. Tente de novo\./ : outcome === 'CHANGED_MEANWHILE' ? /mudou na central\. Nada foi gravado/ : outcome === 'READBACK_FAILED' ? /Não sei como ficou/ : /Não consegui falar com a central/);
    assert.doesNotMatch(e.flow.view().message, /detalhe|0x|ACK|ECU/);
  }
});

test('desarma sem gravar: outro toque, troca de rota, USB caiu, cancelar', () => {
  for (const motivo of ['cancel', 'route', 'usb', 'other']) {
    const e = ambiente();
    e.flow.tapSwitch(lido([0, 0]), true);
    e.setStatus(pronto([0, 0], true));
    e.emit();
    assert.equal(e.flow.view().phase, 'preview');
    if (motivo === 'cancel') e.flow.cancel();
    if (motivo === 'route') e.flow.observe({ route: 'map', usbConnected: true });
    if (motivo === 'usb') e.flow.observe({ route: 'tools', usbConnected: false });
    if (motivo === 'other') e.flow.otherTouch();
    assert.equal(e.flow.view().phase, 'idle', motivo);
    assert.equal(e.count('confirm'), 0, motivo);
    assert.equal(e.count('cancel'), 1, motivo + ': avisa o nativo para descartar a prévia');
    assert.equal(e.hooks.size, 0);
  }
});

test('observe com rota Ferramentas e USB ligado não desarma', () => {
  const e = ambiente();
  e.flow.tapSwitch(lido([0, 0]), true);
  e.setStatus(pronto([0, 0], true));
  e.emit();
  e.flow.observe({ route: 'tools', usbConnected: true });
  assert.equal(e.flow.view().phase, 'preview');
});

test('USB cai durante a gravação: a espera termina (sem resposta final) com aviso honesto', () => {
  const e = ambiente();
  e.flow.tapSwitch(lido([0, 0]), true);
  e.setStatus(pronto([0, 0], true));
  e.emit();
  e.flow.tapSwitch(lido([0, 0]), true);
  e.flow.observe({ route: 'tools', usbConnected: false });
  const v = e.flow.view();
  assert.equal(v.phase, 'error');
  assert.match(v.message, /cabo soltou/);
  assert.match(v.message, /Não sei como ficou/);
  assert.equal(e.hooks.size, 0);
});

test('valor atual desconhecido ("—"): o botão não faz nada', () => {
  const e = ambiente();
  for (const entrada of [undefined, { state: 'NACK', values: null }]) {
    e.flow.tapSwitch(entrada, true);
    assert.equal(e.flow.view().phase, 'idle');
  }
  assert.equal(e.chamadas.length, 0);
  assert.equal(e.flow.canTap(undefined, true), false);
});

test('sem ECU conectada: não prepara nem grava', () => {
  const e = ambiente();
  e.flow.tapSwitch(lido([0, 0]), false);
  assert.equal(e.chamadas.length, 0);
});

test('ponte sem as funções (simulação/ambiente antigo): nada é chamado e o botão fica desabilitado', () => {
  const flow = A.createWriteFlow({ bridge: {}, addHook: () => () => {} });
  flow.tapSwitch(lido([0, 0]), true);
  assert.equal(flow.view().phase, 'idle');
  assert.equal(flow.canTap(lido([0, 0]), true), false);
});

test('já ligado: o botão pede DESLIGAR (alvo = bit invertido)', () => {
  const e = ambiente();
  assert.equal(e.flow.buttonLabel(lido([0x20, 0])), 'Desligar anti-apagão');
  assert.equal(e.flow.buttonLabel(lido([0, 0])), 'Ligar anti-apagão');
  e.flow.tapSwitch(lido([0x20, 0]), true);
  deepEqual(e.chamadas, [['prepare', false]]);
});

test('prévia com valor já no estado pedido (alguém mudou): avisa e não oferece gravar', () => {
  const e = ambiente();
  e.flow.tapSwitch(lido([0, 0]), true);
  e.setStatus({ ...pronto([0x20, 0], true), noop: true });
  e.emit();
  assert.equal(e.flow.view().phase, 'idle');
  assert.match(e.flow.view().message, /já está/);
  assert.equal(e.count('confirm'), 0);
});

test('DESFAZER restaura o valor anterior pelo MESMO fluxo (prepara, prévia, toque duplo)', () => {
  const e = ambiente();
  e.flow.tapSwitch(lido([0, 0]), true);
  e.setStatus(pronto([0, 0], true));
  e.emit();
  e.flow.tapSwitch(lido([0, 0]), true);
  e.setStatus({ state: 'DONE', outcome: 'CONFIRMED', readback: [0x20, 0], undo: { available: true, enable: false } });
  e.emit();
  assert.equal(e.flow.view().phase, 'done');
  e.chamadas.length = 0;
  e.flow.tapUndo();
  deepEqual(e.chamadas, [['prepare', false]], 'desfazer só prepara: nada grava ainda');
  e.setStatus(pronto([0x20, 0], false, 'tk2'));
  e.emit();
  const v = e.flow.view();
  assert.equal(v.phase, 'preview');
  assert.equal(v.preview.beforeHex, '0020 0000');
  assert.equal(v.preview.afterHex, '0000 0000');
  e.flow.tapUndo(); // 2º toque no mesmo botão confirma
  deepEqual(e.chamadas.filter(c => c[0] === 'confirm'), [['confirm', 'tk2']]);
});

test('sem desfazer disponível: tapUndo não chama nada', () => {
  const e = ambiente();
  e.flow.tapUndo();
  assert.equal(e.chamadas.length, 0);
});

test('o módulo não usa relógio nem prazo (setTimeout/setInterval/Date/delay)', () => {
  const fonte = read('core/advanced-flags.js').replace(/\/\*[\s\S]*?\*\/|\/\/.*$/gm, '');
  assert.doesNotMatch(fonte, /setTimeout|setInterval|requestAnimationFrame|Date\.now|new Date|performance\.now|\bdelay\b/);
});

// ---------- estado incerto (revisão independente, item 3) ----------
function terminar(status) {
  const e = ambiente();
  e.flow.tapSwitch(lido([0, 0]), true);
  e.setStatus(pronto([0, 0], true));
  e.emit();
  e.flow.tapSwitch(lido([0, 0]), true);
  e.setStatus({ state: 'DONE', ...status });
  e.emit();
  return e.flow.view();
}

test('estado incerto: READBACK_FAILED / READBACK_DIVERGED / valueChanged nunca dizem "nada foi dado como gravado"', () => {
  const casos = [
    { outcome: 'READBACK_FAILED', message: 'A leitura de volta falhou' },
    { outcome: 'READBACK_DIVERGED', readback: [0x10, 0], valueChanged: true, message: 'x' },
    { outcome: 'NOT_ACKED', readback: [0x20, 0], valueChanged: true, message: 'A ECU não confirmou a gravação (status 0xCA)' },
  ];
  for (const caso of casos) {
    const v = terminar(caso);
    assert.equal(v.phase, 'error', caso.outcome);
    assert.match(v.message, /Não sei como ficou na central/, caso.outcome);
    assert.doesNotMatch(v.message, /[Nn]ada foi dado como gravado/, caso.outcome);
    assert.equal(v.uncertain, true, caso.outcome);
  }
});

test('recusa limpa (NOT_ACKED sem mudança, valor intacto) segue dizendo que nada foi gravado', () => {
  const v = terminar({ outcome: 'NOT_ACKED', readback: [0, 0], valueChanged: false });
  assert.equal(v.phase, 'error');
  assert.match(v.message, /A central não aceitou\. Tente de novo\./);
  assert.doesNotMatch(v.message, /Não sei como ficou/);
  assert.notEqual(v.uncertain, true);
});

test('CONFIRMED que o JS não consegue reconferir também é estado incerto', () => {
  const v = terminar({ outcome: 'CONFIRMED', readback: [0x30, 0] });
  assert.match(v.message, /Não sei como ficou na central/);
  assert.doesNotMatch(v.message, /[Nn]ada foi dado como gravado/);
});
