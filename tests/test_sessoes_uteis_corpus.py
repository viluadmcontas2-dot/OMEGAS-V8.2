import csv, gzip, hashlib, json, os, sys, unittest
ROOT = os.path.join(os.path.dirname(__file__), '..')
BASE = os.path.join(ROOT, 'tests', 'fixtures', 'sessoes-uteis')

class CorpusSessoesUteis(unittest.TestCase):
    def test_integridade_sha256_e_contagens(self):
        info = json.load(open(os.path.join(BASE, 'integridade.json')))
        for name, digest in info['sha256'].items():
            self.assertEqual(hashlib.sha256(open(os.path.join(BASE, name), 'rb').read()).hexdigest(), digest, name)
        rows = list(csv.DictReader(gzip.open(os.path.join(BASE, 'telemetria.csv.gz'), 'rt')))
        self.assertEqual(len(rows), info['linhas_telemetria'])
        self.assertEqual(sum(1 for r in rows if r['fuel'] == 'GNV'), info['linhas_gnv'])

    def test_gnv_real_e_ids_do_drive(self):
        info = json.load(open(os.path.join(BASE, 'integridade.json')))
        self.assertGreater(info['linhas_gnv'], 50000)
        ids = json.load(open(os.path.join(BASE, 'drive-ids.json')))
        self.assertEqual(len(ids['pacotes_com_gnv']), 14)
        self.assertIn('rank074', ids['nao_baixados'])

    def test_carregador(self):
        sys.path.insert(0, os.path.join(ROOT, 'tools', 'sessoes'))
        import corpus
        first = next(corpus.telemetria(somente_gnv=True))
        self.assertEqual(first['fuel'], 'GNV')
        self.assertIsNotNone(first['rpm'])

if __name__ == '__main__':
    unittest.main()
