import re
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MONITOR = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt"
SERVICE = ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt"


def extract_expression_function(source: str, signature: str) -> str:
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 0
    for index in range(brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError(f"unclosed function: {signature}")


def function_body(source: str, signature: str) -> str:
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 0
    for index in range(brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[brace + 1:index]
    raise AssertionError(f"unclosed function: {signature}")


class Owner116TransversalRuntimeGate(unittest.TestCase):
    def test_1000_real_status_and_snapshot_calls_do_not_request_io(self):
        source = MONITOR.read_text("utf-8")
        status_fn = extract_expression_function(source, "fun statusJson(): JSONObject")
        latest_fn = extract_expression_function(source, "fun latestSnapshotJson(): JSONObject")

        with tempfile.TemporaryDirectory(prefix="owner116-runtime-") as tmp:
            tmp = Path(tmp)
            main = tmp / "Main.kt"
            main.write_text(textwrap.dedent(f'''
                package com.omegas.prohub.autocal

                class JSONObject() {{
                    companion object {{ val NULL = Any() }}
                    constructor(raw: String): this()
                    fun put(key: String, value: Any?): JSONObject = this
                    override fun toString(): String = "{{}}"
                }}
                class Identity {{
                    val functionFingerprint = "F"
                    fun materiallyUsable() = true
                }}
                class Harness {{
                    private val lock = Any()
                    private var state = JSONObject()
                    private var latestSnapshot = JSONObject()
                    private var snapshotRequested = false
                    private var snapshotReason = ""
                    private var calibrationBootstrapAttempted = false
                    private var calibrationIdentity: Identity? = null
                    private var latestAutoMatchEvent: JSONObject? = null
                    var ioCalls = 0
                        private set
                    private fun probeMetricsJson(): JSONObject = JSONObject()

                    {status_fn}
                    {latest_fn}

                    fun requested() = snapshotRequested
                }}
                fun main() {{
                    val h = Harness()
                    repeat(1000) {{
                        h.statusJson()
                        h.latestSnapshotJson()
                    }}
                    check(h.ioCalls == 0)
                    check(!h.requested())
                    println("OWNER_116_TRANSVERSAL_RUNTIME=PASS")
                }}
            '''), encoding="utf-8")
            jar = tmp / "owner116.jar"
            subprocess.run(["kotlinc", str(main), "-include-runtime", "-d", str(jar)], check=True, capture_output=True, text=True, timeout=30)
            result = subprocess.run(["java", "-jar", str(jar)], check=True, capture_output=True, text=True, timeout=10)
            self.assertIn("OWNER_116_TRANSVERSAL_RUNTIME=PASS", result.stdout)

    def test_real_service_keeps_ui_projection_separate_from_health_tick(self):
        monitor = MONITOR.read_text("utf-8")
        service = SERVICE.read_text("utf-8")
        status = function_body(monitor, "fun statusJson()")
        latest = function_body(monitor, "fun latestSnapshotJson()")
        tick = function_body(monitor, "fun tick()")

        self.assertNotIn("serial.transaction(", status)
        self.assertNotIn("readFullSnapshot(", status)
        self.assertNotIn("requestSnapshot(", status)
        self.assertNotIn("serial.transaction(", latest)
        self.assertNotIn("readFullSnapshot(", latest)
        self.assertNotIn("requestSnapshot(", latest)
        self.assertIn("readFullSnapshot(currentSession, probe, countIncreased)", tick)
        self.assertIn("nativeAutoCal.statusJson().toString()", service)
        self.assertIn("nativeAutoCal.latestSnapshotJson().toString()", service)
        self.assertIn("nativeAutoCal.tick()", service)
        self.assertRegex(service, re.compile(r"scheduleWithFixedDelay\(::healthTick,\s*200L,\s*3000L", re.S))


if __name__ == "__main__":
    unittest.main()
