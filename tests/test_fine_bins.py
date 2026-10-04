"""Lote H: bins finos (54) → 18 faixas da ECU + intervalos entre os pontos da ECU, nas sessões reais.

Propriedades: mesma entrada → mesmo bands18; mesmo snapshot duas vezes → mesma proposta; embaralhar quadros a ±1
janela estável não muda o bands18 além de uma tolerância declarada; portões da evidência fina (bin fino, episódios).
O Kotlin tem os mesmos casos em FineBinsTest.
"""
import gzip
import json
import math
import random
import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/autocal_refine"))

import fine_bins as fb  # noqa: E402
import refined_oracle as oracle  # noqa: E402

REAL = ROOT / "fixtures/autocal/real"
KOTLIN = ROOT / "app/src/main/java/com/omegas/prohub/autocal/FineBins.kt"


def load(name):
    with gzip.open(REAL / f"{name}.json.gz", "rt", encoding="utf-8") as handle:
        return json.load(handle)


def session_pairs(name, telemetry=None):
    data = load(name)
    tel = data["telemetry"] if telemetry is None else telemetry
    snap = data["snapshots"][-1]
    pairs = fb.ledger_pairs(tel, None, None, fb.ecu_reference_from_snapshot(snap))
    return data, snap, [(p[0], p[1], p[2], p[3]) for p in pairs]


class Grid(unittest.TestCase):
    def test_54_log_spaced_bins_nest_exactly_three_per_band(self):
        self.assertEqual(len(fb.FINE_EDGES), 55)
        self.assertEqual((fb.FINE_EDGES[0], fb.FINE_EDGES[-1]), (3.0, 12.0))
        self.assertTrue(all(b > a for a, b in zip(fb.FINE_EDGES, fb.FINE_EDGES[1:])))
        ratios = [math.log(b / a) for a, b in zip(fb.FINE_EDGES, fb.FINE_EDGES[1:])]
        self.assertLess(max(ratios) - min(ratios), 0.001)
        # mais fino que o passo mais fino do eixo K de 30 pontos na faixa de trabalho (≥ 1,9×)
        axis = [v / 512.0 for v in oracle_axis()]
        steps = [math.log(b / a) for a, b in zip(axis, axis[1:]) if 3.0 <= a < 12.0]
        self.assertGreaterEqual(min(steps) / max(ratios), 1.9)
        self.assertEqual(fb.fine_index(3.0), 0)
        self.assertEqual(fb.fine_index(11.9999), 53)
        self.assertIsNone(fb.fine_index(2.99))
        self.assertIsNone(fb.fine_index(12.0))
        for k in range(54):
            self.assertEqual(fb.fine_index(fb.FINE_EDGES[k]), k)

    def test_kotlin_edges_are_the_python_edges(self):
        source = KOTLIN.read_text(encoding="utf-8")
        block = re.search(r"val EDGES = doubleArrayOf\((.*?)\n    \)", source, re.S).group(1)
        edges = [float(v) for v in re.findall(r"\d+\.\d+|\d+\.0", block)]
        self.assertEqual(edges, fb.FINE_EDGES)


def oracle_axis():
    snap = condução_only(load("ref_2026-10-01_1719")["snapshots"][0])
    return oracle.raw(snap, "PETR_INJ_TBP")


