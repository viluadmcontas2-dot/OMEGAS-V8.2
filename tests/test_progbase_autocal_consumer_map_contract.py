#!/usr/bin/env python3
import json
import statistics
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAP_PATH = ROOT / "tests/fixtures/progbase-autocal-consumer-map-v1.json"
PORTMON_PATH = ROOT / "tests/fixtures/portmon-autocal-cycle-v1.json"

consumer_map = json.loads(MAP_PATH.read_text(encoding="utf-8"))
portmon = json.loads(PORTMON_PATH.read_text(encoding="utf-8"))

assert consumer_map["schema"] == "omegas.progbase.autocal-consumer-map.v1"
assert consumer_map["source"]["progbase"]["sha256"] == "8A2D297C8C21FF3B4F7A47F7FE64593B0FEC9014DD938BD91022DC0C68AC36F4"
assert consumer_map["source"]["portmon"]["source_raw_sha256"] == portmon["sourceRawSha256"]
assert consumer_map["source"]["portmon"]["source_zip_sha256"] == portmon["sourceZipSha256"]

tx = portmon["transactions"]
by_request = {}
for item in tx:
    by_request.setdefault(item["request"], []).append(item)

live = by_request["48 01 49"]
assert len(live) >= 100
gaps = [b["at_ms"] - a["at_ms"] for a, b in zip(live, live[1:])]
live_median = statistics.median(gaps)
assert 30.0 <= live_median <= 70.0, live_median
assert abs(live_median - consumer_map["live_telemetry"]["compact_fixture_median_gap_ms"]) < 0.01

for item in live:
    raw = bytes.fromhex(item["response"])
    assert len(raw) == consumer_map["live_telemetry"]["response_total_bytes"]
    assert raw[:3] == bytes.fromhex("48 01 49")
    assert raw[3] == 0x53
    assert raw[4] == 0x22
    payload = raw[5:-1]
    assert len(payload) == consumer_map["live_telemetry"]["payload_bytes"]

fields = {row["semantic"]: row for row in consumer_map["live_telemetry"]["payload_fields"]}
assert fields["petrol_injection_raw"]["offset"] == 8
assert fields["levels_raw"]["offset"] == 13
assert fields["map_raw"]["offset"] == 17
assert consumer_map["live_telemetry"]["runpoint"]["chart"] == "ChartData"
assert consumer_map["levels"]["live_raw"]["conversion"] == "NONE"

required_consumers = {
    "PetrolCurve", "GasCurve", "CurrentBand", "AcqusitionAreas",
    "PollingPetrol", "PollingGas",
}
assert required_consumers <= set(consumer_map["consumers"])

def median_gap(items):
    return statistics.median(
        b["at_ms"] - a["at_ms"] for a, b in zip(items, items[1:])
    )

for row in consumer_map["autocal_dm"]:
    request = row.get("request")
    if not request:
        continue
    assert request in by_request, (row["name"], request)
    samples = by_request[request]
    lengths = {len(bytes.fromhex(item["response"])) for item in samples}
    assert lengths == {row["response_total_bytes"]}, (row["name"], lengths)
    if "median_gap_ms" in row:
        observed = median_gap(samples)
        assert abs(observed - row["median_gap_ms"]) < 0.01, (row["name"], observed)

for row in consumer_map["autocal_dm"]:
    request = row.get("request")
    if not request:
        continue
    body = bytes.fromhex(request)
    assert sum(body[:-1]) & 0xFF == body[-1], (row["name"], request)

slow = [
    row["median_gap_ms"] for row in consumer_map["autocal_dm"]
    if row.get("median_gap_ms") and 0x015B <= row["serial_code"] <= 0x0170
]
assert all(1500.0 <= gap <= 2600.0 for gap in slow), slow

rv = {
    row["name"]: row["median_gap_ms"] for row in consumer_map["autocal_dm"]
    if row["name"] in {"PETR_MNFLD_PRESS_RV", "GAS_MNFLD_PRESS_RV"}
}
assert 3000.0 <= rv["PETR_MNFLD_PRESS_RV"] <= 5000.0
assert 3000.0 <= rv["GAS_MNFLD_PRESS_RV"] <= 5000.0

assert consumer_map["levels"]["calibration"]["word_layout"]["low_byte"] == "learned min reference"
assert consumer_map["levels"]["calibration"]["word_layout"]["high_byte"] == "learned max reference"
assert "percent" in consumer_map["levels"]["calibration"]["warning"].lower()

print(
    "PROGBASE_AUTOCAL_CONSUMER_MAP=PASS "
    f"live_median_ms={live_median:.3f} "
    f"slow_rows={len(slow)} "
    f"rv_petrol_ms={rv['PETR_MNFLD_PRESS_RV']:.3f} "
    f"rv_gas_ms={rv['GAS_MNFLD_PRESS_RV']:.3f}"
)
