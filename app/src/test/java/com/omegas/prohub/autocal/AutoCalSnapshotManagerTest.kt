package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.usb.UsbProtocolReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AutoCalSnapshotManagerTest {
    @Test
    fun `leitura completa publica snapshot e nunca e automatica`() {
        val session = AtomicLong(7L)
        val calls = AtomicInteger(0)
        val manager = manager(session = session) { request, _, _, _ ->
            calls.incrementAndGet()
            reply(request, byteArrayOf(1))
        }
        val started = manager.startRead(listOf(AutoCalProtocol.AUTO_CAL_ENABLE))
        assertTrue(started.getBoolean("started"))
        awaitIdle(manager)
        assertEquals(1, calls.get())
        assertEquals("READY", manager.statusJson().getString("state"))
        assertTrue(manager.latestSnapshotJson().getBoolean("manualOnly"))
        assertFalse(manager.latestSnapshotJson().getBoolean("automatic"))
        manager.close()
    }

    @Test
    fun `falha de um campo produz snapshot parcial`() {
        val session = AtomicLong(8L)
        val calls = AtomicInteger(0)
        val manager = manager(session = session) { request, _, _, _ ->
            if (calls.getAndIncrement() == 0) reply(request, byteArrayOf(1))
            else UsbProtocolReply(false, error = "timeout", request = request)
        }
        manager.startRead(listOf(AutoCalProtocol.AUTO_CAL_ENABLE, AutoCalProtocol.MUL_ACT))
        awaitIdle(manager)
        assertEquals("READY_PARTIAL", manager.statusJson().getString("state"))
        assertTrue(manager.latestSnapshotJson().getBoolean("partial"))
        assertEquals(1, manager.latestSnapshotJson().getInt("validFieldCount"))
        manager.close()
    }

    @Test
    fun `outra calibracao impede iniciar`() {
        val busy = AtomicBoolean(true)
        val manager = manager(otherBusy = busy)
        val result = manager.startRead(listOf(AutoCalProtocol.AUTO_CAL_ENABLE))
        assertFalse(result.getBoolean("ok"))
        assertTrue(result.getString("error").contains("Outra operação"))
        manager.close()
    }

    @Test
    fun `mudanca de sessao descarta leitura obsoleta`() {
        val session = AtomicLong(9L)
        val manager = manager(session = session) { request, _, _, _ ->
            session.incrementAndGet()
            reply(request, byteArrayOf(1))
        }
        manager.startRead(listOf(AutoCalProtocol.AUTO_CAL_ENABLE))
        awaitIdle(manager)
        assertEquals("STALE_SESSION", manager.statusJson().getString("state"))
        assertFalse(manager.latestSnapshotJson().getBoolean("available"))
        manager.close()
    }

    @Test
    fun `cancelamento nao publica snapshot`() {
        val session = AtomicLong(10L)
        val manager = manager(session = session) { request, _, _, _ ->
            Thread.sleep(30L)
            reply(request, byteArrayOf(1))
        }
        manager.startRead(listOf(AutoCalProtocol.AUTO_CAL_ENABLE, AutoCalProtocol.ACQUIRED_ZONES_PETROL))
        manager.cancel()
        awaitIdle(manager)
        assertEquals("CANCELLED", manager.statusJson().getString("state"))
        assertFalse(manager.latestSnapshotJson().getBoolean("available"))
        manager.close()
    }

    private fun manager(
        session: AtomicLong = AtomicLong(1L),
        connected: AtomicBoolean = AtomicBoolean(true),
        otherBusy: AtomicBoolean = AtomicBoolean(false),
        transaction: (ByteArray, String, Int, Long) -> UsbProtocolReply = { request, _, _, _ ->
            reply(request, byteArrayOf(1))
        },
    ) = AutoCalSnapshotManager(
        isConnected = connected::get,
        currentSessionId = session::get,
        otherCalibrationBusy = otherBusy::get,
        transaction = transaction,
    )

    private fun reply(request: ByteArray, payload: ByteArray) = UsbProtocolReply(
        ok = true,
        status = Mp48Protocol.STATUS_ACK,
        payload = payload,
        request = request,
        echo = request,
    )

    private fun awaitIdle(manager: AutoCalSnapshotManager) {
        repeat(200) {
            if (!manager.isBusy()) return
            Thread.sleep(5L)
        }
        throw AssertionError("Leitura AutoCal não finalizou")
    }
}
