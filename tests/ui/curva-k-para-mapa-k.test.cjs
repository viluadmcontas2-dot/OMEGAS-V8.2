'use strict';

// "Curva K → Mapa K" próprio do OMEGAS. Fórmula tirada do ExportToK do ProgBase (desmontagem, PROTOCOLO-LANDI doc 26):
// por linha do Mapa K, x = Tinj da linha (ms); acha o par de nós da curva K com TBP[a] ≤ x ≤ TBP[b];
// K = y0 + (y1−y0)·(x−x0)/(x1−x0); cada célula da linha é multiplicada por K. Linhas fora de [TBP[0], TBP[último]] não mudam.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const { plan, lerp } = require('../../app/src/main/assets/ui/core/curve-to-map.js');
const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/autocal-captura-ac-t1000.json'), 'utf8'));
const phys = name => fixture.snapshot.fields.find(f => f.key === name).physicalValues;
const TBP = phys('PETR_INJ_TBP');
const K = phys('MUL_ACT');
// Eixo de linhas do Mapa K da captura (TEMPI_PER_K × 0,00256 ms): 2,0 … 18 ms.
const ROWS_MS = [781, 977, 1172, 1367, 1758, 2344, 3125, 3906, 4687, 5469, 6250, 7031].map(v => v * 0.00256);

test('interpolação linear entre dois nós, como no original', () => {
  assert.equal(lerp(1, 0, 2, 10, 20), 15);
  assert.equal(lerp(2, 0, 2, 10, 20), 20);
  assert.equal(lerp(0, 0, 2, 10, 20), 10);
});

test('nó a nó: x exatamente em um nó devolve o K desse nó', () => {
  const result = plan({ tbp: [1, 2, 3], k: [1.0, 1.5, 1.2], rowMs: [1, 2, 3] });
  assert.deepEqual(result.rows.map(r => r.k), [1.0, 1.5, 1.2]);
});

test('entre nós: K cresce linearmente (caso do original)', () => {
  const result = plan({ tbp: [2, 4], k: [1.0, 1.4], rowMs: [3] });
  assert.ok(Math.abs(result.rows[0].k - 1.2) < 1e-12);
  assert.equal(result.rows[0].status, 'ok');
});

test('fora da faixa da curva a linha NÃO muda (como o original) e é dita', () => {
  const result = plan({ tbp: [2, 4], k: [1.0, 1.4], rowMs: [1.5, 4.5] });
  assert.deepEqual(result.rows.map(r => [r.status, r.k]), [['abaixo', null], ['acima', null]]);
  assert.equal(result.rows.filter(r => r.status === 'ok').length, 0);
});

test('nós repetidos (divisão por zero) não zeram a linha: ela é pulada', () => {
  const result = plan({ tbp: [2, 2, 4], k: [1.0, 1.2, 1.4], rowMs: [2] });
  assert.notEqual(result.rows[0].status, 'ok-zero');
  assert.ok(result.rows[0].k === null || result.rows[0].k > 0);
});

test('nós com Tinj 0 ou K inválido são ignorados; K ≤ 0 nunca vira fator', () => {
  const result = plan({ tbp: [0, 2, 4], k: [1.0, 0, 1.4], rowMs: [3] });
  assert.equal(result.rows[0].status, 'sem-curva');
});

test('com a captura real: 12 linhas dentro da curva e K igual à interpolação manual', () => {
  const result = plan({ tbp: TBP, k: K, rowMs: ROWS_MS });
  assert.equal(result.rows.length, 12);
  assert.ok(result.rows.every(r => r.status === 'ok'));
  const manual = ROWS_MS.map(x => {
    const i = TBP.findIndex((v, n) => n < TBP.length - 1 && v <= x && x <= TBP[n + 1]);
    return K[i] + (K[i + 1] - K[i]) * (x - TBP[i]) / (TBP[i + 1] - TBP[i]);
  });
  result.rows.forEach((r, n) => assert.ok(Math.abs(r.k - manual[n]) < 1e-12, 'linha ' + n));
});

test('curva neutra (tudo 1,0): nenhuma linha muda', () => {
  const result = plan({ tbp: TBP, k: TBP.map(() => 1), rowMs: ROWS_MS });
  assert.equal(result.rows.every(r => r.percent === 0), true);
  assert.equal(result.changing.length, 0);
});

test('percentual = (K − 1) × 100, é o que o Mapa K nativo aplica como "percent"', () => {
  const result = plan({ tbp: [2, 4], k: [1.0, 1.5], rowMs: [4] });
  assert.ok(Math.abs(result.rows[0].percent - 50) < 1e-9);
  assert.deepEqual(result.changing.map(r => r.row), [0]);
});

test('entradas inválidas: sem curva ou sem eixo não inventam nada', () => {
  assert.equal(plan({ tbp: [], k: [], rowMs: [2] }).rows[0].status, 'sem-curva');
  assert.equal(plan({ tbp: [2, 4], k: [1, 1], rowMs: [null] }).rows[0].status, 'sem-eixo');
});

test('saturação: 220 × 1,484 = 326 passa de 255 (Mapa K guarda 1 byte); 160 × 1,484 = 237 cabe', () => {
  const { expectedTarget, saturates } = require('../../app/src/main/assets/ui/core/curve-to-map.js');
  assert.equal(Math.round(expectedTarget(160, 48.4)), 237);
  assert.equal(Math.round(expectedTarget(220, 48.4)), 326);
  assert.equal(saturates(220, 48.4), true);
  assert.equal(saturates(160, 48.4), false);
  assert.equal(saturates(255, 0), false, 'exatamente 255 não satura');
  assert.equal(saturates(250, 2), false, '255,0 cabe');
  assert.equal(saturates(250, 3), true);
});
