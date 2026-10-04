"""Lote W, parte 4 (subconjunto rápido): mutantes que a suíte de USO precisa MATAR em todo CI.
O conjunto completo (34 mutantes) roda sob demanda: python3 -B tools/wiring/run_ui_mutants.py"""
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class WiringMutantsCi(unittest.TestCase):
    def test_fast_subset_is_fully_killed(self):
        run = subprocess.run([sys.executable, "-B", "tools/wiring/run_ui_mutants.py", "--ci"], cwd=ROOT,
                             capture_output=True, text=True, timeout=600)
        self.assertEqual(run.returncode, 0, run.stdout[-3000:] + run.stderr[-1500:])
        self.assertIn("MORTOS=", run.stdout)


if __name__ == "__main__":
    unittest.main()
