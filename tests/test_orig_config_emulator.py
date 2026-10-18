"""Emulador da tela TFormConfig > Iniettori (região do anti-stallo) do ProgBase original.

Prova que o HTML sai do DFM + i18n + valores do fio, e não de números escritos à mão."""
import json
import os
import re
import subprocess
import sys
import tempfile
import unittest

ROOT = os.path.join(os.path.dirname(__file__), '..')
sys.path.insert(0, os.path.join(ROOT, 'tools', 'emulacao'))
import orig_config_emulator as emu  # noqa: E402

DFM = os.path.join(ROOT, 'docs', 'evidence', 'dfm-original', 'TFORMCONFIG.txt')
I18N = '/home/user/protocolo-landi/dfm/i18n_all.json'
FIXTURE = os.path.join(ROOT, 'tests', 'fixtures', 'config-iniettori-lognovo-v1.json')
LOG = os.environ.get('PORTMON_LOGNOVO', '/tmp/claude-0/-home-user/57e20309-4640-5e31-b02d-4b2faf9cf2b3/'
                     'scratchpad/portmon/x/PortmonLOGNOVO.LOG')


def valores_reais():
    with open(FIXTURE, encoding='utf-8') as fh:
        v = json.load(fh)['vetores']
    return {'ANTI_STALLO': v['ANTI_STALLO']['u16'], 'FLAG_CONF3': v['FLAG_CONF3']['u16']}


def ctrl(html, nome):
    m = re.search(r'<div[^>]*data-nome="%s"[^>]*>' % nome, html)
    assert m, nome
    tag = m.group(0)
    est = re.search(r'style="([^"]*)"', tag).group(1)
    attrs = dict(re.findall(r'data-([a-z]+)="([^"]*)"', tag))
    px = dict((k, int(v)) for k, v in re.findall(r'(left|top|width|height):(-?\d+)px', est))
    return attrs, px, html[m.end():html.index('</div>', m.end())]


