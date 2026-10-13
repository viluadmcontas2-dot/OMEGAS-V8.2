#!/usr/bin/env python3
"""Causal replay of the Obsidian RPM-local residual.

This tool mirrors the observational core of EquivalenceLedger and the exact
classification contract of EquivalenceAttribution.  It is deliberately offline:
it has no Android, USB or ECU writer dependency.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
from collections import defaultdict, deque
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable, Iterator, Sequence


STABLE_WINDOW_MS = 1_200
STABLE_RPM_SPREAD = 150.0
STABLE_MAP_SPREAD = 0.03
MATCH_RPM = 150.0
MATCH_MAP = 0.02
MIN_PETROL_MS = 1.0
PETROL_FREEZE_SAMPLES = 8
CELL_CAP = 30

RPM_BINS = (850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500)
BANDS = ((3.0, 4.5), (4.5, 6.0), (6.0, 7.5), (7.5, 9.0), (9.0, 12.0))
ENTER_LOG = math.log(1.05)
EXIT_LOG = math.log(1.04)
MIN_PAIRS = 8
MIN_VISITS = 3
VISIT_SPREAD_LOG = math.log(1.03)
MIN_OTHER_PAIRS = 8
SAME_T_MS = 0.5
VISIT_GAP_MS = 30_000

CALIBRATION_EVENTS = {
    "k_batch_confirmed",
    "k_factor_batch_confirmed",
    "autocal_native_calibration_epoch",
}
CALIBRATION_ACTIONS = {"RESET_GAS", "RESET_PETROL", "RESET_ALL", "MANUAL_AUTOMATCH", "AUTO_MATCH"}


@dataclass(frozen=True)
class Frame:
    t: int
    fuel: str
    rpm: float
    map: float
    petrol_ms: float
    epoch: int
    session: str = ""


@dataclass(frozen=True)
class Observation:
    t: int
    fuel: str
    rpm: float
    map: float
    petrol_ms: float
    epoch: int
    session: str = ""


@dataclass(frozen=True)
class Pair:
    session: str
    epoch: int
    t: int
    rpm: float
    map: float
    petrol_ref_ms: float
    gas_petrol_ms: float

    @property
    def error_log(self) -> float:
        return math.log(self.gas_petrol_ms / self.petrol_ref_ms)


@dataclass(frozen=True)
class Group:
    session: str
    epoch: int
    column: int
    band: int
    pairs: int
    visits: int
    petrol_ms: float
    gas_ms: float
    error_log: float
    others: int
    others_log: float | None
    residual_log: float | None
    local: bool
    reason: str

    @property
    def rpm(self) -> int:
        return RPM_BINS[self.column]


def upper_median(values: Iterable[float]) -> float | None:
    ordered = sorted(values)
    return ordered[len(ordered) // 2] if ordered else None


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def read_events(path: Path) -> Iterator[dict]:
    with path.open("r", encoding="utf-8", errors="replace") as handle:
        for line_number, line in enumerate(handle, 1):
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                yield {"_malformed": True, "_line": line_number}
                continue
            if isinstance(event, dict):
                yield event
            else:
                yield {"_malformed": True, "_line": line_number}


def event_session_id(event: dict) -> str | None:
    data = event.get("data") or {}
    recorder = data.get("session_recorder") or {}
    session = recorder.get("sessionId")
    if session:
        return str(session)
    summary = recorder.get("semanticSummary") or {}
    if summary.get("sessionId"):
        return str(summary["sessionId"])
    return None


def inspect_file(path: Path) -> dict:
    sessions: set[str] = set()
    malformed = 0
    event_count = 0
    dropped = 0
    gaps = 0
    previous_sequence: int | None = None
    for event in read_events(path):
        if event.get("_malformed"):
            malformed += 1
            continue
        event_count += 1
        session = event_session_id(event)
        if session:
            sessions.add(session)
        recorder = (event.get("data") or {}).get("session_recorder") or {}
        try:
            dropped = max(dropped, int(recorder.get("droppedEvents") or 0))
        except (TypeError, ValueError):
            pass
        sequence = event.get("sequence")
        if isinstance(sequence, int):
            if previous_sequence is not None and sequence > previous_sequence + 1:
                gaps += sequence - previous_sequence - 1
            previous_sequence = sequence
    identity_source = "events"
    if not sessions:
        sidecar = path.parent / "session_summary.json"
        if sidecar.is_file():
            try:
                summary = json.loads(sidecar.read_text(encoding="utf-8"))
                if summary.get("sessionId"):
                    sessions.add(str(summary["sessionId"]))
                    identity_source = sidecar.name
            except (OSError, json.JSONDecodeError):
                pass
    return {
        "path": str(path),
        "bytes": path.stat().st_size,
        "sha256": sha256_file(path),
        "sessions": sorted(sessions),
        "sessionIdentitySource": identity_source if sessions else None,
        "events": event_count,
        "malformedLines": malformed,
        "sequenceGaps": gaps,
        "droppedEvents": dropped,
    }


def build_manifest(paths: Sequence[Path]) -> dict:
    rows = [inspect_file(Path(path)) for path in sorted(map(Path, paths), key=lambda p: str(p))]
    canonical_by_hash: dict[str, str] = {}
    for row in rows:
        if row["sha256"] in canonical_by_hash:
            row["classification"] = "DUPLICATE"
            row["duplicateOf"] = canonical_by_hash[row["sha256"]]
        elif len(row["sessions"]) != 1:
            row["classification"] = "REJECTED"
            row["reason"] = "SESSION_ID_AUSENTE" if not row["sessions"] else "MULTIPLAS_SESSOES_NO_ARQUIVO"
            canonical_by_hash[row["sha256"]] = row["path"]
        elif row["malformedLines"] or row["sequenceGaps"] or row["droppedEvents"]:
            row["classification"] = "PARTIAL"
            row["reason"] = "AQUISICAO_PARCIAL"
            canonical_by_hash[row["sha256"]] = row["path"]
        else:
            row["classification"] = "ACCEPTED"
            row["reason"] = "INTEGRO"
            canonical_by_hash[row["sha256"]] = row["path"]
    counts = defaultdict(int)
    for row in rows:
        counts[row["classification"]] += 1
    return {
        "schema": "omegas-obsidian-rpm-residual-corpus-v1",
        "files": rows,
        "counts": dict(sorted(counts.items())),
    }


def calibration_boundary(event: dict) -> bool:
    if event.get("type") in CALIBRATION_EVENTS:
        return True
    if event.get("type") == "autocal_native_action":
        action = str((event.get("data") or {}).get("action") or "").upper()
        return action in CALIBRATION_ACTIONS
    return False


def frames_from_events(events: Iterable[dict], session: str) -> list[Frame]:
    epoch = 0
    frames: list[Frame] = []
    seen: set[tuple] = set()
    for event in sorted((e for e in events if not e.get("_malformed")), key=lambda e: (int(e.get("recordedAtMs") or 0), int(e.get("sequence") or 0))):
        if calibration_boundary(event):
            epoch += 1
            continue
        if event.get("type") != "telemetry":
            continue
        data = event.get("data") or {}
        try:
            t = int(event.get("recordedAtMs") or 0)
            fuel = str(data.get("fuel") or "").upper()
            rpm = float(data.get("rpm") or 0.0)
            map_bar = float(data.get("load_bar") or 0.0)
            petrol_ms = float(data.get("petrol_ms") or 0.0)
        except (TypeError, ValueError):
            continue
        if data.get("plausible") is False or fuel not in {"GASOLINA", "GNV"}:
            continue
        key = (t, fuel, rpm, map_bar, petrol_ms, epoch)
        if key in seen:
            continue
        seen.add(key)
        frames.append(Frame(t, fuel, rpm, map_bar, petrol_ms, epoch, session))
    return frames


def stable_observations(frames: Sequence[Frame]) -> list[Observation]:
    windows: deque[Frame] = deque(maxlen=3)
    out: list[Observation] = []
    for frame in sorted(frames, key=lambda f: f.t):
        if frame.fuel not in {"GASOLINA", "GNV"} or frame.rpm <= 0 or frame.map <= 0 or frame.petrol_ms < MIN_PETROL_MS:
            windows.clear()
            continue
        if windows and (windows[-1].fuel != frame.fuel or windows[-1].epoch != frame.epoch or windows[-1].session != frame.session):
            windows.clear()
        windows.append(frame)
        if len(windows) < 3:
            continue
        values = list(windows)
        if values[-1].t - values[0].t > STABLE_WINDOW_MS:
            continue
        if max(v.rpm for v in values) - min(v.rpm for v in values) > STABLE_RPM_SPREAD:
            continue
        if max(v.map for v in values) - min(v.map for v in values) > STABLE_MAP_SPREAD:
            continue
        out.append(Observation(
            values[1].t,
            frame.fuel,
            sum(v.rpm for v in values) / 3.0,
            sum(v.map for v in values) / 3.0,
            sum(v.petrol_ms for v in values) / 3.0,
            frame.epoch,
            frame.session,
        ))
    return out


def cell_key(obs: Observation) -> tuple[int, int]:
    return math.floor(int(obs.rpm) / MATCH_RPM), math.floor(int(obs.map * 1000) / int(MATCH_MAP * 1000))


def lineage_of(session: str) -> str:
    return session.rsplit("_", 1)[-1] if session else ""


def online_pairs(observations: Sequence[Observation], carry_across_sessions: bool = False) -> list[Pair]:
    petrol: dict[tuple[str, int, int, int], list[Observation]] = defaultdict(list)
    gas_cells: dict[tuple[str, int, int, int], deque[Observation]] = defaultdict(lambda: deque(maxlen=CELL_CAP))
    out: list[Pair] = []
    for obs in sorted(observations, key=lambda value: value.t):
        r, m = cell_key(obs)
        identity = lineage_of(obs.session) if carry_across_sessions else obs.session
        key = (identity, obs.epoch, r, m)
        if obs.fuel == "GASOLINA":
            if len(petrol[key]) < PETROL_FREEZE_SAMPLES:
                petrol[key].append(obs)
            continue
        gas_cells[key].append(obs)
        matches: list[float] = []
        for rr in range(r - 1, r + 2):
            for mm in range(m - 1, m + 2):
                for candidate in petrol.get((identity, obs.epoch, rr, mm), ()):
                    if candidate.t < obs.t and abs(candidate.rpm - obs.rpm) <= MATCH_RPM and abs(candidate.map - obs.map) <= MATCH_MAP:
                        matches.append(candidate.petrol_ms)
        if len(matches) >= 2:
            ref = upper_median(matches)
            out.append(Pair(obs.session, obs.epoch, obs.t, obs.rpm, obs.map, ref, obs.petrol_ms))
    return out


def rpm_column(rpm: float) -> int:
    best = 0
    for index in range(1, len(RPM_BINS)):
        if abs(rpm - RPM_BINS[index]) < abs(rpm - RPM_BINS[best]):
            best = index
    return best


def petrol_band(petrol_ms: float) -> int:
    return next((index for index, (lo, hi) in enumerate(BANDS) if lo <= petrol_ms < hi), -1)


def classify(
    pairs: Sequence[Pair],
    latched: set[tuple[int, int]] | None = None,
    scope: str | None = None,
) -> list[Group]:
    latched = latched or set()
    members = []
    for pair in pairs:
        if pair.petrol_ref_ms <= 0 or pair.gas_petrol_ms <= 0:
            continue
        column = rpm_column(pair.rpm)
        band = petrol_band(pair.petrol_ref_ms)
        key = (column, band) if band >= 0 else None
        members.append((pair, column, key, pair.error_log))
    grouped: dict[tuple[int, int], list[tuple]] = defaultdict(list)
    for member in members:
        if member[2] is not None:
            grouped[member[2]].append(member)

    stats = []
    for key, group in grouped.items():
        ordered = sorted(group, key=lambda value: (value[0].session, value[0].epoch, value[0].t))
        visits: list[list[float]] = []
        previous: Pair | None = None
        for member in ordered:
            pair = member[0]
            if previous is None or pair.session != previous.session or pair.epoch != previous.epoch or pair.t - previous.t > VISIT_GAP_MS:
                visits.append([])
            visits[-1].append(member[3])
            previous = pair
        stats.append({
            "key": key,
            "members": group,
            "petrol": upper_median(m[0].petrol_ref_ms for m in group),
            "gas": upper_median(m[0].gas_petrol_ms for m in group),
            "error": upper_median(m[3] for m in group),
            "visits": [upper_median(values) for values in visits],
        })

    def others_of(stat: dict, excluded: set[tuple[int, int]]) -> list[float]:
        return [member[3] for member in members
                if member[1] != stat["key"][0]
                and abs(member[0].petrol_ref_ms - stat["petrol"]) <= SAME_T_MS
                and (member[2] is None or member[2] not in excluded)]

    initial = {}
    for stat in stats:
        reference = upper_median(others_of(stat, set()))
        initial[stat["key"]] = abs(stat["error"] - reference) if reference is not None else -1.0
    ordered_stats = sorted(stats, key=lambda stat: (
        0 if stat["key"] in latched else 1,
        -initial[stat["key"]], stat["key"][0], stat["key"][1],
    ))
    excluded: set[tuple[int, int]] = set()
    out: list[Group] = []
    for stat in ordered_stats:
        others = others_of(stat, excluded)
        others_log = upper_median(others)
        residual = stat["error"] - others_log if others_log is not None else None
        visit_residuals = [value - others_log for value in stat["visits"]] if others_log is not None else []
        centre = upper_median(visit_residuals)
        stable = bool(visit_residuals) and (all(value > 0 for value in visit_residuals) or all(value < 0 for value in visit_residuals))
        if stable:
            stable = upper_median(abs(value - centre) for value in visit_residuals) <= VISIT_SPREAD_LOG
        threshold = EXIT_LOG if stat["key"] in latched else ENTER_LOG
        if len(stat["members"]) < MIN_PAIRS:
            reason = "POUCOS_PARES"
        elif len(stat["visits"]) < MIN_VISITS:
            reason = "POUCAS_VISITAS"
        elif len(others) < MIN_OTHER_PAIRS or residual is None:
            reason = "SEM_OUTRA_COLUNA_NO_MESMO_T"
        elif not stable:
            reason = "VISITAS_INSTAVEIS"
        elif abs(residual) <= threshold:
            reason = "IGUAL_AS_OUTRAS_COLUNAS"
        else:
            reason = "LOCAL"
        if reason == "LOCAL" or (residual is not None and abs(residual) > threshold):
            excluded.add(stat["key"])
        first = stat["members"][0][0]
        out.append(Group(
            scope or first.session, first.epoch, stat["key"][0], stat["key"][1], len(stat["members"]),
            len(stat["visits"]), stat["petrol"], stat["gas"], stat["error"], len(others),
            others_log, residual, reason == "LOCAL", reason,
        ))
    return sorted(out, key=lambda group: (group.session, group.epoch, group.column, group.band))


def write_csv(path: Path, rows: Sequence[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if not rows:
        path.write_text("", encoding="utf-8")
        return
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def run(paths: Sequence[Path], output: Path, head: str = "") -> dict:
    output.mkdir(parents=True, exist_ok=True)
    manifest = build_manifest(paths)
    (output / "corpus-manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    events_by_session: dict[str, list[dict]] = defaultdict(list)
    accepted = {"ACCEPTED", "PARTIAL"}
    for row in manifest["files"]:
        if row["classification"] not in accepted:
            continue
        session = row["sessions"][0]
        events_by_session[session].extend(read_events(Path(row["path"])))

    all_observations: list[Observation] = []
    all_pairs: list[Pair] = []
    all_groups: list[Group] = []
    frames_by_session: dict[str, list[Frame]] = {}
    observations_by_session: dict[str, list[Observation]] = {}
    sessions_by_lineage: dict[str, list[str]] = defaultdict(list)
    for session in events_by_session:
        sessions_by_lineage[lineage_of(session)].append(session)
    for lineage, sessions in sessions_by_lineage.items():
        sessions.sort(key=lambda session: min((int(event.get("recordedAtMs") or 0) for event in events_by_session[session] if not event.get("_malformed")), default=0))
        epoch_offset = 0
        lineage_observations: list[Observation] = []
        for session in sessions:
            local_frames = frames_from_events(events_by_session[session], session)
            frames = [Frame(frame.t, frame.fuel, frame.rpm, frame.map, frame.petrol_ms, frame.epoch + epoch_offset, frame.session) for frame in local_frames]
            observations = stable_observations(frames)
            frames_by_session[session] = frames
            observations_by_session[session] = observations
            lineage_observations.extend(observations)
            epoch_offset += sum(calibration_boundary(event) for event in events_by_session[session] if not event.get("_malformed"))
        lineage_pairs = online_pairs(lineage_observations, carry_across_sessions=True)
        lineage_groups: list[Group] = []
        for epoch in sorted({pair.epoch for pair in lineage_pairs}):
            lineage_groups.extend(classify([pair for pair in lineage_pairs if pair.epoch == epoch], scope=lineage))
        all_observations.extend(lineage_observations)
        all_pairs.extend(lineage_pairs)
        all_groups.extend(lineage_groups)

    session_rows = []
    for session in sorted(events_by_session):
        frames = frames_by_session.get(session, [])
        observations = observations_by_session.get(session, [])
        pairs = [pair for pair in all_pairs if pair.session == session]
        lineage = lineage_of(session)
        session_rows.append({
            "session": session,
            "lineage": lineage,
            "frames": len(frames),
            "observations": len(observations),
            "pairs": len(pairs),
            "epochs": len({frame.epoch for frame in frames}),
            "localGroups": sum(group.local and group.session == lineage for group in all_groups),
        })

    pair_rows = []
    for pair in all_pairs:
        row = asdict(pair)
        row["error_log"] = pair.error_log
        row["error_percent"] = (math.exp(pair.error_log) - 1.0) * 100.0
        pair_rows.append(row)
    group_rows = []
    for group in all_groups:
        row = asdict(group)
        row["rpm_bin"] = group.rpm
        row["error_percent"] = (math.exp(group.error_log) - 1.0) * 100.0
        row["residual_percent"] = (math.exp(group.residual_log) - 1.0) * 100.0 if group.residual_log is not None else ""
        group_rows.append(row)
    write_csv(output / "session-results.csv", session_rows)
    write_csv(output / "pairs.csv", pair_rows)
    write_csv(output / "residual-groups.csv", group_rows)

    summary = {
        "schema": "omegas-obsidian-rpm-residual-replay-v1",
        "sourceHead": head,
        "sessions": len(events_by_session),
        "observations": len(all_observations),
        "pairs": len(all_pairs),
        "groups": len(all_groups),
        "localGroups": sum(group.local for group in all_groups),
        "unknownGroups": sum(group.reason != "LOCAL" for group in all_groups),
        "reasons": dict(sorted((reason, sum(group.reason == reason for group in all_groups)) for reason in {g.reason for g in all_groups})),
        "limits": [
            "Replay does not prove fuel consumption or drivability.",
            "A local group is a calibration hypothesis, never an ECU write.",
            "Temperature and pressure are validity context, not base-curve axes.",
        ],
    }
    (output / "summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    digest_lines = []
    for artifact in sorted(output.iterdir()):
        if artifact.name == "manifest.sha256" or not artifact.is_file():
            continue
        digest_lines.append(f"{sha256_file(artifact)}  {artifact.name}")
    (output / "manifest.sha256").write_text("\n".join(digest_lines) + "\n", encoding="utf-8")
    return summary


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("inputs", nargs="+", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--head", default="")
    args = parser.parse_args()
    summary = run(args.inputs, args.output, args.head)
    print(json.dumps(summary, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
