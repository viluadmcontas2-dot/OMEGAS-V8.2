'use strict';

// Tropeços/apagões do GNV → células do Mapa K (dono, 2026-10-13). O app só lê o que a ECU manda; aqui só se escolhe onde propor.
const test = require('node:test');
const assert = require('node:assert/strict');
const { suggest, covering, MIN_EVENTS, MAX_PERCENT } = require('../../app/src/main/assets/ui/core/stall-region.js');

const PETROL_BINS = [2.0, 2.5, 3.0, 3.5, 4.5, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0];
const RPM_BINS = [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500];
const near = (fromMs, count, rpmLow, rpmHigh, extra = {}) => ({ fromMs, toMs: fromMs + 0.5, count, mapBar: 0.25, rpmBefore: (rpmLow + rpmHigh) / 2, rpmLow, rpmHigh, afterCutoff: 1, ...extra });

test('covering: da linha de baixo até a de cima, como o Kotlin', () => {
  assert.deepEqual(covering(PETROL_BINS, 2.0, 2.5), [0, 1]);
  assert.deepEqual(covering(PETROL_BINS, 2.2, 2.9), [0, 1, 2]);
  assert.deepEqual(covering(RPM_BINS, 1500, 1700), [1, 2]);
  assert.deepEqual(covering([], 1, 2), []);
});

test('evidência suficiente: propõe linhas da faixa × colunas do RPM, +5% (um passo pequeno)', () => {
  const out = suggest({ nearRegions: [near(2.0, 4, 1400, 1700)] }, { petrolBins: PETROL_BINS, rpmBins: RPM_BINS });
  assert.equal(out.ok, true);
  assert.equal(out.percent, 5);
  assert.deepEqual([...new Set(out.cells.map(c => c.row))], [0, 1]);
  assert.deepEqual([...new Set(out.cells.map(c => c.column))], [1, 2]);
  assert.equal(out.cells.length, 4);
  assert.ok(out.cells.every(c => c.deltaPercent === 5));
});

test('um apagão real sozinho já basta para propor (o motor morreu)', () => {
  const one = suggest({ regions: [{ fromMs: 2.0, toMs: 2.5, count: 1, mapBar: 0.23, rpmBefore: 1236, rpmLow: 1236, rpmHigh: 1236 }] }, { petrolBins: PETROL_BINS, rpmBins: RPM_BINS });
  assert.equal(one.ok, true);
  assert.equal(one.percent, 5);
  assert.ok(one.cells.every(c => c.deltaPercent === 5));
});

test('pouca evidência (< mínimo): não propõe e diz por quê; sem Mapa K lido: também não', () => {
  const few = suggest({ nearRegions: [near(2.0, MIN_EVENTS - 1, 1400, 1700)] }, { petrolBins: PETROL_BINS, rpmBins: RPM_BINS });
  assert.equal(few.ok, false);
  assert.match(few.reason, /preciso de 3/);
  const none = suggest({ nearRegions: [near(2.0, 5, 1400, 1700)] }, {});
  assert.equal(none.ok, false);
  assert.match(none.reason, /Mapa K ainda não foi lido/);
  assert.equal(suggest({}, { petrolBins: PETROL_BINS, rpmBins: RPM_BINS }).ok, false);
});

test('apagão pesa 2, muita evidência sobe para +10%, e nunca passa do passo máximo do ajuste local', () => {
  const stalls = { regions: [{ fromMs: 2.0, toMs: 2.5, count: 2, mapBar: 0.3, rpmBefore: 1500, rpmLow: 1400, rpmHigh: 1600 }], nearRegions: [near(2.0, 3, 1400, 1700)] };
  const out = suggest(stalls, { petrolBins: PETROL_BINS, rpmBins: RPM_BINS });
  assert.equal(out.events, 5);
  assert.equal(out.percent, 10);
  assert.ok(out.percent <= MAX_PERCENT);
});

test('RPM muito espalhado: no máximo 3 colunas, as mais perto do meio', () => {
  const out = suggest({ nearRegions: [near(3.0, 5, 900, 3200)] }, { petrolBins: PETROL_BINS, rpmBins: RPM_BINS });
  assert.equal(out.ok, true);
  assert.ok(new Set(out.cells.map(c => c.column)).size <= 3);
});

test('escolhe a faixa mais forte entre várias, usando o Mapa lido (eixos diferentes funcionam)', () => {
  const out = suggest({ nearRegions: [near(2.0, 3, 1400, 1500), near(3.0, 8, 1300, 1500)] }, { petrolBins: [1.5, 3.0, 5.0], rpmBins: [800, 1400, 2000] });
  assert.equal(out.ok, true);
  assert.equal(out.region.fromMs, 3.0);
  assert.deepEqual([...new Set(out.cells.map(c => c.row))], [1, 2]);
});
