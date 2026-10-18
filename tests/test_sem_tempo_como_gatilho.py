from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"
DOC = (ROOT / "docs/handoff/EVENTOS-SEM-TEMPO.md").read_text("utf-8")
SERVICE = (MAIN / "java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text("utf-8")
PROBE = [MAIN / "java/com/omegas/prohub/autocal/BufferWriteProbe.kt", MAIN / "java/com/omegas/prohub/autocal/BufferWriteProbeCore.kt"]
UI = MAIN / "assets/ui"
SET_INTERVAL_OK = {"core/scheduler.js"}


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

    def test_documento_lista_gatilhos(self):
        self.assertIn("Gatilhos", DOC)
        self.assertIn("Exceções inerentes", DOC)


if __name__ == "__main__":
    unittest.main()
