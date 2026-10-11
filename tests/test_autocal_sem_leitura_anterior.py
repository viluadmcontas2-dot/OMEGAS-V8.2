#!/usr/bin/env python3
"""TRAVA autocal-sem-leitura-anterior (dono, 2026-10-11, AGENTS.md).

O AutoCal nunca mostra leitura anterior: nem curva esmaecida, nem legenda, nem botão,
nem comparação antes × depois, nem função que a guarde. Tudo em tempo real.
Par em Node: tests/ui/autocal-sem-leitura-anterior.test.cjs. Não remover nem enfraquecer."""
import os
import re
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
UI = Path(os.environ.get("UI_ROOT", REPO / "app/src/main/assets/ui"))
FILES = ["screens/autocal-cockpit.js", "styles-autocal-cockpit.css", "index.html"]
FORBIDDEN = [
    r"Leitura\s+anterior", r"Curva\s+anterior", r"GNV\s+anterior", r'data-legend="previous"', r"autocal-previous", r"autocal-history",
    r"previousReferencePoints", r"comparisonPinned", r"chartHistoryVisible", r"renderResetComparison", r"referenceComparison",
    r"autocalResetComparison", r"historyCurve", r"\bhistory\b", r'class="[^"]*\bprevious\b', r"\.autocal-reference-line\.previous",
    r"_GAS_PREV",
]


class AutoCalSemLeituraAnterior(unittest.TestCase):
    def test_nenhum_arquivo_do_autocal_tem_leitura_anterior(self):
        for name in FILES:
            src = (UI / name).read_text(encoding="utf-8")
            for pattern in FORBIDDEN:
                self.assertIsNone(re.search(pattern, src, re.I if pattern[0].isupper() or pattern == r"\bhistory\b" else 0), f"{name}: {pattern}")

    def test_trava_registrada_nas_regras(self):
        agents = (REPO / "AGENTS.md").read_text(encoding="utf-8")
        if "UI_ROOT" not in os.environ:
            self.assertIn("autocal-sem-leitura-anterior", agents, "a trava sumiu do AGENTS.md")
            self.assertTrue((REPO / "tests/ui/autocal-sem-leitura-anterior.test.cjs").exists())


if __name__ == "__main__":
    unittest.main()
