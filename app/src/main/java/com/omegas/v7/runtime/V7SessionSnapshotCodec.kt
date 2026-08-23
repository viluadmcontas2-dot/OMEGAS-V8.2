package com.omegas.v7.runtime

import com.omegas.prohub.physics.CorrectionMechanism
import com.omegas.prohub.physics.EffectDirection
import com.omegas.prohub.physics.MagnitudeAuthority

/** Snapshot textual determinístico, sem dependência de Android ou bibliotecas externas. */
object V7SessionSnapshotCodec {
    private const val SCHEMA = "OMEGAS_V7_SESSION_7"
    private const val LIST_SEPARATOR = '\u001F'
    private val ACCEPTED_SCHEMAS = setOf(
        SCHEMA,
        "OMEGAS_V7_SESSION_6",
        "OMEGAS_V7_SESSION_5",
        "OMEGAS_V7_SESSION_4",
        "OMEGAS_V7_SESSION_3",
        "OMEGAS_V7_SESSION_2",
    )

    fun encode(state: V7SessionState): String = buildString {
        line("schema", SCHEMA)
        line("session", escape(state.sessionId))
        line("revision", "${state.calibration.revision.curveK},${state.calibration.revision.mapK}")
        line("curve", state.calibration.curveK.joinToString(","))
        line("mapShape", "${CalibrationShapeV7.MAP_K_STORAGE_ROWS},${CalibrationShapeV7.MAP_K_COLUMNS}")
        line("lastWriteMessage", escape(state.lastWriteMessage))
        state.calibration.mapK.forEachIndexed { row, values -> line("map.$row", values.joinToString(",")) }
        state.petrolEvidence.forEachIndexed { index, evidence -> line("petrol.$index", encodeEvidence(evidence)) }
        state.cngEvidenceByRevision.toSortedMap(compareBy<CalibrationRevisionV7> { it.curveK }.thenBy { it.mapK })
            .forEach { (revision, evidence) ->
                evidence.forEachIndexed { index, item ->
                    line("cng.${revision.curveK}.${revision.mapK}.$index", encodeEvidence(item))
                }
            }
        state.comparisons.forEachIndexed { index, comparison ->
            line("comparison.$index", encodeComparison(comparison))
        }
        state.suggestions.forEachIndexed { index, suggestion ->
            line("suggestion.$index", encodeSuggestion(suggestion))
        }
        state.checkpoints.forEachIndexed { index, checkpoint ->
            line("checkpoint.$index", encodeCheckpoint(checkpoint))
        }
    }

