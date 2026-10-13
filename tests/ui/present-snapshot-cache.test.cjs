'use strict';

// F4a: presentSnapshot() pergunta à ponte só "mudou desde a sequência N?". Sem quadro novo o Kotlin não monta
// nem serializa nada (resposta leve) e a tela recebe o último quadro com a idade andando; quadro novo chega na hora.
// OMEGAS_ROOT permite rodar contra outra cópia (ex.: o código anterior, que deve falhar).

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = process.env.OMEGAS_ROOT || path.resolve(__dirname, '../..');
const UI = path.join(ROOT, 'app/src/main/assets/ui');
const read = rel => fs.readFileSync(path.join(UI, rel), 'utf8');

/** Relógio controlado do contexto (Date.now) e da ponte falsa. */
function fakeClock(start) {
  const clock = { now: start };
  class FakeDate extends Date {
    constructor(...args) { super(...(args.length ? args : [clock.now])); }
    static now() { return clock.now; }
  }
  clock.Date = FakeDate;
  return clock;
}

/**
 * Ponte falsa com o contrato do HubJavascriptBridge: completo `{ok, revision, data, changed:true}` só quando a
 * sequência mudou (ou nunca foi entregue); senão `{ok, changed:false, revision, sequence, telemetryAgeMs}`.
 */
function fakeBridge(clock, options) {
  const withAge = !(options && options.withAge === false);
  const bridge = {
    sequence: 1, served: -1, valid: true, updatedAt: clock.now - 40,
    frame: { rpm: 2000, petrol_ms: 4.2, load_bar: 0.55, fuel: 'GNV' },
    full: 0, light: 0, legacy: 0, asked: [],
    age() { return this.valid ? clock.now - this.updatedAt : -1; },
    newFrame(rpm) { this.sequence += 1; this.updatedAt = clock.now; this.frame = { ...this.frame, rpm }; },
    envelope() {
      const age = this.age();
      return {
        ok: true, revision: this.full + this.legacy,
        data: {
          ok: true, sequence: this.sequence, valid: this.valid, ageMs: age, telemetryAgeMs: age, live: { ...this.frame },
          interpolation: { valid: true, rpm: this.frame.rpm, petrolMs: this.frame.petrol_ms, sequence: this.sequence, cell: { row: 4, column: 3 } },
        },
      };
    },
    getPresentSnapshotIfChanged(last) {
      this.asked.push(last);
      if (last >= 0 && last === this.sequence && this.served === this.sequence) {
        this.light += 1;
        const reply = { ok: true, changed: false, revision: this.sequence, sequence: this.sequence };
        if (withAge) reply.telemetryAgeMs = this.age();
        return JSON.stringify(reply);
      }
      this.full += 1;
      this.served = this.sequence;
      return JSON.stringify({ ...this.envelope(), changed: true });
    },
    getPresentSnapshot() { this.legacy += 1; return JSON.stringify(this.envelope()); },
  };
  return bridge;
}

function nativeApi(bridge, clock) {
  const context = { console, Date: clock.Date, OmegasNative: bridge };
  context.window = context;
  vm.createContext(context);
  vm.runInContext(read('core/native-api.js'), context, { filename: 'native-api.js' });
  return new context.OmegasUi.NativeApi();
}

test('sem quadro novo: a ponte não monta o quadro e a tela recebe o último com a idade andando', () => {
  const clock = fakeClock(1_000_000);
  const bridge = fakeBridge(clock);
  const api = nativeApi(bridge, clock);
  const first = api.presentSnapshot();
  assert.equal(first.data.live.rpm, 2000);
  assert.equal(first.data.telemetryAgeMs, 40);
  let previous = first.data;
  for (let step = 1; step <= 10; step += 1) {
    clock.now += 200;
    const envelope = api.presentSnapshot();
    assert.equal(envelope.ok, true);
    assert.equal(envelope.data.live.rpm, 2000, 'mesmo quadro');
    assert.equal(envelope.data.sequence, 1);
    assert.equal(envelope.data.telemetryAgeMs, 40 + step * 200, 'idade andando');
    assert.equal(envelope.data.ageMs, 40 + step * 200);
    assert.notEqual(envelope.data, previous, 'objeto novo a cada idade');
    previous = envelope.data;
  }
  assert.equal(bridge.full, 1, 'um quadro completo só');
  assert.equal(bridge.light, 10, 'dez respostas leves');
  assert.equal(bridge.legacy, 0);
  assert.deepEqual(bridge.asked, [-1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1]);
  assert.equal(first.data.telemetryAgeMs, 40, 'o quadro guardado não é alterado');
});

test('quadro novo: RPM novo chega na primeira pergunta depois dele', () => {
  const clock = fakeClock(2_000_000);
  const bridge = fakeBridge(clock);
  const api = nativeApi(bridge, clock);
  api.presentSnapshot();
  clock.now += 300;
  api.presentSnapshot();
  bridge.newFrame(2350);
  clock.now += 50;
  const envelope = api.presentSnapshot();
  assert.equal(envelope.data.live.rpm, 2350);
  assert.equal(envelope.data.interpolation.rpm, 2350);
  assert.equal(envelope.data.sequence, 2);
  assert.equal(envelope.data.telemetryAgeMs, 50);
  assert.equal(bridge.full, 2);
  clock.now += 100;
  assert.equal(api.presentSnapshot().data.live.rpm, 2350);
  assert.equal(bridge.light, 2);
});

