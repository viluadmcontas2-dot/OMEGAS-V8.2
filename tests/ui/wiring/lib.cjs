'use strict';
// Biblioteca de verificações de USO compartilhada pelos módulos M1..M8.
const assert = require('node:assert/strict');
const { boot } = require('./harness.cjs');
const { serialize } = require('./dom.cjs');
const { World } = require('./world.cjs');
const registry = require('./registry.cjs');

// Erros de DEFEITOS CONHECIDOS (ativos) não escondem os demais achados: são filtrados aqui e reportados em DEFECTS.md.
const KNOWN_ERRORS = [['DEFECT-9', /agreed/]];
// Botões congelados já registrados como defeito (descrição do elemento): saem da falha enquanto o defeito existir
// e voltam a falhar sozinhos quando ele for corrigido (a probe deixa de reproduzir).
// Falhas de fuzz já registradas como defeito (rótulo do caso): mascaradas enquanto o defeito existir.
const KNOWN_FUZZ = [
  ['DEFECT-22', /getRefinedAnalysis\.points :=/],
  ['DEFECT-21', /getSessionRecorderStatus\.settings\.(maxSessionMb|keepSessions|telemetryEveryMs)/],
];
const KNOWN_FROZEN = [
];
function errorsSince(app, from = 0) {
  return app.errors.slice(from).filter(e => !KNOWN_ERRORS.some(([id, rx]) => rx.test(e) && registry.active(id)));
}

const BAD_TEXT = /\bNaN\b|\bundefined\b|\[object Object\]|\bInfinity\b|\bnull\b/;
const BAD_ATTR = /\bNaN\b|\bundefined\b|\[object Object\]|\bInfinity\b/;

function bodyText(app) {
  // textContent sem <script>/<style>
  const walk = node => {
    if (node.nodeType === 3) return node.data;
    if (node.nodeType !== 1 || node.localName === 'script' || node.localName === 'style') return '';
    return node.childNodes.map(walk).join('⁣');
  };
  return walk(app.doc.body);
}

/** Problemas visíveis na página: texto ruim, atributos ruins (coordenadas SVG, aria, data-*). */
function pageProblems(app) {
  const problems = [];
  const text = bodyText(app);
  const m = BAD_TEXT.exec(text.replace(/⁣/g, ' '));
  if (m) {
    const at = text.replace(/⁣/g, ' ').indexOf(m[0]);
    problems.push(`texto com "${m[0]}": …${text.replace(/⁣/g, ' ').slice(Math.max(0, at - 50), at + 40).replace(/\s+/g, ' ')}…`);
  }
  const walk = el => {
    if (el.nodeType !== 1 || el.localName === 'script' || el.localName === 'style') return;
    el.attrs.forEach((v, k) => { if (BAD_ATTR.test(v)) problems.push(`atributo ${k}="${v.slice(0, 80)}" em <${el.localName}${el.id ? '#' + el.id : ''}>`); });
    el.childNodes.forEach(walk);
  };
  walk(app.doc.body);
  return problems;
}

function assertClean(app, label, { allowErrors = 0 } = {}) {
  const { unhandled } = require('./harness.cjs');
  assert.ok(errorsSince(app).length <= allowErrors, `${label}: exceção/console.error: ${errorsSince(app).slice(0, 2).map(e => e.slice(0, 300)).join(' | ')}`);
  assert.equal(unhandled.length, 0, `${label}: rejeição não tratada: ${unhandled.map(String).join(' | ')}`);
  const problems = pageProblems(app);
  assert.deepEqual(problems.slice(0, 3), [], `${label}: ${problems.length} problema(s) na tela`);
}

// ---------------------------------------------------------------- elementos interativos
const INTERACTIVE = 'button, a[href], input, select, textarea, summary, [role="button"], [role="tab"], [data-route], circle[data-curve-index], circle[data-learning-curve-index], [data-refino-dot], [data-autocal-ref-index], [data-autocal-acquired-index]';

