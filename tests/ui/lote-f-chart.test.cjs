'use strict';
// Lote F · F2 (curvas por evidência, não em tempo real) e F4 (um gráfico só para AutoCal e Refino).
// Classe de prova 2 (sintético, ponte falsa) e, quando há Chromium, 4 (render real do desenho).

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { freshContext, UI } = require('./_support.cjs');

const read = rel => fs.readFileSync(path.join(UI, rel), 'utf8');

/** Documento mínimo: o nó compartilhado precisa poder ser criado e movido entre quadros. */
function fakeDocument() {
  const makeNode = () => {
    const node = {
      innerHTML: '', className: '', parentNode: null, children: [], attrs: {},
      appendChild(child) { child.parentNode = node; node.children.push(child); return child; },
      setAttribute(name, value) { node.attrs[name] = value; },
      querySelectorAll: () => [],
    };
    Object.defineProperty(node, 'innerHTML', {
      get() { return node._html || ''; },
      set(value) { node._html = value; if (value === '') { node.children.forEach(c => { c.parentNode = null; }); node.children = []; } },
    });
    return node;
  };
  return { createElement: makeNode, makeNode };
}

function load() {
  const document = fakeDocument();
  const ctx = freshContext({ console, document });
  vm.runInContext(read('screens/autocal-cockpit.js'), ctx, { filename: 'autocal-cockpit.js' });
  return { ctx, document, chart: ctx.OmegasUi.CurveChart };
}

function snapshot(extra) {
  const thd = Array.from({ length: 18 }, (_, i) => 0.2 + i * 0.05);
  const axis = Array.from({ length: 30 }, (_, i) => 0.5 + i * 0.45);
  return {
    available: true,
    fields: [
      { key: 'PETR_INJ_TBP', status: 'VALID', rawValues: axis.map(v => Math.round(v * 512)), physicalValues: axis },
      { key: 'PETR_MNFLD_PRESS_RV', status: 'VALID', rawValues: axis.map((_, i) => 300 + i * 20), physicalValues: axis.map((_, i) => 0.3 + i * 0.02) },
      { key: 'GAS_MNFLD_PRESS_RV', status: 'VALID', rawValues: axis.map((_, i) => 320 + i * 20), physicalValues: axis.map((_, i) => 0.32 + i * 0.02) },
      { key: 'MNFLD_PRESS_THD', status: 'VALID', rawValues: thd.map(v => Math.round(v * 1024)), physicalValues: thd },
      ...(extra || []),
    ],
  };
}
function dense(count, fuel, seed) {
  // `count` leituras espalhadas pelas faixas de MAP; as primeiras têm mais amostras.
  return Array.from({ length: count }, (_, i) => ({
    mapBar: 0.15 + ((i * 7 + seed) % 100) * 0.0085,
    tpetMs: 1 + ((i * 13 + seed) % 100) * 0.1,
    samples: i < 10 ? 80 : 1 + (i % 5),
    rpmMedian: 2500, idleShare: 0, lastAtMs: 1000 + i,
  })).map(p => ({ ...p, fuel }));
}

test('Diamante: eixo completo até 22 ms mantém os pontos altos da ECU mesmo com aquisição só em marcha lenta',()=>{
 const {chart}=load();
 const reference=[{petrolMs:.5,petrolMapBar:.2,gasMapBar:.21},{petrolMs:22,petrolMapBar:1.2,gasMapBar:1.18}];
 const domain=chart.focusDomain(reference,[{petrolMs:3,mapBar:.4}],[]);
 assert.equal(domain.xMin,0);assert.ok(domain.xMax>22 && domain.xMax<24);assert.ok(domain.yMax>=1.2);
 const svg=chart.buildSvg({reference,domain,ecu:[],ours:[],zones:[]},{}).svg;
 assert.match(svg,/Injeção de gasolina/);assert.match(svg,/Injeção de gasolina/);
 assert.ok(chart.focusDomain(reference,[{petrolMs:24,mapBar:1.2}],[]).xMax>=24,'dados além de 22 ms continuam visíveis');
 const high=chart.buildSvg({reference,domain:{...domain,xMax:25},ecu:[],ours:[],zones:[]},{}).svg;
 assert.match(high,/>25,0<\/text>/);assert.doesNotMatch(high,/>24,0<\/text>/,'rótulos do extremo não ficam colados');
});

