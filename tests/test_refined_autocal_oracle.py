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
        # 4,5% depois de a faixa fina deixar de contar como evidência (antes 4%): a convergência é real, não exata.
        self.assertLess(worst, 0.05)

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


class TelemetryFusion(unittest.TestCase):
    """Faixas da ECU + pares GNV×gasolina da telemetria, validado em metade escondida."""

    def _held_out(self, train_first):
        import datetime
        with gzip.open(REAL / "ref_2026-10-01_1719.json.gz", "rt", encoding="utf-8") as handle:
            data = json.load(handle)
        snap = next(s for s in data["snapshots"] if s["sequence"] == 95)
        end = datetime.datetime.fromisoformat(data["kFactorWrites"][0]["recordedAtUtc"].replace("Z", "+00:00")).timestamp() * 1000
        pairs = [p for p in blind.telemetry_pairs(data["telemetry"], end) if p[0] >= oracle.TELEMETRY_MIN_MS]
        half = len(pairs) // 2
        train, test = (pairs[:half], pairs[half:]) if train_first else (pairs[half:], pairs[:half])
        bands = oracle.refine(snap)
        fused = oracle.refine(snap, train)
        axis = bands["axisMs"]
        current = factors(bands["currentRaw"])

        def error(raw):
            k = factors(raw)
            sq = [(math.log(oracle.interp(tp, axis, k)) - math.log(oracle.interp(tg, axis, current) * tg / tp)) ** 2 for tp, tg in test]
            return math.sqrt(sum(sq) / len(sq))

        return error(bands["currentRaw"]), error(bands["refinedRaw"]), error(fused["refinedRaw"])

    def test_fusion_beats_bands_only_on_unseen_half_both_ways(self):
        for train_first in (True, False):
            current, bands, fused = self._held_out(train_first)
            self.assertLess(bands, current)
            # A telemetria não move ponto que a nativa madura já cobre (P2-2): aqui o K nativo já cobre a faixa,
            # então fundir não pode PIORAR; onde a nativa não cobre a telemetria preenche (testes sintéticos).
            self.assertLessEqual(fused, bands * 1.02)

    def test_telemetry_alone_never_enables_equivalence(self):
        result = oracle.refine(AUTOMATCH[1401], [(5.0, 5.5)] * 50)
        self.assertEqual(result["mode"], "POLISH")


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
            # a janela estável recusa ms que pula >10% (zigue-zague 8↔9 ms): menos leituras, mais limpas
            self.assertGreaterEqual(count, 20, name)
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


AXIS_MS = [0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0,
           11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 20.0, 22.0]
CENTERS = [3.75, 5.25, 6.75, 8.25, 10.5]  # uma por faixa do livro (LEDGER_BANDS)


def synthetic_snapshot(k_raw=16384, bands=None, coherent=True):
    """Snapshot sintético (classe 2): eixo observado, MUL_ACT plano e, opcionalmente, buffers de 18 faixas."""
    def field(key, values):
        return {"key": key, "status": "VALID", "rawValues": list(values), "capturedAtMs": 1}

    fields = [field("PETR_INJ_TBP", [int(v * 512) for v in AXIS_MS]), field("MUL_ACT", [k_raw] * 30)]
    if bands:
        for key, values in bands.items():
            fields.append(field(key, values))
    return {"sequence": 1, "temporalCoherent": coherent, "partial": True, "fields": fields}


def pairs_in(ratio, per_band, bands):
    """Pares espalhados por DENTRO de cada faixa do livro (cobertura interna: uma ponta só não vale como a faixa)."""
    out = []
    for b in bands:
        lo, hi = oracle.LEDGER_BANDS[b]
        out += [(lo + (k + 0.5) / per_band * (hi - lo), (lo + (k + 0.5) / per_band * (hi - lo)) * ratio) for k in range(per_band)]
    return out


def assert_no_proposal(case, result):
    case.assertEqual(result["mode"], "POLISH")
    case.assertEqual(result["reason"], oracle.REASON_NO_EVIDENCE)
    case.assertEqual(result["message"], "sem evidência suficiente")
    case.assertEqual(result["refinedRaw"], result["currentRaw"])
    case.assertTrue(all(o == "HELD" for o in result["origins"]))


