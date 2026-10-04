"""Portões de evidência do cérebro do Refino (revisão adversarial P1-1, P1-2, P2-1/2/3/6, P3) no oráculo Python.

Cada teste falha se a regra correspondente for desfeita (mutantes registrados em docs/guardian/LOG.md). O Kotlin repete os
mesmos cenários em NewEvidenceGatesTest.kt e é comparado com estes oráculos em EquivalenceOracleParityTest.kt.
"""
import copy
import gzip
import json
import math
import random
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/autocal_refine"))
sys.path.insert(0, str(ROOT / "tools/equivalence_oracle"))

import equivalence_oracle as brain  # noqa: E402
import refined_oracle as refined  # noqa: E402

REAL = ROOT / "fixtures/autocal/real"


def real(name):
    with gzip.open(REAL / f"{name}.json.gz", "rt", encoding="utf-8") as handle:
        return json.load(handle)


REF_DATA = real("ref_2026-10-01_1719")
REF95 = next(s for s in REF_DATA["snapshots"] if s["sequence"] == 95)
AXIS_RAW = brain.snapshot_raw(REF95, "PETR_INJ_TBP")
FLAT_K = [16384] * 30
REFERENCE = [(0.1 * i, 10.0 * 0.1 * i, 10) for i in range(2, 11)]      # gasolina T = 10·MAP


def synthetic_obs(factor, noise, readings, visit_gap_ms=70000):
    """Leituras sintéticas rpm 2000, T = 10·MAP·factor·noise. A leitura i de cada célula cai numa visita própria."""
    out = []
    for j in range(5, 45):
        c = brain.center(j)
        for i in range(readings):
            out.append((2000.0, c, 10.0 * c * factor(c) * noise(i, j), i * visit_gap_ms + j))
    return out


def uniform_usage():
    cells = [0.0] * brain.GRID_CELLS
    for j in range(5, 45):
        cells[j] = 1.0
    return cells


def interior(band, n):
    lo, hi = refined.LEDGER_BANDS[band]
    return [lo + (k + 0.5) / n * (hi - lo) for k in range(n)]


def snapshot(k_raw=16384):
    axis = [0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0,
            11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 20.0, 22.0]
    return {"sequence": 1, "temporalCoherent": True, "fields": [
        {"key": "PETR_INJ_TBP", "status": "VALID", "rawValues": [int(v * 512) for v in axis]},
        {"key": "MUL_ACT", "status": "VALID", "rawValues": [k_raw] * 30}]}


