/* Runtime LOCAL mínimo do formato .dc.html (Design Component), só para renderizar e testar as telas
 * do canvas de design aqui, com Chromium + Playwright (tools/design/render_dc.cjs). Não é o runtime da
 * plataforma: cobre o que as telas OMEGAS usam (buracos {{ caminho.pontilhado }}, <sc-for>, <sc-if>,
 * eventos onClick/onPointer*, state/setState). Copiado como `support.js` ao lado das telas.
 */
(function () {
  'use strict';

  class DCLogic {
    constructor(props) {
      this.props = props || {};
      if (!this.state) this.state = {};
    }
    setState(patch, cb) {
      const p = typeof patch === 'function' ? patch(this.state, this.props) : patch;
      this.state = Object.assign({}, this.state, p || {});
      this.__render();
      if (typeof cb === 'function') cb();
    }
    forceUpdate() { this.__render(); }
  }
  window.DCLogic = DCLogic;

  const HOLE = /\{\{\s*([^}]*?)\s*\}\}/g;
  const WHOLE = /^\s*\{\{\s*([^}]*?)\s*\}\}\s*$/;

  function lookup(path, scope) {
    if (path === 'true') return true;
    if (path === 'false') return false;
    if (path === 'null') return null;
    if (/^-?\d+(\.\d+)?$/.test(path)) return Number(path);
    let cur = scope;
    for (const part of path.split('.')) {
      if (cur === null || cur === undefined) return undefined;
      cur = cur[part];
    }
    return cur;
  }
  const text = (s, scope) => s.replace(HOLE, (_, p) => {
    const v = lookup(p, scope);
    return v === null || v === undefined || v === false ? '' : String(v);
  });

  function build(nodes, scope, parent) {
    for (const n of nodes) {
      if (n.nodeType === 3) { parent.appendChild(document.createTextNode(text(n.nodeValue, scope))); continue; }
      if (n.nodeType !== 1) continue;
      const tag = n.localName;
      if (tag === 'sc-for') {
        const m = WHOLE.exec(n.getAttribute('list') || '');
        const list = (m && lookup(m[1], scope)) || [];
        const as = n.getAttribute('as') || 'item';
        list.forEach((item, i) => {
          const s = Object.create(scope);
          s[as] = item; s.$index = i;
          build(n.childNodes, s, parent);
        });
        continue;
      }
      if (tag === 'sc-if') {
        const m = WHOLE.exec(n.getAttribute('value') || '');
        if (m && lookup(m[1], scope)) build(n.childNodes, scope, parent);
        continue;
      }
      const el = n.cloneNode(false);
      el.__h = {};
      for (const a of Array.from(n.attributes)) {
        const name = a.name;
        const whole = WHOLE.exec(a.value);
        if (/^on[a-z]+$/.test(name) && whole) {
          el.removeAttribute(name);
          const fn = lookup(whole[1], scope);
          if (typeof fn === 'function') el.__h[name.slice(2)] = fn;
          continue;
        }
        if (a.value.indexOf('{{') >= 0) {
          if (whole) {
            const v = lookup(whole[1], scope);
            if (v === false || v === null || v === undefined) el.removeAttribute(name);
            else el.setAttribute(name, v === true ? '' : String(v));
          } else {
            el.setAttribute(name, text(a.value, scope));
          }
        }
      }
      build(n.childNodes, scope, el);
      parent.appendChild(el);
    }
  }

  // Atualiza o DOM existente a partir do novo (mantém elementos, foco e captura de ponteiro).
  function ensureListeners(el) {
    el.__reg = el.__reg || {};
    for (const type of Object.keys(el.__h || {})) {
      if (el.__reg[type]) continue;
      el.__reg[type] = true;
      el.addEventListener(type, (e) => { const f = el.__h && el.__h[type]; if (f) f(e); });
    }
  }
  function morph(oldEl, newEl) {
    const oldAttrs = Array.from(oldEl.attributes).map((a) => a.name);
    for (const name of oldAttrs) if (!newEl.hasAttribute(name)) oldEl.removeAttribute(name);
    for (const a of Array.from(newEl.attributes)) if (oldEl.getAttribute(a.name) !== a.value) oldEl.setAttribute(a.name, a.value);
    oldEl.__h = newEl.__h;
    ensureListeners(oldEl);
    morphChildren(oldEl, newEl);
  }
  function morphChildren(oldP, newP) {
    const o = Array.from(oldP.childNodes);
    const n = Array.from(newP.childNodes);
    const len = Math.max(o.length, n.length);
    for (let i = 0; i < len; i++) {
      const a = o[i]; const b = n[i];
      if (!b) { oldP.removeChild(a); continue; }
      if (!a) { oldP.appendChild(b); if (b.nodeType === 1) walkListeners(b); continue; }
      if (a.nodeType === 3 && b.nodeType === 3) { if (a.nodeValue !== b.nodeValue) a.nodeValue = b.nodeValue; continue; }
      if (a.nodeType === 1 && b.nodeType === 1 && a.localName === b.localName && a.namespaceURI === b.namespaceURI) { morph(a, b); continue; }
      oldP.replaceChild(b, a);
      if (b.nodeType === 1) walkListeners(b);
    }
  }
  function walkListeners(el) {
    ensureListeners(el);
    for (const c of Array.from(el.children)) walkListeners(c);
  }

  function start() {
    const xdc = document.querySelector('x-dc');
    const sc = document.querySelector('script[data-dc-script]');
    if (!xdc || !sc) { window.__dcError = 'sem x-dc ou script'; return; }
    for (const h of Array.from(xdc.querySelectorAll('helmet'))) {
      for (const c of Array.from(h.children)) document.head.appendChild(c.cloneNode(true));
      h.remove();
    }
    const tpl = Array.from(xdc.childNodes);
    xdc.parentNode.removeChild(xdc);
    const host = document.createElement('div');
    host.id = 'dc-host';
    document.body.appendChild(host);
    document.body.style.margin = '0';

    let props = {};
    try {
      const raw = JSON.parse(sc.getAttribute('data-props') || '{}');
      Object.keys(raw).forEach((k) => { if (k !== '$preview' && raw[k] && 'default' in raw[k]) props[k] = raw[k].default; });
    } catch (e) { /* props opcionais */ }

    let inst;
    try {
      const Component = new Function('DCLogic', sc.textContent + '\n;return Component;')(DCLogic);
      inst = new Component(props);
    } catch (e) { window.__dcError = 'script: ' + e.message; throw e; }

    let first = true;
    inst.__render = function () {
      const vals = inst.renderVals ? inst.renderVals() : {};
      const scope = Object.create(vals);
      const fresh = document.createElement('div');
      build(tpl, scope, fresh);
      if (first) {
        while (fresh.firstChild) host.appendChild(fresh.firstChild);
        walkListeners(host);
        first = false;
      } else {
        morphChildren(host, fresh);
      }
    };
    try { inst.__render(); } catch (e) { window.__dcError = 'render: ' + e.message; throw e; }
    window.__dc = inst;
    window.__dcReady = true;
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();