// ------------------------------------------------------------------ F2: evidência igual = zero redesenho
test('F2: evidência igual por N leituras do relógio = zero redesenhos; mudou = exatamente um', () => {
  const { chart, document } = load();
  const host = document.makeNode();
  const feed = { eq: { denseBands: { petrol: dense(40, 'PETROL', 1), gas: dense(40, 'GAS', 3) }, stalls: { count: 0 } }, analysis: { points: [] } };
  const snap = snapshot();
  let builds = 0;
  const tick = () => {
    const signature = chart.evidenceSignature({ snapshot: snap, eq: feed.eq, analysis: feed.analysis, sessionId: 1, extra: '40x20' });
    chart.mount(host, signature, () => { builds += 1; return { svg: '<svg></svg>', scale: {}, model: {} }; }, 'between');
  };
  tick();
  const base = chart.shared.renders;
  for (let i = 0; i < 50; i += 1) tick();
  assert.equal(chart.shared.renders - base, 0, 'nada mudou: nenhum redesenho em 50 ticks');
  assert.equal(builds, 1);
  // snapshotHash e idade mudam o tempo todo sem a tabela mudar: não contam como evidência
  snap.snapshotHash = 'outro';
  snap.capturedAtMs = 12345;
  for (let i = 0; i < 10; i += 1) tick();
  assert.equal(chart.shared.renders - base, 0, 'hash/idade do quadro não é evidência');
  // um ponto novo no que medimos: um redesenho só
  feed.eq = { denseBands: { petrol: dense(40, 'PETROL', 1), gas: dense(41, 'GAS', 3) }, stalls: { count: 0 } };
  for (let i = 0; i < 10; i += 1) tick();
  assert.equal(chart.shared.renders - base, 1, 'evidência mudou: exatamente um redesenho');
  // contador de aquisição da ECU mudou: um redesenho
  const before = chart.shared.renders;
  snap.fields.push({ key: 'NUM_BUF_UPD_GAS', status: 'VALID', rawValues: [1, 2, 3] });
  for (let i = 0; i < 10; i += 1) tick();
  assert.equal(chart.shared.renders - before, 1);
});

test('F2: o vigia de evidência busca no máximo a cada 5 s ou na hora quando a tabela da ECU muda', () => {
  const { chart } = load();
  let fetches = 0;
  const api = { equivalence: () => { fetches += 1; return { ok: true }; }, refinedAnalysis: () => ({ ok: true }) };
  const projection = { snapshot: snapshot() };
  chart.evidence.fetchedAt = 0; chart.evidence.tableSig = '';
  assert.equal(chart.updateEvidence(api, projection, 1000, false), true, 'primeira vez busca');
  assert.equal(chart.updateEvidence(api, projection, 2000, false), false);
  assert.equal(chart.updateEvidence(api, projection, 5900, false), false, 'antes de 5 s não busca');
  assert.equal(chart.updateEvidence(api, projection, 6100, false), true, 'vigia de 5 s');
  const changed = { snapshot: snapshot([{ key: 'NUM_BUF_UPD_PETR', status: 'VALID', rawValues: [9] }]) };
  assert.equal(chart.updateEvidence(api, changed, 6200, false), true, 'tabela mudou: busca na hora');
  assert.equal(fetches, 3);
  assert.equal(chart.WATCHDOG_MS, 5000);
});

