#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path

EVENT_RE = re.compile(
    r"^(?P<index>\d+)\s+(?P<field2>\d+\.\d+)\s+ProgBase\.exe\s+"
    r"(?P<op>IRP_MJ_WRITE|IRP_MJ_READ|IOCTL_SERIAL_PURGE)\s+Silabser\d+\s*(?P<detail>.*)$"
)
SUCCESS_RE = re.compile(r"^(?P<index>\d+)\s+(?P<duration>\d+\.\d+)\s+SUCCESS\s*(?P<detail>.*)$")
HEX_RE = re.compile(r"Length\s+\d+:\s*((?:[0-9A-Fa-f]{2}(?:\s+|$))+)")
EXPECTED_TIMING_RULE = (
    "transaction.at_ms = cumulative duration of every completed Portmon operation "
    "before the IRP_MJ_WRITE that starts the transaction"
)
REQUIRED_REQUESTS = {
    "48 0B 53",
    "48 01 49",
    "29 4B 01 75",
    "29 61 01 8B",
    "29 8D 01 B7",
    "29 8E 01 B8",
    "02 24 04 04 2E",
}


def sha256_path(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def _payload(detail: str) -> str:
    m = HEX_RE.search(detail)
    return "" if not m else " ".join(m.group(1).upper().split())


def iter_events(zip_path: Path, entry: str):
    clock = 0.0
    pending = None
    with zipfile.ZipFile(zip_path) as zf, zf.open(entry) as raw:
        for blob in raw:
            line = blob.decode("utf-8", "replace").rstrip("\r\n")
            m = EVENT_RE.match(line)
            if m:
                pending = (int(m.group("index")), m.group("op"), _payload(m.group("detail")))
                continue
            s = SUCCESS_RE.match(line)
            if s and pending is not None:
                idx, op, fallback = pending
                if int(s.group("index")) == idx:
                    duration = float(s.group("duration")) * 1000.0
                    p = _payload(s.group("detail")) or fallback
                    yield {
                        "index": idx,
                        "clock_ms": clock,
                        "duration_ms": duration,
                        "op": op,
                        "payload": p,
                    }
                    clock += duration
                pending = None


def canonical_transactions(zip_path: Path, entry: str) -> dict[int, dict]:
    out: dict[int, dict] = {}
    current = None
    for ev in iter_events(zip_path, entry):
        if ev["op"] == "IRP_MJ_WRITE":
            if current is not None:
                current["response"] = " ".join(current.pop("_reads"))
                out[current["portmon_index"]] = current
            current = {
                "portmon_index": ev["index"],
                "at_ms": ev["clock_ms"],
                "request": ev["payload"],
                "_reads": [],
            }
        elif ev["op"] == "IRP_MJ_READ" and current is not None and ev["payload"]:
            current["_reads"].append(ev["payload"])
    if current is not None:
        current["response"] = " ".join(current.pop("_reads"))
        out[current["portmon_index"]] = current
    return out


def _frame_ok(value: str) -> bool:
    try:
        data = bytes.fromhex(value)
    except ValueError:
        return False
    return len(data) >= 2 and (sum(data[:-1]) & 0xFF) == data[-1]


def _response_ok(request_hex: str, response_hex: str) -> bool:
    try:
        req = bytes.fromhex(request_hex)
        resp = bytes.fromhex(response_hex)
    except ValueError:
        return False
    if len(resp) < len(req) + 3 or not resp.startswith(req):
        return False
    tail = resp[len(req):]
    if len(tail) < 3:
        return False
    payload_size = tail[1]
    if len(tail) != payload_size + 3:
        return False
    return (sum(tail[:-1]) & 0xFF) == tail[-1]


def validate(replay: dict, zip_path: Path, raw_entry: str, raw_sha256: str) -> dict:
    if replay.get("sourceRawSha256") != raw_sha256:
        raise AssertionError(
            f"replay sourceRawSha256 mismatch: {replay.get('sourceRawSha256')} != {raw_sha256}"
        )
    if replay.get("timingRule") != EXPECTED_TIMING_RULE:
        raise AssertionError("replay timing rule does not match canonical Portmon clock semantics")

    canonical = canonical_transactions(zip_path, raw_entry)
    rows = replay.get("transactions", [])
    if not rows:
        raise AssertionError("replay has no transactions")

    seen_sequences = []
    seen_requests = set()
    max_delta = 0.0
    for row in rows:
        seq = int(row["sequence"])
        seen_sequences.append(seq)
        idx = int(row["portmon_index"])
        source = canonical.get(idx)
        if source is None:
            raise AssertionError(f"replay transaction references absent Portmon event {idx}")
        request = " ".join(str(row["request"]).upper().split())
        response = " ".join(str(row["response"]).upper().split())
        if request != source["request"]:
            raise AssertionError(f"request mismatch at Portmon {idx}")
        if response != source["response"]:
            raise AssertionError(f"response mismatch at Portmon {idx}")
        delta = abs(float(row["at_ms"]) - float(source["at_ms"]))
        max_delta = max(max_delta, delta)
        if delta > 0.0015:
            raise AssertionError(f"timing mismatch at Portmon {idx}: delta={delta}")
        if not _frame_ok(request):
            raise AssertionError(f"invalid request checksum at Portmon {idx}")
        if not _response_ok(request, response):
            raise AssertionError(f"invalid echoed response/checksum at Portmon {idx}")
        seen_requests.add(request)

    if seen_sequences != sorted(seen_sequences) or len(seen_sequences) != len(set(seen_sequences)):
        raise AssertionError("replay sequence is not strictly unique/ordered")

    missing = sorted(REQUIRED_REQUESTS - seen_requests)
    if missing:
        raise AssertionError(f"replay misses required AutoCal requests: {missing}")

    return {
        "schema": "omegas.atlas.consumer-replay-proof.v1",
        "status": "PROVEN",
        "source_raw_sha256": raw_sha256,
        "replay_schema": replay.get("schema"),
        "replay_transactions": len(rows),
        "canonical_transactions_indexed": len(canonical),
        "validated_exact_transactions": len(rows),
        "max_timing_delta_ms": max_delta,
        "timing_rule": EXPECTED_TIMING_RULE,
        "required_requests": sorted(REQUIRED_REQUESTS),
        "classification": (
            "Consumer replay is byte/timing-faithful to canonical LOGNOVO. "
            "It is a compatibility challenger, not a native semantic authority."
        ),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--replay", type=Path, required=True)
    ap.add_argument("--canonical-zip", type=Path, required=True)
    ap.add_argument("--raw-entry", required=True)
    ap.add_argument("--raw-sha256", required=True)
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()

    replay = json.loads(args.replay.read_text(encoding="utf-8"))
    result = validate(
        replay,
        args.canonical_zip,
        args.raw_entry,
        args.raw_sha256.lower(),
    )
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
