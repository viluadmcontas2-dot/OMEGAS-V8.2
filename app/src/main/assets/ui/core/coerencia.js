(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.OmegasCoerencia = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const copy = v => JSON.parse(JSON.stringify(v));
  const key = r => JSON.stringify(['banda', 'coluna', 'ponto'].map(k => r && r[k] != null ? r[k] : null));
  const known = r => r && ['banda', 'coluna', 'ponto'].some(k => r[k] != null);
  const delta = e => Number.isFinite(e.antes) && Number.isFinite(e.depois) ? e.depois - e.antes : 0;
  const sign = e => Math.sign(delta(e));
  /**
   * Entrada: eventos do Diário; contexto {leituras, tropecos:[{regiao,leituras}],
   * localLatch:[{regiao,local:boolean}], cobertura:[{rpm,map,leituras}]}.
   * ate.leituras é contagem ABSOLUTA. R4/R5 exigem reavaliação da evidência,
   * não expiram apenas pela contagem. liberacoes são propostas de evento,
   * que o chamador deve registrar uma vez. Nunca há escrita nem estado interno.
   * R2 informa direção suspensa; R5 informa manter/aguardando. Cobertura R6
   * contém células RPM×MAP já discretizadas pelo chamador, após congelamento.
   */
  function avaliar(events, context) {
    context = context || {};
    const now = context.leituras;
    if (!Number.isSafeInteger(now) || now < 0) throw Error('Contagem de leituras inválida');
    const all = events.filter(e => Number.isSafeInteger(e.leituras) && e.leituras >= 0 && e.leituras <= now)
      .slice().sort((a,b) => a.seq - b.seq);
    const groups = new Map(), avisos = [], liberacoes = [];
    all.forEach(e => { if (known(e.regiao)) { const k = key(e.regiao); if (!groups.has(k)) groups.set(k, []); groups.get(k).push(e); } });
    function aviso(regra, e, texto, acao, until, extra) {
      avisos.push(Object.assign({ regra, regiao: copy(e.regiao), texto, acao, ate: { leituras: until } }, extra));
    }
    groups.forEach((items, k) => {
      // R1: três deslocamentos consecutivos significativos no mesmo alvo.
      ['CURVA_K', 'MAPA_K'].forEach(alvo => {
        const moves = items.filter(e => e.alvo === alvo && ['GRAVOU', 'ECU_MUDOU'].includes(e.oque) && now - e.leituras <= 2000);
        for (let i = moves.length - 1; i >= 2; i--) {
          const triple = moves.slice(i - 2, i + 1);
          if (triple.every(e => e.antes > 0 && Math.abs(delta(e)) / e.antes >= 0.015) && sign(triple[0]) === sign(triple[2]) && sign(triple[0]) === -sign(triple[1])) {
            const last = triple[2];
            if (now < last.leituras + 1500 && !items.some(e => e.oque === 'LIBEROU' && e.seq > last.seq)) aviso('R1', last, 'Esta região foi para cima e para baixo; vou esperar', 'congelar', last.leituras + 1500);
            break;
          }
        }
      });
      // R2: pareia a última gravação com a mudança da central, sem atribuir autoria desconhecida.
      const ecu = items.filter(e => e.oque === 'ECU_MUDOU' && e.quem === 'AUTOCAL_ECU' && e.regiao.ponto != null).pop();
      if (ecu) {
        const write = items.filter(e => e.seq < ecu.seq && e.oque === 'GRAVOU' && e.alvo === ecu.alvo).pop();
        if (write && write.quem === 'REFINO' && ecu.leituras - write.leituras <= 600 && sign(write) !== 0 && sign(ecu) === -sign(write) && Math.abs(delta(ecu)) >= Math.abs(delta(write)) / 2 && now < ecu.leituras + 300) {
          aviso('R2', ecu, sign(write) > 0 ? 'O AutoCal baixou o que o Refino subiu aqui' : 'O AutoCal subiu o que o Refino baixou aqui', 'deixar-autocal', ecu.leituras + 300, { direcao: sign(write) });
        }
      }
      ['CURVA_K', 'MAPA_K'].forEach(alvo => {
        const write = items.filter(e => e.oque === 'GRAVOU' && e.alvo === alvo).pop();
        [-1, 1].forEach(direction => {
          const suggestions = items.filter(e => e.oque === 'SUGERIU' && e.alvo === alvo && sign(e) === direction && now - e.leituras <= 1000 && (!write || e.seq > write.seq));
          if (suggestions.length >= 3) aviso('R3', suggestions[suggestions.length - 1], 'Sugerida ' + suggestions.length + ' vezes, não aplicada', 'avisar', suggestions[0].leituras + 1000, { alvo, direcao: direction });
        });
      });
      const mapWrite = items.filter(e => e.oque === 'GRAVOU' && e.alvo === 'MAPA_K').pop();
      if (mapWrite && Array.isArray(context.tropecos)) {
        const related = context.tropecos.filter(e => key(e.regiao) === k && Number.isSafeInteger(e.leituras) && e.leituras <= now);
        const fresh = related.filter(e => e.leituras > mapWrite.leituras).length;
        if (related.length && fresh < 3) aviso('R4', mapWrite, 'Esta região já foi tratada; espere novos tropeços', 'congelar', now, { tropeçosNovos: fresh, faltam: 3 - fresh });
      }
      const suggestions = items.filter(e => e.oque === 'SUGERIU' && now - e.leituras <= 1000);
      const latch = (context.localLatch || []).find(e => key(e.regiao) === k);
      if (latch && typeof latch.local === 'boolean' && suggestions.some(e => e.alvo === 'CURVA_K') && suggestions.some(e => e.alvo === 'MAPA_K')) {
        const manter = latch.local ? 'MAPA_K' : 'CURVA_K';
        aviso('R5', suggestions[suggestions.length - 1], 'Dois ajustes no mesmo lugar; aguarde pelo outro caminho', 'avisar', now, { manter, aguardando: latch.local ? 'CURVA_K' : 'MAPA_K' });
      }
      const freeze = items.filter(e => ['CONGELOU', 'LIBEROU'].includes(e.oque)).pop();
      if (freeze && freeze.oque === 'CONGELOU') {
        const coverage = new Set((context.cobertura || []).filter(e => Number.isFinite(e.rpm) && Number.isFinite(e.map) && e.leituras > freeze.leituras && e.leituras <= now).map(e => JSON.stringify([e.rpm, e.map])));
        if (now - freeze.leituras >= 1500 && coverage.size >= 3) liberacoes.push({ leituras: now, quem: 'REFINO', oque: 'LIBEROU', alvo: freeze.alvo, regiao: copy(freeze.regiao), ref: freeze.seq });
        else aviso('R6', freeze, 'Espere novas leituras em mais regiões', 'congelar', freeze.leituras + 1500);
      }
    });
    return { avisos, liberacoes };
  }
  return { avaliar };
});
