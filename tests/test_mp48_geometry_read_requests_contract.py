import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PROTOCOL = ROOT / "app/src/main/java/com/omegas/prohub/ecu/Mp48Protocol.kt"


class Mp48GeometryReadRequestsContract(unittest.TestCase):
    def setUp(self):
        self.source = PROTOCOL.read_text("utf-8")

    def test_geometry_addresses_are_the_oem_map_k_pair(self):
        self.assertRegex(self.source, r"TEMPI_PER_K_ADDRESS\s*=\s*0x0037")
        self.assertRegex(self.source, r"GIRI_PER_K_ADDRESS\s*=\s*0x003D")
        self.assertNotRegex(self.source, r"(?:TEMPI_PER_K_ADDRESS|GIRI_PER_K_ADDRESS)\s*=\s*0x000C")

    def test_read_builders_emit_the_exact_oem_request_bodies(self):
        self.assertRegex(
            self.source,
            r"fun\s+readKPetrolAxis\(\):\s*ByteArray\s*=\s*frame\(byteArrayOf\(0x29,\s*0x37,\s*0x00\)\)",
        )
        self.assertRegex(
            self.source,
            r"fun\s+readKRpmAxis\(\):\s*ByteArray\s*=\s*frame\(byteArrayOf\(0x29,\s*0x3D,\s*0x00\)\)",
        )

    def test_expected_checksums_are_independently_derived(self):
        def checksum(body):
            return sum(body) & 0xFF

        self.assertEqual(0x60, checksum([0x29, 0x37, 0x00]))
        self.assertEqual(0x66, checksum([0x29, 0x3D, 0x00]))


if __name__ == "__main__":
    unittest.main()
