import { simulatedAdapter } from './simulated.js';
import { simulatedMapKAdapter } from './simulated-map.js';
import { simulatedPredictorAdapter } from './simulated-predictor.js';
import { simulatedCurveAdapter } from './simulated-curve.js';
import { simulatedObdAdapter } from './simulated-obd.js';
import { simulatedSuggestionsAdapter } from './simulated-suggestions.js';
import { SIMULATED_FIXTURES, selectedFixtureName } from './simulated-fixtures.js';
import { CAPABILITY, NEXT_SCHEMA, capabilitySet, makeError, revisionEvent } from './next-contract.js';

const fixtureName = selectedFixtureName();
const fixture = SIMULATED_FIXTURES[fixtureName];

const capabilities = capabilitySet({
  [CAPABILITY.FAST_TELEMETRY]: { available: true },
  [CAPABILITY.LEARNING_STATUS]: { available: true },
  [CAPABILITY.CELL_SEMANTICS]: { available: true },
  [CAPABILITY.PREDICTOR]: { available: true },
  [CAPABILITY.MAP_READ]: { available: true },
  [CAPABILITY.MAP_PREVIEW]: { available: true },
  [CAPABILITY.MAP_WRITE]: { available: false, reason: fixture?.failures?.write || 'Netlify/simulador nunca grava ECU.' },
  [CAPABILITY.CURVE_READ]: { available: true },
  [CAPABILITY.CURVE_PREVIEW]: { available: true },
  [CAPABILITY.CURVE_WRITE]: { available: false, reason: fixture?.failures?.write || 'Netlify/simulador nunca grava ECU.' },
  [CAPABILITY.AUTOCAL_STATUS]: { available: true },
  [CAPABILITY.AUTOCAL_ACTIONS]: { available: false, reason: 'Netlify/simulador nunca envia comandos AutoCal.' },
  [CAPABILITY.OBD_WITNESS]: { available: true },
  [CAPABILITY.SUGGESTIONS]: { available: true },
  [CAPABILITY.REVISION_EVENTS]: { available: true },
});

export class SimulatedNextAdapter {
  identity() {
    return Object.freeze({
      schema: NEXT_SCHEMA.adapter,
      mode: 'SIMULATED',
      source: 'FIXTURES_ONLY',
      dataFictional: true,
      native: false,
      fixture: fixtureName,
      fixtureLabel: fixture.label,
      product: 'OMEGAS V8.2 NEXT',
    });
  }

  capabilities() { return capabilities; }

  subscribeRevisions(listener) {
    if (typeof listener !== 'function') return () => {};
    const handler = (event) => listener(revisionEvent({
      type: 'SIMULATED_REVISION',
      sequence: Number(event?.detail?.sequence ?? 0),
      sessionId: Number(event?.detail?.sessionId ?? 9001),
      structural: event?.detail?.structural === true,
      reason: 'Fixture simulada',
    }));
    globalThis.addEventListener?.('omegas-simulated-revision', handler);
    return () => globalThis.removeEventListener?.('omegas-simulated-revision', handler);
  }

  async fastTelemetry() {
    const base = await simulatedAdapter.fastTelemetry();
    return { ...base, ...fixture.telemetry, fixture: fixtureName };
  }

  async learningStatus() {
    const base = await simulatedAdapter.learningStatus();
    return { ...base, ...fixture.learning, fixture: fixtureName };
  }

  async cellContext() {
    if (fixtureName === 'no-data' || fixtureName === 'error') return null;
    return simulatedAdapter.cellContext();
  }

  async predictorSnapshot() {
    if (fixture.failures?.predictor) {
      throw makeError('SIMULATED_PREDICTOR_FAILURE', fixture.failures.predictor, 'fixture=error', { source: 'FIXTURES_ONLY' });
    }
    return simulatedPredictorAdapter.snapshot();
  }

  readMapK() { return simulatedMapKAdapter.readMap(); }
  previewMapK(selection, delta) { return simulatedMapKAdapter.preview(selection, delta); }
  readCurveK() { return simulatedCurveAdapter.readCurve(); }
  previewCurveK(index, delta) { return simulatedCurveAdapter.preview(index, delta); }
  autoCalStatus() { return simulatedCurveAdapter.autoCalStatus(); }
  curveComparison() { return simulatedCurveAdapter.comparison(); }
  obdSnapshot() { return simulatedObdAdapter.snapshot(); }
  suggestionsSnapshot() { return simulatedSuggestionsAdapter.snapshot(); }
}

export const simulatedNextAdapter = new SimulatedNextAdapter();
