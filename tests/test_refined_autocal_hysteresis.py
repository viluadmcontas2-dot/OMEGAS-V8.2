"""Lote E: histerese (E2), ganho decrescente (E1) e cobertura por episódio (E3) nas sessões reais.

Espelho do oráculo de tools/autocal_refine/refined_oracle.py; o Kotlin tem os mesmos casos em
AutoMatchHysteresisTest. Os números medidos saem na tela (python3 tests/test_refined_autocal_hysteresis.py).
"""
import gzip
import json
import math
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/autocal_refine"))

import blind_telemetry_test as blind  # noqa: E402
import refined_oracle as oracle  # noqa: E402

REAL = ROOT / "fixtures/autocal/real"
SESSIONS = ("automatch_2026-10-01_1301", "ref_2026-10-01_1719", "gnv_only_2026-09-30_0931")


def load(name):
    with gzip.open(REAL / f"{name}.json.gz", "rt", encoding="utf-8") as handle:
        return json.load(handle)


def proposal_sequence(data, hold, gain, episodes):
    """(reversões de sinal, passos propostos, passo médio) das propostas sobre snapshots consecutivos.

    A telemetria entra até o instante de cada snapshot (a condução acumula) e cada ponto proposto conta uma
    passada (o dono aplicou), como o RefinementJournal conta."""
    passes = [0] * 30
    last = [0.0] * 30
    reversals = 0
    steps = []
    for snap in data["snapshots"]:
        pairs, eps = blind.telemetry_pairs(data["telemetry"], snap["capturedAtMs"], with_episodes=True, petrol_until=True)
        scale = [oracle.pass_gain(p) for p in passes] if gain else None
        result = oracle.refine(snap, telemetry_pairs=pairs, point_gain_scale=scale,
                               telemetry_episodes=eps if episodes else None, hold_log=hold)
        if result["mode"] != "EQUIVALENCE":
            continue
        for j in range(30):
            if result["origins"][j] == "HELD" or result["refinedRaw"][j] == result["currentRaw"][j]:
                continue
            delta = math.log(result["refinedRaw"][j] / result["currentRaw"][j])
            passes[j] += 1
            steps.append(abs(delta))
            if last[j] and (delta > 0) != (last[j] > 0):
                reversals += 1
            last[j] = delta
    return reversals, len(steps), (sum(steps) / len(steps) if steps else 0.0)


class PassGain(unittest.TestCase):
    def test_gain_is_1_then_0_7_then_0_5_independent_of_verdict(self):
        self.assertEqual([oracle.pass_gain(n) for n in (0, 1, 2, 3, 9)], [1.0, 0.7, 0.5, 0.5, 0.5])

    def test_gain_scale_shrinks_the_proposed_step(self):
        snap = next(s for s in load("ref_2026-10-01_1719")["snapshots"] if s["sequence"] == 95)
        full = oracle.refine(snap)
        half = oracle.refine(snap, point_gain_scale=[0.5] * 30)
        moved = [j for j in range(30) if full["refinedRaw"][j] != full["currentRaw"][j]]
        self.assertTrue(moved)
        step = lambda r: sum(abs(r["refinedRaw"][j] - r["currentRaw"][j]) for j in range(30))
        self.assertLess(step(half), step(full))


