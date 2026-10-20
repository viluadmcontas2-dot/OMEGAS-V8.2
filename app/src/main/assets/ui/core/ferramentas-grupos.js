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

  // Bits já mostrados num grupo próprio (não se repetem em "Outras").
  const TROCA_BITS = [
    ['FLAG_CONF1', 1, 0x1000, 'Um cilindro por vez', 'Troca para o gás um cilindro de cada vez.'],
    ['FLAG_CONF3', 0, 0x0010, 'Sobreposição da troca', 'Gasolina e gás juntos por um instante na troca.'],
    ['FLAG_CONF3', 0, 0x8000, 'Troca automática', 'A central passa sozinha para o gás.'],
    ['FLAG_CONF1', 1, 0x0001, 'Troca pela temperatura da água', 'Só passa ao gás com o motor aquecido.'],
    ['FLAG_CONF1', 1, 0x0002, 'Troca pela temperatura do gás', 'Espera o gás aquecer para trocar.'],
    ['FLAG_CONF2', 0, 0x0002, 'Partida a gás com motor quente', 'Com o motor quente, já parte no gás.'],
    ['FLAG_CONF1', 0, 0x0080, 'Válvulas do gás abrem antes', 'Abre as válvulas um pouco antes da troca.'],
  ];
  const CORTE_BITS = [
    ['FLAG_CONF2', 0, 0x0001, 'Corte das injeções extras', 'Corta as injeções extras de gasolina.'],
  ];
  const INJ_BITS = [
    ['FLAG_CONF1', 1, 0x0010, 'Controle dos injetores', 'Aciona os injetores na troca para o gás.'],
    ['FLAG_CONF2', 0, 0x0040, 'Aquecimento dos injetores', 'Aquece os injetores do gás.'],
    ['FLAG_CONF2', 1, 0x0200, 'Aquecer só depois do repouso', 'Aquece os injetores só ao acordar a central.'],
    ['FLAG_CONF2', 1, 0x0800, 'Aquecimento em paralelo', 'Aquece os injetores sem esperar a troca.'],
    ['FLAG_CONF2', 1, 0x0400, 'Corrente maior na troca', 'Mais força nos injetores ao trocar para o gás.'],
  ];
  const VOLTA_BITS = [
    ['FLAG_CONF1', 1, 0x2000, 'Gasolina em rotação alta', 'Em rotação alta a gasolina entra sozinha.'],
  ];
  // Bit da marcha lenta (core/idle-mode.js) e o anti-apagão (cartão próprio) também ficam fora de "Outras".
  const USED = new Set([...TROCA_BITS, ...CORTE_BITS, ...INJ_BITS, ...VOLTA_BITS].map(b => `${b[0]}:${b[1]}:${b[2]}`)
    .concat(['FLAG_CONF1:1:256', 'FLAG_CONF3:0:32']));

  function bitCard(params, [flag, word, mask, label, hint]) {
    const A = ns.AdvancedFlags;
    const w = A ? A.wordsOf((params || {})[flag]) : okValues((params || {})[flag], 2);
    const on = w ? (w[word] & mask) !== 0 : null;
    return { id: `${flag}-${word}-${mask.toString(16)}`, kind: 'read', label, value: stateOf(on), unit: '', hint, on };
  }

  function readCards(group, params) {
    const p = params || {};
    const bits = list => list.map(b => bitCard(p, b));
    if (group === 'troca') {
      const giri = okValues(p.GIRI_MIN_CAMBIO, 1);
      const rit = okValues(p.RITARDO_CAMBIO, 1);
      const cut = okValues(p.NUMERO_INJ_BENZINA_CUTOFF, 1);
      return [
        ...bits(TROCA_BITS.slice(0, 1)),
        { id: 'giri-cambio', kind: 'read', label: 'Giros do motor para passar ao gás', value: giri ? num(giri[0] & ~1) : DASH, unit: 'rpm', hint: 'Abaixo disso continua na gasolina.', on: null },
        { id: 'ritardo-cambio', kind: 'read', label: 'Espera para passar ao gás', value: rit ? num(rit[0]) : DASH, unit: '', hint: 'Número da central; unidade ainda não confirmada.', on: null },
        ...bits(TROCA_BITS.slice(1)),
        { id: 'corte-inj', kind: 'read', label: 'Gasolina ao sair do corte', value: cut ? num(cut[0]) : DASH, unit: cut ? 'injeções' : '', hint: 'Injeções de gasolina ao voltar a acelerar.', on: null },
        ...bits(CORTE_BITS),
        ...bits(INJ_BITS),
      ];
    }
    if (group === 'lenta') {
      const idle = ns.IdleMode ? ns.IdleMode.view(p) : { stateText: DASH, suggestionText: '' };
      const g = okValues(p.GIRI_PER_BENZINA, 2);
      const out = [
        { id: 'lenta-modo', kind: 'read', label: 'Modo na marcha lenta', value: idle.stateText, unit: '', hint: 'Como o motor fica parado e sem acelerar.', on: null },
        { id: 'lenta-giro', kind: 'read', label: 'Giros que valem como marcha lenta', value: g ? (g[0] === 0 ? 'Não usada' : num(g[0])) : DASH, unit: g && g[0] !== 0 ? 'rpm' : '', hint: 'Abaixo disso a central considera parado.', on: null },
      ];
      if (idle.suggestionText) out.push({ id: 'lenta-sugestao', kind: 'read', label: 'Sugestão', value: 'Segurar com gasolina na parada', unit: '', hint: 'Nada é gravado sozinho.', on: null, small: true });
      return out;
    }
    if (group === 'volta') return bits(VOLTA_BITS);
    if (group === 'outras') {
      const data = ns.AdvancedFlagsData || {};
      const out = [];
      Object.keys(data).forEach(flag => (data[flag].bits || []).forEach(b => {
        if (b.kind !== 'caixa' || USED.has(`${flag}:${b.word}:${b.mask}`)) return;
        // Nome do original entre parênteses (código em inglês) não ajuda o leigo: sai do rótulo; fica nos detalhes do ProgBase.
        out.push(bitCard(p, [flag, b.word, b.mask, b.label.replace(/\s*\((?:SwitchOnDiagnosis|split fuel)\)/, ''), 'Configuração lida da central.']));
      }));
      return out;
    }
    return [];
  }

  ns.FerramentasGrupos = { HOME_TO_GROUP, TITLES, readCards, groupOf: id => HOME_TO_GROUP[id] || null };
})(typeof window !== 'undefined' ? window : globalThis);