    fun decode(text: String): V7SessionState {
        val values = linkedMapOf<String, String>()
        text.lineSequence().filter { it.isNotBlank() }.forEach { raw ->
            val split = raw.indexOf('=')
            require(split > 0) { "Linha inválida no snapshot V7" }
            values[raw.substring(0, split)] = raw.substring(split + 1)
        }
        require(values["schema"] in ACCEPTED_SCHEMAS) { "Schema de sessão V7 incompatível" }
        require(requireValue(values, "mapShape") == "${CalibrationShapeV7.MAP_K_STORAGE_ROWS},${CalibrationShapeV7.MAP_K_COLUMNS}") {
            "Dimensão do Mapa K incompatível"
        }
        val revision = parseRevision(requireValue(values, "revision"))
        val curve = requireValue(values, "curve").split(',').map(String::toDouble)
        val map = (0 until CalibrationShapeV7.MAP_K_STORAGE_ROWS).map { row ->
            requireValue(values, "map.$row").split(',').map(String::toInt).also { valuesInRow ->
                require(valuesInRow.size == CalibrationShapeV7.MAP_K_COLUMNS) {
                    "Linha $row do Mapa K possui dimensão inválida"
                }
            }
        }
        val petrol = values.entries
            .filter { it.key.startsWith("petrol.") }
            .sortedBy { numericSuffix(it.key) }
            .map { decodeEvidence(it.value, FuelV7.PETROL, null) }
        val cng = linkedMapOf<CalibrationRevisionV7, MutableList<EvidenceV7>>()
        values.entries.filter { it.key.startsWith("cng.") }.forEach { (key, value) ->
            val parts = key.split('.')
            require(parts.size == 4)
            val itemRevision = CalibrationRevisionV7(parts[1].toLong(), parts[2].toLong())
            cng.getOrPut(itemRevision) { mutableListOf() }
                .add(decodeEvidence(value, FuelV7.CNG, itemRevision))
        }
        val comparisons = values.entries
            .filter { it.key.startsWith("comparison.") }
            .sortedBy { numericSuffix(it.key) }
            .map { decodeComparison(it.value) }
        val suggestions = values.entries
            .filter { it.key.startsWith("suggestion.") }
            .sortedBy { numericSuffix(it.key) }
            .map { decodeSuggestion(it.value) }
        val checkpoints = values.entries
            .filter { it.key.startsWith("checkpoint.") }
            .sortedBy { numericSuffix(it.key) }
            .map { decodeCheckpoint(it.value) }
        return V7SessionState(
            sessionId = unescape(requireValue(values, "session")),
            calibration = CalibrationStateV7(revision, curve, map),
            petrolEvidence = petrol,
            cngEvidenceByRevision = cng.mapValues { it.value.toList() },
            comparisons = comparisons,
            suggestions = suggestions,
            checkpoints = checkpoints,
            lastWriteMessage = values["lastWriteMessage"]?.let(::unescape).orEmpty(),
        )
    }

    private fun StringBuilder.line(key: String, value: String) {
        append(key).append('=').append(value).append('\n')
    }

    private fun encodeEvidence(item: EvidenceV7): String = listOf(
        escape(item.id),
        item.collectedAtMs.toString(),
        escape(item.visitId),
        item.rpm.toString(),
        item.mapBar.toString(),
        item.petrolMs.toString(),
        item.quality.toString(),
        item.waterC.toString(),
        item.gasC.toString(),
        item.pressureDiffBar.toString(),
    ).joinToString("|")

    private fun decodeEvidence(
        value: String,
        fuel: FuelV7,
        revision: CalibrationRevisionV7?,
    ): EvidenceV7 {
        val parts = splitEscaped(value)
        require(parts.size == 7 || parts.size == 10)
        return EvidenceV7(
            id = unescape(parts[0]),
            fuel = fuel,
            collectedAtMs = parts[1].toLong(),
            visitId = unescape(parts[2]),
            rpm = parts[3].toDouble(),
            mapBar = parts[4].toDouble(),
            petrolMs = parts[5].toDouble(),
            quality = parts[6].toDouble(),
            cngRevision = revision,
            waterC = parts.getOrNull(7)?.toDouble() ?: EvidenceV7.UNKNOWN_TEMPERATURE_C,
            gasC = parts.getOrNull(8)?.toDouble() ?: EvidenceV7.UNKNOWN_TEMPERATURE_C,
            pressureDiffBar = parts.getOrNull(9)?.toDouble() ?: 0.0,
        )
    }

    private fun encodeComparison(item: FuelComparisonV7): String = listOf(
        escape(item.id),
        item.revision.curveK.toString(),
        item.revision.mapK.toString(),
        escape(item.cngVisitId),
        escape(item.petrolEvidenceIds.joinToString(",")),
        item.rpm.toString(),
        item.mapBar.toString(),
        item.waterC.toString(),
        item.petrolTargetMs.toString(),
        item.petrolOnCngMs.toString(),
        item.differenceMs.toString(),
        item.errorPercent.toString(),
        escape(item.direction),
        item.quality.toString(),
        item.createdAtMs.toString(),
    ).joinToString("|")

