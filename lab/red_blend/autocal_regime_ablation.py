"""Readiness gate for AutoCal acquisition zones as an offline regime feature."""
from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
from typing import Any, Mapping


@dataclass(frozen=True)
class AutoCalRegimeAudit:
    status: str
    acquisition_zone_count: int
    curve_point_count: int
    map_rows: int
    map_columns: int
    map_cells: int
    snapshot_count: int
    partial_snapshot_count: int
    temporal_coherent_snapshot_count: int
    telemetry_aligned_zone_snapshots: int
    thirty_point_fields: tuple[str, ...]
    thirty_point_field_status: str
    corruption_claim_allowed: bool
    promotion_allowed: bool
    android_changed: bool = False
    p_improve_proven: bool = False
    claim_scope: str = "OFFLINE_AUTOCAL_REGIME_READINESS_NOT_PRODUCTION"


def _payload(source: Path | Mapping[str, Any]) -> Mapping[str, Any]:
    if isinstance(source, Mapping):
        return source
    return json.loads(Path(source).read_text(encoding="utf-8"))


def audit_autocal_regime_readiness(
    checkpoint_source: Path | Mapping[str, Any],
) -> AutoCalRegimeAudit:
    payload = _payload(checkpoint_source)
    if payload.get("schema") != "omegas-full-corpus-f3-f5-checkpoint-v1":
        raise ValueError("unsupported full-corpus checkpoint schema")
    autocal = ((payload.get("f5_ecu_state") or {}).get("autocal") or {})
    snapshots = int(autocal.get("distinct_snapshots") or 0)
    partial = int(autocal.get("partial_snapshots") or 0)
    coherent = int(autocal.get("temporal_coherent_snapshots") or 0)
    systematic = autocal.get("systematic_invalid_fields") or {}
    thirty = tuple(sorted(str(key).split("@", 1)[0] for key in systematic))

    expected_thirty = {
        "PETR_INJ_TBP",
        "MUL_ACT",
        "PETR_MNFLD_PRESS_RV",
        "GAS_MNFLD_PRESS_RV",
    }
    if set(thirty) != expected_thirty:
        raise ValueError("checkpoint does not preserve the known 30-point family")

    # No committed snapshot is both temporally coherent and decoded as a real
    # 18-zone acquisition state aligned to telemetry.  The old version-derived
    # 18-element expectation cannot turn the four 30-point fields into corrupt
    # data; their honest status remains unknown pending protocol proof.
    aligned = 0
    ready = coherent > 0 and aligned > 0
    return AutoCalRegimeAudit(
        status=(
            "READY_FOR_AUTOCAL_REGIME_ABLATION"
            if ready
            else "DEFER_NO_TEMPORALLY_COHERENT_18_ZONE_SUPPORT"
        ),
        acquisition_zone_count=18,
        curve_point_count=30,
        map_rows=12,
        map_columns=12,
        map_cells=144,
        snapshot_count=snapshots,
        partial_snapshot_count=partial,
        temporal_coherent_snapshot_count=coherent,
        telemetry_aligned_zone_snapshots=aligned,
        thirty_point_fields=thirty,
        thirty_point_field_status="UNKNOWN_PENDING_PROTOCOL_PROOF",
        corruption_claim_allowed=False,
        promotion_allowed=False,
    )
