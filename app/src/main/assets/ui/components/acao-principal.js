(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.OmegasAcaoPrincipal = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  function element(host, tag, kind) {
    const el = host.ownerDocument.createElement(tag);
    if (kind) el.setAttribute('data-fluxo', kind);
    if (tag === 'button') el.type = 'button';
    return el;
  }
  function primary(host, options) {
    const el = element(host, 'button', 'principal');
    el.id = options.id + '-principal';
    host.appendChild(el);
    let state;
    el.addEventListener('click', function () {
      if (state.enabled && !state.busy && typeof state.onTap === 'function') return state.onTap();
    });
    return { el, render(next) {
      state = next;
      const disabled = !state.enabled || state.busy;
      el.setAttribute('aria-disabled', String(!!disabled));
      el.setAttribute('aria-busy', String(!!state.busy));
      const base = options.base || 'Gravar correção';
      const label = state.count == null ? base : base.replace(/\bN\b/g, String(state.count));
      el.textContent = state.busy ? 'Gravando…' : label + (disabled ? ' · ' + (state.reason || 'Nada mais para gravar agora') : '');
    } };
  }
  function last(host, options, onUndo) {
    const el = element(host, 'section', 'ultima');
    el.id = options.id + '-ultima';
    const title = element(host, 'strong'); title.textContent = 'Última gravação';
    const description = element(host, 'p'); description.setAttribute('aria-live', 'polite');
    const undo = element(host, 'button', 'desfazer');
    el.appendChild(title); el.appendChild(description); el.appendChild(undo);
    host.insertBefore(el, host.querySelector('[data-fluxo="principal"]'));
    let enabled = false;
    undo.addEventListener('click', function () { if (enabled) return onUndo(); });
    return { el, render(record) {
      enabled = !!(record && record.undo && typeof record.undo.run === 'function' && record.undoable !== false && !record.undone && !record.undoing);
      description.textContent = record ? record.what + ' · ' + record.by + ' · vale até fechar o app' : 'Nenhuma gravação ainda';
      undo.setAttribute('aria-disabled', String(!enabled));
      undo.textContent = enabled ? (record.undo.label || 'Desfazer') : 'Desfazer · ' + (!record ? 'Nenhuma gravação ainda' : record.undoing ? 'Desfazendo…' : record.undone ? 'Já desfeito' : 'Sem restauração disponível');
    } };
  }
  return { primary, last };
});
