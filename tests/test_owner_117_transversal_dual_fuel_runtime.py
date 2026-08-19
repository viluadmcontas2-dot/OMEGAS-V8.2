import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORRELATOR = ROOT / "app/src/main/java/com/omegas/prohub/learning/NativeAutoCalEventCorrelator.kt"
OBSERVER = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalDualFuelMaturityObserver.kt"
MONITOR = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt"
PROJECTOR = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMaturityEventProjector.kt"

KOTLINC = "kotlinc"


class Owner117TransversalDualFuelRuntime(unittest.TestCase):
    def test_correlator_executes_petrol_cng_and_fail_closed_paths(self):
        source = CORRELATOR.read_text("utf-8")
        self.assertNotIn("compatible.size >= 3", source)
        self.assertNotIn("confidence >= 0.90", source)
        with tempfile.TemporaryDirectory(prefix="owner117-correlator-") as raw:
            tmp = Path(raw)
            (tmp / "NativeAutoCalEventCorrelator.kt").write_text(source, encoding="utf-8")
            (tmp / "Stubs.kt").write_text(textwrap.dedent('''
                package com.omegas.prohub.learning
                class NativeAnchorTelemetryWindow {
                    data class Frame(
                        val sequence: Long, val elapsedMs: Long, val rpm: Int,
                        val mapBar: Double, val petrolMs: Double, val fuel: String,
                        val sessionId: Long = 0L, val gasMsDiagnostic: Double? = null,
                        val plausible: Boolean = true,
                    )
                }
                data class LearningTolerancePolicy(
                    val breakingGapMs: Long = 900L,
                    val petrolCenterMinimumMs: Double = 0.15,
                    val petrolCenterPercent: Double = 6.0,
                    val mapCenterBar: Double = 0.020,
                    val rpmOscillationMinimum: Double = 40.0,
                    val rpmOscillationPercent: Double = 1.5,
                ) { fun normalized() = this }
            '''), encoding="utf-8")
            (tmp / "Main.kt").write_text(textwrap.dedent('''
                import com.omegas.prohub.learning.*
                fun frame(seq:Long,t:Long,rpm:Int,fuel:String,pet:Double=4.50,map:Double=0.45) =
                    NativeAnchorTelemetryWindow.Frame(seq,t,rpm,map,pet,fuel,7L,if(fuel=="GNV")7.0 else null,true)
                fun main() {
                    val p = LearningTolerancePolicy()
                    val petrol = listOf(frame(1,1000,850,"PETROL"),frame(2,1250,852,"PETROL"),frame(3,1500,851,"PETROL"))
                    val pr = NativeAutoCalEventCorrelator.correlate(petrol,NativeAutoCalEventCorrelator.SourceFuel.PETROL,4.5,0.45,1500,900,1500,p,7)
                    check(pr.state=="CORRELATED" && pr.rpm!=null && pr.overlapKey=="7:1-3:PETROL" && pr.canCloseWindowEarly)
                    val gas = listOf(frame(10,2000,900,"GNV"),frame(11,2250,902,"GNV"),frame(12,2500,899,"GNV"))
                    val gr = NativeAutoCalEventCorrelator.correlate(gas,NativeAutoCalEventCorrelator.SourceFuel.CNG,4.5,0.45,2500,1900,2500,p,7)
                    check(gr.state=="CORRELATED" && gr.sourceFuel==NativeAutoCalEventCorrelator.SourceFuel.CNG && gr.rpm!=null)
                    val empty = NativeAutoCalEventCorrelator.correlate(emptyList(),NativeAutoCalEventCorrelator.SourceFuel.CNG,4.5,0.45,2500,1900,2500,p,7)
                    check(empty.state=="INCONCLUSIVE" && empty.rpm==null && empty.confidence==0.0)
                    val stale = NativeAutoCalEventCorrelator.correlate(listOf(frame(20,1000,900,"GNV"),frame(21,1100,901,"GNV")),NativeAutoCalEventCorrelator.SourceFuel.CNG,4.5,0.45,3000,900,3000,p,7)
                    check(stale.state=="INCONCLUSIVE" && stale.reason=="STALE_WINDOW")
                    val mixed = gas + frame(13,2550,901,"GNV",5.4,0.55)
                    val mr = NativeAutoCalEventCorrelator.correlate(mixed,NativeAutoCalEventCorrelator.SourceFuel.CNG,4.5,0.45,2550,1900,2550,p,7)
                    check(mr.state=="CORRELATED" && !mr.canCloseWindowEarly && mr.matchedFrames==3)
                    println("OWNER_117_CORRELATOR_RUNTIME=PASS")
                }
            '''), encoding="utf-8")
            jar = tmp / "correlator.jar"
            subprocess.run([KOTLINC, str(tmp / "NativeAutoCalEventCorrelator.kt"), str(tmp / "Stubs.kt"), str(tmp / "Main.kt"), "-include-runtime", "-d", str(jar)], check=True, capture_output=True, text=True, timeout=30)
            result = subprocess.run(["java", "-jar", str(jar)], check=True, capture_output=True, text=True, timeout=10)
            self.assertIn("OWNER_117_CORRELATOR_RUNTIME=PASS", result.stdout)

    def test_production_call_path_consumes_dual_fuel_observer_and_projector(self):
        monitor = MONITOR.read_text("utf-8")
        observer = OBSERVER.read_text("utf-8")
        projector = PROJECTOR.read_text("utf-8")
        self.assertIn("NativeAutoCalDualFuelMaturityObserver", monitor)
        self.assertIn("dualMaturityObserver.observe(currentSession)", monitor)
        self.assertIn("NativeAutoCalMaturityEventProjector.project(", monitor)
        self.assertIn("NUM_BUF_UPD_PETR", observer)
        self.assertIn("NUM_BUF_UPD_GAS", observer)
        self.assertIn("Mp48WorkClass.READ_ONLY", observer)
        self.assertNotIn("NativeAutoCalAnchorCorrelator", monitor)
        self.assertNotIn("ScheduledExecutor", monitor + observer + projector)
        self.assertNotIn("Executors.", monitor + observer + projector)
        self.assertNotIn("MANUAL_WRITE", monitor + observer + projector)
        self.assertIn('SourceFuel.PETROL -> "GASOLINA"', projector)
        self.assertIn('SourceFuel.CNG -> "GNV"', projector)
        self.assertIn("overlapKey", projector)
        self.assertIn("rpmConfidence", projector)
        self.assertIn("appAutomaticWrite", projector)


if __name__ == "__main__":
    unittest.main()
