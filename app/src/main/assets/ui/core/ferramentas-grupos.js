(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Ferramentas por GRUPO (dono, 2026-10-20): cada "Ver ›" / "Mudar valores" abre UMA página com tudo do assunto, no modelo da tela
  // "Valores do anti-apagão" (cartões lado a lado; − / + visíveis nos editáveis). Este módulo só diz QUAIS cartões de leitura cada grupo
  // mostra e com que texto; os editáveis (anti-apagão e volta para gasolina) são desenhados pelo AdvancedPanel, que já fala com a ponte.
  // Nada aqui grava. Só entra o que o app LÊ hoje (getAdvancedParams); desconhecido = "—".
  const DASH = '—';

  /** Página de cada cartão da tela inicial de Ferramentas. */
  const HOME_TO_GROUP = { valores: 'valores', anti: 'valores', corte: 'troca', troca: 'troca', injetores: 'troca', lenta: 'lenta', volta: 'volta', outras: 'outras' };
  const TITLES = {
    valores: 'Anti-apagão',
    volta: 'Volta para gasolina',
    troca: 'Troca entre gasolina e gás',
    lenta: 'Marcha lenta',
    outras: 'Outras configurações da central',
  };

  const num = v => (v === null || v === undefined ? DASH : String(v).replace(/\B(?=(\d{3})+(?!\d))/g, '.'));
  const okValues = (e, n) => (e && e.state === 'OK' && Array.isArray(e.values) && e.values.length === n && e.values.every(Number.isInteger) ? e.values : null);
  const stateOf = on => (on === null ? DASH : on ? 'Ligado' : 'Desligado');

  // Copy em 3 linhas por cartão (dono, 2026-10-20: "fazer o leigo entender"; "o que não for comprovado, nem coloca"):
  // [flag, palavra, máscara, rótulo, O QUE É, EFEITO]. Só entra item com efeito de fonte: manual do original
  // (docs/progbase/manual/manual-2.16.4C.txt, "segundo o manual") ou a tela/código do ProgBase. Nunca inventar.
  const BIT_RANGE = 'Ligado ou desligado.';
  const TROCA_BITS = [
    ['FLAG_CONF3', 0, 0x0010, 'Sobreposição da troca', 'Gasolina e gás juntos por um instante na troca.', 'Segundo o manual: ligado, evita tranco logo depois da troca. Quase nunca é preciso.'],
    ['FLAG_CONF1', 1, 0x0001, 'Troca pela temperatura da água', 'Só passa ao gás depois que o motor esquenta.', 'Segundo o manual: ligado, a troca espera a água chegar à temperatura marcada.'],
    ['FLAG_CONF1', 1, 0x0002, 'Troca pela temperatura do gás', 'Usa a temperatura do gás para decidir a troca.', 'Segundo o manual: serve quando não há sensor da água ligado.'],
    ['FLAG_CONF2', 0, 0x0002, 'Partida a gás com motor quente', 'Com o motor quente, já liga direto no gás.', 'Segundo o manual: ligado, pula a gasolina na partida se a água estiver quente.'],
    ['FLAG_CONF1', 0, 0x0080, 'Válvulas do gás abrem antes', 'Abre as válvulas do gás uns 5 segundos antes da troca.', 'Segundo o manual: ligado, evita falha logo após a troca; a bomba de gasolina não desliga.'],
  ];
  // Bits sem efeito comprovado: não aparecem em lugar nenhum (nem em "Outras"), só ficam aqui para não reaparecerem.
  const SEM_EFEITO = [
    'FLAG_CONF1:1:4096', 'FLAG_CONF3:0:32768', 'FLAG_CONF2:0:1', 'FLAG_CONF1:1:16', 'FLAG_CONF2:0:64',
    'FLAG_CONF2:1:512', 'FLAG_CONF2:1:2048', 'FLAG_CONF2:1:1024', 'FLAG_CONF1:1:8192',
  ];
  // "Outras": o nome do original NÃO some; é traduzido e explicado. Chave = controle da tela do ProgBase (advanced-flags-data.js).
  const OUTRAS_TEXT = {
    CheckValvetronik: ['Valvetronic é um tipo de motor (BMW) que some o sinal de giro às vezes.', 'Segundo o manual: ligado, o sistema segue funcionando sem o sinal de giro.'],
    CheckDisabilitaCommutatore: ['Comutador é o botão gás/gasolina do painel. Aqui ele fica sem efeito.', 'Segundo o manual: ligado, roda só no gás, menos na partida fria, em erro ou sem gás.'],
    CheckSpegniBuzzer: ['Buzzer é o bip de aviso da central.', 'Segundo o manual: ligado, o bip toca só 5 segundos ao voltar para a gasolina.'],
    CheckStartAndStop: ['Start & Stop: o carro desliga sozinho no sinal e religa.', 'Segundo o manual: ligado, religa já no gás depois da parada.'],
  };
  // Bit da marcha lenta (core/idle-mode.js) e o anti-apagão (cartão próprio) também ficam fora de "Outras".
  const USED = new Set(TROCA_BITS.map(b => `${b[0]}:${b[1]}:${b[2]}`)
    .concat(SEM_EFEITO, ['FLAG_CONF1:1:256', 'FLAG_CONF3:0:32']));

  function bitCard(params, [flag, word, mask, label, hint, effect]) {
    const A = ns.AdvancedFlags;
    const w = A ? A.wordsOf((params || {})[flag]) : okValues((params || {})[flag], 2);
    const on = w ? (w[word] & mask) !== 0 : null;
    return { id: `${flag}-${word}-${mask.toString(16)}`, kind: 'read', label, value: stateOf(on), unit: '', hint, effect, range: BIT_RANGE, on };
  }

  function readCards(group, params) {
    const p = params || {};
    const bits = list => list.map(b => bitCard(p, b));
    if (group === 'troca') {
      const giri = okValues(p.GIRI_MIN_CAMBIO, 1);
      return [
        { id: 'giri-cambio', kind: 'read', label: 'Giros do motor para passar ao gás', value: giri ? num(giri[0] & ~1) : DASH, unit: 'rpm', hint: 'Abaixo destes giros o carro fica na gasolina.', effect: 'Segundo o manual: se aumentar, passa ao gás mais tarde. Se diminuir, passa mais cedo.', range: 'De 550 a 3.000. Fábrica: 1.600.', on: null },
        ...bits(TROCA_BITS),
      ];
    }
    if (group === 'lenta') {
      const idle = ns.IdleMode ? ns.IdleMode.view(p) : { stateText: DASH, suggestionText: '' };
      const out = [];
      if (idle.suggestionText) out.push({ id: 'lenta-sugestao', kind: 'read', label: 'Sugestão', value: 'Segurar com gasolina na parada', unit: '', hint: 'O app só sugere. Nada é gravado sozinho.', effect: 'O fabricante indica isso para motor que apaga ao voltar à marcha lenta.', range: 'Só sugestão.', on: null, small: true });
      return out;
    }
    if (group === 'outras') {
      const data = ns.AdvancedFlagsData || {};
      const out = [];
      Object.keys(data).forEach(flag => (data[flag].bits || []).forEach(b => {
        if (b.kind !== 'caixa' || USED.has(`${flag}:${b.word}:${b.mask}`)) return;
        const t = OUTRAS_TEXT[b.control];
        if (!t) return; // sem efeito comprovado: não aparece
        out.push(bitCard(p, [flag, b.word, b.mask, b.label, t[0], t[1]]));
      }));
      return out;
    }
    return [];
  }

  ns.FerramentasGrupos = { OUTRAS_TEXT, HOME_TO_GROUP, TITLES, readCards, groupOf: id => HOME_TO_GROUP[id] || null };
})(typeof window !== 'undefined' ? window : globalThis);
