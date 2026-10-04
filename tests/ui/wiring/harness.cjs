'use strict';
// Harness de uso: carrega o index.html e os scripts REAIS num vm com mini-DOM, pontes falsas roteirizáveis
// (world.cjs), relógio virtual (Date/timers/requestAnimationFrame) e captura de console.error / rejeições.
// `UI_ROOT` permite apontar para uma CÓPIA mutada (mutantes).
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { Document, DomEvent } = require('./dom.cjs');
const { World } = require('./world.cjs');

const REPO = path.resolve(__dirname, '../../..');
function uiRoot() { return process.env.UI_ROOT || path.join(REPO, 'app/src/main/assets/ui'); }

const scriptCache = new Map();
function compiled(file) {
  const full = path.join(uiRoot(), file);
  const key = full;
  if (!scriptCache.has(key)) scriptCache.set(key, new vm.Script(fs.readFileSync(full, 'utf8'), { filename: file }));
  return scriptCache.get(key);
}

const unhandled = [];
process.on('unhandledRejection', reason => { unhandled.push(reason); });

function boot(options = {}) {
  const world = options.world || new World();
  const clock = world.clock;
  const startedClock = clock.now;
  const doc = new Document();
  const html = fs.readFileSync(path.join(uiRoot(), 'index.html'), 'utf8');
  doc.parseHtml(html);

  const errors = [];
  const logs = [];
  const timers = new Map();
  let timerSeq = 0;
  const frames = [];
  let frameSeq = 0;
  const storage = new Map(Object.entries(options.storage || {}));
  const winListeners = new Map();

  const RealDate = Date;
  class FakeDate extends RealDate {
    constructor(...args) { if (args.length === 0) super(clock.now); else super(...args); }
    static now() { return clock.now; }
  }

  const win = {
    console: {
      log: (...a) => logs.push(a.join(' ')), info() {}, debug() {},
      warn: (...a) => logs.push('warn: ' + a.join(' ')),
      error: (...a) => errors.push(a.map(x => (x && x.stack) || String(x)).join(' ')),
    },
    document: doc, Date: FakeDate, Math, JSON, Number, String, Array, Object, Set, Map, WeakMap, WeakSet, Promise, Symbol, Intl, Error, TypeError, RangeError,
    parseFloat, parseInt, isFinite, isNaN, encodeURIComponent, decodeURIComponent, Proxy, Reflect, RegExp, Uint8Array, Float64Array, ArrayBuffer, queueMicrotask,
    performance: { now: () => clock.now - startedClock },
    navigator: { userAgent: 'wiring-harness' },
    location: { href: 'file:///android_asset/ui/index.html', search: '', hash: '' },
    innerWidth: 1280, innerHeight: 720, devicePixelRatio: 1,
    localStorage: {
      getItem: k => (storage.has(k) ? storage.get(k) : null),
      setItem: (k, v) => { storage.set(k, String(v)); },
      removeItem: k => { storage.delete(k); },
    },
    matchMedia: () => ({ matches: false, addEventListener() {}, removeEventListener() {} }),
    getComputedStyle: () => ({ getPropertyValue: () => '' }),
    setTimeout(fn, ms) { const id = ++timerSeq; timers.set(id, { fn, at: clock.now + (Number(ms) || 0), every: 0 }); return id; },
    clearTimeout(id) { timers.delete(id); },
    setInterval(fn, ms) { const id = ++timerSeq; const every = Math.max(1, Number(ms) || 1); timers.set(id, { fn, at: clock.now + every, every }); return id; },
    clearInterval(id) { timers.delete(id); },
    // cancelAnimationFrame cancela de verdade (como o navegador): sem isso um quadro "cancelado" ainda rodava e se rearmava sozinho.
    requestAnimationFrame(fn) { const id = ++frameSeq; frames.push({ id, fn }); return id; },
    cancelAnimationFrame(id) { const i = frames.findIndex(f => f.id === id); if (i >= 0) frames.splice(i, 1); },
    Event: DomEvent, CustomEvent: DomEvent,
    addEventListener(type, fn, opts) {
      const list = winListeners.get(type) || []; list.push({ fn, once: !!(opts && opts.once) }); winListeners.set(type, list);
    },
    removeEventListener(type, fn) { const l = winListeners.get(type) || []; const i = l.findIndex(x => x.fn === fn); if (i >= 0) l.splice(i, 1); },
    dispatchEvent(event) {
      event.target = win;
      for (const l of (winListeners.get(event.type) || []).slice()) {
        try { l.fn.call(win, event); } catch (e) { win.console.error('[window listener]', e); }
        if (l.once) win.removeEventListener(event.type, l.fn);
      }
      return true;
    },
  };
  win._listeners = winListeners;
  win.window = win; win.globalThis = win; win.self = win; win.top = win; win.parent = win;
  // bubbling documento -> janela usa a mesma tabela de listeners da janela
  doc.window = { console: win.console, _listeners: winListeners, removeEventListener: win.removeEventListener };
  const bridges = world.makeBridges();
  Object.assign(win, bridges);

  const context = vm.createContext(win);
  const loaded = [];
  const pendingScripts = [];
  function runScript(src) {
    try { compiled(src).runInContext(context); loaded.push(src); }
    catch (error) { errors.push(`[script ${src}] ${(error && error.stack) || error}`); }
  }
  doc.onElementConnected = el => {
    if (el.localName === 'script' && el.attrs.has('src') && !el._ran) { el._ran = true; pendingScripts.push(el); }
  };
  function drainScripts() {
    while (pendingScripts.length) {
      const el = pendingScripts.shift();
      runScript(el.attrs.get('src'));
      if (typeof el.onload === 'function') el.onload();
    }
  }

  // scripts estáticos do index.html, na ordem (defer)
  const staticScripts = [...html.matchAll(/<script\s+src="([^"]+)"/g)].map(m => m[1]);

  function runFrames(limit) {
    let n = 0;
    while (frames.length && n < (limit || 50)) { const batch = frames.splice(0); batch.forEach(({ fn }) => { try { fn(clock.now); } catch (e) { errors.push('[raf] ' + ((e && e.stack) || e)); } }); n += 1; }
  }
  function fireDueTimers(upTo) {
    for (;;) {
      let nextId = null; let next = null;
      timers.forEach((t, id) => { if (t.at <= upTo && (next === null || t.at < next.at)) { next = t; nextId = id; } });
      if (next === null) return;
      clock.now = Math.max(clock.now, next.at);
      if (next.every) next.at += next.every; else timers.delete(nextId);
      try { next.fn(); } catch (e) { errors.push('[timer] ' + ((e && e.stack) || e)); }
      runFrames();
    }
  }
  /** Avança o relógio virtual: dispara timers e quadros de animação em ordem. */
  function advance(ms) {
    const end = clock.now + ms;
    fireDueTimers(end);
    clock.now = end;
    runFrames();
    drainScripts();
  }
  /** Só esvazia filas imediatas (quadros e scripts), sem passar o tempo. */
  function flush() { runFrames(); drainScripts(); runFrames(); }

  const app = { world, doc, win, context, errors, logs, timers, advance, flush, storage, bridges, loaded, unhandled };
  for (const src of staticScripts) runScript(src);
  drainScripts();
  flush();
  Object.defineProperty(app, 'App', { get: () => win.OmegasApp });
  app.$ = sel => doc.querySelector(sel);
  app.$$ = sel => doc.querySelectorAll(sel);
  app.byId = id => doc.getElementById(id);
  app.store = () => win.OmegasApp && win.OmegasApp.store;
  app.state = () => (win.OmegasApp ? win.OmegasApp.store.get() : null);
  app.route = () => (app.state() || {}).route;
  /** Navega pelo botão do trilho (toque real), deixa tudo assentar e devolve a rota. */
  app.go = route => {
    const button = doc.querySelector(`.side-nav [data-route="${route}"]`);
    if (!button) throw new Error(`sem botão de rota ${route}`);
    button.click();
    app.settle();
    return app.route();
  };
  /** Assenta: quadros + alguns ticks do scheduler (200 ms) para o app consultar a ponte. */
  app.settle = (ticks = 6, step = 250) => { flush(); for (let i = 0; i < ticks; i += 1) advance(step); flush(); };
  app.text = () => doc.body.textContent;
  app.html = () => doc.body.innerHTML;
  app.chartWrites = id => (doc.getElementById(id) ? doc.getElementById(id)._innerHTMLWrites : 0);
  app.listenerTotal = () => doc.stats.added - doc.stats.removed;
  app.destroy = () => { timers.clear(); frames.length = 0; };
  return app;
}

module.exports = { boot, REPO, uiRoot, unhandled };
