from pathlib import Path
import shutil
import subprocess


ROOT = Path(__file__).resolve().parents[1]
STORE = ROOT / "app/src/main/assets/ui/core/store.js"
PREDICTOR = ROOT / "app/src/main/assets/ui/screens/predictor.js"


def run_node(script: str) -> subprocess.CompletedProcess[str]:
    node = shutil.which("node")
    assert node, "Node.js is required to behaviorally verify the WebView Store contract"
    return subprocess.run(
        [node, "-e", script],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )


def test_selected_subscription_ignores_unrelated_store_patches():
    script = r"""
const fs = require('fs');
const vm = require('vm');
const context = { console };
context.globalThis = context;
context.window = context;
vm.createContext(context);
vm.runInContext(fs.readFileSync('app/src/main/assets/ui/core/store.js', 'utf8'), context);

const Store = context.OmegasUi.Store;
const store = new Store({ route: 'dashboard', status: {}, telemetry: {} });
let calls = 0;
let observed = null;
const unsubscribe = store.subscribeSelected(
  state => state.route,
  value => { calls += 1; observed = value; },
  true,
);

if (calls !== 1 || observed !== 'dashboard') {
  throw new Error(`immediate selected subscription mismatch: calls=${calls}, observed=${observed}`);
}
store.patch({ status: { rpm: 2100 } });
store.patch({ telemetry: { live: { rpm: 2150 } } });
if (calls !== 1) {
  throw new Error(`unrelated patches woke route subscriber: calls=${calls}`);
}
store.patch({ route: 'predictor' });
if (calls !== 2 || observed !== 'predictor') {
  throw new Error(`route change was not delivered exactly once: calls=${calls}, observed=${observed}`);
}
unsubscribe();
store.patch({ route: 'dashboard' });
if (calls !== 2) {
  throw new Error(`unsubscribe failed: calls=${calls}`);
}
"""
    result = run_node(script)
    assert result.returncode == 0, result.stderr or result.stdout


def test_predictor_subscribes_only_to_route_changes():
    source = PREDICTOR.read_text(encoding="utf-8")
    assert "subscribeSelected(" in source
    assert "state => state.route" in source
    assert "this.store.subscribe(state => this.onState(state), true)" not in source


def test_predictor_skips_parse_patch_and_dom_render_for_identical_v7_payload():
    source = PREDICTOR.read_text(encoding="utf-8")
    marker = "if (raw === this.lastStatePayload) return;"
    assert "this.lastStatePayload = null;" in source
    assert marker in source
    assert source.index(marker) < source.index("const calibration = parse(raw, {})")
    assert source.index(marker) < source.index("this.store.patch({", source.index("refresh()"))
