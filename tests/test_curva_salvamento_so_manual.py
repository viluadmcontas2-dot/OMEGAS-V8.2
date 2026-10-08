"""curva-salvamento-so-manual — TRAVA PERMANENTE (regra 15 do AGENTS.md; dono, 2026-10-08).

Salvar a Curva K é SEMPRE manual: arquivo em Download/Omegas/Curva só quando o dono toca em Salvar
(curveSaveButton -> curveBackupSave -> saveBackup -> startCurveBackup -> saveCurrentBackup). Nome didático.
Gravar, Resetar, Desfazer, voltar à aba, timer e escrita em lote NUNCA criam arquivo visível da curva.
A foto do Desfazer (PREWRITE-) fica só no armazenamento privado do app.
Par Kotlin: KFactorCurveFileNameTest. Mutantes: curva-* (tools/wiring/run_ui_mutants.py). Nenhum agente pode remover.
"""
import os
import re
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
ROOT = Path(os.environ.get("CURVA_ROOT") or os.environ.get("RESET_ROOT") or REPO)
UI = ROOT / "app/src/main/assets/ui"
KT = ROOT / "app/src/main/java/com/omegas/prohub"


def read(path):
    return Path(path).read_text(encoding="utf-8")


CURVE = read(UI / "screens/curve.js")
MANAGER = read(KT / "calibration/KFactorManager.kt")
NAMER = read(KT / "calibration/KFactorCurveFileName.kt")
MIRROR = read(KT / "diagnostics/DocumentsSessionMirror.kt")
SERVICE = read(KT / "service/TelemetryForegroundService.kt")
BRIDGE = read(KT / "web/CalibrationOperationsBridge.kt")


def js_body(source, signature):
    """Corpo de um método de classe JS (`  nome(args) {` até a chave de mesmo nível)."""
    start = source.index(signature)
    open_at = source.index("{", start)
    depth = 0
    for i in range(open_at, len(source)):
        depth += {"{": 1, "}": -1}.get(source[i], 0)
        if depth == 0:
            return source[open_at:i + 1]
    raise AssertionError("corpo não fechado: " + signature)


def kt_body(source, signature):
    return js_body(source, signature)


