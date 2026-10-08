#!/usr/bin/env python3
"""TRAVA PERMANENTE autocal-sem-leitura-anterior (dono, 2026-10-08, AGENTS.md regra 16).

O AutoCal nunca mostra leitura anterior: nem curva esmaecida, nem legenda, nem botao, nem funcao que a guarde.
Par em Node: tests/ui/autocal-sem-leitura-anterior.test.cjs. Mutante: autocal-leitura-anterior-volta. Nao remover nem enfraquecer."""
import os
import re
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
UI = Path(os.environ.get("UI_ROOT", REPO / "app/src/main/assets/ui"))
FILES = ["screens/autocal-cockpit.js", "components/curve-chart.js", "styles-autocal-cockpit.css", "styles-autocal-refino.css", "index.html"]
FORBIDDEN = [
    r"Leitura\s+anterior", r"Curva\s+anterior", r"GNV\s+anterior", r'data-legend="previous"', r"autocal-previous", r"autocal-history",
    r"previousReferencePoints", r"comparisonPinned", r"chartHistoryVisible", r"renderResetComparison", r"referenceComparison",
    r"autocalResetComparison", r"historyCurve", r"\bhistory\b", r'class="[^"]*\bprevious\b', r"\.autocal-reference-line\.previous",
]


class AutoCalSemLeituraAnterior(unittest.TestCase):
    def test_nenhum_arquivo_do_autocal_tem_leitura_anterior(self):
        for name in FILES:
            src = (UI / name).read_text(encoding="utf-8")
            for pattern in FORBIDDEN:
                self.assertIsNone(re.search(pattern, src, re.I if pattern[0].isupper() else 0), f"{name}: {pattern}")

    def test_trava_registrada_nas_regras(self):
        agents = (REPO / "AGENTS.md").read_text(encoding="utf-8")
        if "RESET_ROOT" not in os.environ and "UI_ROOT" not in os.environ:
            self.assertIn("autocal-sem-leitura-anterior", agents, "regra 16 sumiu do AGENTS.md")
            self.assertTrue((REPO / "tests/ui/autocal-sem-leitura-anterior.test.cjs").exists())


if __name__ == "__main__":
    unittest.main()
