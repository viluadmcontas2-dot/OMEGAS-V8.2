"""Aceite da Equivalência Refinada contra sessões reais (oráculo Python).

Fixtures em fixtures/autocal/real/ extraídas por tools/autocal_refine/extract_session.py.
"""
import gzip
import json
import math
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/autocal_refine"))

import closed_loop_sim as sim  # noqa: E402
import refined_oracle as oracle  # noqa: E402

REAL = ROOT / "fixtures/autocal/real"


def snapshots(name):
    with gzip.open(REAL / f"{name}.json.gz", "rt", encoding="utf-8") as handle:
        data = json.load(handle)
    return {s["sequence"]: s for s in data["snapshots"]}


REF = snapshots("ref_2026-10-01_1719")
AUTOMATCH = snapshots("automatch_2026-10-01_1301")
GNV = snapshots("gnv_only_2026-09-30_0931")


def factors(raw):
    return [v / oracle.Q14 for v in raw]


class NativeAutoMatchEvidence(unittest.TestCase):
    def test_native_automatch_is_full_gain_ratio_clamped(self):
        """ECU 16:10:32Z: MUL_ACT=1.0 → resultado = clamp(T_g/T_p, 0,75..1,20) ponto a ponto."""
        after = AUTOMATCH[1716]
        unit = json.loads(json.dumps(after))
        for field in unit["fields"]:
            if field["key"] == "MUL_ACT":
                ecu = field["rawValues"]
                field["rawValues"] = [16384] * 30
        replica = oracle.native_automatch_replica(unit)
        for index in range(4, 30):
            self.assertLessEqual(abs(replica[index] - ecu[index]), 10, index)
        self.assertEqual(min(ecu[4:]), 12288)   # piso 0,75
        self.assertEqual(max(ecu), 19661)       # teto 1,20


class RefinedEquivalenceAcceptance(unittest.TestCase):
    def test_reference_session_keeps_level_and_removes_anomalies(self):
        result = oracle.refine(REF[95])
        self.assertEqual(result["mode"], "EQUIVALENCE")
        old = factors(result["currentRaw"])
        new = factors(result["refinedRaw"])
        after = result["metricsAfter"]
        before = result["metricsBefore"]
        self.assertLessEqual(after["maxElasticity"], oracle.E_MAX + 0.01)
        self.assertLess(after["roughness"], before["roughness"] * 0.1)
        self.assertLessEqual(after["maxNeighborStep"], 0.05)
        # nível da curva boa preservado na faixa usada em GNV (índices 4–18)
        deviation = sum(abs(n / o - 1.0) for n, o in zip(new[4:19], old[4:19])) / 15
        self.assertLess(deviation, 0.04)
        # buraco do índice 9 (banda 9 outlier) removido e corcova 16–17 achatada
        self.assertIn(("GASOLINA", 9), [(r["fuel"], r["band"]) for r in result["rejectedBands"]])
        self.assertGreater(new[9], old[9] + 0.03)
        self.assertLess(new[17], old[17] - 0.04)
        # cabeça e cauda sem evidência ficam mantidas
        self.assertEqual(result["refinedRaw"][:3], result["currentRaw"][:3])
        self.assertEqual(result["refinedRaw"][22:], result["currentRaw"][22:])
        self.assertTrue(all(abs(n / o - 1.0) <= 0.1501 for n, o in zip(new, old)))

    def test_independent_gas_acquisitions_agree(self):
        """Duas aquisições de GNV independentes (antes/depois do RESET_GAS) convergem."""
        first = factors(oracle.refine(GNV[37])["refinedRaw"])
        second = factors(oracle.refine(GNV[2336])["refinedRaw"])
        worst = max(abs(a / b - 1.0) for a, b in zip(first[4:20], second[4:20]))
        self.assertLess(worst, 0.04)

    def test_fails_closed_without_mature_common_bands(self):
        result = oracle.refine(AUTOMATCH[1401])
        self.assertEqual(result["mode"], "POLISH")
        self.assertEqual(result["refinedRaw"], result["currentRaw"])

    def test_sawtooth_after_native_automatch_is_flagged_for_second_pass(self):
        result = oracle.refine(AUTOMATCH[2262])
        self.assertTrue(result["needsAnotherPass"])
        self.assertLess(result["metricsAfter"]["roughness"], result["metricsBefore"]["roughness"] * 0.2)
        old = factors(result["currentRaw"])
        new = factors(result["refinedRaw"])
        self.assertTrue(all(abs(n / o - 1.0) <= 0.1501 for n, o in zip(new, old)))

    def test_unchanged_flat_curve_stays_flat(self):
        result = oracle.refine(REF[2550])
        self.assertEqual(result["refinedRaw"], result["currentRaw"])


class ClosedLoopEvidence(unittest.TestCase):
    def test_reference_curve_reproduces_jerk_and_refined_curve_is_stable(self):
        result = oracle.refine(REF[95])
        axis = result["axisMs"]
        old = factors(result["currentRaw"])
        new = factors(result["refinedRaw"])
        for delay in (0, 1):
            alpha = sim.calibrate_alpha(axis, old, delay)
            self.assertIsNotNone(alpha)
            old_osc = [a for _, a in sim.oscillation_profile(axis, old, alpha, delay, 1.5, 12.0, 0.25) if a > 0.1]
            new_osc = [a for _, a in sim.oscillation_profile(axis, new, alpha * 1.5, delay, 1.5, 12.0, 0.25) if a > 0.1]
            self.assertTrue(old_osc)
            self.assertEqual(new_osc, [])
        _, tail = sim.simulate(axis, old, 8.3, sim.calibrate_alpha(axis, old, 0), 0)
        self.assertGreater(max(tail) - min(tail), 0.5)


if __name__ == "__main__":
    unittest.main()
