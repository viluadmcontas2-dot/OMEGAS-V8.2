from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"
DOC = (ROOT / "docs/handoff/EVENTOS-SEM-TEMPO.md").read_text("utf-8")
SERVICE = (MAIN / "java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text("utf-8")
PROBE = [MAIN / "java/com/omegas/prohub/autocal/BufferWriteProbe.kt", MAIN / "java/com/omegas/prohub/autocal/BufferWriteProbeCore.kt"]
UI = MAIN / "assets/ui"
JAVA = MAIN / "java"
SET_INTERVAL_OK = {"core/scheduler.js"}
READ_EVENTS = (MAIN / "java/com/omegas/prohub/service/CalibrationReadEvents.kt").read_text("utf-8")
POLICY = (MAIN / "java/com/omegas/prohub/service/ServiceEventPolicy.kt")

# Fonte de relógio e comparação: qualquer constante Long (ou literal Long) comparada num arquivo que lê relógio precisa
# estar no inventário do EVENTOS-SEM-TEMPO.md como `Arquivo.kt` `NOME`. Renomear a constante exige atualizar o inventário.
CLOCK = re.compile(r"System\.currentTimeMillis\(\)|System\.nanoTime\(\)|SystemClock\.(?:elapsedRealtime|uptimeMillis)\(\)|\bclock\(\)|\b\w*ElapsedMs\b")
COMPARE = re.compile(r"(?<![=!<>-])(?:<=|>=|<|>)(?![=>])")


def clock_constants():
    found = {}
    for arquivo in sorted(JAVA.rglob("*.kt")):
        texto = arquivo.read_text("utf-8")
        if not CLOCK.search(texto):
            continue
        longs = {n for n in re.findall(r"\bconst\s+val\s+([A-Z][A-Z0-9_]*)\s*(?::\s*Long\s*)?=\s*[\d_]+L\b", texto)
                 if not n.endswith("_BYTES")}
        for numero, linha in enumerate(texto.splitlines(), 1):
            codigo = linha.split("//")[0]
            if not COMPARE.search(codigo):
                continue
            for nome in longs:
                if re.search(rf"\b{nome}\b", codigo) and not re.search(rf"const\s+val\s+{nome}\b", codigo):
                    found.setdefault((arquivo.name, nome), numero)
            for m in re.finditer(r"[-+]\s*\w+\s*(?:<=|>=|<|>)\s*([\d_]+L)\b", codigo):
                found.setdefault((arquivo.name, m.group(1)), numero)
    return found


class SemTempoComoGatilho(unittest.TestCase):
    def test_servico_sem_constantes_de_prazo(self):
        for proibido in ("12_000L", "600_000L", "scheduleWithFixedDelay", "scheduleAtFixedRate", "autoReadLastOkAtMs"):
            self.assertNotIn(proibido, SERVICE)

    def test_sonda_sem_pausa_entre_leituras(self):
        for arquivo in PROBE:
            texto = arquivo.read_text("utf-8")
            self.assertNotIn("SETTLE_PAUSE_MS", texto, arquivo.name)
            self.assertNotIn("pause(", texto, arquivo.name)

    def test_set_interval_so_na_lista_documentada(self):
        for arquivo in UI.rglob("*.js"):
            rel = arquivo.relative_to(UI).as_posix()
            if rel.startswith("vendor/"):
                continue
            if re.search(r"\bsetInterval\s*\(", arquivo.read_text("utf-8")):
                self.assertIn(rel, SET_INTERVAL_OK, f"{rel} usa setInterval fora da lista")
                self.assertIn(f"ui/{rel}", DOC, f"{rel} não está justificado em EVENTOS-SEM-TEMPO.md")

    def test_js_sem_prazos_de_decisao(self):
        for arquivo in UI.rglob("*.js"):
            rel = arquivo.relative_to(UI).as_posix()
            if rel.startswith("vendor/"):
                continue
            texto = arquivo.read_text("utf-8")
            for m in re.finditer(r"setTimeout\(.*", texto):
                linha = m.group(0)
                if "toastTimer" in texto and rel == "app.js" and "3600" in linha:
                    continue
                self.assertNotRegex(linha, r",\s*(?:[1-9]\d{2,}|\d+_\d{3})\s*\)", f"{rel}: {linha}")
            self.assertNotRegex(texto, r"AUTO_SYNC_MS|OPERATION_TIMEOUT_MS|FLUSH_EVERY_MS", rel)

    def test_gravacao_confirmada_nao_pede_releitura(self):
        # Laço infinito: KWriteManager.readFullMap chama onConfirmedWrite ao fim de toda leitura OK.
        self.assertNotIn('requestCalibrationRead("WRITE_CONFIRMED")', SERVICE)
        self.assertIn("trigger == WRITE_CONFIRMED", READ_EVENTS)

    def test_sonda_do_autocal_por_contagem_de_quadros(self):
        self.assertRegex(POLICY.read_text("utf-8"), r"AUTOCAL_TICK_FRAMES = 4\b")
        self.assertIn("FrameCountTrigger(ServiceEventPolicy.AUTOCAL_TICK_FRAMES)", SERVICE)
        corpo = SERVICE[SERVICE.index("private fun consumeEngineEvent"):SERVICE.index("private fun consumeGpsUpdate")]
        self.assertRegex(corpo, r"if \(autoCalDue\) \{\s*autoCalTick\(\)")
        self.assertNotIn("scheduler.execute", corpo, "fila do serviço só por executeOnScheduler (respeita o encerramento)")
        self.assertIn("AUTOCAL_TICK_FRAMES", DOC)

    def test_health_tick_na_borda_livre_e_reinicio_limitado(self):
        self.assertEqual(SERVICE.count("onBusyChanged = { onCalibrationBusyChanged() }"), 2)
        self.assertIn("calibrationIdle.observe(", SERVICE)
        self.assertIn("restartBudget.tryConsume(", SERVICE)
        self.assertIn("restartBudget.onValidFrame(", SERVICE)
        self.assertIn("FrameCountTrigger(ServiceEventPolicy.FULL_SNAPSHOT_FRAMES)", SERVICE)

    def test_sonda_espera_quadros_entre_leituras(self):
        core = PROBE[1].read_text("utf-8")
        self.assertIn("io.awaitFrames(FRAMES_BETWEEN_READS)", core)
        self.assertIn("Mp48Protocol.CMD_TELEMETRY", PROBE[0].read_text("utf-8"))

    def test_constantes_de_tempo_inventariadas(self):
        encontrados = clock_constants()
        self.assertTrue(encontrados, "a varredura deve achar ao menos as exceções conhecidas")
        faltando = [f"{arquivo}:{linha} {nome}" for (arquivo, nome), linha in sorted(encontrados.items())
                    if f"`{arquivo}` `{nome}`" not in DOC]
        self.assertEqual([], faltando, "constante/literal de tempo fora do inventário do EVENTOS-SEM-TEMPO.md")
        self.assertNotIn(("EquivalenceLedger.kt", "EVIDENCE_CACHE_MS"), encontrados)

    def test_documento_lista_gatilhos(self):
        self.assertIn("Gatilhos", DOC)
        self.assertIn("Exceções inerentes", DOC)


if __name__ == "__main__":
    unittest.main()
