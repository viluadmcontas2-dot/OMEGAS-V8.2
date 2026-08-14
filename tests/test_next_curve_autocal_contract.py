#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEXT = ROOT / "app/src/main/assets/ui-next"
ROUTE = NEXT / "routes/curva-k.js"
BOOT = NEXT / "bootstrap.js"
SIM = NEXT / "adapters/simulated-curve.js"
MANAGER = ROOT / "app/src/main/java/com/omegas/prohub/calibration/KFactorManager.kt"
AUTOCAL = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt"


class NextCurveAutoCalContractTest(unittest.TestCase):
    def test_one_global_route_has_three_perspectives(self):
        route = ROUTE.read_text(encoding="utf-8")
        for label in ["Ajustar Curva K", "AutoCal", "Comparar"]:
            self.assertIn(label, route)
        self.assertNotIn("Mapa K local", route)

    def test_curve_uses_exactly_thirty_simulated_points(self):
        sim = SIM.read_text(encoding="utf-8")
        self.assertIn("length: 30", sim)
        self.assertIn("pointCount: 30", sim)
        route = ROUTE.read_text(encoding="utf-8")
        self.assertIn("30 pontos reais", route)

    def test_preparing_curve_point_does_not_write(self):
        boot = BOOT.read_text(encoding="utf-8")
        self.assertIn("prepareCurvePoint", boot)
        self.assertIn("Confirmar Curva K — indisponível no simulador", boot)
        for token in ["startKFactorWrite(", "protocolTransaction(", "OmegasNative"]:
            self.assertNotIn(token, boot)

    def test_autocal_disable_warning_is_not_innocent_pause(self):
        sim = SIM.read_text(encoding="utf-8")
        route = ROUTE.read_text(encoding="utf-8")
        self.assertIn("retirar o efeito da correção K", sim)
        self.assertIn("revisão crítica", route)
        self.assertNotIn("Pausar AutoCal", route)
        native = AUTOCAL.read_text(encoding="utf-8")
        self.assertIn("changesEffectiveKCorrection", native)

    def test_finish_is_not_invented(self):
        route = ROUTE.read_text(encoding="utf-8")
        self.assertIn("Finish não é inventado", route)
        self.assertNotIn("FINISH_AUTO_CAL", route)

    def test_real_curve_writer_stays_kotlin_owned(self):
        manager = MANAGER.read_text(encoding="utf-8")
        self.assertIn("class KFactorManager", manager)
        route = ROUTE.read_text(encoding="utf-8")
        self.assertNotIn("KFactorManager", route)


if __name__ == "__main__":
    unittest.main()
