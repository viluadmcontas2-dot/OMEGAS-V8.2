const startedAt = performance.now();

export class SimulatedNextAdapter {
  async fastTelemetry() {
    const t = (performance.now() - startedAt) / 1000;
    const rpm = Math.round(1760 + Math.sin(t * 1.2) * 85);
    const petrolMs = 4.50 + Math.sin(t * 0.75) * 0.08;
    return {
      sequence: Math.floor(t * 10),
      capturedAtMs: Date.now(),
      ageMs: 0,
      valid: true,
      sessionId: 9001,
      rpm,
      petrolMs,
      gasMsDiagnostic: 7.84 + Math.sin(t * 0.65) * 0.12,
      mapBar: 0.62 + Math.sin(t * 0.4) * 0.015,
      fuel: 'CNG',
      engineState: 'ONLINE',
    };
  }

  async learningStatus() {
    return {
      state: 'CONSOLIDATED',
      label: 'Referência pronta para comparar',
      reason: 'Há referência gasolina equivalente nesta condição física.',
      source: 'LEARNING',
    };
  }

  async cellContext() {
    return {
      schema: 'omegas-next-cell-semantics-v1',
      cell: { row: 4, column: 3, meaning: 'PHYSICAL_REGION_RPM_X_PETROL_MS', isMeasurement: false },
      currentCondition: {
        rpm: 1850,
        mapBar: 0.62,
        fuel: 'CNG',
        petrolInjection: {
          role: 'CURRENT_CONDITION',
          label: 'Petrol Inj. agora',
          value: 4.50,
          unit: 'ms',
          state: 'OBSERVED',
          source: 'MP48_TELEMETRY_NOW',
          explanation: 'Valor instantâneo. Não substitui a referência equivalente.',
        },
      },
      gasolineEquivalentReference: {
        role: 'GASOLINE_EQUIVALENT_REFERENCE',
        label: 'Referência gasolina equivalente',
        value: 5.18,
        unit: 'ms',
        state: 'CONSOLIDATED',
        confidence: 0.88,
        source: 'CONTINUOUS_GASOLINE_REFERENCE_SURFACE',
        explanation: 'Referência aprendida em condição física comparável de RPM, MAP e temperatura.',
      },
      cngObservation: {
        role: 'CNG_OBSERVATION',
        label: 'Petrol Inj. observado no GNV',
        value: 5.72,
        unit: 'ms',
        state: 'CONSOLIDATED',
        confidence: 0.82,
        source: 'MP48_PETROL_INJECTION_WHILE_CNG',
        explanation: 'O que a ECU de gasolina comandou enquanto o motor rodava no GNV. Não é Gas Inj.',
      },
      inference: {
        role: 'INFERENCE',
        label: 'Valor inferido',
        value: null,
        unit: 'ms',
        state: 'UNKNOWN',
        confidence: null,
        source: 'PREDICTOR',
        explanation: 'Sem previsão necessária neste exemplo.',
      },
      comparison: {
        comparable: true,
        differenceMs: 0.54,
        differencePct: 10.42,
        direction: 'INCREASE_CNG_DELIVERY',
        quality: 0.82,
        reason: 'Condição equivalente por RPM, MAP e temperatura.',
        rule: 'CNG_PETROL_OBSERVATION_MINUS_EQUIVALENT_GASOLINE_REFERENCE',
      },
      calibration: {
        role: 'CALIBRATION_K',
        currentK: 120,
        targetK: 132,
        proposedK: null,
        unit: 'K_FACTOR',
        automaticWrite: false,
        humanConfirmationRequired: true,
      },
      obdWitness: {
        role: 'OBD_WITNESS',
        trimPct: null,
        fresh: false,
        observationalOnly: true,
      },
    };
  }
}

export const simulatedAdapter = new SimulatedNextAdapter();
