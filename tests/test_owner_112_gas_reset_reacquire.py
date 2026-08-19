import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROGRESSION = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalProgression.kt"
PETROL_MEMORY = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalPetrolReferenceMemory.kt"
TRACKER = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalGasReacquireTracker.kt"


class Owner112GasResetReacquire(unittest.TestCase):
    def test_reset_reacquire_is_explicit_and_gas_only(self):
        with tempfile.TemporaryDirectory(prefix="owner112-gas-reset-") as tmp:
            tmp = Path(tmp)
            main = tmp / "Main.kt"
            main.write_text(textwrap.dedent(
                """
                import com.omegas.prohub.autocal.NativeAutoCalGasReacquireTracker
                import com.omegas.prohub.autocal.NativeAutoCalPetrolReferenceMemory
                import com.omegas.prohub.autocal.NativeAutoCalProgression

                fun snap(gasCounters: IntArray?, gasZones: IntArray?, validPetrol: Boolean = true) =
                    NativeAutoCalProgression.evaluate(
                        petrolCounters = IntArray(18) { 5 },
                        petrolTimes = if (validPetrol) IntArray(18) { 1000 + it } else null,
                        petrolMaps = IntArray(18) { 2000 + it },
                        petrolZoneFlags = intArrayOf(1, 1, 1, 1),
                        gasCounters = gasCounters,
                        gasTimes = gasCounters?.let { IntArray(18) { 3000 + it } },
                        gasMaps = gasCounters?.let { IntArray(18) { 4000 + it } },
                        gasZoneFlags = gasZones,
                        previousGasTimes = null,
                        previousGasMaps = null,
                    )

                fun main() {
                    val tracker = NativeAutoCalGasReacquireTracker()
                    val petrol = NativeAutoCalPetrolReferenceMemory()

                    val active = snap(IntArray(18) { 8 }, intArrayOf(1, 1, 1, 1))
                    val reference = petrol.observe(active)!!
                    check(reference.revision == 1L)
                    check(tracker.observe(active).isEmpty())

                    val reset = snap(IntArray(18) { 0 }, intArrayOf(0, 0, 0, 0))
                    val resetEvents = tracker.observe(reset)
                    check(resetEvents.size == 1)
                    check(resetEvents.single().type == NativeAutoCalGasReacquireTracker.EventType.GAS_RESET)
                    check(resetEvents.single().invalidationScope == "GNV_ACQUISITION_ONLY")
                    check(!resetEvents.single().reconnect)
                    check(petrol.observe(reset)!!.revision == 1L)

                    check(tracker.observe(reset).isEmpty())

                    val unknown = snap(null, null, validPetrol = false)
                    check(tracker.observe(unknown).isEmpty())
                    check(petrol.observe(unknown)!!.revision == 1L)

                    val reacquire = snap(IntArray(18) { if (it < 3) 1 else 0 }, intArrayOf(1, 0, 0, 0))
                    val reacquireEvents = tracker.observe(reacquire)
                    check(reacquireEvents.size == 1)
                    check(reacquireEvents.single().type == NativeAutoCalGasReacquireTracker.EventType.GAS_REACQUIRE_STARTED)
                    check(reacquireEvents.single().invalidationScope == "GNV_ACQUISITION_ONLY")
                    check(!reacquireEvents.single().reconnect)
                    check(petrol.observe(reacquire)!!.revision == 1L)

                    println("OWNER_112_GAS_RESET_REACQUIRE=PASS")
                }
                """
            ), encoding="utf-8")
            jar = tmp / "owner112.jar"
            compiler = "/root/.sdkman/candidates/kotlin/current/bin/kotlinc"
            subprocess.run(
                [compiler, str(PROGRESSION), str(PETROL_MEMORY), str(TRACKER), str(main), "-include-runtime", "-d", str(jar)],
                check=True,
                capture_output=True,
                text=True,
                timeout=30,
            )
            result = subprocess.run(
                ["java", "-jar", str(jar)],
                check=True,
                capture_output=True,
                text=True,
                timeout=10,
            )
            self.assertIn("OWNER_112_GAS_RESET_REACQUIRE=PASS", result.stdout)


if __name__ == "__main__":
    unittest.main()
