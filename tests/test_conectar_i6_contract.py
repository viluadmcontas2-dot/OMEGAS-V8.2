"""I6: contrato (texto) da conexão automática: política pura, supervisor por contagem, diário e ponte."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SVC = ROOT / "app/src/main/java/com/omegas/prohub/service"
STO = ROOT / "app/src/main/java/com/omegas/prohub/storage"
BRIDGE = ROOT / "app/src/main/java/com/omegas/prohub/web/CalibrationJavascriptBridge.kt"
TESTS = ROOT / "app/src/test/java/com/omegas/prohub/service"


def read(p):
    assert p.exists(), f"arquivo ausente: {p}"
    return p.read_text(encoding="utf-8")


class ConectarI6(unittest.TestCase):
    def test_politica_tem_fases_e_causas(self):
        s = read(SVC / "ConnectionPolicy.kt")
        for f in ("PROCURANDO", "PERMISSAO", "CONECTANDO", "LENDO", "PRONTO", "PROBLEMA", "RETOMANDO"):
            self.assertRegex(s, rf"\b{f}\b")
        for c in ("SEM_PERMISSAO", "CABO_SEM_CENTRAL", "CENTRAL_NAO_RESPONDE", "APARELHO_INCOMPATIVEL"):
            self.assertRegex(s, rf"\b{c}\b")

    def test_sem_relogio_nos_tres_arquivos(self):
        for p in (SVC / "ConnectionPolicy.kt", SVC / "ConnectionSupervisor.kt", STO / "WriteJournal.kt"):
            s = read(p)
            for banido in ("currentTimeMillis", "nanoTime", "delay(", "Thread.sleep", "Handler", "postDelayed", "Timer", "Instant", "elapsedRealtime"):
                self.assertNotIn(banido, s, f"{p.name} usa relógio: {banido}")

    def test_supervisor_decide_por_contagem_e_nao_toca_protegidas(self):
        s = read(SVC / "ConnectionSupervisor.kt")
        self.assertIn("MAX_READS_WITHOUT_FRAME", s)
        self.assertIn("MAX_RECONNECTS", s)
        self.assertIn("MAX_RECONNECTS", read(SVC / "ConnectionPolicy.kt"))
        self.assertRegex(s, r"fun onEvent\(")
        for ev in ("UsbAttached", "UsbDetached", "PermissionGranted", "PermissionDenied", "Read(", "HandshakeOk"):
            self.assertIn(ev, read(SVC / "ConnectionPolicy.kt") + s)
        for protegida in ("UsbSerialManager", "KWriteManager", "KFactorManager", "ResponseDrivenEcuEngine"):
            self.assertNotRegex(s, rf"import .*{protegida}")

    def test_diario_atomico_e_nunca_retoma_sozinho(self):
        s = read(STO / "WriteJournal.kt")
        for nome in ("fun open(", "fun mark(", "fun close(", "fun pending("):
            self.assertIn(nome, s)
        self.assertRegex(s, r"renameTo|Files\.move|ATOMIC_MOVE")
        self.assertIn(".tmp", s)

    def test_ponte_expoe_estado_da_conexao(self):
        s = read(BRIDGE)
        self.assertRegex(s, r"@JavascriptInterface\s+fun getConnection\(\)")
        self.assertRegex(s, r"@JavascriptInterface\s+fun getPendingWrite\(\)")

    def test_testes_jvm_existem(self):
        for n in ("service/ConnectionPolicyTest.kt", "service/ConnectionSupervisorTest.kt", "storage/WriteJournalTest.kt"):
            self.assertTrue((TESTS.parent / n).exists(), n)


if __name__ == "__main__":
    unittest.main()
