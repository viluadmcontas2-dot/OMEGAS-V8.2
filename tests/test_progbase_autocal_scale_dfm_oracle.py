import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
FIXTURE=json.loads((ROOT/'tests/fixtures/progbase-autocal-scale-dfm-v1.json').read_text(encoding='utf-8'))
SCALE=(ROOT/'app/src/main/java/com/omegas/prohub/ecu/AutoCalScale.kt').read_text(encoding='utf-8')
KFACTOR=(ROOT/'app/src/main/java/com/omegas/prohub/ecu/KFactorProtocol.kt').read_text(encoding='utf-8')

def test_canonical_dfm_scale_oracle_is_original_derived():
    assert FIXTURE['classification']=='ORIGINAL_DERIVED'
    assert FIXTURE['source']['progbase']['sha256']=='8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4'
    assert FIXTURE['source']['extraction']['immutableAtlasRef']=='1ad8af2b249e069cee7b92f211f6678ce990de98'
    assert FIXTURE['formulaFamily']['transformation']=='ttFormula'
    assert FIXTURE['formulaFamily']['semanticCrossCheck']['observedRawOne']==16384
    assert FIXTURE['formulaFamily']['semanticCrossCheck']['observedPhysicalOne']==1.0

def test_direct_dfm_denominators_are_512_1024_and_q14():
    by_name={row['name']:row for row in FIXTURE['fields']}
    for name in ('PETR_INJ_TBP','PETR_INJ_TBUF','PETR_INJ_TBUF_GAS'):
        assert by_name[name]['denominator']==512
        assert by_name[name]['coeffsExtendedHex'][3]=='00000000000000800840'
    assert by_name['MNFLD_PRESS_THD']['denominator']==1024
    assert by_name['MNFLD_PRESS_THD']['coeffsExtendedHex'][3]=='00000000000000800940'
    assert by_name['MUL_ACT']['denominator']==16384
    assert by_name['MUL_ACT']['coeffsExtendedHex'][3]=='00000000000000800d40'

def test_production_autocal_scale_matches_binary_oracle():
    assert 'INJECTION_COUNTS_PER_MS = 512.0' in SCALE
    assert 'MAP_COUNTS_PER_BAR = 1_024.0' in SCALE
    assert 'Q14_COUNTS_PER_FACTOR = 16_384.0' in SCALE
    assert 'AXIS_COUNTS_PER_MS = 512.0' in KFACTOR

if __name__=='__main__':
    test_canonical_dfm_scale_oracle_is_original_derived()
    test_direct_dfm_denominators_are_512_1024_and_q14()
    test_production_autocal_scale_matches_binary_oracle()
    print('PROGBASE_AUTOCAL_SCALE_DFM_ORACLE=PASS')
