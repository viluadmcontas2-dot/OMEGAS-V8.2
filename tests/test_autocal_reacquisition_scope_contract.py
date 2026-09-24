#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
manager = (ROOT / 'app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt').read_text(encoding='utf-8')
bridge = (ROOT / 'app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt').read_text(encoding='utf-8')
cockpit = (ROOT / 'app/src/main/assets/ui/screens/autocal-cockpit.js').read_text(encoding='utf-8')

# Host intent remains the proven ProgBase action. Curve K is a distinct path.
assert 'RESET_GAS(' in manager
assert 'byteArrayOf(0x02, 0x24, 0x04, 0x02)' in manager
assert 'RESET_K_FACTOR' not in manager.split('enum class Action', 1)[1].split('// ProgBase', 1)[0]

# Reset must not be gated by a pre-reset snapshot/backup.
for forbidden in (
    'PERSISTING_BACKUP',
    'persistPreMutationBackup',
    'autocal_pre_reset',
    'READING_BEFORE',
    'CONFIRMED_WITH_SCOPE_WARNING',
):
    assert forbidden not in manager, forbidden

assert '.put("automaticBackup", false)' in manager
assert '.put("preMutationBackup", JSONObject.NULL)' in manager
assert 'update("SENDING_ACTION"' in manager
assert 'update("READING_AFTER", "Atualizando estado da ECU"' in manager

# UX says exactly what the operator asked for: backups are optional/manual.
assert 'Backup da Curva K é manual' in cockpit
assert 'Backup não é requisito' in cockpit
assert 'Confirmar executa agora pelo OMEGAS' in cockpit
assert 'AlertDialog' not in bridge
assert 'nativeAndroidConfirmation", true' not in bridge
assert 'backup pré-mutação' not in bridge.lower()

print('AUTOCAL_REACQUISITION_NO_BACKUP_CONTRACT=PASS')