test('F2: nenhuma tela serializa pontos nem repinta curva por relógio (sem JSON.stringify de evidência)', () => {
  const refino = read('screens/refino.js');
  const cockpit = read('screens/autocal-cockpit.js');
  const curve = read('screens/curve.js');
  assert.doesNotMatch(refino, /JSON\.stringify/, 'Refino: sem JSON.stringify');
  assert.doesNotMatch(read('components/curve-chart.js'), /JSON\.stringify/);
  const reference = cockpit.slice(cockpit.indexOf('    renderReferenceChart(snapshot) {'), cockpit.indexOf('    renderHistoryControl() {'));
  assert.doesNotMatch(reference, /JSON\.stringify/, 'renderReferenceChart não monta chave com o JSON de todos os pontos');
  assert.match(reference, /chart\.evidenceSignature\(/);
  assert.match(reference, /chart\.mount\(host, signature/);
  assert.doesNotMatch(curve.slice(curve.indexOf('    renderOverview(state) {'), curve.indexOf('    renderOverviewPointContext')), /JSON\.stringify/);
  // O cursor AGORA anda no quadro de animação (rAF), não no relógio de evidência.
  assert.match(refino, /addFrameHook\(timestamp => this\.animateLive\(timestamp\)\)/);
  assert.doesNotMatch(refino, /renderLive\(\);\s*\n\s*\}\);\s*\n\s*\/\/ O cursor/);
});

// ------------------------------------------------------------------ F4: um gráfico, dois quadros, um desenho
// Revisto (P0-3, travada ao trocar AutoCal↔Refino): na prática as assinaturas dos dois modos nunca são iguais
// (extra 'ecu18|…' × 'between|…'), então o nó único era refeito a CADA troca de aba. Agora há um nó por modo:
// cada modo desenha uma vez e trocar de aba não redesenha nada.
test('F4: AutoCal e Refino têm um nó por modo: trocar de aba não redesenha', () => {
  const { chart, document } = load();
  const autocal = document.makeNode();
  const refino = document.makeNode();
  const signature = chart.evidenceSignature({ snapshot: snapshot(), eq: { denseBands: { petrol: dense(30, 'PETROL', 1), gas: dense(30, 'GAS', 2) } }, extra: '40x20' });
  let builds = 0;
  const build = () => { builds += 1; return { svg: '<svg data-shared></svg>', scale: {}, model: {} }; };
  const start = chart.shared.renders;
  chart.mount(autocal, signature + 'ecu18', build, 'ecu18');
  chart.mount(refino, signature + 'between', build, 'between');
  const autocalNode = chart.nodeFor('ecu18');
  for (let i = 0; i < 5; i += 1) {
    chart.mount(autocal, signature + 'ecu18', build, 'ecu18');
    chart.mount(refino, signature + 'between', build, 'between');
  }
  assert.equal(builds, 2, 'um desenho por modo, nenhum a mais ao trocar de aba');
  assert.equal(chart.shared.renders - start, 2);
  chart.mount(autocal, signature + 'ecu18', build, 'ecu18');
  assert.equal(chart.shared.node, autocalNode, 'o nó do AutoCal é o mesmo de antes');
  assert.notEqual(chart.nodeFor('between'), autocalNode, 'cada modo tem o seu nó');
  assert.equal(chart.shared.node.attrs['data-mode'], 'ecu18');
});

test('F4: 500 pontos viram no máximo 18 marcadores por combustível e mantêm os mais informativos', () => {
  const { chart } = load();
  const thd = Array.from({ length: 18 }, (_, i) => 0.2 + i * 0.05);
  const items = [...dense(250, 'PETROL', 5), ...dense(250, 'GAS', 9)];
  assert.equal(items.length, 500);
  for (const kind of ['ecu18', 'between']) {
    const result = chart.aggregateEvidence(items, { thresholds: thd, yMin: 0.1, yMax: 1.1, xMin: 0, xMax: 12, kind });
    const gas = result.markers.filter(m => m.fuel === 'GAS').length;
    const petrol = result.markers.filter(m => m.fuel === 'PETROL').length;
    const slots = kind === 'between' ? 19 : 18;
    assert.ok(gas <= slots && petrol <= slots, `${kind}: no máximo ${slots} marcadores por combustível (veio ${petrol}/${gas})`);
    assert.ok(result.markers.length < 500 / 5, `${kind}: de 500 pontos sobram poucos marcadores (${result.markers.length})`);
    assert.equal(result.total, 500);
    // Os mais informativos ficam: a faixa com mais amostras sempre tem marcador, com confiança maior que a de uma faixa rala.
    const strongest = items.filter(i => i.samples === 80)[0];
    const strongSlot = chart.bandSlots(thd, 0.1, 1.1, kind).find(s => strongest.mapBar > s.lowerBar && strongest.mapBar <= s.upperBar).index;
    const strongMarker = result.markers.find(m => m.fuel === strongest.fuel && m.slot === strongSlot);
    assert.ok(strongMarker, `${kind}: faixa mais carregada tem marcador`);
    assert.ok(strongMarker.samples >= 80);
    const weakest = result.markers.slice().sort((a, b) => a.samples - b.samples)[0];
    assert.ok(strongMarker.confidence >= weakest.confidence);
  }
});

