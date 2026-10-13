"""O modelo do artefato do PROTOCOLO-LANDI tem um único bloco gerado e nenhuma afirmação defasada fixa."""
import unittest
from pathlib import Path

TPL = (Path(__file__).resolve().parents[1] / "tools/protocolo-artifact/template.html").read_text(encoding="utf-8")


class ProtocoloArtifactTemplate(unittest.TestCase):
    def test_um_bloco_gerado(self):
        self.assertEqual(1, TPL.count("<!--GEN:BEGIN-->"))
        self.assertEqual(1, TPL.count("<!--GEN:END-->"))

    def test_sem_texto_defasado_fixo(self):
        for velho in ("not yet fully reversed", "Cipher algorithm TBD", "decryption routine located, analysis in progress"):
            self.assertNotIn(velho, TPL)


if __name__ == "__main__":
    unittest.main()
