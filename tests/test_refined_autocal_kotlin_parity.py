"""Paridade AutoMatchRefinedEngine.kt ↔ refined_oracle.py nas sessões reais.

Requer `kotlinc` (PATH ou variável KOTLINC) e `java`; sem compilador o teste é
pulado explicitamente (o oráculo continua coberto por test_refined_autocal_oracle).
"""
import gzip
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/autocal_refine"))

import refined_oracle as oracle  # noqa: E402

ENGINE = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoMatchRefinedEngine.kt"
LEDGER = ROOT / "app/src/main/java/com/omegas/prohub/autocal/EquivalenceLedger.kt"
JSON_JAR = os.environ.get("ORG_JSON_JAR", "")
REAL = ROOT / "fixtures/autocal/real"
KEYS = ("PETR_INJ_TBP", "MUL_ACT", "PETR_INJ_TBUF", "MNFLD_PRESS_BUF", "NUM_BUF_UPD_PETR",
        "PETR_INJ_TBUF_GAS", "MNFLD_PRESS_BUF_GAS", "NUM_BUF_UPD_GAS")

HARNESS = """
import com.omegas.prohub.autocal.AutoMatchRefinedEngine
import com.omegas.prohub.autocal.EquivalenceLedger
import com.omegas.prohub.autocal.FineBins
import java.io.File

fun main(args: Array<String>) {
    // Acumulador primeiro: os bins finos da sessão inteira alimentam o caminho fino do motor (Lote H).
    val frames = File(args[1]).readLines().filter { it.isNotBlank() }
    val ledger = EquivalenceLedger(null)
    frames.forEach { line ->
        val p = line.split(" ")
        ledger.accept(EquivalenceLedger.Frame(p[0].toLong(), p[1], p[2].toDouble(), p[3].toDouble(), p[4].toDouble()))
    }
    val fine = ledger.fineBins()
    val lines = File(args[0]).readLines().filter { it.isNotBlank() }
    var i = 0
    while (i < lines.size) {
        val id = lines[i++]
        fun arr(): IntArray? = lines[i++].trim().let { if (it == "-") null else it.split(" ").map(String::toInt).toIntArray() }
        val base = listOf(arr()!!, arr()!!, arr(), arr(), arr(), arr(), arr(), arr())
        val pairsLine = lines[i++].trim()
        val pairs = if (pairsLine == "-") emptyList() else pairsLine.split(" ").chunked(2).map { it[0].toDouble() to it[1].toDouble() }
        val scaleLine = lines[i++].trim()
        val scale = if (scaleLine == "-") null else scaleLine.split(" ").map(String::toDouble).toDoubleArray()
        val input = AutoMatchRefinedEngine.Input(base[0]!!, base[1]!!, base[2], base[3], base[4], base[5], base[6], base[7], pairs, scale)
        val r = AutoMatchRefinedEngine.refine(input)
        println(id + "|" + r.mode + "|" + r.refinedRaw.joinToString(" ") + "|" +
            r.origins.joinToString("") { it.name.take(1) } + "|" + r.matureCommonPoints + "|" + r.needsAnotherPass)
        val f = AutoMatchRefinedEngine.refine(AutoMatchRefinedEngine.Input(base[0]!!, base[1]!!, base[2], base[3], base[4], base[5], base[6], base[7], fineBins = fine))
        println("FINE|" + id + "|" + f.mode + "|" + f.refinedRaw.joinToString(" ") + "|" + f.origins.joinToString("") { it.name.take(1) })
    }
    // Paridade do acumulador: quadros "t fuel rpm map petrol" → pares
    println("BANDS18|" + FineBins.bands18Json(fine).let { a -> (0 until a.length()).joinToString(" ") { i ->
        val o = a.getJSONObject(i); o.getInt("samples").toString() + "," + o.opt("ratio") + "," + o.opt("episodes") } })
    println("BETWEEN|" + FineBins.betweenJson(fine).let { a -> (0 until a.length()).joinToString(" ") { i ->
        val o = a.getJSONObject(i); o.getInt("samples").toString() + "," + o.opt("ratio") + "," + o.opt("episodes") } })
    println("PAIRS|" + ledger.pairs().joinToString(" ") { "%.5f,%.5f".format(java.util.Locale.ROOT, it.petrolRefMs, it.gasPetrolMs) })
}
"""

def kotlinc():
    return os.environ.get("KOTLINC") or shutil.which("kotlinc")


