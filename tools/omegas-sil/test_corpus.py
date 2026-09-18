#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from corpus import (  # noqa: E402
    TELEMETRY_REQUEST,
    canonicalize_sessions,
    parse_reply,
    read_portmon_zip,
    read_session_zip,
)


def response_bytes(request: bytes, payload: bytes, status: int = 0x53) -> bytes:
    body = bytes([status, len(payload)]) + payload
    checksum = sum(body) & 0xFF
    return request + body + bytes([checksum])


class CorpusProtocolTest(unittest.TestCase):
    def test_parse_reply_validates_real_mp48_envelope(self):
        payload = bytes(range(34))
        parsed = parse_reply(TELEMETRY_REQUEST, response_bytes(TELEMETRY_REQUEST, payload))
        self.assertEqual(0x53, parsed.status)
        self.assertEqual(payload, parsed.payload)
        self.assertTrue(parsed.valid_checksum)

    def test_session_zip_reconstructs_fragmented_usb_rx(self):
        payload = bytes((index * 7) & 0xFF for index in range(34))
        raw = response_bytes(TELEMETRY_REQUEST, payload)
        events = [
            self.event(1, 1000, "TX", TELEMETRY_REQUEST),
            self.event(2, 1001, "RX", raw[:2]),
            self.event(3, 1002, "RX", raw[2:11]),
            self.event(4, 1003, "RX", raw[11:]),
        ]
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "session.zip"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("events_0001.jsonl", "\n".join(json.dumps(row) for row in events))
            result = read_session_zip(path)

        self.assertEqual(1, len(result.telemetry))
        self.assertEqual(payload, result.telemetry[0].payload)
        self.assertEqual(0, result.invalid_replies)

    def test_portmon_zip_uses_same_envelope_parser(self):
        payload = bytes((255 - index) & 0xFF for index in range(34))
        raw = response_bytes(TELEMETRY_REQUEST, payload)
        chunks = (raw[:3], raw[3:20], raw[20:])
        lines = [
            "100  0.00000000  ProgBase.exe  IRP_MJ_WRITE  Silabser0  Length 3: 48 01 49",
        ]
        for index, chunk in enumerate(chunks, start=101):
            lines.append(f"{index}  0.00000000  ProgBase.exe  IRP_MJ_READ  Silabser0  Length {len(chunk)}")
            lines.append(
                f"{index}  0.00000000  SUCCESS Length {len(chunk)}: "
                + " ".join(f"{value:02X}" for value in chunk)
            )
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "PortmonLOGNOVO.zip"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("PortmonLOGNOVO.LOG", "\n".join(lines))
            result = read_portmon_zip(path)

        self.assertEqual(1, len(result.telemetry))
        self.assertEqual(payload, result.telemetry[0].payload)

    def test_exact_payload_duplicates_collapse_but_keep_aliases(self):
        payload = bytes(range(34))
        tx = self.transaction("a.zip", payload)
        corpus = canonicalize_sessions({
            "a.zip": [tx],
            "copy-of-a.zip": [self.transaction("copy-of-a.zip", payload)],
        })
        self.assertEqual(1, len(corpus.sessions))
        self.assertEqual(["a.zip", "copy-of-a.zip"], sorted(corpus.sessions[0].aliases))

    @staticmethod
    def event(sequence: int, recorded_at_ms: int, direction: str, data: bytes) -> dict:
        return {
            "format": "omegas-session-v1",
            "sequence": sequence,
            "recordedAtMs": recorded_at_ms,
            "type": "usb_raw",
            "source": "usb",
            "data": {
                "direction": direction,
                "size": len(data),
                "hex": " ".join(f"{value:02X}" for value in data),
            },
        }

    @staticmethod
    def transaction(session: str, payload: bytes):
        from corpus import RecordedTransaction

        return RecordedTransaction(
            source=session,
            session=session,
            sequence=1,
            recorded_at_ms=1000,
            request=TELEMETRY_REQUEST,
            status=0x53,
            payload=payload,
            raw_response=response_bytes(TELEMETRY_REQUEST, payload)[len(TELEMETRY_REQUEST):],
        )


if __name__ == "__main__":
    unittest.main()
