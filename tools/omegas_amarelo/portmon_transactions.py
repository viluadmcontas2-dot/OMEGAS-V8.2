"""OMEGAS Amarelo Portmon ground-truth parser.

Timing rule proved against the real ProgBase AutoCal fixture:
transaction.at_ms is the cumulative duration of *all* completed Portmon
operations before the IRP_MJ_WRITE that starts that transaction.
The per-line second numeric field on ProgBase operation rows is not treated
as an absolute timestamp.
"""
from __future__ import annotations

from dataclasses import dataclass
import re
from typing import Iterable, Iterator, Optional

_EVENT = re.compile(r"^(\d+)\s+([0-9.]+)\s+ProgBase\.exe\s+([^\s]+)\s+Silabser\d+\s*(.*)$")
_SUCCESS = re.compile(r"^(\d+)\s+([0-9.]+)\s+SUCCESS\s*(.*)$")
_HEX = re.compile(r"Length\s+\d+:\s*((?:[0-9A-Fa-f]{2}(?:\s+|$))+)")

@dataclass(frozen=True)
class PortmonOperation:
    index: int
    kind: str
    event_value_s: float
    duration_ms: float
    event_detail: str
    success_detail: str
    data: bytes

@dataclass(frozen=True)
class PortmonTransaction:
    sequence: int
    portmon_index: int
    at_ms: float
    request: bytes
    response: bytes

def _data(detail: str) -> bytes:
    match = _HEX.search(detail)
    if not match:
        return b""
    return bytes.fromhex(" ".join(match.group(1).split()))

def iter_operations(lines: Iterable[str]) -> Iterator[PortmonOperation]:
    pending: Optional[tuple[int, float, str, str, bytes]] = None
    for raw in lines:
        line = raw.rstrip("\r\n")
        event = _EVENT.match(line)
        if event:
            pending = (
                int(event.group(1)),
                float(event.group(2)),
                event.group(3),
                event.group(4),
                _data(event.group(4)),
            )
            continue
        success = _SUCCESS.match(line)
        if not success or pending is None:
            continue
        if int(success.group(1)) != pending[0]:
            continue
        index, event_value_s, kind, event_detail, event_data = pending
        pending = None
        success_detail = success.group(3)
        success_data = _data(success_detail)
        yield PortmonOperation(
            index=index,
            kind=kind,
            event_value_s=event_value_s,
            duration_ms=float(success.group(2)) * 1000.0,
            event_detail=event_detail,
            success_detail=success_detail,
            data=success_data or event_data,
        )

def iter_transactions(lines: Iterable[str]) -> Iterator[PortmonTransaction]:
    elapsed_ms = 0.0
    sequence = 0
    current_index: Optional[int] = None
    current_at_ms = 0.0
    current_request = b""
    response = bytearray()

    for operation in iter_operations(lines):
        start_ms = elapsed_ms

        if operation.kind == "IRP_MJ_WRITE":
            if current_index is not None:
                yield PortmonTransaction(
                    sequence=sequence,
                    portmon_index=current_index,
                    at_ms=current_at_ms,
                    request=current_request,
                    response=bytes(response),
                )
            sequence += 1
            current_index = operation.index
            current_at_ms = start_ms
            current_request = operation.data
            response = bytearray()
        elif operation.kind == "IRP_MJ_READ" and current_index is not None and operation.data:
            response.extend(operation.data)

        elapsed_ms += operation.duration_ms

    if current_index is not None:
        yield PortmonTransaction(
            sequence=sequence,
            portmon_index=current_index,
            at_ms=current_at_ms,
            request=current_request,
            response=bytes(response),
        )

def additive_checksum_ok(frame: bytes) -> bool:
    return len(frame) >= 2 and (sum(frame[:-1]) & 0xFF) == frame[-1]

def response_payload(request: bytes, response: bytes) -> bytes:
    if not request or not response.startswith(request):
        raise ValueError("response does not echo request")
    tail = response[len(request):]
    if len(tail) < 3 or tail[0] != 0x53:
        raise ValueError("missing 0x53 response marker")
    payload_len = tail[1]
    expected = payload_len + 3
    if len(tail) != expected:
        raise ValueError(f"response tail length {len(tail)} != {expected}")
    if not additive_checksum_ok(tail):
        raise ValueError("response tail checksum mismatch")
    return tail[2:2 + payload_len]

def valid_echo_response(transaction: PortmonTransaction) -> bool:
    try:
        response_payload(transaction.request, transaction.response)
        return additive_checksum_ok(transaction.request)
    except ValueError:
        return False

def address16(request: bytes) -> Optional[int]:
    if len(request) < 4:
        return None
    if request[0] not in {0x09, 0x0A, 0x12, 0x14, 0x29, 0x2A}:
        return None
    return request[1] | (request[2] << 8)

def u16le_values(payload: bytes) -> tuple[int, ...]:
    if len(payload) % 2:
        raise ValueError("u16 payload must have even length")
    return tuple(payload[i] | (payload[i + 1] << 8) for i in range(0, len(payload), 2))

def q14_values(payload: bytes) -> tuple[float, ...]:
    return tuple(value / 16384.0 for value in u16le_values(payload))


PROGBASE_AUTOCAL_ACTION_CODES = {
    "RESET_PETROL": 0x01,
    "RESET_GAS": 0x02,
    "RESET_ALL": 0x04,
    "AUTO_MATCH": 0x08,
}

def progbase_autocal_action_frame(action_code: int) -> bytes:
    """Reconstruct the ProgBase native-action request proven from binary code.

    Binary evidence:
    - handlers push action codes 1/2/4/8 into shared dispatcher 0x00517568;
    - native bridge 0x00512280 builds payload [0x04, action];
    - transport 0x009244C8 serializes header word (0x24 << 8) | 2 little-endian;
    - byte append accumulates an additive checksum;
    - finalize appends that checksum.

    Only action code 4 is also observed byte-for-byte in the current raw logs.
    This is engineering evidence tooling, not permission to transmit
    unobserved actions to a real ECU.
    """
    if action_code not in PROGBASE_AUTOCAL_ACTION_CODES.values():
        raise ValueError(f"unsupported ProgBase AutoCal action code: {action_code}")
    body = bytes((0x02, 0x24, 0x04, action_code))
    return body + bytes((sum(body) & 0xFF,))