class Hysteresis(unittest.TestCase):
    def test_hold_keeps_coherence_and_the_native_range(self):
        snap = next(s for s in load("ref_2026-10-01_1719")["snapshots"] if s["sequence"] == 95)
        result = oracle.refine(snap, hold_log=oracle.HOLD_MIN_STEP_LOG)
        self.assertLessEqual(result["metricsAfter"]["maxElasticity"], oracle.E_MAX + 0.01)
        for new, old, origin in zip(result["refinedRaw"], result["currentRaw"], result["origins"]):
            if origin == "HELD":
                self.assertEqual(new, old)
            else:
                self.assertTrue(oracle.MIN_RAW_PROPOSAL <= new <= oracle.MAX_RAW_PROPOSAL)

    def test_small_step_is_held_and_big_step_is_kept(self):
        axis = [round(v * 512) for v in [1.0 + 0.45 * i for i in range(30)]]
        snap = {"fields": [
            {"key": "PETR_INJ_TBP", "status": "VALID", "rawValues": axis},
            {"key": "MUL_ACT", "status": "VALID", "rawValues": [16384] * 30},
        ]}
        centers = (3.75, 5.25, 6.75, 8.25, 10.5)
        for ratio, changes in ((1.02, False), (1.10, True)):
            pairs = [(c, c * ratio) for c in centers[1:] for _ in range(12)]
            free = oracle.refine(snap, pairs)
            held = oracle.refine(snap, pairs, hold_log=oracle.HOLD_MIN_STEP_LOG)
            self.assertNotEqual(free["refinedRaw"], free["currentRaw"])
            self.assertEqual(held["refinedRaw"] != held["currentRaw"], changes, ratio)

    def test_real_sessions_show_fewer_sign_reversals(self):
        total_before = total_after = 0
        for name in SESSIONS:
            data = load(name)
            before = proposal_sequence(data, 0.0, False, False)
            after = proposal_sequence(data, oracle.HOLD_MIN_STEP_LOG, True, False)
            only_e1 = proposal_sequence(data, 0.0, True, False)
            only_e2 = proposal_sequence(data, oracle.HOLD_MIN_STEP_LOG, False, False)
            print(f"E_REVERSOES {name}: só E1={only_e1[0]} só E2={only_e2[0]}")
            print(f"E_REVERSOES {name}: antes={before[0]} (passos {before[1]}, médio {before[2]*100:.1f}%) "
                  f"depois E1+E2={after[0]} (passos {after[1]}, médio {after[2]*100:.1f}%)")
            self.assertLessEqual(after[0], before[0], name)
            total_before += before[0]
            total_after += after[0]
        print(f"E_REVERSOES total: antes={total_before} depois={total_after}")
        self.assertLess(total_after, total_before)
        self.assertLessEqual(total_after, total_before * 0.5)


class EpisodeCoverage(unittest.TestCase):
    PAIRS = [(c, c * 1.1) for c in (6.75, 8.25, 10.5) for _ in range(9)]

    def _snapshot(self):
        axis = [round(v * 512) for v in [1.0 + 0.45 * i for i in range(30)]]
        return {"fields": [
            {"key": "PETR_INJ_TBP", "status": "VALID", "rawValues": axis},
            {"key": "MUL_ACT", "status": "VALID", "rawValues": [16384] * 30},
        ]}

    def test_eight_pairs_from_one_stretch_do_not_drive_a_proposal(self):
        snap = self._snapshot()
        n = len(self.PAIRS)
        self.assertEqual(oracle.refine(snap, self.PAIRS, telemetry_episodes=[0] * n)["mode"], "POLISH")
        self.assertEqual(oracle.refine(snap, self.PAIRS, telemetry_episodes=[i % 2 for i in range(n)])["mode"], "POLISH")
        self.assertEqual(oracle.refine(snap, self.PAIRS, telemetry_episodes=[i % 3 for i in range(n)])["mode"], "EQUIVALENCE")
        self.assertEqual(oracle.refine(snap, self.PAIRS)["mode"], "EQUIVALENCE", "episódio desconhecido não liga o portão")

    def test_real_session_gate_only_removes_bands_never_adds(self):
        data = load("ref_2026-10-01_1719")
        for snap in data["snapshots"]:
            pairs, eps = blind.telemetry_pairs(data["telemetry"], snap["capturedAtMs"], with_episodes=True, petrol_until=True)
            free = oracle.refine(snap, pairs)
            gated = oracle.refine(snap, pairs, telemetry_episodes=eps)
            self.assertLessEqual(gated["telemetryPairsUsed"], free["telemetryPairsUsed"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
