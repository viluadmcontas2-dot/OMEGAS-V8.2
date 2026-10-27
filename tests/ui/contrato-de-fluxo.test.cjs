const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const { documentMinimo } = require('./helpers/dom-minimo.cjs');
const ui = path.resolve(__dirname, '../../app/src/main/assets/ui');
function load(dom = true, extra = {}) {
  const ctx = { ...extra };
  for (const file of ['core/write-gate.js', 'components/acao-principal.js', 'core/fluxo.js']) {
    const source = path.join(ui, file);
    assert.ok(fs.existsSync(source), 'API ausente: ' + file);
    vm.runInNewContext(fs.readFileSync(source, 'utf8'), ctx);
  }
  const host = dom ? documentMinimo().createElement('section') : null;
  return { f: ctx.OmegasFluxo, host, ctx };
}
test('principal e Desfazer preservam identidade e visibilidade em todos os estados', async () => {
  const { f, host } = load();
  const p = f.mountPrimary(host, { id: 'map', base: 'Gravar N células' });
  const last = f.mountLast(host, { id: 'map' });
  const undo = host.querySelector('[data-fluxo="desfazer"]');
  assert.match(host.textContent, /Nenhuma gravação ainda/);
  assert.equal(undo.getAttribute('aria-disabled'), 'true');
  let clicks = 0;
  for (const state of [{ enabled: false, reason: 'Escolha um trecho' }, { enabled: true, count: 2, onTap: () => clicks++ }, { busy: true }, { busy: false }]) {
    p.set(state);
    assert.equal(host.querySelector('[data-fluxo="principal"]'), p.el);
    assert.equal(p.el.hidden, false); assert.notEqual(p.el.style.display, 'none');
  }
  p.set({ enabled: false, reason: 'Escolha um trecho' }); p.el.click();
  assert.equal(clicks, 0); assert.match(p.el.textContent, /Escolha um trecho/);
  p.set({ enabled: true, count: 2 }); p.el.click(); assert.equal(clicks, 1);
  assert.match(p.el.textContent, /Gravar 2 células/);
  last.record({ what: 'Primeira', undo: { run: async () => true } });
  const first = last.view().id;
  last.record({ what: 'Segunda', by: 'Refino', undo: { run: async () => true } });
  assert.equal(last.view().what, 'Segunda'); assert.equal(f.history()[0].id, first);
  assert.equal(f.history()[1].by, 'Refino');
  assert.equal((await f.undoById(last.view().id)).ok, true);
  assert.equal(undo.getAttribute('aria-disabled'), 'true');
  last.record({ what: 'Terceira' }); assert.equal(last.view().what, 'Terceira');
  assert.equal(host.querySelector('[data-fluxo="desfazer"]'), undo);
  assert.equal(host.querySelector('[data-fluxo="principal"]'), p.el);
  assert.equal(f.registry()[0].el, p.el);
  assert.equal(f.mountPrimary(host, { id: 'map' }), p);
  last.clear(); assert.match(host.textContent, /Nenhuma gravação ainda/);
});
test('run bloqueia na porta, só registra após verificar e aceita selo opcional', async () => {
  const { f, host } = load();
  const p = f.mountPrimary(host, { id: 'a', base: 'Gravar correção' });
  const last = f.mountLast(host, { id: 'a' }); let writes = 0; const events = [];
  const app = { writeVerify: { begin: () => events.push('begin') }, operationStatus: { clear: () => events.push('clear'), done: () => events.push('done') } };
  const opts = { id: 'a', owner: 'teste', app, autopilot: { ecuDone: false }, write: async () => { writes++; return { type: 'BATCH_CONFIRMED' }; }, verify: async () => { events.push('verify'); return true; }, record: { what: 'Mapa' } };
  assert.equal((await f.run(opts)).ok, false); assert.equal(writes, 0);
  assert.match(p.el.textContent, /Espere/); assert.equal(p.el.getAttribute('aria-disabled'), 'true');
  opts.autopilot.ecuDone = true;
  assert.equal((await f.run(opts)).ok, true); assert.equal(last.view().what, 'Mapa');
  assert.deepEqual(events, ['begin', 'verify', 'clear', 'done']);
  delete app.operationStatus.done; assert.equal((await f.run(opts)).ok, true);
  assert.equal(f.history().length, 2);
});
test('run falha sem readback positivo e oferece retry/leave/details sem repetir sozinho', async () => {
  const { f } = load(false); let writes = 0;
  const opts = { id: 'a', write: async () => { writes++; return {}; }, verify: async () => false };
  const failure = await f.run(opts);
  assert.equal(failure.ok, false); assert.equal(writes, 1); assert.equal(f.history().length, 0);
  assert.equal(typeof failure.retry, 'function'); assert.equal(typeof failure.leave, 'function'); assert.ok(failure.details);
  failure.leave(); assert.equal(writes, 1);
  opts.verify = async () => true; assert.equal((await failure.retry()).ok, true);
});
test('pending usa backend injetável, sobrevive nova instância e tolera storage proibido/corrompido', () => {
  const values = new Map(); const storage = { setItem: (k,v) => values.set(k,v), getItem: k => values.get(k) ?? null, removeItem: k => values.delete(k) };
  const { f } = load(false); f.pending.use(storage); assert.equal(f.pending.save('map', { stage: 2 }), true);
  const second = load(false).f; second.pending.use(storage);
  assert.equal(second.pending.get('map').stage, 2); second.pending.clear('map'); assert.equal(second.pending.get('map'), null);
  f.pending.use({ getItem() { throw Error('negado'); }, setItem() { throw Error('negado'); }, removeItem() { throw Error('negado'); } });
  assert.equal(f.pending.save('x', {}), false); assert.equal(f.pending.get('x'), null); assert.equal(f.pending.clear('x'), false);
  f.pending.use({ getItem: () => '{' }); assert.equal(f.pending.get('x'), null);
});
test('snapshot executa coletor e comparação detecta ausências, visibilidade e movimento >2px', async () => {
  const file = path.resolve(__dirname, '../../tools/ci/_contrato_fluxo.cjs');
  assert.ok(fs.existsSync(file), 'coletor ausente');
  const { snapshot, compararEstados } = require(file);
  const { f, host } = load(); f.mountPrimary(host, { id: 'a', base: 'Gravar' });
  const page = { evaluate: (fn, args) => vm.runInNewContext('(' + fn.toString() + ')(args)', { args, document: host, getComputedStyle: el => ({ display: el.style.display || 'block', visibility: 'visible', opacity: '1' }) }) };
  const a = await snapshot(page, ['[data-fluxo="principal"]']);
  assert.equal(a[0].hidden, false);
  const copy = patch => [{ ...a[0], ...patch }];
  assert.equal(compararEstados(a, copy({ x: 2 })).length, 0);
  assert.equal(compararEstados(a, copy({ x: 3 }))[0].tipo, 'mudou de lugar');
  assert.equal(compararEstados(a, copy({ hidden: true }))[0].tipo, 'escondido');
  assert.equal(compararEstados(a, [])[0].tipo, 'sumiu');
  assert.equal(compararEstados([], a)[0].tipo, 'apareceu');
});
test('readback pendente mantém o mesmo botão ocupado e rejeita gravação concorrente', async () => {
  const { f, host } = load();
  const p = f.mountPrimary(host, { id: 'a', base: 'Gravar correção' });
  f.mountLast(host, { id: 'a' });
  let release; let written = 0;
  const verification = new Promise(resolve => { release = resolve; });
  const options = { id: 'a', write: async () => { written++; return { type: 'BATCH_CONFIRMED' }; }, verify: () => verification };
  const result = f.run(options);
  await Promise.resolve();
  assert.equal(p.el.textContent, 'Gravando…'); assert.equal(p.el.getAttribute('aria-disabled'), 'true');
  assert.equal(f.history().length, 0);
  assert.equal((await f.run(options)).ok, false); assert.equal(written, 1);
  release(true); assert.equal((await result).ok, true);
  assert.equal(host.querySelector('[data-fluxo="principal"]'), p.el);
  assert.equal(p.el.hidden, false); assert.match(p.el.textContent, /Nada mais para gravar agora/);
});
test('falha de Desfazer preserva registro e permite nova tentativa explícita', async () => {
  const { f, host } = load(); const last = f.mountLast(host, { id: 'a' }); let allowed = false;
  last.record({ what: 'Mapa', undo: { run: async () => allowed } });
  const failure = await f.undoById(last.view().id);
  assert.equal(failure.ok, false); assert.notEqual(last.view().undone, true);
  allowed = true; assert.equal((await failure.retry()).ok, true);
  assert.equal(last.view().undone, true); assert.equal((await f.undoById(last.view().id)).ok, false);
});
test('storage padrão tolera getter proibido e CommonJS carrega sem DOM', () => {
  const extra = {}; Object.defineProperty(extra, 'localStorage', { enumerable: false, get() { throw Error('proibido'); } });
  const { f, ctx } = load(false);
  Object.defineProperty(ctx, 'localStorage', { get() { throw Error('proibido'); } });
  assert.equal(f.pending.save('a', {}), false); assert.equal(f.pending.get('a'), null);
  const common = require(path.join(ui, 'core/fluxo.js'));
  assert.equal(typeof common.run, 'function');
});
test('callback de registro inválido falha antes de enviar bytes', async () => {
  const { f } = load(false); let writes = 0;
  const result = await f.run({ id: 'a', record: () => { throw Error('prévia inválida'); }, write: async () => { writes++; }, verify: async () => true });
  assert.equal(result.ok, false); assert.equal(writes, 0);
});
