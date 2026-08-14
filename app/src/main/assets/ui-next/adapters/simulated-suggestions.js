export class SimulatedSuggestionsAdapter {
  async snapshot() {
    return {
      schema: 'omegas-next-suggestions-v1',
      state: 'READY',
      activeCount: 3,
      readyCount: 2,
      automaticWrite: false,
      humanSelectionRequired: true,
      items: [
        {
          id: 'advisor-map-4-3-r1', createdAt: 1000, updatedAt: 4500,
          target: 'MAP_K', targetLabel: 'Mapa K local', lifecycle: 'PENDENTE', actionable: true,
          confidence: 0.88, supportState: 'CONSOLIDATED',
          reason: 'Residual local consolidado nesta condição; aplicação exclusivamente manual.',
          whatIsMissing: 'Nada obrigatório: está pronta para revisão humana, não para escrita automática.',
          mapChanges: [{ row: 4, column: 3, before: 120, after: 132 }], curveChanges: [], automaticWrite: false, requiresReview: true,
        },
        {
          id: 'advisor-curve-global-r1', createdAt: 1200, updatedAt: 4300,
          target: 'CURVE_K', targetLabel: 'Curva K global', lifecycle: 'PENDENTE', actionable: true,
          confidence: 0.81, supportState: 'CONSOLIDATED',
          reason: 'Tendência global consistente em três pontos da Curva K; aplicação exclusivamente manual.',
          whatIsMissing: 'Nada obrigatório: está pronta para revisão humana, não para escrita automática.',
          mapChanges: [], curveChanges: [
            { index: 8, before: 1.000, after: 1.025 },
            { index: 9, before: 1.004, after: 1.029 },
            { index: 10, before: 1.007, after: 1.032 },
          ], automaticWrite: false, requiresReview: true,
        },
        {
          id: 'advisor-map-6-4-r1', createdAt: 1300, updatedAt: 4200,
          target: 'MAP_K', targetLabel: 'Mapa K local', lifecycle: 'OBSERVANDO', actionable: false,
          confidence: 0.46, supportState: 'REVALIDATING',
          reason: 'A sugestão continua registrada, mas a evidência recente contradiz o consolidado.',
          whatIsMissing: 'Mais evidência independente e coerente nesta condição.',
          mapChanges: [], curveChanges: [], automaticWrite: false, requiresReview: false,
        },
        {
          id: 'advisor-map-old-r0', createdAt: 100, updatedAt: 900,
          target: 'MAP_K', targetLabel: 'Mapa K local', lifecycle: 'SUPERADA', actionable: false,
          confidence: 0.77, supportState: 'SUPERSEDED',
          reason: 'A base de calibração mudou após readback confirmado.',
          whatIsMissing: 'A revisão/base mudou; esta sugestão fica apenas no histórico.',
          mapChanges: [], curveChanges: [], automaticWrite: false, requiresReview: false,
        },
      ],
    };
  }
}

export const simulatedSuggestionsAdapter = new SimulatedSuggestionsAdapter();
