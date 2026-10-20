'use strict';
// Linguagem de leigo (dono, 2026-10-18): varre os TEXTOS VISÍVEIS renderizados no Chromium (ponte de mentira + central emulada) e
// reprova jargão (rpm, ciclo, flag, bit, palavra, 0x, FLAG_CONF, ECU, módulo, SerialCode...) e rótulos genéricos ("Ajustes 1", "Números").
// A lista de proibidos vem de docs/ux/glossario.md (dicionário interno): editar a tabela muda o teste. Detalhes técnicos só valem dentro de
// "Ver detalhes técnicos" (recolhido); esse bloco é ignorado. Sem Chromium o teste PULA. OMEGAS_UI=<pasta> aponta para outra cópia da UI (prova RED).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const T = require('./helpers/browser.cjs');

const skip = T.skipReason() || false;
const UI = process.env.OMEGAS_UI ? path.resolve(process.env.OMEGAS_UI) : T.UI_DIR;
const VPS = { '1280x720': { width: 1280, height: 720 }, '1024x600': { width: 1024, height: 600 }, '800x480': { width: 800, height: 480 } };
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

/** Lê a tabela do glossário: cada linha "| `regex` | ... |" vira uma expressão (barras escapadas `\|` viram `|`). */
function proibidos() {
  const md = fs.readFileSync(path.join(T.ROOT, 'docs/ux/glossario.md'), 'utf8');
  const out = [];
  for (const line of md.split('\n')) {
    const m = line.match(/^\|\s*`((?:[^`]|\\`)+)`\s*\|/);
    if (!m) continue;
    const source = m[1].replace(/\\\|/g, '|');
    out.push({ source, re: new RegExp(source, 'i') });
  }
  return out;
}

/** Todos os textos que uma pessoa lê na tela agora (visíveis, fora de "Ver detalhes técnicos"). */
function lerTextos() {
  const visible = e => {
    const cs = getComputedStyle(e);
    if (cs.display === 'none' || cs.visibility === 'hidden') return false;
    const r = e.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  };
  const out = [];
  const walk = (el) => {
    if (!(el instanceof Element) || ['SCRIPT', 'STYLE', 'OPTION', 'svg'].includes(el.tagName) || el.hasAttribute('hidden')) return;
    if (el.matches('.tool-tech, .tool-tech *') && !el.matches('.tool-tech > summary')) return;
    if (!visible(el)) return;
    for (const attr of ['aria-label', 'title', 'placeholder']) { const v = el.getAttribute(attr); if (v && v.trim()) out.push(v.trim()); }
    for (const node of el.childNodes) {
      if (node.nodeType === 3) { const t = node.textContent.replace(/\s+/g, ' ').trim(); if (t) out.push(t); }
      else walk(node);
    }
    if (el.tagName === 'SELECT') for (const o of el.options) if (o.text.trim()) out.push(o.text.trim());
  };
  walk(document.body);
  return out;
}

async function textos(page) { return page.evaluate(lerTextos); }
const sub = (lista, estado) => lista.map(t => ({ estado, t }));

