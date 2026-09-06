package com.omegas.prohub.obd

/** Parser puro das respostas ASCII do ELM327. */
object ElmResponseParser {
    private val terminalFailures = listOf(
        "NO DATA",
        "UNABLE TO CONNECT",
        "STOPPED",
        "BUS ERROR",
        "CAN ERROR",
        "ERROR",
    )

    /**
     * Retorna apenas os bytes de dados após `41 <pid>`.
     * Eco de comando, prompt, SEARCHING e linhas que pertençam a outro PID são
     * ignorados; respostas negativas não são convertidas em bytes falsos.
     */
    fun mode01(response: String, pid: Int): List<Int>? {
        val upper = response.uppercase()
        if (terminalFailures.any { upper.contains(it) }) return null
        val expectedPid = pid and 0xFF
        val cleaned = upper.replace("SEARCHING...", "")
        val lines = cleaned.split('\r', '\n', '>')
        for (line in lines) {
            val compact = line.filter { it in '0'..'9' || it in 'A'..'F' }
            if (compact.length < 6 || compact.length % 2 != 0) continue
            val bytes = compact.chunked(2).mapNotNull { it.toIntOrNull(16) }
            if (bytes.size < 3) continue
            for (index in 0 until bytes.size - 1) {
                if (bytes[index] == 0x41 && bytes[index + 1] == expectedPid) {
                    return bytes.drop(index + 2).takeIf { it.isNotEmpty() }
                }
            }
        }
        return null
    }
}
