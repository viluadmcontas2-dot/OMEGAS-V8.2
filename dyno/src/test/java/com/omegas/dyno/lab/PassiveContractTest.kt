package com.omegas.dyno.lab

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveContractTest {
    private val main = File("src/main").takeIf { it.isDirectory } ?: File("dyno/src/main")

    @Test fun noProductionCallSitesOutsideReaders() {
        val readers = setOf("ObdBluetoothReader.kt", "Mp48UsbReader.kt", "PassiveObdBackgroundService.kt")
        val forbidden = Regex(
            """\b(ObdBluetoothReader|Mp48UsbReader|UsbManager|UsbSerialPort|BluetoothAdapter|BluetoothSocket|""" +
                """requestPermissions?|requestUsb|openDevice|registerReceiver|onRequestPermissionsResult)\b"""
        )
        val sources = main.resolve("java").walkTopDown().filter {
            it.isFile && it.extension in setOf("kt", "java") && it.name !in readers
        }.toList()
        assertTrue(sources.any { it.name == "DynoActivity.kt" })
        for (source in sources) {
            val reference = forbidden.find(source.readText())
            assertTrue("${source.name}: ${reference?.value}", reference == null)
        }
    }

    @Test fun bridgeConnectionMethodsOnlyReturnPassiveDenial() {
        val activity = main.resolve("java/com/omegas/dyno/lab/DynoActivity.kt").readText()
        val expected = listOf(
            """@JavascriptInterface fun connectObd(id: String): String = passiveOnly()""",
            """@JavascriptInterface fun connectMp48(): String = passiveOnly()""",
            """@JavascriptInterface fun listObdDevices(): String = "[]"""",
            """        JSONObject().put("ok", false).put("error", "Somente recepção passiva; sem emitir comandos").toString()"""
        )
        val lines = activity.lineSequence().map(String::trim).toSet()
        for (line in expected) {
            assertTrue(line, line.trim() in lines)
        }
    }
}
