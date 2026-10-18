(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  // Marcha lenta (SOMENTE LEITURA + sugestão). Escrita: PENDENTE (ver PENDENTE.md). Nada aqui grava na ECU.
  // OBS-COD: RadioGasClick 0x4BFAD8 (execução emulada): Gás = rpm 0 e bit 0; Volta = 1100 e bit 0; Gasolina = 1100 e bit 1.
  // O bit é FLAG_CONF1 palavra 1 máscara 0x0100 quando [obj+0x80D]=0 (ECU do dono, INFERIDO pelo log); 0x0400 só com 0x80D!=0.
  const MASK = 0x0100;
  const MODES = {
    gas: { name: 'Só gás', help: 'Na parada o motor roda só a gás.' },
    volta: { name: 'Segurar com gasolina na parada', help: 'Ao voltar para a marcha lenta entra um pouco de gasolina e depois volta ao gás.' },
    gasolina: { name: 'Sempre gasolina na parada', help: 'Em marcha lenta o motor roda sempre a gasolina.' },
  };
  function words(e) {
    return e && e.state === 'OK' && Array.isArray(e.values) && e.values.length === 2 && e.values.every(v => Number.isInteger(v) && v >= 0 && v <= 0xFFFF) ? e.values : null;
  }
  // Mesma regra da tela original (TabBenzinaShow). Combinação bit=1 com rpm 0 é incomum: devolve 'incomum'.
  function decode(params) {
    const f = words((params || {}).FLAG_CONF1), g = words((params || {}).GIRI_PER_BENZINA);
    if (!f || !g) return null;
    const bit = (f[1] & MASK) !== 0, rpm = g[0];
    if (!bit && rpm === 0) return 'gas';
    if (bit && rpm > 0) return 'gasolina';
    return bit ? 'incomum' : 'volta';
  }
  // Só sugere, nunca aplica. Manual Landi p.28-29 e p.31: para apagar ao voltar à lenta, preferir Gás–Gasolina.
  function suggestion(mode) { return mode === 'gas' ? 'volta' : null; }
  function view(params) {
    const mode = decode(params);
    const s = suggestion(mode);
    return {
      mode, stateText: mode === null ? '—' : mode === 'incomum' ? 'Combinação incomum' : MODES[mode].name,
      options: Object.keys(MODES).map(k => ({ id: k, label: MODES[k].name, help: MODES[k].help, current: k === mode, suggested: k === s })),
      suggestionText: s ? `Sugestão: “${MODES[s].name}”. É o que o fabricante indica para motor que apaga ao voltar à marcha lenta. Nada é gravado sozinho.` : '',
      writeAvailable: false,
    };
  }
  ns.IdleMode = { MASK, MODES, decode, suggestion, view };
})(typeof window !== 'undefined' ? window : globalThis);
