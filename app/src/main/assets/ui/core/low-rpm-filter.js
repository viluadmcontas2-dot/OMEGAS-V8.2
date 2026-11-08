(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};
  // Filtro de ponto ruim (dono, 2026-10-20): contador que sobe com o motor abaixo de 1000 RPM (ou parado) é apagado.
  // Só observa e devolve alvos; quem apaga é a função de lote que já existe. RPM desconhecido nunca conta como baixo.
  const MIN_RPM = 1000;
  const FUELS = ['GAS', 'PETROL'];
  const SATURATED = 10;   // o contador da central trava em 10: acima disso "subir" não aparece, só o valor guardado muda
  const BRAKE_AFTER = 3;  // apagamentos da mesma banda no mesmo episódio de RPM baixo antes de parar (laço na marcha lenta)

  function create(options) {
    // minRpm pode ser número ou função (lê o ajuste atual do dono a cada passo); 0 desliga o filtro.
    const limit = () => {
      const raw = options && options.minRpm;
      const value = Number(typeof raw === 'function' ? raw() : raw === undefined ? MIN_RPM : raw);
      return Number.isFinite(value) && value >= 0 ? value : MIN_RPM;
    };
    // minSamples: leituras de RPM baixo SEGUIDAS para condenar (1 = qualquer leitura baixa; o app usa 3 para ignorar troca de marcha).
    const minSamples = Math.max(1, Math.floor(Number(options && options.minSamples) || 1));
    let lowest = null;
    let previous = null;
    let previousValues = null;
    let lastId;
    let lastEpoch = {};
    let streak = 0;       // leituras seguidas abaixo do limite, ainda em andamento
    let peakStreak = 0;   // maior sequência vista na janela atual
    let sawOk = false;    // houve RPM conhecido e >= limite na janela (encerra o episódio de RPM baixo)
    const strikes = {};   // apagamentos por banda no episódio atual
    let braked = [];      // bandas que o freio parou (aviso pendente)

    function observeRpm(rpm) {
      const value = Number(rpm);
      if (rpm === null || rpm === undefined || rpm === '' || !Number.isFinite(value) || value < 0) return;
      lowest = lowest === null ? value : Math.min(lowest, value);
      const min = limit();
      if (min > 0 && value < min) {
        streak += 1;
        peakStreak = Math.max(peakStreak, streak);
      } else {
        streak = 0;
        if (min > 0 && value >= min) sawOk = true;
      }
    }

    // Esquece a linha de base e a janela de RPM: aba fora de vista, USB caiu, leitura indisponível ou época nova.
    // Sem isso, tudo que cresceu enquanto ninguém olhava seria atribuído ao primeiro RPM baixo que aparecer.
    function reset() {
      previous = null;
      previousValues = null;
      lowest = null;
      streak = 0;
      peakStreak = 0;
      sawOk = false;
      Object.keys(strikes).forEach(k => delete strikes[k]);
      braked = [];
      lastId = undefined;
      lastEpoch = {};
    }

    // Aviso pendente do freio (uma vez por episódio): { braked: ['GAS:2', ...] } ou null.
    function takeNotice() {
      if (!braked.length) return null;
      const notice = { braked };
      braked = [];
      return notice;
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
      const low = min > 0 && lowest !== null && lowest < min && (minSamples <= 1 || peakStreak >= minSamples);
      const ok = sawOk;
      lowest = null;
      peakStreak = streak; // uma sequência que atravessa duas leituras do contador continua valendo
      sawOk = false;
      if (!low && ok) { // o motor passou do limite: o episódio de RPM baixo acabou, a banda volta a valer
        Object.keys(strikes).forEach(k => delete strikes[k]);
        braked = [];
      }
      const values = info.values || null;
      const current = {};
      FUELS.forEach(fuel => { current[fuel] = Array.isArray(counters && counters[fuel]) ? counters[fuel].map(known) : []; });
      const epoch = info.epoch || {};
      FUELS.forEach(fuel => {
        const sameEpoch = (epoch[fuel] === undefined ? '' : epoch[fuel]) === (lastEpoch[fuel] === undefined ? '' : lastEpoch[fuel]);
        if (previous && low && sameEpoch) {
          current[fuel].forEach((value, index) => {
            const before = previous[fuel][index];
            let rose = value !== null && before !== null && before !== undefined && value > before;
            if (!rose && value !== null && before !== null && before !== undefined && value === before && value >= SATURATED && values && previousValues) {
              // Contador travado em 10: se o ponto guardado (injeção|MAP) mudou, a central mediu de novo.
              const now = values[fuel] && values[fuel][index];
              const was = previousValues[fuel] && previousValues[fuel][index];
              rose = now !== null && now !== undefined && was !== null && was !== undefined && now !== was;
            }
            if (!rose) return;
            const id = fuel + ':' + index;
            strikes[id] = (strikes[id] || 0) + 1;
            if (strikes[id] > BRAKE_AFTER) { if (!braked.includes(id) && strikes[id] === BRAKE_AFTER + 1) braked.push(id); return; }
            targets.push({ fuel, index });
          });
        }
      });
      lastEpoch = Object.assign({}, epoch);
      previous = current;
      previousValues = values;
      return targets;
    }

    return { observeRpm, step, reset, takeNotice };
  }

  ns.LowRpmFilter = { create, MIN_RPM };
})(typeof globalThis !== 'undefined' ? globalThis : this);
