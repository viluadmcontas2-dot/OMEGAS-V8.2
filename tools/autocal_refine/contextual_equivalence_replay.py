#!/usr/bin/env python3
"""Order-independent RPM x MAP -> Tpet equivalence replay.

The state is (RPM, MAP).  Tpet is the response.  Gas observations are compared
with gasoline observations in the same calibration epoch regardless of which
fuel was collected first.  This is an offline scientific replay and never an
ECU writer.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import statistics
from collections import defaultdict
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable, Sequence

from rpm_residual_replay import (
    Observation,
    build_manifest,
    calibration_boundary,
    frames_from_events,
    lineage_of,
    read_events,
    rpm_column,
    stable_observations,
)


MATCH_RPM = 200.0
MATCH_MAP = 0.025
MAX_NEIGHBORS = 8
VISIT_MS = 5_000


@dataclass(frozen=True)
class ContextPair:
    lineage: str
    epoch: int
    gas_session: str
    gas_t: int
    rpm: float
    map_bar: float
    petrol_ref_ms: float
    gas_petrol_ms: float
    petrol_neighbors: int
    petrol_t_min: int
    petrol_t_max: int

    @property
    def ratio(self) -> float:
        return self.gas_petrol_ms / self.petrol_ref_ms

    @property
    def error_log(self) -> float:
        return math.log(self.ratio)

    @property
    def reference_after_gas(self) -> bool:
        return self.petrol_t_min > self.gas_t


def upper_median(values: Iterable[float]) -> float:
    ordered = sorted(values)
    return ordered[len(ordered) // 2]


def collapse_visits(observations: Sequence[Observation]) -> list[Observation]:
    buckets: dict[tuple, list[Observation]] = defaultdict(list)
    for obs in observations:
        key = (
            obs.session, obs.epoch, obs.fuel, obs.t // VISIT_MS,
            round(obs.rpm / 100.0), round(obs.map / 0.01),
        )
        buckets[key].append(obs)
    out = []
    for members in buckets.values():
        first = members[0]
        out.append(Observation(
            int(statistics.median(member.t for member in members)),
            first.fuel,
            statistics.median(member.rpm for member in members),
            statistics.median(member.map for member in members),
            statistics.median(member.petrol_ms for member in members),
            first.epoch,
            first.session,
        ))
    return sorted(out, key=lambda obs: (obs.t, obs.session, obs.fuel))


def symmetric_pairs(observations: Sequence[Observation]) -> list[ContextPair]:
    scopes: dict[tuple[str, int], list[Observation]] = defaultdict(list)
    for obs in observations:
        scopes[(lineage_of(obs.session), obs.epoch)].append(obs)
    pairs = []
    for (lineage, epoch), members in scopes.items():
        petrol = [obs for obs in members if obs.fuel == "GASOLINA"]
        gas = [obs for obs in members if obs.fuel == "GNV"]
        for target in gas:
            candidates = [obs for obs in petrol
                          if abs(obs.rpm - target.rpm) <= MATCH_RPM
                          and abs(obs.map - target.map) <= MATCH_MAP]
            candidates.sort(key=lambda obs:
                            ((obs.rpm - target.rpm) / MATCH_RPM) ** 2
                            + ((obs.map - target.map) / MATCH_MAP) ** 2)
            neighbors = candidates[:MAX_NEIGHBORS]
            if not neighbors:
                continue
            reference = upper_median(obs.petrol_ms for obs in neighbors)
            if reference <= 0 or target.petrol_ms <= 0:
                continue
            pairs.append(ContextPair(
                lineage, epoch, target.session, target.t, target.rpm, target.map,
                reference, target.petrol_ms, len(neighbors),
                min(obs.t for obs in neighbors), max(obs.t for obs in neighbors),
            ))
    return sorted(pairs, key=lambda pair: (pair.lineage, pair.epoch, pair.gas_t))


def map_bin(map_bar: float) -> float:
    return round(map_bar / 0.05) * 0.05


def contextual_groups(pairs: Sequence[ContextPair]) -> list[dict]:
    grouped: dict[tuple, list[ContextPair]] = defaultdict(list)
    for pair in pairs:
        grouped[(pair.lineage, pair.epoch, rpm_column(pair.rpm), map_bin(pair.map_bar))].append(pair)
    rows = []
    for key, members in sorted(grouped.items()):
        lineage, epoch, column, pressure = key
        error = upper_median(pair.error_log for pair in members)
        visits = {(pair.gas_session, pair.gas_t // 30_000) for pair in members}
        others = [other for other_key, values in grouped.items()
                  if other_key[0] == lineage and other_key[1] == epoch
                  and other_key[3] == pressure and other_key[2] != column
                  for other in values]
        other_error = upper_median(pair.error_log for pair in others) if others else None
        residual = error - other_error if other_error is not None else None
        rows.append({
            "lineage": lineage, "epoch": epoch, "rpm_bin": column,
            "rpm_center": (850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500)[column],
            "map_bin": pressure, "pairs": len(members), "visits": len(visits),
            "median_ratio": math.exp(error),
            "median_error_percent": (math.exp(error) - 1.0) * 100.0,
            "other_rpm_pairs": len(others),
            "rpm_local_residual_percent": (math.exp(residual) - 1.0) * 100.0 if residual is not None else "",
        })
    return rows


def compare_context_models(observations: Sequence[Observation]) -> dict:
    """Full-session holdout for MAP-only versus RPM+MAP Tpet surfaces."""
    scopes: dict[tuple, list[Observation]] = defaultdict(list)
    for obs in observations:
        scopes[(lineage_of(obs.session), obs.epoch, obs.fuel)].append(obs)
    errors: dict[tuple[str, str], list[float]] = defaultdict(list)
    session_errors: dict[tuple[str, str], list[float]] = defaultdict(list)
    for (_, _, fuel), members in scopes.items():
        sessions = sorted({obs.session for obs in members})
        if len(sessions) < 2:
            continue
        for held in sessions:
            training = [obs for obs in members if obs.session != held]
            test = [obs for obs in members if obs.session == held]
            local: dict[str, list[float]] = defaultdict(list)
            for query in test:
                common = [obs for obs in training if abs(obs.map - query.map) <= 0.075]
                if len(common) < 8:
                    continue
                rankings = {
                    "MAP": sorted(common, key=lambda obs: abs(obs.map - query.map) / MATCH_MAP),
                    "RPM_MAP": sorted(common, key=lambda obs:
                                      ((obs.map - query.map) / MATCH_MAP) ** 2
                                      + ((obs.rpm - query.rpm) / 300.0) ** 2),
                }
                for model, ranked in rankings.items():
                    neighbors = ranked[:8]
                    prediction = upper_median(obs.petrol_ms for obs in neighbors)
                    error = abs(math.log(prediction / query.petrol_ms))
                    errors[(fuel, model)].append(error)
                    local[model].append(error)
            for model, values in local.items():
                if values:
                    session_errors[(fuel, model)].append(statistics.median(values))
    result = {}
    for fuel in sorted({key[0] for key in errors}):
        result[fuel] = {}
        for model in ("MAP", "RPM_MAP"):
            values = errors[(fuel, model)]
            sessions = session_errors[(fuel, model)]
            if not values:
                continue
            ordered = sorted(values)
            result[fuel][model] = {
                "observations": len(values),
                "heldSessions": len(sessions),
                "median_abs_log_percent": statistics.median(values) * 100.0,
                "mean_abs_log_percent": statistics.fmean(values) * 100.0,
                "p90_abs_log_percent": ordered[min(len(ordered) - 1, int(.9 * len(ordered)))] * 100.0,
                "median_session_percent": statistics.median(sessions) * 100.0,
            }
    return result


def observations_from_files(paths: Sequence[Path]) -> tuple[dict, list[Observation]]:
    manifest = build_manifest(paths)
    events_by_session: dict[str, list[dict]] = defaultdict(list)
    for row in manifest["files"]:
        if row["classification"] not in {"ACCEPTED", "PARTIAL"}:
            continue
        events_by_session[row["sessions"][0]].extend(read_events(Path(row["path"])))
    by_lineage: dict[str, list[str]] = defaultdict(list)
    for session in events_by_session:
        by_lineage[lineage_of(session)].append(session)
    observations = []
    for lineage, sessions in by_lineage.items():
        sessions.sort(key=lambda session: min(
            (int(event.get("recordedAtMs") or 0) for event in events_by_session[session]
             if not event.get("_malformed")), default=0))
        offset = 0
        for session in sessions:
            frames = frames_from_events(events_by_session[session], session)
            shifted = [type(frame)(frame.t, frame.fuel, frame.rpm, frame.map,
                                   frame.petrol_ms, frame.epoch + offset, frame.session)
                       for frame in frames]
            observations.extend(stable_observations(shifted))
            offset += sum(calibration_boundary(event) for event in events_by_session[session]
                          if not event.get("_malformed"))
    return manifest, collapse_visits(observations)


def write_csv(path: Path, rows: Sequence[dict]) -> None:
    if not rows:
        path.write_text("", encoding="utf-8")
        return
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def run(paths: Sequence[Path], output: Path, source_head: str = "") -> dict:
    output.mkdir(parents=True, exist_ok=True)
    manifest, observations = observations_from_files(paths)
    pairs = symmetric_pairs(observations)
    pair_rows = []
    for pair in pairs:
        row = asdict(pair)
        row.update(ratio=pair.ratio, error_log=pair.error_log,
                   error_percent=(pair.ratio - 1.0) * 100.0,
                   reference_after_gas=pair.reference_after_gas)
        pair_rows.append(row)
    groups = contextual_groups(pairs)
    models = compare_context_models(observations)
    write_csv(output / "context-pairs.csv", pair_rows)
    write_csv(output / "rpm-map-groups.csv", groups)
    (output / "context-model-comparison.json").write_text(
        json.dumps(models, indent=2) + "\n", encoding="utf-8")
    summary = {
        "schema": "omegas-contextual-equivalence-rpm-map-v1",
        "sourceHead": source_head,
        "matching": "order-independent; state=(RPM,MAP); response=Tpet",
        "files": len(manifest["files"]),
        "collapsedObservations": len(observations),
        "pairs": len(pairs),
        "pairsUsingOnlyFuturePetrol": sum(pair.reference_after_gas for pair in pairs),
        "groups": len(groups),
        "modelComparison": models,
        "limits": [
            "Pairs never cross a calibration epoch.",
            "Tpet is the response and is not used to select a contextual match.",
            "The result is offline evidence, not an ECU write recommendation.",
        ],
    }
    (output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    return summary


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("inputs", nargs="+", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--head", default="")
    args = parser.parse_args()
    print(json.dumps(run(args.inputs, args.output, args.head), indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
