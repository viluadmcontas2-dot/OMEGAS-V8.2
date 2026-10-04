package com.omegas.prohub.properties

import com.omegas.prohub.autocal.EquivalenceLedger
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import java.util.Random

/**
 * Apoio das propriedades (Lote W): geradores com semente (sem dependência nova) e verificadores de JSON.
 * Tudo determinístico: a mesma semente produz exatamente a mesma sequência.
 */
object PropertySupport {
    val SEEDS = longArrayOf(1L, 2L, 3L, 5L, 8L, 13L, 21L, 34L, 55L, 89L, 144L, 233L)

    /** Percorre o JSON e falha em qualquer número não finito (o app nunca pode emitir NaN/Infinity). */
    fun assertFiniteJson(value: Any?, path: String = "$") {
        when (value) {
            is JSONObject -> value.keys().forEach { key -> assertFiniteJson(value.opt(key), "$path.$key") }
            is JSONArray -> for (i in 0 until value.length()) assertFiniteJson(value.opt(i), "$path[$i]")
            is Double -> assertTrue("número não finito em $path: $value", value.isFinite())
            is Float -> assertTrue("número não finito em $path: $value", value.isFinite())
        }
    }

    /** Condução plausível: trechos de RPM/MAP quase constantes, troca de combustível, corte e marcha lenta. */
    class DriveScript(seed: Long, private val clockStart: Long = 1_000L) {
        private val rnd = Random(seed)
        private var t = clockStart
        private var fuel = "GASOLINA"

        fun frames(count: Int): List<EquivalenceLedger.Frame> {
            val out = ArrayList<EquivalenceLedger.Frame>(count)
            var rpm = 2000.0
            var map = 0.55
            var remaining = 0
            while (out.size < count) {
                if (remaining <= 0) {
                    remaining = 3 + rnd.nextInt(14)
                    rpm = if (rnd.nextInt(6) == 0) 800.0 + rnd.nextInt(150) else 1200.0 + rnd.nextInt(3200)
                    map = 0.25 + rnd.nextDouble() * 0.9
                    when (rnd.nextInt(7)) {
                        0 -> fuel = "GASOLINA"
                        1, 2, 3 -> fuel = "GNV"
                        4 -> fuel = "CUTOFF"
                        5 -> fuel = "DESCONHECIDO"
                    }
                }
                remaining--
                val petrol = (map * 10.0 * (0.9 + rnd.nextDouble() * 0.2)).coerceAtLeast(0.2)
                val gas = if (fuel == "GNV") petrol * (0.95 + rnd.nextDouble() * 0.15) + 1.0 else 0.0
                val frame = EquivalenceLedger.Frame(
                    t, fuel,
                    rpm + rnd.nextGaussian() * 25.0,
                    (map + rnd.nextGaussian() * 0.006).coerceAtLeast(0.01),
                    if (fuel == "CUTOFF") 0.0 else petrol,
                    gas,
                )
                out += frame
                t += 80L + rnd.nextInt(400)
                if (rnd.nextInt(60) == 0) t += 5_000L // lacuna: abre outro episódio de condução
            }
            return out
        }
    }

    fun tempFile(prefix: String): java.io.File = java.nio.file.Files.createTempFile(prefix, ".json").toFile().also {
        it.delete()
        it.deleteOnExit()
    }
}
