"""Byte-preserving Portmon parser for OMEGAS Amarelo evidence.

This module intentionally decodes only wire facts that are mechanically
supported by the MP48 frames. Product/native semantics live in evidence
manifests and higher-level contracts.
"""
from __future__ import annotations

from dataclasses import dataclass
import re
from typing import Iterable, Iterator, Optional


_HEX_PAYLOAD = re.compile(r"Length\s+\d+:\s*((?:[0-9A-Fa-f]{2}(?:\s+|$))+)")
_EVENT = re.compile(
    r"^(?:\d+:\s*)?(\d+)\s+([0-9.]+)\s+ProgBase\.exe\s+"
    r"(IRP_MJ_WRITE|IRP_MJ_READ)\s+Silabser\d+\s*(.*)$"
)
_SUCCESS = re.compile(r"^(?:\d+:\s*)?(\d+)\s+([0-9.]+)\s+SUCCESS\s*(.*)$")


@dataclass(frozen=True)
class PortmonTransaction:
    event_id: int
    write_elapsed_s: float
    request: bytes
    response: bytes


@dataclass(frozen=True)
class ObjectRequest:
    kind: str
    command: int
    address: int
    value: Optional[int] = None
    index: Optional[int] = None
    checksum: Optional[int] = None
    checksum_valid: bool = False
    raw: bytes = b""


def _payload(text: str) -> bytes:
    match = _HEX_PAYLOAD.search(text)
    if not match:
        return b""
    return bytes.fromhex(" ".join(match.group(1).split()))


def _frame_checksum_valid(frame: bytes) -> bool:
    return len(frame) >= 2 and (sum(frame[:-1]) & 0xFF) == frame[-1]


def decode_object_request(request: bytes) -> ObjectRequest:
    """Decode the generic MP48 object-request envelope without inventing semantics."""
    if len(request) < 4:
        raise ValueError("MP48 object request is too short")

    command = request[0]
    address = request[1] | (request[2] << 8)
    checksum = request[-1]
    valid = _frame_checksum_valid(request)
    if not valid:
        raise ValueError("MP48 request checksum mismatch")

    if command == 0x12:
        if len(request) != 5:
            raise ValueError("WRITE_U8 must contain command,address,value,checksum")
        return ObjectRequest(
            kind="WRITE_U8",
            command=command,
            address=address,
            value=request[3],
            checksum=checksum,
            checksum_valid=True,
            raw=bytes(request),
        )
    if command == 0x09:
        if len(request) != 4:
            raise ValueError("READ_SCALAR must be 4 bytes")
        return ObjectRequest(
            kind="READ_SCALAR",
            command=command,
            address=address,
            checksum=checksum,
            checksum_valid=True,
            raw=bytes(request),
        )
    if command == 0x29:
        if len(request) != 4:
            raise ValueError("READ_VECTOR must be 4 bytes")
        return ObjectRequest(
            kind="READ_VECTOR",
            command=command,
            address=address,
            checksum=checksum,
            checksum_valid=True,
            raw=bytes(request),
        )
    if command == 0x0A:
        if len(request) != 5:
            raise ValueError("READ_INDEXED must contain index before checksum")
        return ObjectRequest(
            kind="READ_INDEXED",
            command=command,
            address=address,
            index=request[3],
            checksum=checksum,
            checksum_valid=True,
            raw=bytes(request),
        )
    return ObjectRequest(
        kind="OTHER",
        command=command,
        address=address,
        checksum=checksum,
        checksum_valid=True,
        raw=bytes(request),
    )


def parse_portmon_lines(lines: Iterable[str]) -> Iterator[PortmonTransaction]:
    """Reconstruct write + fragmented read-response transactions.

    Portmon records the request write and each read fragment as separate events.
    We preserve the exact request bytes and concatenate only successful read
    payloads until the next write starts.
    """
    current_event: Optional[int] = None
    current_elapsed = 0.0
    current_request = b""
    current_response = bytearray()

    pending_kind: Optional[str] = None
    pending_event: Optional[int] = None
    pending_payload = b""

    def finish() -> Optional[PortmonTransaction]:
        if current_event is None or not current_request:
            return None
        return PortmonTransaction(
            event_id=current_event,
            write_elapsed_s=current_elapsed,
            request=current_request,
            response=bytes(current_response),
        )

    for raw_line in lines:
        line = raw_line.strip("\r\n")
        event = _EVENT.match(line)
        if event:
            event_id = int(event.group(1))
            elapsed = float(event.group(2))
            kind = event.group(3)
            data = _payload(event.group(4))
            pending_kind = kind
            pending_event = event_id
            pending_payload = data

            if kind == "IRP_MJ_WRITE":
                previous = finish()
                if previous is not None:
                    yield previous
                current_event = event_id
                current_elapsed = elapsed
                current_request = data
                current_response = bytearray()
            continue

        success = _SUCCESS.match(line)
        if not success or pending_event is None:
            continue
        if int(success.group(1)) != pending_event:
            continue

        success_payload = _payload(success.group(3))
        data = success_payload or pending_payload
        if pending_kind == "IRP_MJ_READ" and current_event is not None and data:
            current_response.extend(data)

        pending_kind = None
        pending_event = None
        pending_payload = b""

    last = finish()
    if last is not None:
        yield last