class CurvaSalvamentoSoManual(unittest.TestCase):
    # ---- 1. só o botão Salvar cria arquivo -------------------------------------------------------------
    def test_ui_so_o_handler_do_salvar_chama_o_arquivo(self):
        for path in sorted(UI.rglob("*.js")):
            text = read(path)
            n = len(re.findall(r"startCurveBackup\s*\(", text))
            if path.name == "native-api.js":
                self.assertEqual(n, 1, "native-api.js só define startCurveBackup")
            elif path.name == "curve.js":
                self.assertEqual(n, 1, "curve.js: startCurveBackup só no saveBackup")
            else:
                self.assertEqual(n, 0, f"{path.name} não pode criar arquivo da Curva K")
        self.assertIn("startCurveBackup", js_body(CURVE, "    saveBackup()"))
        self.assertEqual(len(re.findall(r"\.saveBackup\s*\(", CURVE)), 1)
        self.assertIn("addEventListener('click', () => this.saveBackup())", CURVE)
        self.assertRegex(CURVE, r"getElementById\('curveSaveButton'\)\?\.addEventListener\('click', \(\) => document\.getElementById\('curveBackupSave'\)\?\.click\(\)\)")
        self.assertIn("'Curva salva manualmente'", js_body(CURVE, "    saveBackup()"))

    def test_gravar_resetar_desfazer_e_entrada_nao_chamam_a_rotina_de_arquivo(self):
        for sig in ("    resetCurve()", "    undoCurve()", "    writeRestore()", "    startResetWrite()", "    onEnter(", "    onResume(",
                    "    prepareRestore(", "    refreshBackups()", "    poll()", "    startRead("):
            if sig not in CURVE:
                continue
            body = js_body(CURVE, sig)
            for banned in ("startCurveBackup", "saveBackup", "curveBackupSave", "curveSaveButton", "publishRootFile"):
                self.assertNotIn(banned, body, f"{sig.strip()} não pode criar arquivo ({banned})")
        for sig in ("    resetCurve()", "    startResetWrite()", "    writeRestore()", "    writeProposals()"):
            if sig in CURVE:
                self.assertNotIn("'save'", js_body(CURVE, sig))
        self.assertNotIn("reset-photo", CURVE, "reset não tira mais foto visível antes de zerar")
        self.assertNotIn("Antes do reset", CURVE)

    def test_sem_timer_nem_autosave_de_curva(self):
        self.assertNotRegex(CURVE, r"setInterval\s*\(", "a Curva K não tem timer")
        for match in re.finditer(r"setTimeout\s*\(", CURVE):
            window = CURVE[match.start():match.start() + 400]
            self.assertNotRegex(window, r"saveBackup|startCurveBackup|curveBackupSave|curveSaveButton", "timer chamando salvar")
        for name in ("saveKFactorBackup", "saveCurrentBackup", "startCurveBackup"):
            self.assertNotRegex(MANAGER + SERVICE, r"(Timer|schedule\w*|postDelayed|Handler\()[^\n]*" + name, f"autosave via {name}")
        self.assertNotRegex(MANAGER, r"scheduleAtFixedRate|scheduleWithFixedDelay|java\.util\.Timer|postDelayed|AlarmManager")

    def test_kotlin_uma_unica_cadeia_do_botao_ate_o_arquivo_publico(self):
        calls = lambda text, name: len(re.findall(re.escape(name) + r"\s*\(", text))
        self.assertEqual(calls(MANAGER, "publishManualBackup"), 1, "publishManualBackup só em saveCurrentBackup")
        self.assertIn("publishManualBackup(", kt_body(MANAGER, "    fun saveCurrentBackup("))
        self.assertEqual(calls(SERVICE, "saveCurrentBackup"), 1)
        self.assertEqual(calls(SERVICE, "saveKFactorBackup"), 1)  # definição
        self.assertEqual(calls(BRIDGE, "saveKFactorBackup"), 1)
        self.assertIn("saveKFactorBackup", kt_body(BRIDGE, "    fun startCurveBackup("))
        # nenhum outro arquivo do app aciona o salvamento da curva
        for path in sorted(KT.rglob("*.kt")):
            if path.name in ("KFactorManager.kt", "TelemetryForegroundService.kt", "CalibrationOperationsBridge.kt"):
                continue
            text = read(path)
            for name in ("saveCurrentBackup", "saveKFactorBackup", "startCurveBackup"):
                self.assertNotRegex(text, name + r"\s*\(", path.name)
        # publicação em Download/Omegas/Curva: um único chamador de publishRootFile, e é o do botão
        publishers = [p.name for p in KT.rglob("*.kt") if re.search(r"publishRootFile\s*\(", read(p)) and p.name != "DocumentsSessionMirror.kt"]
        self.assertEqual(publishers, ["TelemetryForegroundService.kt"])
        self.assertRegex(SERVICE, r"publishManualBackup\s*=\s*\{ file -> documentsMirror\.publishRootFile\(file, KFactorCurveFileName\.PUBLIC_SUBFOLDER\) \}")

    def test_gravacao_em_lote_so_guarda_foto_privada_do_desfazer(self):
        photos = re.findall(r"writeManualPhoto\(", MANAGER)
        self.assertEqual(len(photos), 3, "definição + salvar manual + foto privada do lote")
        batch = kt_body(MANAGER, "    private fun executeBatch(")
        self.assertIn('writeManualPhoto(cachedAxis, ecuBefore, "Antes de gravar", preWrite = true)', batch)
        for fn in ("    private fun executeBatch(", "    fun startBatchWrite(", "    fun startResetToNeutral(", "    fun startRestoreWrite("):
            body = kt_body(MANAGER, fn)
            for banned in ("publishManualBackup", "publishRootFile", "saveCurrentBackup", "KFactorCurveFileName"):
                self.assertNotIn(banned, body, f"{fn.strip()} não pode criar arquivo visível ({banned})")
        manual = kt_body(MANAGER, "    fun saveCurrentBackup(")
        self.assertNotIn("preWrite = true", manual)
        self.assertIn('val namePrefix = if (preWrite) "PREWRITE" else "MANUAL"', MANAGER)

    # ---- 2. nome e pasta -----------------------------------------------------------------------------------
    def test_nome_didatico_e_pasta_curva(self):
        self.assertIn('const val PUBLIC_SUBFOLDER = "Curva"', NAMER)
        self.assertIn('const val SUFFIX = "salva manualmente"', NAMER)
        self.assertIn("\"dd-MM-yyyy HH'h'mm'm'ss's'\"", NAMER)
        self.assertIn('"Curva K - ${format.format(Date(createdAtMs))} - $pointCount pontos - $SUFFIX.json"', NAMER)
        self.assertIn('const val PUBLIC_ROOT = "Download/Omegas"', MIRROR)
        self.assertIn("KFactorCurveFileName.of(createdAt, KFactorProtocol.POINT_COUNT)", MANAGER)
        self.assertIn("KFactorCurveFileName.PUBLIC_SUBFOLDER", MANAGER)
        # o nome gerado (réplica Python do formato) segue o padrão e não tem caractere inválido
        sample = "Curva K - 08-10-2026 19h42m07s - 30 pontos - salva manualmente.json"
        self.assertRegex(sample, r"^Curva K - \d{2}-\d{2}-\d{4} \d{2}h\d{2}m\d{2}s - \d+ pontos - salva manualmente\.json$")
        self.assertIsNone(re.search(r'[\\/:*?"<>|]', sample))
        # o nome do arquivo público não é o nome interno MANUAL-/PREWRITE-
        self.assertNotRegex(NAMER, r"MANUAL-|PREWRITE")
        publish = kt_body(MANAGER, "    fun saveCurrentBackup(")
        self.assertIn("File(staging, publicName)", publish)

    def test_pasta_curva_chega_ao_armazenamento_publico(self):
        body = kt_body(MIRROR, "    fun publishRootFile(")
        self.assertIn('"$PUBLIC_ROOT/$folder"', body)
        self.assertIn('"Omegas/$folder"', body)
        self.assertIn('syncScopedAt(source, "$publicDir/")', body)

    def test_ui_diz_a_pasta_certa_e_nao_avisa_foto_salva_sem_toque(self):
        html = read(UI / "index.html")
        self.assertIn('id="curveSaveButton"', html)
        self.assertIn("Downloads/Omegas/Curva", html)
        self.assertNotIn("A foto da curva atual foi salva antes", CURVE)
        self.assertNotIn("Salvando a foto da curva antes de zerar", CURVE)


if __name__ == "__main__":
    unittest.main()
