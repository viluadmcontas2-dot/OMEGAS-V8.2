"""reset-nunca-pausa-aprendizado — TRAVA PERMANENTE (dono, 2026-10-08; portada da linha Diamante em 2026-10-11).

Readquirir gasolina/GNV e Nova aquisição completa nunca deixam o aprendizado pausado: AUTO_CAL_ENABLE termina em 1,
conferido por readback, mesmo em falha parcial/timeout (erro de transporte ≠ erro da ECU). O botão manda só o comando
do próprio combustível. Par Kotlin: ResetNuncaPausaAprendizadoTest (mutante reset-sem-religar morre lá).
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANAGER = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt").read_text(encoding="utf-8")
PROTOCOL = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt").read_text(encoding="utf-8")
COCKPIT = (ROOT / "app/src/main/assets/ui/screens/autocal-cockpit.js").read_text(encoding="utf-8")


def body(source, start, end):
    return source.split(start, 1)[1].split(end, 1)[0]


class ResetNuncaPausaAprendizado(unittest.TestCase):
    def test_todo_reset_de_aquisicao_religa_e_confere_o_aprendizado(self):
        self.assertRegex(
            MANAGER,
            r"LEARNING_PRESERVING_RESETS\s*=\s*setOf\(Action\.RESET_PETROL,\s*Action\.RESET_GAS,\s*Action\.RESET_ALL\)",
        )
        fixed = body(MANAGER, "private fun executeFixedAction(", "private fun sendFixedAction(")
        self.assertIn("val isReset = prepared.action in LEARNING_PRESERVING_RESETS", fixed)
        self.assertIn("keepLearningEnabled(prepared)", fixed)  # caminho feliz
        self.assertIn("learningRestoreAfterFailure(prepared, error)", fixed)  # timeout/NAK/USB
        self.assertLess(fixed.index("keepLearningEnabled(prepared)"), fixed.index("validateActionReadback"))
        keep = body(MANAGER, "private fun keepLearningEnabled(", "private fun readAutoCalEnable(")
        self.assertIn("AutoCalProtocol.setEnabled(true)", keep)
        self.assertNotIn("setEnabled(false)", keep)
        self.assertIn("readAutoCalEnable(prepared) == 1", keep)
        self.assertIn("throw IllegalStateException", keep)  # sem readback = 1 o reset NÃO é "concluído"
        self.assertIn("LEARNING_RESTORE_ATTEMPTS = 3", MANAGER)
        self.assertIn('"KEEPING_LEARNING"', MANAGER)

    def test_reset_nunca_escreve_pausa(self):
        fixed = body(MANAGER, "private fun executeFixedAction(", "private fun executeFinish(")
        self.assertNotIn("setEnabled(false)", fixed)
        self.assertNotIn("DISABLE_AUTO_CAL", fixed)
        # Na tela, o caminho dos botões de reset não chama o interruptor de pausa.
        for name in ("prepare(action) {", "confirmPrepared() {"):
            seg = body(COCKPIT, name, "\n    }\n")
            self.assertNotIn("setAcquisitionEnabled", seg)
            self.assertNotIn("DISABLE_AUTO_CAL", seg)
        # A etapa nova aparece como trabalho em andamento na tela.
        self.assertGreaterEqual(COCKPIT.count("'KEEPING_LEARNING'"), 3)

    def test_mapa_botao_para_comando_e_combustivel(self):
        modes = dict(re.findall(r"(RESET_\w+)\((0x[0-9A-Fa-f]+)\)", body(PROTOCOL, "enum class ManualActionMode", "}")))
        self.assertEqual({"RESET_PETROL": "0x01", "RESET_GAS": "0x02", "RESET_ALL": "0x04"}, modes)
        for name in ("RESET_PETROL", "RESET_GAS", "RESET_ALL"):
            self.assertRegex(
                MANAGER,
                rf"{name}\(\s*AutoCalProtocol\.manualAction\(AutoCalProtocol\.ManualActionMode\.{name}\)",
            )
        # (dono, 2026-10-11: texto para leigo): "Reaprender … (apaga o atual)".
        self.assertRegex(COCKPIT, r'data-autocal-action="RESET_GAS">Reaprender GNV <')
        self.assertRegex(COCKPIT, r'data-autocal-action="RESET_PETROL">Reaprender gasolina <')
        self.assertIn("this.prepare(button.dataset.autocalAction);", COCKPIT)
        # Curva K tem caminho próprio (30 escritas em MUL_ACT), nunca o 0x24.
        self.assertRegex(MANAGER, r"RESET_K_FACTOR\(\s*byteArrayOf\(\)")

    def test_a_trava_esta_registrada(self):
        agents = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        self.assertIn("reset-nunca-pausa-aprendizado", agents)
        self.assertTrue((ROOT / "app/src/test/java/com/omegas/prohub/autocal/ResetNuncaPausaAprendizadoTest.kt").exists())
        ci = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        self.assertIn("testDebugUnitTest", ci)  # o teste Kotlin roda no CI com todos os outros


if __name__ == "__main__":
    unittest.main()
