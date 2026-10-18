(function (global) {
  'use strict';
  function validate(factors) {
    if (!Array.isArray(factors) || factors.length !== 30 ||
        !Array.from(factors).every(v => typeof v === 'number' && Number.isFinite(v) && v >= 0.8 && v <= 1.8)) {
      throw new Error('Leia os 30 pontos da curva entre 0,8 e 1,8 antes de suavizar.');
    }
  }
  function roughness(factors) {
    validate(factors);
    const secondDifferences = factors.slice(1, -1).map((v, i) => factors[i] - 2 * v + factors[i + 2]);
    return { secondDifferences, max: Math.max(...secondDifferences.map(Math.abs)),
      rms: Math.sqrt(secondDifferences.reduce((sum, v) => sum + v * v, 0) / 28) };
  }
  // Proposta geométrica, não calibração física. 0,025 é tolerância de curvatura
  // em fator K por nó (não tempo), ajustável; não é um limiar validado no carro.
  // Os primeiros cinco nós e o último são âncoras. Não inferir contagens de
  // evidência a partir dos 18 pontos de aquisição: são eixos diferentes.
  // Uma passagem Jacobi: cada nó usa somente vizinhos da curva de entrada.
  // Mais evidência => menos deslocamento. Entradas fora do limite são recusadas:
  // cortar âncoras ou uma curva suave violaria a preservação solicitada.
  function smooth(factors, opts = {}) {
    validate(factors);
    const threshold = opts.threshold === undefined ? 0.025 : opts.threshold;
    if (!Number.isFinite(threshold) || threshold < 0) throw new Error('Tolerância de suavização inválida.');
    const counts = opts.evidenceCounts;
    if (counts !== undefined && (!Array.isArray(counts) || counts.length !== 30 ||
        !Array.from(counts).every(n => Number.isSafeInteger(n) && n >= 0))) {
      throw new Error('Informe a quantidade de pontos de evidência para cada um dos 30 nós.');
    }
    const before = factors.slice();
    const roughnessBefore = roughness(before);
    let after = before.map((value, index) => {
      if (index < 5 || index === 29 || Math.abs(roughnessBefore.secondDifferences[index - 1]) <= threshold) return value;
      const weight = 1 / (1 + (counts ? counts[index] : 0));
      return Math.max(0.8, Math.min(1.8, value + weight * (before[index - 1] - 2 * value + before[index + 1]) / 4));
    });
    // Âncoras/pesos podem impedir melhora global: nesse caso não propor piora.
    if (roughness(after).rms >= roughnessBefore.rms) after = before.slice();
    return { before, after, roughnessBefore, roughnessAfter: roughness(after),
      changed: after.flatMap((value, index) => value === before[index] ? [] : [{ index, before: before[index], after: value }]) };
  }
  const api = { smooth, roughness };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  (global.OmegasUi = global.OmegasUi || {}).CurveSmoothing = api;
})(typeof window !== 'undefined' ? window : globalThis);
