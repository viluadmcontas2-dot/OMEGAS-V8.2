'use strict';
// TRAVA DE LAYOUT (dono, 2026-10-07): Curva K, Ajuste GNV, AutoCal e Mapa K estão aprovados. Este teste quebra se a estrutura
// mudar (cabeçalho voltar, botão mudar de lugar ou de ordem, gráfico/grade encolher). Mudar exige pedido explícito do dono.
// Classe de prova 4 (render real, ponte falsa).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { open, go, playwright } = require('./render/lib.js');

const pw = playwright();
let ok = false;
if (pw) { try { ok = fs.existsSync(pw.chromium.executablePath()); } catch (_) { ok = false; } }
const skip = ok ? false : 'Chromium/Playwright indisponível neste ambiente';

async function visit(route, scn, fn) {
  const { browser, page } = await open(pw.chromium, 'connected', { viewport: { width: 1280, height: 720 }, scn: { noStalls: true, ...scn } });
  try { await go(page, route); await page.waitForTimeout(2200); return await page.evaluate(fn); } finally { await browser.close(); }
}
const inOrder = (all, expected, msg) => {
  let at = -1;
  for (const e of expected) { const i = all.indexOf(e, at + 1); assert.ok(i > at, `${msg}: "${e}" ausente ou fora de ordem em [${all.join(' | ')}]`); at = i; }
};

test('trava · Ajuste GNV: sem cabeçalho, gráfico grande, rodapé único em ordem', { skip }, async () => {
  const r = await visit('refino', { phase: 'COLETANDO_NOSSOS' }, () => {
    const s = document.querySelector('.screen.active');
    const bar = s.querySelector('.ar-act > .ar-buttons');
    const labels = [...bar.querySelectorAll('button, summary')].filter(e => e.getBoundingClientRect().width > 0 && !e.closest('[hidden]') && !e.closest('.instrument-detail-content')).map(e => e.textContent.trim());
    return { header: !!s.querySelector('.ar-status'), chartH: s.querySelector('#refinoChart').getBoundingClientRect().height, labels, sentenceBelow: s.querySelector('#refinoHeadline').getBoundingClientRect().top > s.querySelector('#refinoChart').getBoundingClientRect().bottom };
  });
  assert.equal(r.header, false, 'cabeçalho do Ajuste GNV voltou');
  assert.ok(r.chartH >= 360, `gráfico do Ajuste GNV encolheu: ${Math.round(r.chartH)} px`);
  assert.ok(r.sentenceBelow, 'a frase de estado deve ficar abaixo do gráfico');
  inOrder(r.labels, ['Sugestões', 'Ver detalhes', 'Apagar medições do app (GNV)', 'Ver aprendizado da ECU'], 'Ajuste GNV');
});

test('trava · AutoCal: mesma estrutura do Ajuste GNV, rodapé único em ordem', { skip }, async () => {
  const r = await visit('autocal', {}, () => {
    const s = document.querySelector('.screen.active');
    const bar = s.querySelector('.ar-act > .ar-buttons');
    const labels = [...bar.querySelectorAll('button')].filter(e => e.getBoundingClientRect().width > 0 && !e.closest('[hidden]')).map(e => e.textContent.trim());
    return { header: !!s.querySelector('.ar-status'), statusline: !!s.querySelector('.ar-act .refino-statusline'), chartH: s.querySelector('#autocalReferenceChart').getBoundingClientRect().height, labels };
  });
  assert.equal(r.header, false, 'cabeçalho do AutoCal voltou');
  assert.ok(r.statusline, 'AutoCal precisa da mesma linha de estado do Ajuste GNV');
  assert.ok(r.chartH >= 380, `gráfico do AutoCal encolheu: ${Math.round(r.chartH)} px`);
  inOrder(r.labels, ['Pausar aprendizado da ECU', 'Reler GNV', 'Reler gasolina'], 'AutoCal');
});

test('trava · Mapa K: cabeçalho de uma linha, sem Reler ECU visível, grade em largura total, barra única em ordem', { skip }, async () => {
  const r = await visit('map', {}, () => {
    const s = document.querySelector('.screen.active');
    const head = s.querySelector('.map-head').getBoundingClientRect();
    const grid = s.querySelector('#mapGrid').getBoundingClientRect();
    const bar = s.querySelector('.map-bar');
    const ids = [...bar.querySelectorAll('[id]')].filter(e => e.getBoundingClientRect().width > 0).map(e => e.id);
    return { headH: head.height, gridW: grid.width, gridH: grid.height, aside: !!s.querySelector('.map-editor-panel'), readVisible: s.querySelector('#mapReadButton').getBoundingClientRect().width > 0, ids };
  });
  assert.ok(r.headH <= 40, `cabeçalho do Mapa K voltou a crescer: ${Math.round(r.headH)} px`);
  assert.equal(r.aside, false, 'painel lateral do Mapa K voltou');
  assert.equal(r.readVisible, false, 'Reler ECU só aparece quando a leitura falha');
  assert.ok(r.gridW >= 1200, `grade do Mapa K estreitou: ${Math.round(r.gridW)} px`);
  assert.ok(r.gridH >= 380, `grade do Mapa K encolheu: ${Math.round(r.gridH)} px`);
  inOrder(r.ids, ['mapSelectAll', 'mapClearSelection', 'mapAdjustmentValue', 'mapReviewButton'], 'Mapa K');
});

test('trava · Curva K: sem cabeçalho, gráfico grande, rodapé único (editar, Desfazer, visões, Fotos, Salvar)', { skip }, async () => {
  const r = await visit('curve', {}, () => {
    const s = document.querySelector('.screen.active');
    const foot = s.querySelector('.curve-foot');
    const order = [...foot.querySelectorAll('[id], .curve-photos')].filter(e => e.getBoundingClientRect().width > 0 && !e.closest('.curve-photos-menu') && !(e.parentElement && e.parentElement.closest('.view-switch')) && e.id !== 'curveBackupStatus').map(e => e.id || 'curve-photos');
    const tops = [...foot.querySelectorAll('button, .view-switch, .curve-photos, .value-field')].filter(e => e.getBoundingClientRect().width > 0 && !e.closest('.curve-photos-menu') && !e.closest('.view-switch') || e.classList.contains('view-switch')).map(e => Math.round(e.getBoundingClientRect().top / 4));
    return { head: !!s.querySelector('.curve-head'), chartH: s.querySelector('#curveChart').getBoundingClientRect().height, order, oneRow: new Set(tops).size === 1, readVisible: s.querySelector('#curveReadButton').getBoundingClientRect().width > 0 };
  });
  assert.equal(r.head, false, 'cabeçalho da Curva K voltou');
  assert.ok(r.chartH >= 340, `gráfico da Curva K encolheu: ${Math.round(r.chartH)} px`);
  assert.equal(r.oneRow, true, 'rodapé da Curva K deve caber numa linha só');
  assert.equal(r.readVisible, false, 'Reler ECU só aparece quando a leitura falha');
  inOrder(r.order, ['curveTargetFactor', 'curveReviewButton', 'curveViewSwitch', 'curve-photos', 'curveSaveButton'], 'Curva K');
});
