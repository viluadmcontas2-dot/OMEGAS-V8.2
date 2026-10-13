import json, os, sys, unittest
ROOT = os.path.join(os.path.dirname(__file__), '..')
sys.path.insert(0, os.path.join(ROOT, 'tools', 'sessoes'))
FIXTURE = os.path.join(ROOT, 'tests', 'fixtures', 'sessoes-uteis', 'apagao-2026-10-13-1613.json')


def replay_module():
    import corpus
    src = open(os.path.join(ROOT, 'tools', 'sessoes', 'replay_stallwatch.py')).read().split('por = collections')[0]
    src = src.replace('sys.path.insert(0, os.path.dirname(__file__))', '')
    ns = {'corpus': corpus}
    exec(compile(src, 'replay_stallwatch', 'exec'), ns)
    return ns


class ApagaoReal(unittest.TestCase):
    """Apagão gravado pelo app (APK 8.0.0-test-debug): desacelera em corte, volta a ~2 ms, o giro despenca e a ECU apaga."""

    def setUp(self):
        self.data = json.load(open(FIXTURE))
        self.frames = self.data['quadros']

    def test_forma_do_evento_gravado(self):
        cut = [f for f in self.frames if f['fuel'] == 'CUTOFF']
        gnv = [f for f in self.frames if f['fuel'] == 'GNV']
        self.assertGreaterEqual(len(cut), 6)                       # vários quadros de desaceleração em corte
        self.assertTrue(all(f['petrol_ms'] == 0 for f in cut))      # corte = injeção zero
        last_cut = max(i for i, f in enumerate(self.frames) if f['fuel'] == 'CUTOFF')
        back = self.frames[last_cut + 1]                            # primeiro quadro depois do corte
        self.assertEqual(back['fuel'], 'GNV')
        self.assertLess(back['petrol_ms'], 2.0)                     # volta do corte a ~2 ms
        self.assertGreater(back['rpm'], 1400)
        low = [f for f in gnv if f['rpm'] < 800]
        self.assertTrue(low)                                        # o giro despenca depois do retorno
        self.assertEqual(self.frames[-1]['fuel'], 'DESLIGADO')      # e a ECU apaga

    def test_detector_novo_registra_o_apagao(self):
        ns = replay_module()
        T = [dict(t_ms=f['t_s'] * 1000, rpm=f['rpm'], fuel=f['fuel'], petrol_ms=f['petrol_ms'] or 0, map_bar=f['map_bar']) for f in self.frames]
        stalls, _near = ns['replay'](T)
        self.assertEqual(len(stalls), 1)

    def test_o_proprio_app_ja_registrou_onde_foi(self):
        ev = self.data['engine_stall_registrado']
        self.assertTrue(ev['decelerating'])
        self.assertLess(ev['petrolMs'], 3.0)
        self.assertLess(ev['mapBar'], 0.3)


if __name__ == '__main__':
    unittest.main()
