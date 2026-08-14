export class SimulatedObdAdapter {
  async snapshot() {
    return {
      schema: 'omegas-next-obd-v1',
      state: 'VALIDO',
      connected: true,
      mode: 'local',
      updatedAt: Date.now(),
      ageMs: 120,
      fuel: 'GNV',
      stftPct: 3.1,
      ltftPct: 1.6,
      rpm: 1812,
      mapKpa: 61,
      loadPct: 34,
      coolantC: 91,
      closedLoop: true,
      pidAvailability: { stft: true, ltft: true, rpm: true, map: true, load: true },
      observationalOnly: true,
      ecuAuthority: false,
      learningAuthority: false,
      automaticCalibration: false,
      layers: {
        gasoline: { samples: 42, stftMeanPct: 1.4, ltftMeanPct: 1.1, state: 'OBSERVADO' },
        cng: { samples: 37, stftMeanPct: 3.2, ltftMeanPct: 1.8, state: 'OBSERVADO' },
        comparison: {
          state: 'CONCORDA',
          explanation: 'OBD e comparação gasolina↔GNV apontam para a mesma direção nesta condição.',
          learningDirection: 'INCREASE_CNG_DELIVERY',
          obdDirection: 'INCREASE_CNG_DELIVERY',
          mergedValue: null,
        },
      },
    };
  }
}

export const simulatedObdAdapter = new SimulatedObdAdapter();
