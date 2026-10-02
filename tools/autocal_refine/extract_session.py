#!/usr/bin/env python3
"""Reduz uma sessão `omegas-session-log-v1` (JSONL) a uma fixture AutoCal compacta.

Mantém apenas o que o motor de Equivalência Refinada e o simulador de laço
fechado consomem: snapshots nativos AutoCal (campos do AutoMatch) e frames de
telemetria essenciais. Uso:

    python3 tools/autocal_refine/extract_session.py <events.jsonl> <saida.json.gz> <rótulo>
"""
import gzip
import json
import sys

SNAPSHOT_KEYS = {
    "PETR_INJ_TBP", "MNFLD_PRESS_THD", "MUL_ACT",
    "PETR_MNFLD_PRESS_RV", "GAS_MNFLD_PRESS_RV",
    "PETR_INJ_TBUF", "MNFLD_PRESS_BUF", "NUM_BUF_UPD_PETR",
    "PETR_INJ_TBUF_GAS", "MNFLD_PRESS_BUF_GAS", "NUM_BUF_UPD_GAS",
    "PETR_INJ_TBUF_GAS_PREV", "MNFLD_PRESS_BUF_GAS_PREV",
    "ACQUIRED_ZONES_PETROL", "ACQUIRED_ZONES_GAS",
    "NUM_AUTOMATCH_EXECUTED", "CALIBRATION_VAL_1", "VECT_AUTOCAL_U8_1",
}
TELEMETRY_KEYS = ("rpm", "load_bar", "petrol_ms", "petrol_2_ms_diagnostic", "gas_ms_diagnostic", "fuel")


def reduce_snapshot(event):
    data = event["data"]
    fields = []
    for field in data.get("fields", []):
        if field.get("key") not in SNAPSHOT_KEYS:
            continue
        fields.append({
            "key": field["key"],
            "status": field.get("status"),
            "rawValues": field.get("rawValues"),
            "capturedAtMs": field.get("capturedAtMs", 0),
        })
    return {
        "sequence": event["sequence"],
        "recordedAtUtc": event["recordedAtUtc"],
        "snapshotReason": data.get("snapshotReason"),
        "snapshotHash": data.get("snapshotHash"),
        "capturedAtMs": data.get("capturedAtMs"),
        "temporalCoherent": data.get("temporalCoherent"),
        "coherenceGroups": data.get("coherenceGroups"),
        "partial": data.get("partial"),
        "fields": sorted(fields, key=lambda item: item["key"]),
    }


def reduce_telemetry(event):
    data = event["data"]
    frame = {"t": event["recordedAtMs"]}
    for key in TELEMETRY_KEYS:
        value = data.get(key)
        frame[key] = round(value, 5) if isinstance(value, float) else value
    return frame


def main(source, target, label):
    snapshots, telemetry, k_writes = [], [], []
    with open(source, encoding="utf-8") as handle:
        for line in handle:
            if not line.strip():
                continue
            event = json.loads(line)
            kind = event.get("type")
            if kind == "autocal_native_snapshot":
                snapshots.append(reduce_snapshot(event))
            elif kind == "telemetry":
                telemetry.append(reduce_telemetry(event))
            elif kind == "k_factor_batch_confirmed":
                k_writes.append({"sequence": event["sequence"], "recordedAtUtc": event["recordedAtUtc"]})
    out = {
        "format": "omegas-autocal-replay-v1",
        "label": label,
        "snapshots": snapshots,
        "kFactorWrites": k_writes,
        "telemetry": telemetry,
    }
    opener = gzip.open if target.endswith(".gz") else open
    with opener(target, "wt", encoding="utf-8") as handle:
        json.dump(out, handle, separators=(",", ":"))
        handle.write("\n")


if __name__ == "__main__":
    main(*sys.argv[1:4])
