package com.omegas.prohub.obd

import org.junit.Assert.assertEquals
import org.junit.Test

class ObdStftAcquisitionTest {
    @Test
    fun `PID 0106 byte converts to SAE STFT percent`() {
        assertEquals(0.0, ObdStftCodec.percent(128), 0.000001)
        assertEquals(10.15625, ObdStftCodec.percent(141), 0.000001)
        assertEquals(-10.15625, ObdStftCodec.percent(115), 0.000001)
    }

    @Test
    fun `SAE STFT endpoints preserve the raw byte domain`() {
        assertEquals(-100.0, ObdStftCodec.percent(0), 0.000001)
        assertEquals(99.21875, ObdStftCodec.percent(255), 0.000001)
    }
}
