"""Evidências do ProgBase: o auditor tem que passar no repo real e rejeitar índices ruins.

Rodar: python3 -B tests/test_progbase_evidencias.py
Os ids de fixture são montados em tempo de execução para não virarem "órfãos" no repo real.
"""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
P = 'EV-'  # prefixo; o id completo é montado nos testes


def carregar():
    spec = importlib.util.spec_from_file_location('auditar_progbase', ROOT / 'tools/progbase/auditar.py')
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def ev(i, **kw):
    d = {'id': i, 'afirmacao': 'Afirmação de teste.', 'marca': 'OBS-COD', 'fonte': 'docs/fonte.md',
         'versao_progbase': '2.16.4', 'sha_commit': 'abc1234', 'usado_por': ['tests/t.py']}
    d.update(kw)
    return d


def montar(tmp, itens, extra_md=None):
    root = Path(tmp)
    (root / 'docs/progbase/evidencias').mkdir(parents=True)
    (root / 'docs/fonte.md').write_text('fonte')
    (root / 'tests').mkdir()
    (root / 'tests/t.py').write_text('# teste')
    (root / 'docs/progbase/evidencias/INDEX.json').write_text(json.dumps(itens))
    if extra_md is not None:
        (root / 'docs/nota.md').write_text(extra_md)
    return root


class RepoReal(unittest.TestCase):
    def test_indice_real_passa_e_tem_o_minimo(self):
        a = carregar()
        r = a.auditar(ROOT)
        self.assertEqual(r['problemas'], [], '\n'.join(r['problemas']))
        self.assertGreaterEqual(r['total'], 40)
        for m in a.MARCAS:
            self.assertIn(m, r['por_marca'])

    def test_saida_deterministica(self):
        a = carregar()
        self.assertEqual(a.texto(a.auditar(ROOT)), a.texto(a.auditar(ROOT)))

    def test_cli_check_zero_no_repo_real(self):
        a = carregar()
        self.assertEqual(a.main(['--check', '--root', str(ROOT)]), 0)


class Rejeicoes(unittest.TestCase):
    def rodar(self, itens, md=None):
        a = carregar()
        with tempfile.TemporaryDirectory() as tmp:
            root = montar(tmp, itens, md)
            return a.auditar(root), a.main(['--check', '--root', str(root)])

    def test_indice_bom_passa(self):
        r, rc = self.rodar([ev(P + 'TESTE-001')])
        self.assertEqual(r['problemas'], [])
        self.assertEqual(rc, 0)

    def test_id_duplicado_rejeitado(self):
        r, rc = self.rodar([ev(P + 'TESTE-001'), ev(P + 'TESTE-001')])
        self.assertTrue(any('duplicado' in p for p in r['problemas']))
        self.assertNotEqual(rc, 0)

    def test_id_fora_do_padrao_rejeitado(self):
        r, rc = self.rodar([ev(P + 'teste-1')])
        self.assertTrue(any('padrão' in p for p in r['problemas']))
        self.assertNotEqual(rc, 0)

    def test_marca_invalida_rejeitada(self):
        r, rc = self.rodar([ev(P + 'TESTE-001', marca='PROVADO')])
        self.assertTrue(any('marca' in p for p in r['problemas']))
        self.assertNotEqual(rc, 0)

    def test_id_orfao_em_doc_rejeitado(self):
        r, rc = self.rodar([ev(P + 'TESTE-001')], md='cita ' + P + 'TESTE-002 aqui')
        self.assertTrue(any('órfão' in p and 'TESTE-002' in p for p in r['problemas']))
        self.assertNotEqual(rc, 0)

    def test_fonte_e_usado_por_inexistentes_rejeitados(self):
        r, rc = self.rodar([ev(P + 'TESTE-001', fonte='docs/nao-existe.md', usado_por=['app/nada.kt'])])
        self.assertTrue(any('fonte inexistente' in p for p in r['problemas']))
        self.assertTrue(any('usado_por inexistente' in p for p in r['problemas']))
        self.assertNotEqual(rc, 0)

    def test_usado_por_vazio_so_para_desconhecido(self):
        r, _ = self.rodar([ev(P + 'TESTE-001', usado_por=[])])
        self.assertTrue(any('usado_por vazio' in p for p in r['problemas']))
        r, rc = self.rodar([ev(P + 'TESTE-001', usado_por=[], marca='DESCONHECIDO')])
        self.assertEqual(r['problemas'], [])
        self.assertEqual(rc, 0)

    def test_contradicao_por_chave_rejeitada(self):
        r, rc = self.rodar([ev(P + 'TESTE-001', chave='k', marca='OBS-FIO'),
                            ev(P + 'TESTE-002', chave='k', marca='INFERIDO')])
        self.assertTrue(any('contradição' in p for p in r['problemas']))
        self.assertNotEqual(rc, 0)

    def test_mesma_chave_mesma_marca_ok(self):
        r, rc = self.rodar([ev(P + 'TESTE-001', chave='k'), ev(P + 'TESTE-002', chave='k')])
        self.assertEqual(r['problemas'], [])
        self.assertEqual(rc, 0)


if __name__ == '__main__':
    unittest.main()
