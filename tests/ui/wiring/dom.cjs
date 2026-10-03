'use strict';
// Mini DOM determinístico (sem dependências) para rodar o index.html e os scripts REAIS em vm.
// Suporta o que a UI do OMEGAS usa: innerHTML/outerHTML, seletores simples, eventos com bubbling,
// classList, dataset, atributos refletidos, details/summary, contadores de escrita por nó e de listeners.

const VOID = new Set(['area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'source', 'track', 'wbr']);
const ENTITIES = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ', times: '×', minus: '−', middot: '·', hellip: '…', rarr: '→', larr: '←', uarr: '↑', darr: '↓', deg: '°', ordm: 'º', ge: '≥', le: '≤', plusmn: '±', check: '✓' };

function decode(text) {
  return text.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (m, name) => {
    if (name[0] === '#') {
      const code = name[1].toLowerCase() === 'x' ? parseInt(name.slice(2), 16) : parseInt(name.slice(1), 10);
      return Number.isFinite(code) ? String.fromCodePoint(code) : m;
    }
    return Object.prototype.hasOwnProperty.call(ENTITIES, name) ? ENTITIES[name] : m;
  });
}
function escText(text) { return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
function escAttr(text) { return text.replace(/&/g, '&amp;').replace(/"/g, '&quot;'); }
const camel = name => name.replace(/-([a-z])/g, (_, c) => c.toUpperCase());
const kebab = name => name.replace(/[A-Z]/g, c => '-' + c.toLowerCase());

class DomEvent {
  constructor(type, init) {
    this.type = type;
    this.bubbles = !init || init.bubbles !== false;
    this.cancelable = true;
    this.defaultPrevented = false;
    this.target = null;
    this.currentTarget = null;
    this.detail = init && init.detail;
    this._stop = false;
    Object.assign(this, init || {});
  }
  preventDefault() { this.defaultPrevented = true; }
  stopPropagation() { this._stop = true; }
  stopImmediatePropagation() { this._stop = true; }
}

class Node {
  constructor(doc) { this.ownerDocument = doc; this.parentNode = null; this.childNodes = []; }
  get parentElement() { return this.parentNode && this.parentNode.nodeType === 1 ? this.parentNode : null; }
  get firstChild() { return this.childNodes[0] || null; }
  get lastChild() { return this.childNodes[this.childNodes.length - 1] || null; }
  get children() { return this.childNodes.filter(n => n.nodeType === 1); }
  get firstElementChild() { return this.children[0] || null; }
  get nextElementSibling() {
    if (!this.parentNode) return null;
    const siblings = this.parentNode.children; return siblings[siblings.indexOf(this) + 1] || null;
  }
  get previousElementSibling() {
    if (!this.parentNode) return null;
    const siblings = this.parentNode.children; return siblings[siblings.indexOf(this) - 1] || null;
  }
  contains(other) { for (let n = other; n; n = n.parentNode) if (n === this) return true; return false; }
  _touch() { this.ownerDocument && this.ownerDocument._mutated(); }
  appendChild(child) { return this.insertBefore(child, null); }
  append(...items) { items.forEach(i => this.appendChild(typeof i === 'string' ? this.ownerDocument.createTextNode(i) : i)); }
  prepend(...items) { const first = this.firstChild; items.forEach(i => this.insertBefore(typeof i === 'string' ? this.ownerDocument.createTextNode(i) : i, first)); }
  insertBefore(child, ref) {
    if (child.nodeType === 11) { [...child.childNodes].forEach(c => this.insertBefore(c, ref)); return child; }
    if (child.parentNode) child.parentNode._detach(child);
    child.parentNode = this;
    const at = ref ? this.childNodes.indexOf(ref) : -1;
    if (at < 0) this.childNodes.push(child); else this.childNodes.splice(at, 0, child);
    this._touch();
    if (child.nodeType === 1) this.ownerDocument._connected(child);
    return child;
  }
  _detach(child) { const at = this.childNodes.indexOf(child); if (at >= 0) this.childNodes.splice(at, 1); child.parentNode = null; this._touch(); }
  removeChild(child) { this._detach(child); return child; }
  remove() { if (this.parentNode) this.parentNode._detach(this); }
  replaceChildren(...items) { this.childNodes.slice().forEach(c => this._detach(c)); this.append(...items); }
  get isConnected() { for (let n = this; n; n = n.parentNode) if (n === this.ownerDocument) return true; return false; }
}

class Text extends Node {
  constructor(doc, data) { super(doc); this.nodeType = 3; this.data = String(data); }
  get textContent() { return this.data; }
  set textContent(v) { this.data = String(v); this._touch(); }
  get nodeValue() { return this.data; }
  cloneNode() { return new Text(this.ownerDocument, this.data); }
}
class Comment extends Node {
  constructor(doc, data) { super(doc); this.nodeType = 8; this.data = data; }
  get textContent() { return ''; }
}
class Fragment extends Node {
  constructor(doc) { super(doc); this.nodeType = 11; }
  querySelector(sel) { return queryAll(this, sel, true)[0] || null; }
  querySelectorAll(sel) { return queryAll(this, sel, false); }
}

const REFLECT = { id: 'id', className: 'class', title: 'title', href: 'href', src: 'src', rel: 'rel', type: 'type', placeholder: 'placeholder', name: 'name', htmlFor: 'for' };
const BOOL = { hidden: 'hidden', disabled: 'disabled', open: 'open', required: 'required', readOnly: 'readonly', selected: 'selected' };

class Element extends Node {
  constructor(doc, tag, ns) {
    super(doc);
    this.nodeType = 1;
    this.localName = tag;
    this.tagName = ns ? tag : tag.toUpperCase();
    this.namespaceURI = ns || 'http://www.w3.org/1999/xhtml';
    this.attrs = new Map();
    this._listeners = new Map();
    this._innerHTMLWrites = 0;
    this._value = undefined;
    this._checked = false;
    this._styleProps = {};
    const self = this;
    this.dataset = new Proxy({}, {
      get(_, key) { if (typeof key !== 'string') return undefined; const v = self.attrs.get('data-' + kebab(key)); return v === undefined ? undefined : v; },
      set(_, key, value) { self.setAttribute('data-' + kebab(key), String(value)); return true; },
      has(_, key) { return self.attrs.has('data-' + kebab(String(key))); },
      deleteProperty(_, key) { self.removeAttribute('data-' + kebab(String(key))); return true; },
      ownKeys() { return [...self.attrs.keys()].filter(k => k.startsWith('data-')).map(k => camel(k.slice(5))); },
      getOwnPropertyDescriptor(_, key) { const v = self.attrs.get('data-' + kebab(String(key))); return v === undefined ? undefined : { value: v, enumerable: true, configurable: true, writable: true }; },
    });
    this.classList = {
      add: (...c) => { const s = this._classes(); c.forEach(x => s.add(x)); this._setClasses(s); },
      remove: (...c) => { const s = this._classes(); c.forEach(x => s.delete(x)); this._setClasses(s); },
      toggle: (c, force) => { const s = this._classes(); const on = force === undefined ? !s.has(c) : !!force; if (on) s.add(c); else s.delete(c); this._setClasses(s); return on; },
      contains: c => this._classes().has(c),
      replace: (a, b) => { const s = this._classes(); if (s.has(a)) { s.delete(a); s.add(b); this._setClasses(s); } },
    };
    this.style = new Proxy(this._styleProps, {
      get: (t, k) => (k === 'setProperty' ? (n, v) => { t[n] = String(v); this._touch(); } : k === 'removeProperty' ? n => { delete t[n]; this._touch(); } : k === 'getPropertyValue' ? n => t[n] || '' : t[k] || ''),
      set: (t, k, v) => { t[k] = String(v); this._touch(); return true; },
    });
  }
  _classes() { return new Set((this.attrs.get('class') || '').split(/\s+/).filter(Boolean)); }
  _setClasses(set) { const v = [...set].join(' '); if (v) this.attrs.set('class', v); else this.attrs.delete('class'); this._touch(); }
  getAttribute(n) { const v = this.attrs.get(n); return v === undefined ? null : v; }
  setAttribute(n, v) { this.attrs.set(n, String(v)); this._touch(); }
  removeAttribute(n) { this.attrs.delete(n); this._touch(); }
  hasAttribute(n) { return this.attrs.has(n); }
  toggleAttribute(n, force) { const on = force === undefined ? !this.attrs.has(n) : !!force; if (on) this.attrs.set(n, ''); else this.attrs.delete(n); this._touch(); return on; }
  get attributes() { return [...this.attrs].map(([name, value]) => ({ name, value })); }
  get textContent() { return this.childNodes.map(c => c.textContent).join(''); }
  set textContent(v) { this.childNodes.slice().forEach(c => this._detach(c)); const s = String(v); if (s) this.appendChild(this.ownerDocument.createTextNode(s)); }
  get innerText() { return this.textContent; }
  set innerText(v) { this.textContent = v; }
  get innerHTML() { return this.childNodes.map(serialize).join(''); }
  set innerHTML(html) {
    this._innerHTMLWrites += 1;
    this.childNodes.slice().forEach(c => { c.parentNode = null; });
    this.childNodes = [];
    this._value = undefined;
    parseInto(this.ownerDocument, this, String(html));
    this._touch();
  }
  get outerHTML() { return serialize(this); }
  get value() {
    if (this.localName === 'select') {
      const opts = queryAll(this, 'option', false);
      if (this._value !== undefined) return opts.some(o => optionValue(o) === this._value) ? this._value : '';
      const chosen = opts.find(o => o.hasAttribute('selected')) || opts[0];
      return chosen ? optionValue(chosen) : '';
    }
    if (this.localName === 'option') return optionValue(this);
    if (this._value !== undefined) return this._value;
    return this.attrs.get('value') || '';
  }
  set value(v) { this._value = String(v); this._touch(); }
  get checked() { return this._checked; }
  set checked(v) { this._checked = !!v; this._touch(); }
  get options() { return queryAll(this, 'option', false); }
  get selectedIndex() { const v = this.value; return this.options.findIndex(o => optionValue(o) === v); }
  get clientWidth() { return 640; }
  get clientHeight() { return 360; }
  get offsetWidth() { return 640; }
  get offsetHeight() { return 360; }
  get scrollTop() { return 0; }
  set scrollTop(_) {}
  getBoundingClientRect() { return { x: 0, y: 0, left: 0, top: 0, right: 640, bottom: 360, width: 640, height: 360 }; }
  getBBox() { return { x: 0, y: 0, width: 640, height: 360 }; }
  focus() { this.ownerDocument.activeElement = this; }
  blur() { if (this.ownerDocument.activeElement === this) this.ownerDocument.activeElement = this.ownerDocument.body; }
  scrollIntoView() {}
  setPointerCapture() {}
  releasePointerCapture() {}
  cloneNode(deep) {
    const c = new Element(this.ownerDocument, this.localName, this.namespaceURI === 'http://www.w3.org/1999/xhtml' ? null : this.namespaceURI);
    this.attrs.forEach((v, k) => c.attrs.set(k, v));
    if (deep) this.childNodes.forEach(n => c.appendChild(n.cloneNode(true)));
    return c;
  }
  addEventListener(type, fn, opts) {
    if (typeof fn !== 'function') return;
    const list = this._listeners.get(type) || [];
    const capture = opts === true || (opts && opts.capture === true);
    if (list.some(l => l.fn === fn && l.capture === capture)) return;
    list.push({ fn, once: !!(opts && opts.once), capture });
    this._listeners.set(type, list);
    this.ownerDocument.stats.added += 1;
  }
  removeEventListener(type, fn) {
    const list = this._listeners.get(type) || [];
    const at = list.findIndex(l => l.fn === fn);
    if (at >= 0) { list.splice(at, 1); this.ownerDocument.stats.removed += 1; }
  }
  listenerCount() { let n = 0; this._listeners.forEach(l => { n += l.length; }); return n; }
  dispatchEvent(event) { return this.ownerDocument._dispatch(this, event); }
  click() {
    if (this.hasAttribute('disabled') && ['button', 'input', 'select', 'textarea'].includes(this.localName)) return;
    this.dispatchEvent(new DomEvent('click', { bubbles: true }));
    if (this.localName === 'summary' && this.parentNode && this.parentNode.localName === 'details') {
      this.parentNode.toggleAttribute('open');
      this.parentNode.dispatchEvent(new DomEvent('toggle', { bubbles: false }));
    }
  }
  matches(sel) { return matchesSelector(this, sel); }
  closest(sel) { for (let n = this; n && n.nodeType === 1; n = n.parentNode) if (matchesSelector(n, sel)) return n; return null; }
  querySelector(sel) { return queryAll(this, sel, true)[0] || null; }
  querySelectorAll(sel) { return queryAll(this, sel, false); }
  getElementsByTagName(tag) { return queryAll(this, tag, false); }
}
function optionValue(o) { return o.attrs.has('value') ? o.attrs.get('value') : o.textContent; }
for (const [prop, attr] of Object.entries(REFLECT)) {
  Object.defineProperty(Element.prototype, prop, {
    get() { return this.attrs.get(attr) || ''; },
    set(v) { this.setAttribute(attr, v); },
    configurable: true,
  });
}
for (const [prop, attr] of Object.entries(BOOL)) {
  Object.defineProperty(Element.prototype, prop, {
    get() { return this.attrs.has(attr); },
    set(v) { if (v) this.setAttribute(attr, ''); else this.removeAttribute(attr); },
    configurable: true,
  });
}

// ---------------------------------------------------------------- serialização e parser
function serialize(node) {
  if (node.nodeType === 3) return escText(node.data);
  if (node.nodeType === 8) return `<!--${node.data}-->`;
  if (node.nodeType === 11) return node.childNodes.map(serialize).join('');
  let attrs = '';
  node.attrs.forEach((v, k) => { attrs += v === '' ? ` ${k}` : ` ${k}="${escAttr(v)}"`; });
  if (Object.keys(node._styleProps).length) attrs += ` style="${escAttr(Object.entries(node._styleProps).map(([k, v]) => `${k}:${v}`).join(';'))}"`;
  if (node._value !== undefined && ['input', 'select', 'textarea'].includes(node.localName)) attrs += ` data-live-value="${escAttr(String(node._value))}"`;
  if (node._checked) attrs += ' data-live-checked';
  if (VOID.has(node.localName)) return `<${node.localName}${attrs}>`;
  return `<${node.localName}${attrs}>${node.childNodes.map(serialize).join('')}</${node.localName}>`;
}

const SVG_TAGS = new Set(['svg', 'g', 'path', 'circle', 'rect', 'line', 'polyline', 'polygon', 'text', 'tspan', 'defs', 'linearGradient', 'stop', 'clipPath', 'title', 'use', 'ellipse', 'marker', 'pattern', 'filter']);
function parseInto(doc, parent, html) {
  const stack = [parent];
  const top = () => stack[stack.length - 1];
  const re = /<!--([\s\S]*?)-->|<\/([A-Za-z][\w:-]*)\s*>|<([A-Za-z][\w:-]*)((?:\s+[^\s=>\/]+(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+))?)*)\s*(\/?)>|([^<]+|<)/g;
  let m;
  let inSvg = 0;
  while ((m = re.exec(html))) {
    if (m[1] !== undefined) { top().appendChild(new Comment(doc, m[1])); continue; }
    if (m[2]) {
      const name = m[2].toLowerCase();
      for (let i = stack.length - 1; i > 0; i -= 1) {
        if (stack[i].localName.toLowerCase() === name) { stack.length = i; if (name === 'svg') inSvg = Math.max(0, inSvg - 1); break; }
      }
      continue;
    }
    if (m[3]) {
      const tag = m[3];
      const lower = tag.toLowerCase();
      const isSvg = lower === 'svg' || inSvg > 0 || SVG_TAGS.has(tag);
      const el = new Element(doc, isSvg ? tag : lower, isSvg ? 'http://www.w3.org/2000/svg' : null);
      const attrRe = /([^\s=>\/]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g;
      let a;
      while ((a = attrRe.exec(m[4] || ''))) {
        const value = a[2] !== undefined ? a[2] : a[3] !== undefined ? a[3] : a[4] !== undefined ? a[4] : '';
        el.attrs.set(a[1], decode(value));
      }
      top().appendChild(el);
      if (lower === 'script' || lower === 'style') {
        const end = html.toLowerCase().indexOf(`</${lower}`, re.lastIndex);
        const stop = end < 0 ? html.length : end;
        const raw = html.slice(re.lastIndex, stop);
        if (raw) el.appendChild(new Text(doc, raw));
        re.lastIndex = end < 0 ? html.length : html.indexOf('>', end) + 1;
        continue;
      }
      if (!m[5] && !VOID.has(lower)) { stack.push(el); if (lower === 'svg') inSvg += 1; }
      continue;
    }
    if (m[4] !== undefined || m[0]) top().appendChild(new Text(doc, decode(m[0])));
  }
}

// ---------------------------------------------------------------- seletores
function parseCompound(src) {
  const parts = [];
  const re = /(\*|[A-Za-z][\w-]*)|#([\w-]+)|\.([\w-]+)|\[([\w-]+)(?:([~^$*|]?=)(?:"([^"]*)"|'([^']*)'|([^\]]*)))?\]|:not\(([^)]*)\)|:([\w-]+)/g;
  let m;
  while ((m = re.exec(src))) {
    if (m[1]) parts.push({ t: 'tag', v: m[1].toLowerCase() });
    else if (m[2]) parts.push({ t: 'id', v: m[2] });
    else if (m[3]) parts.push({ t: 'class', v: m[3] });
    else if (m[4]) parts.push({ t: 'attr', n: m[4], op: m[5] || '', v: m[6] !== undefined ? m[6] : m[7] !== undefined ? m[7] : m[8] });
    else if (m[9] !== undefined) parts.push({ t: 'not', v: parseCompound(m[9].trim()) });
    else if (m[10]) parts.push({ t: 'pseudo', v: m[10] });
  }
  return parts;
}
const selCache = new Map();
function parseSelector(sel) {
  if (selCache.has(sel)) return selCache.get(sel);
  const list = sel.split(',').map(s => s.trim()).filter(Boolean).map(s => {
    const tokens = s.replace(/\s*>\s*/g, ' > ').split(/\s+/);
    const chain = [];
    let comb = ' ';
    tokens.forEach(tok => { if (tok === '>') comb = '>'; else { chain.push({ comb, parts: parseCompound(tok) }); comb = ' '; } });
    return chain;
  });
  selCache.set(sel, list);
  return list;
}
function matchCompound(el, parts) {
  return parts.every(p => {
    switch (p.t) {
      case 'tag': return p.v === '*' || el.localName.toLowerCase() === p.v;
      case 'id': return el.attrs.get('id') === p.v;
      case 'class': return el._classes().has(p.v);
      case 'attr': {
        if (!el.attrs.has(p.n)) return false;
        const val = el.attrs.get(p.n);
        switch (p.op) { case '': return true; case '=': return val === p.v; case '^=': return val.startsWith(p.v); case '$=': return val.endsWith(p.v); case '*=': return val.includes(p.v); case '~=': return val.split(/\s+/).includes(p.v); default: return false; }
      }
      case 'not': return !matchCompound(el, p.v);
      case 'pseudo':
        if (p.v === 'first-child') return !!el.parentNode && el.parentNode.children[0] === el;
        if (p.v === 'checked') return el.checked;
        if (p.v === 'disabled') return el.hasAttribute('disabled');
        return false;
      default: return false;
    }
  });
}
function matchChain(el, chain, i) {
  const link = chain[i];
  if (!matchCompound(el, link.parts)) return false;
  if (i === 0) return true;
  if (link.comb === '>') return !!el.parentNode && el.parentNode.nodeType === 1 && matchChain(el.parentNode, chain, i - 1);
  for (let a = el.parentNode; a && a.nodeType === 1; a = a.parentNode) if (matchChain(a, chain, i - 1)) return true;
  return false;
}
function matchesSelector(el, sel) { return parseSelector(sel).some(chain => matchChain(el, chain, chain.length - 1)); }
function queryAll(root, sel, first) {
  const out = [];
  const chains = parseSelector(sel);
  const walk = node => {
    for (const child of node.childNodes) {
      if (child.nodeType !== 1) continue;
      if (chains.some(chain => matchChain(child, chain, chain.length - 1))) { out.push(child); if (first) return true; }
      if (walk(child)) return true;
    }
    return false;
  };
  walk(root);
  return out;
}

// ---------------------------------------------------------------- documento
class Document extends Node {
  constructor() {
    super(null);
    this.ownerDocument = this;
    this.nodeType = 9;
    this.stats = { added: 0, removed: 0, mutations: 0 };
    this._listeners = new Map();
    this.onElementConnected = null;
    this.window = null;
    this.documentElement = new Element(this, 'html');
    this.head = new Element(this, 'head');
    this.body = new Element(this, 'body');
    this.documentElement.parentNode = this; this.childNodes.push(this.documentElement);
    this.documentElement.childNodes.push(this.head, this.body);
    this.head.parentNode = this.documentElement; this.body.parentNode = this.documentElement;
    this.activeElement = this.body;
    this.hidden = false;
    this.visibilityState = 'visible';
    this.readyState = 'complete';
  }
  _mutated() { this.stats.mutations += 1; }
  _connected(el) { if (this.onElementConnected && el.isConnected) this.onElementConnected(el); }
  createElement(tag) { return new Element(this, String(tag).toLowerCase()); }
  createElementNS(ns, tag) { return new Element(this, tag, ns); }
  createTextNode(t) { return new Text(this, t); }
  createDocumentFragment() { return new Fragment(this); }
  getElementById(id) { return queryAll(this, `#${id}`, true)[0] || null; }
  querySelector(sel) { return queryAll(this, sel, true)[0] || null; }
  querySelectorAll(sel) { return queryAll(this, sel, false); }
  addEventListener(type, fn, opts) { Element.prototype.addEventListener.call(this, type, fn, opts); }
  removeEventListener(type, fn) { Element.prototype.removeEventListener.call(this, type, fn); }
  dispatchEvent(event) { return this._dispatch(this, event); }
  _dispatch(target, event) {
    event.target = target;
    const path = [];
    for (let n = target; n; n = n.parentNode) path.push(n);
    if (this.window) path.push(this.window);
    for (const node of path) {
      if (event._stop) break;
      event.currentTarget = node;
      const list = (node._listeners && node._listeners.get(event.type)) || [];
      for (const l of list.slice()) {
        try { l.fn.call(node, event); } catch (error) { this.window && this.window.console.error('[listener]', error); }
        if (l.once) node.removeEventListener(event.type, l.fn);
      }
      if (!event.bubbles && node === target) break;
    }
    return !event.defaultPrevented;
  }
  parseHtml(html) {
    // Só o <body> e os <script src> interessam; <head> do arquivo é ignorado.
    const bodyMatch = /<body[^>]*>([\s\S]*)<\/body>/i.exec(html);
    parseInto(this, this.body, bodyMatch ? bodyMatch[1] : html);
  }
}

module.exports = { Document, Element, DomEvent, serialize, queryAll, escText };
