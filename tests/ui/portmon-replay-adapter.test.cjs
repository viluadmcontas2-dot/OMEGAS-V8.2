'use strict';

const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');

const { PortmonReplayAdapter, PortmonReplayError } = require('../../app/src/main/assets/ui/portmon-replay-adapter.js');
const corpus = JSON.parse(fs.readFileSync(path.resolve('tests/fixtures/portmon-autocal-real-sample.json'), 'utf8'));

function makeAdapter() {
  return new PortmonReplayAdapter(corpus);
}

test('reproduz respostas reais em ordem e reinicia deterministicamente', () => {
  const adapter = makeAdapter();
  const first = adapter.exchange('48 01 49');
  const second = adapter.exchange('48 01 49');

  assert.equal(first.sequence, 1);
  assert.equal(second.sequence, 2);
  assert.notEqual(first.response, second.response, 'telemetria real deveria variar entre amostras');
  assert.ok(first.response.startsWith('48 01 49'));

  adapter.reset();
  assert.deepEqual(adapter.exchange('48 01 49'), first);
});

test('mantém proveniência pelos hashes do corpus completo', () => {
  const adapter = makeAdapter();
  assert.equal(adapter.source.originalSha256, '4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b');
  assert.equal(adapter.source.compressedSha256, '202ff799ca3bba653986ce000ed69d4b3049fdf0aef6d614e605a3ca4d959deb');
});

test('comandos diferentes conservam suas próprias posições', () => {
  const adapter = makeAdapter();
  assert.equal(adapter.exchange('29 1d 00 46').sequence, 10);
  assert.equal(adapter.exchange('48 01 49').sequence, 1);
  assert.equal(adapter.exchange('29 1D 00 46').sequence, 11);
  assert.equal(adapter.exchange('09 21 00 2A').sequence, 15);
});

test('comando ausente falha fechado', () => {
  const adapter = makeAdapter();
  assert.throws(
    () => adapter.exchange('00 25 25'),
    error => error instanceof PortmonReplayError && error.code === 'UNKNOWN_COMMAND',
  );
});

test('timeout não devolve resposta nem avança a sequência', () => {
  const adapter = makeAdapter();
  adapter.setMode('TIMEOUT');
  assert.throws(
    () => adapter.exchange('48 01 49'),
    error => error instanceof PortmonReplayError && error.code === 'TIMEOUT',
  );
  adapter.setMode('NORMAL');
  assert.equal(adapter.exchange('48 01 49').sequence, 1);
});

test('truncamento e corrupção são reproduzíveis e não alteram o corpus', () => {
  const normal = makeAdapter().exchange('09 21 00 2A');

  const truncatedAdapter = makeAdapter();
  truncatedAdapter.setMode('TRUNCATE');
  const truncated = truncatedAdapter.exchange('09 21 00 2A');
  assert.equal(truncated.response.split(' ').length, normal.response.split(' ').length - 1);

  const corruptAdapter = makeAdapter();
  corruptAdapter.setMode('CORRUPT');
  const corrupt = corruptAdapter.exchange('09 21 00 2A');
  assert.notEqual(corrupt.response, normal.response);
  assert.equal(corrupt.response.split(' ').length, normal.response.split(' ').length);

  assert.equal(makeAdapter().exchange('09 21 00 2A').response, normal.response);
});
