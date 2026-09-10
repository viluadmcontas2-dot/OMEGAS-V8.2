from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
text = (ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt").read_text(encoding="utf-8")
start = text.index("    private fun pollCycle(sock: BluetoothSocket) {")
end = text.index("    private fun readPid(sock: BluetoothSocket", start)
poll = text[start:end]
for token in ['readPidTimed(sock, "010C", 0x0C)', 'readPidTimed(sock, "010B", 0x0B)', 'readPidTimed(sock, "0106", 0x06)', "ObdPidCycle.decode", '"OBD_GNV_CYCLE"']:
    assert token in poll, token
for forbidden in ["petrol_ms", "nearestFrame", "ObdWitnessEngine"]:
    assert forbidden not in poll, forbidden
print("BLUE_OBD_STFT_ONLY_CONTRACT=PASS")