function describe(el) {
  const data = [...el.attrs.keys()].filter(k => k.startsWith('data-')).slice(0, 2).map(k => `${k}=${el.attrs.get(k)}`).join(',');
  const label = !el.id && !data && el.attrs.get('aria-label') ? `(${el.attrs.get('aria-label')})` : '';
  return `${el.localName}${el.id ? '#' + el.id : ''}${label}${data ? '[' + data + ']' : ''}${el.attrs.get('class') ? '.' + el.attrs.get('class').split(/\s+/)[0] : ''}`;
}

/** Visível ao toque: sem [hidden], tela ativa, camada de operação aberta, <details> aberto. */
function tappable(el, app) {
  for (let n = el; n && n.nodeType === 1; n = n.parentNode) {
    if (n.hasAttribute('hidden')) return false;
    if (n.localName === 'details' && !n.hasAttribute('open') && n !== el) {
      // dentro de <details> fechado só o <summary> direto é tocável
      const direct = el.localName === 'summary' && el.parentNode === n;
      if (!direct) return false;
    }
    const cls = n._classes ? n._classes() : new Set();
    if (n.attrs.get('data-screen') !== undefined && !cls.has('active')) return false;
    if (cls.has('alert-toast') && !cls.has('show')) return false;
    if (cls.has('operation-layer')) {
      const screen = n.closest('[data-screen]');
      if (cls.has('writing-layer') && !(screen && screen._classes().has('is-writing'))) return false;
      if (cls.has('result-layer') && !(screen && screen._classes().has('has-result'))) return false;
    }
  }
  return true;
}

function interactives(app, within) {
  const scope = within ? app.doc.querySelector(within) : app.doc.body;
  if (!scope) return [];
  const seen = new Set();
  return scope.querySelectorAll(INTERACTIVE).filter(el => {
    if (seen.has(el)) return false;
    seen.add(el);
    return tappable(el, app);
  });
}

function reasonOf(el) {
  return (el.textContent || '').trim() || el.attrs.get('title') || el.attrs.get('aria-label') || el.attrs.get('data-reason') || '';
}

/**
 * Toca o elemento e diz o que aconteceu. `effect` = 'bridge' | 'dom' | 'state' | 'disabled' | 'none'.
 * Só olha efeitos causais (síncronos + quadros de animação), nunca o polling por timer.
 */
function tap(app, el) {
  const world = app.world;
  app.flush();
  const calls0 = world.mark();
  const domBefore = serialize(app.doc.body);
  const stateBefore = app.state();
  const errors0 = app.errors.length;
  const tag = el.localName;
  const type = (el.attrs.get('type') || '').toLowerCase();
  const info = { desc: describe(el), effect: 'none', calls: [], error: null };
  if ((el.hasAttribute('disabled') || el.attrs.get('aria-disabled') === 'true') && ['button', 'input', 'select', 'textarea'].includes(tag)) {
    info.effect = 'disabled';
    info.reason = reasonOf(el);
    return info;
  }
  if (tag === 'select') {
    const opts = el.options;
    const cur = el.value;
    const other = opts.find(o => (o.attrs.has('value') ? o.attrs.get('value') : o.textContent) !== cur);
    if (other) el.value = other.attrs.has('value') ? other.attrs.get('value') : other.textContent;
    el.dispatchEvent(new app.win.Event('input', { bubbles: true }));
    el.dispatchEvent(new app.win.Event('change', { bubbles: true }));
  } else if (tag === 'input' && type === 'checkbox') {
    el.checked = !el.checked;
    el.dispatchEvent(new app.win.Event('input', { bubbles: true }));
    el.dispatchEvent(new app.win.Event('change', { bubbles: true }));
  } else if (tag === 'input' || tag === 'textarea') {
    const base = Number(el.value);
    el.value = String(Number.isFinite(base) && el.value !== '' ? base + 1 : (type === 'number' ? '1' : 'x'));
    el.dispatchEvent(new app.win.Event('input', { bubbles: true }));
    el.dispatchEvent(new app.win.Event('change', { bubbles: true }));
  } else {
    // toque real: pointerdown, pointerup, click (o Mapa K seleciona por pointer, o resto por click)
    el.dispatchEvent(new app.win.Event('pointerdown', { bubbles: true, pointerId: 1 }));
    el.dispatchEvent(new app.win.Event('pointerup', { bubbles: true, pointerId: 1 }));
    el.click();
  }
  app.flush();
  info.calls = world.since(calls0).map(c => `${c.bridge}.${c.method}`);
  if (errorsSince(app, errors0).length) info.error = errorsSince(app, errors0).join(' | ').slice(0, 300);
  if (info.calls.length) info.effect = 'bridge';
  else if (serialize(app.doc.body) !== domBefore) info.effect = 'dom';
  else if (app.state() !== stateBefore) info.effect = 'state';
  return info;
}

