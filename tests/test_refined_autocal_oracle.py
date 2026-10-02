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

import calibrate  # noqa: E402
import blind_telemetry_test as blind  # noqa: E402
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
        self.assertGreater(before["maxElasticity"], 1.5)
        # funcional: a curva nova segue melhor o que a medição de GNV pede
        self.assertLess(result["evidenceErrorAfter"], result["evidenceErrorBefore"] * 0.5)
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
        self.assertLess(result["metricsAfter"]["maxElasticity"], result["metricsBefore"]["maxElasticity"] * 0.25)
        old = factors(result["currentRaw"])
        new = factors(result["refinedRaw"])
        self.assertTrue(all(abs(n / o - 1.0) <= 0.1501 for n, o in zip(new, old)))

    def test_unchanged_flat_curve_stays_flat(self):
        result = oracle.refine(REF[2550])
        self.assertEqual(result["refinedRaw"], result["currentRaw"])


class FunctionalCalibration(unittest.TestCase):
    def test_refined_curve_predicts_unseen_bands_better_than_current_curve(self):
        """Validação cruzada: tirar uma faixa de GNV e prevê-la. Não é critério estético."""
        refined, count = calibrate.held_out_error()
        current, _ = calibrate.held_out_error(no_correction=True)
        self.assertGreaterEqual(count, 20)
        self.assertLess(refined, current * 0.6)  # medido: 3,2% vs 6,0%

    def test_blind_telemetry_prefers_refined_curve_and_the_slope_guard(self):
        """Teste cego: telemetria em gasolina no mesmo RPM×MAP (não usada pelo motor) julga a curva."""
        guarded = calibrate.blind_errors()
        loose = calibrate.blind_errors(e_max=9.0)
        for (name, count, current, refined), (_, _, _, refined_loose) in zip(guarded, loose):
            self.assertGreaterEqual(count, 40, name)
            self.assertLess(refined, current * 0.8, name)
            self.assertLessEqual(refined, refined_loose + 1e-9, name)

    def test_petrol_itself_zigzags_so_alternation_is_not_the_jolt(self):
        """A alternância 8,0↔8,9 ms também existe na gasolina: não é assinatura de tranco."""
        with gzip.open(REAL / "ref_2026-10-01_1719.json.gz", "rt", encoding="utf-8") as handle:
            telemetry = json.load(handle)["telemetry"]
        zig = total = 0
        for a, b, c in zip(telemetry, telemetry[1:], telemetry[2:]):
            if not (a["fuel"] == b["fuel"] == c["fuel"] == "GASOLINA"):
                continue
            if any(not f["petrol_ms"] or f["petrol_ms"] < 7.5 for f in (a, b, c)):
                continue
            if c["t"] - a["t"] > 1200 or max(f["rpm"] for f in (a, b, c)) - min(f["rpm"] for f in (a, b, c)) > 120:
                continue
            if max(f["load_bar"] for f in (a, b, c)) - min(f["load_bar"] for f in (a, b, c)) > 0.03:
                continue
            d1 = math.log(b["petrol_ms"] / a["petrol_ms"])
            d2 = math.log(c["petrol_ms"] / b["petrol_ms"])
            total += 1
            zig += d1 * d2 < 0 and min(abs(d1), abs(d2)) > 0.04
        self.assertGreater(total, 50)
        self.assertGreater(zig / total, 0.2)


if __name__ == "__main__":
    unittest.main()
