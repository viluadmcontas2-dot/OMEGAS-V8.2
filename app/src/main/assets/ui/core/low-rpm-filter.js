(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  // Filtro de ponto ruim (dono, 2026-10-20): contador que sobe com o motor abaixo de 1000 RPM (ou parado) é apagado.
  // Só observa e devolve alvos; quem apaga é a função de lote que já existe. RPM desconhecido nunca conta como baixo.
  const MIN_RPM = 1000;
  const FUELS = ['GAS', 'PETROL'];

  function create(options) {
    // minRpm pode ser número ou função (lê o ajuste atual do dono a cada passo); 0 desliga o filtro.
    const limit = () => {
      const raw = options && options.minRpm;
      const value = Number(typeof raw === 'function' ? raw() : raw === undefined ? MIN_RPM : raw);
      return Number.isFinite(value) && value >= 0 ? value : MIN_RPM;
    };
    let lowest = null;
    let previous = null;
    let lastId;
    let lastEpoch = {};

    function observeRpm(rpm) {
      const value = Number(rpm);
      if (rpm === null || rpm === undefined || rpm === '' || !Number.isFinite(value) || value < 0) return;
      lowest = lowest === null ? value : Math.min(lowest, value);
    }

    // Esquece a linha de base e a janela de RPM: aba fora de vista, USB caiu, leitura indisponível ou época nova.
    // Sem isso, tudo que cresceu enquanto ninguém olhava seria atribuído ao primeiro RPM baixo que aparecer.
    function reset() {
      previous = null;
      lowest = null;
      lastId = undefined;
      lastEpoch = {};
    }

    // Desconhecido (null/NaN) continua desconhecido: nunca vira 0 e nunca conta como subida.
    const known = value => (value === null || value === undefined || value === '' ? null : (Number.isFinite(Number(value)) ? Number(value) : null));

    // counters = { GAS: [18], PETROL: [18] } (null = desconhecido). meta (opcional) = { id, epoch: { GAS, PETROL } }:
    // id identifica a leitura (a mesma leitura repetida não gasta a janela de RPM); epoch muda quando a central
    // zera/reinicia o aprendizado ou a sessão USB muda (a linha de base daquele combustível recomeça).
    // Devolve [{ fuel, index }] das bandas que ganharam medição com RPM baixo.
    function step(counters, meta) {
      const info = meta || {};
      if (info.id !== undefined && info.id !== null && info.id === lastId) return [];
      lastId = info.id;
      const targets = [];
      const min = limit();
      const low = min > 0 && lowest !== null && lowest < min;
      lowest = null;
      const current = {};
      FUELS.forEach(fuel => { current[fuel] = Array.isArray(counters && counters[fuel]) ? counters[fuel].map(known) : []; });
      const epoch = info.epoch || {};
      FUELS.forEach(fuel => {
        const sameEpoch = (epoch[fuel] === undefined ? '' : epoch[fuel]) === (lastEpoch[fuel] === undefined ? '' : lastEpoch[fuel]);
        if (previous && low && sameEpoch) {
          current[fuel].forEach((value, index) => {
            const before = previous[fuel][index];
            if (value !== null && before !== null && before !== undefined && value > before) targets.push({ fuel, index });
          });
        }
      });
      lastEpoch = Object.assign({}, epoch);
      previous = current;
      return targets;
    }

    return { observeRpm, step, reset };
  }

  ns.LowRpmFilter = { create, MIN_RPM };
})(typeof globalThis !== 'undefined' ? globalThis : this);