class TestDoDFM(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(DFM, encoding='utf-8') as fh:
            cls.dfm = fh.read()
        cls.raiz = emu.parse_dfm(cls.dfm)

    def test_posicoes_dos_5_edits_e_da_caixa_vem_do_dfm(self):
        esperado = {'EditAntiStallo0': (548, 1159), 'EditAntiStallo1': (548, 1198), 'EditAntiStallo2': (548, 1237),
                    'EditAntiStallo3': (548, 1277), 'EditAntiStallo4': (548, 1317), 'CheckAntiStallo': (38, 1120)}
        painel = emu.achar(self.raiz, 'PanelSubInjection')
        for nome, (l, t) in esperado.items():
            o = emu.achar(painel, nome)
            self.assertEqual((o.props['Left'], o.props['Top']), (l, t), nome)
        html = emu.render(self.dfm, emu.carregar_i18n(I18N), valores_reais(), caixa=None)
        y0 = emu.Y0_PADRAO
        for nome, (l, t) in esperado.items():
            _, px, _ = ctrl(html, nome)
            self.assertEqual((px['left'], px['top']), (l, t - y0), nome)

    def test_legendas_vem_do_i18n_por_ou_do_dfm(self):
        i18n = emu.carregar_i18n(I18N)
        html = emu.render(self.dfm, i18n, valores_reais(), caixa='on')
        for nome in ['CheckAntiStallo', 'LabelAntiStallo0', 'LabelAntiStallo4']:
            _, _, texto = ctrl(html, nome)
            self.assertIn(emu.html_esc(i18n[nome.upper()]['por']), texto, nome)
        # LabAntiStallo* (unidades) não tem chave no i18n -> legenda do DFM
        self.assertNotIn('LABANTISTALLO1', i18n)
        self.assertIn('rpm/ciclo', ctrl(html, 'LabAntiStallo1')[2])
        html_ita = emu.render(self.dfm, i18n, valores_reais(), caixa='on', idioma='ita')
        self.assertIn('Abilitazione anti-stallo', ctrl(html_ita, 'CheckAntiStallo')[2])


class TestValoresReais(unittest.TestCase):
    def test_fixture_tem_os_valores_do_fio(self):
        v = valores_reais()
        self.assertEqual(v['ANTI_STALLO'], [1600, 100, 1500, 2, 20])
        self.assertEqual(v['FLAG_CONF3'], [0, 0])

    @unittest.skipUnless(os.path.exists(LOG), 'log Portmon do dono não está nesta máquina')
    def test_fixture_bate_com_o_log(self):
        sys.path.insert(0, os.path.join(ROOT, 'tools', 'sessoes'))
        import portmon_ler_vetor as plv
        with open(LOG, encoding='latin1') as fh:
            linhas = fh.read().split('\n')
        for nome, cod in [('ANTI_STALLO', 307), ('FLAG_CONF3', 250)]:
            achados = plv.ler(linhas, cod)
            self.assertTrue(achados and all(a['checksum_ok'] for a in achados))
            self.assertEqual({tuple(a['u16']) for a in achados}, {tuple(valores_reais()[nome])})

    def test_caixa_real_desmarcada_e_edits_ocultos(self):
        with open(DFM, encoding='utf-8') as fh:
            html = emu.render(fh.read(), emu.carregar_i18n(I18N), valores_reais(), caixa=None)
        a, _, _ = ctrl(html, 'CheckAntiStallo')
        self.assertEqual(a['checked'], '0')
        for i in range(5):
            self.assertEqual(ctrl(html, 'EditAntiStallo%d' % i)[0]['visivel'], '0')
            self.assertEqual(ctrl(html, 'LabelAntiStallo%d' % i)[0]['visivel'], '0')

    def test_caixa_on_mostra_os_valores_do_dono(self):
        with open(DFM, encoding='utf-8') as fh:
            html = emu.render(fh.read(), emu.carregar_i18n(I18N), valores_reais(), caixa='on')
        self.assertEqual(ctrl(html, 'CheckAntiStallo')[0]['checked'], '1')
        for i, v in enumerate([1600, 100, 1500, 2, 20]):
            a, _, texto = ctrl(html, 'EditAntiStallo%d' % i)
            self.assertEqual(a['visivel'], '1')
            self.assertIn('>%d<' % v, texto + '<')

    def test_bit_da_flag_liga_a_caixa(self):
        self.assertFalse(emu.anti_stallo_marcado([0, 0]))
        self.assertTrue(emu.anti_stallo_marcado([0x20, 0]))
        self.assertFalse(emu.anti_stallo_marcado([0, 0x20]))


class TestMutacaoEIdempotencia(unittest.TestCase):
    def test_mutacao_left_do_edit_no_dfm_muda_o_html(self):
        with open(DFM, encoding='utf-8') as fh:
            dfm = fh.read()
        i18n = emu.carregar_i18n(I18N)
        base = emu.render(dfm, i18n, valores_reais(), caixa='on')
        alvo = '          object EditAntiStallo2: TNumericEdit\n            Left = 548\n'
        self.assertEqual(dfm.count(alvo), 1)
        mut = dfm.replace(alvo, alvo.replace('Left = 548', 'Left = 300'))
        html = emu.render(mut, i18n, valores_reais(), caixa='on')
        self.assertNotEqual(base, html)
        self.assertEqual(ctrl(html, 'EditAntiStallo2')[1]['left'], 300)
        self.assertEqual(ctrl(base, 'EditAntiStallo2')[1]['left'], 548)

    def test_duas_execucoes_bytes_iguais(self):
        script = os.path.join(ROOT, 'tools', 'emulacao', 'orig_config_emulator.py')
        with tempfile.TemporaryDirectory() as td:
            vj = os.path.join(td, 'v.json')
            with open(vj, 'w') as fh:
                json.dump(valores_reais(), fh)
            saidas = []
            for k in range(2):
                out = os.path.join(td, 'o%d.html' % k)
                subprocess.run([sys.executable, '-I', script, '--dfm', DFM, '--i18n', I18N, '--valores', vj,
                                '--caixa', 'off', '--saida', out], check=True)
                with open(out, 'rb') as fh:
                    saidas.append(fh.read())
            self.assertEqual(saidas[0], saidas[1])
            self.assertIn(b'EMULADO', saidas[0])


if __name__ == '__main__':
    unittest.main()
