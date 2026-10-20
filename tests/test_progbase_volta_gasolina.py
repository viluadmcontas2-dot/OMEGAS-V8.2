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


@unittest.skipUnless(EXE and os.path.exists(EXE), 'ProgBase.exe ausente (PROGBASE_EXE)')
class Original(unittest.TestCase):
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
