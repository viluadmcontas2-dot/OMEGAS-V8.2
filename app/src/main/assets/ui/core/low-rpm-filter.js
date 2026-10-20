(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  // Filtro de ponto ruim (dono, 2026-10-20): contador que sobe com o motor abaixo de 1000 RPM (ou parado) é apagado.
  // Só observa e devolve alvos; quem apaga é a função de lote que já existe. RPM desconhecido nunca conta como baixo.
  const MIN_RPM = 1000;
  const FUELS = ['GAS', 'PETROL'];

  function create(options) {
    const minRpm = (options && options.minRpm) || MIN_RPM;
    let lowest = null;
    let previous = null;

    function observeRpm(rpm) {
      const value = Number(rpm);
      if (rpm === null || rpm === undefined || !Number.isFinite(value) || value < 0) return;
      lowest = lowest === null ? value : Math.min(lowest, value);
    }

    // counters = { GAS: [18], PETROL: [18] }. Devolve [{ fuel, index }] das bandas que ganharam medição com RPM baixo.
    function step(counters) {
      const targets = [];
      const low = lowest !== null && lowest < minRpm;
      lowest = null;
      const current = {};
      FUELS.forEach(fuel => { current[fuel] = Array.isArray(counters && counters[fuel]) ? counters[fuel].map(Number) : []; });
      if (previous && low) {
        FUELS.forEach(fuel => {
          current[fuel].forEach((value, index) => {
            const before = previous[fuel][index];
            if (Number.isFinite(value) && Number.isFinite(before) && value > before) targets.push({ fuel, index });
          });
        });
      }
      previous = current;
      return targets;
    }

    return { observeRpm, step };
  }

  ns.LowRpmFilter = { create, MIN_RPM };
})(typeof globalThis !== 'undefined' ? globalThis : this);
