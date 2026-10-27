import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STORE = (ROOT / "app/src/main/java/com/omegas/prohub/session/DiarioStore.kt").read_text(encoding="utf-8")
BRIDGE = (ROOT / "app/src/main/java/com/omegas/prohub/web/CalibrationJavascriptBridge.kt").read_text(encoding="utf-8")


class DiarioStoreKotlin(unittest.TestCase):
    def test_poda_em_2000(self):
        self.assertIn("MAX_EVENTS = 2000", STORE)
        self.assertIn("takeLast(MAX_EVENTS)", STORE)

    def test_escrita_atomica(self):
        self.assertIn("ATOMIC_MOVE", STORE)
        self.assertIn("StandardCopyOption.REPLACE_EXISTING", STORE)
        self.assertRegex(STORE, r"\.tmp")

    def test_leitura_tolerante_descarta_so_linha_ruim(self):
        self.assertIn("lineSequence()", STORE)
        self.assertRegex(STORE, r"catch \(_: Exception\)")
        self.assertIn("mapNotNull", STORE)
        self.assertIn("format", STORE)

    def test_ponte_minima(self):
        for name in ("loadDiario", "saveDiario"):
            self.assertRegex(BRIDGE, r"@JavascriptInterface\s+fun %s\(" % name)
        self.assertIn("DiarioStore(", BRIDGE)

    def test_nao_toca_protegidas(self):
        for name in ("UsbSerialManager", "ResponseDrivenEcuEngine", "AutoCalProtocol", "KFactorManager", "KWriteManager", "AutoCalNativeActionManager"):
            self.assertNotIn(name, STORE)


if __name__ == "__main__":
    unittest.main()
