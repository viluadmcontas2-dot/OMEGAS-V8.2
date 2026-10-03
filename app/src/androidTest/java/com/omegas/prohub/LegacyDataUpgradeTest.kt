package com.omegas.prohub

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omegas.prohub.storage.AppPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** APK novo sobre dados antigos: o serviço limpa o aprendizado antigo uma vez e o app abre. */
@RunWith(AndroidJUnit4::class)
class LegacyDataUpgradeTest {
    @Test fun apkNovoSobreDadosAntigos() {
        val paths = AppPaths(ApplicationProvider.getApplicationContext())
        val legacy = File(paths.runtimeRoot, "native_learning_state_mp48_v4.json").apply { parentFile!!.mkdirs(); writeText("{}") }
        val quarantine = File(paths.runtimeRoot, "learning_quarantine/x.json").apply { parentFile!!.mkdirs(); writeText("{}") }
        val checkpoints = File(paths.runtimeBackupsRoot, "learning_checkpoints/a.omegas").apply { parentFile!!.mkdirs(); writeText("x") }
        val autopilotBody = """{"format":"omegas-refinement-autopilot-v1"}"""
        val autopilot = File(paths.runtimeRoot, "refinement_autopilot.json").apply { writeText(autopilotBody) }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val deadline = SystemClock.elapsedRealtime() + 15_000L
        while ((legacy.exists() || quarantine.exists() || checkpoints.exists()) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(200L)
        assertFalse(legacy.exists()); assertFalse(quarantine.parentFile!!.exists()); assertFalse(checkpoints.parentFile!!.exists())
        assertEquals(autopilotBody, autopilot.readText())
        assertEquals(Lifecycle.State.RESUMED, scenario.state)
    }
}
