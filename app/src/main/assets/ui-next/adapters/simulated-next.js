import { simulatedAdapter } from './simulated.js';
import { simulatedMapKAdapter } from './simulated-map.js';
import { simulatedPredictorAdapter } from './simulated-predictor.js';
import { simulatedCurveAdapter } from './simulated-curve.js';
import { simulatedObdAdapter } from './simulated-obd.js';
import { simulatedSuggestionsAdapter } from './simulated-suggestions.js';
import { CAPABILITY, NEXT_SCHEMA, capabilitySet } from './next-contract.js';

const capabilities = capabilitySet({
  [CAPABILITY.FAST_TELEMETRY]: { available: true },
  [CAPABILITY.LEARNING_STATUS]: { available: true },
  [CAPABILITY.CELL_SEMANTICS]: { available: true },
  [CAPABILITY.PREDICTOR]: { available: true },
  [CAPABILITY.MAP_READ]: { available: true },
  [CAPABILITY.MAP_PREVIEW]: { available: true },
  [CAPABILITY.MAP_WRITE]: { available: false, reason: 'Netlify/simulador nunca grava ECU.' },
  [CAPABILITY.CURVE_READ]: { available: true },
  [CAPABILITY.CURVE_PREVIEW]: { available: true },
  [CAPABILITY.CURVE_WRITE]: { available: false, reason: 'Netlify/simulador nunca grava ECU.' },
  [CAPABILITY.AUTOCAL_STATUS]: { available: true },
  [CAPABILITY.AUTOCAL_ACTIONS]: { available: false, reason: 'Netlify/simulador nunca envia comandos AutoCal.' },
  [CAPABILITY.OBD_WITNESS]: { available: true },
  [CAPABILITY.SUGGESTIONS]: { available: true },
});

export class SimulatedNextAdapter {
  identity() {
    return Object.freeze({
      schema: NEXT_SCHEMA.adapter,
      mode: 'SIMULATED',
      source: 'FIXTURES_ONLY',
      dataFictional: true,
      native: false,
      product: 'OMEGAS V8.2 NEXT',
    });
  }

  capabilities() { return capabilities; }
  fastTelemetry() { return simulatedAdapter.fastTelemetry(); }
  learningStatus() { return simulatedAdapter.learningStatus(); }
  cellContext() { return simulatedAdapter.cellContext(); }
  predictorSnapshot() { return simulatedPredictorAdapter.snapshot(); }
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
