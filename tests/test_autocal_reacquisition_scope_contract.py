#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
manager = (ROOT / 'app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt').read_text(encoding='utf-8')
bridge = (ROOT / 'app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt').read_text(encoding='utf-8')
cockpit = (ROOT / 'app/src/main/assets/ui/screens/autocal-cockpit.js').read_text(encoding='utf-8')

# Host intent remains the proven ProgBase action. Curve K is a distinct host path.
assert 'RESET_GAS(' in manager
assert 'byteArrayOf(0x02, 0x24, 0x04, 0x02)' in manager
assert 'RESET_K_FACTOR' not in manager.split('enum class Action', 1)[1].split('// ProgBase', 1)[0]

# Every destructive acquisition action must record what actually changed after ACK/readback.
for token in (
    'scopeAssessment',
    'CONFIRMED_WITH_SCOPE_WARNING',
    'MUL_ACT',
    'ACQUIRED_ZONES_PETROL',
    'ACQUIRED_ZONES_GAS',
    'broaderThanIntended',
):
    assert token in manager, token

# UI/bridge must not claim firmware-selective behavior before the after-snapshot proves it.
assert 'Curva K usa outro caminho' in cockpit
assert 'Readquirir GNV' in cockpit
assert 'mudança fora do esperado' in cockpit
assert 'efeito seletivo garantido' not in bridge.lower()

print('AUTOCAL_REACQUISITION_SCOPE_CONTRACT=PASS')
