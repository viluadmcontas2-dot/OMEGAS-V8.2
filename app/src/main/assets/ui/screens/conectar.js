(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.OmegasConectar = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  // Tela Conectar (mockups 01–01d). O app conecta sozinho; o botão só ajuda. Sem relógio: só fase e contagem.
  const FASES = ['PROCURANDO', 'PERMISSAO', 'CONECTANDO', 'LENDO', 'PRONTO', 'RETOMANDO', 'PROBLEMA'];
  const CAUSAS = {
    SEM_PERMISSAO: ['Sem permissão para usar o cabo.', 'Toque em Permitir USB quando o Android pedir.'],
    CABO_SEM_CENTRAL: ['O cabo está ligado, mas a central não aparece.', 'Ligue a ignição e tente de novo.'],
    CENTRAL_NAO_RESPONDE: ['A central não responde.', 'Tire e recoloque o cabo.'],
    APARELHO_INCOMPATIVEL: ['Esse cabo não é da OMEGAS (10C4:EA60).', 'Use o cabo da central.'],
  };
  const PASSOS = ['Aparelho encontrado', 'Permissão', 'Central respondeu', 'Lendo mapas'];
  const ETAPA = { PROCURANDO: 0, PERMISSAO: 1, CONECTANDO: 2, LENDO: 3, PRONTO: 4, RETOMANDO: 0, PROBLEMA: 0 };

  function parse(raw) {
    try {
      const o = typeof raw === 'string' ? JSON.parse(raw) : raw;
      if (o && FASES.includes(o.fase)) return o;
    } catch (_) { /* cai no padrão */ }
    return { fase: 'PROCURANDO', tentativas: 0 };
  }

  function describe(estado) {
    const e = parse(estado);
    const d = { fase: e.fase, titulo: '', linha: '', causa: '', acao: '', botao: '', habilitado: false, etapa: ETAPA[e.fase], concluir: false, retomada: 'Nenhuma gravação para concluir.', pendencia: e.pendencia || null };
    if (e.pendencia) d.retomada = e.pendencia.feito + ' gravado. ' + e.pendencia.falta + ' ainda não.';
    switch (e.fase) {
      case 'PROCURANDO': d.titulo = 'Procurando a central'; d.linha = 'O app procura sozinho. Ligue o cabo e a ignição.'; d.botao = 'Procurando'; break;
      case 'PERMISSAO': d.titulo = 'Permita o acesso'; d.linha = 'Toque em Permitir na janela do Android.'; d.botao = 'Pedir permissão'; d.habilitado = true; break;
      case 'CONECTANDO': d.titulo = 'Conectando'; d.linha = 'Cabo encontrado. Falando com a central.'; d.botao = 'Conectando'; break;
      case 'LENDO': d.titulo = 'Lendo os mapas'; d.linha = 'A central respondeu. Lendo os dados.'; d.botao = 'Lendo'; break;
      case 'PRONTO': d.titulo = 'Central conectada'; d.linha = 'Tudo certo. Pode usar o app.'; d.botao = 'Ver o AutoCal'; d.habilitado = true; d.concluir = !!e.pendencia; break;
      case 'RETOMANDO': d.titulo = 'Retomando a conexão'; d.linha = 'Cabo solto. Tentativa ' + (e.tentativas || 0) + '. Seu trabalho está guardado.'; d.botao = 'Reconectando'; break;
      default: {
        const c = CAUSAS[e.causa] || CAUSAS.CENTRAL_NAO_RESPONDE;
        d.titulo = 'Não deu para conectar'; d.linha = c[0]; d.causa = c[0]; d.acao = c[1]; d.botao = 'Tentar de novo'; d.habilitado = true;
      }
    }
    return d;
  }

  function mount(host, handlers) {
    const h = handlers || {};
    const doc = host.ownerDocument;
    const make = (tag, text, flux) => {
      const el = doc.createElement(tag);
      if (text != null) el.textContent = text;
      if (flux) el.setAttribute('data-fluxo', flux);
      host.appendChild(el);
      return el;
    };
    const titulo = make('h2', '', 'titulo');
    const linha = make('p', '', 'linha');
    const causa = make('p', '', 'causa');
    const passos = PASSOS.map((p, i) => make('p', p, 'passo-' + i));
    const retomada = make('p', '', 'retomada');
    const concluir = make('button', 'Concluir', 'concluir');
    const deixar = make('button', 'Deixar assim', 'deixar');
    const detalhes = make('button', 'Ver detalhes', 'detalhes');
    const detalhe = make('p', '', 'detalhe');
    const como = make('button', 'Ver como funciona', 'como-funciona');
    const demo = make('button', 'Ver demonstração', 'demonstracao');
    const principal = make('button', '', 'principal');
    let atual = describe(null);
    let aberto = false;
    const fire = (fn, ok) => () => { if (ok() && typeof fn === 'function') return fn(); return undefined; };
    principal.addEventListener('click', fire(() => (atual.fase === 'PRONTO' ? h.onOpen : h.onRetry)(), () => atual.habilitado));
    concluir.addEventListener('click', fire(h.onFinish, () => atual.concluir));
    deixar.addEventListener('click', fire(h.onKeep, () => !!atual.pendencia));
    como.addEventListener('click', fire(h.onHow, () => true));
    demo.addEventListener('click', fire(h.onDemo, () => true));
    detalhes.addEventListener('click', () => { aberto = !aberto; draw(); if (typeof h.onDetails === 'function') h.onDetails(aberto); });
    function draw() {
      titulo.textContent = atual.titulo;
      linha.textContent = atual.linha;
      causa.textContent = atual.causa ? atual.causa + ' ' + atual.acao : 'Sem problema agora.';
      passos.forEach((el, i) => { el.textContent = (i + 1) + '. ' + PASSOS[i] + ' · ' + (i < atual.etapa ? 'Concluído' : i === atual.etapa && atual.fase !== 'PRONTO' ? 'Aguardando' : 'A seguir'); });
      retomada.textContent = atual.retomada;
      detalhe.textContent = aberto ? 'Conexão por etapas. O app relê os mapas ao voltar; gravação pendente só com o seu toque.' : '';
      detalhes.textContent = aberto ? 'Voltar' : 'Ver detalhes';
      principal.textContent = atual.botao;
      principal.setAttribute('aria-disabled', atual.habilitado ? 'false' : 'true');
      principal.disabled = !atual.habilitado;
      concluir.setAttribute('aria-disabled', atual.concluir ? 'false' : 'true'); concluir.disabled = !atual.concluir;
      deixar.setAttribute('aria-disabled', atual.pendencia ? 'false' : 'true'); deixar.disabled = !atual.pendencia;
    }
    draw();
    return { set(estado) { atual = describe(estado); draw(); }, el: principal };
  }

  return { FASES, describe, parse, mount };
});
