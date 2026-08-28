package com.omegas.prohub.ecu

enum class AutoCalEvidenceLevel { E0, E1, E2, E3, E4, E5 }

enum class AutoCalSemanticKnownness { KNOWN, LIMITED, UNKNOWN }

data class AutoCalFieldEvidence(
    val field: AutoCalProtocol.Field,
    val evidence: AutoCalEvidenceLevel,
    val meaning: String,
    val knownness: AutoCalSemanticKnownness,
    val consumer: String,
    val source: String,
) {
    init {
        require(meaning.isNotBlank())
        require(consumer.isNotBlank())
        require(source.isNotBlank())
    }
}

/**
 * Owner 103 — registry versionado field/address/index/encoding/shape/unit/evidence/meaning/consumer.
 *
 * O registry não muda o decoder e não aumenta semântica. Campos cuja função física
 * não foi fechada permanecem LIMITED/UNKNOWN mesmo quando address/shape são provados.
 */
object AutoCalFieldEvidenceRegistry {
    const val REVISION = "AUTOCAL-FIELD-REGISTRY-V2-2026-08-18"

    private fun entry(
        field: AutoCalProtocol.Field,
        evidence: AutoCalEvidenceLevel,
        meaning: String,
        knownness: AutoCalSemanticKnownness,
        consumer: String,
        source: String,
    ) = AutoCalFieldEvidence(field, evidence, meaning, knownness, consumer, source)