async function percorrerFerramentasESessoes(app) {
  const { page } = app;
  const todos = [];
  const colher = async nome => todos.push(...sub(await textos(page), nome));
  // sem USB: motivos de botão desabilitado
  await T.route(app, 'tools'); await T.pump(page, 4);
  await colher('ferramentas página 1, sem central');
  await page.evaluate(() => { window.__bench.plugUsb(); window.__bench.deliver(); });
  await T.pump(page, 40);
  await colher('ferramentas página 1');
  for (const id of ['valores', 'corte', 'lenta', 'troca', 'injetores', 'volta']) {
    await T.tap(page, `[data-adv-open="${id}"]`); await T.pump(page, 3);
    await colher('cartão ' + id);
    await T.tap(page, '[data-adv-back]'); await T.pump(page, 2);
  }
  // interruptor: um toque liga (sem janela); erro; Desfazer
  await T.tap(page, '[data-adv-switch]'); await T.pump(page, 10);
  await colher('anti-apagão: pronto');
  await page.evaluate(() => window.__bench.setFault('nak'));
  await T.tap(page, '[data-adv-switch]'); await T.pump(page, 10);
  await colher('anti-apagão: central não aceitou');
  await page.evaluate(() => window.__bench.setFault('nak', false));
  // valores: erro de digitação, botões − / +, dicas, gravado
  await T.tap(page, '[data-adv-open="valores"]'); await T.pump(page, 3);
  await colher('valores: como funciona e dicas');
  await T.tap(page, '[data-adv-open-view="info"]'); await T.pump(page, 2);
  for (const aba of ['como', 'f0', 'f1', 'f2', 'f3', 'f4', 'carro', 'nao']) { await T.tap(page, `[data-adv-info-tab="${aba}"]`); await T.pump(page, 1); await colher('valores: caminho completo e extras'); }
  await T.tap(page, '[data-adv-back-valores]'); await T.pump(page, 2);
  await page.locator('#advField0').fill('7000');
  await colher('valores: fora da faixa');
  await page.locator('#advField0').fill('1700');
  await page.evaluate(() => document.activeElement && document.activeElement.blur()); await T.pump(page, 10);
  await colher('valores: pronto');
  await T.tap(page, '[data-adv-back]'); await T.pump(page, 2);
  // página 2 e registro
  await T.tap(page, '#toolsPageNext'); await T.pump(page, 3);
  await colher('ferramentas página 2');
  await T.tap(page, '[data-tool-open-log]'); await T.pump(page, 3);
  await colher('registro do app');
  await T.tap(page, '[data-tool-back]'); await T.pump(page, 2);
  // sessões
  await T.route(app, 'sessions'); await T.pump(page, 4, { data: true });
  await colher('sessões');
  await T.tap(page, '.diagnostic-settings > summary'); await T.pump(page, 2);
  await colher('sessões: tamanho das gravações');
  return todos;
}

function violacoes(lista, regras) {
  const achados = [];
  for (const { estado, t } of lista) for (const r of regras) if (r.re.test(t)) achados.push(`[${estado}] "${t}" ~ /${r.source}/`);
  return [...new Set(achados)];
}

test('o glossário interno traz os termos que o dono proibiu', () => {
  const fontes = proibidos().map(r => r.source).join('\n');
  for (const termo of [/rpm/, /ciclo/, /flag/, /bit/, /palavra/, /0x/, /FLAG_CONF/, /ECU/, /serialcode/i, /Ajustes\?/, /Números/]) assert.match(fontes, termo, String(termo));
  assert.ok(proibidos().length >= 12);
});

test('o detector pega jargão (prova de sensibilidade, sem navegador)', () => {
  const regras = proibidos();
  for (const t of ['rpm por ciclo', 'palavra 0', 'bit ligado', '0x0020', 'FLAG_CONF3', 'Ajustes 1', 'Números', 'ECU online', 'módulo', 'SerialCode 307', 'histerese', 'status 0xCA', 'Avançado']) {
    assert.ok(violacoes([{ estado: 'x', t }], regras).length, 'deveria reprovar: ' + t);
  }
  for (const t of ['Rotação máxima para agir', 'Valor de fábrica: 1.600', 'Central conectada', 'Ligar anti-apagão', 'Agora não', 'Ver detalhes técnicos', 'ms', 'De 0 a 6.000']) {
    assert.deepEqual(violacoes([{ estado: 'x', t }], regras), [], 'não deveria reprovar: ' + t);
  }
});

