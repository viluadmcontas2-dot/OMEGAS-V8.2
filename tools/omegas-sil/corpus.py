#!/usr/bin/env python3
"""Deterministic OMEGAS SIL corpus reconstruction.

Offline/read-only. Python reconstructs transport evidence only; scientific
authority remains the production Kotlin runtime exercised by the SIL.
"""
from __future__ import annotations

import hashlib
import io
import json
import re
import sys
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Mapping

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.omegas.portmon_parser import iter_events, iter_transactions

TELEMETRY_REQUEST = bytes.fromhex("48 01 49")
ACK = 0x53
TELEMETRY_PAYLOAD_SIZE = 34
_HEX_PAIR = re.compile(r"[0-9A-Fa-f]{2}")


@dataclass(frozen=True)
class ParsedReply:
    status: int
    payload: bytes
    raw_response: bytes
    valid_checksum: bool


@dataclass(frozen=True)
class RecordedTransaction:
    source: str
    session: str
    sequence: int
    recorded_at_ms: int
    request: bytes
    status: int
    payload: bytes
    raw_response: bytes

    @property
    def is_telemetry(self) -> bool:
        return (
            self.request == TELEMETRY_REQUEST
            and self.status == ACK
            and len(self.payload) >= TELEMETRY_PAYLOAD_SIZE
        )

    def to_json(self) -> dict:
        return {
            "source": self.source,
            "session": self.session,
            "sequence": self.sequence,
            "recorded_at_ms": self.recorded_at_ms,
            "request_hex": self.request.hex().upper(),
            "status": self.status,
            "payload_hex": self.payload.hex().upper(),
            "raw_response_hex": self.raw_response.hex().upper(),
        }


@dataclass
class SessionReadResult:
    source: str
    transactions: list[RecordedTransaction] = field(default_factory=list)
    invalid_replies: int = 0
    ignored_events: int = 0
    unsupported_reason: str = ""

    @property
    def telemetry(self) -> list[RecordedTransaction]:
        return [item for item in self.transactions if item.is_telemetry]


@dataclass(frozen=True)
class CanonicalSession:
    canonical_id: str
    fingerprint: str
    aliases: tuple[str, ...]
    transactions: tuple[RecordedTransaction, ...]

    @property
    def telemetry(self) -> tuple[RecordedTransaction, ...]:
        return tuple(item for item in self.transactions if item.is_telemetry)


@dataclass(frozen=True)
class CanonicalCorpus:
    sessions: tuple[CanonicalSession, ...]

    @property
    def duplicate_groups(self) -> list[list[str]]:
        return [list(item.aliases) for item in self.sessions if len(item.aliases) > 1]


def _hex_bytes(value: str) -> bytes:
    return bytes(int(pair, 16) for pair in _HEX_PAIR.findall(value or ""))


def parse_reply(request: bytes, received: bytes) -> ParsedReply:
    """Validate the same echo + STATUS/LEN/PAYLOAD/CK envelope used by USB."""
    if not request:
        raise ValueError("empty request")
    if not received.startswith(request):
        raise ValueError("response does not start with exact request echo")
    body = received[len(request):]
    if len(body) < 3:
        raise ValueError("response missing status/length/checksum")
    status = body[0]
    length = body[1]
    expected_size = 2 + length + 1
    if len(body) < expected_size:
        raise ValueError(f"incomplete response: {len(body)}/{expected_size}")
    packet = body[:expected_size]
    payload = packet[2:2 + length]
    checksum = packet[-1]
    expected = sum(packet[:-1]) & 0xFF
    if checksum != expected:
        raise ValueError(
            f"checksum mismatch: expected {expected:02X}, received {checksum:02X}"
        )
    return ParsedReply(
        status=status,
        payload=payload,
        raw_response=packet,
        valid_checksum=True,
    )


def _record(
    source: str,
    session: str,
    sequence: int,
    recorded_at_ms: int,
    request: bytes,
    received: bytes,
) -> RecordedTransaction:
    parsed = parse_reply(request, received)
    return RecordedTransaction(
        source=source,
        session=session,
        sequence=sequence,
        recorded_at_ms=recorded_at_ms,
        request=request,
        status=parsed.status,
        payload=parsed.payload,
        raw_response=parsed.raw_response,
    )