test('F4b (Refino): UM marcador por intervalo ENTRE limiares consecutivos da ECU, no centro ponderado; pontas só com evidência', () => {
  const { chart } = load();
  // limiares 1,3,5,7 (e o resto fora da faixa de dados): marcadores esperados em ~2, ~4, ~6
  const thd = [1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27, 29, 31, 33, 35];
  const items = [
    { fuel: 'GAS', mapBar: 1.9, tpetMs: 3, samples: 20 }, { fuel: 'GAS', mapBar: 2.1, tpetMs: 3.2, samples: 20 },
    { fuel: 'GAS', mapBar: 4.0, tpetMs: 5, samples: 10 }, { fuel: 'GAS', mapBar: 6.0, tpetMs: 7, samples: 10 },
  ];
  const result = chart.aggregateEvidence(items, { thresholds: thd, yMin: 0, yMax: 40, kind: 'between' });
  const centers = Array.from(result.markers.map(m => Math.round(m.mapBar)));
  assert.deepEqual(centers, [2, 4, 6], 'um por intervalo, no meio entre os limiares');
  for (const m of result.markers) assert.equal(m.kind, 'gap', 'sem evidência nas pontas: nenhum marcador de ponta');
  const below = chart.aggregateEvidence([{ fuel: 'GAS', mapBar: 0.5, tpetMs: 1, samples: 5 }], { thresholds: thd, yMin: 0, yMax: 40, kind: 'between' });
  assert.equal(below.markers.length, 1);
  assert.equal(below.markers[0].kind, 'below', 'a ponta aberta ganha marcador só quando carrega evidência');
  // Refino: nossos pontos são BOLINHAS (mesmo tamanho das da ECU), uma por combustível medido em cada intervalo.
  const betweenPoints = chart.normalizeBetween([
    { index: 0, centerMs: 3, centerMapBar: 2, state: 'coletado', gas: { ms: 3, mapBar: 2, n: 20 }, petrol: { ms: 2.9, mapBar: 2, n: 8 } },
    { index: 1, centerMs: 5, centerMapBar: 4, state: 'falta' },
  ]);
  const model = { reference: [], history: [], zones: [], ecu: [], ours: [], between: result.markers, betweenPoints, edges: thd, domain: { xMin: 0, xMax: 12, yMin: 0, yMax: 40 }, proposal: [], stalls: [] };
  const svg = chart.buildSvg(model, { width: 800, height: 400, mode: 'between' }).svg;
  assert.match(svg, /class="layer-between"/);
  assert.equal((svg.match(/class="chart-between (gas|petrol) collected"/g) || []).length, 2, 'uma bolinha por combustível medido');
  assert.equal((svg.match(/class="chart-between missing"/g) || []).length, 1, 'intervalo sem medida: anel tracejado no centro');
  assert.doesNotMatch(svg, /<rect class="chart-ours/, 'nada de triângulos/losangos: só bolinhas');
  assert.doesNotMatch(chart.buildSvg(model, { width: 800, height: 400, mode: 'ecu18' }).svg, /chart-between/, 'AutoCal não mostra pontos nossos');
  assert.match(chart.legendHtml({ mode: 'between' }), /Pontos da ECU/);
  assert.doesNotMatch(chart.legendHtml({ mode: 'ecu18' }), /Pontos da ECU|Pontos do OMEGAS/);
});

test('F4b: lê betweenPoints do Kotlin quando existe e trata a AUSÊNCIA sem quebrar; fineBins entram na faixa', () => {
  const { chart } = load();
  const thd = Array.from({ length: 18 }, (_, i) => 0.2 + i * 0.05);
  const fine = [{ fuel: 'GAS', tpetMs: 4, mapBar: 0.31, samples: 6 }, { fuel: 'GAS', tpetMs: 4.2, mapBar: 0.33, samples: 14 }];
  const folded = chart.aggregateEvidence([{ fuel: 'GAS', tpetMs: 4.1, mapBar: 0.32, samples: 20, fineBins: fine }], { thresholds: thd, yMin: 0.1, yMax: 1.1, kind: 'between' });
  assert.equal(folded.markers.length, 1);
  assert.equal(folded.markers[0].fineBins.length, 2, 'os bins finos entram na faixa e só aparecem no toque');
  assert.equal(folded.markers[0].samples, 20);
  const model = chart.buildModel({
    snapshot: snapshot(), projection: { snapshot: snapshot() },
    mode: 'between',
    eq: { betweenPoints: [{ index: 3, centerMs: 4, centerMapBar: 0.4, state: 'coletado', gas: { ms: 4, mapBar: 0.4, n: 9 }, visits: 3 }, { index: 4, state: 'falta', centerMs: 5, centerMapBar: 0.5 }] },
  });
  assert.ok(model, 'modelo montado');
  assert.equal(model.betweenPoints.length, 2, 'betweenPoints do Kotlin manda');
  assert.equal(model.betweenGiven, true);
  assert.equal(model.betweenPoints[1].state, 'missing');
  const absent = chart.buildModel({ snapshot: snapshot(), projection: { snapshot: snapshot() }, mode: 'between', eq: {} });
  assert.equal(absent.betweenGiven, false, 'contrato ausente: o desenho deriva dos marcadores, sem exceção');
  assert.equal(chart.normalizeBetween(null).length, 0, 'ausência = lista vazia');
  assert.equal(chart.normalizeBetween(Array.from({ length: 50 }, (_, i) => ({ index: i }))).length, 36, 'no máximo 36 intervalos');
});

test('F4: legenda em português com as cinco séries humanas, fora do desenho', () => {
  const { chart } = load();
  const html = chart.legendHtml({ mode: 'ecu18' });
  for (const label of ['Curva da gasolina', 'Curva do GNV hoje', 'Agora']) assert.match(html, new RegExp(label));
  assert.doesNotMatch(html, /O que medimos/, 'AutoCal só mostra o que é do AutoCal');
  assert.match(chart.legendHtml({ mode: 'between', proposal: true }), /Proposta/);
  assert.equal(chart.LEGEND.length, 4, 'no máximo 3 séries + Agora');
  assert.doesNotMatch(chart.buildSvg({ reference: [], history: [], zones: [], ecu: [], ours: [], between: [], domain: { xMin: 0, xMax: 4, yMin: 0, yMax: 1 }, proposal: [], stalls: [] }, {}).svg, /Curva da gasolina/, 'a legenda não vai dentro do SVG');
});

test('AutoCal e Refino: gasolina usa o mesmo laranja da bolinha pendente', () => {
  const cockpitCss = read('styles-autocal-cockpit.css');
  const sharedCss = read('styles-autocal-refino.css');
  const finalCss = read('styles-diamante.css');
  assert.match(cockpitCss, /\.autocal-zone-dot\.petrol\s*\{[^}]*fill:\s*var\(--warn\)/s,
    'a bolinha pendente da gasolina define a cor semântica laranja');
  for (const selector of ['autocal-reference-line', 'autocal-reference-point', 'autocal-acquired-point', 'autocal-epoch-acquisition-line', 'autocal-missing-point']) {
    assert.match(cockpitCss, new RegExp(`\\.${selector}\\.petrol\\s*\\{[^}]*?(?:fill|stroke):\\s*var\\(--warn\\)`, 's'),
      `${selector} da gasolina acompanha a bolinha laranja`);
  }
  assert.match(sharedCss, /\[data-legend="petrol"\]::before\s*\{[^}]*background:\s*var\(--warn\)/s,
    'a legenda da gasolina acompanha a bolinha laranja');
  assert.match(sharedCss, /\.chart-between\.petrol\s*\{[^}]*stroke:\s*var\(--warn\)/s,
    'os pontos próprios do Refino usam o laranja da gasolina');
  assert.match(finalCss, /\[data-legend="petrol"\]::before\s*\{[^}]*border-top:[^;}]*var\(--warn\)/s,
    'a legenda final não reverte a gasolina para branco');
  assert.match(finalCss, /\.autocal-reference-line\.petrol\s*\{[^}]*stroke:\s*var\(--warn\)/s,
    'a curva da gasolina permanece laranja no CSS final');
  assert.match(finalCss, /\.autocal-acquired-point\.petrol\s*\{[^}]*fill:\s*var\(--warn\)/s,
    'os pontos adquiridos da gasolina permanecem laranja no CSS final');
});

