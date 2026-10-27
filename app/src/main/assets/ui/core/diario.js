(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.OmegasDiario = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const FORMAT = 'omegas-diario-v1';
  const WHO = ['VOCE', 'REFINO', 'AUTOCAL_ECU', 'D1_SOMBRA', 'ANTIAPAGAO'];
  const WHAT = ['SUGERIU', 'GRAVOU', 'DESFEZ', 'RESTAURAR', 'ECU_MUDOU', 'DESFECHO', 'CONGELOU', 'LIBEROU'];
  const VERDICTS = ['CONFIRMADA', 'PASSOU', 'CURTA', 'PIOROU', 'SEM_BASE'];
  const copy = v => JSON.parse(JSON.stringify(v));
  function number(v) {
    if (v == null) return null;
    if (typeof v !== 'number' || !Number.isFinite(v)) throw Error('Número inválido');
    return v;
  }
  function region(r) {
    r = r || {};
    const out = {};
    ['banda', 'coluna', 'ponto'].forEach(k => {
      out[k] = number(r[k]);
      if (out[k] !== null && (!Number.isSafeInteger(out[k]) || out[k] < 0)) throw Error('Região inválida');
    });
    return out;
  }
  const key = r => JSON.stringify(region(r));
  function normalize(e, seq) {
    if (!e || !Number.isSafeInteger(e.leituras) || e.leituras < 0 || !WHO.includes(e.quem) || !WHAT.includes(e.oque) || !['CURVA_K', 'MAPA_K'].includes(e.alvo)) throw Error('Evento inválido');
    const d = e.desfecho || {};
    if (d.veredito != null && !VERDICTS.includes(d.veredito)) throw Error('Desfecho inválido');
    if (e.curva != null && typeof e.curva !== 'string') throw Error('Curva inválida');
    for (const v of [e.ref, d.leiturasNovas]) if (v != null && (!Number.isSafeInteger(v) || v < 0)) throw Error('Contagem inválida');
    return { seq, leituras: e.leituras, quem: e.quem, oque: e.oque, alvo: e.alvo,
      regiao: region(e.regiao), antes: number(e.antes), depois: number(e.depois), curva: e.curva == null ? null : e.curva,
      desfecho: { veredito: d.veredito == null ? null : d.veredito, erroPct: number(d.erroPct), leiturasNovas: number(d.leiturasNovas) }, ref: e.ref == null ? null : e.ref };
  }
  // Interface storage: load() -> {format, eventos}; save(snapshot). Sem acesso implícito ao navegador.
  function localStorageAdapter(storage, name) {
    name = name || 'omegas-diario-v1';
    return {
      load() { try { const raw = storage.getItem(name); return raw ? JSON.parse(raw) : null; } catch (e) { throw Error('Diário não carregado: ' + e.message); } },
      save(v) { try { storage.setItem(name, JSON.stringify(v)); } catch (e) { throw Error('Diário não salvo: ' + e.message); } }
    };
  }
  function create(options) {
    const storage = (options || {}).storage;
    let events = [], seq = 0, reads = 0;
    const api = { loadError: null, saveError: null, append, list: () => copy(events), query, resumo };
    if (storage) try {
      const raw = storage.load();
      if (raw != null) {
        if (raw.format !== FORMAT || !Array.isArray(raw.eventos)) throw Error('Formato desconhecido');
        const loaded = raw.eventos.map(e => {
          if (!Number.isSafeInteger(e.seq) || e.seq <= seq || e.leituras < reads) throw Error('Ordem inválida');
          const normalized = normalize(e, e.seq); seq = e.seq; reads = e.leituras; return normalized;
        });
        events = loaded; prune();
      }
    } catch (e) { api.loadError = e.message; events = []; seq = 0; reads = 0; }
    function prune() {
      const last = new Map();
      events.forEach(e => { if (e.oque === 'DESFECHO') last.set(e.alvo + key(e.regiao), e.seq); });
      const protectedSeq = new Set(last.values());
      // Se houver mais de 2000 regiões, prevalece o limite: retém os desfechos mais recentes.
      let remove = Math.max(0, events.length - 2000);
      events = events.filter(e => { if (remove && !protectedSeq.has(e.seq)) { remove--; return false; } return true; }).slice(-2000);
    }
    function append(e) {
      const next = normalize(e, seq + 1);
      if (next.leituras < reads) throw Error('Leituras fora de ordem');
      events.push(next); seq++; reads = next.leituras; prune();
      if (storage && !api.loadError) try { storage.save({ format: FORMAT, eventos: copy(events) }); api.saveError = null; } catch (error) { api.saveError = error.message; }
      return copy(next);
    }
    function query(filter) {
      filter = filter || {};
      return copy(events.filter(e => (!filter.regiao || key(e.regiao) === key(filter.regiao)) && (!filter.por || e.quem === filter.por) && (!filter.tipo || e.oque === filter.tipo)));
    }
    function resumo(regiao) {
      const found = query({ regiao });
      return { total: found.length, ultimo: found[found.length - 1] || null, ultimoDesfecho: found.filter(e => e.oque === 'DESFECHO').pop() || null };
    }
    return api;
  }
  return { FORMAT, create, localStorageAdapter };
});
