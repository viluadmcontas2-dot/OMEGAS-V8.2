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

  // Copy em 3 linhas por cartão (dono, 2026-10-20: "fazer o leigo entender; o que não está provado, botar lá"):
  // [flag, palavra, máscara, rótulo, O QUE É, EFEITO (null = não comprovado)]. Efeito só com fonte: manual do original
  // (docs/progbase/manual/manual-2.16.4C.txt, "segundo o manual") ou a tela/código do ProgBase. Nunca inventar.
  const UNPROVEN = 'Ainda não sei exatamente o efeito. Não mude sem necessidade.';
  const BIT_RANGE = 'Ligado ou desligado. Fábrica: não sei.';
  const TROCA_BITS = [
    ['FLAG_CONF1', 1, 0x1000, 'Um cilindro por vez', 'Passa da gasolina ao gás um cilindro de cada vez.', null],
    ['FLAG_CONF3', 0, 0x0010, 'Sobreposição da troca', 'Gasolina e gás juntos por um instante na troca.', 'Segundo o manual: ligado, evita tranco logo depois da troca. Quase nunca é preciso.'],
    ['FLAG_CONF3', 0, 0x8000, 'Troca automática', 'A central decide sozinha a hora de passar ao gás.', null],
    ['FLAG_CONF1', 1, 0x0001, 'Troca pela temperatura da água', 'Só passa ao gás depois que o motor esquenta.', 'Segundo o manual: ligado, a troca espera a água chegar à temperatura marcada.'],
    ['FLAG_CONF1', 1, 0x0002, 'Troca pela temperatura do gás', 'Usa a temperatura do gás para decidir a troca.', 'Segundo o manual: serve quando não há sensor da água ligado.'],
    ['FLAG_CONF2', 0, 0x0002, 'Partida a gás com motor quente', 'Com o motor quente, já liga direto no gás.', 'Segundo o manual: ligado, pula a gasolina na partida se a água estiver quente.'],
    ['FLAG_CONF1', 0, 0x0080, 'Válvulas do gás abrem antes', 'Abre as válvulas do gás uns 5 segundos antes da troca.', 'Segundo o manual: ligado, evita falha logo após a troca; a bomba de gasolina não desliga.'],
  ];
  const CORTE_BITS = [
    ['FLAG_CONF2', 0, 0x0001, 'Corte das injeções extras', 'Corta as injeções extras de gasolina.', null],
  ];
  const INJ_BITS = [
    ['FLAG_CONF1', 1, 0x0010, 'Controle dos injetores', 'A central vigia os bicos de gás.', null],
    ['FLAG_CONF2', 0, 0x0040, 'Aquecimento dos injetores', 'Esquenta os bicos de gás antes de usar.', null],
    ['FLAG_CONF2', 1, 0x0200, 'Aquecer só depois do repouso', 'Esquenta os bicos só quando a central acorda.', null],
    ['FLAG_CONF2', 1, 0x0800, 'Aquecimento em paralelo', 'Esquenta os bicos sem esperar a troca.', null],
    ['FLAG_CONF2', 1, 0x0400, 'Corrente maior na troca', 'Mais força elétrica nos bicos ao trocar para o gás.', null],
  ];
  const VOLTA_BITS = [
    ['FLAG_CONF1', 1, 0x2000, 'Gasolina em rotação alta', 'Em giro alto, a gasolina entra sozinha para ajudar.', null],
  ];
  // "Outras": o nome do original NÃO some; é traduzido e explicado. Chave = controle da tela do ProgBase (advanced-flags-data.js).
  const OUTRAS_TEXT = {
    CheckSequenzaInj: ['Diz à central a ordem de injeção, em vez de ela descobrir.', null],
    CheckTempMotore: ['Lê a temperatura pelo sensor do próprio carro.', null],
    CheckAdattativita: ['OBD é a tomada de diagnóstico do carro. Aqui a central aprende com ela.', null],
    CheckValvetronik: ['Valvetronic é um tipo de motor (BMW) que some o sinal de giro às vezes.', 'Segundo o manual: ligado, o sistema segue funcionando sem o sinal de giro.'],
    CheckAzzeraErroriObd: ['OBD é a tomada de diagnóstico do carro. Apaga os avisos de erro dela.', null],
    CheckDisabilitaCommutatore: ['Comutador é o botão gás/gasolina do painel. Aqui ele fica sem efeito.', 'Segundo o manual: ligado, roda só no gás, menos na partida fria, em erro ou sem gás.'],
    CheckAzzeraBlackListObd: ['Apaga só alguns avisos de erro do OBD (tomada de diagnóstico).', null],
    CheckSpegniBuzzer: ['Buzzer é o bip de aviso da central.', 'Segundo o manual: ligado, o bip toca só 5 segundos ao voltar para a gasolina.'],
    CheckSplitFuel: ['Divide o combustível: parte gasolina, parte gás.', null],
    CheckStartAndStop: ['Start & Stop: o carro desliga sozinho no sinal e religa.', 'Segundo o manual: ligado, religa já no gás depois da parada.'],
    CheckSwitchOnDiagnosis: ['Liga o diagnóstico da central.', null],
    CheckContributi: ['Mapa que soma um pouco de gasolina ao gás.', null],
    CheckFlexFuel: ['Flex: carro que aceita álcool e gasolina.', null],
    CheckInjAcquisition: ['Ordem de injeção informada à mão, usada na aquisição.', null],
    CheckFlashLube: ['Vigia o nível do lubrificante das válvulas (sistema VP).', null],
    CheckTaglioPompaBenzina: ['Desliga a bomba de gasolina enquanto roda no gás.', null],
    CheckPetrolObdConnection: ['Mantém a ligação com o OBD (tomada de diagnóstico) na gasolina.', null],
    CheckDeleteErrorMotoreSpento: ['Apaga os erros do OBD com o motor desligado.', null],
    CheckFlashLubeSuTempAcqua: ['Vigia o lubrificante pela temperatura da água (sistema VP).', null],
    CheckOutDiagInfoSuKLine: ['Linha K é um fio de diagnóstico. Avisa erro por ela.', null],
    CheckUsaCanPerObd: ['CAN é a rede de fios do carro. Usa ela para o OBD.', null],
    CheckAttivaPompaInRiserva: ['Religa a bomba de gasolina quando o gás entra na reserva.', null],
    CheckRiconoscimentoPieno: ['Percebe quando o cilindro de gás foi enchido.', null],
    CheckGiriRuotaFonica: ['Lê os giros pela roda dentada do motor (roda fônica).', null],
    CheckEnableLandiConnect: ['Landi Connect: ligação da central com o aplicativo da Landi.', null],
    chkRitardoPassaggioInMinuti: ['Conta a espera para passar ao gás em minutos.', null],
    CheckBoxEnableExtLedKLine: ['Luz de aviso externa ligada pelo fio de diagnóstico (linha K).', null],
    CheckBoxEnDiagDtcPowertrain: ['DTC são códigos de erro do motor e câmbio. Liga o diagnóstico deles.', null],
  };
  // Bit da marcha lenta (core/idle-mode.js) e o anti-apagão (cartão próprio) também ficam fora de "Outras".
  const USED = new Set([...TROCA_BITS, ...CORTE_BITS, ...INJ_BITS, ...VOLTA_BITS].map(b => `${b[0]}:${b[1]}:${b[2]}`)
    .concat(['FLAG_CONF1:1:256', 'FLAG_CONF3:0:32']));

  function bitCard(params, [flag, word, mask, label, hint, effect]) {
    const A = ns.AdvancedFlags;
    const w = A ? A.wordsOf((params || {})[flag]) : okValues((params || {})[flag], 2);
    const on = w ? (w[word] & mask) !== 0 : null;
    return { id: `${flag}-${word}-${mask.toString(16)}`, kind: 'read', label, value: stateOf(on), unit: '', hint, effect: effect || null, range: BIT_RANGE, on };
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
        { id: 'giri-cambio', kind: 'read', label: 'Giros do motor para passar ao gás', value: giri ? num(giri[0] & ~1) : DASH, unit: 'rpm', hint: 'Abaixo destes giros o carro fica na gasolina.', effect: 'Segundo o manual: se aumentar, passa ao gás mais tarde. Se diminuir, passa mais cedo.', range: 'De 550 a 3.000. Fábrica: 1.600.', on: null },
        { id: 'ritardo-cambio', kind: 'read', label: 'Espera para passar ao gás', value: rit ? num(rit[0]) : DASH, unit: '', hint: 'Tempo depois da partida antes de passar ao gás.', effect: null, range: 'Unidade ainda não confirmada.', on: null },
        ...bits(TROCA_BITS.slice(1)),
        { id: 'corte-inj', kind: 'read', label: 'Gasolina ao sair do corte', value: cut ? num(cut[0]) : DASH, unit: cut ? 'injeções' : '', hint: 'Injeções de gasolina ao voltar a acelerar, depois de tirar o pé.', effect: null, range: 'Papel deduzido pelo nome.', on: null },
        ...bits(CORTE_BITS),
        ...bits(INJ_BITS),
      ];
    }
    if (group === 'lenta') {
      const idle = ns.IdleMode ? ns.IdleMode.view(p) : { stateText: DASH, suggestionText: '' };
      const g = okValues(p.GIRI_PER_BENZINA, 2);
      const out = [
        { id: 'lenta-modo', kind: 'read', label: 'Modo na marcha lenta', value: idle.stateText, unit: '', hint: 'Com que combustível o motor fica parado, sem acelerar.', effect: null, range: 'A gás ou com gasolina.', on: null },
        { id: 'lenta-giro', kind: 'read', label: 'Giros que valem como marcha lenta', value: g ? (g[0] === 0 ? 'Não usada' : num(g[0])) : DASH, unit: g && g[0] !== 0 ? 'rpm' : '', hint: 'Abaixo destes giros a central considera o motor parado.', effect: null, range: 'Zero = não usada.', on: null },
      ];
      if (idle.suggestionText) out.push({ id: 'lenta-sugestao', kind: 'read', label: 'Sugestão', value: 'Segurar com gasolina na parada', unit: '', hint: 'O app só sugere. Nada é gravado sozinho.', effect: 'O fabricante indica isso para motor que apaga ao voltar à marcha lenta.', range: 'Só sugestão.', on: null, small: true });
      return out;
    }
    if (group === 'volta') return bits(VOLTA_BITS);
    if (group === 'outras') {
      const data = ns.AdvancedFlagsData || {};
      const out = [];
      Object.keys(data).forEach(flag => (data[flag].bits || []).forEach(b => {
        if (b.kind !== 'caixa' || USED.has(`${flag}:${b.word}:${b.mask}`)) return;
        // O nome do original fica no rótulo; a linha 1 traduz e explica; sem fonte, o efeito é "não comprovado".
        const t = OUTRAS_TEXT[b.control] || ['Configuração da central com este nome no original.', null];
        out.push(bitCard(p, [flag, b.word, b.mask, b.label, t[0], t[1]]));
      }));
      return out;
    }
    return [];
  }

  ns.FerramentasGrupos = { UNPROVEN, OUTRAS_TEXT, HOME_TO_GROUP, TITLES, readCards, groupOf: id => HOME_TO_GROUP[id] || null };
})(typeof window !== 'undefined' ? window : globalThis);
