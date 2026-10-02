package com.omegas.prohub.telemetry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import org.json.JSONObject
class CalibrationScoreboardTest {
 @Test fun onlyConfirmedReadbackStartsEpochAndDuplicateReceiptDoesNotResetIt() {
   val s=CalibrationScoreboard()
   assertFalse(s.confirm("a","CURVE_K",false,true,0L))
   assertFalse(s.confirm("a","CURVE_K",true,false,0L))
   assertNull(s.json().optJSONObject("current"))
   assertTrue(s.confirm("a","CURVE_K",true,true,1L))
   s.observe(2.0,10.0,50.0,true)
   s.observe(2.0,20.0,25.0,true)
   assertFalse(s.confirm("a","CURVE_K",true,true,2L))
   assertEquals(10.0,s.json().getJSONObject("current").getDouble("km"),1e-9)
   assertTrue(s.confirm("b","MAP_K",true,true,3L))
   s.observe(1.8,20.0,25.0,true);s.observe(1.8,25.0,12.5,true)
   assertEquals(-10.0,s.json().getDouble("gasPerAirChangePercent"),1e-9)
   assertEquals(10.0,s.json().getJSONObject("previous").getDouble("km"),1e-9)
 }
 @Test fun gasolineGpsGapsRefuelAndUnknownLevelDoNotManufactureConsumption() {
   val s=CalibrationScoreboard();s.confirm("a","CURVE_K",true,true,1L)
   s.observe(2.0,10.0,50.0,true);s.observe(2.0,20.0,25.0,false)
   s.observe(2.0,25.0,25.0,true)
   assertEquals(0.0,s.json().getJSONObject("current").getDouble("km"),1e-9)
   s.observe(2.0,null,20.0,true);s.observe(2.0,30.0,80.0,true)
   assertTrue(s.json().getJSONObject("current").isNull("kmPerStep"))
   s.interrupt("AUTOMATCH_NATIVO")
   s.observe(1.0,40.0,60.0,true)
   assertEquals(2.0,s.json().getJSONObject("current").getDouble("gasPerAir"),1e-9)
 }
 @Test fun historySurvivesRestartWithoutCountingGpsResetAsDistance() {
   val f=File.createTempFile("scoreboard", ".json");f.delete()
   val s=CalibrationScoreboard(f);s.confirm("a","CURVE_K",true,true,1L)
   s.observe(2.0,10.0,50.0,true);s.observe(2.0,20.0,25.0,true);s.save()
   val restored=CalibrationScoreboard(f)
   restored.observe(2.0,0.0,25.0,true)
   assertEquals(10.0,restored.json().getJSONObject("current").getDouble("km"),1e-9)
   f.delete()
 }
 @Test fun bothEpochsNeedMinimumDistanceAndClosureCopiesExistingLedgerMetric() {
   val s=CalibrationScoreboard();s.confirm("a","CURVE_K",true,true,1)
   s.observe(2.0,0.0,null,true,1000);s.observe(2.0,5.0,null,true,2000)
   s.confirm("b","MAP_K",true,true,3000,3.0)
   assertEquals(3.0,s.json().getJSONObject("previous").getDouble("gasPerAir"),1e-9)
   s.observe(1.5,0.0,null,true,4000);s.observe(1.5,4.0,null,true,5000)
   assertTrue(s.json().isNull("gasPerAirChangePercent"))
   s.observe(1.5,5.0,null,true,6000)
   assertEquals(-50.0,s.json().getDouble("gasPerAirChangePercent"),1e-9)
   assertTrue(s.json().getJSONObject("current").isNull("kmPerStep"))
 }
 @Test fun staleGpsGapDoesNotCountUnobservedDriving() {
   val s=CalibrationScoreboard();s.confirm("a","CURVE_K",true,true,1)
   s.observe(2.0,0.0,null,true,1000);s.observe(2.0,10.0,null,true,20000)
   assertEquals(0.0,s.json().getJSONObject("current").getDouble("km"),1e-9)
 }
}