test('sem idade na resposta leve, a idade anda pelo relógio JS; idade desconhecida continua desconhecida', () => {
  const clock = fakeClock(3_000_000);
  const bridge = fakeBridge(clock, { withAge: false });
  const api = nativeApi(bridge, clock);
  assert.equal(api.presentSnapshot().data.telemetryAgeMs, 40);
  clock.now += 600;
  assert.equal(api.presentSnapshot().data.telemetryAgeMs, 640);
  clock.now += 11_000;
  const late = api.presentSnapshot();
  assert.equal(late.data.telemetryAgeMs, 11_640, 'passa de 10 s e continua andando');
  assert.equal(late.data.ageMs, 11_640);

  const offline = fakeBridge(clock, { withAge: false });
  offline.valid = false;
  const unknown = nativeApi(offline, clock);
  assert.equal(unknown.presentSnapshot().data.telemetryAgeMs, -1);
  clock.now += 5_000;
  assert.equal(unknown.presentSnapshot().data.telemetryAgeMs, -1, 'desconhecida não vira número inventado');
  assert.equal(offline.light, 1);
});

test('erro da ponte limpa o guardado; depois volta pedindo o quadro completo', () => {
  const clock = fakeClock(4_000_000);
  const bridge = fakeBridge(clock);
  const api = nativeApi(bridge, clock);
  api.presentSnapshot();
  const healthy = bridge.getPresentSnapshotIfChanged;
  bridge.getPresentSnapshotIfChanged = function () { return JSON.stringify({ ok: false, error: 'Serviço indisponível' }); };
  const failed = api.presentSnapshot();
  assert.equal(failed.ok, false);
  bridge.getPresentSnapshotIfChanged = healthy;
  bridge.asked.length = 0;
  const recovered = api.presentSnapshot();
  assert.deepEqual(bridge.asked, [-1], 'sem guardado: pede o quadro inteiro');
  assert.equal(recovered.data.live.rpm, 2000);
  assert.equal(bridge.full, 2);
});

test('ponte antiga (sem getPresentSnapshotIfChanged) continua com getPresentSnapshot a cada pergunta', () => {
  const clock = fakeClock(5_000_000);
  const bridge = fakeBridge(clock);
  delete bridge.getPresentSnapshotIfChanged;
  const api = nativeApi(bridge, clock);
  for (let index = 0; index < 3; index += 1) {
    clock.now += 100;
    assert.equal(api.presentSnapshot().data.live.rpm, 2000);
  }
  assert.equal(bridge.legacy, 3);
});

/** app.js de verdade sobre a NativeApi de verdade e a ponte falsa: a idade e o RPM da tela não congelam. */
function bootApp(bridge, clock) {
  const document = {
    getElementById: () => null, querySelector: () => null, querySelectorAll: () => [],
    createElement: () => ({ dataset: {} }), head: { appendChild() {} }, addEventListener() {},
  };
  const context = {
    console, document, Date: clock.Date, OmegasNative: bridge,
    setInterval: () => 1, clearInterval() {}, setTimeout: () => 1, clearTimeout() {},
    addEventListener() {}, localStorage: { getItem: () => null, setItem() {} },
  };
  context.window = context;
  context.globalThis = context;
  vm.createContext(context);
  for (const file of ['core/native-api.js', 'core/store.js', 'core/router.js', 'core/scheduler.js', 'app.js']) {
    vm.runInContext(read(file), context, { filename: file });
  }
  return context.OmegasApp;
}

test('tela inteira: sem quadro novo a idade guardada no estado anda (inclusive após 10 s); quadro novo troca o RPM', () => {
  const clock = fakeClock(6_000_000);
  const bridge = fakeBridge(clock);
  const app = bootApp(bridge, clock);
  assert.equal(app.store.get().route, 'autocal');
  const ages = [];
  for (let tick = 0; tick < 70; tick += 1) {
    app.scheduler.onFast();
    ages.push(app.store.get().telemetry.telemetryAgeMs);
    clock.now += 200;
  }
  const seen = [...new Set(ages)];
  assert.ok(seen.length >= 20, 'a idade guardada mudou ' + seen.length + ' vezes');
  assert.ok(Math.max(...seen) >= 13_000, 'passou de 13 s: ' + Math.max(...seen));
  assert.equal(app.store.get().telemetry.live.rpm, 2000);
  assert.equal(bridge.full, 1, 'catorze segundos sem quadro novo: um quadro completo só');
  bridge.newFrame(2600);
  app.scheduler.onFast();
  assert.equal(app.store.get().telemetry.live.rpm, 2600, 'RPM novo na tela no primeiro tick');
  assert.equal(app.store.get().telemetry.telemetryAgeMs, 0);
  assert.equal(bridge.full, 2);
});
