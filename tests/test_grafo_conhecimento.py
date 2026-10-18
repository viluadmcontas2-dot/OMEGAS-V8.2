import copy, os, sys, unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'tools'))
import grafo_conhecimento as g


class GrafoConhecimentoTest(unittest.TestCase):
    def base(self):
        return {'nodes': [{'id': 'EcuParameterSnapshot', 'label': 'EcuParameterSnapshot', 'file_type': 'code',
                           'source_file': 'app/src/main/java/com/omegas/prohub/autocal/EcuParameterSnapshot.kt'}], 'links': []}

    def test_liga_anti_stallo_ao_codigo_e_e_idempotente(self):
        grafo = self.base()
        nos, arestas = g.construir(grafo)
        um = g.aplicar(copy.deepcopy(grafo), nos, arestas)
        nos2, arestas2 = g.construir(um)
        dois = g.aplicar(copy.deepcopy(um), nos2, arestas2)
        self.assertEqual(um, dois, 'aplicar duas vezes não pode duplicar nada')
        rel = {(l['source'], l['target'], l['relation']) for l in um['links']}
        self.assertIn(('doc:docs/evidence/2026-10-18-anti-stallo.md', 'conceito:ANTI_STALLO', 'menciona'), rel)
        self.assertIn(('doc:docs/evidence/2026-10-18-anti-stallo.md', 'EcuParameterSnapshot', 'documenta'), rel)

    def test_so_arestas_extraidas(self):
        grafo = self.base()
        nos, arestas = g.construir(grafo)
        novo = g.aplicar(grafo, nos, arestas)
        self.assertTrue(all(l['confidence'] == 'EXTRACTED' for l in novo['links'] if l.get('_origin') == 'conhecimento'))


if __name__ == '__main__':
    unittest.main()
