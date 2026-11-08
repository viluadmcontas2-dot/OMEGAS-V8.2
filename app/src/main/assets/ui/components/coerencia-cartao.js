(function (root, factory) {
  const api = factory(typeof require === 'function' ? require('../core/coerencia.js') : root.OmegasCoerencia);
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.OmegasCoerenciaCartao = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (Coerencia) {
  'use strict';
  const WHO = { 'você': 'VOCE', voce: 'VOCE', refino: 'REFINO', autocal: 'AUTOCAL_ECU' };
  /**
   * Ganchos de registro (um por origem): 'gravacao' (entrada de OmegasFluxo.history com .diario = {alvo, regiao, antes, depois}),
   * 'sugestao' (Refino) e 'autocal' (curva do AutoCal mudou). Sem região conhecida não inventa evento: devolve null.
   */
  function registrar(diario, tipo, dado, leituras) {
    dado = dado || {};
    const base = tipo === 'gravacao' ? dado.diario : dado;
    if (!diario || !base || !base.alvo || !base.regiao) return null;
    const quem = tipo === 'sugestao' ? 'REFINO' : tipo === 'autocal' ? 'AUTOCAL_ECU' : WHO[String(dado.by || '').toLowerCase()] || 'VOCE';
    const oque = tipo === 'sugestao' ? 'SUGERIU' : tipo === 'autocal' ? 'ECU_MUDOU' : 'GRAVOU';
    try {
      return diario.append({ leituras, quem, oque, alvo: base.alvo, regiao: base.regiao, antes: base.antes, depois: base.depois });
    } catch (_) { return null; }
  }
  function onde(r) {
    const p = [];
    if (r && r.banda != null) p.push('faixa ' + r.banda);
    if (r && r.coluna != null) p.push('coluna ' + r.coluna);
    if (r && r.ponto != null) p.push('ponto ' + r.ponto);
    return p.length ? p.join(', ') : '—';
  }
  function texto(a) {
    const n = (/(\d+)/.exec(a.texto) || [])[1];
    switch (a.regra) {
      case 'R1': return 'A sugestão mudou 3 vezes aqui; vou esperar';
      case 'R2': return a.direcao > 0 ? 'O AutoCal divergiu da curva sugerida: baixou o que o Refino subiu' : 'O AutoCal divergiu da curva sugerida: subiu o que o Refino baixou';
      case 'R3': return 'A sugestão mudou ' + (n || 3) + ' vezes sem ser aplicada';
      case 'R4': return 'Região já tratada; espere novos tropeços';
      case 'R5': return 'Dois ajustes no mesmo lugar; um espera o outro';
      default: return 'Espere mais leituras em outras regiões';
    }
  }
  function detalhe(a, now) {
    const falta = a.ate && Number.isFinite(a.ate.leituras) ? a.ate.leituras - now : 0;
    return onde(a.regiao) + (falta > 0 ? ' · faltam ' + falta + ' leituras' : ' · reavalia na próxima leitura');
  }
  /** Avalia o Diário e devolve {itens, liberacoes}. Com {registrar:true} grava cada liberação uma única vez. */
  function avaliar(diario, contexto, opcoes) {
    const now = contexto.leituras;
    const r = Coerencia.avaliar(diario.list(), contexto);
    if (opcoes && opcoes.registrar) r.liberacoes.forEach(function (e) { try { diario.append(e); } catch (_) { /* fora de ordem: tenta na próxima leitura */ } });
    return {
      itens: r.avisos.map(function (a) { return { regra: a.regra, texto: texto(a), detalhe: detalhe(a, now) }; }),
      liberacoes: r.liberacoes.map(function (e) { return 'Liberado: ' + onde(e.regiao) + ' pode voltar a ser sugerido'; })
    };
  }
  function render(host, view) {
    const doc = host.ownerDocument;
    function el(tag, text) { const e = doc.createElement(tag); if (text != null) e.textContent = text; return e; }
    host.replaceChildren();
    host.setAttribute('data-coerencia', 'cartao');
    host.setAttribute('aria-label', 'Coerência');
    host.appendChild(el('strong', 'Coerência'));
    const linhas = view.itens.map(function (i) { return [i.texto, i.detalhe]; }).concat(view.liberacoes.map(function (t) { return [t, '']; }));
    if (!linhas.length) { host.appendChild(el('p', 'Tudo coerente por enquanto')); return; }
    const ul = el('ul');
    linhas.forEach(function (l) {
      const li = el('li');
      li.appendChild(el('span', l[0]));
      if (l[1]) li.appendChild(el('small', l[1]));
      ul.appendChild(li);
    });
    host.appendChild(ul);
  }
  return { registrar, avaliar, render };
});
