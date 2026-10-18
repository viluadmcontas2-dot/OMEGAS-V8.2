"""Integridade da decompilação e do grafo do ProgBase (docs/progbase/decomp, docs/progbase/grafo). Sem rede, sem o .exe."""
import gzip, json, os, re, subprocess, sys, unittest
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
DEC = os.path.join(ROOT, 'docs', 'progbase', 'decomp')
GR = os.path.join(ROOT, 'docs', 'progbase', 'grafo', 'graph.json')


class Decomp(unittest.TestCase):
    def test_funcoes_e_pseudocodigo(self):
        F = json.load(gzip.open(os.path.join(DEC, 'funcoes.json.gz')))['funcoes']
        self.assertGreater(len(F), 20000)
        self.assertEqual(len({f['a'] for f in F}), len(F))
        frags = {f['frag'] for f in F if f.get('frag')}
        for fr in frags:
            self.assertTrue(os.path.exists(os.path.join(DEC, 'pseudocodigo', fr)))
        self.assertLess(sum(os.path.getsize(os.path.join(dp, f)) for dp, _, fs in os.walk(DEC) for f in fs), 40e6)

    def test_cobertura_consistente(self):
        c = json.load(open(os.path.join(DEC, 'cobertura.json')))
        self.assertGreater(c['pct_text_em_funcoes'], 50)
        self.assertTrue(os.path.exists(os.path.join(DEC, 'COBERTURA.md')))

    def test_grafo_integro(self):
        g = json.load(open(GR))
        ids = {n['id'] for n in g['nodes']}
        for l in g['links']:
            self.assertIn(l['source'], ids); self.assertIn(l['target'], ids)
        for n in g['nodes']:
            if n.get('source_file'):
                self.assertTrue(os.path.exists(os.path.join(ROOT, n['source_file'])), n['id'])
        self.assertLess(os.path.getsize(GR), 20e6)
        grau = {i: 0 for i in ids}
        for l in g['links']:
            grau[l['source']] += 1; grau[l['target']] += 1
        crit = [i for i, n in ((n['id'], n) for n in g['nodes']) if n['tipo'] in ('formulario', 'comando', 'vetor', 'opcode') and grau[i] == 0] if False else \
               [n['id'] for n in g['nodes'] if n['tipo'] in ('formulario', 'comando', 'vetor', 'opcode') and grau[n['id']] == 0]
        self.assertEqual(crit, [])


if __name__ == '__main__':
    unittest.main()
