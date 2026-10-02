"""Benchmarks for the OBD x MP48 evidence qualification contract."""
import json
from pathlib import Path

import obd_mp48_contract as contract

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = json.loads((ROOT / "lab/contracts/fixtures/obd_mp48_replay.json").read_text("utf-8"))


def build_stream(frames: int) -> list[dict]:
    """Deterministic live stream mixing accepted frames and every rejection path."""
    base = FIXTURES[0]["sample"]
    mutations = (
        {},
        {"mp48_present": False},
        {"obd_present": False},
        {"obd_at_ms": base["mp48_at_ms"] + 400},
        {"obd_rpm": base["mp48_rpm"] + 300},
        {"fuel": "ALCOOL"},
        {"gasoline_injection_ms": 0},
        {"coolant_c": 40},
        {"closed_loop": False},
        {"fuel_transition": True},
        {"stft": 120.0},
        {"physical_cell": None},
    )
    stream = []
    for index in range(frames):
        sample = dict(base)
        sample["mp48_rpm"] = 800 + (index * 37) % 3600
        sample["obd_rpm"] = sample["mp48_rpm"] - 8
        sample["stft"] = ((index * 13) % 40) - 20.0
        sample.update(mutations[index % len(mutations)])
        stream.append(sample)
    return stream


STREAM = build_stream(5_000)


def test_qualify_replay_fixtures(benchmark):
    samples = [fixture["sample"] for fixture in FIXTURES]
    result = benchmark(lambda: [contract.qualify(sample) for sample in samples])
    assert [item.accepted for item in result] == [fixture["accepted"] for fixture in FIXTURES]


def test_qualify_live_stream(benchmark):
    result = benchmark(lambda: sum(contract.qualify(sample).accepted for sample in STREAM))
    assert 0 < result < len(STREAM)


def test_condition_deduplication(benchmark):
    conditions = [
        {
            "origin_device_id": f"device-{index % 3}",
            "condition_id": f"c-{index % 1_500}",
            "map_epoch_id": "m-4",
            "curve_epoch_id": "k-2",
        }
        for index in range(5_000)
    ]

    def run():
        seen: set[tuple] = set()
        return sum(contract.is_duplicate(condition, seen) for condition in conditions)

    assert benchmark(run) > 0


def test_signal_and_parallel_comparison(benchmark):
    readings = [(((index * 7) % 60) - 30.0, ((index * 11) % 20) - 10.0) for index in range(5_000)]

    def run():
        return [
            (contract.direct_gnv_signal(gnv), contract.parallel_comparison(gnv, gasoline))
            for gnv, gasoline in readings
        ]

    assert len(benchmark(run)) == len(readings)
