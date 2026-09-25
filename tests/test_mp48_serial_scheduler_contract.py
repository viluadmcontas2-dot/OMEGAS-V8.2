#!/usr/bin/env python3
from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / "app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt"
SCHEDULER = ROOT / "app/src/main/java/com/omegas/prohub/ecu/Mp48SerialScheduler.kt"


class Mp48SerialSchedulerContractTest(unittest.TestCase):
    def test_only_engine_owns_protocol_transaction_outside_usb_transport(self):
        offenders = []
        source_root = ROOT / "app/src/main/java/com/omegas/prohub"
        for path in source_root.rglob("*.kt"):
            if path.name in {"UsbSerialManager.kt", "ResponseDrivenEcuEngine.kt"}:
                continue
            if "protocolTransaction(" in path.read_text(encoding="utf-8"):
                offenders.append(path.relative_to(ROOT).as_posix())
        self.assertEqual([], offenders)

    def test_known_clients_use_scheduler_fast_ack_and_final_readback_units(self):
        kwrite = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/KWriteManager.kt").read_text(encoding="utf-8")
        kfactor = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/KFactorManager.kt").read_text(encoding="utf-8")
        bridge = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt").read_text(encoding="utf-8")
        self.assertNotIn("UsbSerialManager", kwrite)
        self.assertNotIn("UsbSerialManager", kfactor)
        self.assertNotIn("protocolTransaction(", bridge)
        self.assertRegex(
            kwrite,
            re.compile(r"serial\.unit\([\s\S]*?writeKCell\(", re.M),
        )
        self.assertIn("BATCH_VERIFYING_ROWS", kwrite)
        self.assertIn("confirmação final K[$row]", kwrite)
        self.assertNotRegex(
            kwrite,
            re.compile(r"serial\.unit\([\s\S]*?writeKCell\([\s\S]*?readRow\(unit,", re.M),
        )
        self.assertRegex(
            kfactor,
            re.compile(r"serial\.unit\([\s\S]*?writeFactor\(", re.M),
        )
        self.assertIn("VERIFYING_FINAL", kfactor)
        self.assertIn("confirmação final K factor", kfactor)
        self.assertNotRegex(
            kfactor,
            re.compile(r"serial\.unit\([\s\S]*?writeFactor\([\s\S]*?readRawPoints\(unit,", re.M),
        )

    def test_engine_protects_telemetry_opportunity_and_definitive_mutation_wait(self):
        source = ENGINE.read_text(encoding="utf-8")
        self.assertIn("PriorityBlockingQueue<QueuedSerialWork>", source)
        self.assertIn("thenBy { it.sequence }", source)
        self.assertRegex(
            source,
            re.compile(r"runQueued\(queued\)[\s\S]*?queued\.telemetryAfter[\s\S]*?pollTelemetry\(\)", re.M),
        )
        self.assertIn("if (workClass == Mp48WorkClass.READ_ONLY)", source)
        self.assertIn("future.get(waitTimeoutMs.coerceAtLeast(250L), TimeUnit.MILLISECONDS)", source)
        self.assertIn("future.get()", source)



if __name__ == "__main__":
    unittest.main()
