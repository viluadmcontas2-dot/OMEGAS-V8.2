#!/usr/bin/env python3
"""Extract compact, provenance-bearing AutoCal control evidence from a raw Portmon LOG.

Usage:
    python tools/omegas-amarelo/extract_portmon_control_fixture.py INPUT.LOG OUTPUT.json
"""
from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path

EVENT = re.compile(r"^(\d+)\s+([0-9.]+)\s+ProgBase\.exe\s+(IRP_MJ_WRITE|IRP_MJ_READ)\s+Silabser\d+\s*(.*)$")
SUCCESS = re.compile(r"^(\d+)\s+([0-9.]+)\s+SUCCESS\s*(.*)$")
HEX = re.compile(r"Length\s+\d+:\s*((?:[0-9A-Fa-f]{2}(?:\s+|$))+)")

SELECTORS = {
    "RESET_ALL_ACTION": bytes.fromhex("02 24 04 04 2E"),
    "READ_MUL_ACT": bytes.fromhex("29 61 01 8B"),
    "READ_AUTOMATCH_COUNT": bytes.fromhex("09 74 01 7E"),
    "READ_MAX_RPM_AUTOCAL": bytes.fromhex("09 7A 01 84"),
    "ENABLE_AUTOCAL_0": bytes.fromhex("12 4A 01 00"),
    "ENABLE_AUTOCAL_1": bytes.fromhex("12 4A 01 01"),
    "HANDSHAKE_3A": bytes.fromhex("01 00 3A 3B"),
    "HANDSHAKE_25": bytes.fromhex("00 25 25"),
}

def _data(text: str) -> bytes:
    match = HEX.search(text)
    if not match:
        return b""
    return bytes.fromhex(" ".join(match.group(1).split()))

def transactions(path: Path):
    pending = None
    current = None
    with path.open("rt", encoding="latin1", errors="replace") as stream:
        for line_number, raw in enumerate(stream, 1):
            line = raw.rstrip()
            event = EVENT.match(line)
            if event:
                pending = (
                    line_number,
                    int(event.group(1)),
                    float(event.group(2)),
                    event.group(3),
                    _data(event.group(4)),
                )
                continue
            success = SUCCESS.match(line)
            if not success or pending is None or int(success.group(1)) != pending[1]:
                continue
            source_line, index, delta, operation, payload = pending
            pending = None
            payload = _data(success.group(3)) or payload
            if operation == "IRP_MJ_WRITE":
                if current is not None:
                    yield current
                current = {
                    "line": source_line,
                    "idx": index,
                    "delta": delta,
                    "req": payload,
                    "resp": bytearray(),
                }
            elif operation == "IRP_MJ_READ" and current is not None and payload:
                current["resp"].extend(payload)
    if current is not None:
        yield current

def extract(source: Path) -> dict:
    source_sha = hashlib.sha256(source.read_bytes()).hexdigest()
    found = {name: [] for name in SELECTORS}
    for sequence, tx in enumerate(transactions(source), 1):
        for name, prefix in SELECTORS.items():
            if tx["req"].startswith(prefix):
                found[name].append(
                    {
                        "sequence": sequence,
                        "portmon_index": tx["idx"],
                        "source_line": tx["line"],
                        "request": tx["req"].hex(" ").upper(),
                        "response": bytes(tx["resp"]).hex(" ").upper(),
                    }
                )
    return {
        "schema": "omegas.amarelo.portmon-evidence.v1",
        "sourceRawName": source.name,
        "sourceRawSha256": source_sha,
        "extraction": "stream IRP_MJ_WRITE + following successful fragmented IRP_MJ_READ responses; select exact command prefixes",
        "selectors": {name: prefix.hex(" ").upper() for name, prefix in SELECTORS.items()},
        "evidence": {name: rows[:12] for name, rows in found.items()},
        "counts": {name: len(rows) for name, rows in found.items()},
    }

def main(argv: list[str]) -> int:
    if len(argv) != 3:
        raise SystemExit("usage: extract_portmon_control_fixture.py INPUT.LOG OUTPUT.json")
    source = Path(argv[1])
    output = Path(argv[2])
    output.write_text(json.dumps(extract(source), indent=2) + "\n", encoding="utf-8")
    return 0

if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
