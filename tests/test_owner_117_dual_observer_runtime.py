import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OBSERVER = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalDualFuelMaturityObserver.kt"
KOTLINC = Path("/root/.sdkman/candidates/kotlin/current/bin/kotlinc")


class Owner117DualObserverRuntime(unittest.TestCase):
    def test_bootstrap_is_minimal_once_per_session_and_dual_fuel_is_read_only(self):
        with tempfile.TemporaryDirectory(prefix="owner117-observer-") as raw:
            tmp = Path(raw)
            (tmp / "Observer.kt").write_text(OBSERVER.read_text("utf-8"), encoding="utf-8")
            (tmp / "EventStub.kt").write_text(textwrap.dedent('''
                package com.omegas.prohub.learning
                object NativeAutoCalEventCorrelator { enum class SourceFuel { PETROL, CNG } }
            '''), encoding="utf-8")
            (tmp / "Tracker.kt").write_text(textwrap.dedent('''
                package com.omegas.prohub.autocal
                object NativeAutoCalProgression { const val ACQUISITION_BANDS = 18 }
                class NativeAutoCalMaturityTracker {
                    data class Transition(
                        val bandIndex:Int,val zone:Int,val previousCounter:Int,val counter:Int,
                        val threshold:Int,val previousObservedAtElapsedMs:Long,val observedAtElapsedMs:Long,
                    )
                    private var previousCounters:IntArray?=null
                    private var previousAt=0L
                    fun reset(){ previousCounters=null; previousAt=0L }
                    fun baseline(counters:IntArray, observedAtElapsedMs:Long){ require(counters.size==18); previousCounters=counters.copyOf(); previousAt=observedAtElapsedMs }
                    fun observe(counters:IntArray, gasLowThreshold:Int?, gasNormalThreshold:Int?, enabled:Boolean, observedAtElapsedMs:Long):List<Transition>{
                        if(counters.size!=18) return emptyList()
                        val prev=previousCounters; val priorAt=previousAt
                        previousCounters=counters.copyOf(); previousAt=observedAtElapsedMs
                        if(prev==null || !enabled) return emptyList()
                        return buildList {
                            repeat(18){ i ->
                                val threshold=if(i<=5) gasLowThreshold else gasNormalThreshold
                                if(threshold!=null && threshold>0 && prev[i]<threshold && counters[i]>=threshold){
                                    val zone=if(i<=5)0 else if(i<=9)1 else if(i<=13)2 else 3
                                    add(Transition(i,zone,prev[i],counters[i],threshold,priorAt,observedAtElapsedMs))
                                }
                            }
                        }
                    }
                }
            '''), encoding="utf-8")
            (tmp / "Ecu.kt").write_text(textwrap.dedent('''
                package com.omegas.prohub.ecu
                enum class Mp48WorkClass { READ_ONLY }
                data class Reply(val ok:Boolean,val status:Int,val payload:ByteArray,val error:String="")
                open class Mp48SerialScheduler {
                    val fields=mutableListOf<String>()
                    var petrol=IntArray(18)
                    var cng=IntArray(18)
                    open fun transaction(request:ByteArray,reason:String,timeoutMs:Int,purgeBefore:Boolean,expectedSessionId:Long,workClass:Mp48WorkClass):Reply {
                        check(workClass==Mp48WorkClass.READ_ONLY)
                        val key=request.decodeToString(); fields+=key
                        val values = when(key) {
                            "ENABLE" -> intArrayOf(1)
                            "PETROL_LOW" -> intArrayOf(2)
                            "CAL" -> intArrayOf(0,0,3,0,0,2,0,0,3)
                            "PETROL" -> petrol
                            "CNG" -> cng
                            else -> error("unexpected field $key")
                        }
                        val bytes=ByteArray(values.size*2)
                        values.forEachIndexed { i,v -> bytes[i*2]=(v and 255).toByte(); bytes[i*2+1]=((v ushr 8) and 255).toByte() }
                        return Reply(true,0,bytes)
                    }
                }
                object AutoCalProtocol {
                    data class Field(val key:String)
                    data class Decoded(val rawValues:IntArray)
                    val AUTO_CAL_ENABLE=Field("ENABLE")
                    val VECT_AUTOCAL_U8_1=Field("PETROL_LOW")
                    val CALIBRATION_VAL_1=Field("CAL")
                    val NUM_BUF_UPD_PETR=Field("PETROL")
                    val NUM_BUF_UPD_GAS=Field("CNG")
                    fun read(field:Field)=field.key.encodeToByteArray()
                    fun decode(field:Field,status:Int,payload:ByteArray):Decoded = Decoded(IntArray(payload.size/2){ i ->
                        (payload[i*2].toInt() and 255) or ((payload[i*2+1].toInt() and 255) shl 8)
                    })
                }
            '''), encoding="utf-8")
            (tmp / "Main.kt").write_text(textwrap.dedent('''
                import com.omegas.prohub.autocal.*
                import com.omegas.prohub.ecu.*
                import com.omegas.prohub.learning.NativeAutoCalEventCorrelator
                fun main(){
                    val serial=Mp48SerialScheduler(); var now=1000L
                    val observer=NativeAutoCalDualFuelMaturityObserver(serial){now}
                    check(!observer.bootstrapState().attempted && !observer.bootstrapState().complete)
                    check(observer.ensureBootstrap(55))
                    check(observer.bootstrapState().attempted && observer.bootstrapState().complete)
                    check(observer.readiness().petrol && observer.readiness().cng)
                    check(serial.fields==listOf("ENABLE","PETROL_LOW","CAL","PETROL","CNG"))
                    val afterFirstBootstrap=serial.fields.toList()
                    check(observer.ensureBootstrap(55))
                    check(serial.fields==afterFirstBootstrap)

                    now=1300L; serial.petrol[0]=2; serial.cng[6]=3
                    val result=observer.observe(55)
                    check(result.petrolRead && result.cngRead)
                    check(serial.fields.takeLast(2)==listOf("PETROL","CNG"))
                    check(result.events.size==2)
                    check(result.events.any{it.sourceFuel==NativeAutoCalEventCorrelator.SourceFuel.PETROL && it.transition.bandIndex==0})
                    check(result.events.any{it.sourceFuel==NativeAutoCalEventCorrelator.SourceFuel.CNG && it.transition.bandIndex==6})

                    observer.reset()
                    check(!observer.bootstrapState().attempted && !observer.bootstrapState().complete)
                    check(!observer.readiness().petrol && !observer.readiness().cng)
                    serial.petrol=IntArray(18); serial.cng=IntArray(18); now=2000L
                    check(observer.ensureBootstrap(77))
                    check(serial.fields.takeLast(5)==listOf("ENABLE","PETROL_LOW","CAL","PETROL","CNG"))
                    println("OWNER_117_DUAL_OBSERVER_BOOTSTRAP_RUNTIME=PASS")
                }
            '''), encoding="utf-8")
            jar = tmp / "observer.jar"
            subprocess.run([str(KOTLINC), str(tmp / "Observer.kt"), str(tmp / "EventStub.kt"), str(tmp / "Tracker.kt"), str(tmp / "Ecu.kt"), str(tmp / "Main.kt"), "-include-runtime", "-d", str(jar)], check=True, capture_output=True, text=True, timeout=30)
            result = subprocess.run(["java", "-jar", str(jar)], check=True, capture_output=True, text=True, timeout=10)
            self.assertIn("OWNER_117_DUAL_OBSERVER_BOOTSTRAP_RUNTIME=PASS", result.stdout)


if __name__ == "__main__":
    unittest.main()
