"""Privacy-safe real-corpus audit for RED V8.2 Science Blend.

This module consumes only the governed split episode fixture. It is an offline
science tool, not Android/runtime authority. Session identities are already
privacy-preserving hashes. Local results do not prove transfer across sessions.
"""
from __future__ import annotations

import argparse
import base64
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass
import gzip
import hashlib
import json
import math
from pathlib import Path
from typing import Any, Sequence

from lab.red_blend.local_science import (
    BootstrapInterval,
    DistributionSummary,
    MultimodalityDecision,
    MultimodalityPolicy,
    bootstrap_mean_interval,
    detect_multimodality,
    summarize_distribution,
)


_FIXTURE_SCHEMA = "omegas-science-episode-fixture-index-v1"
_ALLOWED_FIELDS = {
    "session_key",
    "order",
    "fuel",
    "start_ms",
    "end_ms",
    "rpm",
    "map_bar",
    "petrol_ms",
    "window_count",
    "rpm_bin",
    "map_bin",
}
_FUELS = {"GASOLINA", "GNV"}


@dataclass(frozen=True)
class RealRegionAudit:
    rpm_bin: int
    map_bin: int
    count: int
    unique_sessions: int
    mean_rpm: float
    mean_map_bar: float
    summary: DistributionSummary
    bootstrap: BootstrapInterval
    multimodality: MultimodalityDecision
    classification: str


@dataclass(frozen=True)
class RealCorpusReport:
    fuel: str
    total_fuel_episodes: int
    analyzed_regions: int
    analyzed_episodes: int
    sparse_episodes: int
    unimodal_regions: int
    multimodal_regions: int
    ambiguous_regions: int
    regions: tuple[RealRegionAudit, ...]
    claim_scope: str = "REAL_CORPUS_LOCAL_ONLY_NOT_TRANSFER"
    policy_label: str = "LAB_HEURISTIC"


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _validate_episode(episode: Any) -> dict[str, Any]:
    if not isinstance(episode, dict) or set(episode) != _ALLOWED_FIELDS:
        raise ValueError("episode fixture privacy/schema shape mismatch")
    session_key = episode["session_key"]
    if not isinstance(session_key, str) or len(session_key) != 16 or any(c not in "0123456789abcdef" for c in session_key):
        raise ValueError("episode session_key is not a privacy-safe 16-hex key")
    if episode["fuel"] not in _FUELS:
        raise ValueError("episode fuel outside governed lane")
    for key in ("rpm", "map_bar", "petrol_ms"):
        try:
            value = float(episode[key])
        except (TypeError, ValueError) as exc:
            raise ValueError(f"episode {key} is not numeric") from exc
        if not math.isfinite(value):
            raise ValueError(f"episode {key} is not finite")
    for key in ("order", "start_ms", "end_ms", "window_count", "rpm_bin", "map_bin"):
        if isinstance(episode[key], bool) or not isinstance(episode[key], int):
            raise ValueError(f"episode {key} must be an integer")
    if episode["end_ms"] < episode["start_ms"] or episode["window_count"] < 1:
        raise ValueError("episode temporal/window contract invalid")
    return episode


def load_governed_fixture(parts_dir: Path, index_path: Path) -> list[dict[str, Any]]:
    """Reconstruct the WU-006 split fixture and fail closed on any identity drift."""
    index = json.loads(index_path.read_text(encoding="utf-8"))
    if index.get("schema") != _FIXTURE_SCHEMA:
        raise ValueError("unsupported fixture index schema")
    expected = index.get("parts") or []
    actual = sorted(parts_dir.glob(index["part_glob"]))
    if len(actual) != index.get("part_count") or len(expected) != index.get("part_count"):
        raise ValueError("fixture part count mismatch")

    chunks: list[bytes] = []
    for path, spec in zip(actual, expected):
        if path.name != spec.get("name"):
            raise ValueError("fixture part order/name mismatch")
        raw = path.read_bytes()
        if len(raw) != index.get("part_chars"):
            raise ValueError(f"fixture part length mismatch: {path.name}")
        if _sha256(raw) != spec.get("sha256"):
            raise ValueError(f"fixture part sha256 mismatch: {path.name}")
        chunks.append(raw)

    try:
        compressed = base64.b64decode(b"".join(chunks), validate=True)
    except Exception as exc:
        raise ValueError("fixture base64 reconstruction failed") from exc
    if len(compressed) != index.get("compressed_bytes") or _sha256(compressed) != index.get("compressed_sha256"):
        raise ValueError("reconstructed compressed fixture mismatch")
    try:
        uncompressed = gzip.decompress(compressed)
    except (OSError, EOFError) as exc:
        raise ValueError("fixture gzip reconstruction failed") from exc
    if len(uncompressed) != index.get("uncompressed_bytes") or _sha256(uncompressed) != index.get("uncompressed_sha256"):
        raise ValueError("reconstructed uncompressed fixture mismatch")

    lines = [line for line in uncompressed.splitlines() if line]
    if len(lines) != index.get("episode_lines"):
        raise ValueError("fixture episode line count mismatch")
    episodes = [_validate_episode(json.loads(line)) for line in lines]
    return episodes


