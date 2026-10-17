'use strict';

// "Curva K → Mapa K" do OMEGAS (dono, 2026-10-13): a ECU aplica Mapa K × curva K, então a rotina REFATORA o produto
// (mapa leva o que cabe com folga, curva fica neutra/resto). Nada é fixo: o mapa vem da leitura em tempo real.
// A interpolação da curva vem do ExportToK do ProgBase (PROTOCOLO-LANDI doc 26); o resto é do OMEGAS.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const { balance, naiveError, interp, mapAt, refinementTargets, MAP_MIN, MAP_MAX } = require('../../app/src/main/assets/ui/core/curve-to-map.js');
const { writeCurveStage } = require('../../app/src/main/assets/ui/core/balance-runner.js');
const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/autocal-captura-ac-t1000.json'), 'utf8'));
const phys = name => fixture.snapshot.fields.find(f => f.key === name).physicalValues;
const TBP = phys('PETR_INJ_TBP');
const K = phys('MUL_ACT');
const ROWS_MS = [781, 977, 1172, 1367, 1758, 2344, 3125, 3906, 4687, 5469, 6250, 7031].map(v => v * 0.00256);
const COLS = 12;

function seeded(seed) { let s = seed >>> 0; return () => (s = (s * 1664525 + 1013904223) >>> 0) / 4294967296; }
const flat = value => ROWS_MS.map(() => Array(COLS).fill(value));
const product = (cells, ks, x, c) => mapAt(ROWS_MS, cells.map(r => r[c]), x) * interp(TBP, ks, x);

test('captura real, mapa baixo/médio/alto: produto preservado, curva neutra nas linhas, nada fora de 100–255', () => {
  for (const value of [100, 120, 160, 200, 220, 255]) {
    const cells = flat(value);
    const out = balance({ tbp: TBP, k: K, rowMs: ROWS_MS, cells });
    assert.ok(out.summary.maxRowErrorPct < 2.5, `mapa ${value}: erro por linha ${out.summary.maxRowErrorPct}`);
    out.rows.forEach(r => r.cells.forEach(c => assert.ok(c.target >= MAP_MIN && c.target <= MAP_MAX)));
    out.nodes.forEach(n => assert.ok(n.target >= 0.6 && n.target <= 1.8, 'respeita o piso/teto de escrita da curva'));
  }
});

test('mapa com folga: erro do produto ≤0,35% e curva fica com a menor parte; ingênuo erra ~12%', () => {
  const cells = flat(160);
  const out = balance({ tbp: TBP, k: K, rowMs: ROWS_MS, cells });
  assert.ok(out.summary.maxRowErrorPct < 0.35, String(out.summary.maxRowErrorPct));
  assert.ok(out.summary.maxNodeErrorPct < 0.35, String(out.summary.maxNodeErrorPct));
  assert.ok(naiveError({ tbp: TBP, k: K, rowMs: ROWS_MS, cells }) > 10, 'multiplicar e zerar a curva perde a forma entre as linhas');
});

test('mapa sem folga (alto): a curva assume o excedente em vez de a célula estourar ou inventar 326', () => {
  const out = balance({ tbp: TBP, k: K, rowMs: ROWS_MS, cells: flat(220) });
  assert.ok(out.summary.curveShare > 0.1, 'a curva não é neutra quando o mapa não tem folga');
  out.rows.forEach(r => r.cells.forEach(c => assert.ok(c.target <= 255)));
  assert.ok(out.summary.maxRowErrorPct < 2.5);
});

