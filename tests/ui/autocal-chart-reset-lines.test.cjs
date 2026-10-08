'use strict';
// Gráfico do AutoCal em render real (Chromium): antes/depois de um reset, com tabela repovoada em época nova.
// Defeito (dono, 2026-10-08): linhas erradas ligando pontos zerados/ausentes e de épocas diferentes.
// Regra: nenhum segmento de linha liga pontos separados por um ponto inválido (0/ausente) nem volta atrás em x.
// Classe de prova 4 (parcial: Chromium + CurveChart real; não é o carro). Capturas em OMEGAS_SHOTS (padrão: tmp).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { open, go, playwright } = require('./render/lib.js');

const pw = playwright();
let browserOk = false;
if (pw) { try { browserOk = !!require('./render/lib.js').chromiumPath(pw.chromium); } catch (_) { browserOk = false; } }
const skip = browserOk ? false : 'Chromium/Playwright indisponível neste ambiente';
const SHOTS = process.env.OMEGAS_SHOTS || path.join(os.tmpdir(), 'omegas-chart-reset');
fs.mkdirSync(SHOTS, { recursive: true });

// Épocas: A = antes do reset (30 pontos bons); B = logo depois (tabela zerada + 3 pontos novos, resto 0);
// C = mistura (pontos novos entre zeros e um ponto velho que volta atrás em x).
const mk = (xs, ys) => xs.map((x, i) => ({ index: i, petrolMs: x, petrolMapBar: ys[i], gasMapBar: ys[i] > 0 ? ys[i] * 0.9 : 0 }));
const A = mk(Array.from({ length: 12 }, (_, i) => 1 + i * 0.5), Array.from({ length: 12 }, (_, i) => 0.3 + i * 0.07));
const B = mk([0, 0, 0, 2.5, 3, 3.5, 0, 0, 0, 0, 0, 0], [0, 0, 0, 0.6, 0.65, 0.7, 0, 0, 0, 0, 0, 0]);
const C = mk([1, 0, 2.5, 0, 4, 1.2, 5, 0, 6, 6.5, 0, 7], [0.3, 0, 0.6, 0, 0.7, 0.35, 0.8, 0, 0.9, 0.95, 0, 1.0]);

async function render(page, name, reference, history) {
  return page.evaluate(({ name, reference, history }) => {
    const host = document.getElementById('autocalReferenceChart');
    const model = { reference, history, zones: [], ecu: [], ours: [], domain: { xMin: 0, xMax: 8, yMin: 0, yMax: 1.2 }, proposal: [], stalls: [] };
    const out = window.OmegasUi.CurveChart.buildSvg(model, { width: host.clientWidth || 1000, height: host.clientHeight || 400 });
    host.innerHTML = out.svg;
    const paths = [...host.querySelectorAll('path.autocal-reference-line')].map(p => ({ cls: p.getAttribute('class'), d: p.getAttribute('d') || '' }));
    return { name, paths };
  }, { name, reference, history });
}

// Cada "L" só pode seguir um ponto válido do mesmo traço com x >= anterior; um "M" começa traço novo.
function segments(d) {
  const out = []; let cur = null;
  for (const m of d.matchAll(/([ML]) ([\d.-]+) ([\d.-]+)/g)) {
    const pt = { x: Number(m[2]), y: Number(m[3]) };
    if (m[1] === 'M') { cur = [pt]; out.push(cur); } else cur.push(pt);
  }
  return out;
}

test('reset: nenhuma linha liga ponto zerado/ausente nem de épocas diferentes (antes, logo depois, mistura)', { skip }, async () => {
  const { browser, page, errors } = await open(pw.chromium, 'connected');
  try {
    await page.waitForTimeout(1500); await go(page, 'autocal'); await page.waitForTimeout(2500);
    const frames = [['1-antes-do-reset', A, []], ['2-logo-depois-tabela-zerada', B, A], ['3-readquirindo-mistura', C, A]];
    for (const [name, ref, hist] of frames) {
      const r = await render(page, name, ref, hist);
      await page.screenshot({ path: path.join(SHOTS, `autocal-chart-${name}.png`) });
      for (const p of r.paths) {
        for (const seg of segments(p.d)) {
          for (let i = 1; i < seg.length; i++) assert.ok(seg[i].x >= seg[i - 1].x, `${name}: ${p.cls} volta atrás em x (época misturada)`);
        }
      }
      const valid = (list, key) => list.filter(q => q[key] > 0 && q.petrolMs >= 0);
      if (name.startsWith('2')) {
        // 3 pontos novos contíguos: UM traço de 3 pontos (nada ligando aos zeros).
        const petrol = r.paths.find(p => /petrol/.test(p.cls) && !/previous/.test(p.cls));
        assert.deepEqual(segments(petrol.d).map(s => s.length), [3], `${name}: só os 3 pontos novos, ligados entre si`);
      }
      if (name.startsWith('3')) {
        const petrol = r.paths.find(p => /petrol/.test(p.cls) && !/previous/.test(p.cls));
        const total = segments(petrol.d).reduce((n, s) => n + s.length, 0);
        assert.equal(total, valid(C, 'petrolMapBar').length, `${name}: cada ponto válido desenhado uma vez, zeros fora`);
        assert.ok(segments(petrol.d).length > 1, `${name}: zeros e retrocesso quebram o traço`);
      }
    }
    assert.deepEqual(errors.filter(e => /pageerror/.test(e)), [], 'sem erro de página');
  } finally { await browser.close(); }
});