def session_pairs(name):
    """Pares (gasolina de referência, GNV) de condução (rpm ≥ 1000, ≥ 3 ms) da sessão inteira, como o livro."""
    with gzip.open(REAL / f"{name}.json.gz", "rt", encoding="utf-8") as handle:
        telemetry = json.load(handle)["telemetry"]
    petrol = blind.cap_cells(blind.stable_frames(telemetry, "GASOLINA"))
    gas = blind.cap_cells(blind.stable_frames(telemetry, "GNV"))
    out = []
    for g in gas:
        match = sorted(p["t"] for p in petrol if abs(p["rpm"] - g["rpm"]) <= 150 and abs(p["map"] - g["map"]) <= 0.02)
        if len(match) >= 2 and g["rpm"] >= 1000 and match[len(match) // 2] >= oracle.TELEMETRY_MIN_MS:
            out.append((match[len(match) // 2], g["t"]))
    return out


class SafetyGates(unittest.TestCase):
    """Fatia H-evidência: sem evidência plausível, madura e coberta não há proposta."""

    def refine(self, pairs, k_raw=16384):
        return oracle.refine(synthetic_snapshot(k_raw), pairs)

    def test_implausible_ratio_bands_are_discarded_not_proposed(self):
        for ratio in (0.5, 2.0):
            result = self.refine(pairs_in(ratio, 8, [2, 3]))
            assert_no_proposal(self, result)
            self.assertEqual(result["telemetryOutlierBands"], 2)
        assert_no_proposal(self, self.refine(pairs_in(2.0, 12, [1, 2, 3, 4])))

    def test_driving_needs_three_distinct_bands_with_eight_valid_pairs(self):
        assert_no_proposal(self, self.refine(pairs_in(1.1, 40, [3, 4])))
        assert_no_proposal(self, self.refine(pairs_in(1.1, 7, [2, 3, 4])))
        assert_no_proposal(self, self.refine(pairs_in(1.1, 2, [0, 1, 2, 3, 4])))  # só faixas finas
        ok = self.refine(pairs_in(1.1, 8, [2, 3, 4]))
        self.assertEqual(ok["mode"], "EQUIVALENCE")
        self.assertTrue(ok["telemetryOnly"])
        self.assertNotEqual(ok["refinedRaw"], ok["currentRaw"])
        self.assertEqual(ok["telemetryPairsUsed"], 24)

    def test_one_implausible_band_is_dropped_the_rest_stand(self):
        result = self.refine(pairs_in(1.1, 8, [1, 2, 3, 4]) + pairs_in(2.0, 20, [0]))
        self.assertEqual(result["telemetryOutlierBands"], 1)
        self.assertEqual(result["mode"], "EQUIVALENCE")
        self.assertEqual(result["telemetryPairsUsed"], 32)

    def test_no_evidence_means_no_proposal(self):
        assert_no_proposal(self, self.refine([]))
        assert_no_proposal(self, oracle.refine(synthetic_snapshot(), None))
        snap = synthetic_snapshot()
        snap["fields"][1]["rawValues"] = [14000 + i * 120 for i in range(30)]
        assert_no_proposal(self, oracle.refine(snap, []))

    def test_thin_native_bands_are_not_evidence(self):
        time = [0] * 18
        map_ = [0] * 18
        gas = [0] * 18
        counts = [0] * 18
        for b in range(8):
            time[b], map_[b], counts[b] = int((3.0 + b) * 512), 400 + b * 60, 2
            gas[b] = int((3.0 + b) * 512 * 1.1)
        bands = {"PETR_INJ_TBUF": time, "MNFLD_PRESS_BUF": map_, "NUM_BUF_UPD_PETR": counts,
                 "PETR_INJ_TBUF_GAS": gas, "MNFLD_PRESS_BUF_GAS": map_, "NUM_BUF_UPD_GAS": counts}
        result = oracle.refine(synthetic_snapshot(bands=bands), [])
        self.assertEqual(result["thinBandsIgnored"], 16)
        self.assertEqual(result["matureCommonPoints"], 0)
        assert_no_proposal(self, result)
        # Com 3 amostras a mesma leitura vira evidência (controle: o gate é a contagem, não o formato).
        mature = oracle.refine(synthetic_snapshot(bands=dict(bands, NUM_BUF_UPD_PETR=[3] * 18, NUM_BUF_UPD_GAS=[3] * 18)), [])
        self.assertEqual(mature["thinBandsIgnored"], 0)

    def test_map_raw_with_bit_0x8000_is_counted_invalid_evidence(self):
        time = [0] * 18
        map_ = [0] * 18
        counts = [0] * 18
        time[5], map_[5], counts[5] = 5 * 512, 0x8000 | 700, 6
        time[6], map_[6], counts[6] = 6 * 512, 0xFFFF, 6
        bands = {"PETR_INJ_TBUF": time, "MNFLD_PRESS_BUF": map_, "NUM_BUF_UPD_PETR": counts,
                 "PETR_INJ_TBUF_GAS": time, "MNFLD_PRESS_BUF_GAS": map_, "NUM_BUF_UPD_GAS": counts}
        result = oracle.refine(synthetic_snapshot(bands=bands), [])
        self.assertEqual(result["invalidEvidenceBands"], 4)
        self.assertEqual(result["targets"], [])
        assert_no_proposal(self, result)

    def test_proposal_stays_in_native_range_and_insane_k_is_rejected(self):
        result = self.refine(pairs_in(1.18, 12, [1, 2, 3, 4]), int(1.15 * 16384))
        self.assertEqual(result["mode"], "EQUIVALENCE")
        self.assertTrue(all(oracle.MIN_RAW_PROPOSAL <= r <= oracle.MAX_RAW_PROPOSAL for r in result["refinedRaw"]))
        insane = self.refine(pairs_in(1.1, 12, [1, 2, 3, 4]), 65535)  # K = 4,0
        self.assertFalse(insane["available"])
        self.assertEqual(insane["reason"], "MUL_ACT_FORA_DA_FAIXA")
        self.assertEqual(insane["outOfRangePoints"], 30)
        high = self.refine(pairs_in(1.1, 12, [1, 2, 3, 4]), int(1.5 * 16384))  # K = 1,50 não entra na faixa em ±15%
        self.assertEqual(high["refinedRaw"], high["currentRaw"])
        self.assertEqual(high["outOfRangePoints"], 30)
        low = self.refine(pairs_in(0.95, 12, [1, 2, 3, 4]), int(0.6 * 16384))
        self.assertTrue(all(n == c or n >= oracle.MIN_RAW_PROPOSAL for n, c in zip(low["refinedRaw"], low["currentRaw"])))

    def test_incoherent_snapshot_is_ignored_and_partial_is_not_a_criterion(self):
        result = oracle.refine(AUTOMATCH[634], [])
        self.assertFalse(result["available"])
        self.assertEqual(result["reason"], oracle.SNAPSHOT_REASON_INCOHERENT)
        self.assertFalse(AUTOMATCH[634]["temporalCoherent"])
        self.assertTrue(AUTOMATCH[1716]["partial"])
        self.assertTrue(oracle.refine(AUTOMATCH[1716], [])["available"])

    def test_real_sessions_every_proposal_is_in_range_mature_and_covered(self):
        counts = {}
        for name, snaps in (("gnv_only_2026-09-30_0931", GNV), ("automatch_2026-10-01_1301", AUTOMATCH), ("ref_2026-10-01_1719", REF)):
            pairs = session_pairs(name)
            proposals = total = 0
            for seq, snap in snaps.items():
                if oracle.raw(snap, "MUL_ACT") is None:
                    continue
                total += 1
                result = oracle.refine(snap, pairs)
                if snap.get("temporalCoherent") is False:
                    self.assertFalse(result["available"], f"{name}#{seq}")
                    continue
                if result["mode"] == "POLISH":
                    self.assertEqual(result["refinedRaw"], result["currentRaw"], f"{name}#{seq}")
                    self.assertEqual(result["reason"], oracle.REASON_NO_EVIDENCE)
                    continue
                changed = [(n, c) for n, c in zip(result["refinedRaw"], result["currentRaw"]) if n != c]
                for new, _ in changed:
                    self.assertTrue(oracle.MIN_RAW_PROPOSAL <= new <= oracle.MAX_RAW_PROPOSAL, f"{name}#{seq}: K {new / oracle.Q14:.3f}")
                proposals += bool(changed)
                for target in result["targets"]:
                    if target.get("source") == "TELEMETRIA":
                        continue
                    self.assertGreaterEqual(target["w"], oracle.BAND_MATURE_COUNT / float(oracle.BAND_FULL_COUNT) - 1e-9, f"{name}#{seq}")
                if result["telemetryOnly"]:
                    covered = [sum(1 for tp, _ in pairs if lo <= tp < hi) for lo, hi in oracle.LEDGER_BANDS]
                    self.assertGreaterEqual(sum(1 for c in covered if c >= 8), 3, f"{name}#{seq}")
                    self.assertEqual(result["telemetryOutlierBands"], 0)
                else:
                    self.assertGreaterEqual(result["matureCommonPoints"], oracle.MIN_COMMON_MATURE, f"{name}#{seq}")
            counts[name] = (proposals, total)
        # as sessões com evidência real continuam propondo (os gates não são rígidos demais)
        self.assertGreaterEqual(counts["ref_2026-10-01_1719"][0], 2)
        self.assertGreaterEqual(counts["automatch_2026-10-01_1301"][0], 2)
        self.assertGreaterEqual(counts["gnv_only_2026-09-30_0931"][0], 8)
        print("PROPOSTAS por sessão (com proposta/analisados):", counts)

    def test_no_real_polish_snapshot_rewrites_k(self):
        """Antes: POLISH reescrevia 16–19 de 30 pontos em até 15% (automatch 634, 1716–2222; gnv_only 1856, 1994)."""
        for snaps, seqs in ((AUTOMATCH, (1716, 1809, 2019, 2080, 2158, 2222)), (GNV, (1856, 1994))):
            for seq in seqs:
                result = oracle.refine(snaps[seq], [])
                self.assertEqual(result["refinedRaw"], result["currentRaw"], seq)


if __name__ == "__main__":
    unittest.main()
