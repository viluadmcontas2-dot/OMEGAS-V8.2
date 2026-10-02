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
}
