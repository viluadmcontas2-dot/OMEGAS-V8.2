'use strict';
// Cartão "Volta para gasolina" (dono, 2026-10-20): é a volta automática por PRESSÃO BAIXA do gás (ProgBase F4), não rotação
// nem bomba. Valores reais da central do dono: PortmonLOGNOVO (tests/ui/fixtures/ecu-estado-lognovo.json): tempo 0 s,
// RIF_PRESS_DIFF[0] = 1250 mbar (o "1,25 bar" da tela F4). Controles − / + / Espera / Salvar juntos, Desfazer depois.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const UI = path.join(__dirname, '../../app/src/main/assets/ui');
const LOG = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/ecu-estado-lognovo.json'), 'utf8')).leituras;

function load(withVolta = true) {
  const ctx = { console, Math, JSON, Number, String, Array, Object, Set, Map, Intl }; ctx.window = ctx; ctx.globalThis = ctx; vm.createContext(ctx);
  const files = ['core/advanced-flags-data.js', 'core/advanced-flags.js', 'core/anti-apagao-valores.js', 'core/idle-mode.js']
    .concat(withVolta ? ['core/volta-gasolina.js'] : []).concat(['core/cartoes-motor.js']);
  for (const f of files) vm.runInContext(fs.readFileSync(path.join(UI, f), 'utf8'), ctx);
  return ctx.OmegasUi;
}
const plain = v => JSON.parse(JSON.stringify(v));   // objetos do vm são de outro realm
const ok = values => ({ state: 'OK', values });
const words = hex => { const b = hex.split(' ').map(x => parseInt(x, 16)); return Array.from({ length: b.length / 2 }, (_, i) => b[2 * i] | (b[2 * i + 1] << 8)); };
const s16 = v => (v & 0x8000 ? v - 0x10000 : v);
const DONO = {
  TEMPO_RITORNO_BENZINA: ok([parseInt(LOG['09 3E 00 47'], 16)]),
  RIF_PRESS_DIFF: ok(words(LOG['29 7B 00 A4'])),
  COEFF_PRESS_DIFF: ok(words(LOG['29 7C 00 A5']).map(s16)),
};

test('central do dono: a frase diz a pressão lida (1,25 bar) e que o carro volta sozinho para a gasolina', () => {
  const card = load().CartoesMotor.build(DONO).find(c => c.id === 'volta');
  assert.equal(card.title, 'Volta para gasolina');
  assert.equal(card.line, 'Se a pressão do gás cair abaixo de 1,25 bar, o carro volta sozinho para a gasolina.');
  assert.equal(card.stateText, 'Ligado');
  assert.equal(card.kind, 'volta');
  const tudo = [card.title, card.line, card.shortLine, card.note || '', ...card.rows.map(r => r.label + ' ' + r.text)].join(' ');
  assert.doesNotMatch(tudo, /rota[cç]|giro|bomba|RIF|COEFF|SC |0x/i, 'sem rotação/bomba nem jargão na frente');
  assert.deepEqual(plain(card.rows.map(r => r.text)), ['Ligado', '1,25 bar', 'Na hora']);
  assert.ok(card.tech.some(t => /TEMPO_RITORNO_BENZINA.*62.*0/.test(t)));
  assert.ok(card.tech.some(t => /RIF_PRESS_DIFF.*1250/.test(t)));
});

test('tempo 255 = desligado (como o original ao desmarcar); espera de 2 s aparece escrita', () => {
  const U = load();
  const off = U.CartoesMotor.build({ ...DONO, TEMPO_RITORNO_BENZINA: ok([255]) }).find(c => c.id === 'volta');
  assert.equal(off.stateText, 'Desligado');
  assert.match(off.line, /não volta sozinho/);
  const dois = U.CartoesMotor.build({ ...DONO, TEMPO_RITORNO_BENZINA: ok([2]) }).find(c => c.id === 'volta');
  assert.equal(dois.rows[2].text, '2 s');
});

test('sem leitura: "—", nunca inventa 1,25', () => {
  const card = load().CartoesMotor.build({}).find(c => c.id === 'volta');
  assert.equal(card.stateText, '—');
  assert.doesNotMatch(card.line, /1,25/);
  assert.match(card.line, /pressão do gás/);
});

test('sem o módulo (WebView antigo) o cartão não cai', () => {
  const card = load(false).CartoesMotor.build(DONO).find(c => c.id === 'volta');
  assert.match(card.line, /pressão do gás/);
});

test('− / + andam 0,05 bar dentro de 0,2..2,0; Espera gira 0→1→2→3→0 s; Salvar manda só o que mudou', () => {
  const V = load().VoltaGasolina;
  const m = V.read(DONO);
  assert.equal(V.step(1250, -1), 1200);
  assert.equal(V.step(1250, +1), 1300);
  assert.equal(V.step(200, -1), 200);
  assert.equal(V.step(2000, +1), 2000);
  assert.equal(V.step(1199, +1), 1250, 'parte do valor mostrado (1,20), não do truncado');
  assert.deepEqual(plain([0, 1, 2, 3].map(V.nextWait)), [1, 2, 3, 0]);
  assert.equal(V.saveRequest(m, {}), null);
  assert.deepEqual(plain(V.saveRequest(m, { mbar: 1300 })), { acao: 'salvar', mbar: 1300 });
  assert.deepEqual(plain(V.saveRequest(m, { segundos: 2, mbar: 1250 })), { acao: 'salvar', segundos: 2 });
  assert.equal(V.barText(1199), '1,20', 'o original trunca 1,2 bar para 1199 mbar; a tela mostra com o passo de 0,005 do original');
});

test('estado da gravação: Gravando… → Salvo e conferido só com CONFIRMED; falha diz o que fazer', () => {
  const V = load().VoltaGasolina;
  assert.deepEqual(plain(V.phaseOf({ state: 'WRITING', busy: true })), { phase: 'writing', text: 'Gravando e conferindo…' });
  assert.deepEqual(plain(V.phaseOf({ state: 'DONE', outcome: 'CONFIRMED', busy: false, message: 'x' })), { phase: 'done', text: 'Salvo e conferido na central ✓' }, 'o aviso usa a palavra do botão (Salvar)');
  const bad = V.phaseOf({ state: 'DONE', outcome: 'PARTIAL', busy: false, message: 'A central parou de confirmar no meio. Toque em Desfazer para voltar ao que estava.' });
  assert.equal(bad.phase, 'error');
  assert.match(bad.text, /Desfazer/);
});