def _region_seed(seed: int, rpm_bin: int, map_bin: int) -> int:
    # Stable across Python processes; never use randomized built-in hash().
    return (int(seed) * 1_000_003 + int(rpm_bin) * 9_176 + int(map_bin) * 6_113) & 0x7FFFFFFF


def analyze_real_regions(
    episodes: Sequence[dict[str, Any]],
    *,
    fuel: str,
    min_samples: int = 4,
    bootstrap_draws: int = 1000,
    seed: int = 0,
    policy: MultimodalityPolicy = MultimodalityPolicy(),
) -> RealCorpusReport:
    """Audit local RPM×MAP bins without promoting any cross-session claim."""
    if fuel not in _FUELS:
        raise ValueError("fuel must be GASOLINA or GNV")
    if min_samples < 2:
        raise ValueError("min_samples must be >= 2")

    selected = [_validate_episode(dict(e)) for e in episodes if e.get("fuel") == fuel]
    groups: dict[tuple[int, int], list[dict[str, Any]]] = defaultdict(list)
    for episode in selected:
        groups[(episode["rpm_bin"], episode["map_bin"])].append(episode)

    regions: list[RealRegionAudit] = []
    for (rpm_bin, map_bin), group in sorted(groups.items()):
        if len(group) < min_samples:
            continue
        samples = [float(e["petrol_ms"]) for e in group]
        summary = summarize_distribution(samples)
        bootstrap = bootstrap_mean_interval(
            samples,
            draws=bootstrap_draws,
            seed=_region_seed(seed, rpm_bin, map_bin),
            alpha=0.05,
        )
        decision = detect_multimodality(samples, policy)
        if decision.is_multimodal:
            classification = "MULTIMODAL"
        elif decision.bic_gain <= 0.0:
            classification = "UNIMODAL_SUPPORTED"
        else:
            # A two-mode likelihood signal exists, but mass/separation/evidence guards
            # do not jointly justify calling it a second physical regime.
            classification = "AMBIGUOUS_MIXTURE_SIGNAL"
        regions.append(
            RealRegionAudit(
                rpm_bin=rpm_bin,
                map_bin=map_bin,
                count=len(group),
                unique_sessions=len({str(e["session_key"]) for e in group}),
                mean_rpm=sum(float(e["rpm"]) for e in group) / len(group),
                mean_map_bar=sum(float(e["map_bar"]) for e in group) / len(group),
                summary=summary,
                bootstrap=bootstrap,
                multimodality=decision,
                classification=classification,
            )
        )

    # Count-heavy regions first makes the CLI useful while preserving deterministic tie order.
    regions.sort(key=lambda r: (-r.count, r.rpm_bin, r.map_bin))
    analyzed_episodes = sum(r.count for r in regions)
    classes = Counter(r.classification for r in regions)
    return RealCorpusReport(
        fuel=fuel,
        total_fuel_episodes=len(selected),
        analyzed_regions=len(regions),
        analyzed_episodes=analyzed_episodes,
        sparse_episodes=len(selected) - analyzed_episodes,
        unimodal_regions=classes["UNIMODAL_SUPPORTED"],
        multimodal_regions=classes["MULTIMODAL"],
        ambiguous_regions=classes["AMBIGUOUS_MIXTURE_SIGNAL"],
        regions=tuple(regions),
    )


def _compact_report(report: RealCorpusReport, top: int) -> dict[str, Any]:
    return {
        "fuel": report.fuel,
        "total_fuel_episodes": report.total_fuel_episodes,
        "analyzed_regions": report.analyzed_regions,
        "analyzed_episodes": report.analyzed_episodes,
        "sparse_episodes": report.sparse_episodes,
        "unimodal_regions": report.unimodal_regions,
        "multimodal_regions": report.multimodal_regions,
        "ambiguous_regions": report.ambiguous_regions,
        "claim_scope": report.claim_scope,
        "policy_label": report.policy_label,
        "top_regions": [
            {
                "rpm_bin": r.rpm_bin,
                "map_bin": r.map_bin,
                "count": r.count,
                "unique_sessions": r.unique_sessions,
                "mean_rpm": r.mean_rpm,
                "mean_map_bar": r.mean_map_bar,
                "petrol_ms_mean": r.summary.mean,
                "petrol_ms_cv": r.summary.cv,
                "bootstrap95": [r.bootstrap.low, r.bootstrap.high],
                "classification": r.classification,
                "bic_gain": r.multimodality.bic_gain,
                "min_component_weight": r.multimodality.min_component_weight,
                "separation_sigma": r.multimodality.separation_sigma,
            }
            for r in report.regions[:top]
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("parts_dir", type=Path)
    parser.add_argument("index", type=Path)
    parser.add_argument("--fuel", choices=sorted(_FUELS), required=True)
    parser.add_argument("--min-samples", type=int, default=4)
    parser.add_argument("--bootstrap-draws", type=int, default=600)
    parser.add_argument("--seed", type=int, default=20260830)
    parser.add_argument("--top", type=int, default=12)
    args = parser.parse_args()
    episodes = load_governed_fixture(args.parts_dir, args.index)
    report = analyze_real_regions(
        episodes,
        fuel=args.fuel,
        min_samples=args.min_samples,
        bootstrap_draws=args.bootstrap_draws,
        seed=args.seed,
    )
    print(json.dumps(_compact_report(report, max(0, args.top)), sort_keys=True, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
