package com.omegas.prohub.usb

enum class UsbProtocolStatusClass {
    ACK,
    EXTENDED_RETRYABLE,
    EXTENDED_NON_RETRYABLE,
    EXTENDED_UNKNOWN,
    UNKNOWN,
}

object UsbProtocolStatusClassifier {
    const val STATUS_ACK = 0x53
    const val STATUS_EXTENDED = 0xCA
    const val EXTENDED_RETRYABLE_CODE = 0x08
    const val EXTENDED_NON_RETRYABLE_CODE = 0x10

    fun classify(status: Int, payload: ByteArray): UsbProtocolStatusClass = when {
        status == STATUS_ACK -> UsbProtocolStatusClass.ACK
        status == STATUS_EXTENDED && payload.firstOrNull()?.toInt()?.and(0xFF) == EXTENDED_RETRYABLE_CODE ->
            UsbProtocolStatusClass.EXTENDED_RETRYABLE
        status == STATUS_EXTENDED && payload.firstOrNull()?.toInt()?.and(0xFF) == EXTENDED_NON_RETRYABLE_CODE ->
            UsbProtocolStatusClass.EXTENDED_NON_RETRYABLE
        status == STATUS_EXTENDED -> UsbProtocolStatusClass.EXTENDED_UNKNOWN
        else -> UsbProtocolStatusClass.UNKNOWN
    }
}

data class UsbProtocolReply(
    val ok: Boolean,
    val status: Int = -1,
    val payload: ByteArray = byteArrayOf(),
    val request: ByteArray = byteArrayOf(),
    val echo: ByteArray = byteArrayOf(),
    val rawResponse: ByteArray = byteArrayOf(),
    val error: String = "",
    val elapsedMs: Long = 0,
) {
    val statusClass: UsbProtocolStatusClass
        get() = UsbProtocolStatusClassifier.classify(status, payload)

    val retryable: Boolean
        get() = statusClass == UsbProtocolStatusClass.EXTENDED_RETRYABLE

    val nonRetryable: Boolean
        get() = statusClass == UsbProtocolStatusClass.EXTENDED_NON_RETRYABLE
}