    fun entries(): List<AutoCalFieldEvidence> = listOf(
        entry(AutoCalProtocol.MODULE_VERSION, AutoCalEvidenceLevel.E4, "module/version observed value", AutoCalSemanticKnownness.LIMITED, "AutoCal snapshot provenance", "Protocol map + observed snapshots"),
        entry(AutoCalProtocol.PETR_INJ_TBP, AutoCalEvidenceLevel.E5, "30-point petrol injection curve/reference axis", AutoCalSemanticKnownness.KNOWN, "Curve K / reference vector", "Protocol map section 5 + real 30-point corpus"),
        entry(AutoCalProtocol.MNFLD_PRESS_THD, AutoCalEvidenceLevel.E4, "18-band native MAP/pressure acquisition axis/threshold family", AutoCalSemanticKnownness.LIMITED, "AutoCal acquisition", "RE CP160 / protocol map"),
        entry(AutoCalProtocol.MUL_ACT, AutoCalEvidenceLevel.E5, "30-point Q14 Curva K factors", AutoCalSemanticKnownness.KNOWN, "Calibration Identity / Curva K / AutoMatch bracket", "Protocol map section 5 + writer/readback corpus"),
        entry(AutoCalProtocol.PETR_MNFLD_PRESS_RV, AutoCalEvidenceLevel.E4, "30-point petrol MAP reference vector", AutoCalSemanticKnownness.LIMITED, "AutoCal reference context", "102B + real 30-point corpus"),
        entry(AutoCalProtocol.GAS_MNFLD_PRESS_RV, AutoCalEvidenceLevel.E4, "30-point gas MAP reference vector", AutoCalSemanticKnownness.LIMITED, "AutoCal reference context", "102B + real 30-point corpus"),
        entry(AutoCalProtocol.AUTO_CAL_ENABLE, AutoCalEvidenceLevel.E5, "native AutoCal enable/disable flag", AutoCalSemanticKnownness.KNOWN, "NativeAutoCalMonitor", "Portmon frames + protocol map"),
        entry(AutoCalProtocol.NUM_BUF_UPD_PETR, AutoCalEvidenceLevel.E4, "18 petrol acquisition counters", AutoCalSemanticKnownness.KNOWN, "Native maturity/progression", "102B acquisition family"),
        entry(AutoCalProtocol.NUM_BUF_UPD_GAS, AutoCalEvidenceLevel.E4, "18 gas acquisition counters", AutoCalSemanticKnownness.KNOWN, "Native maturity/progression", "102B acquisition family"),
        entry(AutoCalProtocol.VECT_AUTOCAL_U8_1, AutoCalEvidenceLevel.E3, "indexed AutoCal raw parameter", AutoCalSemanticKnownness.UNKNOWN, "diagnostic only", "existing indexed descriptor; physical meaning not closed"),
        entry(AutoCalProtocol.MAX_AUTOMATCH, AutoCalEvidenceLevel.E4, "configured maximum AutoMatch count", AutoCalSemanticKnownness.LIMITED, "NativeAutoCalMonitor", "field registry / observed snapshots"),
        entry(AutoCalProtocol.PETR_INJ_TBUF_GAS_PREV, AutoCalEvidenceLevel.E4, "18 previous-gas native Tpet acquisition buffer", AutoCalSemanticKnownness.KNOWN, "AutoCal acquisition projection", "102B acquisition family"),
        entry(AutoCalProtocol.MNFLD_PRESS_BUF_GAS_PREV, AutoCalEvidenceLevel.E4, "18 previous-gas native MAP acquisition buffer", AutoCalSemanticKnownness.KNOWN, "AutoCal acquisition projection", "102B acquisition family"),
        entry(AutoCalProtocol.PETR_INJ_TBUF_GAS, AutoCalEvidenceLevel.E4, "18 current-gas native Tpet acquisition buffer", AutoCalSemanticKnownness.KNOWN, "AutoCal acquisition projection", "102B acquisition family"),
        entry(AutoCalProtocol.MNFLD_PRESS_BUF_GAS, AutoCalEvidenceLevel.E4, "18 current-gas native MAP acquisition buffer", AutoCalSemanticKnownness.KNOWN, "AutoCal acquisition projection", "102B acquisition family"),
        entry(AutoCalProtocol.PETR_INJ_TBUF, AutoCalEvidenceLevel.E4, "18 petrol native Tpet acquisition buffer", AutoCalSemanticKnownness.KNOWN, "AutoCal acquisition projection", "102B acquisition family"),
        entry(AutoCalProtocol.MNFLD_PRESS_BUF, AutoCalEvidenceLevel.E4, "18 petrol native MAP acquisition buffer", AutoCalSemanticKnownness.KNOWN, "AutoCal acquisition projection", "102B acquisition family"),
        entry(AutoCalProtocol.RAW_AUTOCAL_0167, AutoCalEvidenceLevel.E5, "raw/1024 dedicated AutoCal gate/config value; physical semantics UNKNOWN", AutoCalSemanticKnownness.UNKNOWN, "diagnostic only until new oracle", "ER-CP-161/162"),
        entry(AutoCalProtocol.CALIBRATION_VAL_1, AutoCalEvidenceLevel.E3, "10-byte calibration raw vector", AutoCalSemanticKnownness.UNKNOWN, "diagnostic only", "protocol map; physical semantics not closed"),
        entry(AutoCalProtocol.ACQUIRED_ZONES_PETROL, AutoCalEvidenceLevel.E4, "four petrol acquisition-zone states", AutoCalSemanticKnownness.KNOWN, "native progression", "protocol map / acquisition cycle evidence"),
        entry(AutoCalProtocol.ACQUIRED_ZONES_GAS, AutoCalEvidenceLevel.E4, "four gas acquisition-zone states", AutoCalSemanticKnownness.KNOWN, "native progression", "protocol map / acquisition cycle evidence"),
        entry(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED, AutoCalEvidenceLevel.E5, "native AutoMatch execution counter", AutoCalSemanticKnownness.KNOWN, "AutoMatch material event bracket", "protocol map / Portmon counter changes"),
        entry(AutoCalProtocol.MAX_RPM_FOR_AUTOCAL, AutoCalEvidenceLevel.E4, "configured maximum RPM field for AutoCal", AutoCalSemanticKnownness.LIMITED, "native AutoCal context", "protocol map; exact policy semantics limited"),
    )

    private val byIdentity: Map<String, AutoCalFieldEvidence> by lazy { entries().associateBy { it.field.identity } }

    fun forField(field: AutoCalProtocol.Field): AutoCalFieldEvidence? = byIdentity[field.identity]

    fun unregisteredReadOnlyFields(): List<AutoCalProtocol.Field> =
        AutoCalProtocol.READ_ONLY_FIELDS.filter { it.identity !in byIdentity }
}