/**
 * Varre TODOS os elementos tocáveis (dentro de `within`, ou a tela inteira) em um estado preparado por `prepare(app)`.
 * Cada toque roda numa instância nova (isolamento). `allow(desc, info, state)` devolve um motivo para um não-efeito legítimo.
 * Devolve { total, results, failures }.
 */
function sweep({ prepare, within, allow, world }) {
  const probe = prepare();
  const els = interactives(probe, within);
  const all = els.map(describe);
  // grupos homogêneos (144 células do Mapa K, 30 pontos da curva): primeiro, do meio e último
  const groups = new Map();
  els.forEach((el, i) => {
    const key = `${el.localName}|${(el.attrs.get('class') || '').split(/\s+/)[0]}|${[...el.attrs.keys()].filter(k => k.startsWith('data-')).join(',')}|${el.id}`;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(i);
  });
  const picked = new Set();
  groups.forEach(list => { picked.add(list[0]); picked.add(list[Math.floor(list.length / 2)]); picked.add(list[list.length - 1]); });
  const indexes = [...picked].sort((a, b) => a - b);
  const descs = all;
  probe.destroy();
  const failures = [];
  const knownHits = [];
  const results = [];
  for (const i of indexes) {
    const app = prepare();
    const list = interactives(app, within);
    const el = list[i];
    if (!el || describe(el) !== descs[i]) { failures.push(`${descs[i]}: elemento instável entre instâncias`); app.destroy(); continue; }
    const info = tap(app, el);
    results.push(info);
    if (info.error) failures.push(`${info.desc}: exceção ao tocar: ${info.error}`);
    else if (info.effect === 'disabled') {
      if (!info.reason) failures.push(`${info.desc}: desabilitado SEM motivo visível`);
    } else if (info.effect === 'none') {
      const reason = allow && allow(info.desc, el, app);
      const known = KNOWN_FROZEN.find(([id, rx]) => rx.test(info.desc) && registry.active(id));
      if (reason) { /* no-op legítimo */ } else if (known) knownHits.push(`${known[0]} ${info.desc}`);
      else failures.push(`${info.desc}: CONGELADO (tocar não chamou ponte, não mudou DOM nem estado)`);
    }
    app.destroy();
  }
  return { total: all.length, exercised: indexes.length, results, failures, knownHits, descs };
}

// ---------------------------------------------------------------- fuzz
const MUTATIONS = [
  ['null', () => null],
  ['infinito (1e999)', () => '1e999'],
  ['vazio', () => ''],
  ['array vazio', () => []],
  ['ausente', () => undefined],
  ['string no lugar de número', () => 'abc'],
  ['negativo gigante', () => -1e308],
];
function leafPaths(obj, prefix = [], out = [], depth = 0) {
  if (out.length > 400) return out;
  if (Array.isArray(obj)) {
    out.push(prefix);
    if (obj.length && depth < 4) leafPaths(obj[0], prefix.concat(0), out, depth + 1);
  } else if (obj && typeof obj === 'object') {
    for (const key of Object.keys(obj)) { out.push(prefix.concat(key)); if (depth < 4) leafPaths(obj[key], prefix.concat(key), out, depth + 1); }
  }
  return out;
}
function getPath(obj, p) { return p.reduce((o, k) => (o == null ? undefined : o[k]), obj); }
function mutateAt(obj, p, value) {
  if (!p.length) return value;
  const parent = getPath(obj, p.slice(0, -1));
  if (parent == null || typeof parent !== 'object') return obj;
  if (value === undefined) { if (Array.isArray(parent)) parent.splice(p[p.length - 1], 1); else delete parent[p[p.length - 1]]; } else parent[p[p.length - 1]] = value;
  return obj;
}

