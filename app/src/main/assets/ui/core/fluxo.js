(function (root, factory) {
  const api = factory(root);
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.OmegasFluxo = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (root) {
  'use strict';
  const primaries = new Map(), lasts = new Map(), entries = [], active = new Set();
  let sequence = 0, backend;
  function drawing() {
    return root.OmegasAcaoPrincipal || (typeof require === 'function' ? require('../components/acao-principal.js') : null);
  }
  function mountPrimary(host, options) {
    if (primaries.has(options.id)) return primaries.get(options.id);
    const renderer = drawing().primary(host, options);
    const state = { enabled: false, busy: false, reason: 'Nada mais para gravar agora' };
    const api = { id: options.id, el: renderer.el, set(patch) { Object.assign(state, patch); renderer.render(state); } };
    api.set({}); primaries.set(options.id, api); return api;
  }
  function record(id, value) {
    const entry = Object.assign({}, value, { id: id + ':' + (++sequence), at: sequence, by: value.by || 'você', what: value.what || 'Gravação', flowId: id });
    entries.push(entry); return entry;
  }
  function mountLast(host, options) {
    if (lasts.has(options.id)) return lasts.get(options.id);
    let current = null;
    const renderer = drawing().last(host, options, () => undoById(current.id));
    const api = {
      record(value) { current = record(options.id, value); renderer.render(current); return { ...current }; },
      clear() { current = null; renderer.render(null); },
      view() { return current ? { ...current } : null; },
      refresh() { renderer.render(current); }
    };
    api.clear(); lasts.set(options.id, api); return api;
  }
  function refresh() { lasts.forEach(last => last.refresh()); }
  async function undoById(id) {
    const entry = entries.find(item => item.id === id);
    if (!entry || !entry.undo || typeof entry.undo.run !== 'function' || entry.undoable === false || entry.undone || entry.undoing || active.has(entry.flowId)) return { ok: false, details: 'Sem restauração disponível' };
    entry.undoing = true; active.add(entry.flowId); refresh();
    try {
      // O adaptador de undo deve resolver somente depois de ACK + readback.
      const result = await entry.undo.run();
      if (!(result === true || (result && result.ok === true))) throw new Error('Restauração não conferida');
      entry.undone = true; return { ok: true };
    } catch (error) {
      return { ok: false, retry: () => undoById(id), leave: () => ({ ok: false }), details: String(error.message || error) };
    } finally { entry.undoing = false; active.delete(entry.flowId); refresh(); }
  }
  function status(app, method, ...args) {
    // Um consumidor de selo ausente/com defeito não pode transformar uma escrita
    // já conferida em falha que ofereça repetir os bytes.
    try { if (app && app.operationStatus && typeof app.operationStatus[method] === 'function') app.operationStatus[method](...args); } catch (_) { /* selo opcional */ }
  }
  async function run(options) {
    const primary = primaries.get(options.id);
    const app = options.app || root.OmegasApp;
    const failure = error => ({ ok: false, retry: () => run(options), leave: () => { status(app, 'clear', options.owner); return { ok: false }; }, details: String(error.message || error) });
    if (active.has(options.id)) return { ok: false, details: 'Gravação em andamento' };
    active.add(options.id);
    try {
      const gate = root.OmegasWriteGate || (typeof require === 'function' ? require('./write-gate.js') : null);
      const autopilot = typeof options.autopilot === 'function' ? options.autopilot() : options.autopilot;
      const permission = gate ? gate.check(autopilot) : { ok: false, message: 'Porta de gravação indisponível' };
      const checked = permission.ok && options.check ? await options.check() : permission;
      if (!checked || checked.ok !== true) {
        const reason = checked && (checked.message || checked.reason) || 'Espere a central';
        if (primary) primary.set({ enabled: false, busy: false, reason });
        return failure(reason);
      }
      // O adaptador de eventos deve resolver write() no BATCH_CONFIRMED, nunca no envio.
      if (typeof options.write !== 'function' || typeof options.verify !== 'function') throw new Error('Falta conferir a leitura de volta');
      // Prévia do registro: se for inválido, falha antes de enviar qualquer byte.
      if (typeof options.record === 'function') options.record({});
      if (primary) primary.set({ enabled: true, busy: true });
      status(app, 'update', options.owner, 'Gravando…');
      const written = await options.write();
      if (written === false || (written && written.ok === false)) throw new Error('Gravação não confirmada');
      try { if (app && app.writeVerify && typeof app.writeVerify.begin === 'function') app.writeVerify.begin(); } catch (_) { /* observador opcional */ }
      const verified = await options.verify(written);
      if (!(verified === true || (verified && verified.ok === true))) throw new Error('Leitura de volta não conferida');
      const value = typeof options.record === 'function' ? options.record(written) : options.record || { what: 'Gravação' };
      const last = lasts.get(options.id);
      const entry = last ? last.record(value) : record(options.id, value);
      status(app, 'clear', options.owner);
      status(app, 'done', options.owner, 'Gravado e conferido');
      if (primary) primary.set(Object.assign({ enabled: false, reason: 'Nada mais para gravar agora' }, options.next || {}));
      return { ok: true, record: entry };
    } catch (error) {
      const result = failure(error);
      if (primary) primary.set({ enabled: false, reason: 'Tentar de novo · ' + result.details });
      status(app, 'fail', options.owner, result.details);
      return result;
    } finally { active.delete(options.id); if (primary) primary.set({ busy: false }); }
  }
  // Backend compatível com Storage. Falha é explícita; não promete persistência em memória.
  const pending = {
    use(storage) { backend = storage; },
    save(key, payload) { try { (backend || root.localStorage).setItem('omegas.fluxo.' + key, JSON.stringify(payload)); return true; } catch (_) { return false; } },
    get(key) { try { return JSON.parse((backend || root.localStorage).getItem('omegas.fluxo.' + key) || 'null'); } catch (_) { return null; } },
    clear(key) { try { (backend || root.localStorage).removeItem('omegas.fluxo.' + key); return true; } catch (_) { return false; } }
  };
  return { mountPrimary, mountLast, history: () => entries.map(entry => ({ ...entry })), undoById, run, pending, registry: () => Array.from(primaries.values()) };
});
