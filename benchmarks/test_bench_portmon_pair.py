"""Matched-run Portmon baseline vs optimized executable benchmark.

Both variants run over the exact same immutable 2,000-transaction synthetic
fixture and on the very same CodSpeed runner; no historic runner subtraction.
Legacy implementation is copied verbatim (function rename only) from
OmegasPlatina fork commit 4fbf1cb6, before the deferred extraction change.
It never touches USB, network, serial ports or an ECU.
"""
from __future__ import annotations

from portmon_parser import EVENT_RE, SUCCESS_RE, SerialEvent, extract_payload, iter_events
from test_bench_portmon_parser import LOG_LARGE


def legacy_iter_events(lines: Iterable[str]) -> Iterator[SerialEvent]:
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


# Collection-time equality detects fixture drift and output differences before
# any benchmark is uploaded. Existing six parity tests cover malformed/fallback
# edge cases independently.
EXPECTED = list(legacy_iter_events(LOG_LARGE))
assert len(EXPECTED) > 0
assert list(iter_events(LOG_LARGE)) == EXPECTED


def test_legacy_events_same_runner(benchmark):
    result = benchmark(lambda: list(legacy_iter_events(LOG_LARGE)))
    assert result == EXPECTED


def test_optimized_events_same_runner(benchmark):
    result = benchmark(lambda: list(iter_events(LOG_LARGE)))
    assert result == EXPECTED
