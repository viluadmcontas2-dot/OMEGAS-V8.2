package com.omegas.prohub.learning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CoalescedSnapshotWriterTest {
    @Test
    fun `slow storage coalesces intermediate snapshots and persists the newest state`() {
        val directory = Files.createTempDirectory("omegas-snapshot-writer").toFile()
        val target = directory.resolve("evidence.json")
        val firstWriteStarted = CountDownLatch(1)
        val releaseFirstWrite = CountDownLatch(1)
        val first = AtomicBoolean(true)
        val writer = CoalescedSnapshotWriter(
            target = target,
            threadName = "snapshot-writer-test",
            beforeWrite = {
                if (first.compareAndSet(true, false)) {
                    firstWriteStarted.countDown()
                    releaseFirstWrite.await(2L, TimeUnit.SECONDS)
                }
            },
        )
        try {
            assertTrue(writer.submit(JSONObject().put("sequence", 1).toString()))
            assertTrue(firstWriteStarted.await(1L, TimeUnit.SECONDS))
            (2..100).forEach { sequence ->
                assertTrue(writer.submit(JSONObject().put("sequence", sequence).toString()))
            }
            releaseFirstWrite.countDown()
            assertTrue(writer.flush(5_000L))

            assertEquals(100, JSONObject(target.readText()).getInt("sequence"))
            val metrics = writer.metricsJson()
            assertEquals(100L, metrics.getLong("requests"))
            assertTrue(metrics.getLong("writes") <= 3L)
            assertTrue(metrics.getLong("coalesced") >= 97L)
            assertEquals(0L, metrics.getLong("failures"))
        } finally {
            releaseFirstWrite.countDown()
            writer.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `close flushes the latest accepted snapshot`() {
        val directory = Files.createTempDirectory("omegas-snapshot-close").toFile()
        val target = directory.resolve("evidence.json")
        val writer = CoalescedSnapshotWriter(target, "snapshot-close-test")
        try {
            assertTrue(writer.submit(JSONObject().put("sequence", 1).toString()))
            assertTrue(writer.submit(JSONObject().put("sequence", 2).toString()))
            writer.close()
            assertEquals(2, JSONObject(target.readText()).getInt("sequence"))
        } finally {
            writer.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `payload construction is coalesced before expensive serialization`() {
        val directory = Files.createTempDirectory("omegas-snapshot-provider").toFile()
        val target = directory.resolve("evidence.json")
        val firstWriteStarted = CountDownLatch(1)
        val releaseFirstWrite = CountDownLatch(1)
        val first = AtomicBoolean(true)
        val providerCalls = AtomicInteger(0)
        val writer = CoalescedSnapshotWriter(
            target = target,
            threadName = "snapshot-provider-test",
            beforeWrite = {
                if (first.compareAndSet(true, false)) {
                    firstWriteStarted.countDown()
                    releaseFirstWrite.await(2L, TimeUnit.SECONDS)
                }
            },
        )
        try {
            assertTrue(writer.request {
                providerCalls.incrementAndGet()
                JSONObject().put("sequence", 1).toString()
            })
            assertTrue(firstWriteStarted.await(1L, TimeUnit.SECONDS))
            (2..100).forEach { sequence ->
                assertTrue(writer.request {
                    providerCalls.incrementAndGet()
                    JSONObject().put("sequence", sequence).toString()
                })
            }
            releaseFirstWrite.countDown()
            assertTrue(writer.flush(5_000L))

            assertEquals(100, JSONObject(target.readText()).getInt("sequence"))
            assertTrue("coalescing must also skip most payload factories", providerCalls.get() <= 3)
        } finally {
            releaseFirstWrite.countDown()
            writer.close()
            directory.deleteRecursively()
        }
    }
}
