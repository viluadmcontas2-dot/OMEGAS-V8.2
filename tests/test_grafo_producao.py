"""Grafo de PRODUÇÃO: idempotente, sem elo ESSENCIAL quebrado, e o detector de fato detecta (python3 -B -m unittest)."""
import importlib.util
import os
import unittest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
spec = importlib.util.spec_from_file_location('grafo_producao', os.path.join(ROOT, 'tools', 'grafo_producao.py'))
gp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gp)


class GrafoProducao(unittest.TestCase):
    def test_idempotente_mesma_saida_duas_vezes(self):
        j1, m1, _ = gp.gerar()
        j2, m2, _ = gp.gerar()
        self.assertEqual(j1, j2)
        self.assertEqual(m1, m2)

    def test_markdown_curto_e_com_as_tres_secoes(self):
        _, m, _ = gp.gerar()
        self.assertLessEqual(len(m.splitlines()), 120)
        for titulo in ('## (a) Por aba', '## (b) IMPLEMENTADO × FALTA', '## (c) Elos quebrados'):
            self.assertIn(titulo, m)

    def test_cobre_as_oito_abas_do_contrato(self):
        _, _, d = gp.gerar()
        nomes = {a['aba'] for a in d['abas']}
        self.assertEqual({'Agora', 'Mapa K', 'Curva K', 'AutoCal', 'Ajuste GNV', 'Sessões', 'Ferramentas', 'Diagnóstico'}, nomes)
        self.assertGreater(d['contagem_nos']['ponte'], 0)
        self.assertGreater(d['contagem_arestas']['chama'], 0)

    def test_grafo_real_sem_elo_essencial_quebrado(self):
        _, _, d = gp.gerar()
        quebrados = {k: v for k, v in d['elos_quebrados']['essenciais'].items() if v}
        self.assertEqual({}, quebrados)

    def test_arquivos_gerados_estao_em_dia(self):
        j, m, _ = gp.gerar()
        for nome, esperado in (('producao.json', j), ('PRODUCAO.md', m)):
            with open(os.path.join(ROOT, 'docs', 'grafo', nome), encoding='utf-8') as f:
                self.assertEqual(esperado, f.read(), f'docs/grafo/{nome} desatualizado: rode python3 -B tools/grafo_producao.py')

    def test_detector_pega_ponte_inexistente_e_escrita_sem_teste(self):
        g = gp.construir()
        g['textos'] = dict(g['textos'])
        alvo = next(iter(g['textos']))
        g['textos'][alvo] += '\nOmegasNative.metodoQueNaoExiste();\n'
        g['pontes'] = dict(g['pontes'])
        g['pontes']['OmegasNative.eraseTudoSemTeste'] = {'interface': 'OmegasNative', 'metodo': 'eraseTudoSemTeste', 'arquivo': 'x.kt', 'alvos': []}
        g['chamadores'] = dict(g['chamadores'])
        g['chamadores']['OmegasNative.eraseTudoSemTeste'] = [alvo]
        ess = gp.analisar(g)['essenciais']
        self.assertTrue(any('metodoQueNaoExiste' in x for x in ess['E1_ui_chama_ponte_inexistente']))
        self.assertIn('OmegasNative.eraseTudoSemTeste', ess['E3_ponte_de_escrita_chamada_sem_teste'])


if __name__ == '__main__':
    unittest.main()
