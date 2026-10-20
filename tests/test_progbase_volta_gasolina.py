"""Volta automática à gasolina / pressão baixa: conta da tabela (sempre) e quadros do original emulado (pula sem o .exe)."""
import os, sys, unittest
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
sys.path.insert(0, os.path.join(ROOT, 'tools', 'emulacao'))
import volta_gasolina_original as V  # noqa: E402
EXE = os.environ.get('PROGBASE_EXE') or ''


class Tabela(unittest.TestCase):
    def test_pontos_fixos(self):
        a, b = V.tabela_pressao(1.25, 1.5)
        self.assertEqual(len(a), 15)
        self.assertEqual(a[0], 1.25)
        self.assertEqual(a[10], 1.5)
        self.assertAlmostEqual(a[14], (1.5 - 0.95) * 1.176470588235294 + 1.1)
        self.assertAlmostEqual(a[5], 1.375)
        self.assertEqual(b[10], 0.0)
        self.assertAlmostEqual(b[0], (1 - (1.25 / 1.5) ** 0.5) * 125)


GRADE = os.path.join(ROOT, 'tests', 'fixtures', 'volta_gasolina_original_grade.json')
LOGNOVO = os.path.join(ROOT, 'tests', 'ui', 'fixtures', 'ecu-estado-lognovo.json')


class Grade(unittest.TestCase):
    """Oráculo congelado (gerado do .exe): coerente com a central real do dono e com o formato dos quadros."""

    def setUp(self):
        import json
        self.g = json.load(open(GRADE, encoding='utf-8'))

    def test_quadros_tem_soma_e_ordem_do_original(self):
        self.assertEqual(self.g['tempo_ritorno_benzina']['255'], ['12 3E 00 FF 4F'])
        self.assertGreaterEqual(len(self.g['tabelas']), 70)
        for t in self.g['tabelas']:
            self.assertEqual(len(t['quadros']), 30)
            for k, q in enumerate(t['quadros']):
                b = [int(x, 16) for x in q.split()]
                self.assertEqual(b[:4], [0x14, 0x7B if k % 2 == 0 else 0x7C, 0x00, k // 2])
                self.assertEqual(sum(b[:-1]) & 0xFF, b[-1])

    def test_central_do_dono_e_a_tabela_do_original_para_1_25_bar_e_p0_2000(self):
        import json
        leit = json.load(open(LOGNOVO, encoding='utf-8'))['leituras']
        t = [x for x in self.g['tabelas'] if x['p0_bruto'] == 2000 and abs(x['pmp_bar'] - 1.25) < 1e-9][0]
        vals = lambda h: [int(a, 16) | int(b, 16) << 8 for a, b in zip(h.split()[0::2], h.split()[1::2])]
        rif = [int(q.split()[4], 16) | int(q.split()[5], 16) << 8 for q in t['quadros'][0::2]]
        coeff = [int(q.split()[4], 16) | int(q.split()[5], 16) << 8 for q in t['quadros'][1::2]]
        self.assertEqual(vals(leit['29 7B 00 A4']), rif)
        self.assertEqual(vals(leit['29 7C 00 A5']), coeff)
        self.assertEqual(leit['09 3E 00 47'], '00')


@unittest.skipUnless(EXE and os.path.exists(EXE), 'ProgBase.exe ausente (PROGBASE_EXE)')
class Original(unittest.TestCase):
    def test_grade_congelada_e_a_que_o_exe_gera_hoje(self):
        import json
        import volta_gasolina_grade as G
        self.assertEqual(G.gerar(EXE), json.load(open(GRADE, encoding='utf-8')))

    def test_quadros(self):
        r = V.emular(EXE, 1.25, 1500, 3)
        t = r['tempo_ritorno_benzina']
        self.assertEqual(t['desmarcar_volta_auto (SetEsc 28, 255.0)']['quadros'], ['12 3E 00 FF 4F'])
        self.assertEqual(t['marcar_restaura_3 (SetEsc 28, 3.0)']['quadros'], ['12 3E 00 03 53'])
        self.assertEqual(r['press_bassa_retropas_lr']['quadros'], ['13 98 00 E2 04 91'])
        p = r['tabela_press_diff']['pontos']
        self.assertEqual(p[0]['quadros'], ['14 7B 00 00 E2 04 75', '14 7C 00 00 41 04 D5'])
        self.assertEqual(p[14]['coeff_bruto'], -990)


if __name__ == '__main__':
    unittest.main()