test('propriedade: mapas e curvas aleatórios (50 casos) — produto, faixa e idempotência', () => {
  const rnd = seeded(7);
  for (let n = 0; n < 50; n += 1) {
    const k = TBP.map(() => 0.7 + rnd() * 0.9);
    const cells = ROWS_MS.map(() => Array.from({ length: COLS }, () => Math.round(110 + rnd() * 130)));
    const out = balance({ tbp: TBP, k, rowMs: ROWS_MS, cells });
    out.rows.forEach(r => r.cells.forEach(c => {
      assert.ok(c.target >= MAP_MIN && c.target <= MAP_MAX);
      if (!c.saturated) assert.ok(Math.abs(c.errorPct) < 3, `caso ${n}: erro ${c.errorPct}`);
    }));
    out.nodes.forEach(node => assert.ok(node.targetRaw >= Math.round(0.6 * 16384) && node.targetRaw <= Math.round(1.8 * 16384)));
  }
});

test('idempotência em curvas e mapas suaves (como os reais): refazer o plano quase não muda nada', () => {
  const rnd = seeded(11);
  for (let n = 0; n < 30; n += 1) {
    const base = 0.9 + rnd() * 0.5;
    const k = TBP.map((_, i) => base + 0.15 * Math.sin(i / 4 + n));
    const level = Math.round(115 + rnd() * 120);
    const cells = ROWS_MS.map((_, r) => Array.from({ length: COLS }, (__, c) => Math.min(255, Math.max(100, level + r * 2 - c))));
    const out = balance({ tbp: TBP, k, rowMs: ROWS_MS, cells });
    const next = out.rows.map(r => r.cells.map(c => c.target));
    const kNext = TBP.map((_, i) => out.nodes.find(node => node.index === i)?.target ?? k[i]);
    const again = balance({ tbp: TBP, k: kNext, rowMs: ROWS_MS, cells: next });
    assert.ok(again.summary.maxRowErrorPct < 2.5, `caso ${n}: segunda passada ${again.summary.maxRowErrorPct}`);
  }
});

test('linhas fora do eixo da curva e dados faltando não são tocados', () => {
  const out = balance({ tbp: [3, 10], k: [1.2, 1.4], rowMs: [1, 5, 20], cells: [[150, 150], [150, 150], [150, 150]] });
  assert.equal(out.rows[0].status, 'abaixo');
  assert.equal(out.rows[2].status, 'acima');
  assert.equal(out.rows[1].status, 'ok');
  assert.equal(balance({ tbp: [], k: [], rowMs: [2], cells: [[150]] }).rows[0].status, 'sem-curva');
  assert.equal(balance({ tbp: TBP, k: K, rowMs: [2], cells: [[150, null]] }).rows[0].status, 'sem-dados');
});

test('curva já neutra: nada muda', () => {
  const out = balance({ tbp: TBP, k: TBP.map(() => 1), rowMs: ROWS_MS, cells: flat(180) });
  assert.equal(out.summary.cellsChanged, 0);
  assert.equal(out.summary.nodesChanged, 0);
});

// ECU falsa: curva em Q14 + operações assíncronas do jeito da ponte (READING → COMPLETED, WRITING → BATCH_CONFIRMED).
function fakeEcu(raw, { failWrite = false, drift = false } = {}) {
  const state = { raw: raw.slice(), op: { state: 'IDLE', busy: false }, writes: 0 };
  let pending = 0;
  return {
    state,
    startCurveRead() { pending = 2; state.op = { state: 'READING', busy: true, kind: 'read' }; return { ok: true, started: true }; },
    writeCurve(points) {
      state.writes += 1; pending = 2; state.op = { state: 'WRITING', busy: true, kind: 'write', points };
      return { ok: true, started: true };
    },
    curveOperation() {
      if (pending > 0 && --pending > 0) return state.op;
      if (state.op.kind === 'read') {
        if (drift) state.raw[3] += 5;
        state.op = { state: 'COMPLETED', busy: false, ok: true, points: state.raw.map((factorRaw, index) => ({ index, factorRaw })) };
      } else if (state.op.kind === 'write') {
        if (failWrite) state.op = { state: 'BATCH_PARTIAL_FAILED', busy: false, ok: false, error: 'NAK' };
        else { state.op.points.forEach(p => { state.raw[p.index] = p.targetRaw; }); state.op = { state: 'BATCH_CONFIRMED', busy: false, ok: true, readbackValid: true }; }
      }
      return state.op;
    },
  };
}
const nodesOf = out => out.nodes.filter(n => n.changed).map(n => ({ index: n.index, currentRaw: n.currentRaw, targetRaw: n.targetRaw }));
const noWait = () => Promise.resolve();