test('AutoCal: cada faixa mostra a zona e uma bolinha do combustível faltante, sem card', () => {
  const { chart } = load();
  const zones = [1, 2, 3, 4].map((zone, index) => ({
    zone, lower: index * .25, upper: (index + 1) * .25,
    gasState: index === 1 ? 'acquired' : 'missing',
    petrolState: index === 1 ? 'missing' : 'acquired',
  }));
  const built = chart.buildSvg({ reference: [], zones, ecu: [], ours: [], domain: { xMin: 0, xMax: 10, yMin: 0, yMax: 1 }, proposal: [], stalls: [] }, { width: 1000, height: 400, mode: 'ecu18' });
  assert.doesNotMatch(built.svg, /data-autocal-zone-card/);
  assert.equal((built.svg.match(/data-autocal-zone-surface=/g) || []).length, 4);
  assert.match(built.svg, /class="autocal-zone-dot petrol" data-zone-missing="petrol"/);
  assert.match(built.svg, /class="autocal-zone-dot gas" data-zone-missing="gas"/);
  assert.match(built.svg, /class="autocal-zone-edge"/);
  assert.equal(built.scale.xFor(10), 968, 'sem card: a curva recupera toda a largura do gráfico');
  const both = chart.buildSvg({ reference: [], zones: [{ zone: 1, lower: 0, upper: 1, gasState: 'missing', petrolState: 'missing' }], ecu: [], ours: [], domain: { xMin: 0, xMax: 10, yMin: 0, yMax: 1 }, proposal: [], stalls: [] }, { width: 1000, height: 400, mode: 'ecu18' }).svg;
  assert.match(both, /class="autocal-zone-dots" data-zone-missing="both"/);
  assert.equal((both.match(/class="autocal-zone-dot (gas|petrol)"/g) || []).length, 2, 'faltam os dois: duas bolinhas lado a lado');
});

