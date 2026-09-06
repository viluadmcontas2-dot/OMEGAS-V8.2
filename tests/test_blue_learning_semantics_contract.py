from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

MOTOR = (ROOT / "app/src/main/java/com/omegas/prohub/learning/MotorSampleAnalyzer.kt").read_text(encoding="utf-8")
ENGINE = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt").read_text(encoding="utf-8")
HUB = (ROOT / "app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt").read_text(encoding="utf-8")
LEARNING = (ROOT / "app/src/main/assets/ui/screens/learning.js").read_text(encoding="utf-8")
APP = (ROOT / "app/src/main/assets/ui/app.js").read_text(encoding="utf-8")

# A verdade científica para 0x88/TRANSITION é gasolina enquanto a comutação não terminou.
assert "frame.fuel == Mp48Fuel.TRANSITION" in MOTOR
assert "frame.copy(fuel = Mp48Fuel.PETROL" in MOTOR
assert 'state = "GASOLINA_TRANSICAO"' in MOTOR

# Recuperação serial é política de transporte, não uma preferência do aprendizado.
assert "Mp48SerialRecoveryPolicy" in ENGINE
assert "LearningToleranceSettings.current.toleratedSerialFailures" not in ENGINE
assert "LearningToleranceSettings.current.hardRecoveryFailures" not in ENGINE
assert "LearningToleranceSettings.current.hardRecoverySilenceMs" not in ENGINE

# A ciência calculada pelo BlueCalibrationCoordinator chega à projeção Learning no boundary nativo.
assert 'calibration.optJSONArray("comparisons")' in HUB
assert 'learning.put("comparisons"' in HUB

# Cockpit normal não oferece knobs para o usuário afrouxar/apertar a ciência.
for legacy in (
    'data-learning-inspector="tolerances"',
    "data-tolerance-profile",
    "data-tolerance-control",
    "setLearningToleranceControls",
    "resetLearningToleranceSettings",
):
    assert legacy not in LEARNING
assert "patch.learningTolerance = api.learningToleranceSettings()" not in APP

print("BLUE_LEARNING_SEMANTICS_CONTRACT=PASS")