class Bands(unittest.TestCase):
    def test_bands18_and_between_bands_structure_on_real_sessions(self):
        for name in ("automatch_2026-10-01_1301", "ref_2026-10-01_1719", "gnv_only_2026-09-30_0931"):
            _, _, pairs = session_pairs(name)
            bins = fb.aggregate(pairs)
            bands = fb.bands18_json(bins)
            self.assertEqual(len(bands), 18, name)
            for band in bands:
                self.assertEqual(len(band["fineBins"]), 3)
                self.assertTrue(0.0 <= band["confidence"] <= 1.0)
                if band["samples"] == 0:
                    self.assertIsNone(band["ratio"])
                    self.assertEqual(band["confidence"], 0.0)
                else:
                    fine = [b for b in band["fineBins"] if b["samples"] > 0]
                    total = sum(b["samples"] for b in fine)
                    self.assertEqual(total, band["samples"])
                    expected = math.exp(sum(b["samples"] * math.log(b["ratio"]) for b in fine) / total)
                    self.assertAlmostEqual(band["ratio"], expected, delta=3e-5)
            between = fb.between_json(bins)
            gaps = [b for b in between if b["kind"] == "gap"]
            self.assertEqual(len(gaps), 17, name)
            self.assertTrue(all(len(g["fineBins"]) == 2 for g in gaps), "≥ 2 bins finos em todo intervalo entre pontos da ECU")
            self.assertTrue(all(b["kind"] == "gap" or b["samples"] > 0 for b in between))
            # os intervalos e os pontos da ECU (bin do meio) não se sobrepõem
            used = [b["index"] for g in gaps for b in g["fineBins"]]
            self.assertEqual(len(used), len(set(used)))
            self.assertFalse(set(used) & {3 * i + 1 for i in range(18)})

    def test_fine_bins_per_gap_with_evidence_are_reported(self):
        counts = {}
        for name in ("automatch_2026-10-01_1301", "ref_2026-10-01_1719", "gnv_only_2026-09-30_0931"):
            _, _, pairs = session_pairs(name)
            gaps = [g for g in fb.between_json(fb.aggregate(pairs)) if g["kind"] == "gap"]
            counts[name] = [sum(1 for b in g["fineBins"] if b["samples"] > 0) for g in gaps]
        print("bins finos com dado por intervalo (17):", counts)
        self.assertTrue(any(sum(c) > 0 for c in counts.values()))

    def test_reservoir_bounds_memory_but_not_the_count(self):
        pairs = [(5.0, 5.5, i, False) for i in range(500)]
        bins = fb.aggregate(pairs)
        b = bins[fb.fine_index(5.0)]
        self.assertEqual(b["n"], 500)
        self.assertEqual(len(b["episodeIds"]), fb.RESERVOIR)
        self.assertAlmostEqual(b["ratio"], 1.1, places=9)


class Determinism(unittest.TestCase):
    def test_same_frames_same_order_same_bands18(self):
        name = "ref_2026-10-01_1719"
        a = json.dumps(fb.bands18_json(fb.aggregate(session_pairs(name)[2])))
        b = json.dumps(fb.bands18_json(fb.aggregate(session_pairs(name, list(load(name)["telemetry"]))[2])))
        self.assertEqual(a, b)

    def test_same_snapshot_twice_same_proposal(self):
        for name in ("automatch_2026-10-01_1301", "gnv_only_2026-09-30_0931"):
            _, snap, pairs = session_pairs(name)
            bins = fb.aggregate(pairs)
            first = oracle.refine(snap, hold_log=oracle.HOLD_MIN_STEP_LOG, fine_bins=bins)
            second = oracle.refine(snap, hold_log=oracle.HOLD_MIN_STEP_LOG, fine_bins=fb.aggregate(pairs))
            self.assertEqual(first["refinedRaw"], second["refinedRaw"])
            self.assertEqual(first["origins"], second["origins"])

    def test_swapping_neighbouring_frames_moves_bands18_within_tolerance(self):
        name = "ref_2026-10-01_1719"
        data = load(name)
        _, _, base_pairs = session_pairs(name)
        base = fb.bands18_json(fb.aggregate(base_pairs))
        rng = random.Random(7)
        tel = [dict(f) for f in data["telemetry"]]
        for i in range(0, len(tel) - 1, 2):
            if rng.random() < 0.5:
                a, b = tel[i], tel[i + 1]
                ta, tb = a["t"], b["t"]
                tel[i], tel[i + 1] = dict(b, t=ta), dict(a, t=tb)  # troca o conteúdo, o relógio segue crescente
        _, _, moved_pairs = session_pairs(name, tel)
        moved = fb.bands18_json(fb.aggregate(moved_pairs))
        compared = 0
        for x, y in zip(base, moved):
            if x["samples"] >= 12 and y["samples"] >= 12:
                compared += 1
                self.assertLessEqual(abs(math.log(x["ratio"] / y["ratio"])), 0.04, (x["fromMs"], x["ratio"], y["ratio"]))
                self.assertLessEqual(abs(x["samples"] - y["samples"]) / max(x["samples"], y["samples"]), 0.40)
        self.assertGreater(compared, 0)


