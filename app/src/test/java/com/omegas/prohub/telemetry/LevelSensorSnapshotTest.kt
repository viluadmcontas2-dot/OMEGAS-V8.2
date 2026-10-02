package com.omegas.prohub.telemetry
import com.omegas.prohub.usb.UsbProtocolReply
import org.junit.Assert.*
import org.junit.Test
class LevelSensorSnapshotTest {
    @Test fun `raw unknown fields stay undecoded and only tank index zero is requested`() {
        val requests=mutableListOf<ByteArray>();val sensor=LevelSensorSnapshot { 1000 }
        val result=sensor.read(7,{7}){r->requests.add(r);UsbProtocolReply(true,0x53,byteArrayOf(1))}
        assertEquals(5,requests.size);assertEquals(0x0A,requests.last()[0].toInt() and 255);assertEquals(0,requests.last()[3].toInt() and 255)
        val fields=result.getJSONArray("fields");assertEquals("UNDECODED",fields.getJSONObject(0).getString("status"));assertFalse(fields.getJSONObject(1).has("references"));assertTrue(result.getBoolean("proxy"));assertFalse(result.getBoolean("modelVerified"))
    }
    @Test fun `unanswered parameter is not zero and stale telemetry is not current`() {
        var now=1000L;val sensor=LevelSensorSnapshot { now };val result=sensor.read(7,{7}){UsbProtocolReply(false,error="timeout")}
        assertEquals("NOT_READ",result.getJSONArray("fields").getJSONObject(0).getString("status"));assertTrue(result.isNull("percent"))
        sensor.accept(100,now);assertEquals(60,sensor.json().getInt("percent"));now+=2001;assertTrue(sensor.json().isNull("percent"));assertEquals("Sem leitura atual",sensor.json().getString("label"))
    }
    @Test fun `session change rejects capture and discards prior reading`() {
        val sensor=LevelSensorSnapshot { 1000 };var session=7L
        val result=sensor.read(7,{session}){session=8;UsbProtocolReply(true,0x53,byteArrayOf(0))}
        assertFalse(result.getBoolean("available"));assertFalse(result.getBoolean("ok"));sensor.resetForSession(8);assertTrue(sensor.json().isNull("percent"))
    }
    @Test fun `leitura antiga iniciada apos troca nao regride sessao nova`() {
        val sensor=LevelSensorSnapshot { 1000 }
        sensor.resetForSession(8)
        val current=sensor.read(8,{8}){UsbProtocolReply(true,0x53,byteArrayOf(1))}
        assertEquals(8L,current.getLong("sessionId"))
        assertEquals("READY",current.getString("state"))
        val stale=sensor.read(7,{8}){
            fail("A leitura atrasada não pode chegar ao transporte USB")
            UsbProtocolReply(false)
        }
        assertEquals(8L,stale.getLong("sessionId"))
        assertEquals("READY",stale.getString("state"))
        assertTrue(stale.getBoolean("available"))
        assertEquals(current.getJSONArray("fields").toString(),stale.getJSONArray("fields").toString())
    }
    @Test fun `troca de sessao entre a ultima conferencia e a publicacao nao publica captura antiga`() {
        val sensor=LevelSensorSnapshot { 1000 };var answered=0;var resetDone=false
        // A troca de sessão chega por outra via (resetForSession) depois que a última conferência já passou.
        val currentSession:()->Long={ if(answered==5 && !resetDone){resetDone=true;sensor.resetForSession(8)};7L }
        sensor.read(7,currentSession){answered++;UsbProtocolReply(true,0x53,byteArrayOf(1))}
        val now=sensor.json()
        assertNotEquals("READY",now.optString("state"));assertNotEquals(7L,now.optLong("sessionId",-1));assertFalse(now.getBoolean("available"))
    }
}

