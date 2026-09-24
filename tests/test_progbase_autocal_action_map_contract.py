#!/usr/bin/env python3
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ORACLE=json.loads((ROOT/'tests/fixtures/progbase-autocal-action-map-v1.json').read_text(encoding='utf-8'))
ACTION=(ROOT/'app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt').read_text(encoding='utf-8')
assert ORACLE['classification']=='ORIGINAL_DERIVED'
rows={r['action']:r for r in ORACLE['actions']}
expected={
 'MANUAL_AUTOMATCH':('ActionAutoMatchExecute','0x005189B4','0x08','02 24 04 08 32'),
 'RESET_PETROL':('ActionResetPetrolExecute','0x005189C0','0x01','02 24 04 01 2B'),
 'RESET_GAS':('ActionResetGasExecute','0x005189CC','0x02','02 24 04 02 2C'),
 'RESET_ALL':('ActionResetAllExecute','0x005189D8','0x04','02 24 04 04 2E'),
}
for n,v in expected.items():
 r=rows[n]; assert (r['handler'],r['handlerVa'],r['mode'],r['frame'])==v,(n,r)
rk=ORACLE['separateActions']['resetKFactor']
assert rk['handler']=='ActionResetKFactorExecute' and rk['target']=='MUL_ACT' and '1.0' in rk['operation']
for mode in ('0x01','0x02','0x04'):
 assert f'Mp48Protocol.frame(byteArrayOf(0x02, 0x24, 0x04, {mode}))' in ACTION
print('PROGBASE_AUTOCAL_ACTION_ORACLE=PASS')