test('F4: AutoCal e Refino não desenham gráfico próprio (um componente, sem duplicar o desenho)', () => {
  for (const file of ['screens/refino.js', 'screens/autocal-cockpit.js']) {
    const source = read(file);
    const body = file.endsWith('refino.js') ? source : source.slice(source.indexOf('    renderReferenceChart(snapshot) {'), source.indexOf('    renderHistoryControl() {'));
    assert.doesNotMatch(body, /autocal-reference-line|autocal-grid-line|<svg/, `${file}: o desenho é do componente compartilhado`);
  }
  const chart = read('components/curve-chart.js');
  assert.match(chart, /autocal-reference-line petrol/);
  assert.match(chart, /<svg class="autocal-reference-svg"/);
});

// ------------------------------------------------------------------ cursor
test('cursor único: LiveStore (cinza 1,5 s, some 3 s) e CSS transform, sem texto por quadro', () => {
  const ctx = freshContext({ console });
  const live = ctx.OmegasUi.LiveStore;
  const fresh = ageMs => live.point({ valid: true, ageMs, live: { petrol_ms: 4, load_bar: 0.5, rpm: 2000, fuel: 'GNV' } });
  assert.equal(fresh(1500).grey, false);
  assert.equal(fresh(1501).grey, true);
  assert.equal(fresh(3001), null);
  const layer = { style: {}, querySelector: () => null, __n: 0 };
  const cursor = new live.EaseCursor(() => layer);
  const scale = { xFor: v => v, yFor: v => v, xMax: 1000, yMax: 1000 };
  cursor.setTarget(100, 50, scale);
  cursor.frame(0);
  assert.match(layer.style.transform, /translate\(100\.0px, 50\.0px\)/);
  cursor.setTarget(200, 50, scale);
  let still = true;
  for (let t = 16; t < 600 && still; t += 16) still = cursor.frame(t);
  assert.match(layer.style.transform, /translate\(200\.0px, 50\.0px\)/, 'chega ao alvo');
  const source = read('core/live-store.js');
  assert.doesNotMatch(source, /textContent/, 'nenhum texto por quadro');
  assert.match(source, /style\.transform/);
  // Agora, AutoCal e Refino usam a mesma leitura
  assert.match(read('screens/dashboard.js'), /LiveStore\.read/);
  assert.match(read('screens/autocal-cockpit.js'), /ns\.LiveStore\.point/);
  assert.match(read('screens/refino.js'), /ns\.LiveStore\.point/);
});

