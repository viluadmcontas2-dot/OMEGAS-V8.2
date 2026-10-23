package com.omegas.prohub.carinfo

/**
 * Junta as fontes do painel. O DashBoardInfo (tx 6) tem PRIORIDADE: é o que provamos bater com o painel do carro (campo,
 * 2026-10-21: autonomia 35 km e consumo médio 11,7). Um valor de outra transação (tx 17, propriedades) só preenche o campo
 * quando o DashBoardInfo está nulo para ele, e a origem fica registrada ("tx17"). Mapeamento de propId e unidades: INCERTO
 * (ids no estilo VHAL do Android Automotive; o contrato não traz a tabela de ids do Peugeot).
 */
class CarInfoAggregator {
    companion object {
        const val SRC_DASH = "tx6"
        const val SRC_PROPS = "tx17"

        /** propId → (campo do painel, fator de unidade). Estilo VHAL: PERF_VEHICLE_SPEED em m/s, ODOMETER em km, RPM, temperatura. INCERTO. */
        val PROP_MAP: Map<Int, Pair<String, Double>> = mapOf(
            0x11600207 to ("speed" to 3.6),
            0x11600204 to ("odo" to 1.0),
            0x11600305 to ("rotationRate" to 1.0),
            0x11600301 to ("coolantTemp" to 1.0),
        )
    }

    private var dash: CarInfoSample? = null
    private val alt = LinkedHashMap<String, Number>()

    @Synchronized fun onDashboard(sample: CarInfoSample): CarInfoSample {
        dash = sample
        return mergedLocked()!!
    }

    /** Devolve o painel já fundido quando o lote trouxe algum campo conhecido; senão null (nada mudou para a tela). */
    @Synchronized fun onProps(batch: CarPropBatch): CarInfoSample? {
        var any = false
        for (p in batch.props) {
            val (field, factor) = PROP_MAP[p.propId] ?: continue
            val n = p.number ?: continue
            alt[field] = n.toDouble() * factor
            any = true
        }
        return if (any) mergedLocked() else null
    }

    @Synchronized fun reset() { dash = null; alt.clear() }

    @Synchronized fun merged(): CarInfoSample? = mergedLocked()

    private fun mergedLocked(): CarInfoSample? {
        var out = dash ?: if (alt.isEmpty()) return null else CarInfoSample()
        for ((field, value) in alt) {
            if (out.valueOf(field) == null) out = out.withField(field, value, SRC_PROPS)
        }
        return out
    }
}
