#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
manager = (ROOT / 'app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt').read_text(encoding='utf-8')
bridge = (ROOT / 'app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt').read_text(encoding='utf-8')
cockpit = (ROOT / 'app/src/main/assets/ui/screens/autocal-cockpit.js').read_text(encoding='utf-8')

# Host intent remains the proven ProgBase action. Curve K is a distinct path.
assert 'RESET_GAS(' in manager
assert 'ManualActionMode.RESET_GAS' in manager
assert 'RESET_K_FACTOR(' in manager
assert 'AutoCalProtocol.resetKFactorMulActFrames()' in manager
assert 'RESET_K_FACTOR MUL_ACT[$index]' in manager
assert 'actual.all { it == 0x4000 }' in manager
assert 'if (requested == "NEUTRALIZE_LIVE_K") "RESET_K_FACTOR"' not in bridge
assert 'NEUTRALIZE_LIVE_K foi removido' in bridge

# Fuel-specific reacquisition remains free of a backup gate. Reset All is different:
# canonical Portmon observed a broad effect including MUL_ACT, so a read-only K
# before/after bracket is required evidence rather than an automatic rollback/backup.
for forbidden in (
    'PERSISTING_BACKUP',
    'persistPreMutationBackup',
    'autocal_pre_reset',
    'CONFIRMED_WITH_SCOPE_WARNING',
):
    assert forbidden not in manager, forbidden

assert 'RESET_GAS(' in manager and '"Readquirir GNV"' in manager
assert 'RESET_PETROL(' in manager and '"Readquirir gasolina"' in manager
assert 'RESET_ALL(' in manager and '"Nova aquisição completa"' in manager
for action, mode in (
    ('RESET_GAS', 'RESET_GAS'),
    ('RESET_PETROL', 'RESET_PETROL'),
):
    pattern = rf'{action}\([\s\S]*?ManualActionMode\.{mode}\)[\s\S]*?\n\s*false,\n\s*\),'
    assert re.search(pattern, manager), f'{action} must remain a non-K-changing fuel reacquisition action'

reset_all = r'RESET_ALL\([\s\S]*?ManualActionMode\.RESET_ALL\)[\s\S]*?\n\s*true,\n\s*\),'
assert re.search(reset_all, manager), 'RESET_ALL must preserve the Portmon-proven possible MUL_ACT effect'
assert 'if (prepared.action.mayChangeMulAct)' in manager

assert '.put("automaticBackup", false)' in manager
assert '.put("preMutationBackup", JSONObject.NULL)' in manager
assert 'update("SENDING_ACTION"' in manager
assert 'update("READING_AFTER", "Atualizando estado da ECU"' in manager

# UX says exactly what the operator asked for: backups are optional/manual.
assert 'salve manualmente' in cockpit
assert 'Backup não é requisito' in cockpit
assert 'Confirmar executa agora pelo OMEGAS' in cockpit
assert 'AlertDialog' not in bridge
assert 'nativeAndroidConfirmation", true' not in bridge
assert 'backup pré-mutação' not in bridge.lower()

print('AUTOCAL_REACQUISITION_NO_BACKUP_CONTRACT=PASS')
