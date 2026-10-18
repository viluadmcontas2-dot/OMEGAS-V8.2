'use strict';
// A bancada (ECU em JS) tem que se comportar como o emulador de referência (tools/emulacao/ecu_emulator.py, regras observadas
// no LOGNOVO). Sem python3 o cruzamento é pulado; a parte em JS puro roda sempre.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { EcuJs, pedido, H, B } = require('./helpers/ecu-bench.js');
const fixture = require('./fixtures/ecu-estado-lognovo.json');

const py = spawnSync('python3', ['--version']);
const temPython = py.status === 0;
const LOG = process.env.OMEGAS_LOGNOVO || '/tmp/claude-0/-home-user/57e20309-4640-5e31-b02d-4b2faf9cf2b3/scratchpad/portmon/x/PortmonLOGNOVO.LOG';

test('quadros da ECU: eco + ACK + n + dados + soma; escrita atualiza a leitura; pedido desconhecido só ecoa', () => {
  const ecu = new EcuJs(fixture);
  const rd = pedido(0x29, 0xFA, 0x00);
  assert.equal(H(ecu.transact(rd)), '29 FA 00 23 53 04 00 00 00 00 57');
  const wr = pedido(0x35, 0xFA, 0x00, 0x20, 0, 0, 0);
  assert.equal(H(wr), '35 FA 00 20 00 00 00 4F');
  assert.equal(H(ecu.transact(wr)), '35 FA 00 20 00 00 00 4F 53 00 53');
  assert.equal(H(ecu.transact(rd)), '29 FA 00 23 53 04 20 00 00 00 77', 'mesmo quadro de leitura que a captura mostra para esse vetor');
  assert.equal(H(ecu.transact(pedido(0x29, 0x99, 0x09))), '29 99 09 CB');
  assert.deepEqual(ecu.sent('WRITE').map(f => f.hex), ['35 FA 00 20 00 00 00 4F']);
});

test('pedido com soma errada: a ECU só ecoa e nada muda', () => {
  const ecu = new EcuJs(fixture);
  const ruim = [0x35, 0xFA, 0x00, 0x20, 0, 0, 0, 0x00];
  assert.deepEqual(ecu.transact(ruim), ruim);
  assert.deepEqual(ecu.get('29 FA 00 23'), [0, 0, 0, 0]);
});

test('cruzamento com o emulador Python: mesmas respostas para todas as leituras e para as escritas do Avançado', { skip: temPython ? false : 'sem python3' }, () => {
  const script = `
import sys, json
sys.path.insert(0, ${JSON.stringify(path.resolve(__dirname, '../../tools/emulacao'))})
import ecu_emulator as E
fx = json.load(open(${JSON.stringify(path.resolve(__dirname, 'fixtures/ecu-estado-lognovo.json'))}))
e = E.EcuEmulada()
for k, v in fx['leituras'].items():
    e.estado[bytes.fromhex(k)] = ('quadro', E.ACK, bytes.fromhex(v))
reqs = json.load(sys.stdin)
print(json.dumps([e.responder(bytes.fromhex(r)).hex(' ').upper() for r in reqs]))`;
  const reqs = [...Object.keys(fixture.leituras),
    '35 FA 00 20 00 00 00 4F', '29 FA 00 23', '35 FA 00 00 00 00 00 2F', '29 FA 00 23',
    '14 54 00 02 03 B4 ' + H([sumOf([0x14, 0x54, 0, 2, 3, 0xB4])]), '2A 54 00 02 80',
    '14 61 01 05 00 40 ' + H([sumOf([0x14, 0x61, 1, 5, 0, 0x40])]), '29 61 01 8B',
    '02 24 04 08 32', '29 99 09 CB'];
  const r = spawnSync('python3', ['-I', '-c', script], { input: JSON.stringify(reqs), encoding: 'utf8' });
  assert.equal(r.status, 0, r.stderr);
  const esperado = JSON.parse(r.stdout);
  const ecu = new EcuJs(fixture);
  const got = reqs.map(q => H(ecu.transact(B(q))));
  reqs.forEach((q, i) => assert.equal(got[i], esperado[i], 'pedido ' + q));
});
function sumOf(a) { return a.reduce((x, y) => (x + y) & 255, 0); }

test('a fixture versionada é o estado real da captura (só roda com o LOG original presente)', { skip: fs.existsSync(LOG) && temPython ? false : 'LOG original ausente' }, () => {
  const tmp = path.join(require('node:os').tmpdir(), 'fx-e2e-' + process.pid + '.json');
  const r = spawnSync('python3', ['-B', '-I', path.resolve(__dirname, '../../tools/ci/gerar_fixture_e2e.py'), LOG, tmp], { encoding: 'utf8' });
  assert.equal(r.status, 0, r.stderr);
  assert.equal(fs.readFileSync(tmp, 'utf8'), fs.readFileSync(path.resolve(__dirname, 'fixtures/ecu-estado-lognovo.json'), 'utf8'));
  fs.unlinkSync(tmp);
});

test('fixture: valores do dono (FLAG_CONF3 0000 0000, ANTI_STALLO 1600 100 1500 2 20) e telemetria com CNG', () => {
  assert.equal(fixture.leituras['29 FA 00 23'], '00 00 00 00');
  assert.equal(fixture.leituras['29 33 01 5D'], '40 06 64 00 DC 05 02 00 14 00');
  assert.ok(fixture.telemetria_48_01.length >= 10 && fixture.telemetria_48_01.every(f => B(f).length >= 20));
});
