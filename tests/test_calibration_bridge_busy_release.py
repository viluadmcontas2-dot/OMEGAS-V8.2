"""Lote W, parte 3: a ponte de operações de calibração nunca deixa `busy` preso.

CalibrationOperationsBridge depende de MainActivity (não roda em JVM puro), então o contrato "busy é solto
depois de qualquer callback que lance" é provado pela ESTRUTURA do código, linha a linha:
  * `busy` só é solto em `launch` (no finally e no caminho de executor encerrado);
  * toda aquisição `busy.compareAndSet(false, true)` é seguida de `launch(...)` sem nenhum `return` no meio;
  * `launch` captura Throwable (não só Exception) e solta no finally;
  * toda chamada de `launch` trata o executor encerrado.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = ROOT / "app/src/main/java/com/omegas/prohub/web/CalibrationOperationsBridge.kt"


class CalibrationBridgeBusyRelease(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.src = BRIDGE.read_text("utf-8")
        match = re.search(r"private fun launch\(.*?\n    }\n\n", cls.src, re.S)
        assert match, "função launch não encontrada"
        cls.launch = match.group(0)

    def test_busy_is_released_only_inside_launch(self):
        outside = self.src.replace(self.launch, "")
        self.assertNotIn("busy.set(false)", outside, "busy solto fora de launch: algum caminho pode deixá-lo preso")
        self.assertEqual(self.launch.count("busy.set(false)"), 2, "launch solta no finally e no executor encerrado")

    def test_launch_catches_throwable_and_releases_in_finally(self):
        self.assertIn("catch (error: Throwable)", self.launch)
        self.assertRegex(self.launch, r"finally\s*\{\s*busy\.set\(false\)")
        self.assertIn("catch (_: RejectedExecutionException)", self.launch)

    def test_every_acquisition_reaches_launch_without_an_early_return(self):
        acquisitions = [m.start() for m in re.finditer(r"busy\.compareAndSet\(false, true\)", self.src)]
        self.assertGreaterEqual(len(acquisitions), 4, "o extrator deixou de enxergar as aquisições")
        launches = [m.start() for m in re.finditer(r"= launch\(", self.src)]
        self.assertEqual(len(acquisitions), len(launches), "toda aquisição de busy precisa de um launch")
        for start in acquisitions:
            # o corpo do `if (!busy.compareAndSet...) { return ... }` é a recusa; o que vem depois do `}` que o fecha
            after_if = self.src.index("}", self.src.index("return", start)) + 1
            next_launch = min(l for l in launches if l > start)
            between = self.src[after_if:next_launch]
            self.assertNotRegex(between, r"\breturn\b", "return entre adquirir busy e chamar launch deixaria busy preso")
            self.assertNotRegex(between, r"\bthrow\b")

    def test_every_launch_handles_a_closed_executor(self):
        calls = re.findall(r"val accepted = launch\(", self.src)
        handled = re.findall(r"if \(!accepted\) return executorClosed\(\)", self.src)
        self.assertEqual(len(calls), len(handled))
        self.assertGreaterEqual(len(calls), 4)

    def test_executor_is_single_thread_so_there_is_one_operation_queue(self):
        self.assertEqual(self.src.count("Executors.newSingleThreadExecutor"), 1)
        self.assertEqual(self.src.count("Executors.new"), 1)


if __name__ == "__main__":
    unittest.main()
