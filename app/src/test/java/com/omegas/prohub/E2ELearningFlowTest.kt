package com.omegas.prohub

import com.omegas.prohub.ecu.*
import com.omegas.prohub.learning.*
import com.omegas.prohub.util.RingLog
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class E2ELearningFlowTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `test complete learning lifecycle with latency metrics`() {
        val file = temporary.newFile("e2e-learning.json")
        var memory = MotorLearningMemory(file, RingLog())
        val productionPolicy = LearningTolerancePolicy()
        assertEquals(10, productionPolicy.requiredFrames)
        val analyzer = MotorSampleAnalyzer { productionPolicy }
        
        memory.startSession()
        
        val rpm = 900
        val map = 0.44
        var tick = 0L
        
        // --- 1. Gasolina learns (RPM 900, MAP 0.44, Tinj 4.7) ---
        var petrolFrames = 0
        var petrolAbsorbed = false
        
        while (!petrolAbsorbed && petrolFrames < 100) {
            val tel = telemetry(at = tick, fuel = Mp48Fuel.PETROL, rpm = rpm, mapBar = map, petrolMs = 4.7)
            val decision = analyzer.add(tel)
            if (decision.learningEligible) {
                memory.ingest(tel, decision)
                petrolAbsorbed = true
            }
            petrolFrames++
            tick += 50
        }
        
        assertTrue("Gasolina should be absorbed", petrolAbsorbed)
        println("Gasolina latency: $petrolFrames frames")

        // Persist and recreate the memory before GNV learning: gasoline truth must survive restart.
        println("BEFORE_RELOAD=" + memory.export("e2e").toString())
        memory.awaitPersistence()
        assertTrue("Learning state must be written to disk", file.isFile && file.length() > 0L)
        val persisted = JSONObject(file.readText())
        assertEquals(MotorLearningMemory.FORMAT, persisted.getString("format"))
        assertTrue("Persisted state must carry its integrity digest", persisted.optString("stateDigest").isNotBlank())
        val persistedRegions = persisted.getJSONArray("regions")
        assertTrue(
            "Persisted state must retain the petrol reference using the public wire contract",
            (0 until persistedRegions.length()).any {
                persistedRegions.getJSONObject(it).getString("fuel") == Mp48Fuel.PETROL.wireName
            },
        )
        memory.close()
        memory = MotorLearningMemory(file, RingLog())
        println("AFTER_RELOAD=" + memory.export("e2e").toString())
        val restoredPetrol = memory.export("e2e").getJSONArray("regions")
        assertTrue(
            "Reload must retain the persisted petrol reference",
            (0 until restoredPetrol.length()).any {
                restoredPetrol.getJSONObject(it).getString("fuel") == Mp48Fuel.PETROL.wireName
            },
        )
        
        // Gap to simulate a later GNV visit
        tick += 5000
        analyzer.reset()

        // --- 3. GNV learns at +8% diff -> Tinj is 4.7 * 1.08 = 5.076 ---
        // To get a suggestion, we might need multiple visits/sessions. 
        // We will simulate visits until we get an actionable suggestion.
        var visits = 0
        var totalGnvFrames = 0
        var provisionalVisits = -1
        var actionableVisits = -1
        
        while (actionableVisits == -1 && visits < 20) {
            visits++
            memory.startSession()
            analyzer.reset()
            
            var cngAbsorbed = false
            var currentVisitFrames = 0
            
            while (!cngAbsorbed && currentVisitFrames < 100) {
                val tel = telemetry(at = tick, fuel = Mp48Fuel.CNG, rpm = rpm, mapBar = map, petrolMs = 5.076)
                val decision = analyzer.add(tel)
                if (decision.learningEligible) {
                    memory.ingest(tel, decision)
                    cngAbsorbed = true
                }
                currentVisitFrames++
                totalGnvFrames++
                tick += 50
            }
            
            // Check for suggestions
            val result = AssistedCalibrationAdvisor.analyze(memory.advisorSnapshot())
            val suggestions = result.optJSONArray("kFactorSuggestions")
            
            if (suggestions != null && suggestions.length() > 0) {
                var hasProvisional = false
                var hasActionable = false
                
                for (i in 0 until suggestions.length()) {
                    val s = suggestions.getJSONObject(i)
                    if (s.getBoolean("actionable")) {
                        hasActionable = true
                    } else {
                        hasProvisional = true
                    }
                }
                
                if (hasProvisional && provisionalVisits == -1) {
                    provisionalVisits = visits
                }
                if (hasActionable && actionableVisits == -1) {
                    actionableVisits = visits
                }
            }
            
            // Gap for next visit
            tick += 5000
        }
        
        assertTrue("Should get actionable suggestion eventually", actionableVisits != -1)
        
        println("Provisional suggestion appeared at visit $provisionalVisits")
        println("Actionable suggestion appeared at visit $actionableVisits")
        println("Total GNV frames processed: $totalGnvFrames")
    }

    private fun telemetry(
        at: Long,
        fuel: Mp48Fuel,
        rpm: Int,
        mapBar: Double,
        petrolMs: Double,
    ) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = rpm,
        levelRaw = 100,
        gasRaw = if (fuel == Mp48Fuel.CNG) 200 else 0,
        gasMsDiagnostic = null,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = petrolMs,
        dynamicCorrection = 0,
        fuelByte = when (fuel) {
            Mp48Fuel.PETROL -> 0x80
            Mp48Fuel.CNG -> 0x90
            else -> 0x00
        },
        fuel = fuel,
        state = fuel.wireName,
        waterRaw = 80,
        waterC = 80,
        gasC = if (fuel == Mp48Fuel.CNG) 65 else 30,
        gasPressureRaw = 100,
        gasPressureAbsBar = 2.0,
        mapRaw = 100,
        mapBar = mapBar,
        pressureDiffBar = 1.4,
        plausible = true,
    )
}
