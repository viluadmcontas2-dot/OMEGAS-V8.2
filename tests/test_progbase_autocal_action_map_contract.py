#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ORACLE = json.loads((ROOT / 'tests/fixtures/progbase-autocal-action-map-v1.json').read_text(encoding='utf-8'))
ACTION = (ROOT / 'app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt').read_text(encoding='utf-8')

assert ORACLE['classification'] == 'ORIGINAL_DERIVED'
assert ORACLE['sources']['progbase']['sha256'] == '8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4'
rows = {row['action']: row for row in ORACLE['actions']}
expected = {
    'MANUAL_AUTOMATCH': ('ActionAutoMatchExecute', '0x005189C0', '0x01', '02 24 04 01 2B'),
    'RESET_PETROL': ('ActionResetPetrolExecute', '0x005189CC', '0x02', '02 24 04 02 2C'),
    'RESET_GAS': ('ActionResetGasExecute', '0x005189D8', '0x04', '02 24 04 04 2E'),
}
for name, (handler, va, mode, frame) in expected.items():
    row = rows[name]
    assert row['handler'] == handler, (name, row)
    assert row['handlerVa'] == va, (name, row)
    assert row['mode'] == mode, (name, row)
    assert row['frame'] == frame, (name, row)

reset_all = rows['RESET_ALL']
assert reset_all['handler'] == 'ActionResetAllExecute'
assert reset_all['handlerVa'] == '0x005189E4'
assert reset_all['mode'] is None
assert reset_all['frame'] is None

modify_refs = ORACLE['separateActions']['modifyMapRefs']
assert modify_refs['handler'] == 'ActionAutoCalRifExecute'
assert modify_refs['handlerVa'] == '0x005189B4'
assert modify_refs['mode'] == '0x08'
assert modify_refs['frame'] == '02 24 04 08 32'

gas_effect = ORACLE['observedEffects']['RESET_GAS_LOGNOVO']
assert gas_effect['frame'] == '02 24 04 04 2E'
assert gas_effect['petrolBuffersCleared'] is True
assert gas_effect['gasBuffersCleared'] is True
assert gas_effect['petrolReferenceCurveCleared'] is True
assert gas_effect['gasReferenceCurveCleared'] is True
assert gas_effect['mulActResetToQ14One'] is True

assert 'Mp48Protocol.frame(byteArrayOf(0x02, 0x24, 0x04, 0x02))' in ACTION
assert 'Mp48Protocol.frame(byteArrayOf(0x02, 0x24, 0x04, 0x04))' in ACTION
assert 'RESET_PETROL' in ACTION and 'RESET_GAS' in ACTION
print('PROGBASE_AUTOCAL_ACTION_ORACLE=PASS')
