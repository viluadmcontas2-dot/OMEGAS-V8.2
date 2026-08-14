const petrolBins = Array.from({ length: 30 }, (_, i) => Number((1.5 + i * 0.35).toFixed(2)));
const base = petrolBins.map((petrolMs, index) => ({ index, petrolMs, factor: Number((1 + Math.sin(index / 5) * 0.035).toFixed(3)) }));

export class SimulatedCurveAdapter {
  async readCurve() {
    return { state: 'READY', revision: 'SIM-CURVE-001', perspective: 'adjust', points: base.map((p) => ({ ...p })), prepared: [], sourceConfirmed: true, pointCount: 30 };
  }
  async preview(index, delta) {
    const point = base[index];
    if (!point) throw new Error('Ponto Curva K inválido');
    return { index, petrolMs: point.petrolMs, before: point.factor, after: Number((point.factor + delta).toFixed(3)), automaticWrite: false, humanConfirmationRequired: true, simulatedOnly: true };
  }
  async autoCalStatus() {
    return {
      state: 'READY', enabled: true, actionState: 'IDLE', nativeSource: 'ECU_NATIVE',
      acquiredZones: 18, matureZones: 7, maxZones: 30, counter: 12,
      mulActSummary: 'Curva K nativa ativa',
      disableWarning: 'Desabilitar pode retirar o efeito da correção K/linha azul; não é pausa inofensiva.',
      automaticWrite: false, manualOnly: true,
    };
  }
  async comparison() {
    return { state: 'READY', globalErrorPct: 3.8, confidence: 0.82, direction: 'INCREASE_CNG_DELIVERY', gasolineCoverage: 0.76, cngCoverage: 0.68, localResidualNote: 'Resíduos localizados permanecem responsabilidade do Mapa K.' };
  }
}
export const simulatedCurveAdapter = new SimulatedCurveAdapter();