test('etapa da curva: grava, confere e, repetida, não regrava (sem dobrar)', async () => {
  const out = balance({ tbp: TBP, k: K, rowMs: ROWS_MS, cells: flat(160) });
  const ecu = fakeEcu(K.map(v => Math.round(v * 16384)));
  const first = await writeCurveStage(ecu, nodesOf(out), { sleep: noWait });
  assert.equal(first.ok, true);
  assert.equal(ecu.state.writes, 1);
  const second = await writeCurveStage(ecu, nodesOf(out), { sleep: noWait });
  assert.equal(second.ok, true);
  assert.equal(second.written, 0);
  assert.equal(ecu.state.writes, 1, 'a segunda tentativa vê a curva já no alvo e não grava');
});

test('etapa da curva: curva mudou desde o plano → não grava; falha de ECU → erro claro', async () => {
  const out = balance({ tbp: TBP, k: K, rowMs: ROWS_MS, cells: flat(160) });
  const raw = K.map(v => Math.round(v * 16384));
  const drifted = fakeEcu(raw, { drift: true });
  const a = await writeCurveStage(drifted, nodesOf(out), { sleep: noWait });
  assert.equal(a.ok, false);
  assert.equal(a.stale, true);
  assert.equal(drifted.state.writes, 0);
  const failing = fakeEcu(raw, { failWrite: true });
  const b = await writeCurveStage(failing, nodesOf(out), { sleep: noWait });
  assert.equal(b.ok, false);
  assert.match(b.error, /NAK/);
});

// ---- Suavização antes de exportar (dono, 2026-10-13): o AutoMatch nativo gera serrilhado e pontos extremos ----
const real = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/curvas-k-reais.json'), 'utf8'));
const { smoothCurve, roughness } = require('../../app/src/main/assets/ui/core/curve-to-map.js');
const REAL_X = real.tbp_raw.map(v => v / 512);
const REAL_CURVES = real.curvas_raw.map(c => c.map(v => v / 16384));
const median = a => a.slice().sort((p, q) => p - q)[Math.floor(a.length / 2)];

test('curvas reais da ECU (57): serrilhado cai para ≤3,5% e o desvio típico é pequeno', () => {
  const before = REAL_CURVES.map(roughness);
  assert.ok(median(before) > 10, 'as curvas reais são mesmo serrilhadas: ' + median(before));
  const rms = [];
  REAL_CURVES.forEach(k => {
    const s = smoothCurve(REAL_X, k);
    assert.ok(roughness(s) <= 3.5, 'serrilhado depois: ' + roughness(s));
    s.forEach(v => assert.ok(v >= 0.6 && v <= 1.8));
    rms.push(Math.sqrt(k.reduce((acc, v, i) => acc + Math.log(s[i] / v) ** 2, 0) / k.length) * 100);
  });
  assert.ok(median(rms) < 4, 'desvio RMS mediano ' + median(rms));
});

test('curva já suave ou neutra não é mexida (faixa morta), e suavizar duas vezes quase não muda', () => {
  const neutral = REAL_X.map(() => 1);
  assert.deepEqual(smoothCurve(REAL_X, neutral), neutral);
  const smooth = REAL_X.map((x, i) => 1.2 + 0.1 * Math.sin(i / 6));
  smoothCurve(REAL_X, smooth).forEach((v, i) => assert.ok(Math.abs(Math.log(v / smooth[i])) < 0.02));
  REAL_CURVES.slice(0, 10).forEach(k => {
    const once = smoothCurve(REAL_X, k);
    const twice = smoothCurve(REAL_X, once);
    twice.forEach((v, i) => assert.ok(Math.abs(Math.log(v / once[i])) < 0.05, 'segunda passada mexe pouco'));
  });
});