class P1EvidenceBeforePercentage(unittest.TestCase):
    def evaluate(self, gas, petrol=None):
        petrol = petrol if petrol is not None else synthetic_obs(lambda c: 1.0, lambda i, j: 1.0, 20)
        return brain.evaluate(AXIS_RAW, FLAT_K, REFERENCE, petrol, gas, uniform_usage())

    def test_two_visits_never_judge_even_with_many_readings(self):
        # 40 leituras por célula mas só 2 visitas (>= 60 s entre elas): o ponto NÃO é julgado e não há índice.
        gas = [(r, m, ms, (i % 2) * 70000 + i) for i, (r, m, ms, _t) in enumerate(synthetic_obs(lambda c: 1.0, lambda i, j: 1.0, 40))]
        out = self.evaluate(gas)
        self.assertTrue(all(p["state"] in ("SEM_DADOS", "APRENDENDO") for p in out["points"]))
        self.assertIsNone(out["index"])

    def test_overlapping_windows_are_one_sample_not_many(self):
        # Todas as leituras dentro de 0,6 s partilham quadros da janela: n efetivo ~ 1, intervalo largo, ainda sem certeza.
        gas = [(r, m, ms, (k % 2) * 285) for k, (r, m, ms, _t) in enumerate(synthetic_obs(lambda c: 1.0, lambda i, j: 1.0, 20))]
        out = self.evaluate(gas)
        self.assertTrue(all(p["state"] in ("SEM_DADOS", "APRENDENDO") for p in out["points"]))
        self.assertIsNone(out["index"])

    def test_unknown_dispersion_is_not_judged_instead_of_wide_tolerance(self):
        # Uma só leitura por célula: dispersão desconhecida. Antes assumia 10% => tolerância de 20% => EQUIVALENTE.
        gas = [o for o in synthetic_obs(lambda c: 1.05, lambda i, j: 1.0, 1)]
        out = self.evaluate(gas)
        self.assertTrue(all(p["state"] != "EQUIVALENTE" for p in out["points"]))

    def test_tolerance_is_clamped_between_4_and_5_percent(self):
        self.assertEqual(0.04, brain.tolerance_of(0.0))
        self.assertEqual(0.04, brain.tolerance_of(None))
        self.assertAlmostEqual(0.05, brain.tolerance_of(0.03), places=12)
        self.assertEqual(0.05, brain.tolerance_of(0.5))

    def test_index_is_null_when_judged_points_cover_less_than_half_of_the_usage(self):
        # Só o trecho 0,60-0,70 bar tem GNV com 3+ visitas; o uso está espalhado: o índice não vira número.
        gas = [o for o in synthetic_obs(lambda c: 1.0, lambda i, j: 1.0, 20) if 0.60 <= o[1] <= 0.70]
        out = self.evaluate(gas)
        self.assertLess(out["judgedUsage"], brain.MIN_JUDGED_USAGE)
        self.assertIsNone(out["index"])
        # com GNV em todo o uso o índice aparece
        full = self.evaluate(synthetic_obs(lambda c: 1.0, lambda i, j: 1.0, 20))
        self.assertGreaterEqual(full["judgedUsage"], brain.MIN_JUDGED_USAGE)
        self.assertIsNotNone(full["index"])

    def test_reference_that_contradicts_the_book_by_rpm_is_not_equivalent(self):
        # A gasolina medida em 2000 rpm é 5,5% MENOR que a Referência (curva da ECU); o GNV bate com a Referência.
        # Casar por RPM (gasolina medida) mostra o GNV 5,8% rico em relação à gasolina de verdade: não é EQUIVALENTE.
        petrol = synthetic_obs(lambda c: 0.945, lambda i, j: 1.0, 20)
        gas = synthetic_obs(lambda c: 1.0, lambda i, j: 1.0, 20)
        out = self.evaluate(gas, petrol)
        judged = [p for p in out["points"] if p["state"] in ("EQUIVALENTE", "POBRE", "RICO")]
        self.assertTrue(judged)
        self.assertTrue(all(p["state"] == "POBRE" for p in judged), [p["state"] for p in judged])


class P1GnvIsNotShrunkTowardPetrol(unittest.TestCase):
    def test_measured_mixture_within_one_point_of_the_truth_and_poor_states_appear(self):
        rng = random.Random(7)
        noise = lambda i, j: math.exp(rng.gauss(0.0, 0.03))      # sigma 3%
        petrol = synthetic_obs(lambda c: 1.0, lambda i, j: 1.0, 20)
        gas = synthetic_obs(lambda c: 1.08, noise, 4)           # +8% verdadeiro, n = 4 por célula
        out = brain.evaluate(AXIS_RAW, FLAT_K, REFERENCE, petrol, gas, uniform_usage())
        # (1) a Curva Própria do GNV não é puxada para a gasolina: razão mediana ms/(10·MAP) fica em +8% (±1 ponto)
        ratios = [c["petrolMs"] / (10.0 * c["mapBar"]) - 1.0 for c in out["ownGas"] if c["samples"] >= 3 and c["petrolMs"]]
        self.assertGreater(len(ratios), 20)
        self.assertAlmostEqual(0.08, sum(ratios) / len(ratios), delta=0.01)
        # (2) o veredito por ponto vê o +8% e acusa POBRE
        poor = [p for p in out["points"] if p["state"] == "POBRE"]
        self.assertTrue(poor)
        mixtures = [p["mixture"] for p in out["points"] if p["state"] in ("EQUIVALENTE", "POBRE", "RICO")]
        self.assertAlmostEqual(0.08, sum(mixtures) / len(mixtures), delta=0.01)

    def test_prior_shrink_would_hide_the_deviation(self):
        # Controle: com a Referência como prior do GNV (comportamento antigo) a mesma medição sai ~4%, não 8%.
        rng = random.Random(7)
        gas = synthetic_obs(lambda c: 1.08, lambda i, j: math.exp(rng.gauss(0.0, 0.03)), 4)
        shrunk = brain.own_curve(gas, REFERENCE)
        free = brain.own_curve(gas, None)
        pick = lambda cells: [c["petrolMs"] / (10.0 * c["mapBar"]) - 1.0 for c in cells if c["samples"] >= 3 and c["petrolMs"]]
        self.assertLess(sum(pick(shrunk)) / len(pick(shrunk)), 0.065)
        self.assertGreater(sum(pick(free)) / len(pick(free)), 0.07)