    private fun decodeComparison(value: String): FuelComparisonV7 {
        val parts = splitEscaped(value)
        require(parts.size == 15)
        return FuelComparisonV7(
            id = unescape(parts[0]),
            revision = CalibrationRevisionV7(parts[1].toLong(), parts[2].toLong()),
            cngVisitId = unescape(parts[3]),
            petrolEvidenceIds = unescape(parts[4]).split(',').filter(String::isNotBlank),
            rpm = parts[5].toDouble(),
            mapBar = parts[6].toDouble(),
            waterC = parts[7].toDouble(),
            petrolTargetMs = parts[8].toDouble(),
            petrolOnCngMs = parts[9].toDouble(),
            differenceMs = parts[10].toDouble(),
            errorPercent = parts[11].toDouble(),
            direction = unescape(parts[12]),
            quality = parts[13].toDouble(),
            createdAtMs = parts[14].toLong(),
        )
    }

    private fun encodeSuggestion(item: LocalSuggestionV7): String = listOf(
        escape(item.id),
        item.createdAtMs.toString(),
        item.expectedRevision.curveK.toString(),
        item.expectedRevision.mapK.toString(),
        item.target.name,
        escape(item.rationale),
        item.curveChanges.joinToString(";") { "${it.index},${it.before},${it.after}" },
        item.mapChanges.joinToString(";") { "${it.row},${it.column},${it.before},${it.after}" },
        item.updatedAtMs.toString(),
        item.lifecycle.name,
        item.confidence.toString(),
        item.stabilityGeneration.toString(),
        escape(item.stabilityState),
        item.consolidatedErrorPercent?.toString().orEmpty(),
        item.recentErrorPercent?.toString().orEmpty(),
        item.physics.magnitudeAuthority.name,
        item.physics.stepAuthority.name,
        item.physics.correctionMechanism.name,
        item.physics.effectDirection.name,
        item.physics.lowerBound?.toString().orEmpty(),
        item.physics.upperBound?.toString().orEmpty(),
        encodeStrings(item.physics.assumptions),
        escape(item.physics.falsifier),
        encodeStrings(item.physics.evidencePath),
        item.physics.idealTarget.toString(),
    ).joinToString("|")

    private fun decodeSuggestion(value: String): LocalSuggestionV7 {
        val parts = splitEscaped(value)
        require(parts.size == 8 || parts.size == 11 || parts.size == 15 || parts.size == 25)
        val target = SuggestionTargetV7.valueOf(parts[4])
        val curveChanges = parts[6].takeIf(String::isNotBlank)?.split(';').orEmpty().map { encoded ->
            val values = encoded.split(',')
            require(values.size == 3)
            CurvePointChangeV7(values[0].toInt(), values[1].toDouble(), values[2].toDouble())
        }
        val mapChanges = parts[7].takeIf(String::isNotBlank)?.split(';').orEmpty().map { encoded ->
            val values = encoded.split(',')
            require(values.size == 4)
            MapCellChangeV7(values[0].toInt(), values[1].toInt(), values[2].toInt(), values[3].toInt())
        }
        val createdAt = parts[1].toLong()
        val physics = if (parts.size == 25) {
            PhysicsSuggestionMetadataV7(
                magnitudeAuthority = runCatching { MagnitudeAuthority.valueOf(parts[15]) }
                    .getOrDefault(MagnitudeAuthority.UNKNOWN),
                stepAuthority = runCatching { MagnitudeAuthority.valueOf(parts[16]) }
                    .getOrDefault(MagnitudeAuthority.UNKNOWN),
                correctionMechanism = runCatching { CorrectionMechanism.valueOf(parts[17]) }
                    .getOrDefault(CorrectionMechanism.UNKNOWN),
                effectDirection = runCatching { EffectDirection.valueOf(parts[18]) }
                    .getOrDefault(EffectDirection.UNKNOWN),
                lowerBound = parts[19].takeIf(String::isNotBlank)?.toDoubleOrNull(),
                upperBound = parts[20].takeIf(String::isNotBlank)?.toDoubleOrNull(),
                assumptions = decodeStrings(parts[21]),
                falsifier = unescape(parts[22]),
                evidencePath = decodeStrings(parts[23]),
                idealTarget = parts[24] == "true",
            )
        } else {
            PhysicsSuggestionMetadataV7()
        }
        return LocalSuggestionV7(
            id = unescape(parts[0]),
            createdAtMs = createdAt,
            expectedRevision = CalibrationRevisionV7(parts[2].toLong(), parts[3].toLong()),
            target = target,
            curveChanges = curveChanges,
            mapChanges = mapChanges,
            rationale = unescape(parts[5]),
            updatedAtMs = parts.getOrNull(8)?.toLongOrNull()?.coerceAtLeast(createdAt) ?: createdAt,
            lifecycle = parts.getOrNull(9)?.let { raw ->
                runCatching { SuggestionLifecycleV7.valueOf(raw) }.getOrDefault(SuggestionLifecycleV7.PENDING)
            } ?: SuggestionLifecycleV7.PENDING,
            confidence = parts.getOrNull(10)?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.0,
            stabilityGeneration = parts.getOrNull(11)?.toIntOrNull()?.coerceAtLeast(-1) ?: -1,
            stabilityState = parts.getOrNull(12)?.let(::unescape).takeUnless(String?::isNullOrBlank) ?: "UNASSESSED",
            consolidatedErrorPercent = parts.getOrNull(13)?.takeIf(String::isNotBlank)?.toDoubleOrNull(),
            recentErrorPercent = parts.getOrNull(14)?.takeIf(String::isNotBlank)?.toDoubleOrNull(),
            physics = physics,
        )
    }