test('exportar com suavização: o serrilhado da curva NÃO vai para o mapa nem para a curva nova', () => {
  const rowMs = ROWS_MS;
  REAL_CURVES.filter(k => roughness(k) > 15).slice(0, 12).forEach(k => {
    const cells = rowMs.map(() => Array(COLS).fill(150));
    const smoothOut = balance({ tbp: REAL_X, k, rowMs, cells });
    const rawOut = balance({ tbp: REAL_X, k, rowMs, cells, smooth: false });
    assert.equal(smoothOut.summary.smoothed, true);
    assert.ok(smoothOut.summary.roughnessAfter <= 3.5, 'curva alvo suave: ' + smoothOut.summary.roughnessAfter);
    // curva nova que vai para a ECU: suave em todos os nós; sem suavizar ela herdaria o serrilhado
    const newK = REAL_X.map((_, i) => smoothOut.nodes.find(n => n.index === i)?.target ?? k[i]);
    assert.ok(roughness(newK) <= 6.0, 'curva gravada: ' + roughness(newK));
    // linha do mapa: variação entre linhas vizinhas fica suave (serrilhado do alvo = ~0), não copia o zigue-zague
    const col = smoothOut.rows.filter(r => r.status === 'ok').map(r => Math.log(r.cells[0].target));
    const colRaw = rawOut.rows.filter(r => r.status === 'ok').map(r => Math.log(r.cells[0].target));
    const d2 = a => Math.max(...a.slice(1, -1).map((v, i) => Math.abs(a[i] - 2 * v + a[i + 2]))) * 100;
    assert.ok(d2(col) <= d2(colRaw) + 1e-9, 'o mapa suavizado não é mais serrilhado que o cru');
    assert.ok(smoothOut.summary.maxRowErrorPct < 3, 'produto = curva suavizada, erro ' + smoothOut.summary.maxRowErrorPct);
  });
});

// ---- exportação já refinada (dono, 2026-10-13): o que o Refino aprendeu entra no Mapa K sem esperar outro AutoMatch ----
const POINTS = TBP.map((x, i) => ({ index: i, petrolMs: x, factor: K[i], factorRaw: Math.round(K[i] * 16384) }));

test('refinementTargets: só vale para o nó cujo valor na ECU ainda é exatamente o que o piloto usou; proposta velha é ignorada', () => {
  const ok = { index: 10, currentRaw: POINTS[10].factorRaw, targetRaw: POINTS[10].factorRaw + 400 };
  const stale = { index: 11, currentRaw: POINTS[11].factorRaw + 1, targetRaw: POINTS[11].factorRaw + 400 };
  const junk = [{ index: 99, currentRaw: 1, targetRaw: 2 }, { index: 3, currentRaw: POINTS[3].factorRaw, targetRaw: 0 }, null];
  const r = refinementTargets(POINTS, [ok, stale, ...junk]);
  assert.equal(r.applied, 1);
  assert.equal(r.stale, 1);
  assert.ok(Math.abs(r.refine[10] - (POINTS[10].factorRaw + 400) / 16384) < 1e-12);
  assert.equal(r.refine[11], null);
  assert.equal(r.refine.filter(v => v !== null).length, 1);
});

