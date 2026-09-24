#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
protocol = (ROOT / 'app/src/main/java/com/omegas/prohub/ecu/AutoCalPointDeleteProtocol.kt').read_text(encoding='utf-8')
manager = (ROOT / 'app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt').read_text(encoding='utf-8')
bridge = (ROOT / 'app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt').read_text(encoding='utf-8')
api = (ROOT / 'app/src/main/assets/ui/core/autocal-api.js').read_text(encoding='utf-8')
cockpit = (ROOT / 'app/src/main/assets/ui/screens/autocal-cockpit.js').read_text(encoding='utf-8')

for token in (
    'PETROL_DELETE_ADDRESS = 0x016D',
    'GAS_DELETE_ADDRESS = 0x016E',
    'POINT_COUNT = 18',
    'WRITE_VECTOR_U8 = 0x13',
    'KEEP = 1',
    'DELETE = 0',
    'Mp48Protocol.frame(byteArrayOf(0x01, 0x24, 0x05))',
):
    assert token in protocol, token
assert 'for (fuel in listOf(Fuel.GAS, Fuel.PETROL))' in protocol
assert 'fun preparePointDelete' in manager
assert 'AutoCalPointDeleteProtocol.singlePointPlan(target)' in manager
assert 'maskFrames.forEachIndexed' in manager
assert 'Thread.sleep(500L)' in manager
assert 'automaticBackup", false' in manager
for forbidden in ('persistPreMutationBackup', 'PERSISTING_BACKUP', 'READING_BEFORE'):
    assert forbidden not in manager, forbidden
for token in (
    "PETR_INJ_TBUF_GAS",
    "MNFLD_PRESS_BUF_GAS",
    "NUM_BUF_UPD_GAS",
    "PETR_INJ_TBUF",
    "MNFLD_PRESS_BUF",
    "NUM_BUF_UPD_PETR",
    "data-autocal-acquired-index",
    "Readquirir este ponto",
):
    assert token in cockpit, token
assert "data-autocal-reacquire-point" in cockpit
assert "preparePointDelete" in api
assert "fun preparePointDelete" in bridge
assert "actionManager.execute(preparationId)" in bridge
assert "AlertDialog" not in bridge
assert "READQUIRIR PONTO" not in bridge
assert "snapshot antes/depois" not in cockpit
assert "writer existente com backup" not in bridge
print('AUTOCAL_POINT_DELETE_CONTRACT=PASS')
