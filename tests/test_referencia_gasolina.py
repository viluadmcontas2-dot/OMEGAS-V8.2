import importlib.util
from pathlib import Path
import unittest
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('referencia', ROOT / 'tools/sessoes/gerar_referencia_gasolina.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def row(p=2, state='GASOLINA_ATIVA', **kw):
    return dict(sessao='s1', rpm=900, map_bar=.35, petrol_ms=p, gas_ms=6, state=state, **kw)


class ReferenciaTest(unittest.TestCase):
    def test_median_counts_ratio_and_filter(self):
        rows = [row(p=x) for x in [1, 3] * 15] + [row(4, 'GNV_ATIVO') for _ in range(30)]
        rows += [dict(row(), rpm=699), dict(row(), petrol_ms=None)]
        c = m.aggregate(rows)[0]
        self.assertEqual((c['petrolMs'], c['n'], c['gnvPetrolMs'], c['gnvN'], c['ratio'], c['gasPetrolRatio']), (2, 30, 4, 30, 2, 1.5))
        self.assertEqual(c['petrolSessions'], ['s1'])

    def test_threshold_and_boundaries(self):
        self.assertIsNone(m.aggregate([row()] * 29)[0]['petrolMs'])
        cells = m.aggregate([row()] * 30 + [dict(row(), map_bar=.4, rpm=1000)] * 30)
        self.assertEqual([(c['mapMin'], c['rpmMin']) for c in cells], [(.3, 500), (.4, 1000)])

    def test_last_snapshot_no_fallback_to_old(self):
        fields = [{'key': k, 'rawPayloadHex': '0040' * 30} for k in ['MUL_ACT', 'PETR_INJ_TBP']]
        def event(seq, f):
            return dict(sessao='s1', sequence=seq, type='autocal_native_snapshot', data={'fields': f})
        c = m.curves([event(2, fields), event(1, [])], ['s1'])[0]
        self.assertEqual(c['mulActQ14'], [16384] * 30)
        self.assertEqual(c['k'], [1] * 30)
        self.assertEqual(c['petrolAxisMs'], [32] * 30)
        self.assertIsNone(m.curves([event(2, fields), event(3, [])], ['s1'])[0]['k'])

    def test_generated_files_are_current_and_identical(self):
        a, doc = m.generate()
        b, doc2 = m.generate()
        self.assertEqual((a, doc), (b, doc2))
        self.assertEqual(m.encode(a), m.JSON_PATH.read_text())
        self.assertEqual(doc, m.DOC_PATH.read_text())

    def test_cli_twice_byte_identical_including_embedded_js(self):
        paths = [m.JSON_PATH, m.DOC_PATH, ROOT / 'app/src/main/assets/ui/core/gasoline-reference.js']
        before = [p.read_bytes() for p in paths]
        for _ in range(2):
            subprocess.run([sys.executable, '-B', str(ROOT / 'tools/sessoes/gerar_referencia_gasolina.py')], check=True)
            self.assertEqual(before, [p.read_bytes() for p in paths])


if __name__ == '__main__':
    unittest.main()
