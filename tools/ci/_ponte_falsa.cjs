// Ponte AutoCal simulada (só para haver dados nos testes de tela); nada vai para o app real.
function fakeBridge() {
  const n = 30;
  const tbp = Array.from({ length: n }, (_, i) => +(1.5 + i * (10.1 / (n - 1))).toFixed(3));
  const petrolRv = tbp.map((_, i) => +(0.18 + i * 0.028).toFixed(3));
  const gasRv = petrolRv.map((m, i) => +(m * (1.03 + 0.01 * Math.sin(i / 4))).toFixed(3));
  const buf = Array.from({ length: 18 }, (_, i) => +(2 + i * 0.5).toFixed(2));
  const bufMap = buf.map((_, i) => +(0.22 + i * 0.045).toFixed(3));
  const f = (key, v) => ({ key, status: 'VALID', physicalValues: v, rawValues: v });
  const fields = [
    f('PETR_INJ_TBP', tbp), f('PETR_MNFLD_PRESS_RV', petrolRv), f('GAS_MNFLD_PRESS_RV', gasRv),
    f('PETR_INJ_TBUF', buf), f('MNFLD_PRESS_BUF', bufMap), f('NUM_BUF_UPD_PETR', buf.map((_, i) => (i < 15 ? 12 : 4))),
    f('PETR_INJ_TBUF_GAS', buf.map(t => +(t * 1.02).toFixed(2))), f('MNFLD_PRESS_BUF_GAS', bufMap.map(m => +(m * 1.04).toFixed(3))),
    f('NUM_BUF_UPD_GAS', buf.map((_, i) => (i < 11 ? 12 : i < 14 ? 5 : 0))),
    f('CALIBRATION_VAL_1', [0, 0, 10, 0, 0, 8, 0, 0, 10]), f('VECT_AUTOCAL_U8_1', [8]),
    f('MNFLD_PRESS_THD', Array.from({ length: 18 }, (_, i) => +(0.15 + i * 0.055).toFixed(3))),
    f('ACQUIRED_ZONES_GAS', [1, 1, 1, 0]), f('ACQUIRED_ZONES_PETROL', [1, 1, 1, 1]), f('AUTO_CAL_ENABLE', [1]), f('NUM_AUTOMATCH_EXECUTED', [2]), f('MAX_AUTOMATCH', [3]),
  ];
  const snapshot = { available: true, snapshotHash: 'ci-1', fields };
  const projection = { ok: true, source: 'NATIVE', sessionId: 1, referenceUsable: true, snapshot, analysis: {}, nativeStatus: { ok: true, state: 'RUNNING', autoCalEnabled: 1 }, nativeSnapshot: snapshot };
  const dense = k => Array.from({ length: 14 }, (_, i) => ({ tpetMs: +(2.2 + i * 0.55).toFixed(2), mapBar: +((0.24 + i * 0.05) * k).toFixed(3), samples: 20 + i }));
  const equivalence = {
    ok: true, ratio: 1.021, samples: 840, denseBands: { petrol: dense(1), gas: dense(1.035) }, stalls: { count: 0 },
    autopilot: {
      phase: 'PROPOSTA_PRONTA', headline: 'Curva refinada pronta: 12 pontos para ajustar.', next: 'Toque em Ver alterações.',
      petrolValid: 18, gasValid: 14, action: { kind: 'review', label: 'Revisar e gravar 12 pontos', enabled: true },
      secondary: [{ kind: 'undo', label: 'Desfazer última gravação', enabled: true }],
      proposal: { known: true, count: 12, signature: 'ci', points: [] }, autoMatchCount: 3, maxAutomatch: 3,
    },
    refinement: { result: null }, restorePoints: [],
  };
  const sessions = Array.from({ length: 8 }, (_, i) => ({ id: 's' + i, createdAt: Date.now() - i * 864e5, durationMs: (20 + i) * 6e4, active: i === 0, reason: 'Sessão MP48', semanticSummary: { autocal: { gasZones: 3, correlatedRegions: [1, 2] } } }));
  const json = v => JSON.stringify(v);
  window.OmegasAutoCal = {
    getIdentity: () => json({ ok: true }), getStatus: () => json({ ok: true, state: 'READY' }),
    getSnapshot: () => json(snapshot), getNativeMonitorStatus: () => json({ ok: true, state: 'RUNNING', autoCalEnabled: 1 }),
    getNativeMonitorSnapshot: () => json(snapshot), getUiProjection: () => json(projection),
    getSessionLedgerStatus: () => json({ ok: true, sessionCount: 8 }), listAutoCalSessions: () => json(sessions),
    getNativeActionStatus: () => json({}), getRefinedAnalysis: () => json({ ok: true, available: true, points: [] }),
    getEquivalence: () => json(equivalence), getRefinementPhase: () => json({ ok: true, phase: 'PROPOSTA_PRONTA' }),
  };
}

module.exports = { fakeBridge };
