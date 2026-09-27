import json
from pathlib import Path

FIXTURE=Path("tests/fixtures/portmon-lognovo-autocal-epochs-v1.json")
RAW_SHA="43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64"
ZIP_SHA="6879fa2a7931d22c207cd7fa47dffb59e1df0fe1de216e34e3f11e0c08cc1c17"
PROGBASE_SHA="8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4"

def payload(row):
    request=bytes.fromhex(row["request"]); response=bytes.fromhex(row["response"])
    assert response.startswith(request); suffix=response[len(request):]; assert suffix[0]==0x53
    size=suffix[1]; assert len(suffix)==size+3
    assert (sum(suffix[:-1]) & 0xFF) == suffix[-1]
    return suffix[2:2+size]

def u16(row,signed=False):
    data=payload(row); assert len(data)%2==0
    return [int.from_bytes(data[i:i+2],"little",signed=signed) for i in range(0,len(data),2)]

def scalar(row):
    data=payload(row); assert len(data) in (1,2); return int.from_bytes(data,"little")

def status_count(row):
    data=payload(row); assert len(data)==14; return data[13]

def test_epoch_fixture_is_original_derived_and_reproducible():
    data=json.loads(FIXTURE.read_text(encoding="utf-8")); p=data["provenance"]
    assert data["schema"]=="omegas.mp48.lognovo-autocal-epochs.v1"
    assert data["classification"]=="ORIGINAL_DERIVED"
    assert p["sourceRawSha256"]==RAW_SHA and p["sourceZipSha256"]==ZIP_SHA and p["progBaseSha256"]==PROGBASE_SHA
    assert p["extractor"]=="tools/omegas/extract_lognovo_autocal_epochs.py"
    assert p["manualAutoMatchFrame"]=="02 24 04 08 32" and p["manualAutoMatchFrameCount"]==0
    assert p["resetAllFrameCount"]==1 and data["resetAll"]["request"]=="02 24 04 04 2E"
    assert payload(data["resetAll"])==b""

def test_static_configuration_is_stable_before_native_epochs():
    c=json.loads(FIXTURE.read_text(encoding="utf-8"))["staticConfigurationBeforeEpochs"]
    assert u16(c["AXIS"])==[256,512,768,1024,1280,1536,1792,2048,2304,2560,2816,3072,3328,3584,3840,4096,4352,4608,4864,5120,5632,6144,6656,7168,7680,8192,8704,9216,10240,11264]
    assert u16(c["PRESS"],signed=True)==[154,256,307,358,410,461,512,563,614,666,717,768,819,870,922,973,1024,1126]
    assert scalar(c["MAX"])==3 and scalar(c["ENABLE"])==1 and scalar(c["COUNT"])==0
    assert list(payload(c["CAL"]))==[1,3,3,1,3,3,1,3,3,1]

def test_three_epochs_prove_ecu_side_rollover_and_mul_change_without_manual_frame():
    epochs=json.loads(FIXTURE.read_text(encoding="utf-8"))["epochs"]; assert len(epochs)==3
    observed_caps=[]
    for expected,epoch in enumerate(epochs,start=1):
        before=expected-1
        assert epoch["epoch"]==expected and epoch["autoMatchCountBefore"]==before and epoch["autoMatchCountAfter"]==expected
        assert status_count(epoch["statusBefore"])==before and status_count(epoch["statusAfter"])==expected
        assert scalar(epoch["explicitCountAfter"])==expected
        old=u16(epoch["mulBefore"]); new=u16(epoch["mulAfter"]); assert len(old)==len(new)==30 and old!=new
        observed_caps.append(max(abs(n/o-1.0) for o,n in zip(old,new)))
        ct=u16(epoch["gasCurrentBefore"]["time"]); cm=u16(epoch["gasCurrentBefore"]["map"],signed=True); cn=u16(epoch["gasCurrentBefore"]["counter"])
        pt=u16(epoch["gasPreviousAfterRollover"]["time"]); pm=u16(epoch["gasPreviousAfterRollover"]["map"],signed=True)
        at=u16(epoch["gasCurrentAfterRollover"]["time"]); am=u16(epoch["gasCurrentAfterRollover"]["map"],signed=True); an=u16(epoch["gasCurrentAfterRollover"]["counter"])
        assert len(ct)==len(cm)==len(cn)==len(pt)==len(pm)==len(at)==len(am)==len(an)==18
        copied=0
        for i,count in enumerate(cn):
            if count<=0: continue
            copied+=1; assert pt[i]==ct[i] and pm[i]==cm[i]
            assert (at[i],am[i],an[i])!=(ct[i],cm[i],cn[i])
        assert copied>0
    assert abs(observed_caps[0]-0.25)<1e-12
    assert 0.1199<observed_caps[1]<0.1201
    assert 0.0585<observed_caps[2]<0.0587

def test_eeprom_evidence_preserves_multiplier_history_and_call_counter():
    ee=json.loads(FIXTURE.read_text(encoding="utf-8"))["eepromEvidence"]
    pre=ee["preReset"]; base=ee["postResetBaseline"]
    assert scalar(pre["MUL_UPD_CALL_CNTR_EE"])==3 and scalar(base["MUL_UPD_CALL_CNTR_EE"])==0
    assert u16(pre["MUL_ACT_EE"])!=u16(pre["MUL_PREV_EE"])
    assert u16(base["MUL_ACT_EE"])==[16384]*30 and u16(base["MUL_PREV_EE"])==[16384]*30

if __name__=="__main__":
    test_epoch_fixture_is_original_derived_and_reproducible()
    test_static_configuration_is_stable_before_native_epochs()
    test_three_epochs_prove_ecu_side_rollover_and_mul_change_without_manual_frame()
    test_eeprom_evidence_preserves_multiplier_history_and_call_counter()
    print("LOGNOVO_AUTOCAL_EPOCH_FIXTURE=PASS")
