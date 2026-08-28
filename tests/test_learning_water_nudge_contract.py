from pathlib import Path
import shutil
import subprocess


ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "app/src/main/assets/ui/components/learning-water-nudge.js"
INDEX = ROOT / "app/src/main/assets/ui/index.html"


def run_node(script: str) -> subprocess.CompletedProcess[str]:
    node = shutil.which("node")
    assert node, "Node.js is required to verify Learning water-temperature controls"
    return subprocess.run([node, "-e", script], cwd=ROOT, text=True, capture_output=True, check=False)


def test_temperature_nudges_are_bounded_and_do_not_apply_automatically():
    script = r"""
const controls = require('./app/src/main/assets/ui/components/learning-water-nudge.js');
if (controls.adjustTemperature(60, 5) !== 65) throw new Error('plus five');
if (controls.adjustTemperature(60, -10) !== 50) throw new Error('minus ten');
if (controls.adjustTemperature(98, 10) !== 100) throw new Error('upper bound');
if (controls.adjustTemperature(22, -5) !== 20) throw new Error('lower bound');
if (controls.adjustTemperature('bad', 5) !== 65) throw new Error('safe fallback');
"""
    result = run_node(script)
    assert result.returncode == 0, result.stderr or result.stdout


def test_temperature_enhancer_is_loaded_after_learning_screen_and_before_app_boot():
    html = INDEX.read_text(encoding="utf-8")
    learning = html.index('src="screens/learning.js"')
    enhancer = html.index('src="components/learning-water-nudge.js"')
    app = html.index('src="app.js"')
    assert learning < enhancer < app


def test_temperature_enhancer_offers_large_touch_steps_without_auto_apply():
    source = MODULE.read_text(encoding="utf-8")
    for delta in ('-10', '-5', '5', '10'):
        assert f'data-learning-water-nudge="{delta}"' in source
    assert "setLearningToleranceControls" not in source
    assert "learningMinimumWaterInput" in source
