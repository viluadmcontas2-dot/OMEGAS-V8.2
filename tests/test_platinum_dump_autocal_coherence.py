from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
FIXTURE_PATH = ROOT / "tests/fixtures/platinum-progbase-dump-autocal-v1.json"
FIXTURE = json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))
RESOURCE_DEFAULTS = json.loads(
    (ROOT / "tests/fixtures/progbase-autocal-resource-defaults-v1.json").read_text(encoding="utf-8")
)
SCALE_DFM = json.loads(
    (ROOT / "tests/fixtures/progbase-autocal-scale-dfm-v1.json").read_text(encoding="utf-8")
)
PROTOCOL = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt").read_text(encoding="utf-8")
POINT_DELETE = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/AutoCalPointDeleteProtocol.kt").read_text(encoding="utf-8")
ACTION = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt").read_text(encoding="utf-8")
MONITOR = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt").read_text(encoding="utf-8")
ACQ = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalAcquisition.kt").read_text(encoding="utf-8")
DOC = (ROOT / "docs/platinum/DUMP_AUTOCAL_COHERENCE.md").read_text(encoding="utf-8")


def test_dump_identity_is_bound_to_canonical_progbase():
    identity = FIXTURE["identity"]
    expected = "8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4"
    assert identity["canonical_progbase_sha256"] == expected
    assert identity["dump_bin_sha256"] == expected
    assert identity["dump1_bin_sha256"] == expected
    assert identity["byte_identical"] is True
    assert expected in DOC


def test_platinum_gate_matches_existing_distilled_oracles():
    assert FIXTURE["distilled_oracles"]["resource_defaults"] == "tests/fixtures/progbase-autocal-resource-defaults-v1.json"
    assert FIXTURE["distilled_oracles"]["scale_dfm"] == "tests/fixtures/progbase-autocal-scale-dfm-v1.json"

    selectors = FIXTURE["resources"]["TAutoCalDM"]["runtime_maturity_selectors"]
    canonical = RESOURCE_DEFAULTS["calibrationGrid"]["runtimeMaturitySelectors"]
    assert selectors["zone_boundaries_inclusive"] == canonical["zoneBoundariesInclusive"]
    assert selectors["petrol_low"] == canonical["petrolLow"]
    assert selectors["petrol_normal"] == canonical["petrolNormal"]
    assert selectors["gas_low"] == canonical["gasLow"]
    assert selectors["gas_normal"] == canonical["gasNormal"]

    canonical_sha = FIXTURE["identity"]["canonical_progbase_sha256"]
    assert RESOURCE_DEFAULTS["sources"]["progbase"]["sha256"] == canonical_sha
    assert SCALE_DFM["source"]["progbase"]["sha256"] == canonical_sha


def test_indexed_u8_semantics_match_tautocaldm_dump():
    bindings = FIXTURE["resources"]["TAutoCalDM"]["bindings"]
    assert bindings["VECT_AUTOCAL_U8_1"] == "!AUTOCAL_IDLE_MIN_BUF_UPD_PETR_THD"
    assert bindings["VECT_AUTOCAL_U8_2"] == "MaxAutomatch"
    assert 'val VECT_AUTOCAL_U8_1 = Field("VECT_AUTOCAL_U8_1", 0x0165' in PROTOCOL
    assert 'index = 1' in PROTOCOL.split('val VECT_AUTOCAL_U8_1', 1)[1].split('\n', 1)[0]
    assert 'val MAX_AUTOMATCH = Field("MAX_AUTOMATCH", 0x0165' in PROTOCOL
    assert 'index = 2' in PROTOCOL.split('val MAX_AUTOMATCH', 1)[1].split('\n', 1)[0]
    assert 'val VECT_AUTOCAL_U8_2 = MAX_AUTOMATCH' in PROTOCOL
    assert 'petrolIdleMinUpdateThreshold' in ACQ
    assert '"petrolIdleMinUpdate"' in ACQ
    assert '"maxAutomatch"' in ACQ
    assert '"maturityThresholdsPromoted", maturityThresholdsAvailable' in ACQ
    selectors = FIXTURE["resources"]["TAutoCalDM"]["runtime_maturity_selectors"]
    assert selectors["low_band_max_index_inclusive"] == 5
    assert selectors["zone_boundaries_inclusive"] == [5, 9, 13]
    assert selectors["petrol_low"] == "VECT_AUTOCAL_U8_1"
    assert selectors["petrol_normal"] == "CALIBRATION_VAL_1[2]"
    assert selectors["gas_low"] == "CALIBRATION_VAL_1[5]"
    assert selectors["gas_normal"] == "CALIBRATION_VAL_1[8]"
    assert selectors["direct_disassembly"]["function_va"] == "0x00516F64"
    assert selectors["direct_disassembly"]["static_boundary_bytes_hex"] == "05 09 0D"
    assert 'val petrolNormalThreshold = calibration.getOrNull(2)' in ACQ
    assert 'val gasLowThreshold = calibration.getOrNull(5)' in ACQ
    assert 'val gasNormalThreshold = calibration.getOrNull(8)' in ACQ
    assert '"runtimeSelectorBoundaryInclusive", LOW_BAND_MAX_INDEX' in ACQ


