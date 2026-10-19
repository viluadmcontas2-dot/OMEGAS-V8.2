(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Ferramentas, página "Motor e combustível": um cartão por ASSUNTO, em palavras do dono (2026-10-18). Só texto e estado; sem
  // bit, código ou hexadecimal na frente. O que é técnico (palavra, máscara, código) vai em `tech` ("Ver detalhes técnicos").
  // Desconhecido é "—", nunca desligado. Marcas: OBS-COD = bit da caixa no ProgBase (advanced-flags-data.js); INFERIDO = o modo da
  // marcha lenta (rotação 0 + bit 0x0400 apagado = "A gás", visto em RE 0x4BFB1E/0x43763A).
  const DASH = '—';

  function stateOf(on) { return on === null ? DASH : on ? 'Ligado' : 'Desligado'; }

  function build(params) {
    const A = ns.AdvancedFlags;
    const V = ns.AntiApagaoValores;
    const p = params || {};
    const bit = (flag, word, mask) => {
      const w = A.wordsOf(p[flag]);
      return w ? (w[word] & mask) !== 0 : null;
    };
    const row = (label, on) => ({ label, text: stateOf(on), on });
    const techBit = (flag, word, mask, label) => {
      const w = A.wordsOf(p[flag]);
      return `${label}: ${flag} · palavra ${word} · máscara 0x${mask.toString(16).toUpperCase().padStart(4, '0')} · ${w ? ((w[word] & mask) !== 0 ? 'bit ligado' : 'bit desligado') : 'não lido'}`;
    };
    const num = v => (v === null || v === undefined ? DASH : String(v).replace(/\B(?=(\d{3})+(?!\d))/g, '.'));
    const giri = A.simpleParams(p).find(x => x.name === 'GIRI_PER_BENZINA');
    const cutoffInj = A.simpleParams(p).find(x => x.name === 'NUMERO_INJ_BENZINA_CUTOFF');
    const giriRaw = (p.GIRI_PER_BENZINA && p.GIRI_PER_BENZINA.state === 'OK' && Array.isArray(p.GIRI_PER_BENZINA.values) && p.GIRI_PER_BENZINA.values.length === 2) ? p.GIRI_PER_BENZINA.values : null;
    const cutoffRaw = (p.NUMERO_INJ_BENZINA_CUTOFF && p.NUMERO_INJ_BENZINA_CUTOFF.state === 'OK' && Array.isArray(p.NUMERO_INJ_BENZINA_CUTOFF.values) && p.NUMERO_INJ_BENZINA_CUTOFF.values.length === 1) ? p.NUMERO_INJ_BENZINA_CUTOFF.values[0] : null;

    // Marcha lenta: modo lido por core/idle-mode.js (bit certo desta central: 0x0100; só leitura + sugestão).
    const idle = ns.IdleMode ? ns.IdleMode.view(p) : { stateText: DASH, suggestionText: '' };
    const idleMode = idle.stateText;

    const anti = bit('FLAG_CONF3', 0, A.ANTI_MASK);
    const values = V.valuesOf(p.ANTI_STALLO);
    const valuesState = values ? (values.every((v, i) => v === V.FACTORY[i]) ? 'De fábrica' : 'Mudados') : DASH;

    const cards = [
      { id: 'anti', kind: 'switch', title: 'Anti-apagão', line: 'Ao pisar na embreagem, ajuda a evitar apagões.', shortLine: 'Ajuda ao pisar na embreagem.', on: anti, stateText: stateOf(anti), rows: [], tech: [techBit('FLAG_CONF3', 0, A.ANTI_MASK, 'Anti-apagão')] },
      { id: 'valores', kind: 'values', title: 'Valores do anti-apagão', line: 'Limites de rotação para proteger a lenta.', shortLine: 'Limites de rotação e espera.', on: null, stateText: valuesState,
        rows: V.FIELDS.map(f => ({ label: f.label, text: values ? `${num(values[f.index])} ${f.unit}` : DASH, on: null })),
        tech: [`ANTI_STALLO · código ${V.ADDRESS} · 5 números de 16 bits: ${values ? values.join(' ') : 'não lido'}`] },
      { id: 'corte', kind: 'open', title: 'Saída do corte', viewTitle: 'Saída do corte (quando solta o acelerador)', line: 'Volta suave ao acelerar após soltar o pedal.', shortLine: 'Volta suave ao acelerar.',
        on: null, stateText: cutoffRaw === null ? DASH : `${num(cutoffRaw)} injeções`, stateFor: 'Gasolina ao retomar',
        note: 'Ao desacelerar, a central pode cortar a injeção. Este cartão mostra o número previsto de injeções de gasolina na retomada; não liga uma função de sobreposição.',
        rows: [{ label: 'Gasolina ao sair do corte', text: cutoffRaw === null ? DASH : `${num(cutoffRaw)} injeções`, on: null }, row('Corte das injeções extras', bit('FLAG_CONF2', 0, 0x0001))],
        tech: [techBit('FLAG_CONF2', 0, 0x0001, 'Corte das injeções extras'), `NUMERO_INJ_BENZINA_CUTOFF · código 106 · ${cutoffInj ? cutoffInj.cells.join(' ') : DASH} (papel inferido pelo nome)`] },
      { id: 'lenta', kind: 'open', title: 'Marcha lenta', viewTitle: 'Marcha lenta', line: 'Motor ligado sem acelerar, na parada ou manobra.', shortLine: 'Motor ligado sem acelerar.', on: null, stateText: idleMode, stateFor: 'Modo',
        rows: [{ label: 'Modo na marcha lenta', text: idleMode, on: null }, { label: 'Rotação que vale como marcha lenta', text: giriRaw ? (giriRaw[0] === 0 ? 'Não usada' : num(giriRaw[0])) : DASH, on: null }]
          .concat(idle.suggestionText ? [{ label: 'Sugestão', text: 'Segurar com gasolina na parada', on: null }] : []),
        note: idle.suggestionText || '',
        tech: [`GIRI_PER_BENZINA · código 161 · ${giri ? giri.cells.join(' ') : DASH}`, techBit('FLAG_CONF1', 1, 0x0100, 'Modo da marcha lenta')] },
      { id: 'troca', kind: 'open', title: 'Troca de gasolina para gás', viewTitle: 'Troca de gasolina para gás', line: 'Da gasolina ao GNV, um cilindro por vez.', shortLine: 'Gasolina → GNV, um a um.',
        on: bit('FLAG_CONF1', 1, 0x1000), stateText: stateOf(bit('FLAG_CONF1', 1, 0x1000)), stateFor: 'Um cilindro por vez',
        note: 'É a mudança normal de gasolina para GNV durante a comutação. Não significa que o gás acabou.',
        rows: [row('Um cilindro por vez', bit('FLAG_CONF1', 1, 0x1000)), row('Sobreposição da troca (progressões)', bit('FLAG_CONF3', 0, 0x0010)), row('Troca automática', bit('FLAG_CONF3', 0, 0x8000)), row('Troca pela temperatura da água', bit('FLAG_CONF1', 1, 0x0001)), row('Troca pela temperatura do gás', bit('FLAG_CONF1', 1, 0x0002))],
        tech: [techBit('FLAG_CONF1', 1, 0x1000, 'Um cilindro por vez'), techBit('FLAG_CONF3', 0, 0x0010, 'Sobreposição/progressões'), techBit('FLAG_CONF3', 0, 0x8000, 'Troca automática'), techBit('FLAG_CONF1', 1, 0x0001, 'Temperatura da água'), techBit('FLAG_CONF1', 1, 0x0002, 'Temperatura do gás')] },
      { id: 'injetores', kind: 'open', title: 'Injetores', viewTitle: 'Injetores', line: 'Aciona os injetores na troca para o gás.', shortLine: 'Injetores na troca para gás.',
        on: bit('FLAG_CONF1', 1, 0x0010), stateText: stateOf(bit('FLAG_CONF1', 1, 0x0010)), stateFor: 'Controle',
        note: 'Mostra o controle e o aquecimento dos injetores. Esta tela consulta, não altera.',
        rows: [row('Controle dos injetores', bit('FLAG_CONF1', 1, 0x0010)), row('Aquecimento dos injetores', bit('FLAG_CONF2', 0, 0x0040)), row('Corrente maior na troca para gás', bit('FLAG_CONF2', 1, 0x0400))],
        tech: [techBit('FLAG_CONF1', 1, 0x0010, 'Controle'), techBit('FLAG_CONF2', 0, 0x0040, 'Aquecimento'), techBit('FLAG_CONF2', 1, 0x0400, 'Corrente maior')] },
      { id: 'volta', kind: 'open', title: 'Volta para gasolina', viewTitle: 'Volta para gasolina', line: 'Volta à gasolina em rotações altas.', shortLine: 'Gasolina em rotações altas.',
        on: bit('FLAG_CONF1', 1, 0x2000), stateText: stateOf(bit('FLAG_CONF1', 1, 0x2000)), stateFor: 'Entrada automática',
        note: 'Quando ligada, a central pode usar gasolina em giro alto. Isto não é aviso de gás acabando.',
        rows: [row('Gasolina entra sozinha em rotação alta', bit('FLAG_CONF1', 1, 0x2000)), row('Corte da bomba de gasolina', bit('FLAG_CONF3', 0, 0x0004)), row('Reativar a bomba na reserva', bit('FLAG_CONF3', 0, 0x0800))],
        tech: [techBit('FLAG_CONF1', 1, 0x2000, 'Entrada automática'), techBit('FLAG_CONF3', 0, 0x0004, 'Corte da bomba'), techBit('FLAG_CONF3', 0, 0x0800, 'Reativar a bomba')] },
    ];
    return cards;
  }

  ns.CartoesMotor = { build, stateOf };
})(typeof window !== 'undefined' ? window : globalThis);
