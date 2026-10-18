(function (root) {
  'use strict';
  const ns = root.OmegasUi = root.OmegasUi || {};

  // Regra PROVISÓRIA, tirada do corpus real: nos 14 AutoMatches da própria ECU (leituras de 15–217 s antes, portanto o valor
  // verdadeiro é ≥) havia mediana de 9 bandas do GNV com ≥ 3 amostras. Erro < 3 % = a curva já bate com as médias maduras.
  const MATURE_COUNT = 3;
  const FULL_COUNT = 10;
  const BANDS_NEEDED = 9;
  const SMALL_ERROR = 0.03;
  const BAND_COUNT = 18;
  const DEFAULT_NOTE = '(aplica as médias)';
  const BASIS = 'Base: mediana de 9 bandas maduras (≥ 3 amostras) nos 14 AutoMatches que a própria ECU fez nas suas sessões. Regra provisória.';

  const pct = value => (value * 100).toFixed(1).replace('.', ',') + ' %';

  function advise(input) {
    const { counters, errorBefore, quotaReached, enabled } = input || {};
    const base = { provisional: true, basis: BASIS, facts: {}, shortNote: DEFAULT_NOTE };
    if (!quotaReached || enabled === 0) {
      return { ...base, verdict: 'UNAVAILABLE', reason: 'O AutoMatch manual só vale depois dos AutoMatches automáticos da ECU, com o aprendizado ligado.' };
    }
    if (!Array.isArray(counters) || counters.length < BAND_COUNT || !counters.every(Number.isFinite)) {
      return { ...base, verdict: 'UNKNOWN', reason: 'Sem leitura das bandas do GNV agora; não dá para dizer se vale.' };
    }
    const bandsMature = counters.filter(value => value >= MATURE_COUNT).length;
    const bandsFull = counters.filter(value => value >= FULL_COUNT).length;
    const facts = { bandsMature, bandsFull, bandsNeeded: BANDS_NEEDED };
    if (bandsMature < BANDS_NEEDED) {
      return { ...base, facts, verdict: 'WAIT', shortNote: `(ainda cedo: ${bandsMature} de ${BANDS_NEEDED} bandas)`, reason: `Poucas bandas maduras: ${bandsMature} de ${BANDS_NEEDED}. Continue rodando em GNV.` };
    }
    if (!Number.isFinite(errorBefore)) {
      return { ...base, facts, verdict: 'MATURE_NO_ESTIMATE', shortNote: `(${bandsMature} bandas maduras)`, reason: `${bandsMature} bandas maduras, mas sem estimativa de quanto a curva difere das médias. A decisão é sua.` };
    }
    const withError = { ...facts, errorBefore };
    if (errorBefore < SMALL_ERROR) {
      return { ...base, facts: withError, verdict: 'NOT_NEEDED', shortNote: `(curva já bate: ${pct(errorBefore)})`, reason: `${bandsMature} bandas maduras e a curva já bate com as médias (diferença estimada ${pct(errorBefore)}).` };
    }
    return { ...base, facts: withError, verdict: 'WORTH_IT', shortNote: `(vale: difere ${pct(errorBefore)})`, reason: `${bandsMature} bandas maduras (${bandsFull} cheias) e a curva difere ${pct(errorBefore)} das médias: o AutoMatch manual deve aproximar.` };
  }

  ns.ManualAutoMatchAdvice = { advise, BANDS_NEEDED, SMALL_ERROR };
})(typeof window !== 'undefined' ? window : globalThis);
