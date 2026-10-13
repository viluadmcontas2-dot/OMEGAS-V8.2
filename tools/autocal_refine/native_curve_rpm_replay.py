#!/usr/bin/env python3
"""Reconstruct RPM context around native AutoCal curve acquisitions.

This is evidence extraction, not a calibration algorithm.  Rows backed by the
app's native correlation event are kept distinct from conservative interval
matches inferred from the raw snapshots and telemetry.
"""

from __future__ import annotations

import argparse
import csv
import json
import statistics
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable, Sequence


BOUNDARY_TYPES = {
    "autocal_native_calibration_epoch",
    "k_batch_confirmed",
    "k_factor_batch_confirmed",
}
ACTION_BOUNDARIES = {"RESET_GAS", "RESET_PETROL", "RESET_ALL", "MANUAL_AUTOMATCH", "AUTO_MATCH"}
FUEL_FIELDS = {
    "GASOLINA": ("NUM_BUF_UPD_PETR", "PETR_INJ_TBUF", "MNFLD_PRESS_BUF"),
    "GNV": ("NUM_BUF_UPD_GAS", "PETR_INJ_TBUF_GAS", "MNFLD_PRESS_BUF_GAS"),
}


@dataclass(frozen=True)
class Acquisition:
    session: str
    epoch: int
    t: int
    fuel: str
    band: int
    previous_counter: int
    counter: int
    map_bar: float
    petrol_ms: float
    rpm: float
    source: str
    confidence: float | None
    matched_frames: int


def snapshot_fields(data: dict) -> dict[str, list]:
    result: dict[str, list] = {}
    for item in data.get("fields") or []:
        if item.get("status") != "VALID" or not isinstance(item.get("physicalValues"), list):
            continue
        result[str(item.get("key"))] = item["physicalValues"]
    return result


def is_boundary(event: dict) -> bool:
    if event.get("type") in BOUNDARY_TYPES:
        return True
    if event.get("type") == "autocal_native_action":
        action = str((event.get("data") or {}).get("action") or "").upper()
        return action in ACTION_BOUNDARIES
    return False


def _number(value, default=0.0) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return float(default)


def _explicit_rows(data: dict, session: str, epoch: int, t: int) -> list[Acquisition]:
    rows = []
    for event in data.get("nativeMaturityEvents") or []:
        fuel = str(event.get("fuel") or "").upper()
        if fuel not in FUEL_FIELDS or event.get("rpm") is None:
            continue
        rows.append(Acquisition(
            session=session,
            epoch=epoch,
            t=t,
            fuel=fuel,
            band=int(event.get("bandIndex") or 0),
            previous_counter=int(event.get("previousCounter") or 0),
            counter=int(event.get("counter") or 0),
            map_bar=_number(event.get("mapBar")),
            petrol_ms=_number(event.get("timeMs")),
            rpm=_number(event.get("rpm")),
            source="NATIVE_CORRELATION",
            confidence=_number(event.get("correlationConfidence")) if event.get("correlationConfidence") is not None else None,
            matched_frames=int(event.get("matchedTelemetryFrames") or 0),
        ))
    return rows


def _changed_bands(previous: dict[str, list], current: dict[str, list], fuel: str):
    count_key, ms_key, map_key = FUEL_FIELDS[fuel]
    old_counts = previous.get(count_key) or []
    new_counts = current.get(count_key) or []
    old_ms = previous.get(ms_key) or []
    new_ms = current.get(ms_key) or []
    old_map = previous.get(map_key) or []
    new_map = current.get(map_key) or []
    size = min(len(new_counts), len(new_ms), len(new_map))
    for band in range(size):
        prior_count = int(old_counts[band]) if band < len(old_counts) else 0
        prior_ms = _number(old_ms[band]) if band < len(old_ms) else 0.0
        prior_map = _number(old_map[band]) if band < len(old_map) else 0.0
        count = int(new_counts[band])
        ms = _number(new_ms[band])
        map_bar = _number(new_map[band])
        changed = count > prior_count or (count > 0 and (ms != prior_ms or map_bar != prior_map))
        if changed and count >= prior_count and ms > 0 and map_bar > 0:
            yield band, prior_count, count, map_bar, ms