def read_session_zip(path: Path) -> SessionReadResult:
    """Reconstruct transactions from OMEGAS SessionRecorder usb_raw events."""
    path = Path(path)
    result = SessionReadResult(source=str(path))
    active_request: bytes | None = None
    active_at = 0
    active_sequence = 0
    received = bytearray()
    output_sequence = 0

    def finalize() -> None:
        nonlocal active_request, active_at, active_sequence, received, output_sequence
        if active_request is None:
            return
        if received:
            try:
                output_sequence += 1
                result.transactions.append(
                    _record(
                        source=path.name,
                        session=path.name,
                        sequence=output_sequence,
                        recorded_at_ms=active_at,
                        request=active_request,
                        received=bytes(received),
                    )
                )
            except ValueError:
                result.invalid_replies += 1
        active_request = None
        active_at = 0
        active_sequence = 0
        received = bytearray()

    with zipfile.ZipFile(path) as archive:
        members = sorted(
            name for name in archive.namelist()
            if name.lower().endswith(".jsonl")
        )
        if not members:
            result.unsupported_reason = "NO_JSONL"
            return result
        for member in members:
            with archive.open(member) as stream:
                for raw in stream:
                    try:
                        event = json.loads(raw)
                    except Exception:
                        result.ignored_events += 1
                        continue
                    if event.get("type") != "usb_raw":
                        continue
                    data = event.get("data") or {}
                    direction = str(data.get("direction", "")).upper()
                    chunk = _hex_bytes(str(data.get("hex", "")))
                    if not chunk:
                        result.ignored_events += 1
                        continue
                    if direction == "TX":
                        finalize()
                        active_request = chunk
                        active_at = int(event.get("recordedAtMs", 0) or 0)
                        active_sequence = int(event.get("sequence", 0) or 0)
                    elif direction == "RX" and active_request is not None:
                        received.extend(chunk)
                    else:
                        result.ignored_events += 1
    finalize()
    if not result.transactions and not result.unsupported_reason:
        result.unsupported_reason = "NO_VALID_RAW_USB_TRANSACTIONS"
    return result


def read_portmon_zip(path: Path) -> SessionReadResult:
    """Reuse the repository's canonical Portmon event/transaction parser."""
    path = Path(path)
    result = SessionReadResult(source=str(path))
    with zipfile.ZipFile(path) as archive:
        members = [
            name for name in archive.namelist()
            if name.lower().endswith((".log", ".txt"))
        ]
        if not members:
            result.unsupported_reason = "NO_PORTMON_LOG"
            return result
        member = sorted(members)[0]
        with archive.open(member) as binary:
            text = io.TextIOWrapper(binary, encoding="utf-8", errors="replace")
            for transaction in iter_transactions(iter_events(text)):
                request = _hex_bytes(transaction.request_hex)
                received = _hex_bytes(transaction.response_hex)
                if not request or not received:
                    result.invalid_replies += 1
                    continue
                try:
                    item = _record(
                        source=path.name,
                        session=path.name,
                        sequence=transaction.sequence,
                        recorded_at_ms=int(round(transaction.request_timestamp * 1000.0)),
                        request=request,
                        received=received,
                    )
                except ValueError:
                    result.invalid_replies += 1
                    continue
                result.transactions.append(item)
    if not result.transactions:
        result.unsupported_reason = "NO_VALID_PORTMON_TRANSACTIONS"
    return result


def telemetry_fingerprint(transactions: Iterable[RecordedTransaction]) -> str:
    digest = hashlib.sha256()
    count = 0
    for item in transactions:
        if not item.is_telemetry:
            continue
        digest.update(len(item.payload).to_bytes(2, "little"))
        digest.update(item.payload[:TELEMETRY_PAYLOAD_SIZE])
        count += 1
    if count == 0:
        return ""
    digest.update(count.to_bytes(8, "little"))
    return digest.hexdigest()


def canonicalize_sessions(
    sessions: Mapping[str, Iterable[RecordedTransaction]],
) -> CanonicalCorpus:
    groups: dict[str, dict] = {}
    for alias, values in sorted(sessions.items()):
        tx = tuple(values)
        fingerprint = telemetry_fingerprint(tx)
        if not fingerprint:
            continue
        group = groups.setdefault(
            fingerprint,
            {"aliases": [], "transactions": tx},
        )
        group["aliases"].append(alias)

    canonical = []
    for fingerprint, group in sorted(
        groups.items(),
        key=lambda pair: sorted(pair[1]["aliases"])[0],
    ):
        aliases = tuple(sorted(group["aliases"]))
        canonical.append(
            CanonicalSession(
                canonical_id=aliases[0],
                fingerprint=fingerprint,
                aliases=aliases,
                transactions=tuple(group["transactions"]),
            )
        )
    return CanonicalCorpus(tuple(canonical))


def write_transactions_jsonl(result: SessionReadResult, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="\n") as stream:
        for item in result.transactions:
            stream.write(json.dumps(item.to_json(), sort_keys=True) + "\n")
