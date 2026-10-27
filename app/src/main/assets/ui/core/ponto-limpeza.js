(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  // Limpar pontos aprendidos do AutoCal: UM caminho para o botão de zona e para o filtro de RPM baixo.
  // Um toque = um lote para a central (a ponte nativa já grava as duas máscaras e o commit de uma vez, com leitura de volta).
  // Aqui ficam só as regras de convivência: fila (nunca dois lotes juntos), fim reconhecido pelo NÚMERO da operação,
  // conferência da contagem depois ("N pontos limpos"; desconhecido continua desconhecido) e uma segunda tentativa
  // automática só do que a central não zerou. Nada por tempo: tudo anda por resposta da central ou leitura nova.
  const POINTS = 18;
  const FUELS = ['GAS', 'PETROL'];
  const BUSY_RE = /em andamento|Outra (a[cç][aã]o|opera|calibra)/i;
  const LOST_LIMIT = 3;       // leituras do estado sem ver a nossa operação (outra ação assumiu a central)
  const FRESH_BEFORE_VERDICT = 2; // leituras novas antes de dizer "não zerou" (a 1ª pode ter sido lida antes do commit)

  const plural = (n, one, many) => n + ' ' + (n === 1 ? one : many);
  const key = t => t.fuel + ':' + t.index;
  const known = v => (v === null || v === undefined || v === '' || !Number.isFinite(Number(v)) ? null : Number(v));
  // Texto da central sem sigla: o dono não precisa saber o que é ECU nem máscara.
  const speak = msg => String(msg || '').replace(/\bECU\b/g, 'central').replace(/\bmask\b/gi, 'pedido').replace(/\s*\d+\/\d+/g, '').replace(/[\s.]+$/, '').trim();

  function normalize(targets) {
    const seen = new Set();
    const out = [];
    (Array.isArray(targets) ? targets : []).forEach(t => {
      const fuel = String(t && t.fuel || '').toUpperCase();
      const index = t && t.index;
      if (!FUELS.includes(fuel) || !Number.isInteger(index) || index < 0 || index >= POINTS) return;
      const item = { fuel, index };
      if (seen.has(key(item))) return;
      seen.add(key(item));
      out.push(item);
    });
    return out;
  }

  function create(options) {
    const api = options && options.api;
    const notify = (level, message) => { try { options && options.notify && options.notify(level, message); } catch (_) { /* aviso nunca derruba a limpeza */ } };
    const readCounters = () => { try { return (options && options.counters && options.counters()) || null; } catch (_) { return null; } };
    const changed = () => { try { options && options.onChange && options.onChange(); } catch (_) { /* repintar é opcional */ } };
    const queue = [];
    const verifying = [];
    let active = null;
    let lastCounterId;
    let last = null; // última limpeza concluída (zona ou escolha): o que "Desfazer" restaura, até a próxima limpeza

    const counter = (c, t) => (c && Array.isArray(c[t.fuel]) ? known(c[t.fuel][t.index]) : null);

    function allKeys() {
      const out = new Set();
      queue.concat(active ? [active] : [], verifying).forEach(job => job.targets.forEach(t => out.add(key(t))));
      return out;
    }

    function fail(job, reason) {
      const why = speak(reason);
      notify('warning', 'Não consegui limpar ' + job.label + (why ? ': ' + why : '') + '. Toque de novo.');
      changed();
    }

    function pump() {
      while (!active && queue.length) {
        const job = queue[0];
        if (!api || typeof api.preparePointDeleteBatch !== 'function' || typeof api.execute !== 'function') {
          queue.shift(); fail(job, 'A ligação com a central não está disponível'); continue;
        }
        let prepared;
        try { prepared = api.preparePointDeleteBatch(job.targets); } catch (error) { prepared = { ok: false, error: error && error.message }; }
        if (!prepared || prepared.ok !== true || !prepared.prepared) {
          // Central ocupada com outra ação: o pedido espera a vez (volta a tentar quando o estado mudar).
          if (BUSY_RE.test(String(prepared && prepared.error || ''))) return;
          queue.shift(); fail(job, prepared && prepared.error); continue;
        }
        let started;
        try { started = api.execute(prepared.preparationId); } catch (error) { started = { ok: false, error: error && error.message }; }
        if (!started || started.ok !== true) {
          try { api.cancelPreparation && api.cancelPreparation(); } catch (_) { /* nada a desfazer */ }
          if (BUSY_RE.test(String(started && started.error || ''))) return;
          queue.shift(); fail(job, started && started.error); continue;
        }
        queue.shift();
        job.phase = 'sending';
        job.preparationId = prepared.preparationId;
        job.lost = 0;
        active = job;
      }
      changed();
    }

    function request(rawTargets, meta) {
      const info = meta || {};
      const mine = allKeys();
      const targets = normalize(rawTargets).filter(t => !mine.has(key(t)));
      if (!targets.length) return { accepted: 0 };
      const counters = readCounters();
      const before = {};
      targets.forEach(t => { before[key(t)] = counter(counters, t); });
      const kind = info.kind || 'zone';
      if (kind !== 'lowrpm') last = null; // pedido novo: o "Desfazer" passa a valer só para o que vier a ser apagado agora
      const open = kind === 'lowrpm' ? queue.find(j => j.kind === 'lowrpm' && !j.retried) : null;
      if (open) { // o que chega com a central ocupada junta no mesmo lote
        open.targets = open.targets.concat(targets);
        Object.assign(open.before, before);
        pump();
        return { accepted: targets.length };
      }
      const job = { targets, kind, label: info.label || 'pontos', before, retried: false, phase: 'queued', limit: info.limit };
      queue.push(job);
      if (job.kind !== 'lowrpm') notify('working', (kind === 'selection' ? 'Apagando ' : 'Limpando ') + job.label + '…');
      pump();
      return { accepted: targets.length };
    }

    function onActionState(state) {
      if (active && state) {
        if (state.preparationId === active.preparationId && (state.state === 'CONFIRMED' || state.state === 'FAILED')) {
          const job = active;
          active = null;
          if (state.state === 'FAILED') fail(job, state.message);
          else {
            job.phase = 'verifying';
            job.seenId = lastCounterId;
            job.fresh = 0;
            verifying.push(job);
            if (job.kind !== 'lowrpm') notify('working', 'Pronto. Conferindo ' + job.label + '…');
          }
        } else if (state.preparationId !== active.preparationId && state.busy !== true) {
          active.lost += 1;
          if (active.lost >= LOST_LIMIT) {
            const job = active;
            active = null;
            notify('warning', 'Não consegui conferir ' + job.label + '. Olhe os pontos e toque de novo.');
          }
        }
      }
      pump();
    }

    function verdict(job, c) {
      let cleared = 0; let empty = 0; let unknown = 0;
      const remaining = [];
      job.targets.forEach(t => {
        const b = job.before[key(t)];
        const a = counter(c, t);
        if (b === null || a === null) { unknown += 1; return; }
        if (b === 0) { empty += 1; return; }
        if (a < b) { cleared += 1; return; }
        if (b === 1) { unknown += 1; return; } // 1 → 1 pode ser ponto novo já entrando: inconclusivo
        remaining.push(t);
      });
      return { cleared, empty, unknown, remaining };
    }

    function finish(job, v) {
      let text;
      if (job.kind === 'lowrpm') {
        text = v.cleared > 0 ? 'Apaguei ' + plural(v.cleared, 'ponto aprendido', 'pontos aprendidos') + ' abaixo de ' + (job.limit || 1000) + ' rpm.' : null;
      } else if (job.kind === 'selection' && v.cleared > 0) {
        text = 'Apaguei ' + plural(v.cleared, 'ponto', 'pontos') + '.';
      } else if (v.cleared > 0) text = job.label + ' limpa: ' + plural(v.cleared, 'ponto', 'pontos') + '.';
      else if (v.unknown > 0) text = job.label + ': a central confirmou a limpeza.';
      else text = job.label + ' já estava vazia.';
      if (job.kind !== 'lowrpm' && v.cleared > 0) {
        const snapshot = {};
        job.targets.forEach(t => { snapshot[key(t)] = job.before[key(t)]; });
        last = { label: job.label, targets: job.targets.map(t => ({ fuel: t.fuel, index: t.index })), before: snapshot };
      }
      if (text) notify('ok', text);
    }

    function onSnapshot(c) {
      if (!c) return;
      lastCounterId = c.id;
      for (const job of verifying.slice()) {
        if (c.id !== undefined && c.id === job.seenId) continue;
        job.fresh += 1;
        const v = verdict(job, c);
        if (!v.remaining.length) {
          verifying.splice(verifying.indexOf(job), 1);
          finish(job, v);
        } else if (job.fresh >= FRESH_BEFORE_VERDICT) {
          verifying.splice(verifying.indexOf(job), 1);
          if (!job.retried) {
            const again = Object.assign({}, job, { targets: v.remaining, retried: true, phase: 'queued' });
            queue.unshift(again);
            notify('working', 'Repetindo a limpeza de ' + plural(v.remaining.length, 'ponto', 'pontos') + '…');
          } else {
            notify('warning', job.label + ': a central não zerou ' + plural(v.remaining.length, 'ponto', 'pontos') + '. Toque de novo.');
          }
        }
      }
      pump();
    }

    const canUndo = () => { try { return !!(api && typeof api.canUndoPointDelete === 'function' && api.canUndoPointDelete() && typeof api.undoPointDelete === 'function'); } catch (_) { return false; } };

    function view() {
      return {
        active: !!active,
        pending: queue.length + (active ? 1 : 0) + verifying.length,
        undo: { has: !!last, supported: canUndo(), label: last ? last.label : '' },
      };
    }

    /** Desfazer a última limpeza em 1 toque, se a central deixar restaurar; senão diz o motivo (não finge). */
    function undo() {
      if (!last) return { ok: false };
      if (!canUndo()) {
        notify('warning', 'A central ainda não deixa restaurar estes pontos. Ela volta a aprender sozinha.');
        return { ok: false };
      }
      let result;
      try { result = api.undoPointDelete(JSON.stringify({ targets: last.targets, before: last.before })); } catch (error) { result = { ok: false, error: error && error.message }; }
      if (!result || result.ok === false) {
        const why = speak(result && result.error);
        notify('warning', 'Não consegui restaurar ' + last.label + (why ? ': ' + why : '') + '. Toque em Desfazer de novo.');
        return { ok: false };
      }
      notify('ok', 'Restaurei ' + last.label + '.');
      last = null;
      changed();
      return { ok: true };
    }

    return { request, onActionState, onSnapshot, view, undo, busyKeys: allKeys };
  }

  ns.PointClear = { create, normalize };
})(typeof globalThis !== 'undefined' ? globalThis : this);
