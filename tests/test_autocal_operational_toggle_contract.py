from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
bridge = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt").read_text(encoding="utf-8")
manager = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt").read_text(encoding="utf-8")
cockpit = (ROOT / "app/src/main/assets/ui/screens/autocal-cockpit.js").read_text(encoding="utf-8")
api = (ROOT / "app/src/main/assets/ui/core/autocal-api.js").read_text(encoding="utf-8")

assert "fun setAcquisitionEnabled(enabled: Boolean)" in bridge
assert "setAcquisitionEnabled: enabled => invoke('setAcquisitionEnabled'" in api
assert "this.runOperational(action)" in cockpit
assert "this.api.setAcquisitionEnabled" in cockpit
assert "if (parsed.operationalToggle)" in bridge
assert "requiresCriticalConfirmation\", !action.operationalToggle" in manager
assert "if (!action.operationalToggle)" in manager
assert "if (!current.action.operationalToggle)" in manager
assert "if (!prepared.action.operationalToggle)" in manager

# Destructive resets are separate from the operational enable/pause toggle,
# but they are intentionally present because the canonical ProgBase EXE proves them.
assert "RESET_PETROL(" in manager
assert "RESET_GAS(" in manager
assert "RESET_ALL(" in manager
assert 'data-autocal-action="RESET_PETROL"' in cockpit
assert 'data-autocal-action="RESET_GAS"' in cockpit
assert 'data-autocal-action="RESET_K_FACTOR"' in cockpit
assert 'data-autocal-action="RESET_ALL"' in cockpit
assert "Continuar para confirmação Android" not in cockpit
assert "Confirmação Android aberta" not in cockpit
assert "Executar agora" in cockpit
assert "ACK e readback" in cockpit
assert "actionManager.execute(preparationId)" in bridge
assert "AlertDialog" not in bridge

print("AUTOCAL_OPERATIONAL_TOGGLE_CONTRACT=PASS")
