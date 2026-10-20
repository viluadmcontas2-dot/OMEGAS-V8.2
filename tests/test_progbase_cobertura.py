"""Cobertura da engenharia reversa: determinística, completa e em dia no repo (sem .exe).

Rodar: python3 -B tests/test_progbase_cobertura.py
"""
import importlib.util
import json
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ESPERADAS = ['Anti-apagão', 'Volta à gasolina', 'AutoCal/preferências', 'Mapa K/bins', 'Curva K', 'Marcha lenta',
             'Injetores/F9', 'Nível do gás', 'Diagnóstico', 'Display', 'Transporte', 'Cadastro/F1', 'F6/F7/F8']


def carregar():
    spec = importlib.util.spec_from_file_location('cobertura_re', ROOT / 'tools/progbase/cobertura_re.py')
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class CoberturaTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mod = carregar()
        cls.d = cls.mod.calcular()

    def test_deterministico(self):
        self.assertEqual(self.mod.renderizar_json(self.d), self.mod.renderizar_json(self.mod.calcular()))

    def test_areas_esperadas(self):
        nomes = [a['area'] for a in self.d['areas']]
        for n in ESPERADAS:
            self.assertIn(n, nomes)

    def test_porcentagens_entre_0_e_100(self):
        for a in self.d['areas']:
            for p in (a['pct_provado'], a['pct_cobertura'], a['abas']['pct'], a['vetores']['pct'], a['app']['pct']):
                self.assertTrue(0 <= p <= 100, (a['area'], p))
        self.assertTrue(0 <= self.d['total']['pct_provado'] <= 100)

    def test_toda_evidencia_cai_em_uma_area(self):
        n = sum(a['evidencias'] for a in self.d['areas'])
        self.assertEqual(n, self.d['total']['evidencias'])

    def test_md_curto_e_json_em_dia(self):
        self.assertLessEqual(len(self.mod.renderizar_md(self.d).splitlines()), 60)
        r = subprocess.run([sys.executable, '-B', str(ROOT / 'tools/progbase/cobertura_re.py'), '--check'],
                           capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)

    def test_html_autonomo_e_em_dia(self):
        html = (ROOT / 'docs/progbase/cobertura.html').read_text(encoding='utf-8')
        self.assertLessEqual(len(html.encode('utf-8')), 300 * 1024)
        self.assertIn('<title>Cobertura da Engenharia Reversa</title>', html)
        self.assertNotIn('http://', html)
        self.assertNotIn('https://', html)
        r = subprocess.run([sys.executable, '-B', str(ROOT / 'tools/progbase/cobertura_html.py'), '--check'],
                           capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        json.loads((ROOT / 'docs/progbase/cobertura.json').read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()
