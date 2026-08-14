#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEXT = ROOT / "app/src/main/assets/ui-next"
STORE = NEXT / "core/store.js"
NATIVE = NEXT / "adapters/native-next.js"
REVISION = NEXT / "revision-events.js"


class NextSessionReconnectContractTest(unittest.TestCase):
    def test_store_detects_session_id_replacement(self):
        source = STORE.read_text(encoding="utf-8")
        self.assertIn("reduceTelemetryUpdated", source)
        self.assertIn("sessionReplaced", source)
        self.assertIn("incomingSessionId !== previousSessionId", source)
        self.assertIn("NATIVE_SESSION_REPLACED", source)

    def test_new_session_invalidates_structural_context_without_page_reload(self):
        source = STORE.read_text(encoding="utf-8")
        self.assertIn("mapK: Object.freeze", source)
        self.assertIn("curveK: Object.freeze", source)
        self.assertIn("predictor: Object.freeze", source)
        self.assertIn("suggestions: Object.freeze", source)
        self.assertIn("autocal: Object.freeze", source)
        self.assertIn("A interface foi preservada", source)
        for path in NEXT.rglob("*.js"):
            js = path.read_text(encoding="utf-8")
            self.assertNotIn("location.reload", js, str(path))
            self.assertNotIn("window.location.reload", js, str(path))

    def test_native_refresh_event_carries_session_id_and_fast_snapshot_reconciles(self):
        native = NATIVE.read_text(encoding="utf-8")
        revision = REVISION.read_text(encoding="utf-8")
        self.assertIn("nativeSessionId", native)
        self.assertIn("subscribeRevisions", native)
        self.assertIn("nextAdapter.fastTelemetry()", revision)
        self.assertIn("TELEMETRY_UPDATED", revision)

    def test_reconnect_never_calls_writer_automatically(self):
        store = STORE.read_text(encoding="utf-8")
        revision = REVISION.read_text(encoding="utf-8")
        for token in ["startBatchWrite", "startKFactorWrite", "protocolTransaction", "writeK"]:
            self.assertNotIn(token, store)
            self.assertNotIn(token, revision)


if __name__ == "__main__":
    unittest.main()
