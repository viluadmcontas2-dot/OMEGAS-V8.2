from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    target = ROOT / path
    assert target.is_file(), f"missing recovery seam: {path}"
    return target.read_text(encoding="utf-8")


app = read("app/src/main/assets/ui/app.js")
native_api = read("app/src/main/assets/ui/core/native-api.js")
obd_screen = read("app/src/main/assets/ui/screens/obd.js")
service = read("app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt")
k_writer = read("app/src/main/java/com/omegas/prohub/calibration/KWriteManager.kt")

assert "presentSequence" in app and "presentAgeMs" in app
assert "presentRevision" in app
assert "obdWitness()" in native_api and "getObdWitnessStatus" in native_api
assert "this.api.fullSnapshot()" not in obd_screen
assert "this.api.obdWitness()" in obd_screen
assert "CoalescedSnapshotWriter" in service
assert "obdSnapshotWriter.request()" in service
assert "cycleStartedAtMs" in service and "sessionStartedAtMs" in service and "sessionId" in service
assert "confirmedMapSnapshot" in k_writer
assert "readText(Charsets.UTF_8)" not in service.split("private fun persistObdLearning", 1)[1].split("private fun rotateObdLearningEpoch", 1)[0]

print("BLUE_HOT_PATH_CONTRACT=PASS")
