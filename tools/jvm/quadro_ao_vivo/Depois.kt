import com.omegas.prohub.telemetry.TelemetryStateStore
import com.omegas.prohub.runtime.RuntimeSnapshotBus
import org.json.JSONObject
fun frame(i: Int): JSONObject {
    val live = JSONObject().put("captured_elapsed_ms", 1000L + i).put("rpm", 1500 + i % 400).put("petrol_ms", 4.0 + (i % 7) * 0.1).put("load_bar", 0.45 + (i % 5) * 0.01).put("fuel", "GNV")
    for (k in 0 until 30) live.put("campo_$k", i * 0.5 + k)
    live.put("sample", JSONObject().put("state", "FORMING").put("frame_count", 7).put("quality", 0.82))
    return JSONObject().put("event", "telemetry").put("session_id", 1L).put("live", live).put("runtime", JSONObject().put("link", "ONLINE"))
}
fun main() {
    val store = TelemetryStateStore(); val bus = RuntimeSnapshotBus(); store.beginSession(1L)
    var sink = 0L
    fun passo(i: Int) {
        store.updateFromEngineEvent(frame(i))
        run { val root = store.liveObject(); root.put("ok", true).put("telemetryAgeMs", root.optLong("ageMs", -1L)); val rev = bus.publishPresent(root); sink += JSONObject().put("ok", true).put("revision", rev).put("data", root).toString().length.toLong() }
    }
    repeat(20000) { passo(it) }
    val t0 = System.nanoTime(); val n = 100000
    repeat(n) { passo(it + 20000) }
    val us = (System.nanoTime() - t0) / 1000.0 / n
    println(String.format("%s: %.2f us por quadro (sink %d)", "depois", us, sink))
}
