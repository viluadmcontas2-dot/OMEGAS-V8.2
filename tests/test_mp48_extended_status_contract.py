import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROTOCOL = ROOT / "app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt"
SNAPSHOT = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalSnapshot.kt"
REPLY = ROOT / "app/src/main/java/com/omegas/prohub/usb/UsbProtocolReply.kt"
ENGINE = ROOT / "app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt"


class Mp48ExtendedStatusContract(unittest.TestCase):
    def setUp(self):
        self.protocol = PROTOCOL.read_text("utf-8")
        self.snapshot = SNAPSHOT.read_text("utf-8")
        self.reply = REPLY.read_text("utf-8")
        self.engine = ENGINE.read_text("utf-8")

    def test_module_version_is_observed_but_vector_shape_belongs_to_field_identity(self):
        self.assertIn('Field("MODULE_VERSION", 0x0173', self.protocol)
        self.assertIn('READ_ONLY_FIELDS: List<Field> = listOf(\n        MODULE_VERSION,', self.protocol)
        for field in ("PETR_INJ_TBP", "MUL_ACT", "PETR_MNFLD_PRESS_RV", "GAS_MNFLD_PRESS_RV"):
            self.assertIn(field, self.protocol)
        self.assertIn("return field.expectedElementsHint", self.protocol)
        self.assertIn("requireExpectedShape(decoded, moduleVersion)", self.snapshot)
        self.assertIn('put("moduleVersion"', self.snapshot)

    def test_counters_remain_eighteen_u16(self):
        self.assertIn('Field("NUM_BUF_UPD_PETR", 0x015B, Encoding.U16_LE, Shape.VECTOR, 18)', self.protocol)
        self.assertIn('Field("NUM_BUF_UPD_GAS", 0x015C, Encoding.U16_LE, Shape.VECTOR, 18)', self.protocol)

    def test_extended_statuses_are_typed_without_guessing_retry(self):
        for marker in (
            "EXTENDED_RETRYABLE",
            "EXTENDED_NON_RETRYABLE",
            "EXTENDED_UNKNOWN",
            "EXTENDED_RETRYABLE_CODE = 0x08",
            "EXTENDED_NON_RETRYABLE_CODE = 0x10",
        ):
            self.assertIn(marker, self.reply)
        self.assertIn("rawResponse: ByteArray", self.reply)

    def test_handshake_uses_typed_policy(self):
        self.assertIn("when (init1.statusClass)", self.engine)
        self.assertIn("UsbProtocolStatusClass.EXTENDED_RETRYABLE", self.engine)
        self.assertIn("UsbProtocolStatusClass.EXTENDED_NON_RETRYABLE", self.engine)
        self.assertIn("UsbProtocolStatusClass.EXTENDED_UNKNOWN", self.engine)
        self.assertIn("handshakeRejected", self.engine)
        self.assertIn("handshakeNonRetryable", self.engine)
        self.assertIn("retry bloqueado até nova sessão USB", self.engine)
        self.assertNotIn('"MP48 identificação"', self.engine)


if __name__ == "__main__":
    unittest.main()
