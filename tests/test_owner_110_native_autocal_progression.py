import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalProgression.kt"


class Owner110NativeAutoCalProgression(unittest.TestCase):
    def test_18_band_progression_stays_separate_from_30_point_references(self):
        source = MODEL.read_text("utf-8")
        self.assertIn("ACQUISITION_BANDS = 18", source)
        self.assertIn("REFERENCE_POINTS = 30", source)
        self.assertIn("UNPOSITIONED", source)
        self.assertNotIn("copyOf(18)", source)

        with tempfile.TemporaryDirectory(prefix="owner110-progression-") as tmp:
            tmp = Path(tmp)
            main = tmp / "Main.kt"
            main.write_text(
                textwrap.dedent(
                    """
                    import com.omegas.prohub.autocal.NativeAutoCalProgression
                    import com.omegas.prohub.autocal.NativeAutoCalProgression.CoordinateState
                    import com.omegas.prohub.autocal.NativeAutoCalProgression.Fuel
                    import com.omegas.prohub.autocal.NativeAutoCalProgression.ShapeState
                    import com.omegas.prohub.autocal.NativeAutoCalProgression.ZoneState

                    fun main() {
                        val petrolCounters = IntArray(18) { it }
                        val petrolTimes = IntArray(18) { 1000 + it }
                        val petrolMaps = IntArray(18) { 2000 + it }
                        val gasCounters = IntArray(18) { it + 10 }
                        val gasTimes = IntArray(18) { 3000 + it }
                        val gasMaps = IntArray(18) { 4000 + it }
                        val previousTimes = IntArray(18) { 5000 + it }
                        val previousMaps = IntArray(18) { 6000 + it }

                        val snapshot = NativeAutoCalProgression.evaluate(
                            petrolCounters = petrolCounters,
                            petrolTimes = petrolTimes,
                            petrolMaps = petrolMaps,
                            petrolZoneFlags = intArrayOf(1, 1, 0, 0),
                            gasCounters = gasCounters,
                            gasTimes = gasTimes,
                            gasMaps = gasMaps,
                            gasZoneFlags = intArrayOf(1, 0, 2, 0),
                            previousGasTimes = previousTimes,
                            previousGasMaps = previousMaps,
                            reference30 = mapOf(
                                "MUL_ACT" to IntArray(30) { 16384 },
                                "PETR_INJ_TBP" to IntArray(30) { it },
                                "BROKEN_29" to IntArray(29) { it },
                            ),
                        )

                        check(snapshot.acquisition18.size == 3)
                        val petrol = snapshot.acquisition18.single { it.fuel == Fuel.PETROL }
                        val gas = snapshot.acquisition18.single { it.fuel == Fuel.GAS }
                        check(petrol.shapeState == ShapeState.KNOWN)
                        check(petrol.bands.size == 18)
                        check(petrol.bands[0].zone == 0)
                        check(petrol.bands[6].zone == 1)
                        check(petrol.bands[10].zone == 2)
                        check(petrol.bands[14].zone == 3)
                        check(petrol.zones.map { it.state } == listOf(
                            ZoneState.ACQUIRED,
                            ZoneState.ACQUIRED,
                            ZoneState.NOT_ACQUIRED,
                            ZoneState.NOT_ACQUIRED,
                        ))
                        check(gas.zones[2].state == ZoneState.RAW_OTHER)
                        check(gas.zones[2].rawFlag == 2)

                        val mul = snapshot.reference30.single { it.key == "MUL_ACT" }
                        val broken = snapshot.reference30.single { it.key == "BROKEN_29" }
                        check(mul.shapeState == ShapeState.KNOWN)
                        check(mul.rawValues!!.size == 30)
                        check(broken.shapeState == ShapeState.UNKNOWN)
                        check(broken.rawValues == null)

                        val badShape = NativeAutoCalProgression.evaluate(
                            petrolCounters = IntArray(17),
                            petrolTimes = IntArray(18),
                            petrolMaps = IntArray(18),
                            petrolZoneFlags = intArrayOf(1, 0, 0, 0),
                            gasCounters = null,
                            gasTimes = null,
                            gasMaps = null,
                            gasZoneFlags = null,
                            previousGasTimes = null,
                            previousGasMaps = null,
                        )
                        val badPetrol = badShape.acquisition18.single { it.fuel == Fuel.PETROL }
                        check(badPetrol.shapeState == ShapeState.UNKNOWN)
                        check(badPetrol.bands.all { it.counter == null })

                        val noCoordinates = NativeAutoCalProgression.evaluate(
                            petrolCounters = IntArray(18),
                            petrolTimes = null,
                            petrolMaps = IntArray(18),
                            petrolZoneFlags = intArrayOf(0, 0, 0, 0),
                            gasCounters = null,
                            gasTimes = null,
                            gasMaps = null,
                            gasZoneFlags = null,
                            previousGasTimes = null,
                            previousGasMaps = null,
                        )
                        val unpositioned = noCoordinates.acquisition18.single { it.fuel == Fuel.PETROL }
                        check(unpositioned.bands.all { it.coordinateState == CoordinateState.UNPOSITIONED })

                        println("OWNER_110_NATIVE_PROGRESS=PASS")
                    }
                    """
                ),
                encoding="utf-8",
            )
            jar = tmp / "owner110.jar"
            subprocess.run(
                ["kotlinc", str(MODEL), str(main), "-include-runtime", "-d", str(jar)],
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
            self.assertIn("OWNER_110_NATIVE_PROGRESS=PASS", result.stdout)


if __name__ == "__main__":
    unittest.main()
