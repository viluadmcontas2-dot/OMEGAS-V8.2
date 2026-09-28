#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ORACLE = json.loads((ROOT / "tests/fixtures/progbase-autocal-resource-defaults-v1.json").read_text(encoding="utf-8"))

assert ORACLE["classification"] == "ORIGINAL_DERIVED"
assert ORACLE["sources"]["progbase"]["sha256"] == "8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4"
assert ORACLE["sources"]["resources"]["sha256"] == "8c6b629cf1d69e13074ea8fd3203d48343d002a2ffc5503f20b6ce10ec564b15"

crypt = ORACLE["mltpDecrypt"]
assert crypt["resourceType"] == 0x100
assert crypt["resourceCount"] == 141
assert crypt["encryptedMagic"] == "ENCRYPTED_MLTP_FILE"
assert crypt["decryptedMagic"] == "DECRYPTED_MLTP_FILE"
assert crypt["key"] == "AEBXLANDIRENZO03"
assert crypt["progbaseDecryptVa"] == "0x0042CC48"
assert "does not classify them as ECU firmware" in crypt["note"]

embedded = ORACLE["autocalResources"]
assert embedded["ids"] == list(range(2305, 2313))
assert embedded["shared"]["maxAutomatch"] == 3
assert embedded["shared"]["maxRpmForAutocal"] == 3000
assert embedded["shared"]["calibrationVal1"] == [8, 4, 3, 20, 1, 6, 8, 3, 3, 7]
assert embedded["shared"]["petrolAxisMs"] == [1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5, 5.5, 6, 7, 8, 9, 10, 12, 14, 16, 18]

rows = {row["row"]: row for row in ORACLE["calibrationGrid"]["rowOrder"]}
assert rows[0]["source"] == "VECT_AUTOCAL_U8_0"
assert rows[1]["source"] == "VECT_AUTOCAL_U8_1"
assert rows[4]["source"] == "CALIBRATION_VAL_1[5]"
assert rows[7]["source"] == "CALIBRATION_VAL_1[2]"
assert rows[10]["source"] == "CALIBRATION_VAL_1[8]"

selectors = ORACLE["calibrationGrid"]["runtimeMaturitySelectors"]
assert selectors["zoneBoundariesInclusive"] == [5, 9, 13]
assert selectors["petrolLow"] == "VECT_AUTOCAL_U8_1"
assert selectors["petrolNormal"] == "CALIBRATION_VAL_1[2]"
assert selectors["gasLow"] == "CALIBRATION_VAL_1[5]"
assert selectors["gasNormal"] == "CALIBRATION_VAL_1[8]"

boundary = ORACLE["epistemicBoundary"]
assert "exact ECU AutoMatch arithmetic" in boundary["doesNotProve"]
assert any("18-point" in claim for claim in boundary["doesNotProve"])

print("PROGBASE_AUTOCAL_RESOURCE_DEFAULTS=PASS")
