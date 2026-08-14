function stateFor(row, column) {
  const distance = Math.abs(row - 4) + Math.abs(column - 3);
  if (distance <= 1) return 'VALIDADO';
  if (distance <= 3) return 'OBSERVADO';
  if (distance <= 5) return 'PREVISTO';
  return 'DESCONHECIDO';
}

export class SimulatedPredictorAdapter {
  async snapshot() {
    const cells = [];
    for (let row = 0; row < 12; row += 1) {
      for (let column = 0; column < 12; column += 1) {
        const state = stateFor(row, column);
        const currentK = 112 + row * 4 + column * 2;
        const predicted = state === 'PREVISTO';
        const direct = state === 'VALIDADO' || state === 'OBSERVADO';
        cells.push({
          key: `${row}:${column}`,
          row,
          column,
          rpm: 900 + column * 300,
          petrolMs: 2.0 + row * 0.55,
          state,
          currentK,
          targetK: state === 'DESCONHECIDO' ? null : currentK + (row < 6 ? 4 : -2),
          confidence: state === 'VALIDADO' ? 0.94 : state === 'OBSERVADO' ? 0.78 : predicted ? 0.61 : 0,
          predictionConfidence: predicted ? 0.61 : null,
          predicted,
          directObservation: direct,
          supportCount: predicted ? 5 : direct ? 3 : 0,
          distinctTrajectories: predicted ? 3 : direct ? 2 : 0,
          predictionReason: predicted ? 'Suporte direto independente dentro da região física observada.' : '',
          automaticWrite: false,
        });
      }
    }
    return {
      ok: true,
      source: 'PREDICTOR_PROVIDER',
      revision: 'SIM-PRED-001',
      epoch: 1,
      cells,
      interpolation: {
        mode: 'DIRECT_EVIDENCE_SINGLE_PASS',
        supportFrozenBeforePrediction: true,
        predictionsFeedConfidence: false,
        physicalGeometry: true,
        trajectoryIndependence: true,
        extrapolationAllowed: false,
        automaticWrite: false,
      },
    };
  }
}

export const simulatedPredictorAdapter = new SimulatedPredictorAdapter();