class P2EvidenceIndependenceAndNativePriority(unittest.TestCase):
    def test_no_visit_gate_one_block_of_pairs_may_drive_a_proposal_weight_capped(self):
        pairs = [(t, t * 1.1) for b in (2, 3, 4) for t in interior(b, 9)]
        snap = snapshot()
        one = refined.refine(snap, pairs, telemetry_episodes=[0] * len(pairs))
        three = refined.refine(snap, pairs, telemetry_episodes=[i % 3 for i in range(len(pairs))])
        # Removido o portão de "3 visitas/60 s": a faixa vale pelos pares, com peso limitado por bloco.
        self.assertEqual("EQUIVALENCE", one["mode"])
        self.assertEqual("EQUIVALENCE", three["mode"])

    def test_episode_weight_caps_repeated_readings(self):
        pairs = [(t, t * 1.1) for b in (2, 3, 4) for t in interior(b, 30)]
        eps = [i % 3 for i in range(len(pairs))]                   # 10 pares por episódio e faixa
        w = refined.pair_weights(pairs, eps)
        self.assertTrue(all(abs(x - refined.EPISODE_PAIR_CAP / 10) < 1e-12 for x in w))
        self.assertEqual([1.0] * len(pairs), refined.pair_weights(pairs, None))

    def test_unknown_episode_drops_only_that_pair_not_every_band_gate(self):
        pairs = [(t, t * 1.1) for b in (2, 3, 4) for t in interior(b, 9)]
        eps = [i % 3 for i in range(len(pairs))]
        eps[0] = -1                                                # um par sem episódio conhecido
        out = refined.refine(snapshot(), pairs, telemetry_episodes=eps)
        self.assertEqual("EQUIVALENCE", out["mode"])
        self.assertEqual(len(pairs) - 1, out["telemetryPairsUsed"])

    def test_telemetry_does_not_move_points_the_mature_native_evidence_covers(self):
        base = refined.refine(REF95)
        pairs = [(t, t * 1.3) for b in range(5) for t in interior(b, 12)]
        mixed = refined.refine(REF95, pairs)
        self.assertGreater(mixed["telemetryDroppedByNative"], 0)
        axis = base["axisMs"]
        telemetry = [t for t in mixed["targets"] if t["map"] is None]
        self.assertTrue(telemetry)
        for t in telemetry:
            dominant = max(refined.axis_weights(t["tp"], axis), key=lambda na: (na[1], -na[0]))[0]
            self.assertLess(base["gain"][dominant], refined.NATIVE_COVERED_GAIN, f"alvo em {t['tp']:.2f} ms cai num ponto coberto")

    def test_telemetry_weight_per_band_is_capped(self):
        pairs = [(t, t * 1.1) for t in interior(2, 300)]
        targets = refined.cap_band_weight(refined.telemetry_targets(pairs, [float(i + 1) for i in range(30)], [1.0] * 30))
        self.assertAlmostEqual(refined.TELEMETRY_BAND_WEIGHT_CAP, sum(t["w"] for t in targets), places=9)