// P1-5: o contador da ECU (NUM_BUF_UPD_*) sobe a cada passagem; só a existência do ponto é desenho.
test('P1-5: contador subindo não muda a assinatura; ponto novo muda; progresso vai por atributo', () => {
  const { chart } = load();
  const counters = values => snapshot([{ key: 'NUM_BUF_UPD_GAS', status: 'VALID', rawValues: values }]);
  const base = chart.tableSignature(counters([3, 0, 7]));
  assert.equal(chart.tableSignature(counters([9, 0, 12])), base, 'só contadores subiram: mesmo desenho');
  assert.notEqual(chart.tableSignature(counters([9, 1, 12])), base, 'ponto novo (0 → 1): redesenha');
  assert.notEqual(chart.tableSignature(counters([0, 0, 12])), base, 'ponto apagado (→ 0): redesenha');
  const attrs = {}; const classes = new Set(['collecting']);
  const circle = {
    getAttribute: name => attrs[name] ?? null, setAttribute: (name, value) => { attrs[name] = String(value); },
    removeAttribute: name => { delete attrs[name]; }, classList: { toggle: (name, on) => (on ? classes.add(name) : classes.delete(name)) },
  };
  const root = { querySelector: selector => (selector === '[data-autocal-point-key="GAS:2"]' ? circle : null) };
  assert.equal(chart.updatePoints(root, [{ fuel: 'GAS', index: 2, progress: 0.5, acquisitionState: 'COLLECTING' }]), 1);
  assert.equal(attrs['data-acquisition-progress'], '0.500');
  assert.equal(chart.updatePoints(root, [{ fuel: 'GAS', index: 2, progress: 0.5, acquisitionState: 'COLLECTING' }]), 0, 'igual: não escreve');
  chart.updatePoints(root, [{ fuel: 'GAS', index: 2, progress: 1, acquisitionState: 'ACQUIRED' }]);
  assert.equal(classes.has('acquired'), true);
  assert.equal(attrs['fill-opacity'], undefined);
  assert.match(chart.buildSvg({ domain: { xMin: 0, xMax: 10, yMin: 0, yMax: 1.2 }, reference: [], ecu: [{ fuel: 'GAS', index: 2, petrolMs: 3, mapBar: 0.5, acquisitionState: 'ACQUIRED', progress: 1 }] }, {}).svg,
    /data-autocal-point-key="GAS:2"/);
});
