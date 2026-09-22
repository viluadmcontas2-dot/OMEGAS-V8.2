from __future__ import annotations

import argparse
from collections import Counter
from hashlib import sha256
import json
from pathlib import Path
from statistics import median
from typing import Iterable

from tools.omegas_amarelo.portmon_transactions import (
    iter_transactions,
    response_payload,
    valid_echo_response,
)

LIVE = "48 01 49"
AUTOCAL_SLOW = (
    "29 5B 01 85", "29 5C 01 86", "29 5D 01 87", "29 5E 01 88",
    "29 5F 01 89", "29 60 01 8A", "29 61 01 8B", "29 62 01 8C",
    "29 63 01 8D", "29 6F 01 99", "29 70 01 9A", "48 0B 53",
)
REFERENCE = ("29 8D 01 B7", "29 8E 01 B8")
AUTOMATCH_COUNT = "09 74 01 7E"


def hex_bytes(value: bytes) -> str:
    return value.hex(" ").upper()


def file_sha256(path: Path) -> str:
    digest = sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def cadence_ms(rows, request: str, max_gap_ms: float) -> float | None:
    stamps = [tx.at_ms for tx in rows if hex_bytes(tx.request) == request]
    deltas = [
        b - a for a, b in zip(stamps, stamps[1:])
        if 0.0 < (b - a) <= max_gap_ms
    ]
    return median(deltas) if deltas else None


def automatch_transition_sequences(rows) -> set[int]:
    selected: set[int] = set()
    previous: int | None = None
    for tx in rows:
        if hex_bytes(tx.request) != AUTOMATCH_COUNT or not valid_echo_response(tx):
            continue
        payload = response_payload(tx.request, tx.response)
        if len(payload) != 1:
            continue
        current = payload[0]
        if previous is None or current != previous:
            selected.add(tx.sequence)
        previous = current
    return selected


def control_sequence(tx) -> bool:
    req = tx.request
    return (
        req.startswith(bytes.fromhex("12 4A 01"))
        or req.startswith(bytes.fromhex("02 24 04"))
    )


def build_fixture(raw_path: Path, *, window_ms: float = 20_000.0) -> dict:
    with raw_path.open("r", encoding="latin1", errors="replace") as stream:
        rows = list(iter_transactions(stream))

    counts = Counter(hex_bytes(tx.request) for tx in rows)
    selected: dict[int, set[str]] = {}

    def choose(sequence: int, reason: str) -> None:
        selected.setdefault(sequence, set()).add(reason)

    first_by_command: set[str] = set()
    for tx in rows:
        request = hex_bytes(tx.request)
        if tx.response.startswith(tx.request) and request not in first_by_command:
            choose(tx.sequence, "FIRST_OBSERVED_COMMAND")
            first_by_command.add(request)
        if tx.at_ms <= window_ms and tx.response.startswith(tx.request):
            choose(tx.sequence, "INITIAL_TIMING_WINDOW")
        if control_sequence(tx) and tx.response.startswith(tx.request):
            choose(tx.sequence, "CONTROL_EVENT")

    for sequence in automatch_transition_sequences(rows):
        choose(sequence, "AUTOMATCH_COUNT_EDGE")

    compact = []
    by_sequence = {tx.sequence: tx for tx in rows}
    for sequence in sorted(selected):
        tx = by_sequence[sequence]
        compact.append({
            "sequence": tx.sequence,
            "portmon_index": tx.portmon_index,
            "at_ms": round(tx.at_ms, 3),
            "request": hex_bytes(tx.request),
            "response": hex_bytes(tx.response),
            "selectionReasons": sorted(selected[sequence]),
        })

    slow_cadence = {
        request: cadence_ms(rows, request, 10_000.0)
        for request in (*AUTOCAL_SLOW, *REFERENCE)
    }
    slow_cadence = {
        key: round(value, 3)
        for key, value in slow_cadence.items()
        if value is not None
    }
    live_cadence = cadence_ms(rows, LIVE, 200.0)

    return {
        "schema": "omegas.amarelo.portmon-replay.v1",
        "sourceRawName": raw_path.name,
        "sourceRawSha256": file_sha256(raw_path),
        "timingRule": (
            "transaction.at_ms = cumulative duration of every completed Portmon "
            "operation before the IRP_MJ_WRITE that starts the transaction"
        ),
        "recipe": {
            "builder": "tools/omegas_amarelo/build_portmon_replay_fixture.py",
            "initialTimingWindowMs": window_ms,
            "selection": [
                "every echoed request's first observed command",
                "every echoed transaction inside the initial timing window",
                "all AUTO_CAL_ENABLE and native 0x24 control events",
                "every observed NUM_AUTOMATCH_EXECUTED value edge",
            ],
        },
        "observedWriteCount": len(rows),
        "observedUniqueCommands": len(counts),
        "commandCounts": dict(sorted(counts.items())),
        "metrics": {
            "liveMedianMs": None if live_cadence is None else round(live_cadence, 3),
            "slowMedianMs": slow_cadence,
        },
        "transactions": compact,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("raw_path", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--window-ms", type=float, default=20_000.0)
    args = parser.parse_args()

    fixture = build_fixture(args.raw_path, window_ms=args.window_ms)
    rendered = json.dumps(fixture, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")


if __name__ == "__main__":
    main()
