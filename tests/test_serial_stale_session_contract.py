from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
USB = ROOT / "app/src/main/java/com/omegas/prohub/usb/UsbSerialManager.kt"
ENGINE = ROOT / "app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt"


class SerialStaleSessionContract(unittest.TestCase):
    def test_usb_transaction_checks_pinned_session_before_and_after_io(self):
        source = USB.read_text("utf-8")
        pre = source.index("if (expectedSessionId > 0L && connectionSessionId != expectedSessionId)")
        write = source.index("current.write(request, timeoutMs)")
        post = source.index("if (expectedSessionId > 0L && (!connected || connectionSessionId != expectedSessionId))")
        success = source.index("UsbProtocolReply(status == 0x53", post)
        self.assertLess(pre, write)
        self.assertLess(write, post)
        self.assertLess(post, success)
        self.assertIn("Sessão USB mudou durante $reason", source)

    def test_serial_unit_checks_generation_before_work_and_each_transaction(self):
        source = ENGINE.read_text("utf-8")
        self.assertIn("physicalSessionId != work.expectedSessionId", source)
        self.assertIn("physicalSessionId != sessionId", source)
        self.assertIn("expectedSessionId = sessionId", source)
        self.assertIn("queue.forEach { it.fail(IllegalStateException(\"Nova sessão USB\")) }", source)
        self.assertIn("queue.clear()", source)


if __name__ == "__main__":
    unittest.main()