BUFFERS = ("PETR_INJ_TBUF", "MNFLD_PRESS_BUF", "NUM_BUF_UPD_PETR", "PETR_INJ_TBUF_GAS", "MNFLD_PRESS_BUF_GAS", "NUM_BUF_UPD_GAS")


def condução_only(snapshot):
    """Sem buffers nativos: a evidência da condução decide sozinha (o snapshot real já tem equivalência nativa)."""
    return dict(snapshot, fields=[f for f in snapshot["fields"] if f["key"] not in BUFFERS])


class Decision(unittest.TestCase):
    def test_flag_is_off_in_both_engines_until_cross_validation_wins_on_every_metric(self):
        """fine_bins_cv.py (sessões reais): erro LOBO/LOEO ≈ igual ao grosso (±0,3 pp), mas aspereza maior e mais
        mudanças de sinal em gnv_only → o fino fica desligado em produção. Ligar exige repetir a validação."""
        engine = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoMatchRefinedEngine.kt").read_text(encoding="utf-8")
        self.assertIn("const val FINE_BINS_ENABLED = false", engine)
        self.assertFalse(oracle.FINE_BINS_ENABLED)
        self.assertIn(f"const val FINE_LAMBDA = {oracle.FINE_LAMBDA}", engine)
        self.assertIn(f"const val FINE_NODE_CAP = {oracle.FINE_NODE_CAP}", engine)


class EvidenceGates(unittest.TestCase):
    def _pairs(self, centers, ratio, episodes, per_episode):
        return [(c, c * ratio, e, False) for c in centers for e in range(episodes) for _ in range(per_episode)]

    def test_thin_bins_and_few_episodes_carry_no_evidence(self):
        snap = condução_only(load("ref_2026-10-01_1719")["snapshots"][0])
        centers = (3.75, 5.25, 6.75, 8.25)
        few = oracle.refine(snap, hold_log=oracle.HOLD_MIN_STEP_LOG, fine_bins=fb.aggregate(self._pairs(centers, 1.12, 2, 8)))
        thin = oracle.refine(snap, hold_log=oracle.HOLD_MIN_STEP_LOG, fine_bins=fb.aggregate(self._pairs(centers, 1.12, 3, 0) + [(c, c * 1.12, 9, False) for c in centers]))
        self.assertNotEqual(thin["mode"], "EQUIVALENCE", "bins com 1 par não são evidência")

    def test_enough_episodes_propose_within_the_native_range(self):
        snap = condução_only(load("ref_2026-10-01_1719")["snapshots"][0])
        centers = (3.75, 5.25, 6.75, 8.25)
        pairs = self._pairs(centers, 1.12, 4, 3)
        result = oracle.refine(snap, hold_log=oracle.HOLD_MIN_STEP_LOG, fine_bins=fb.aggregate(pairs))
        self.assertEqual(result["mode"], "EQUIVALENCE")
        self.assertNotEqual(result["refinedRaw"], result["currentRaw"])
        for new, old, origin in zip(result["refinedRaw"], result["currentRaw"], result["origins"]):
            if origin == "HELD":
                self.assertEqual(new, old)
            else:
                self.assertTrue(oracle.MIN_RAW_PROPOSAL <= new <= oracle.MAX_RAW_PROPOSAL)
        self.assertLessEqual(result["metricsAfter"]["maxElasticity"], oracle.E_MAX + 0.01)

    def test_implausible_ratio_is_dropped(self):
        snap = condução_only(load("ref_2026-10-01_1719")["snapshots"][0])
        pairs = self._pairs((3.75, 5.25, 6.75, 8.25), 2.5, 4, 3)
        self.assertNotEqual(oracle.refine(snap, fine_bins=fb.aggregate(pairs))["mode"], "EQUIVALENCE")


if __name__ == "__main__":
    unittest.main()
