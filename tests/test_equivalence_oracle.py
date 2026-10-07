#!/usr/bin/env python3
"""Fatia F4: oráculo independente do cérebro único (tools/equivalence_oracle) nas sessões reais.

O Kotlin é comparado a ele em app/src/test/.../EquivalenceOracleParityTest.kt (a JVM chama este oráculo).
Aqui ficam os portões que valem para o próprio modelo: validação cruzada da Curva Própria, forma da saída em
todas as sessões da pasta e os números reais que a spec pediu (inclusive os que não confirmam a esperança).
"""
import datetime
import glob
import json
import math
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools" / "equivalence_oracle"))
import equivalence_oracle as oracle  # noqa: E402

REAL = ROOT / "fixtures" / "autocal" / "real"
REFERENCE = "ref_2026-10-01_1719"
AUTOMATCH = "automatch_2026-10-01_1301"
STATES = {"SEM_DADOS", "APRENDENDO", "MEDIDO", "EQUIVALENTE", "POBRE", "RICO"}


def fixture(name):
    return oracle.load(REAL / f"{name}.json.gz")


def frames(data):
    return [{"t": f["t"], "fuel": f.get("fuel") or "", "rpm": f.get("rpm") or 0.0,
             "map": f.get("load_bar") or 0.0, "petrol_ms": f.get("petrol_ms") or 0.0} for f in data["telemetry"]]


def epoch_ms(text):
    return datetime.datetime.fromisoformat(text.replace("Z", "+00:00")).timestamp() * 1000


def window_result(name, ref_seq, k_seq, gas_keep):
    """Gasolina da sessão inteira; GNV só dos quadros que passam em gas_keep; uso da sessão inteira."""
    data = fixture(name)
    fr = frames(data)
    ref = oracle.reference_points(oracle.snapshot_by_seq(data, ref_seq))
    petrol, _ = oracle.ledger_obs(fr)
    _, gas = oracle.ledger_obs([f for f in fr if f["fuel"] != "GNV" or gas_keep(f["t"])])
    snap = oracle.snapshot_by_seq(data, k_seq)
    return oracle.evaluate(oracle.snapshot_raw(snap, "PETR_INJ_TBP"), oracle.snapshot_raw(snap, "MUL_ACT"),
                           ref, petrol, gas, oracle.usage_cells(fr))