def reconstruct(events: Iterable[dict], session: str) -> list[Acquisition]:
    ordered = sorted(events, key=lambda e: (int(e.get("recordedAtMs") or 0), int(e.get("sequence") or 0)))
    epoch = 0
    telemetry: list[dict] = []
    previous_fields: dict[str, list] | None = None
    previous_t: int | None = None
    rows: list[Acquisition] = []
    for event in ordered:
        t = int(event.get("recordedAtMs") or 0)
        if is_boundary(event):
            epoch += 1
            telemetry.clear()
            previous_fields = None
            previous_t = None
            continue
        if event.get("type") == "telemetry":
            data = event.get("data") or {}
            if data.get("plausible") is not False:
                telemetry.append({"t": t, **data})
            continue
        if event.get("type") != "autocal_native_snapshot":
            continue
        data = event.get("data") or {}
        fields = snapshot_fields(data)
        explicit = _explicit_rows(data, session, epoch, t)
        rows.extend(explicit)
        explicit_keys = {(row.fuel, row.band) for row in explicit}
        if previous_fields is not None and previous_t is not None:
            interval = [frame for frame in telemetry if previous_t < int(frame["t"]) <= t]
            for fuel in FUEL_FIELDS:
                for band, old_count, count, map_bar, ms in _changed_bands(previous_fields, fields, fuel):
                    if (fuel, band) in explicit_keys:
                        continue
                    matches = []
                    for frame in interval:
                        if str(frame.get("fuel") or "").upper() != fuel:
                            continue
                        if abs(_number(frame.get("load_bar")) - map_bar) > 0.04:
                            continue
                        if abs(_number(frame.get("petrol_ms")) - ms) > 1.0:
                            continue
                        if _number(frame.get("rpm")) <= 0:
                            continue
                        matches.append(frame)
                    if not matches:
                        continue
                    rows.append(Acquisition(
                        session=session,
                        epoch=epoch,
                        t=t,
                        fuel=fuel,
                        band=band,
                        previous_counter=old_count,
                        counter=count,
                        map_bar=map_bar,
                        petrol_ms=ms,
                        rpm=statistics.median(_number(frame.get("rpm")) for frame in matches),
                        source="INFERRED_INTERVAL_MATCH",
                        confidence=None,
                        matched_frames=len(matches),
                    ))
        previous_fields = fields
        previous_t = t
        telemetry = [frame for frame in telemetry if int(frame["t"]) > t]
    return rows


def read_jsonl(path: Path) -> list[dict]:
    events = []
    with path.open(encoding="utf-8", errors="replace") as handle:
        for line in handle:
            try:
                value = json.loads(line)
            except json.JSONDecodeError:
                continue
            if isinstance(value, dict):
                events.append(value)
    return events


def session_id(path: Path) -> str:
    sidecar = path.parent / "session_summary.json"
    if sidecar.is_file():
        try:
            return str(json.loads(sidecar.read_text(encoding="utf-8")).get("sessionId") or path.parent.name)
        except (OSError, json.JSONDecodeError):
            pass
    return path.parent.name


def write_outputs(rows: Sequence[Acquisition], output: Path) -> dict:
    output.mkdir(parents=True, exist_ok=True)
    columns = list(Acquisition.__dataclass_fields__)
    with (output / "native-acquisition-rpm.csv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns)
        writer.writeheader()
        writer.writerows(asdict(row) for row in rows)
    summary = {
        "schema": "omegas-native-autocal-rpm-context-v1",
        "rows": len(rows),
        "sessions": len({row.session for row in rows}),
        "epochs": len({(row.session, row.epoch) for row in rows}),
        "nativeCorrelations": sum(row.source == "NATIVE_CORRELATION" for row in rows),
        "inferredIntervalMatches": sum(row.source == "INFERRED_INTERVAL_MATCH" for row in rows),
        "warning": "Association is observational evidence; it is not an authorized calibration recommendation.",
    }
    (output / "native-acquisition-summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    return summary


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("events", nargs="+", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    rows = []
    for path in args.events:
        rows.extend(reconstruct(read_jsonl(path), session_id(path)))
    print(json.dumps(write_outputs(rows, args.output), indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
