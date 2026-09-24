from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

settings = (ROOT / "app/src/main/java/com/omegas/prohub/settings/AppSettings.kt").read_text(encoding="utf-8")
mirror = (ROOT / "app/src/main/java/com/omegas/prohub/diagnostics/DocumentsSessionMirror.kt").read_text(encoding="utf-8")
manager = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/KFactorManager.kt").read_text(encoding="utf-8")
service = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
bridge = (ROOT / "app/src/main/java/com/omegas/prohub/web/V7JavascriptBridge.kt").read_text(encoding="utf-8")
api = (ROOT / "app/src/main/assets/ui/core/native-api.js").read_text(encoding="utf-8")
drawers = (ROOT / "app/src/main/assets/ui/components/drawers.js").read_text(encoding="utf-8")
curve = (ROOT / "app/src/main/assets/ui/screens/curve.js").read_text(encoding="utf-8")
index = (ROOT / "app/src/main/assets/ui/index.html").read_text(encoding="utf-8")

# Sessões e backups manuais precisam existir fora do sandbox em Download/Omegas.
assert 'const val PUBLIC_ROOT = "Download/Omegas"' in mirror
assert "fun publishRootFile(" in mirror
assert "Environment.DIRECTORY_DOWNLOADS" in mirror
assert "publishManualBackup" in manager
assert "publishRootFile" in service
assert '"publicPath"' in manager

# Retenção: 20 é piso e default, não teto.
assert 'prefs.getInt("sessionKeepCount", 20)' in settings
assert "value.coerceIn(20, 100)" in settings
assert 'min="20" max="100"' in drawers
assert "Math.max(20" in drawers
assert "keepSessions: 20" in api

# O painel de retenção não pode ser recriado enquanto o usuário interage com ele.
assert "diagnostic-settings" in drawers
assert "preserveSessionSettingsInteraction" in drawers

# Reset Curva K: função existente do writer deve estar acessível na UI e passar pela bridge.
assert 'id="curveResetButton"' in index
assert "resetCurve()" in api
assert "fun startCurveReset()" in bridge
assert "curveResetButton" in curve
assert "Resetar a Curva K para 1.0" in curve

print("FINAL_STORAGE_AND_CURVE_RESET_CONTRACT=PASS")
