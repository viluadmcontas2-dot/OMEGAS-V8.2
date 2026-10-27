// DOM deliberadamente pequeno: não simula layout CSS nem WebView.
function documentMinimo() {
  const document = { createElement(tag) { return new Element(tag, document); } };
  return document;
}
class Element {
  constructor(tag, document) {
    this.tagName = tag.toUpperCase(); this.ownerDocument = document;
    this.children = []; this.attributes = {}; this.style = {}; this.hidden = false;
    this.listeners = {}; this._text = ''; this.parentElement = null;
  }
  set textContent(value) { this._text = String(value); this.children = []; }
  get textContent() { return this._text + this.children.map(x => x.textContent).join(''); }
  setAttribute(key, value) { this.attributes[key] = String(value); }
  getAttribute(key) { return this.attributes[key] ?? null; }
  appendChild(child) { this.children.push(child); child.parentElement = this; return child; }
  insertBefore(child, before) { const i = this.children.indexOf(before); if (i < 0) return this.appendChild(child); this.children.splice(i, 0, child); child.parentElement = this; return child; }
  addEventListener(event, fn) { this.listeners[event] = fn; }
  click() { return this.listeners.click?.({ preventDefault() {} }); }
  querySelectorAll(selector) {
    const match = selector.match(/^\[data-fluxo="(.+)"\]$/);
    return this.children.flatMap(x => [...(match && x.getAttribute('data-fluxo') === match[1] ? [x] : []), ...x.querySelectorAll(selector)]);
  }
  querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }
  getBoundingClientRect() { return { x: 0, y: 0, width: 240, height: 56 }; }
}
module.exports = { documentMinimo };
