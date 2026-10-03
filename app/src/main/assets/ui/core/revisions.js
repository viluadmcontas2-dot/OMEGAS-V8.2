(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Revisões por tipo de dado (live / evidence / tables / session). O Kotlin só sobe a revisão quando o dado muda
  // e traz o número em todo quadro (`revisions`) e por empurrão (`window.OmegasOnRevision(kind, revision)`).
  // A UI relê evidência, tabelas e sessão SÓ quando a revisão do tipo andou; o vigia (WATCHDOG_MS) relê de qualquer
  // modo, para que um empurrão perdido nunca deixe a tela velha. Tempo real (cursor, RPM, ms, MAP) não passa por aqui.
  const KINDS = ['live', 'evidence', 'tables', 'session'];
  const SLOW_KINDS = ['evidence', 'tables', 'session'];
  const WATCHDOG_MS = 2500;

  const latest = { live: -1, evidence: -1, tables: -1, session: -1 };
  const listeners = new Set();

  function number(value) {
    const n = Number(value);
    return Number.isFinite(n) ? n : null;
  }

  function emit(kind, revision) {
    listeners.forEach(listener => {
      try { listener(kind, revision); } catch (error) { console.error('[OMEGAS revisions]', error); }
    });
  }

  /** Anota a revisão de um tipo; avisa os ouvintes só quando ela mudou. Devolve true se mudou. */
  function note(kind, revision) {
    const n = number(revision);
    if (!KINDS.includes(kind) || n === null || n === latest[kind]) return false;
    latest[kind] = n;
    emit(kind, n);
    return true;
  }

  /** Aceita o objeto `{live,evidence,tables,session}` que vem em cada quadro da ponte. */
  function noteAll(revisions) {
    if (!revisions || typeof revisions !== 'object') return false;
    let moved = false;
    KINDS.forEach(kind => { if (revisions[kind] !== undefined && note(kind, revisions[kind])) moved = true; });
    return moved;
  }

  function subscribe(listener) {
    if (typeof listener !== 'function') return () => {};
    listeners.add(listener);
    return () => listeners.delete(listener);
  }

  /**
   * Portão de releitura de uma tela: `due()` diz se vale reler agora (revisão andou, ocupado, ou vigia vencido);
   * `mark()` registra que acabou de reler. Sem relógio próprio: usa o `now` de quem chama.
   */
  function gate(kinds, watchdogMs) {
    const watched = (kinds || SLOW_KINDS).filter(kind => KINDS.includes(kind));
    const limit = Number.isFinite(watchdogMs) ? watchdogMs : WATCHDOG_MS;
    let seen = null;
    let markedAt = -Infinity;
    return {
      due(busy, now) {
        const clock = Number.isFinite(now) ? now : Date.now();
        if (busy === true || seen === null) return true;
        if (clock - markedAt >= limit) return true;
        return watched.some(kind => latest[kind] !== seen[kind]);
      },
      mark(now) {
        seen = {};
        watched.forEach(kind => { seen[kind] = latest[kind]; });
        markedAt = Number.isFinite(now) ? now : Date.now();
      },
      reset() { seen = null; markedAt = -Infinity; },
    };
  }

  // Empurrão do Kotlin (acelerador): opcional; o poll de segurança continua valendo.
  root.OmegasOnRevision = function (kind, revision) { note(String(kind), revision); };

  ns.Revisions = { KINDS, SLOW_KINDS, WATCHDOG_MS, note, noteAll, subscribe, gate, snapshot: () => Object.assign({}, latest) };
})(typeof window !== 'undefined' ? window : globalThis);
