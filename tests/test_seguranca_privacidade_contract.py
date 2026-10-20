"""Segurança e privacidade do app vendido a leigos (revisão de código 2026-10-20).

Contrato de texto lendo o fonte Kotlin/Manifest. A prova de comportamento do arquivo privado
é PrivateSessionArchiveTest (JVM); WebView/ciclo de vida só o Gradle/emulador provam de vez.
"""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java"
MAIN = (JAVA / "com/omegas/prohub/MainActivity.kt").read_text(encoding="utf-8")
MIRROR = (JAVA / "com/omegas/prohub/diagnostics/DocumentsSessionMirror.kt").read_text(encoding="utf-8")
ARCHIVE_PATH = JAVA / "com/omegas/prohub/diagnostics/PrivateSessionArchive.kt"
RECORDER = (JAVA / "com/omegas/prohub/diagnostics/SessionRecorder.kt").read_text(encoding="utf-8")
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
GRADLE = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
A = "{http://schemas.android.com/apk/res/android}"

# Regra 8 do AGENTS.md: comandos de leitura/escrita da central não mudam (não auditados aqui).
FROZEN = {
    "UsbSerialManager.kt", "ResponseDrivenEcuEngine.kt", "AutoCalProtocol.kt",
    "KFactorManager.kt", "KWriteManager.kt", "AutoCalNativeActionManager.kt",
}


def kotlin_sources():
    for path in sorted(JAVA.rglob("*.kt")):
        if path.name not in FROZEN:
            yield path, path.read_text(encoding="utf-8")


class WebViewRestritaTest(unittest.TestCase):
    def settings_block(self):
        return MAIN[MAIN.index("webView.settings.apply {"):MAIN.index("jsBridge = HubJavascriptBridge(this)")]

    def test_sem_acesso_a_arquivos_fora_dos_assets(self):
        block = self.settings_block()
        # file:///android_asset/ continua legível com allowFileAccess=false (a doc do Android garante:
        # "this doesn't affect access to assets and resources"); o resto do disco não.
        self.assertIn("allowFileAccess = false", block)
        self.assertNotIn("allowFileAccess = true", MAIN)
        self.assertIn("allowContentAccess = false", block)
        self.assertIn("allowFileAccessFromFileURLs = false", block)
        self.assertIn("allowUniversalAccessFromFileURLs = false", block)
        self.assertIn("MIXED_CONTENT_NEVER_ALLOW", block)
        self.assertIn('webView.loadUrl("file:///android_asset/ui/index.html")', MAIN)

    def test_navegacao_so_dentro_da_ui_empacotada(self):
        self.assertIn('APP_UI_ORIGIN = "file:///android_asset/ui/"', MAIN)
        nav = MAIN[MAIN.index("override fun shouldOverrideUrlLoading"):]
        nav = nav[:nav.index("\n            }\n")]
        self.assertIn("isAppUiUrl(", nav)
        self.assertNotIn('uri.scheme != "file"', MAIN)
        self.assertIn("override fun shouldInterceptRequest", MAIN)
        guard = MAIN[MAIN.index("fun isAppUiUrl("):]
        guard = guard[:guard.index("\n    }\n")]
        self.assertIn("startsWith(APP_UI_ORIGIN)", guard)
        self.assertIn('".."', guard)


class CicloDeVidaTest(unittest.TestCase):
    def test_sem_coroutine_scope_solto(self):
        for path, src in kotlin_sources():
            self.assertNotRegex(src, r"CoroutineScope\(\s*(kotlinx\.coroutines\.)?Dispatchers[^)]*\)\s*\.launch", f"{path.name}: escopo solto")
            self.assertNotIn("GlobalScope", src, path.name)

    def test_tarefas_da_tela_amarradas_ao_ciclo_de_vida(self):
        # Um escopo por tela, cancelado em onDestroy (sem dependência nova no Gradle).
        self.assertIn("private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)", MAIN)
        self.assertIn("screenScope.launch {", MAIN)
        destroy = MAIN[MAIN.index("override fun onDestroy()"):MAIN.index("super.onDestroy()")]
        self.assertIn("screenScope.cancel()", destroy)
        self.assertEqual(1, MAIN.count("CoroutineScope("))