class P2MeasuredProposalNeverWorsensTheEngineCriterion(unittest.TestCase):
    def test_exact_ratio_one_does_not_move_the_curve(self):
        snap = copy.deepcopy(REF95)
        f = {x["key"]: x for x in snap["fields"]}
        f["PETR_INJ_TBUF_GAS"]["rawValues"] = list(f["PETR_INJ_TBUF"]["rawValues"])
        f["MNFLD_PRESS_BUF_GAS"]["rawValues"] = list(f["MNFLD_PRESS_BUF"]["rawValues"])
        f["NUM_BUF_UPD_GAS"]["rawValues"] = list(f["NUM_BUF_UPD_PETR"]["rawValues"])
        out = refined.refine(snap)
        self.assertEqual("EQUIVALENCE", out["mode"])
        self.assertEqual(0.0, out["evidenceErrorBefore"])
        self.assertLessEqual(out["evidenceErrorAfter"], out["evidenceErrorBefore"] + refined.REGRESSION_EPS)
        self.assertEqual(out["currentRaw"], out["refinedRaw"])           # antes: 20 pontos mexidos, erro 0% -> 1,4%

    def test_points_whose_evidence_error_is_inside_the_tolerance_are_not_moved(self):
        pairs = [(t, t * 1.02) for b in (1, 2, 3, 4) for t in interior(b, 12)]       # GNV 2% pobre: dentro de ±4%
        out = refined.refine(snapshot(), pairs)
        self.assertGreater(out["deadBandPoints"], 10)
        self.assertLessEqual(sum(1 for a, b in zip(out["currentRaw"], out["refinedRaw"]) if a != b), 6)
        # 4,5% de erro passa da tolerância: a curva se move de verdade
        far = refined.refine(snapshot(), [(t, t * 1.045) for b in (1, 2, 3, 4) for t in interior(b, 12)])
        self.assertEqual(0, far["deadBandPoints"])
        self.assertGreater(sum(1 for a, b in zip(far["currentRaw"], far["refinedRaw"]) if a != b), 10)

    def test_a_proposal_that_worsens_the_criterion_is_never_emitted(self):
        # Em todos os snapshots reais, com e sem telemetria, o erro de evidência nunca sobe.
        for name in ("ref_2026-10-01_1719", "automatch_2026-10-01_1301", "gnv_only_2026-09-30_0931"):
            for s in real(name)["snapshots"]:
                out = refined.refine(s)
                before, after = out.get("evidenceErrorBefore"), out.get("evidenceErrorAfter")
                if before is not None and after is not None:
                    self.assertLessEqual(after, before + refined.REGRESSION_EPS + 1e-9, f"{name}#{s['sequence']}")


class P2CoarseBandsNeedInteriorCoverage(unittest.TestCase):
    def test_thirty_pairs_in_one_end_of_the_band_are_not_the_band(self):
        # "faixa 4,5–6 ms medida com 30 pares todos entre 4,5–5,0"
        end_only = [(4.5 + 0.5 * (k + 0.5) / 30, 0.0) for k in range(30)]
        pairs = [(t, t * 1.1) for t, _ in end_only]
        pairs += [(t, t * 1.1) for b in (2, 3, 4) for t in interior(b, 12)]
        out = refined.refine(snapshot(), pairs)
        # as faixas 2, 3 e 4 sustentam a proposta; a 1 (só uma ponta) não entra
        self.assertEqual(36, out["telemetryPairsUsed"])
        only_end = refined.refine(snapshot(), pairs[:30])
        self.assertEqual("POLISH", only_end["mode"])

    def test_interior_rule_needs_two_thirds_with_two_pairs(self):
        lo, hi = refined.LEDGER_BANDS[1]
        self.assertFalse(refined.interior_covered([4.6, 4.7, 4.8, 4.9], lo, hi))
        self.assertTrue(refined.interior_covered([4.6, 4.7, 5.6, 5.7], lo, hi))


if __name__ == "__main__":
    unittest.main()
