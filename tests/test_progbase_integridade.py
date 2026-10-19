"""Auditoria deve rejeitar evidência e caminhos inventados, sem precisar do exe."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class Integridade(unittest.TestCase):
    def test_evidencia_manual_exige_registro_e_fonte(self):
        spec = importlib.util.spec_from_file_location('auditoria', ROOT / 'tools/auditoria.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            pb = root / 'docs/progbase'
            (pb / 'grafo').mkdir(parents=True)
            (root / 'docs/handoff').mkdir()
            (root / 'docs/handoff/PENDENTE.md').write_text('- [ ] falta\n')
            (pb / 'grafo/graph.json').write_text('{"nodes": [], "links": []}')
            eid = 'MAN-ABAS-B-TabIniezione'
            (pb / 'INDEX.md').write_text(eid)
            self.assertFalse(module.auditar(root)['integridade_ok'])
            (pb / 'evidencias-locais.json').write_text(json.dumps({'records': [
                {'evidence_id': eid, 'source_file': 'docs/progbase/fonte.md'}]}))
            self.assertFalse(module.auditar(root)['integridade_ok'])
            (pb / 'fonte.md').write_text('OBS-COD: controle no DFM; efeito desconhecido.')
            self.assertTrue(module.auditar(root)['integridade_ok'])

    def test_rejeita_referencias_ausentes_e_nao_inventa_testes_verdes(self):
        spec = importlib.util.spec_from_file_location('auditoria', ROOT / 'tools/auditoria.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            pb = root / 'docs/progbase'
            (pb / 'grafo').mkdir(parents=True)
            (pb / 'decomp').mkdir()
            (root / 'docs/handoff').mkdir()
            (root / 'docs/handoff/PENDENTE.md').write_text('- [ ] falta\n- [x] feito\n')
            eid = 'ev_' + 'a' * 64
            (pb / 'INDEX.md').write_text(eid)
            graph = {'nodes': [{'id': 'n', 'source_file': 'ausente.md'}], 'links': []}
            (pb / 'grafo/graph.json').write_text(json.dumps(graph))
            report = module.auditar(root)
            self.assertFalse(report['integridade_ok'])
            self.assertIn(eid, report['evidencias']['rea_ausentes'])
            self.assertEqual(report['testes']['estado'], 'DESCONHECIDO')
            self.assertEqual(report['pendencias']['abertas'], 1)
            (root / 'ausente.md').write_text('fonte')
            (pb / 'decomp/evidence-bundle.json').write_text(json.dumps({'records': [{'evidence_id': eid, 'operation': 'inspect_pe_resources'}]}))
            self.assertTrue(module.auditar(root)['integridade_ok'])
            graph['links'] = [{'source': 'n', 'target': 'inexistente'}]
            (pb / 'grafo/graph.json').write_text(json.dumps(graph))
            self.assertFalse(module.auditar(root)['integridade_ok'])


if __name__ == '__main__':
    unittest.main()
