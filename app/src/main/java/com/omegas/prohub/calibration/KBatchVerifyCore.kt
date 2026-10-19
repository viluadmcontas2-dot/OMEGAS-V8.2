package com.omegas.prohub.calibration

/**
 * Regras puras da gravação em lote da Curva K e do Mapa K (sem Android, sem serial).
 *
 * Garantia mantida: ACK por quadro + leitura de volta correta. O que muda é o custo:
 * a leitura de volta é a do VETOR/LINHA inteira numa transação (nunca célula a célula),
 * conferindo só as células alteradas, e a tela mostra o progresso real "Gravando i de N…".
 */
object KBatchVerifyCore {
    /** Linhas do Mapa K tocadas pelas células (cada uma é lida de volta uma única vez). */
    fun affectedRows(rowsOfCells: List<Int>): List<Int> = rowsOfCells.distinct()

    /** Transações do fluxo ANTIGO do Mapa K: conferir linhas + entrar + N + sair + reler linhas. */
    fun legacyMapTransactions(cells: Int, rows: Int): Int = rows + 1 + cells + 1 + rows

    /** Transações do fluxo novo: entrar + N escritas (só ACK) + sair + uma leitura por linha afetada. */
    fun mapTransactions(cells: Int, rows: Int): Int = 1 + cells + 1 + rows

    /** Curva K antiga e nova: 1 leitura do vetor + N escritas + 1 leitura do vetor (já sem célula a célula). */
    fun curveTransactions(points: Int): Int = 1 + points + 1

    /** Primeira divergência entre o esperado e a ECU, olhando só as colunas/pontos alterados. Null = confere. */
    fun firstMismatch(expected: IntArray, actual: IntArray, changed: Collection<Int>): Int? {
        for (index in changed.sorted()) {
            if (index !in expected.indices || index !in actual.indices) return index
            if (expected[index] != actual[index]) return index
        }
        return null
    }

    /** Texto de progresso real para a tela (dono, 2026-10-19): só conta, nunca tempo. */
    fun writingMessage(done: Int, total: Int): String = "Gravando $done de $total…"

    fun verifyingMessage(done: Int, total: Int): String = "Conferindo $done de $total linhas…"

    /** Todas as colunas/pontos alterados que divergem (para a correção autônoma regravar só essas). */
    fun divergent(expected: IntArray, actual: IntArray, changed: Collection<Int>): List<Int> =
        changed.sorted().filter { it !in expected.indices || it !in actual.indices || expected[it] != actual[it] }

    /** Aviso da barra do topo (dono, 2026-10-19): gravar é instantâneo; a conferência corre em segundo plano. */
    fun mapCellLabel(row: Int, column: Int): String = "L${row + 1} C${column + 1}"
    fun mismatchDetail(cellLabel: String, fixing: Boolean): String =
        "Gravação diferente na célula $cellLabel" + if (fixing) " — corrigindo" else ""

    /** Fases do estado de conferência em segundo plano (contrato com core/write-verify.js). */
    object Phase { const val IDLE = "idle"; const val CHECKING = "checking"; const val FIXING = "fixing"; const val OK = "ok"; const val FAILED = "failed" }

    /** Decisão pura de um passo: sem divergência = OK; divergiu e ainda não corrigiu = FIXING; divergiu de novo = FAILED. */
    fun nextPhase(divergentCount: Int, alreadyRetried: Boolean): String = when {
        divergentCount == 0 -> Phase.OK
        !alreadyRetried -> Phase.FIXING
        else -> Phase.FAILED
    }
}
