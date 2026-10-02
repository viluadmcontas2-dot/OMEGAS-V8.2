'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { decodeEnvelope } = require('../../app/src/main/assets/ui/portmon-frame-decoder.js');

test('decodifica resposta real 48 01 49 e valida checksum sem somar eco', () => {
  const decoded = decodeEnvelope(
    '48 01 49',
    '48 01 49 53 22 65 03 32 1F 00 00 00 00 EC 06 00 80 2C B1 FA 09 39 93 01 00 00 00 00 00 00 00 00 00 EA 06 00 00 00 00 3D',
  );
  assert.equal(decoded.valid, true);
  assert.equal(decoded.statusHex, '53');
  assert.equal(decoded.receivedChecksum, 0x3D);
  assert.equal(decoded.calculatedChecksum, 0x3D);
  assert.equal(decoded.payload.length, 35);
});

test('rejeita resposta curta sem eco completo', () => {
  const decoded = decodeEnvelope('48 01 49', '01');
  assert.equal(decoded.valid, false);
  assert.equal(decoded.echoed, false);
});

test('detecta corrupção de um byte', () => {
  const decoded = decodeEnvelope('09 21 00 2A', '09 21 00 2A 53 01 00 55');
  assert.equal(decoded.echoed, true);
  assert.equal(decoded.checksumValid, false);
});
