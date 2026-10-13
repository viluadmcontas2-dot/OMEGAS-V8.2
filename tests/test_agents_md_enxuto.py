"""Trava do dono (2026-10-13): AGENTS.md é contrato + mapa + fatos, curto e sem referência quebrada."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEXT = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
MAX_LINHAS = 80


class AgentsMdEnxuto(unittest.TestCase):
    def test_tamanho_limitado(self):
        self.assertLessEqual(len(TEXT.splitlines()), MAX_LINHAS, "AGENTS.md engordou: mova o conteúdo para docs/ e aponte")

    def test_sem_estado_vivo(self):
        self.assertIsNone(re.search(r"\b[0-9a-f]{40}\b|\bSHA[ :`]+[0-9a-f]{7,}", TEXT), "SHA é estado vivo; fica no checkpoint")

    def test_caminhos_citados_existem(self):
        faltando = []
        for ref in re.findall(r"`([^`\s]+/[^`\s]+\.[A-Za-z]+)`", TEXT):
            base = ref.rstrip("/")
            if any(c in base for c in "*<>"):
                continue
            candidatos = [ROOT / base]
            if not base.startswith(("app/", "docs/", "tests/", "tools/", ".")):
                candidatos += list((ROOT / "app/src/main/java/com/omegas/prohub").rglob(Path(base).name))
            if not any(c.exists() for c in candidatos):
                faltando.append(ref)
        self.assertEqual([], faltando)

    def test_nomes_de_arquivo_do_mapa_existem(self):
        for nome in re.findall(r"`([A-Za-z0-9_]+\.(?:kt|js|cjs|py))`", TEXT):
            achou = list(ROOT.rglob(nome))
            self.assertTrue(achou, f"{nome} citado no AGENTS.md não existe")


if __name__ == "__main__":
    unittest.main()
