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
REAL = ROOT / "fixtures/autocal/real"
KEYS = ("PETR_INJ_TBP", "MUL_ACT", "PETR_INJ_TBUF", "MNFLD_PRESS_BUF", "NUM_BUF_UPD_PETR",
        "PETR_INJ_TBUF_GAS", "MNFLD_PRESS_BUF_GAS", "NUM_BUF_UPD_GAS")

HARNESS = """
import com.omegas.prohub.autocal.AutoMatchRefinedEngine
import java.io.File

fun main(args: Array<String>) {
    val lines = File(args[0]).readLines().filter { it.isNotBlank() }
    var i = 0
    while (i < lines.size) {
        val id = lines[i++]
        fun arr(): IntArray? = lines[i++].trim().let { if (it == "-") null else it.split(" ").map(String::toInt).toIntArray() }
        val input = AutoMatchRefinedEngine.Input(arr()!!, arr()!!, arr(), arr(), arr(), arr(), arr(), arr())
        val r = AutoMatchRefinedEngine.refine(input)
        println(id + "|" + r.mode + "|" + r.refinedRaw.joinToString(" ") + "|" +
            r.origins.joinToString("") { it.name.take(1) } + "|" + r.matureCommonPoints + "|" + r.needsAnotherPass)
    }
}
"""


def kotlinc():
    return os.environ.get("KOTLINC") or shutil.which("kotlinc")


@unittest.skipUnless(kotlinc() and shutil.which("java"), "kotlinc/java indisponível: paridade Kotlin não executada")
class RefinedEngineKotlinParity(unittest.TestCase):
    def test_kotlin_matches_oracle_on_every_real_snapshot(self):
        cases = []
        for path in sorted(REAL.glob("*.json.gz")):
            with gzip.open(path, "rt", encoding="utf-8") as handle:
                snapshots = json.load(handle)["snapshots"]
            for snap in snapshots:
                cases.append((f"{path.name[:-8]}#{snap['sequence']}", snap))
        with tempfile.TemporaryDirectory(prefix="refined-parity-") as tmp:
            tmp = Path(tmp)
            payload = []
            for case_id, snap in cases:
                payload.append(case_id)
                for key in KEYS:
                    values = oracle.raw(snap, key)
                    payload.append("-" if values is None else " ".join(str(v) for v in values))
            (tmp / "input.txt").write_text("\n".join(payload) + "\n", "utf-8")
            (tmp / "Main.kt").write_text(HARNESS, "utf-8")
            jar = tmp / "parity.jar"
            subprocess.run([kotlinc(), str(ENGINE), str(tmp / "Main.kt"), "-include-runtime", "-d", str(jar)],
                           check=True, capture_output=True, text=True, timeout=600)
            out = subprocess.run(["java", "-jar", str(jar), str(tmp / "input.txt")],
                                 check=True, capture_output=True, text=True, timeout=120).stdout
        kotlin = {line.split("|")[0]: line.split("|")[1:] for line in out.strip().splitlines()}
        self.assertEqual(len(kotlin), len(cases))
        for case_id, snap in cases:
            expected = oracle.refine(snap)
            mode, raw, origins, mature, again = kotlin[case_id]
            self.assertEqual(mode, expected["mode"], case_id)
            got = [int(v) for v in raw.split()]
            worst = max(abs(a - b) for a, b in zip(got, expected["refinedRaw"]))
            self.assertLessEqual(worst, 1, f"{case_id}: diferença {worst} LSB")
            self.assertEqual(origins, "".join(o[0] for o in expected["origins"]), case_id)
            self.assertEqual(int(mature), expected["matureCommonPoints"], case_id)
            self.assertEqual(again == "true", expected["needsAnotherPass"], case_id)


if __name__ == "__main__":
    unittest.main()
