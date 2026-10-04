"""Governança: regras que o dono já repetiu várias vezes viram teste (quebra no CI se voltarem)."""
import pathlib
import re
import unittest

UI = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/assets/ui"


def sources(pattern):
    return [(p, p.read_text(encoding="utf-8")) for p in UI.rglob(pattern)]


class OwnerRules(unittest.TestCase):
    def test_no_horizontal_scroll_anywhere(self):
        bad = [p.name for p, s in sources("*.css") if re.search(r"overflow-x\s*:\s*(auto|scroll)", s)]
        self.assertEqual([], bad, "rolagem horizontal é proibida (dirigindo ninguém rola de lado)")

    def test_no_entendi_button(self):
        def code(text):  # comentário que cita a palavra não conta
            return re.sub(r"//[^\n]*", "", text)
        bad = [p.name for p, s in sources("*.js") + sources("*.html")
               if re.search(r"""[>'"`]\s*Entendi\s*[<'"`]""", code(s))]
        self.assertEqual([], bad, "botão 'Entendi' que some não comunica nada")

    def test_no_manual_session_zip_export(self):
        bad = [p.name for p, s in sources("*.js") + sources("*.html") if "Exportar ZIP" in s]
        self.assertEqual([], bad, "sessões já são exportadas sozinhas para Downloads")

    def test_dark_single_theme(self):
        tokens = (UI / "tokens.css").read_text(encoding="utf-8")
        self.assertNotIn('data-theme="light"', tokens)
        self.assertRegex(tokens, r"color-scheme\s*:\s*dark")

    def test_rail_has_no_numbers(self):
        html = (UI / "index.html").read_text(encoding="utf-8")
        self.assertNotRegex(html, r'data-route="[^"]+"[^>]*>\s*<[^>]+>\s*0[1-8]\s*<', "trilho sem números 01–08")

    def test_eight_routes_with_diagnostico(self):
        html = (UI / "index.html").read_text(encoding="utf-8")
        routes = re.findall(r'data-route="([^"]+)"', html)
        self.assertEqual(['dashboard', 'map', 'curve', 'autocal', 'refino', 'sessions', 'tools', 'diagnostico'], routes)

    def test_user_texts_never_cite_internal_rules(self):
        banned = re.compile(r"falta(m)? (um|1|\d+) ?min|visitas? separadas?|intervalo de confian", re.I)
        bad = [p.name for p, s in sources("*.js") if banned.search(s)]
        self.assertEqual([], bad, "a UI nunca mostra regras internas (minutos, visitas, intervalos) ao leigo")


if __name__ == "__main__":
    unittest.main()