@unittest.skipUnless(kotlinc() and shutil.which("java") and JSON_JAR, "kotlinc/java/ORG_JSON_JAR indisponível: paridade Kotlin não executada")
class RefinedEngineKotlinParity(unittest.TestCase):
    def test_kotlin_matches_oracle_on_every_real_snapshot(self):
        import blind_telemetry_test as blind
        cases = []
        for path in sorted(REAL.glob("*.json.gz")):
            with gzip.open(path, "rt", encoding="utf-8") as handle:
                data = json.load(handle)
            pairs = blind.telemetry_pairs(data["telemetry"])
            for snap in data["snapshots"]:
                if snap.get("temporalCoherent") is False:
                    continue  # o filtro de coerência temporal vive em AutoMatchSnapshotAnalysis, fora do motor
                stem = path.name[:-8]
                cases.append((f"{stem}#{snap['sequence']}", snap, None, None))
                cases.append((f"{stem}#{snap['sequence']}+tel", snap, pairs, None))
                scale = [0.7 if 3.0 <= v / 512 < 6.0 else 1.2 if 7.5 <= v / 512 < 12 else 1.0
                         for v in (oracle.raw(snap, "PETR_INJ_TBP") or [512] * 30)]
                cases.append((f"{stem}#{snap['sequence']}+tel+scale", snap, pairs, scale))
        with gzip.open(REAL / "ref_2026-10-01_1719.json.gz", "rt", encoding="utf-8") as handle:
            telemetry = json.load(handle)["telemetry"]
        with tempfile.TemporaryDirectory(prefix="refined-parity-") as tmp:
            tmp = Path(tmp)
            payload = []
            for case_id, snap, pairs, scale in cases:
                payload.append(case_id)
                for key in KEYS:
                    values = oracle.raw(snap, key)
                    payload.append("-" if values is None else " ".join(str(v) for v in values))
                payload.append("-" if not pairs else " ".join(f"{a!r} {b!r}" for a, b in pairs))
                payload.append("-" if scale is None else " ".join(repr(v) for v in scale))
            (tmp / "input.txt").write_text("\n".join(payload) + "\n", "utf-8")
            (tmp / "frames.txt").write_text("\n".join(
                f"{f['t']} {f['fuel']} {f['rpm'] or 0} {f['load_bar'] or 0} {f['petrol_ms'] or 0}" for f in telemetry
            ) + "\n", "utf-8")
            (tmp / "Main.kt").write_text(HARNESS, "utf-8")
            jar = tmp / "parity.jar"
            sources = [str(ENGINE), str(LEDGER), str(LEDGER.with_name("PresentationMedian.kt")), str(LEDGER.with_name("TypicalInjectionBands.kt")), str(LEDGER.with_name("FineBins.kt")), str(tmp / "Main.kt")]
            subprocess.run([kotlinc(), *sources, "-cp", JSON_JAR, "-include-runtime", "-d", str(jar)],
                           check=True, capture_output=True, text=True, timeout=900)
            out = subprocess.run(["java", "-cp", f"{jar}:{JSON_JAR}", "MainKt", str(tmp / "input.txt"), str(tmp / "frames.txt")],
                                 check=True, capture_output=True, text=True, timeout=300).stdout
        lines = out.strip().splitlines()
        kotlin = {line.split("|")[0]: line.split("|")[1:] for line in lines if not line.startswith(("PAIRS|", "FINE|", "BANDS18|", "BETWEEN|"))}
        kotlin_fine = {line.split("|")[1]: line.split("|")[2:] for line in lines if line.startswith("FINE|")}
        self.assertEqual(len(kotlin), len(cases))
        for case_id, snap, pairs, scale in cases:
            expected = oracle.refine(snap, pairs, scale)
            mode, raw, origins, mature, again = kotlin[case_id]
            self.assertEqual(mode, expected["mode"], case_id)
            got = [int(v) for v in raw.split()]
            worst = max(abs(a - b) for a, b in zip(got, expected["refinedRaw"]))
            self.assertLessEqual(worst, 1, f"{case_id}: diferença {worst} LSB")
            self.assertEqual(origins, "".join(o[0] for o in expected["origins"]), case_id)
            self.assertEqual(int(mature), expected["matureCommonPoints"], case_id)
            self.assertEqual(again == "true", expected["needsAnotherPass"], case_id)
        # Lote H: caminho fino e bands18/betweenBands do Kotlin == oráculo, sobre os bins da sessão inteira
        import fine_bins as fb
        bins = fb.aggregate([(p[0], p[1], p[2], p[3]) for p in fb.ledger_pairs(telemetry)])
        for case_id, snap, pairs, scale in cases:
            if pairs is not None or scale is not None:
                continue
            expected = oracle.refine(snap, fine_bins=bins)
            mode, raw, origins = kotlin_fine[case_id]
            self.assertEqual(mode, expected["mode"], "fino " + case_id)
            worst = max(abs(a - b) for a, b in zip((int(v) for v in raw.split()), expected["refinedRaw"]))
            self.assertLessEqual(worst, 1, f"fino {case_id}: diferença {worst} LSB")
            self.assertEqual(origins, "".join(o[0] for o in expected["origins"]), "fino " + case_id)

        def cells(entries):
            return [(e["samples"], e["ratio"], e["episodes"]) for e in entries]

        def parse(line):
            out = []
            for item in line.split("|", 1)[1].split():
                n, ratio, episodes = item.split(",")
                out.append((int(n), None if ratio == "null" else float(ratio), None if episodes == "null" else int(episodes)))
            return out
        for tag, expected in (("BANDS18|", fb.bands18_json(bins)), ("BETWEEN|", fb.between_json(bins))):
            got = parse(next(line for line in lines if line.startswith(tag)))
            want = cells(expected)
            self.assertEqual(len(got), len(want), tag)
            for (gn, gr, ge), (wn, wr, we) in zip(got, want):
                self.assertEqual((gn, ge), (wn, we), tag)
                self.assertTrue((gr is None) == (wr is None), tag)
                if gr is not None:
                    self.assertAlmostEqual(gr, wr, delta=2e-5, msg=tag)
        # acumulador Kotlin == pares do script validado
        kotlin_pairs = [tuple(float(x) for x in p.split(",")) for p in next(line for line in lines if line.startswith("PAIRS|")).split("|", 1)[1].split()]
        python_pairs = blind.telemetry_pairs(telemetry)
        self.assertEqual(len(kotlin_pairs), len(python_pairs))
        for (ka, kb), (pa, pb) in zip(kotlin_pairs, python_pairs):
            self.assertAlmostEqual(ka, pa, places=4)
            self.assertAlmostEqual(kb, pb, places=4)


if __name__ == "__main__":
    unittest.main()