def test_normal_and_ee_surfaces_stay_distinct():
    ee = FIXTURE["resources"]["TAutoCalDM_EE"]
    assert ee["distinct_surface"] is True
    assert "MUL_ACT_EE" in ee["proven_fields"]
    assert "VECT_AUTOCAL_EE" in ee["proven_fields"]
    assert 'val MUL_ACT = Field("MUL_ACT", 0x0161' in PROTOCOL
    assert 'val VECT_AUTOCAL_EE = Field("VECT_AUTOCAL_EE", 0x0164' in PROTOCOL
    assert 'writeIndexedU16(MUL_ACT.address, index, 0x4000)' in PROTOCOL
    assert "MUL_ACT_EE" not in ACTION
    assert "MUL_ACT_EE" not in MONITOR
    assert "VECT_AUTOCAL_EE divergente após Reset K" not in ACTION


def test_dump_proven_acquisition_surfaces_are_preserved():
    fields = set(FIXTURE["resources"]["TAutoCalDM"]["proven_fields"])
    for token in (
        "MNFLD_PRESS_THD",
        "PETR_INJ_TBUF",
        "MNFLD_PRESS_BUF",
        "NUM_BUF_UPD_PETR",
        "PETR_INJ_TBUF_GAS_PREV",
        "MNFLD_PRESS_BUF_GAS_PREV",
        "PETR_INJ_TBUF_GAS",
        "MNFLD_PRESS_BUF_GAS",
        "NUM_BUF_UPD_GAS",
        "MUL_ACT",
        "ACQUIRED_ZONES_PETROL",
        "ACQUIRED_ZONES_GAS",
        "PETR_MNFLD_PRESS_RV",
        "GAS_MNFLD_PRESS_RV",
    ):
        assert token in fields
        assert token in PROTOCOL or token in ACQ
    assert "PETROL_POINT_2DELETE" in fields
    assert "GAS_POINT_2DELETE" in fields
    assert "PETROL_DELETE_ADDRESS = 0x016D" in POINT_DELETE
    assert "GAS_DELETE_ADDRESS = 0x016E" in POINT_DELETE


def test_archived_gas_buffers_do_not_invent_previous_counter():
    fields = set(FIXTURE["resources"]["TAutoCalDM"]["proven_fields"])
    assert "PETR_INJ_TBUF_GAS_PREV" in fields
    assert "MNFLD_PRESS_BUF_GAS_PREV" in fields
    assert "NUM_BUF_UPD_GAS_PREV" not in fields
    assert 'Source("GNV_ANTERIOR", "PETR_INJ_TBUF_GAS_PREV", "MNFLD_PRESS_BUF_GAS_PREV", null, previous = true)' in ACQ


def test_native_automatch_remains_observational_on_host():
    assert 'nativeAutoMatchInsideEcu' in MONITOR
    assert '"appAutomaticWrite", false' in MONITOR
    assert "KFactorManager" not in MONITOR
    assert "KWriteManager" not in MONITOR
    assert "Mp48WorkClass.MANUAL_WRITE" not in MONITOR
    assert "NATIVE_AUTOMATCH_HAS_NO_HOST_AUTOMATIC_K_WRITE" in FIXTURE["release_invariants"]


if __name__ == "__main__":
    test_dump_identity_is_bound_to_canonical_progbase()
    test_platinum_gate_matches_existing_distilled_oracles()
    test_indexed_u8_semantics_match_tautocaldm_dump()
    test_normal_and_ee_surfaces_stay_distinct()
    test_dump_proven_acquisition_surfaces_are_preserved()
    test_archived_gas_buffers_do_not_invent_previous_counter()
    test_native_automatch_remains_observational_on_host()
    print("PLATINUM_DUMP_AUTOCAL_COHERENCE=PASS")