/**
 * Fuzz de resposta de ponte: numa única instância preparada, para cada (método, campo, mutação) troca
 * esse campo, deixa o app consultar, confere "limpo" e depois restaura e confere que se RECUPERA.
 * `methods`: métodos cujas respostas serão mutadas (os demais ficam intactos).
 */
function fuzz({ prepare, methods, maxPathsPerMethod = 30, extra, perTick }) {
  const app = prepare();
  // respostas-base de cada método, observadas pelo app em uso normal
  app.settle(4);
  if (extra) extra(app);
  app.settle(2);
  const base = app.world.seen;
  const failures = [];
  const knownHits = [];
  let cases = 0;
  let effective = 0;
  const unhandled = require('./harness.cjs').unhandled;
  for (const method of methods) {
    if (!base[method]) continue;
    const paths = leafPaths(base[method]);
    const stride = Math.max(1, Math.ceil(paths.length / maxPathsPerMethod));
    const picked = paths.filter((_, i) => i % stride === 0);
    for (const p of picked) {
      for (const [mname, make] of MUTATIONS) {
        cases += 1;
        const label = `${method}.${p.join('.') || '(raiz)'} := ${mname}`;
        const fail = msg => { const k = KNOWN_FUZZ.find(([id, rx]) => rx.test(label) && registry.active(id)); if (k) knownHits.push(`${k[0]} ${msg}`); else failures.push(msg); };
        const errors0 = app.errors.length;
        const unhandled0 = unhandled.length;
        app.world.mutateResponse = (bridge, m, obj) => {
          if (m !== method) return obj;
          const before = JSON.stringify(obj);
          const out = mutateAt(obj, p, make());
          if (JSON.stringify(out) !== before) hit = true;
          return out;
        };
        let hit = false;
        try { for (let t = 0; t < 3; t += 1) { if (perTick) perTick(app); app.advance(250); } app.flush(); } catch (e) { fail(`${label}: lançou ${e.message}`); }
        const problems = pageProblems(app);
        if (errorsSince(app, errors0).length) fail(`${label}: exceção: ${errorsSince(app, errors0).join(' | ').slice(0, 220)}`);
        else if (unhandled.length > unhandled0) fail(`${label}: rejeição não tratada`);
        else if (problems.length) fail(`${label}: ${problems[0]}`);
        if (hit) effective += 1;
        // recupera
        app.world.mutateResponse = null;
        for (let t = 0; t < 3; t += 1) { if (perTick) perTick(app); app.advance(250); } app.flush();
        const after = pageProblems(app);
        if (after.length && !problems.length) fail(`${label}: NÃO se recupera: ${after[0]}`);
        app.errors.length = errors0; // não acumula o mesmo erro nos casos seguintes
      }
    }
  }
  app.destroy();
  return { cases, effective, failures, knownHits };
}

const POLL = new Set(['getStatus', 'getPresentSnapshot', 'getPresentSnapshotIfChanged', 'getLastOperation', 'getKMapReadResult', 'getUiProjection', 'getEquivalence', 'getEquivalenceFresh', 'getRefinedAnalysis', 'getRefinementPhase', 'getNativeActionStatus', 'getNativeMonitorStatus', 'getNativeMonitorSnapshot', 'getSessionLedgerStatus', 'listCurveBackups', 'listMapBackups', 'getSessionRecorderStatus', 'listRecordedSessions', 'getLogs', 'getOverlayStatus', 'getBatteryOptimizationStatus', 'getReleaseIdentity', 'getSnapshot', 'getEquivalenceResult', 'listAutoCalSessions', 'getLiveTelemetry', 'getFullEngineSnapshot', 'getIdentity', 'previewKFactorPoint', 'previewMapAdjustment']);
/** Métodos de ponte chamados desde `mark`, sem o polling de leitura (a sequência de AÇÕES). */
function actionCalls(app, mark) { return app.world.since(mark).filter(c => !POLL.has(c.method)).map(c => c.method); }

module.exports = { actionCalls, POLL, errorsSince, boot, World, assert, assertClean, pageProblems, bodyText, interactives, tap, sweep, fuzz, describe, tappable, serialize, BAD_TEXT };
