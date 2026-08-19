import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROGRESSION = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalProgression.kt"
MEMORY = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalPetrolReferenceMemory.kt"


class Owner111PetrolReferencePreservation(unittest.TestCase):
    def test_petrol_reference_survives_gas_reset_and_reacquire(self):
        with tempfile.TemporaryDirectory(prefix="owner111-petrol-reference-") as tmp:
            tmp = Path(tmp)
            main = tmp / "Main.kt"
            main.write_text(textwrap.dedent(
                """
                import com.omegas.prohub.autocal.NativeAutoCalPetrolReferenceMemory
                import com.omegas.prohub.autocal.NativeAutoCalProgression

                fun snapshot(
                    petrolCounters: IntArray?,
                    petrolTimes: IntArray?,
                    petrolMaps: IntArray?,
                    petrolZones: IntArray?,
                    gasCounters: IntArray?,
                    gasZones: IntArray?,
                ) = NativeAutoCalProgression.evaluate(
                    petrolCounters = petrolCounters,
                    petrolTimes = petrolTimes,
                    petrolMaps = petrolMaps,
                    petrolZoneFlags = petrolZones,
                    gasCounters = gasCounters,
                    gasTimes = gasCounters?.let { IntArray(18) { 3000 + it } },
                    gasMaps = gasCounters?.let { IntArray(18) { 4000 + it } },
                    gasZoneFlags = gasZones,
                    previousGasTimes = null,
                    previousGasMaps = null,
                )

                fun main() {
                    val memory = NativeAutoCalPetrolReferenceMemory()
                    val maturePetrol = snapshot(
                        petrolCounters = IntArray(18) { 5 },
                        petrolTimes = IntArray(18) { 1000 + it },
                        petrolMaps = IntArray(18) { 2000 + it },
                        petrolZones = intArrayOf(1, 1, 1, 1),
                        gasCounters = IntArray(18) { 2 },
                        gasZones = intArrayOf(0, 0, 0, 0),
                    )
                    val first = memory.observe(maturePetrol)!!
                    check(first.revision == 1L)
                    check(first.bands[7].petrolTimeRaw == 1007)

                    val gasAcquire = snapshot(
                        petrolCounters = IntArray(18) { 5 },
                        petrolTimes = IntArray(18) { 1000 + it },
                        petrolMaps = IntArray(18) { 2000 + it },
                        petrolZones = intArrayOf(1, 1, 1, 1),
                        gasCounters = IntArray(18) { 7 },
                        gasZones = intArrayOf(1, 1, 1, 1),
                    )
                    check(memory.observe(gasAcquire)!!.revision == 1L)

                    // Reset GNV: somente a geração gasosa zera.
                    val gasReset = snapshot(
                        petrolCounters = IntArray(18) { 5 },
                        petrolTimes = IntArray(18) { 1000 + it },
                        petrolMaps = IntArray(18) { 2000 + it },
                        petrolZones = intArrayOf(1, 1, 1, 1),
                        gasCounters = IntArray(18) { 0 },
                        gasZones = intArrayOf(0, 0, 0, 0),
                    )
                    val afterReset = memory.observe(gasReset)!!
                    check(afterReset.revision == 1L)
                    check(afterReset.bands[7].petrolTimeRaw == 1007)

                    // Durante reacquire, uma fotografia gasolina incompleta não pode apagar a histórica.
                    val incompletePetrolDuringReacquire = snapshot(
                        petrolCounters = IntArray(18) { 5 },
                        petrolTimes = null,
                        petrolMaps = IntArray(18) { 2000 + it },
                        petrolZones = intArrayOf(1, 1, 1, 1),
                        gasCounters = IntArray(18) { 1 },
                        gasZones = intArrayOf(1, 0, 0, 0),
                    )
                    val duringReacquire = memory.observe(incompletePetrolDuringReacquire)!!
                    check(duringReacquire.revision == 1L)
                    check(duringReacquire.bands[7].petrolTimeRaw == 1007)

                    // Só uma nova gasolina completa substitui a referência.
                    val newPetrol = snapshot(
                        petrolCounters = IntArray(18) { 6 },
                        petrolTimes = IntArray(18) { 1100 + it },
                        petrolMaps = IntArray(18) { 2100 + it },
                        petrolZones = intArrayOf(1, 1, 1, 1),
                        gasCounters = IntArray(18) { 4 },
                        gasZones = intArrayOf(1, 1, 0, 0),
                    )
                    val replaced = memory.observe(newPetrol)!!
                    check(replaced.revision == 2L)
                    check(replaced.bands[7].petrolTimeRaw == 1107)

                    println("OWNER_111_PETROL_REFERENCE=PASS")
                }
                """
            ), encoding="utf-8")
            jar = tmp / "owner111.jar"
            compiler = "/root/.sdkman/candidates/kotlin/current/bin/kotlinc"
            subprocess.run(
                [compiler, str(PROGRESSION), str(MEMORY), str(main), "-include-runtime", "-d", str(jar)],
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
            self.assertIn("OWNER_111_PETROL_REFERENCE=PASS", result.stdout)


if __name__ == "__main__":
    unittest.main()