for (const [nome, vp] of Object.entries(VPS)) {
  test(`Ferramentas, anti-apagão e Sessões @${nome}: nenhum jargão nem rótulo genérico em texto visível`, { skip }, async () => {
    const app = await T.openApp(browser, { ui: UI, viewport: vp });
    try {
      const lista = await percorrerFerramentasESessoes(app);
      const fixos = await textos(app.page);
      lista.push(...sub(fixos, 'sessões (navegação e barra do topo)'));
      assert.ok(lista.length > 150, 'a varredura leu poucos textos: ' + lista.length);
      const estados = new Set(lista.map(x => x.estado));
      for (const esperado of ['ferramentas página 1', 'valores: fora da faixa', 'valores: como funciona e dicas', 'valores: caminho completo e extras', 'anti-apagão: central não aceitou', 'registro do app', 'sessões', 'sessões: tamanho das gravações']) assert.ok(estados.has(esperado), 'estado não visitado: ' + esperado);
      assert.deepEqual(violacoes(lista, proibidos()), [], 'jargão em texto visível');
      // a navegação e a barra do topo falam a mesma palavra em todo o app
      const nav = lista.filter(x => x.estado === 'sessões (navegação e barra do topo)').map(x => x.t);
      assert.ok(nav.includes('Sessões'), 'a aba Sessões existe na navegação');
      assert.ok(nav.some(t => /central/i.test(t)), 'a navegação usa "central"');
      assert.ok(!nav.some(t => /\bECU\b/i.test(t)));
    } finally { await app.context.close(); }
  });
}

test('toda ação do app tem texto próprio: nenhum botão vazio ou genérico em Ferramentas e Sessões', { skip }, async () => {
  const app = await T.openApp(browser, { ui: UI, usb: true });
  const { page } = app;
  try {
    const genericos = /^(ok|sim|não|enviar|submit|aplicar mudanças?|ajustes?|opções|mais|detalhes|avançado|números?)$/i;
    const olhar = () => page.evaluate(() => [...document.querySelectorAll('.screen.active button, .screen.active [role="switch"], #toolsLayer button')]
      .filter(b => { const r = b.getBoundingClientRect(); const cs = getComputedStyle(b); return r.width > 0 && r.height > 0 && cs.display !== 'none' && !b.closest('[hidden]'); })
      .map(b => (b.getAttribute('aria-label') || b.textContent || '').replace(/\s+/g, ' ').trim()));
    const todos = [];
    for (const [rota, aoEntrar] of [['tools', null], ['sessions', null]]) { await T.route(app, rota); await T.pump(page, 4, { data: true }); todos.push(...await olhar()); if (aoEntrar) await aoEntrar(); }
    await T.tap(page, '#toolsPageNext').catch(() => {});
    assert.ok(todos.length >= 8, 'poucos botões lidos: ' + todos.length);
    assert.deepEqual(todos.filter(t => !t || genericos.test(t)), [], 'botão sem nome claro');
  } finally { await app.context.close(); }
});

// As telas de AutoCal, Refino, Ajuste local e Ajuste global ainda dizem "ECU" e "RPM": o dono vai passar por elas depois.
// Fica como pendência VISÍVEL (todo), não como verde falso: falha no relatório sem derrubar a suíte; a contagem sai como diagnóstico.
test('AutoCal, Refino e Ajustes: sem a sigla ECU em texto visível', { skip, todo: 'telas do AutoCal/Refino/Ajustes ainda usam "ECU" (fora do escopo desta rodada)' }, async t => {
  const app = await T.openApp(browser, { ui: UI, usb: true });
  const { page } = app;
  try {
    const regras = proibidos().filter(r => /ECU|módulo/.test(r.source));
    const lista = [];
    for (const rota of ['autocal', 'refino', 'map', 'curve']) { await T.route(app, rota); await T.pump(page, 8, { data: true }); lista.push(...sub(await textos(page), rota)); }
    const achados = violacoes(lista, regras);
    t.diagnostic(`textos com "ECU": ${achados.length}`);
    assert.deepEqual(achados, []);
  } finally { await app.context.close(); }
});
