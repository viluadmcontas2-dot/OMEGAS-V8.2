"""Fail-closed chronology audit for privacy-safe calibration derivatives.

Numeric timestamp alignment is useful diagnostic evidence, but it is not a
clock-domain proof.  The audit therefore reports structural alignment and the
missing provenance separately.  It never grants Android or ECU authority.
"""
from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
from typing import Any, Mapping, Sequence

from lab.red_blend.causal_science import AdjustmentFixture


@dataclass(frozen=True)
class ClockBridgeAudit:
    status: str
    episode_count: int
    intervention_count: int
    pre_session_count: int
    post_session_count: int
    interventions_inside_observed_gap: bool
    session_episode_invariants_proven: bool
    common_clock_proven: bool
    reason: str
    actionable: bool = False
    claim_scope: str = "OFFLINE_CHRONOLOGY_DIAGNOSTIC_NOT_CAUSAL"


def _load_manifest(source: Path | Mapping[str, Any]) -> Mapping[str, Any]:
    if isinstance(source, Mapping):
        return source
    return json.loads(Path(source).read_text(encoding="utf-8"))


def audit_clock_bridge(
    manifest_source: Path | Mapping[str, Any],
    episodes: Sequence[Mapping[str, Any]],
    fixture: AdjustmentFixture,
    *,
    declared_clock_domain: str | None = None,
) -> ClockBridgeAudit:
    """Audit chronology without treating a caller-supplied label as proof."""
    manifest = _load_manifest(manifest_source)
    if manifest.get("schema") != "omegas-science-corpus-manifest-v1":
        raise ValueError("unsupported corpus manifest schema")
    sessions = manifest.get("sessions") or []
    by_key = {str(row.get("session_key") or ""): row for row in sessions}
    if len(by_key) != len(sessions) or not episodes or not fixture.adjustments:
        raise ValueError("chronology audit requires unique sessions, episodes, and adjustments")

    invariant_ok = True
    episode_sessions: dict[str, int] = {}
    for episode in episodes:
        key = str(episode.get("session_key") or "")
        row = by_key.get(key)
        if row is None:
            invariant_ok = False
            continue
        order = int(episode["order"])
        start = int(episode["start_ms"])
        end = int(episode["end_ms"])
        if order != int(row["order"]) or start < int(row["created_at_ms"]) or end < start:
            invariant_ok = False
        episode_sessions[key] = order

    ordered_manifest = sorted(
        (int(row["order"]), int(row["created_at_ms"])) for row in sessions
    )
    if any(a[0] >= b[0] or a[1] >= b[1] for a, b in zip(ordered_manifest, ordered_manifest[1:])):
        invariant_ok = False

    first_adjustment = min(item.started_at_ms for item in fixture.adjustments)
    last_adjustment = max(item.ended_at_ms for item in fixture.adjustments)
    pre_keys = {
        str(item["session_key"])
        for item in episodes
        if int(item["end_ms"]) < first_adjustment
    }
    post_keys = {
        str(item["session_key"])
        for item in episodes
        if int(item["start_ms"]) > last_adjustment
    }
    pre_end = max(
        (int(item["end_ms"]) for item in episodes if str(item["session_key"]) in pre_keys),
        default=first_adjustment,
    )
    post_start = min(
        (int(item["start_ms"]) for item in episodes if str(item["session_key"]) in post_keys),
        default=last_adjustment,
    )
    inside_gap = bool(pre_keys and post_keys and pre_end < first_adjustment <= last_adjustment < post_start)

    # Neither governed input currently carries a shared clock-contract identity.
    # A label supplied by a caller is intentionally ignored: provenance must be
    # embedded in both derivatives and match by immutable hash.
    manifest_clock = manifest.get("clock_contract")
    fixture_clock = getattr(fixture, "clock_contract", None)
    common_clock = bool(
        isinstance(manifest_clock, Mapping)
        and isinstance(fixture_clock, Mapping)
        and manifest_clock.get("sha256")
        and manifest_clock == fixture_clock
    )
    _ = declared_clock_domain

    if not invariant_ok:
        status = "REJECT_CHRONOLOGY_INVARIANT_FAILURE"
        reason = "SESSION_EPISODE_RELATIONSHIP_INVALID"
    elif not common_clock:
        status = "DEFER_CLOCK_PROVENANCE_MISSING"
        reason = "NUMERIC_ALIGNMENT_IS_NOT_CLOCK_PROOF"
    else:
        status = "COMMON_CLOCK_PROVEN"
        reason = "MATCHING_IMMUTABLE_CLOCK_CONTRACT"

    return ClockBridgeAudit(
        status=status,
        episode_count=len(episodes),
        intervention_count=len(fixture.adjustments),
        pre_session_count=len(pre_keys),
        post_session_count=len(post_keys),
        interventions_inside_observed_gap=inside_gap,
        session_episode_invariants_proven=invariant_ok,
        common_clock_proven=common_clock,
        reason=reason,
    )
