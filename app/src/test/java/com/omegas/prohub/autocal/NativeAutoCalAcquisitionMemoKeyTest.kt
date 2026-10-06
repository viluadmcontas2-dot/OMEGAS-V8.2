package com.omegas.prohub.autocal

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NativeAutoCalAcquisitionMemoKeyTest {
    @Test
    fun `same blocked state but different fuel epoch invalidates acquisition memo`() {
        val gasPending = JSONObject()
            .put("usbSessionId", 77L)
            .put("nativeAutoMatchCount", 1)
            .put("petrolGeneration", 4)
            .put("gasGeneration", 5)
            .put("petrolPending", false)
            .put("gasPending", true)
            .put("petrolReferencePending", false)
            .put("gasReferencePending", true)
            .put("comparisonAllowed", false)

        val petrolPending = JSONObject(gasPending.toString())
            .put("petrolPending", true)
            .put("gasPending", false)
            .put("petrolReferencePending", true)
            .put("gasReferencePending", false)

        assertNotEquals(
            NativeAutoCalAcquisitionMemoKey.from(gasPending),
            NativeAutoCalAcquisitionMemoKey.from(petrolPending),
        )
        assertEquals(
            NativeAutoCalAcquisitionMemoKey.from(gasPending),
            NativeAutoCalAcquisitionMemoKey.from(JSONObject(gasPending.toString())),
        )
    }
}
