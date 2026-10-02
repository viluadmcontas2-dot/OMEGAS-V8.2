"""Benchmarks for the offline Portmon serial log parser.

The synthetic log is built from the real MP48 request/response pairs stored in
``tests/fixtures/portmon-autocal-real-sample.json`` and laid out in the exact
Portmon text format the parser expects (request line + SUCCESS line, purges and
split reads).
"""
import gzip
import json
from pathlib import Path

import pytest

import portmon_parser

ROOT = Path(__file__).resolve().parents[1]
SAMPLE = json.loads((ROOT / "tests/fixtures/portmon-autocal-real-sample.json").read_text("utf-8"))
PAIRS = [(item["request"], item["response"]) for item in SAMPLE["transactions"]]


def build_log(transactions: int) -> list[str]:
    lines: list[str] = []
    index = 0
    timestamp = 0.0

    def emit(op: str, payload: str) -> None:
        nonlocal index, timestamp
        index += 1
        timestamp += 0.0125
        length = len(payload.split()) if payload else 0
        detail = f"Length {length}: {payload}" if payload else "Purge: RXCLEAR"
        lines.append(f"{index}\t{timestamp:.8f}\tProgBase.exe\t{op}\tSilabser0\t{detail}\n")
        lines.append(f"{index}\t0.00{index % 97:04d}\tSUCCESS\t{'Length ' + str(length) + ': ' + payload if op == 'IRP_MJ_READ' else ''}\n")

    for sequence in range(transactions):
        request, response = PAIRS[sequence % len(PAIRS)]
        if sequence % 16 == 0:
            emit("IOCTL_SERIAL_PURGE", "")
        emit("IRP_MJ_WRITE", request)
        tokens = response.split()
        middle = len(tokens) // 2
        emit("IRP_MJ_READ", " ".join(tokens[:middle]))
        emit("IRP_MJ_READ", " ".join(tokens[middle:]))
    return lines


LOG_SMALL = build_log(200)
LOG_LARGE = build_log(2_000)


@pytest.mark.parametrize("lines", [LOG_SMALL, LOG_LARGE], ids=["200_tx", "2000_tx"])
def test_parse_transactions(benchmark, lines):
    result = benchmark(lambda: list(portmon_parser.iter_transactions(portmon_parser.iter_events(lines))))
    assert len(result) == sum(1 for line in lines if "IRP_MJ_WRITE" in line)


def test_iter_events(benchmark):
    result = benchmark(lambda: list(portmon_parser.iter_events(LOG_LARGE)))
    assert result


def test_extract_payload(benchmark):
    details = [line.split("\t", 5)[5] for line in LOG_SMALL if "Length" in line and "ProgBase" in line]

    def run():
        return [portmon_parser.extract_payload(detail) for detail in details]

    assert all(benchmark(run))


def test_summarize_gzip_log(benchmark, tmp_path):
    path = tmp_path / "PortmonAUTOCAL.LOG.gz"
    with gzip.open(path, "wt", encoding="utf-8") as handle:
        handle.writelines(LOG_LARGE)
    summary = benchmark(portmon_parser.summarize, path)
    assert summary["transactions"] == 2_000
