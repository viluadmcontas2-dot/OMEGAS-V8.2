#!/usr/bin/env python3
"""Anti-apagão: bytes que o ProgBase original manda (emulado no Unicorn) × doc × app.
Os testes de dados versionados rodam sempre; os que executam o .exe pulam se ele faltar (PROGBASE_EXE)."""
import os
import re
import sys
import unittest

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
DOC = os.path.join(RAIZ, 'docs', 'progbase', 'protocolo', 'anti-apagao-quadros-original.md')
EXE = os.environ.get('PROGBASE_EXE', '/tmp/claude-0/-home-user-OMEGAS-V8-2/ce19e461-e70c-54b2-8118-09ced10d40b6/scratchpad/progbase/ProgBase.exe')

ESPERADO = {  # (índice, valor) -> quadro de escrita do original (OBS-COD)
    (0, 1800): '14 33 01 00 08 07 57',
    (0, 1600): '14 33 01 00 40 06 8E',
    (0, 1601): '14 33 01 00 41 06 8F',
    (1, 150): '14 33 01 01 96 00 DF',
    (2, 1000): '14 33 01 02 E8 03 35',
    (3, 5): '14 33 01 03 05 00 50',
    (4, 30): '14 33 01 04 1E 00 6A',
}


def _b(h):
    return bytes.fromhex(h.replace(' ', ''))


def quadro_indexado(indice, valor):
    corpo = bytes([0x14, 0x33, 0x01, indice, valor & 0xFF, valor >> 8])
    return corpo + bytes([sum(corpo) & 0xFF])


def quadro_app(valores):
    """Espelho de AutoCalProtocol.writeVectorU16(0x0133, …) (leitura do código do app, não do original)."""
    dados = b''.join(v.to_bytes(2, 'little') for v in valores)
    corpo = bytes([0x37, 0x33, len(dados) + 2, 0x01]) + dados
    return corpo + bytes([sum(corpo) & 0xFF])


class Dados(unittest.TestCase):
    def test_quadros_do_doc_tem_soma_certa(self):
        texto = open(DOC, encoding='utf-8').read()
        for q in re.findall(r'`((?:14|29|35|37) (?:[0-9A-F]{2} ){2,}[0-9A-F]{2})`', texto):
            b = _b(q)
            self.assertEqual(sum(b[:-1]) & 0xFF, b[-1], q)

    def test_formula_reproduz_o_original(self):
        for (i, v), q in ESPERADO.items():
            self.assertEqual(quadro_indexado(i, v), _b(q))
            self.assertIn(q, open(DOC, encoding='utf-8').read())

    def test_app_diverge_do_original(self):
        app = quadro_app([1800, 100, 1500, 2, 20])
        self.assertEqual(app.hex(' ').upper(), '37 33 0C 01 08 07 64 00 DC 05 02 00 14 00 E1')
        self.assertNotEqual(app, quadro_indexado(0, 1800))
        self.assertNotIn('37 33', ' '.join(ESPERADO.values()))


@unittest.skipUnless(os.path.isfile(EXE), 'ProgBase.exe ausente (PROGBASE_EXE)')
class Emulado(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        sys.path.insert(0, os.path.join(RAIZ, 'tools', 'emulacao'))
        import antiapagao_original as A
        cls.r = A.emular(EXE)

    def test_campos(self):
        for c in self.r['campos']:
            self.assertIsNone(c['erro'])
            self.assertEqual(c['bruto'], '29 33 01 5D ' + ESPERADO[(c['indice'], c['valor'])])

    def test_caixa_igual_ao_fio(self):
        self.assertEqual(self.r['caixa']['marcar (palavra0=0000)']['bruto'], '35 FA 00 20 00 00 00 4F')
        self.assertEqual(self.r['caixa']['desmarcar (palavra0=0020)']['bruto'], '35 FA 00 00 00 00 00 2F')


if __name__ == '__main__':
    unittest.main()
