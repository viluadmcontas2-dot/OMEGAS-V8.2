"""Tabela caso -> (vetor, palavra, máscara) das caixas de flag do ProgBase original, extraída do .exe.

Prova (RED->GREEN) que tools/emulacao/flags_do_original.py lê as rotinas do TStreamDati no binário
(e não uma tabela escrita à mão): caso 4 de FLAG_CONF3 = palavra 0 máscara 0x20 (anti-stallo),
'Abilita progressioni' = FLAG_CONF3 palavra 0 máscara 0x10, saída idempotente, e uma mutação de um
único byte do .exe muda a tabela e faz o --check falhar."""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
TOOL = os.path.join(ROOT, 'tools', 'emulacao', 'flags_do_original.py')
JSON_VERSIONADO = os.path.join(ROOT, 'docs', 'evidence', 'flags-do-original.json')
sys.path.insert(0, os.path.join(ROOT, 'tools', 'emulacao'))
import flags_do_original as fo  # noqa: E402

EXE = fo.achar_exe(None)


def entrada(tab, vetor, caso, condicao='sempre'):
    for e in tab['flags']:
        if e['vetor'] == vetor and e['caso'] == caso and e['condicao'] == condicao:
            return e
    raise AssertionError((vetor, caso, condicao))


def por_caption(tab, caption_ita):
    achados = []
    for e in tab['flags']:
        for c in e['controles']:
            if c.get('caption_ita') == caption_ita and c['papel'] == 'caixa (Checked)':
                achados.append((e, c))
    return achados


class TestJsonVersionado(unittest.TestCase):
    """Não precisa do .exe: confere o JSON que está no repositório."""

    @classmethod
    def setUpClass(cls):
        with open(JSON_VERSIONADO, encoding='utf-8') as fh:
            cls.tab = json.load(fh)

    def test_anti_stallo_caso4_flag_conf3_palavra0_mascara_0x20(self):
        e = entrada(self.tab, 'FLAG_CONF3', 4)
        self.assertEqual((e['serial_code'], e['palavra'], e['mascara']), (250, 0, '0x0020'))
        self.assertTrue(e['coerente'])
        self.assertIn('CheckAntiStallo', [c['nome'] for c in e['controles']])

    def test_abilita_progressioni_flag_conf3_palavra0_mascara_0x10(self):
        ach = por_caption(self.tab, 'Abilita progressioni')
        self.assertTrue(ach)
        for e, c in ach:
            self.assertEqual((e['vetor'], e['palavra'], e['mascara']), ('FLAG_CONF3', 0, '0x0010'))
            self.assertEqual(c['nome'], 'CheckProgressioni')

    def test_ordenado_e_sem_duplicata(self):
        chaves = [(e['vetor'], e['palavra'], int(e['mascara'], 16), e['caso'] if e['caso'] is not None else -1,
                   e['condicao']) for e in self.tab['flags']]
        self.assertEqual(chaves, sorted(chaves))
        self.assertEqual(len(chaves), len(set(chaves)))


@unittest.skipUnless(EXE, 'ProgBase.exe ausente (rode tools/obter_progbase.py ou defina PROGBASE_EXE)')
class TestDoExe(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tab = fo.extrair(EXE)

    def test_anti_stallo_do_exe(self):
        e = entrada(self.tab, 'FLAG_CONF3', 4)
        self.assertEqual((e['palavra'], e['mascara'], e['getter'], e['setter']),
                         (0, '0x0020', '0x42E754', '0x42E8D8'))
        self.assertTrue(e['coerente'])

    def test_progressioni_do_exe(self):
        e = entrada(self.tab, 'FLAG_CONF3', 3)
        self.assertEqual((e['palavra'], e['mascara']), (0, '0x0010'))
        self.assertIn('CheckProgressioni', [c['nome'] for c in e['controles']])

    def test_check_bate_com_versionado(self):
        r = subprocess.run([sys.executable, '-I', TOOL, '--exe', EXE, '--check'], capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)

    def test_idempotente(self):
        with tempfile.TemporaryDirectory() as d:
            a, b = os.path.join(d, 'a.json'), os.path.join(d, 'b.json')
            for p in (a, b):
                subprocess.run([sys.executable, '-I', TOOL, '--exe', EXE, '--out', p], check=True, capture_output=True)
            with open(a, 'rb') as fa, open(b, 'rb') as fb:
                self.assertEqual(fa.read(), fb.read())

    def test_mutacao_de_um_byte_muda_a_tabela_e_quebra_o_check(self):
        with tempfile.TemporaryDirectory() as d:
            mut = os.path.join(d, 'mut.exe')
            shutil.copyfile(EXE, mut)
            off = fo.Binario(EXE).va2off(0x42E7EC + 6)  # imm8 de 'test byte ptr [eax+0x2994], 0x20' (getter caso 4)
            with open(mut, 'r+b') as fh:
                fh.seek(off)
                self.assertEqual(fh.read(1), b'\x20')
                fh.seek(off)
                fh.write(b'\x40')
            t = fo.extrair(mut)
            e = entrada(t, 'FLAG_CONF3', 4)
            self.assertEqual(e['getter_bit'], {'palavra': 0, 'mascara': '0x0040'})
            self.assertFalse(e['coerente'])
            r = subprocess.run([sys.executable, '-I', TOOL, '--exe', mut, '--check'], capture_output=True, text=True)
            self.assertNotEqual(r.returncode, 0)


if __name__ == '__main__':
    unittest.main()
