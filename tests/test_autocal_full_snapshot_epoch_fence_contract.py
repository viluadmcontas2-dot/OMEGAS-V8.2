from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MONITOR = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt"

monitor = MONITOR.read_text("utf-8")
section = monitor.split("private fun readFullSnapshot", 1)[1]
section = section.split("private fun correlationStateJson", 1)[0]

# A scientific/full snapshot can start immediately after a native band matures.
# The original Lognovo capture proves that this is also the boundary where the
# ECU can roll CURRENT -> PREV, update MUL_ACT and increment AutoMatch count.
# Therefore the full multi-field read must be fenced by the same native epoch
# before any snapshot is built/published.
closing_probe = "val afterEpoch = probe(expectedSessionId)"
guard = "NativeAutoCalEpochGuard.sameEpoch(probe, afterEpoch)"

assert closing_probe in section, "full AutoCal snapshot has no closing native-epoch probe"
assert guard in section, "full AutoCal snapshot can publish fields from two native AutoMatch epochs"

guard_index = section.index(guard)
build_index = section.index("AutoCalSnapshotBuilder.build")
publish_index = section.index("latestSnapshot = decorated")
assert guard_index < build_index < publish_index, (
    "epoch fence must reject a torn full read before snapshot construction/publication"
)

print("AUTOCAL_FULL_SNAPSHOT_EPOCH_FENCE=PASS")
