"""Grafo produtor <-> consumidor (Lote W, parte 1).

Falha quando:
  * a UI LE uma chave que nenhum produtor Kotlin emite (consumidor sem alimentacao: mostra "—"/0 para sempre);
  * o Kotlin EMITE (nas superficies entregues a WebView) uma chave que ninguem le (produtor ao vento),
    salvo se listada com MOTIVO em tests/wiring/allowlist.json;
  * o JS chama um metodo de ponte que o Kotlin nao expoe (ou o Kotlin expoe e ninguem chama).
Defeitos conhecidos ficam em `defects`/`consumer_defects`: o teste exige que CONTINUEM violando
(quando o defeito for corrigido, o teste manda remover a entrada).
"""
import json
import os
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools" / "wiring"))
import extract_keys as E  # noqa: E402

ALLOW = json.loads((ROOT / "tests/wiring/allowlist.json").read_text("utf-8"))
# Os mutantes (tools/wiring/run_ui_mutants.py) apontam o extrator para uma cópia mutada das fontes.
SOURCE_ROOT = os.environ.get("WIRING_ROOT") or None


def analysis(root=None, allow=None):
    allow = ALLOW if allow is None else allow
    data = E.extract(root)
    # Defeitos conhecidos entram como permitidos aqui; a conferencia de "continua violando" e separada.
    merged = json.loads(json.dumps(allow))
    merged["producer_internal"].update(merged.get("defects", {}))
    merged["consumer_local"].update(merged.get("consumer_defects", {}))
    merged["methods_unused"].update(merged.get("methods_unused", {}))
    return data, E.analyse(data, merged), E.analyse(data, {"producer_internal": {}, "consumer_local": {}, "methods_unused": {}})


class WiringGraph(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data, cls.result, cls.raw = analysis(SOURCE_ROOT)

    def test_extractor_sees_the_real_surface(self):
        # Se o extrator parar de enxergar, todos os outros testes passariam por vazio.
        self.assertGreater(len(self.data["producers"]), 500)
        self.assertGreater(len(self.data["js_reads"]), 300)
        for bridge in ("OmegasNative", "OmegasCalibration", "OmegasPower", "OmegasAutoCal"):
            self.assertGreaterEqual(len(self.data["kotlin_methods"][bridge]), 5, bridge)
            self.assertGreaterEqual(len(self.data["js_calls"][bridge]), 5, bridge)
        for key in ("usbConnected", "fuelState", "petrol_ms", "telemetryAgeMs", "phase", "state"):
            self.assertIn(key, self.data["producers"], f"produtor {key} sumiu do extrator")
            self.assertIn(key, self.data["js_reads"], f"leitura {key} sumiu do extrator")

    def test_no_unfed_consumer(self):
        unfed = self.result[0]
        self.assertEqual(unfed, [], "UI le chave que nenhum Kotlin emite (mostraria — ou 0 para sempre): %s" % unfed)

    def test_no_wind_producer(self):
        wind = self.result[1]
        self.assertEqual(wind, [], "Kotlin emite chave que ninguem le e que nao esta na allowlist com motivo: %s" % wind)

    def test_js_only_calls_methods_kotlin_exposes(self):
        missing = [m for m in self.result[2] if m not in ALLOW.get("methods_missing_ok", {})]
        self.assertEqual(missing, [], "JS chama metodo de ponte inexistente: %s" % missing)

    def test_kotlin_exposed_methods_are_called(self):
        self.assertEqual(self.result[3], [], "metodo @JavascriptInterface que ninguem chama: %s" % self.result[3])

    def test_known_defects_still_violate_or_get_removed(self):
        raw_unfed, raw_wind = set(self.raw[0]), set(self.raw[1])
        fixed = [k for k in ALLOW.get("defects", {}) if k not in raw_wind]
        fixed += [k for k in ALLOW.get("consumer_defects", {}) if k not in raw_unfed]
        self.assertEqual(fixed, [], "defeito corrigido: remova de tests/wiring/allowlist.json (e de DEFECTS.md): %s" % fixed)

    def test_allowlist_entries_have_reasons_and_are_not_stale(self):
        for section in ("producer_internal", "defects", "consumer_local", "consumer_defects"):
            for key, reason in ALLOW.get(section, {}).items():
                self.assertTrue(isinstance(reason, str) and len(reason) >= 15, f"{section}.{key} sem motivo")
        stale = [k for k in ALLOW["producer_internal"] if k not in self.data["producers"]]
        self.assertEqual(stale, [], "allowlist cita chave que o Kotlin nao emite mais: %s" % stale)


class ScalarShape(unittest.TestCase):
    """O Kotlin emite `index` (0..1), `coverage` e `provisional` como ESCALARES planos; o JS não pode ler `.index.value`."""

    def test_real_ui_reads_no_child_of_a_scalar_key(self):
        data = E.extract(SOURCE_ROOT)
        self.assertEqual(data["shape_violations"], {}, "JS lê filho de chave escalar: %s" % data["shape_violations"])

    def test_extractor_knows_the_equivalence_scalars(self):
        scalars = E.extract_scalar_keys(SOURCE_ROOT)
        for key in ("index", "coverage", "provisional"):
            self.assertIn(key, scalars)

    def test_extractor_catches_the_old_index_value_bug(self):
        import shutil
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            tmp_root = Path(tmp)
            kotlin = tmp_root / E.KOTLIN_REL / "equivalence"
            ui = tmp_root / E.UI_REL / "screens"
            kotlin.mkdir(parents=True)
            ui.mkdir(parents=True)
            src = Path(SOURCE_ROOT or ROOT) / E.KOTLIN_REL / "equivalence" / "EquivalenceJson.kt"
            shutil.copy(src, kotlin / "EquivalenceJson.kt")
            (ui / "refino.js").write_text("const v = finite(eq?.index?.value); const p = eq.index.provisional;", "utf-8")
            found = E.extract_shape_violations(tmp_root)
            self.assertEqual(sorted(found), ["index.provisional", "index.value"])
            (ui / "refino.js").write_text("const v = finite(eq?.index); const p = eq.provisional; const n = eq.index.toFixed(1);", "utf-8")
            self.assertEqual(E.extract_shape_violations(tmp_root), {})


class DefectsDocumented(unittest.TestCase):
    """Todo defeito registrado na suíte (probe ou allowlist) precisa estar em tests/wiring/DEFECTS.md."""

    def test_every_registered_defect_is_in_defects_md(self):
        doc = (ROOT / "tests/wiring/DEFECTS.md").read_text("utf-8")
        registry = (ROOT / "tests/ui/wiring/registry.cjs").read_text("utf-8")
        ids = set(__import__("re").findall(r"defect\('(DEFECT-\d+)'", registry))
        for text in list(ALLOW.get("defects", {}).values()) + list(ALLOW.get("consumer_defects", {}).values()):
            ids |= set(__import__("re").findall(r"DEFECT-\d+", text))
        self.assertGreaterEqual(len(ids), 15)
        missing = sorted(i for i in ids if f"| {i} |" not in doc)
        self.assertEqual(missing, [], "defeito sem linha em tests/wiring/DEFECTS.md: %s" % missing)


if __name__ == "__main__":
    unittest.main()
