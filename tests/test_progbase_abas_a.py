import json, os, re, sys, unittest
RAIZ = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
SAIDA = os.path.join(RAIZ, 'docs', 'progbase', 'abas')
sys.path.insert(0, os.path.join(RAIZ, 'tools', 'progbase'))
ABAS = ['TabCambio', 'TabLambda', 'TabEmissioni', 'TabSensore', 'TabPressione', 'TabTemp', 'TabKCoefficienti',
        'TabCorrettori', 'TabKOpenLoop', 'TabKFactor', 'TabKTempRid', 'TabKTempMot', 'TabKPressDiff', 'TabPressAss',
        'TabKTempi', 'TabKTempiEx']
MARCAS = {'OBS-COD', 'OBS-FIO', 'DERIVADO', 'INFERIDO', 'DESCONHECIDO'}


@unittest.skipUnless(os.path.isfile(os.path.join(SAIDA, 'TabCambio.json')), 'saida nao gerada')
class Saida(unittest.TestCase):
    def test_16_abas_e_contagem(self):
        sys.path.insert(0, os.path.join(RAIZ, 'tools', 'emulacao'))
        import dfm_renderer as D
        raiz = D.parse_dfm(open(os.path.join(RAIZ, 'docs/evidence/dfm-original/TFORMCONFIG.txt'), encoding='utf-8').read())
        def acha(o, n):
            if o.nome == n: return o
            for f in o.filhos:
                x = acha(f, n)
                if x: return x
        def cnt(o):
            return sum(1 + cnt(f) for f in o.filhos if f.classe != 'TTabSheet')
        for a in ABAS:
            d = json.load(open(os.path.join(SAIDA, a + '.json'), encoding='utf-8'))
            self.assertEqual(len(d['controles']), cnt(acha(raiz, a)), a)
            self.assertTrue(os.path.isfile(os.path.join(SAIDA, a + '.md')))

    def test_marcas_validas_e_sem_caminho_local(self):
        for a in ABAS:
            for ext in ('json', 'md'):
                t = open(os.path.join(SAIDA, a + '.' + ext), encoding='utf-8').read()
                self.assertIsNone(re.search(r'/tmp/|/root/|/home/user|scratchpad', t), a)
            d = json.load(open(os.path.join(SAIDA, a + '.json'), encoding='utf-8'))
            for c in d['controles']:
                self.assertIn(c['explicacao_leiga']['marca'], MARCAS)

    def test_dead_time_log_bate_com_emulacao(self):
        d = json.load(open(os.path.join(SAIDA, 'TabKTempi.json'), encoding='utf-8'))
        c = [x for x in d['controles'] if x['nome'] == 'EditTempoMortoGas'][0]
        q = c['envia']['metodos_proprios'][0]['amostras'][0]['quadros'][0]
        self.assertEqual(q, '13 7E 00 87 01 19')   # 1,0 ms = 391 (LOG: TEMPO_MORTO_GAS = 391)


EXE = os.environ.get('PROGBASE_EXE', '')


@unittest.skipUnless(os.path.isfile(EXE), 'sem ProgBase.exe')
class Emulacao(unittest.TestCase):
    def test_set_giri_cambio(self):
        import abas_motor as M, abas_emul as E
        b, r = M.abrir(EXE)
        objs = E.objetos_sd(open(os.path.join(RAIZ, 'docs/evidence/dfm-original/TSTREAMDATI.txt'), encoding='utf-8').read())
        e = E.Emul(EXE, r.fields('TStreamDati'), objs)
        e.preparar(['GIRI_MIN_CAMBIO'])
        self.assertEqual(e.set_esc(12, 1600)['quadros'][0].hex(' '), '13 10 00 40 06 69')
        self.assertEqual(e.get(12), 1600.0)


if __name__ == '__main__':
    unittest.main()
