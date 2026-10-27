'use strict';
async function snapshot(page, seletores) {
  return page.evaluate(selectors => selectors.flatMap(selector => Array.from(document.querySelectorAll(selector), (el, index) => {
    const rect = el.getBoundingClientRect();
    let hidden = rect.width === 0 || rect.height === 0;
    for (let node = el; node; node = node.parentElement) {
      const style = getComputedStyle(node);
      hidden = hidden || node.hidden || style.display === 'none' || style.visibility === 'hidden' || style.visibility === 'collapse' || style.opacity === '0';
    }
    return { key: selector + ':' + (el.id || index), selector, hidden: !!hidden, x: rect.x, y: rect.y, width: rect.width, height: rect.height };
  })), seletores);
}
function compararEstados(a, b) {
  const before = new Map(a.map(item => [item.key, item]));
  const after = new Map(b.map(item => [item.key, item]));
  const changes = [];
  for (const [key, item] of before) {
    const next = after.get(key);
    if (!next) { changes.push({ key, tipo: 'sumiu' }); continue; }
    if (next.hidden) changes.push({ key, tipo: 'escondido' });
    if (Math.abs(next.x - item.x) > 2 || Math.abs(next.y - item.y) > 2) changes.push({ key, tipo: 'mudou de lugar' });
  }
  for (const [key] of after) if (!before.has(key)) changes.push({ key, tipo: 'apareceu' });
  return changes;
}
module.exports = { snapshot, compararEstados };
