#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt"
text = SOURCE.read_text(encoding="utf-8")


def section(start: str, end: str) -> str:
    a = text.index(start)
    b = text.index(end, a)
    return text[a:b]


init = section("    private fun initializeElm", "    private fun pollCycle")
expected = 'listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")'
assert expected in init, "Blue must use the field-proven ELM init sequence"
for forbidden in ("ATI", "ATAT1", "discoverStandardPids", "supportsStandardPid", "stftProbe"):
    assert forbidden not in init, f"Blue init must not hard-gate on {forbidden}"

cmd = section("    private fun elmCommand", "    private fun normalizeElm")
drain = "while (input.available() > 0) input.read()"
write = 'output.write((command.trim() + "\\r").toByteArray(StandardCharsets.US_ASCII))'
assert drain in cmd, "Blue must drain stale ELM bytes before every command"
assert cmd.index(drain) < cmd.index(write), "ELM input drain must happen before command write"

pid = section("    private fun readPidTimed", "    private fun pidAgeMs")
assert "PidRead(null, startedAt" in pid, "A transient PID read failure must become a missing sample"
assert "throw error" not in pid, "A single missing STFT read must not tear down the OBD session"

print("BLUE_OBD_PROVEN_TRANSPORT_CONTRACT=PASS")
