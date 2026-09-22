import json
from pathlib import Path

FIXTURE = Path("tests/fixtures/portmon-lognovo-autocal-reference-v1.json")
RAW_SHA = "43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64"
ZIP_SHA = "6879fa2a7931d22c207cd7fa47dffb59e1df0fe1de216e34e3f11e0c08cc1c17"
PROGBASE_SHA = "8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4"

EXPECTED = {
    "PETR_INJ_TBP": {"request": "29 4B 01 75", "count": 30, "signed": False, "values": [256,512,768,1024,1280,1536,1792,2048,2304,2560,2816,3072,3328,3584,3840,4096,4352,4608,4864,5120,5632,6144,6656,7168,7680,8192,8704,9216,10240,11264]},
    "MNFLD_PRESS_THD": {"request": "29 4C 01 76", "count": 18, "signed": True, "values": [154,256,307,358,410,461,512,563,614,666,717,768,819,870,922,973,1024,1126]},
    "MUL_ACT": {"request": "29 61 01 8B", "count": 30, "signed": False, "values": [13745,13742,13737,13238,12908,12979,13431,14208,18219,21908,21715,21312,20107,19097,19541,20569,20481,21339,21358,20646,16934,16171,16824,16990,17809,17809,17809,17809,17809,17809]},
    "PETR_MNFLD_PRESS_RV": {"request": "29 8D 01 B7", "count": 30, "signed": True, "values": [10,41,104,167,230,308,342,381,433,494,562,618,683,735,763,788,825,844,859,876,930,981,1032,1083,1134,1185,1236,1287,1389,1491]},
    "GAS_MNFLD_PRESS_RV": {"request": "29 8E 01 B8", "count": 30, "signed": True, "values": [90,126,162,198,234,264,308,394,428,505,554,627,687,737,802,823,836,872,901,913,945,972,999,1026,1053,1080,1107,1134,1188,1242]},
}

def _decode_u16(payload: bytes, signed: bool) -> list[int]:
    return [int.from_bytes(payload[i:i+2], "little", signed=signed) for i in range(0, len(payload), 2)]

def test_lognovo_reference_fixture_has_original_provenance_and_exact_bytes():
    data = json.loads(FIXTURE.read_text(encoding="utf-8"))
    assert data["classification"] == "ORIGINAL_DERIVED"
    provenance = data["provenance"]
    assert provenance["sourceRawSha256"] == RAW_SHA
    assert provenance["sourceZipSha256"] == ZIP_SHA
    assert provenance["progBaseSha256"] == PROGBASE_SHA
    assert "Never derived from OMEGAS output" in provenance["scientificUse"]
    rows = {row["key"]: row for row in data["transactions"]}
    assert set(rows) == set(EXPECTED)
    for key, expected in EXPECTED.items():
        row = rows[key]
        assert row["request"] == expected["request"]
        request = bytes.fromhex(row["request"])
        response = bytes.fromhex(row["response"])
        assert response[:len(request)] == request
        assert response[len(request)] == 0x53
        payload_size = response[len(request)+1]
        payload_start = len(request)+2
        payload_end = payload_start+payload_size
        assert len(response) == payload_end+1
        payload = response[payload_start:payload_end]
        assert payload_size == expected["count"]*2
        assert _decode_u16(payload, expected["signed"]) == expected["values"]

def test_original_lognovo_reference_is_not_the_test_only_shifted_fixture():
    data = json.loads(FIXTURE.read_text(encoding="utf-8"))
    rows = {row["key"]: row for row in data["transactions"]}
    assert rows["PETR_MNFLD_PRESS_RV"]["response"] != rows["GAS_MNFLD_PRESS_RV"]["response"]
    shifted = json.loads(Path("fixtures/autocal/autocal_snapshot_shifted_equivalence.json").read_text(encoding="utf-8"))
    assert shifted["nativeFirmwareExact"] is False
    assert "Test/visual evidence only" in shifted["description"]


def test_android_render_fixture_provenance_is_enforced_end_to_end():
    workflow = Path(".github/workflows/verde-android-render-evidence.yml").read_text(encoding="utf-8")
    assert "tests/fixtures/portmon-lognovo-autocal-reference-v1.json" in workflow

    shifted = json.loads(Path("fixtures/autocal/autocal_snapshot_shifted_equivalence.json").read_text(encoding="utf-8"))
    assert shifted["classification"] == "SYNTHETIC_NON_SCIENTIFIC"
    assert shifted["scientificUse"] == "VISUAL_ONLY_NON_SCIENTIFIC"

    render_test = Path("app/src/androidTest/java/com/omegas/prohub/DashboardLevelsRenderTest.kt").read_text(encoding="utf-8")
    assert 'root.getString("classification") == "SYNTHETIC_NON_SCIENTIFIC"' in render_test
    assert 'root.getString("scientificUse") == "VISUAL_ONLY_NON_SCIENTIFIC"' in render_test
    assert 'saveEvidence("autocal-equivalence-shifted", dom, scenario, provenance)' in render_test


if __name__ == "__main__":
    test_lognovo_reference_fixture_has_original_provenance_and_exact_bytes()
    test_original_lognovo_reference_is_not_the_test_only_shifted_fixture()
    test_android_render_fixture_provenance_is_enforced_end_to_end()