def cross_validate(ref_seq, block=40, min_test=3):
    """Ajusta com um bloco de leituras de gasolina e julga no outro (e troca). Só células OWN no treino."""
    data = fixture(REFERENCE)
    ref = oracle.reference_points(oracle.snapshot_by_seq(data, ref_seq))
    petrol, _ = oracle.ledger_obs(frames(data))
    petrol = [o for o in petrol if o[0] >= oracle.DRIVING_MIN_RPM]
    err_own, err_ref = [], []
    for parity in (0, 1):
        train = [o for i, o in enumerate(petrol) if (i // block) % 2 == parity]
        test = [o for i, o in enumerate(petrol) if (i // block) % 2 != parity]
        own = oracle.own_curve(train, ref)
        by_cell = {}
        for _rpm, m, ms, *_ in test:
            j = oracle.cell_of(m)
            if j is not None:
                by_cell.setdefault(j, []).append(math.log(ms))
        for j, values in by_cell.items():
            cell = own[j]
            prior = oracle.prior_at(ref, oracle.center(j))
            if cell["source"] != "OWN" or len(values) < min_test or cell["petrolMs"] is None or prior is None:
                continue
            target = oracle.median(values)
            err_own.append(math.log(cell["petrolMs"]) - target)
            err_ref.append(math.log(prior) - target)
    rms = lambda v: math.sqrt(sum(x * x for x in v) / len(v))
    return len(err_own), rms(err_own), rms(err_ref)


class EquivalenceOracle(unittest.TestCase):
    def test_reference_matches_the_ecu_acquisition_at_seq_95(self):
        ref = oracle.reference_points(oracle.snapshot_by_seq(fixture(REFERENCE), 95))
        self.assertEqual(16, len(ref))
        self.assertEqual((0.1865234375, 1.77734375, 10), ref[0])
        # a ECU zerou a gasolina (seq 962): imatura, sem Referência
        self.assertEqual([], oracle.reference_points(oracle.snapshot_by_seq(fixture(REFERENCE), 962)))

    def test_own_curve_beats_a_stale_reference_on_hidden_readings(self):
        judged, rms_own, rms_ref = cross_validate(95)
        print("CV seq95  judged=%d rmsOwn=%.4f rmsRef=%.4f" % (judged, rms_own, rms_ref))
        self.assertGreaterEqual(judged, 8)
        self.assertLess(rms_own, rms_ref)

    def test_own_curve_does_not_get_materially_worse_when_the_reference_is_fresh(self):
        judged, rms_own, rms_ref = cross_validate(2183)
        print("CV seq2183 judged=%d rmsOwn=%.4f rmsRef=%.4f" % (judged, rms_own, rms_ref))
        self.assertGreaterEqual(judged, 8)
        self.assertLessEqual(rms_own, rms_ref * 1.15)

    def test_reference_seq_95_is_the_stale_one(self):
        # A ECU relearn depois do congelamento: a frozen do seq 95 fica ~26% longe da viva do seq 2183.
        data = fixture(REFERENCE)
        ref = oracle.reference_points(oracle.snapshot_by_seq(data, 95))
        live = oracle.reference_points(oracle.snapshot_by_seq(data, 2183))
        maps, vals = [p[0] for p in live], [p[1] for p in live]
        drift = max(abs(oracle.interp(p[0], maps, vals) / p[1] - 1) for p in ref if maps[0] <= p[0] <= maps[-1])
        self.assertGreater(drift, 0.10)

    def test_real_writes_were_flat_resets(self):
        # O que o dono rotulou "escrita" nas duas sessões foi um reset plano (MUL_ACT = 1,0 nos 30 pontos).
        for name, before, after in ((REFERENCE, 2183, 2550), (AUTOMATCH, 634, 699)):
            data = fixture(name)
            self.assertTrue(all(v == 16384 for v in oracle.snapshot_raw(oracle.snapshot_by_seq(data, after), "MUL_ACT")), name)
            self.assertTrue(any(v != 16384 for v in oracle.snapshot_raw(oracle.snapshot_by_seq(data, before), "MUL_ACT")), name)

    def test_index_falls_after_the_flat_reset_in_the_reference_session(self):
        data = fixture(REFERENCE)
        write_at = epoch_ms(data["kFactorWrites"][0]["recordedAtUtc"])
        before = window_result(REFERENCE, 95, 2183, lambda t: t < write_at)
        after = window_result(REFERENCE, 95, 2550, lambda t: t >= write_at)
        print("INDEX REFERENCE before=%s cov=%d after=%s cov=%d" % (before["index"], before["coverage"], after["index"], after["coverage"]))
        # Revisão adversarial: nesta sessão real os pontos julgados (>= 3 visitas independentes, dispersão conhecida) cobrem
        # < 50% do uso, então o índice NÃO é número. Antes: 100% com coverage 1-5 (evidência de 10 s, tolerância de 20%).
        self.assertLess(before["judgedUsage"], oracle.MIN_JUDGED_USAGE)
        self.assertIsNone(before["index"])
        self.assertTrue(after["index"] is None or 0.0 <= after["index"] <= 1.0)

    def test_automatch_window_is_too_short_to_claim_the_index_rose(self):
        data = fixture(AUTOMATCH)
        write_at = epoch_ms(data["kFactorWrites"][0]["recordedAtUtc"])
        until = epoch_ms(oracle.snapshot_by_seq(data, 1401)["recordedAtUtc"])
        before = window_result(AUTOMATCH, 634, 634, lambda t: t < write_at)
        after = window_result(AUTOMATCH, 634, 699, lambda t: write_at <= t <= until)
        print("INDEX AUTOMATCH before=%s cov=%d after=%s cov=%d" % (before["index"], before["coverage"], after["index"], after["coverage"]))
        # Sem portão de tempo: a cobertura vem do intervalo de confiança; o índice só é número com >= 50% do uso julgado e nunca 100% falso.
        for r in (before, after):
            self.assertTrue(r["index"] is None or r["index"] < 0.95)

    def test_replay_shape_in_every_real_session(self):
        files = sorted(glob.glob(str(REAL / "*.json.gz")))
        self.assertGreaterEqual(len(files), 3)
        for path in files:
            data = oracle.load(path)
            ref_seq, k_seq = oracle.default_seqs(data)
            self.assertIsNotNone(k_seq, path)
            out = oracle.replay(data, ref_seq, k_seq)
            self.assertEqual(50, len(out["ownPetrol"]), path)
            self.assertEqual(50, len(out["ownGas"]), path)
            self.assertEqual(30, len(out["points"]), path)
            self.assertTrue(all(p["state"] in STATES for p in out["points"]), path)
            self.assertTrue(out["index"] is None or 0.0 <= out["index"] <= 1.0, path)
            self.assertAlmostEqual(1.0, sum(p["usage"] for p in out["points"]), places=9) if any(p["usage"] > 0 for p in out["points"]) else None
            json.dumps(out)  # serializável (sem NaN/Infinity no resultado)

    def test_synthetic_gnv_6_percent_rich_plateau_is_poor_and_index_is_usage_weighted(self):
        # Mesmo cenário do EquivalenceEngineTest.kt: gasolina T = 10·MAP, GNV +6% em 0,60–0,70 bar.
        axis_raw = oracle.snapshot_raw(oracle.snapshot_by_seq(fixture(REFERENCE), 95), "PETR_INJ_TBP")
        ref = [(0.1 * i, 10.0 * 0.1 * i, 10) for i in range(2, 11)]

        def obs(factor):
            out = []
            for j in range(5, 45):
                c = oracle.center(j)
                for i in range(20):
                    # a leitura i de cada célula é uma visita própria (70 s de uma à outra)
                    out.append((2000.0, c, 10.0 * c * factor(c) * (1.005 if i % 2 == 0 else 0.995), i * 70000 + j))
            return out
        petrol = obs(lambda c: 1.0)
        gas = obs(lambda c: 1.06 if 0.60 <= c <= 0.70 else 1.0)
        cells = [0.0] * oracle.GRID_CELLS
        cells[oracle.cell_of(0.51)] = 75.0
        cells[oracle.cell_of(0.65)] = 25.0
        out = oracle.evaluate(axis_raw, [16384] * 30, ref, petrol, gas, cells)
        self.assertEqual("POBRE", out["points"][12]["state"])
        self.assertAlmostEqual(0.062, out["points"][12]["mixture"], delta=0.015)
        self.assertEqual("EQUIVALENTE", out["points"][9]["state"])
        self.assertAlmostEqual(0.75, out["index"], places=9)

    def test_cli_prints_json_for_a_fixture(self):
        script = ROOT / "tools" / "equivalence_oracle" / "equivalence_oracle.py"
        result = subprocess.run([sys.executable, "-B", str(script), str(REAL / f"{REFERENCE}.json.gz"), "95", "2183"],
                                capture_output=True, text=True, check=True)
        out = json.loads(result.stdout)
        self.assertEqual((95, 2183), (out["refSeq"], out["curveSeq"]))
        self.assertEqual(30, len(out["points"]))


class WaterRaw19AndCaptureClock(unittest.TestCase):
    """Espelho de ThermalFuelGateTest.kt: água comparável (F14), raw19 valida combustível, relógio de captura."""

    @staticmethod
    def fr(t, fuel, water=None, dyn=None, cap=None, ms=5.0):
        return {"t": t, "fuel": fuel, "rpm": 2000.0, "map": 0.6, "petrol_ms": ms, "water_c": water, "dyn": dyn, "cap": cap}

    def test_pairs_need_comparable_water(self):
        petrol = [(2000.0, 0.6, 5.0, 1000, 40.0), (2000.0, 0.6, 5.0, 2000, 42.0)]
        self.assertEqual([], oracle.build_pairs(petrol, [(2000.0, 0.6, 6.0, 3000, 78.0)], []))
        petrol = [(2000.0, 0.6, 5.0, 1000, 70.0), (2000.0, 0.6, 5.0, 2000, 70.0)]
        self.assertEqual(1, len(oracle.build_pairs(petrol, [(2000.0, 0.6, 6.0, 3000, 78.0)], [])))
        self.assertEqual([], oracle.build_pairs(petrol, [(2000.0, 0.6, 6.0, 3000, 78.1)], []))
        self.assertEqual(1, len(oracle.build_pairs(petrol, [(2000.0, 0.6, 6.0, 3000)], [])))

    def test_ledger_obs_water_mean_and_raw19(self):
        petrol, _ = oracle.ledger_obs([self.fr(i * 280, "GASOLINA", water=60.0 + 2 * i) for i in range(3)])
        self.assertAlmostEqual(62.0, petrol[0][4])
        petrol, _ = oracle.ledger_obs([self.fr(i * 280, "GASOLINA", dyn=223) for i in range(6)])
        self.assertEqual([], petrol)
        _, gas = oracle.ledger_obs([self.fr(i * 280, "GNV", dyn=0) for i in range(6)])
        self.assertEqual([], gas)
        _, gas = oracle.ledger_obs([self.fr(i * 280, "GNV", dyn=220) for i in range(3)])
        self.assertEqual(1, len(gas))

    def test_capture_clock_wins_over_delivery_clock(self):
        burst = [self.fr(i * 13, "GASOLINA", cap=10000 + i * 700) for i in range(3)]
        self.assertEqual([], oracle.ledger_obs(burst)[0])
        spread = [self.fr(i * 700, "GASOLINA", cap=10000 + i * 280) for i in range(3)]
        self.assertEqual(1, len(oracle.ledger_obs(spread)[0]))
        self.assertEqual([], oracle.ledger_obs([self.fr(i * 700, "GASOLINA") for i in range(3)])[0])


if __name__ == "__main__":
    unittest.main()
