package com.omegas.prohub.calibration

import com.omegas.prohub.usb.UsbProtocolReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SerialWriteGuardTest {
    @Test
    fun `so um escritor por vez e quem nao detem nao consegue soltar`() {
        val guard = SerialWriteGuard()
        assertTrue(guard.tryAcquire(SerialWriteGuard.OWNER_K_FACTOR))
        assertFalse(guard.tryAcquire(SerialWriteGuard.OWNER_AUTOCAL))
        assertFalse(guard.tryAcquire(SerialWriteGuard.OWNER_K_MAP))
        assertEquals(SerialWriteGuard.OWNER_K_FACTOR, guard.holder())

        guard.release(SerialWriteGuard.OWNER_AUTOCAL) // não é o detentor: nada acontece
        assertEquals(SerialWriteGuard.OWNER_K_FACTOR, guard.holder())

        guard.release(SerialWriteGuard.OWNER_K_FACTOR)
        guard.release(SerialWriteGuard.OWNER_K_FACTOR) // soltar duas vezes é inofensivo
        assertNull(guard.holder())
        assertTrue(guard.tryAcquire(SerialWriteGuard.OWNER_AUTOCAL))
    }

    @Test
    fun `disputa concorrente tem exatamente um vencedor`() {
        val guard = SerialWriteGuard()
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val winners = AtomicInteger(0)
        val owners = listOf(
            SerialWriteGuard.OWNER_K_FACTOR, SerialWriteGuard.OWNER_K_MAP, SerialWriteGuard.OWNER_AUTOCAL,
        )
        repeat(8) { index ->
            pool.execute {
                start.await()
                if (guard.tryAcquire(owners[index % owners.size])) winners.incrementAndGet()
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(1, winners.get())
        assertTrue(guard.isHeld())
    }

    @Test
    fun `texto humano diz quem esta ocupando a ECU`() {
        assertTrue(SerialWriteGuard.label(SerialWriteGuard.OWNER_AUTOCAL).contains("AutoCal"))
        assertTrue(SerialWriteGuard.label(SerialWriteGuard.OWNER_K_FACTOR).contains("Curva K"))
        assertTrue(SerialWriteGuard.label(SerialWriteGuard.OWNER_K_MAP).contains("Mapa K"))
    }

    @Test
    fun `erro de transporte nao e erro da ECU`() {
        val timeout = UsbProtocolReply(false, error = "Timeout aguardando status")
        val usbDown = UsbProtocolReply(false, error = "USB desconectado")
        val checksum = UsbProtocolReply(false, status = 0x53, error = "Checksum RX inválido: esperado 01, recebido 02")
        val ecuRefused = UsbProtocolReply(false, status = 0xCA, error = "ECU retornou status 0xCA")
        val ackLike = UsbProtocolReply(true, status = 0x53)

        assertEquals(FailureKind.TRANSPORT, FailureKind.ofReply(timeout))
        assertEquals(FailureKind.TRANSPORT, FailureKind.ofReply(usbDown))
        assertEquals(FailureKind.TRANSPORT, FailureKind.ofReply(checksum))
        assertEquals(FailureKind.ECU, FailureKind.ofReply(ecuRefused))
        assertEquals(FailureKind.ECU, FailureKind.ofReply(ackLike))
    }

    @Test
    fun `classificacao por texto e por excecao`() {
        assertEquals(FailureKind.TRANSPORT, FailureKind.ofMessage("USB desconectado"))
        assertEquals(FailureKind.TRANSPORT, FailureKind.ofMessage("Timeout aguardando status"))
        assertEquals(FailureKind.ECU, FailureKind.ofMessage("ECU retornou status 0xCA"))
        assertEquals(FailureKind.APP, FailureKind.ofMessage("A curva da ECU mudou. Leia novamente antes de aplicar"))
        assertEquals(FailureKind.APP, FailureKind.ofMessage(null))

        assertEquals(FailureKind.ECU, FailureKind.of(CalibrationFailure("recusou", FailureKind.ECU)))
        assertEquals(FailureKind.TRANSPORT, FailureKind.of(CalibrationFailure("cabo", FailureKind.TRANSPORT)))
        assertEquals(FailureKind.APP, FailureKind.of(IllegalStateException("estado do app")))
    }
}
