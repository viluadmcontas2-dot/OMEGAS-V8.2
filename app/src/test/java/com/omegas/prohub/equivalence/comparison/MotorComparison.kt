package com.omegas.prohub.equivalence.comparison

import com.omegas.prohub.autocal.*
import com.omegas.prohub.equivalence.*
import org.json.JSONObject

/** Mesma aquisição e mesmos pares entregues aos dois motores; nenhuma escrita na ECU. */
object MotorComparison {
    fun platina(snapshot: JSONObject, ledger: EquivalenceLedger, runtime: EquivalenceRuntime): PlatinaRefinedEngine.Result {
        val acquisition = AutoCalAcquisition.fromSnapshot(snapshot)
        val reference = runtime.references.current() ?: runtime.references.provisional(acquisition)
        val ecuRef = reference?.let { EvidencePairs.cleanReference(it.points.map { p -> p.mapBar to p.petrolMs }) } ?: emptyList()
        val pairs = EvidencePairs.build(ledger.petrolObservations(), ledger.gasObservations(), ecuRef)
            .filter { it.rpm >= EquivalenceLedger.DRIVING_MIN_RPM && it.petrolRefMs >= AutoMatchRefinedEngine.TELEMETRY_MIN_MS }
        val curve = EquivalenceEngine.curveFromSnapshot(snapshot)!!
        val n = NativeBands.fromSnapshot(snapshot)
        return PlatinaRefinedEngine.refine(PlatinaRefinedEngine.Input(
            curve.first, curve.second, n?.petrolTimeRaw, n?.petrolMapRaw, n?.petrolCounts,
            n?.gasTimeRaw, n?.gasMapRaw, n?.gasCounts, pairs.map { it.petrolRefMs to it.gasPetrolMs },
        ))
    }
}
