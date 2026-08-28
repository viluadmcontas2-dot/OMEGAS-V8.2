#!/usr/bin/env python3
"""Parse Portmon serial logs into deterministic OMEGAS transactions.

Passive/offline only: never opens serial, USB, network or device paths.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import re
from collections import Counter
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable, Iterator, TextIO

EVENT_RE = re.compile(r"^(?P<index>\d+)\s+(?P<time>\d+\.\d+)\s+ProgBase\.exe\s+(?P<op>IRP_MJ_WRITE|IRP_MJ_READ|IOCTL_SERIAL_PURGE)\s+Silabser\d+\s*(?P<detail>.*)$")
SUCCESS_RE = re.compile(r"^(?P<index>\d+)\s+(?P<duration>\d+\.\d+)\s+SUCCESS\s*(?P<detail>.*)$")
HEX_RE = re.compile(r"Length\s+\d+:\s*((?:[0-9A-Fa-f]{2}(?:\s+|$))+)")

@dataclass(frozen=True)
class SerialEvent:
    index: int
    timestamp: float
    duration: float
    operation: str
    payload_hex: str

@dataclass(frozen=True)
class Transaction:
    sequence: int
    request_index: int
    request_hex: str
    response_hex: str
    request_timestamp: float
    response_duration: float
    purge_before: bool

def open_text(path: Path) -> TextIO:
    return gzip.open(path, "rt", encoding="utf-8", errors="replace") if path.suffix == ".gz" else path.open("rt", encoding="utf-8", errors="replace")

def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

def extract_payload(detail: str) -> str:
    match = HEX_RE.search(detail)
    return "" if not match else " ".join(match.group(1).upper().split())

def iter_events(lines: Iterable[str]) -> Iterator[SerialEvent]:
    pending = None
    for raw in lines:
        line = raw.rstrip("\r\n")
        event = EVENT_RE.match(line)
        if event:
            pending = (int(event.group("index")), float(event.group("time")), event.group("op"), extract_payload(event.group("detail")))
            continue
        success = SUCCESS_RE.match(line)
        if success and pending is not None:
            index, timestamp, operation, request_payload = pending
            if int(success.group("index")) == index:
                payload = extract_payload(success.group("detail")) or request_payload
                yield SerialEvent(index, timestamp, float(success.group("duration")), operation, payload)
            pending = None

def iter_transactions(events: Iterable[SerialEvent]) -> Iterator[Transaction]:
    purge_seen = False
    active = None
    response_parts = []
    sum_duration = 0.0
    active_purge = False
    sequence = 0
    def emit():
        nonlocal sequence
        if active is None:
            return None
        sequence += 1
        return Transaction(sequence, active.index, active.payload_hex, " ".join(response_parts), active.timestamp, sum_duration, active_purge)
    for event in events:
        if event.operation == "IOCTL_SERIAL_PURGE":
            purge_seen = True
        elif event.operation == "IRP_MJ_WRITE":
            previous = emit()
            if previous is not None:
                yield previous
            active = event
            response_parts = []
            sum_duration = 0.0
            active_purge = purge_seen
            purge_seen = False
        elif event.operation == "IRP_MJ_READ" and active is not None:
            if event.payload_hex:
                response_parts.append(event.payload_hex)
            sum_duration += event.duration
    final = emit()
    if final is not None:
        yield final

def parse(path: Path):
    with open_text(path) as handle:
        yield from iter_transactions(iter_events(handle))

def summarize(path: Path):
    transactions = list(parse(path))
    counts = Counter(item.request_hex for item in transactions)
    return {"input":str(path),"sha256":sha256(path),"transactions":len(transactions),"distinct_commands":len(counts),"empty_responses":sum(not item.response_hex for item in transactions),"responses_starting_with_request":sum(item.response_hex.startswith(item.request_hex) for item in transactions if item.request_hex),"top_commands":counts.most_common(25)}

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("--jsonl", type=Path)
    parser.add_argument("--summary", type=Path)
    args = parser.parse_args()
    result = summarize(args.input)
    if args.jsonl:
        count = 0
        with args.jsonl.open("w", encoding="utf-8", newline="\n") as target:
            for item in parse(args.input):
                target.write(json.dumps(asdict(item), sort_keys=True, separators=(",", ":")) + "\n")
                count += 1
        result["jsonl_transactions"] = count
    encoded = json.dumps(result, indent=2, sort_keys=True) + "\n"
    args.summary.write_text(encoded, encoding="utf-8") if args.summary else print(encoded, end="")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
