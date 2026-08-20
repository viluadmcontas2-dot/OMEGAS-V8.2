from pathlib import Path
import shutil
import subprocess


ROOT = Path(__file__).resolve().parents[1]
LEARNING_MODEL = ROOT / "app/src/main/assets/ui/core/learning-model.js"


def run_node(script: str) -> subprocess.CompletedProcess[str]:
    node = shutil.which("node")
    assert node, "Node.js is required to behaviorally verify the WebView learning efficiency contract"
    return subprocess.run(
        [node, "-e", script],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )


def test_learning_decision_is_projected_from_existing_live_telemetry():
    script = r"""
const model = require('./app/src/main/assets/ui/core/learning-model.js');
const telemetry = {
  valid: true,
  live: {
    rpm: 2100,
    petrol_ms: 4.2,
    fuel: 'GNV',
    sample_state: 'FORMING_SAMPLE',
    sample_reason: 'Formando amostra 7/10 leituras',
    sample: {
      state: 'FORMING_SAMPLE', reason: 'Formando amostra 7/10 leituras', reason_code: 'FORMING_SAMPLE',
      frame_count: 7, minimum_frames: 6, desired_frames: 10, duration_ms: 1780,
      learning_eligible: false, fuel_confirmed: 'GNV', cell_row: 4, cell_column: 3, quality: 0.82,
    },
  },
};
const decision = model.decisionFromTelemetry(telemetry);
if (decision.state !== 'FORMING_SAMPLE') throw new Error('state');
if (decision.frame_count !== 7) throw new Error('frame_count');
if (decision.cell_row !== 4 || decision.cell_column !== 3) throw new Error('cell');
if (decision.live !== telemetry.live) throw new Error('must retain existing live projection');
"""
    result = run_node(script)
    assert result.returncode == 0, result.stderr or result.stdout


def test_science_revision_ignores_frame_churn_but_changes_on_material_science():
    script = r"""
const model = require('./app/src/main/assets/ui/core/learning-model.js');
const base = {
  session_id: 'usb-1', epoch: 3,
  new_frames_absorbed: 120,
  lifetime_new_frames_absorbed: 900,
  advisor_revision: 7,
  advisor_published_revision: 7,
  evidence_budget: { nativeBands: 18, nativeAnchors: 4 },
  calibration_binding: { calibrationFingerprint: 'abc', calibrationGeneration: 3 },
  last_reset: { resetAt: 1000 },
  performance_metrics: { framesReceived: 10000 },
};
const churn = JSON.parse(JSON.stringify(base));
churn.performance_metrics.framesReceived = 10100;
if (model.scienceRevisionSignature(base) !== model.scienceRevisionSignature(churn)) {
  throw new Error('plain frame churn must not invalidate structural science');
}
const material = JSON.parse(JSON.stringify(base));
material.new_frames_absorbed += 8;
if (model.scienceRevisionSignature(base) === model.scienceRevisionSignature(material)) {
  throw new Error('new scientific frames must invalidate structural science');
}
const reset = JSON.parse(JSON.stringify(base));
reset.last_reset.resetAt += 1;
if (model.scienceRevisionSignature(base) === model.scienceRevisionSignature(reset)) {
  throw new Error('calibration reset must invalidate structural science');
}
"""
    result = run_node(script)
    assert result.returncode == 0, result.stderr or result.stdout


def test_runtime_efficiency_reuses_learning_projection_until_science_revision_changes():
    script = r"""
const model = require('./app/src/main/assets/ui/core/learning-model.js');
let statusCalls = 0;
let learningCalls = 0;
let fullSnapshotCalls = 0;
let now = 1000;
let status = {
  session_id: 'usb-1', epoch: 1, new_frames_absorbed: 10,
  lifetime_new_frames_absorbed: 10, advisor_revision: 1, advisor_published_revision: 1,
  evidence_budget: { nativeBands: 0, nativeAnchors: 0 },
};
const telemetry = { live: { sample_state: 'OBSERVING_ENGINE', sample: { state: 'OBSERVING_ENGINE' } } };
const app = {
  store: { get: () => ({ telemetry }) },
  api: {
    learningStatus: () => { statusCalls += 1; return JSON.parse(JSON.stringify(status)); },
    learning: () => { learningCalls += 1; return { marker: learningCalls }; },
    learningDecision: () => { fullSnapshotCalls += 1; return { legacy: true }; },
  },
};
const installed = model.installRuntimeEfficiency(app, { now: () => now, statusBurstMs: 300 });
if (!installed) throw new Error('install failed');
const first = app.api.learning();
const sameTickStatus = app.api.learningStatus();
if (statusCalls !== 1) throw new Error(`same-tick status should be reused: ${statusCalls}`);
if (learningCalls !== 1 || first.marker !== 1) throw new Error('first projection');
now += 2000;
const second = app.api.learning();
if (statusCalls !== 2) throw new Error('fresh revision check expected');
if (learningCalls !== 1 || second.marker !== 1) throw new Error('unchanged science rebuilt learning projection');
status.new_frames_absorbed += 5;
now += 2000;
const third = app.api.learning();
if (learningCalls !== 2 || third.marker !== 2) throw new Error('changed science did not rebuild learning projection');
const decision = app.api.learningDecision();
if (decision.state !== 'OBSERVING_ENGINE') throw new Error('live decision projection');
if (fullSnapshotCalls !== 0) throw new Error('legacy full snapshot path was invoked');
"""
    result = run_node(script)
    assert result.returncode == 0, result.stderr or result.stdout
