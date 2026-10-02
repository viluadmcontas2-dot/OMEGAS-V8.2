#!/usr/bin/env python3
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
o=json.loads((ROOT/'tests/fixtures/progbase-autocal-action-map-v1.json').read_text(encoding='utf-8'))
m=(ROOT/'app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt').read_text(encoding='utf-8')
a={x['action']:x for x in o['actions']}
assert o['sources']['progbase']['sha256']=='8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4'
assert a['MANUAL_AUTOMATCH']['mode']=='0x08'
assert a['RESET_PETROL']['mode']=='0x01'
assert a['RESET_GAS']['mode']=='0x02'
assert a['RESET_ALL']['mode']=='0x04'
assert o['separateActions']['modifyMapRefs']['mode'] is None
assert o['separateActions']['resetKFactor']['operation'].endswith('MUL_ACT[i] = 1.0')
assert 'RESET_ALL(' in m
print('PROGBASE_AUTOCAL_ORIGINAL_ORACLE=PASS')
