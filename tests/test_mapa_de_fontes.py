import subprocess, sys, unittest, os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))


class MapaDeFontesTest(unittest.TestCase):
    def test_mapa_em_dia_e_caminhos_existem(self):
        r = subprocess.run([sys.executable, '-B', os.path.join(ROOT, 'tools', 'mapa_de_fontes.py'), '--check'],
                           capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)

    def test_geracao_idempotente(self):
        caminho = os.path.join(ROOT, 'docs', 'mapa-de-fontes', 'MAPA.md')
        antes = open(caminho, encoding='utf-8').read()
        subprocess.run([sys.executable, '-B', os.path.join(ROOT, 'tools', 'mapa_de_fontes.py')], check=True, capture_output=True)
        self.assertEqual(antes, open(caminho, encoding='utf-8').read())


if __name__ == '__main__':
    unittest.main()