class CatchVazioTest(unittest.TestCase):
    EMPTY = re.compile(r"catch\s*\([^)]*\)\s*\{(\s*)\}")

    def test_nenhum_catch_mudo(self):
        mudos = []
        for path, src in kotlin_sources():
            for match in self.EMPTY.finditer(src):
                line = src.count("\n", 0, match.start()) + 1
                mudos.append(f"{path.relative_to(ROOT)}:{line}")
        self.assertEqual([], mudos, "catch vazio: registre (log/estado) ou explique em comentário curto")

    def test_falhas_de_estado_viram_estado_legivel(self):
        # Sessão: manifesto, gravação em flash e publicação viram lastError visível no status.
        self.assertIn('noteFailure("salvar o manifesto"', RECORDER)
        self.assertIn('noteFailure("gravar no armazenamento"', RECORDER)
        self.assertIn('noteFailure("arquivar a sessão"', RECORDER)
        stall = (JAVA / "com/omegas/prohub/autocal/StallWatch.kt").read_text(encoding="utf-8")
        self.assertIn('.put("saveError"', stall)
        monitor = (JAVA / "com/omegas/prohub/autocal/NativeAutoCalMonitor.kt").read_text(encoding="utf-8")
        self.assertIn("onListenerError", monitor)
        self.assertGreaterEqual(monitor.count("listenerFailed("), 5)
        service = (JAVA / "com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
        self.assertIn("onListenerError =", service)
        self.assertIn('"WAKELOCK"', service)


class PrivacidadeTest(unittest.TestCase):
    def test_sessoes_ficam_no_armazenamento_privado_do_app(self):
        self.assertTrue(ARCHIVE_PATH.is_file())
        archive = ARCHIVE_PATH.read_text(encoding="utf-8")
        self.assertNotIn("android.", archive, "núcleo precisa ser JVM puro (testável)")
        self.assertIn('PRIVATE_ROOT = "Omegas"', MIRROR)
        self.assertIn("getExternalFilesDir", MIRROR)
        self.assertIn("filesDir", MIRROR)
        self.assertNotIn("PUBLIC_ROOT", MIRROR + RECORDER)
        # Escrever em Download/ (público) acabou; o único uso restante é ler/migrar o que já estava lá.
        self.assertNotIn("resolver.insert", MIRROR)
        self.assertNotIn("openOutputStream", MIRROR)
        self.assertIn("migrateLegacyPublicCopies", MIRROR)
        self.assertIn('.put("survivesAppDataClear", false)', MIRROR)
        self.assertIn('.put("private", true)', MIRROR)
        recover = RECORDER[RECORDER.index("fun recoverDocumentsMirrorAsync"):RECORDER.index("fun close()")]
        self.assertIn("migrateLegacyPublicCopies()", recover)

    def test_nada_vai_para_servidor(self):
        root = ET.parse(MANIFEST).getroot()
        perms = {n.attrib.get(A + "name") for n in root.findall("uses-permission")}
        self.assertNotIn("android.permission.INTERNET", perms)
        for path, src in kotlin_sources():
            self.assertNotRegex(src, r"HttpURLConnection|OkHttp|java\.net\.URL\(|Socket\(", path.name)

    def test_manifest_minimo(self):
        root = ET.parse(MANIFEST).getroot()
        perms = {n.attrib.get(A + "name") for n in root.findall("uses-permission")}
        for unused in (
            "android.permission.BLUETOOTH", "android.permission.BLUETOOTH_ADMIN",
            "android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN",
        ):
            self.assertNotIn(unused, perms, "Bluetooth não é usado pelo app")
        features = {n.attrib.get(A + "name") for n in root.findall("uses-feature")}
        self.assertNotIn("android.hardware.bluetooth", features)
        self.assertNotIn("requestBluetoothPermission", MAIN)
        app = root.find("application")
        self.assertEqual("false", app.attrib.get(A + "allowBackup"))
        self.assertEqual("false", app.attrib.get(A + "usesCleartextTraffic"))
        self.assertNotIn(A + "debuggable", app.attrib, "debuggable fica com o Gradle (false no release)")
        self.assertNotRegex(GRADLE, r"isDebuggable\s*=\s*true")
        exported = [
            n.attrib.get(A + "name") for n in app
            if n.attrib.get(A + "exported") == "true"
        ]
        self.assertEqual([".MainActivity"], exported, "só a tela principal (lançador/USB) é exportada")


if __name__ == "__main__":
    unittest.main()
