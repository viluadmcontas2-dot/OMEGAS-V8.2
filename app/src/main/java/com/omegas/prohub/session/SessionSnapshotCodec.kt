package com.omegas.prohub.session

/** Snapshot textual determinístico, sem dependência de Android ou bibliotecas externas. */
object SessionSnapshotCodec {
    private const val SCHEMA = "OMEGAS_V7_SESSION_6"
    private val ACCEPTED_SCHEMAS = setOf(
        SCHEMA,
        "OMEGAS_V7_SESSION_5",
        "OMEGAS_V7_SESSION_4",
        "OMEGAS_V7_SESSION_3",
        "OMEGAS_V7_SESSION_2",
    )

    fun encode(state: SessionState): String = buildString {
        line("schema", SCHEMA)
        line("session", escape(state.sessionId))
        line("revision", "${state.calibration.revision.curveK},${state.calibration.revision.mapK}")
        line("curve", state.calibration.curveK.joinToString(","))
        line("mapShape", "${CalibrationShape.MAP_K_STORAGE_ROWS},${CalibrationShape.MAP_K_COLUMNS}")
        line("lastWriteMessage", escape(state.lastWriteMessage))
        state.calibration.mapK.forEachIndexed { row, values -> line("map.$row", values.joinToString(",")) }
        state.petrolEvidence.forEachIndexed { index, evidence -> line("petrol.$index", encodeEvidence(evidence)) }
        state.cngEvidenceByRevision.toSortedMap(compareBy<CalibrationRevision> { it.curveK }.thenBy { it.mapK })
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

    fun decode(text: String): SessionState {
        val values = linkedMapOf<String, String>()
        text.lineSequence().filter { it.isNotBlank() }.forEach { raw ->
            val split = raw.indexOf('=')
            require(split > 0) { "Linha inválida no snapshot V7" }
            values[raw.substring(0, split)] = raw.substring(split + 1)
        }
        require(values["schema"] in ACCEPTED_SCHEMAS) { "Schema de sessão V7 incompatível" }
        require(requireValue(values, "mapShape") == "${CalibrationShape.MAP_K_STORAGE_ROWS},${CalibrationShape.MAP_K_COLUMNS}") {
            "Dimensão do Mapa K incompatível"
        }
        val revision = parseRevision(requireValue(values, "revision"))
        val curve = requireValue(values, "curve").split(',').map(String::toDouble)
        val map = (0 until CalibrationShape.MAP_K_STORAGE_ROWS).map { row ->
            requireValue(values, "map.$row").split(',').map(String::toInt).also { valuesInRow ->
                require(valuesInRow.size == CalibrationShape.MAP_K_COLUMNS) {
                    "Linha $row do Mapa K possui dimensão inválida"
                }
            }
        }
        val petrol = values.entries
            .filter { it.key.startsWith("petrol.") }
            .sortedBy { numericSuffix(it.key) }
            .map { decodeEvidence(it.value, CalibrationFuel.PETROL, null) }
        val cng = linkedMapOf<CalibrationRevision, MutableList<SessionEvidence>>()
        values.entries.filter { it.key.startsWith("cng.") }.forEach { (key, value) ->
            val parts = key.split('.')
            require(parts.size == 4)
            val itemRevision = CalibrationRevision(parts[1].toLong(), parts[2].toLong())
            cng.getOrPut(itemRevision) { mutableListOf() }
                .add(decodeEvidence(value, CalibrationFuel.CNG, itemRevision))
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
        return SessionState(
            sessionId = unescape(requireValue(values, "session")),
            calibration = CalibrationState(revision, curve, map),
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

    private fun encodeEvidence(item: SessionEvidence): String = listOf(
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
        fuel: CalibrationFuel,
        revision: CalibrationRevision?,
    ): SessionEvidence {
        val parts = splitEscaped(value)
        require(parts.size == 7 || parts.size == 10)
        return SessionEvidence(
            id = unescape(parts[0]),
            fuel = fuel,
            collectedAtMs = parts[1].toLong(),
            visitId = unescape(parts[2]),
            rpm = parts[3].toDouble(),
            mapBar = parts[4].toDouble(),
            petrolMs = parts[5].toDouble(),
            quality = parts[6].toDouble(),
            cngRevision = revision,
            waterC = parts.getOrNull(7)?.toDouble() ?: SessionEvidence.UNKNOWN_TEMPERATURE_C,
            gasC = parts.getOrNull(8)?.toDouble() ?: SessionEvidence.UNKNOWN_TEMPERATURE_C,
            pressureDiffBar = parts.getOrNull(9)?.toDouble() ?: 0.0,
        )
    }

    private fun encodeComparison(item: CalibrationFuelComparison): String = listOf(
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

    private fun decodeComparison(value: String): CalibrationFuelComparison {
        val parts = splitEscaped(value)
        require(parts.size == 15)
        return CalibrationFuelComparison(
            id = unescape(parts[0]),
            revision = CalibrationRevision(parts[1].toLong(), parts[2].toLong()),
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

    private fun encodeSuggestion(item: LocalSuggestion): String = listOf(
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
    ).joinToString("|")

    private fun decodeSuggestion(value: String): LocalSuggestion {
        val parts = splitEscaped(value)
        require(parts.size == 8 || parts.size == 11 || parts.size == 15)
        val target = SuggestionTarget.valueOf(parts[4])
        val curveChanges = parts[6].takeIf(String::isNotBlank)?.split(';').orEmpty().map { encoded ->
            val values = encoded.split(',')
            require(values.size == 3)
            CurvePointChange(values[0].toInt(), values[1].toDouble(), values[2].toDouble())
        }
        val mapChanges = parts[7].takeIf(String::isNotBlank)?.split(';').orEmpty().map { encoded ->
            val values = encoded.split(',')
            require(values.size == 4)
            MapCellChange(values[0].toInt(), values[1].toInt(), values[2].toInt(), values[3].toInt())
        }
        val createdAt = parts[1].toLong()
        return LocalSuggestion(
            id = unescape(parts[0]),
            createdAtMs = createdAt,
            expectedRevision = CalibrationRevision(parts[2].toLong(), parts[3].toLong()),
            target = target,
            curveChanges = curveChanges,
            mapChanges = mapChanges,
            rationale = unescape(parts[5]),
            updatedAtMs = parts.getOrNull(8)?.toLongOrNull()?.coerceAtLeast(createdAt) ?: createdAt,
            lifecycle = parts.getOrNull(9)?.let { raw ->
                runCatching { SuggestionLifecycle.valueOf(raw) }.getOrDefault(SuggestionLifecycle.PENDING)
            } ?: SuggestionLifecycle.PENDING,
            confidence = parts.getOrNull(10)?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.0,
            stabilityGeneration = parts.getOrNull(11)?.toIntOrNull()?.coerceAtLeast(-1) ?: -1,
            stabilityState = parts.getOrNull(12)?.let(::unescape).takeUnless(String?::isNullOrBlank) ?: "UNASSESSED",
            consolidatedErrorPercent = parts.getOrNull(13)?.takeIf(String::isNotBlank)?.toDoubleOrNull(),
            recentErrorPercent = parts.getOrNull(14)?.takeIf(String::isNotBlank)?.toDoubleOrNull(),
        )
    }

    private fun encodeCheckpoint(item: Checkpoint): String = listOf(
        escape(item.id),
        item.createdAtMs.toString(),
        escape(item.reason),
        item.calibration.revision.curveK.toString(),
        item.calibration.revision.mapK.toString(),
        item.calibration.curveK.joinToString(","),
        item.calibration.mapK.joinToString(";") { row -> row.joinToString(",") },
    ).joinToString("|")

    private fun decodeCheckpoint(value: String): Checkpoint {
        val parts = splitEscaped(value)
        require(parts.size == 7)
        val calibration = CalibrationState(
            revision = CalibrationRevision(parts[3].toLong(), parts[4].toLong()),
            curveK = parts[5].split(',').map(String::toDouble),
            mapK = parts[6].split(';').map { row -> row.split(',').map(String::toInt) },
        )
        return Checkpoint(
            id = unescape(parts[0]),
            createdAtMs = parts[1].toLong(),
            reason = unescape(parts[2]),
            calibration = calibration,
        )
    }

    private fun parseRevision(value: String): CalibrationRevision {
        val parts = value.split(',')
        require(parts.size == 2)
        return CalibrationRevision(parts[0].toLong(), parts[1].toLong())
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