    private fun encodeCheckpoint(item: CheckpointV7): String = listOf(
        escape(item.id),
        item.createdAtMs.toString(),
        escape(item.reason),
        item.calibration.revision.curveK.toString(),
        item.calibration.revision.mapK.toString(),
        item.calibration.curveK.joinToString(","),
        item.calibration.mapK.joinToString(";") { row -> row.joinToString(",") },
    ).joinToString("|")

    private fun decodeCheckpoint(value: String): CheckpointV7 {
        val parts = splitEscaped(value)
        require(parts.size == 7)
        val calibration = CalibrationStateV7(
            revision = CalibrationRevisionV7(parts[3].toLong(), parts[4].toLong()),
            curveK = parts[5].split(',').map(String::toDouble),
            mapK = parts[6].split(';').map { row -> row.split(',').map(String::toInt) },
        )
        return CheckpointV7(
            id = unescape(parts[0]),
            createdAtMs = parts[1].toLong(),
            reason = unescape(parts[2]),
            calibration = calibration,
        )
    }

    private fun encodeStrings(values: List<String>): String =
        escape(values.joinToString(LIST_SEPARATOR.toString()))

    private fun decodeStrings(value: String): List<String> =
        unescape(value).split(LIST_SEPARATOR).filter(String::isNotBlank)

    private fun parseRevision(value: String): CalibrationRevisionV7 {
        val parts = value.split(',')
        require(parts.size == 2)
        return CalibrationRevisionV7(parts[0].toLong(), parts[1].toLong())
    }

    private fun requireValue(values: Map<String, String>, key: String): String =
        values[key] ?: error("Campo obrigatório ausente: $key")

    private fun numericSuffix(key: String): Int = key.substringAfterLast('.').toInt()

    private fun escape(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '\\', '|', '=', '\n', '\r' -> append('%').append(char.code.toString(16).padStart(2, '0'))
                else -> append(char)
            }
        }
    }

    private fun unescape(value: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < value.length) {
            if (value[index] == '%' && index + 2 < value.length) {
                result.append(value.substring(index + 1, index + 3).toInt(16).toChar())
                index += 3
            } else {
                result.append(value[index++])
            }
        }
        return result.toString()
    }

    private fun splitEscaped(value: String): List<String> {
        val output = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < value.length) {
            when {
                value[index] == '%' && index + 2 < value.length -> {
                    current.append(value, index, index + 3)
                    index += 3
                }
                value[index] == '|' -> {
                    output += current.toString()
                    current.setLength(0)
                    index++
                }
                else -> current.append(value[index++])
            }
        }
        output += current.toString()
        return output
    }
}
