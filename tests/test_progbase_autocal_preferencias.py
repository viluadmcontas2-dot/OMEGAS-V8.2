"""Contrato versionado + replay opcional; não acessa ECU nem modifica o .exe."""
import importlib.util
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools/emulacao/autocal_preferencias_original.py"
spec = importlib.util.spec_from_file_location("autocal_prefs", SCRIPT)
prefs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prefs)
DOC = ROOT / "docs/progbase/protocolo/autocal-preferencias.md"


def capture():
    return json.loads(re.search(r"```json\n(.*?)\n```", DOC.read_text(), re.S)[1])


class Versionado(unittest.TestCase):
    def test_quadros_sc_indice_valor_checksum(self):
        data = capture()
        self.assertEqual(data["sha256"], prefs.SHA256)
        self.assertEqual(len(data["casos"]), 61)
        for row in data["casos"]:
            with self.subTest(nome=row["nome"], indice=row["indice"]):
                w, r = (bytes.fromhex(row[k]) for k in ("gravacao", "leitura"))
                for q in (w, r):
                    self.assertEqual(sum(q[:-1]) & 255, q[-1])
                    self.assertEqual(int.from_bytes(q[1:3], "little"), row["sc"])
                indexed = row["indice"] >= 0
                width = int(prefs.objects()[row["nome"]]["props"].get("DataLength", 1))
                self.assertEqual(w[0], 0x10 + 1 + width + indexed)
                if indexed:
                    self.assertEqual(w[3], row["indice"])
                self.assertEqual(int.from_bytes(w[4 if indexed else 3:-1], "little"), row["raw"])
                self.assertEqual(len(w), (w[0] & 7) + 3)

    def test_oraculos_e_leitura_nao_e_gravacao(self):
        rows = capture()["casos"]
        self.assertEqual(rows[0]["gravacao"], "13 65 01 02 03 7E")
        self.assertEqual(rows[0]["leitura"], "0A 65 01 02 72")
        self.assertEqual(rows[1]["gravacao"], "13 7A 01 B8 0B 51")
        self.assertEqual(rows[-18]["gravacao"], "14 4B 01 00 00 04 64")
        self.assertEqual(capture()["resposta_leitura"], "não executada")
        for row in rows:
            self.assertIn(bytes.fromhex(row["leitura"])[0], (9, 10, 41))
            self.assertNotEqual(row["gravacao"], row["leitura"])

    def test_vinculos_dfm_e_grade_nao_sequencial(self):
        ui = (prefs.DFM / "TAUTOCALUI.txt").read_text()
        self.assertIn("Value = AutoCalDM.VECT_AUTOCAL_U8_2", ui)
        p = prefs.objects()["VECT_AUTOCAL_U8_2"]["props"]
        self.assertEqual((p["ValueMin"], p["ValueMax"], p["DefaultValue"]), ("1.0", "99.0", "3.0"))
        self.assertEqual(prefs.GRID[2:], (0, 4, 5, 6, 1, 2, 3, 7, 8, 9))
        self.assertEqual(prefs.objects()["MODULE_VERSION"]["props"]["ReadOnly"], "True")

    def test_ausencia_explicita_nao_usa_fallback(self):
        self.assertIsNone(prefs.exe_path(ROOT / "arquivo-inexistente-ProgBase.exe"))


class Original(unittest.TestCase):
    def test_reproduz_captura_versionada(self):
        exe = prefs.exe_path()
        if exe is None:
            self.skipTest("ProgBase.exe ausente; testes versionados permanecem ativos")
        for package in ("unicorn", "pefile"):
            if importlib.util.find_spec(package) is None:
                self.skipTest("dependência opcional ausente: " + package)
        self.assertEqual(prefs.emular(exe), capture())


if __name__ == "__main__":
    unittest.main()
