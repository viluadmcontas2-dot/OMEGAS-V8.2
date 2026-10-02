#!/usr/bin/env python3
"""Portmon read-only payload equivalence regressions for lazy extraction.

Standalone unittest script, discovered by tools/run_checks.py.
It never touches a serial/USB port, device, network or ECU.
"""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts" / "omegas"))

from portmon_parser import extract_payload, iter_events, iter_transactions


def pair(index: int, operation: str, event_detail: str,
         success_detail: str = "", success_index: int | None = None) -> list[str]:
    completed = index if success_index is None else success_index
    return [
        f"{index} {index}.000 ProgBase.exe {operation} Silabser0 {event_detail}\n",
        f"{completed} 0.002 SUCCESS {success_detail}\n",
    ]


class PortmonLazyPayloadParityTest(unittest.TestCase):
    def test_success_read_payload_overrides_event_detail(self) -> None:
        events = list(iter_events(pair(
            2, "IRP_MJ_READ", "Length 2: aa bb", "Length 2: cc dd")))
        self.assertEqual(len(events), 1)
        self.assertEqual(events[0].payload_hex, "CC DD")

    def test_blank_success_falls_back_to_original_event_detail(self) -> None:
        events = list(iter_events(pair(3, "IRP_MJ_WRITE", "Length 2: aa bb")))
        self.assertEqual([event.payload_hex for event in events], ["AA BB"])

    def test_non_hex_success_falls_back_to_original_event_detail(self) -> None:
        events = list(iter_events(pair(
            4, "IRP_MJ_READ", "Length 2: 11 22", "No payload")))
        self.assertEqual([event.payload_hex for event in events], ["11 22"])

    def test_mismatched_success_drops_pending_event(self) -> None:
        lines = pair(5, "IRP_MJ_WRITE", "Length 2: aa bb", success_index=6)
        lines.append("5 0.004 SUCCESS Length 2: cc dd\n")
        self.assertEqual(list(iter_events(lines)), [])

    def test_purge_and_split_read_transaction_content_is_preserved(self) -> None:
        lines: list[str] = []
        lines += pair(1, "IOCTL_SERIAL_PURGE", "Purge: RXCLEAR")
        lines += pair(2, "IRP_MJ_WRITE", "Length 2: aa bb")
        lines += pair(3, "IRP_MJ_READ", "Length 2: 11 22", "Length 2: 33 44")
        lines += pair(4, "IRP_MJ_READ", "Length 2: 55 66")
        transactions = list(iter_transactions(iter_events(lines)))
        self.assertEqual(len(transactions), 1)
        item = transactions[0]
        self.assertEqual((item.request_hex, item.response_hex, item.purge_before),
                         ("AA BB", "33 44 55 66", True))

    def test_hex_whitespace_and_case_normalization_is_unchanged(self) -> None:
        self.assertEqual(extract_payload("prefix Length 2: aa \tbb \t"), "AA BB")


if __name__ == "__main__":
    unittest.main()
