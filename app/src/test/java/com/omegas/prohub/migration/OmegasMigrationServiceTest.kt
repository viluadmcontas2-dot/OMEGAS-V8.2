package com.omegas.prohub.migration

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmegasMigrationServiceTest {

    @Test
    fun `migrates old omegas file correctly ignoring calculated fields`() {
        val oldJson = """
            {
                "format": "omegas-learning-v3",
                "epoch": 2,
                "mapHash": "abc123hash",
                "legacy_advisor_present": true,
                "suggestions": [
                    {"type": "bad_suggestion"}
                ],
                "tolerances": {
                    "some": "tolerance"
                },
                "comparisons": [
                    {"id": "comp1"}
                ],
                "regions": [
                    {
                        "id": "reg1",
                        "fuel": "PETROL",
                        "rpmMean": 1000.0,
                        "mapMean": 0.5,
                        "petrolMean": 3.0,
                        "waterMean": 90.0,
                        "sampleCount": 10
                    },
                    {
                        "id": "reg2",
                        "fuel": "CNG",
                        "rpm": 2000.0,
                        "map_bar": 0.8,
                        "petrol_ms": 4.5,
                        "epoch": 1,
                        "samples": 20
                    }
                ],
                "sessions": [
                    {
                        "id": "sess1",
                        "startedAt": 12345,
                        "sampleCount": 10
                    }
                ]
            }
        """.trimIndent()

        val migrated = OmegasMigrationService.migrate(oldJson)

        assertTrue(migrated.optBoolean("ok"))
        assertEquals("omegas-learning-v5", migrated.optString("format"))
        assertEquals(2, migrated.optInt("epoch"))
        assertEquals("abc123hash", migrated.optString("mapHash"))

        val regions = migrated.optJSONArray("regions")!!
        assertEquals(2, regions.length())

        val reg1 = regions.optJSONObject(0)!!
        assertEquals("reg1", reg1.optString("id"))
        assertEquals("PETROL", reg1.optString("fuel"))
        assertEquals(0, reg1.optInt("epoch")) // Petrol should be forced to epoch 0
        assertEquals(1000.0, reg1.optDouble("rpm"), 0.001)
        assertEquals(0.5, reg1.optDouble("map_bar"), 0.001)
        assertEquals(3.0, reg1.optDouble("petrol_ms"), 0.001)
        assertEquals(10, reg1.optInt("samples"))

        val reg2 = regions.optJSONObject(1)!!
        assertEquals("reg2", reg2.optString("id"))
        assertEquals("CNG", reg2.optString("fuel"))
        assertEquals(1, reg2.optInt("epoch"))
        assertEquals(2000.0, reg2.optDouble("rpm"), 0.001)
        assertEquals(0.8, reg2.optDouble("map_bar"), 0.001)
        assertEquals(4.5, reg2.optDouble("petrol_ms"), 0.001)
        assertEquals(20, reg2.optInt("samples"))

        val sessions = migrated.optJSONArray("sessions")!!
        assertEquals(1, sessions.length())
        assertEquals("sess1", sessions.optJSONObject(0)!!.optString("id"))

        val comparisons = migrated.optJSONArray("comparisons")
        assertEquals(0, comparisons?.length() ?: 0) // Should be cleared

        assertFalse(migrated.has("suggestions"))
        assertFalse(migrated.has("tolerances"))
    }

    @Test
    fun `migration is idempotent`() {
        val oldJson = """
            {
                "format": "omegas-learning-v3",
                "epoch": 2,
                "regions": [
                    {
                        "id": "reg1",
                        "fuel": "PETROL",
                        "rpmMean": 1000.0,
                        "mapMean": 0.5,
                        "petrolMean": 3.0
                    }
                ]
            }
        """.trimIndent()

        val migrated1 = OmegasMigrationService.migrate(oldJson)
        val migrated2 = OmegasMigrationService.migrate(migrated1.toString())

        assertEquals(migrated1.toString(), migrated2.toString())
    }

    @Test
    fun `fails gracefully on corrupted json`() {
        val badJson = "{ \"regions\": ["
        val result = OmegasMigrationService.migrate(badJson)
        
        assertFalse(result.optBoolean("ok"))
        assertEquals("Arquivo corrompido ou JSON inválido", result.optString("error"))
    }
}
