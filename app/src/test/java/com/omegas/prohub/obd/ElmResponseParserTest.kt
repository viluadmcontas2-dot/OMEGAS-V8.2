package com.omegas.prohub.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElmResponseParserTest {
    @Test
    fun `parses spaced and compact mode 01 responses`() {
        assertEquals(listOf(0x90), ElmResponseParser.mode01("41 06 90 >", 0x06))
        assertEquals(listOf(0x90), ElmResponseParser.mode01("410690", 0x06))
        assertEquals(listOf(0x90), ElmResponseParser.mode01("SEARCHING...\r41 06 90\r>\r", 0x06))
    }

    @Test
    fun `rejects negative transport responses and wrong pid`() {
        assertNull(ElmResponseParser.mode01("NO DATA", 0x06))
        assertNull(ElmResponseParser.mode01("UNABLE TO CONNECT", 0x06))
        assertNull(ElmResponseParser.mode01("STOPPED", 0x06))
        assertNull(ElmResponseParser.mode01("41 0C 1A F8", 0x06))
    }

    @Test
    fun `ignores command echo before the actual response`() {
        assertEquals(listOf(0x80), ElmResponseParser.mode01("0106\r41 06 80\r>", 0x06))
    }
}
