"""reset-nunca-pausa-aprendizado — TRAVA PERMANENTE (regra 14 do AGENTS.md; dono, 2026-10-08).

Reset de gasolina/GNV/tudo nunca pausa o aprendizado: AUTO_CAL_ENABLE termina em 1, conferido por readback,
mesmo em falha parcial/timeout. O mapeamento botão -> comando e fuel nunca cruza. Nenhum agente pode remover.
Par Kotlin: ResetNuncaPausaAprendizadoTest. Mutante: reset-sem-religar (tools/wiring/run_ui_mutants.py).
"""
import os
import re
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
ROOT = Path(os.environ.get("RESET_ROOT", REPO))
FULL_TREE = "RESET_ROOT" not in os.environ
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
        self.assertIn("keepLearningEnabled(prepared)", fixed)          # caminho feliz
        self.assertIn("learningRestoreAfterFailure(prepared, error)", fixed)  # timeout/NAK/USB
        self.assertLess(fixed.index("keepLearningEnabled(prepared)"), fixed.index("validateActionReadback"))
        keep = body(MANAGER, "private fun keepLearningEnabled(", "private fun readAutoCalEnable(")
        self.assertIn("AutoCalProtocol.setEnabled(true)", keep)
        self.assertNotIn("setEnabled(false)", keep)
        self.assertIn("readAutoCalEnable(prepared) == 1", keep)
        self.assertIn("throw IllegalStateException", keep)  # sem readback =1 o reset NÃO é "concluído"
        self.assertIn("LEARNING_RESTORE_ATTEMPTS = 3", MANAGER)

    def test_reset_nunca_escreve_pausa(self):
        fixed = body(MANAGER, "private fun executeFixedAction(", "private fun executeFinish(")
        self.assertNotIn("setEnabled(false)", fixed)
        self.assertNotIn("DISABLE_AUTO_CAL", fixed)
        # a UI: o caminho dos botões de reset não chama o interruptor de pausa
        for name in ("prepare(action) {", "confirmPrepared() {"):
            seg = body(COCKPIT, name, "\n    }\n")
            self.assertNotIn("setAcquisitionEnabled", seg)
            self.assertNotIn("DISABLE_AUTO_CAL", seg)

    def test_mapa_botao_para_comando_e_combustivel(self):
        modes = dict(re.findall(r"(RESET_\w+)\((0x[0-9A-Fa-f]+)\)", body(PROTOCOL, "enum class ManualActionMode", "}")))
        self.assertEqual({"RESET_PETROL": "0x01", "RESET_GAS": "0x02", "RESET_ALL": "0x04"}, modes)
        for name in ("RESET_PETROL", "RESET_GAS", "RESET_ALL"):
            self.assertRegex(
                MANAGER,
                rf"{name}\(\s*AutoCalProtocol\.manualAction\(AutoCalProtocol\.ManualActionMode\.{name}\)",
            )
        # botões da tela -> ação (gasolina não manda a do GNV e vice-versa)
        self.assertRegex(COCKPIT, r'data-autocal-action="RESET_GAS">Reler GNV<')
        self.assertRegex(COCKPIT, r'data-autocal-action="RESET_PETROL">Reler gasolina<')
        self.assertIn("this.prepare(button.dataset.autocalAction)", COCKPIT)
        # Curva K tem caminho próprio (30 escritas em MUL_ACT), nunca o 0x24
        self.assertIn("if (action === 'RESET_K_FACTOR') return;", COCKPIT)
        self.assertRegex(MANAGER, r'RESET_K_FACTOR\(\s*byteArrayOf\(\)')

    def test_a_trava_esta_registrada(self):
        if not FULL_TREE:
            self.skipTest("árvore de mutante: sem documentos")
        agents = (REPO / "AGENTS.md").read_text(encoding="utf-8")
        self.assertRegex(agents, r"14\.\s+Reset de gasolina/GNV nunca pausa o aprendizado")
        self.assertIn("reset-nunca-pausa-aprendizado", agents)
        rules = (REPO / ".claude/hooks/owner-rules.txt").read_text(encoding="utf-8")
        self.assertIn("reset-nunca-pausa-aprendizado", rules)
        ci = (REPO / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        self.assertIn("reset-nunca-pausa-aprendizado", ci)
        self.assertIn("ResetNuncaPausaAprendizadoTest", ci)
        runner = (REPO / "tools/run_checks.py").read_text(encoding="utf-8")
        self.assertIn("reset-nunca-pausa-aprendizado", runner)
        self.assertTrue((REPO / "app/src/test/java/com/omegas/prohub/autocal/ResetNuncaPausaAprendizadoTest.kt").exists())


if __name__ == "__main__":
    unittest.main()
