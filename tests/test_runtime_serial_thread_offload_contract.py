#!/usr/bin/env python3
"""A thread serial MP48 não monta JSON de quadro nem avisa overlay/notificação (gaps de telemetria)."""
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt"


def body(source: str, start: str, end: str) -> str:
    begin = source.index(start)
    return source[begin:source.index(end, begin)]


class RuntimeSerialThreadOffloadContract(unittest.TestCase):
    def setUp(self) -> None:
        self.source = RUNTIME.read_text(encoding="utf-8")

    def test_consume_state_only_touches_cheap_fields_and_defers_the_rest(self):
        consume = body(self.source, "private fun consumeState(", "private fun deliverState(")
        self.assertIn("stateDeliveryPipeline.submit(", consume)
        self.assertNotIn("onStateChanged()", consume)
        self.assertNotIn("JSONObject(latestSnapshot.toString())", consume)
        deliver = body(self.source, "private fun deliverState(", "private fun reportExit(")
        self.assertIn("JSONObject(latestSnapshot.toString())", deliver)
        self.assertIn("onStateChanged()", deliver)
        self.assertIn("pendingExitNotice.getAndSet(null)", deliver)

    def test_telemetry_json_is_built_inside_the_delivery_pipeline(self):
        consume = body(self.source, "private fun consumeTelemetry(", "private fun telemetryEvent(")
        before_submit = consume[:consume.index("telemetryDeliveryPipeline.submit(")]
        self.assertNotIn("toJson()", before_submit)
        self.assertIn("telemetryEvent(telemetry, decision, metrics, generation, frameAtWallMs)", consume)

    def test_stop_flushes_both_pipelines(self):
        flush = body(self.source, "private fun flushPipelines(", "private fun emptySnapshot(")
        self.assertIn("telemetryDeliveryPipeline.flush(", flush)
        self.assertIn("stateDeliveryPipeline.flush(", flush)


if __name__ == "__main__":
    unittest.main()