test('com o refino, o Mapa K sai refinado: o produto novo reproduz a curva REFINADA (suavizada), e a curva gravada parte do valor REAL da ECU', () => {
  const cells = flat(160);
  const proposal = [8, 9, 10, 11, 12].map(i => ({ index: i, currentRaw: POINTS[i].factorRaw, targetRaw: Math.round(POINTS[i].factorRaw * 1.06) }));
  const { refine, applied } = refinementTargets(POINTS, proposal);
  assert.equal(applied, 5);
  const base = balance({ tbp: TBP, k: K, rowMs: ROWS_MS, cells });
  const refined = balance({ tbp: TBP, k: K, rowMs: ROWS_MS, cells, refine });
  assert.equal(refined.summary.refinedNodes, 5);
  assert.equal(base.summary.refinedNodes, 0);
  assert.ok(refined.summary.maxRowErrorPct < 0.5, 'o produto ainda fecha: ' + refined.summary.maxRowErrorPct);
  // currentRaw dos nós = o que a ECU tem hoje (a conferência de "curva mudou" do runner depende disso), não o alvo do refino.
  refined.nodes.forEach(n => assert.equal(n.currentRaw, POINTS[n.index].factorRaw));
  // O mapa refinado é diferente do mapa sem refino, nas linhas que o refino tocou (≈ +6% × peso da suavização).
  const diff = refined.rows.flatMap((r, ri) => (r.cells || []).map((c, ci) => (base.rows[ri].cells?.[ci] ? c.target - base.rows[ri].cells[ci].target : 0)));
  assert.ok(diff.some(d => d > 0), 'o refino aumentou alguma célula');
  const ratio = refined.rows.filter(r => r.cells.length).map((r, i) => r.cells[0].target / base.rows.filter(x => x.cells.length)[i].cells[0].target);
  assert.ok(Math.max(...ratio) <= 1.07 && Math.min(...ratio) >= 0.98, 'a mudança é do tamanho do refino (≤ +6–7%): ' + ratio.join(','));
});

test('sem proposta (ou toda velha) o resultado é idêntico ao de antes', () => {
  const cells = flat(160);
  const none = refinementTargets(POINTS, []);
  assert.equal(none.applied, 0);
  assert.equal(JSON.stringify(balance({ tbp: TBP, k: K, rowMs: ROWS_MS, cells, refine: null })), JSON.stringify(balance({ tbp: TBP, k: K, rowMs: ROWS_MS, cells })));
});

test('exportação parcial (dono: não pode ser 100%): share=0,5 deixa metade do ln(K) na curva, mantém o produto e share=1 é o de sempre', () => {
  const cells = flat(160);
  const full = balance({ tbp: TBP, k: K, rowMs: ROWS_MS, cells });
  const same = balance({ tbp: TBP, k: K, rowMs: ROWS_MS, cells, share: 1 });
  assert.deepEqual(same.nodes.map(n => n.targetRaw), full.nodes.map(n => n.targetRaw), 'share=1 é idêntico ao padrão');
  const half = balance({ tbp: TBP, k: K, rowMs: ROWS_MS, cells, share: 0.5 });
  assert.ok(half.summary.maxRowErrorPct < 0.5, `produto preservado: ${half.summary.maxRowErrorPct}`);
  // a curva retém ~metade do ln(K) suavizado; as células mudam menos que na exportação total
  const mid = half.nodes.filter(n => n.ms >= 3 && n.ms <= 12);
  mid.forEach(n => {
    const kept = Math.log(n.target) / Math.log(Math.max(1.0001, n.current));
    assert.ok(Math.abs(Math.log(n.target)) > 0.2 * Math.abs(Math.log(n.current)), `nó ${n.index} ficou neutro demais: ${kept}`);
  });
  const sum = r => r.rows.reduce((a, row) => a + (row.cells || []).reduce((b, c) => b + Math.abs(c.target - c.current), 0), 0);
  assert.ok(sum(half) < sum(full) * 0.75, 'o mapa muda bem menos que na exportação total');
});

test('teto de mapa: a exportação não empurra célula para cima de softMax; o excedente fica na curva', () => {
  const cells = flat(190);
  const out = balance({ tbp: TBP, k: K, rowMs: ROWS_MS, cells, share: 1, softMax: 200 });
  out.rows.forEach(r => (r.cells || []).forEach(c => assert.ok(c.target <= 200 || c.target <= c.current, `célula ${c.target} > 200`)));
  assert.ok(out.summary.maxRowErrorPct < 3, `produto ainda preservado: ${out.summary.maxRowErrorPct}`);
});
