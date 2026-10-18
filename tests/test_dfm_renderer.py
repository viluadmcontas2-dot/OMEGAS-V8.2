"""Renderer genérico do TFormConfig (todas as abas) a partir do DFM do ProgBase original.

As contagens e posições esperadas saem de um leitor independente (linha a linha) do DFM, não do renderer."""
import copy
import hashlib
import html.parser
import json
import os
import re
import subprocess
import sys
import tempfile
import unittest

ROOT = os.path.join(os.path.dirname(__file__), '..')
sys.path.insert(0, os.path.join(ROOT, 'tools', 'emulacao'))
import dfm_renderer as R  # noqa: E402

DFM = os.path.join(ROOT, 'docs', 'evidence', 'dfm-original', 'TFORMCONFIG.txt')
FIXTURE = os.path.join(ROOT, 'tests', 'fixtures', 'config-iniettori-lognovo-v1.json')
TEM_I18N = os.path.isfile(R.I18N_PADRAO)
NAO_VISUAL = re.compile(r'^(TMainMenu|TMenuItem|TPopupMenu|TTimer|TBalloonHint|T\w*Series)$')


def ler_dfm_independente(texto):
    """[(nome, classe, profundidade, pai, props)] com um leitor mínimo, separado do renderer."""
    nos, pilha = [], []
    for linha in texto.split('\n'):
        s = linha.strip()
        m = re.match(r'(?:object|inherited|inline) (\w+): (\w+)$', s)
        if m:
            no = {'nome': m.group(1), 'classe': m.group(2), 'pai': pilha[-1] if pilha else None, 'props': {}}
            nos.append(no)
            pilha.append(no)
        elif s == 'end':
            pilha.pop()
        elif pilha and ' = ' in s:
            k, v = s.split(' = ', 1)
            pilha[-1]['props'][k] = v
    return nos


def esperado_por_aba(nos, aba):
    """Controles visuais da aba, sem os das sub-abas inativas do PageControl interno (ActivePage do DFM)."""
    def dentro(n, alvo):
        while n is not None:
            if n is alvo:
                return True
            n = n['pai']
        return False

    alvo = next(n for n in nos if n['nome'] == aba)
    out = []
    for n in nos:
        if n is alvo or not dentro(n, alvo) or NAO_VISUAL.match(n['classe']):
            continue
        p, inativa = n, False
        while p is not alvo:
            if p['classe'] == 'TTabSheet':
                pc = p['pai']
                ativa = pc['props'].get('ActivePage') or next(x['nome'] for x in nos
                                                               if x['pai'] is pc and x['classe'] == 'TTabSheet')
                if p['nome'] != ativa:
                    inativa = True
            p = p['pai']
        if not inativa:
            out.append(n['nome'])
    return out


class Coletor(html.parser.HTMLParser):
    """Elementos com data-nome dentro da folha (data-folha=1) da aba."""

    def __init__(self, aba):
        super().__init__()
        self.aba, self.nivel, self.dentro, self.achados, self.pilha = aba, 0, None, [], []

    VAZIAS = {'br', 'img', 'input', 'hr', 'meta', 'col', 'wbr'}

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag not in self.VAZIAS:
            self.pilha.append(tag)
        if self.dentro is None and a.get('data-folha') == '1' and a.get('data-nome') == self.aba:
            self.dentro = len(self.pilha)
            return
        if isinstance(self.dentro, int) and self.dentro > 0 and 'data-nome' in a and a.get('data-origem') == 'dfm':
            self.achados.append(a)

    def handle_endtag(self, tag):
        if self.pilha and tag not in self.VAZIAS:
            if self.dentro == len(self.pilha):
                self.dentro = -1
            self.pilha.pop()


def controles(pagina, aba):
    c = Coletor(aba)
    c.feed(pagina)
    return c.achados


def atributos(pagina, nome):
    m = re.search(r'<div[^>]*data-nome="%s"[^>]*>' % re.escape(nome), pagina)
    assert m, nome
    tag = m.group(0)
    attrs = dict(re.findall(r'data-([a-z-]+)="([^"]*)"', tag))
    est = re.search(r'style="([^"]*)"', tag)
    px = dict((k, int(v)) for k, v in re.findall(r'(left|top|width|height):(-?\d+)px', est.group(1) if est else ''))
    return attrs, px, tag


def valores_reais():
    with open(FIXTURE, encoding='utf-8') as fh:
        return json.load(fh)


class Base(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(DFM, encoding='utf-8') as fh:
            cls.texto = fh.read()
        cls.nos = ler_dfm_independente(cls.texto)
        cls.raiz = R.preparar(cls.texto)
        cls.i18n = R.carregar_i18n(R.I18N_PADRAO)
        cls.regras = R.carregar_regras(R.REGRAS_PADRAO)

    def render(self, aba, valores=None, regras=None, raiz=None):
        return R.render_aba(raiz or self.raiz, aba, self.i18n, regras or self.regras,
                            valores if valores is not None else valores_reais())[0]


class TestAbasEContagem(Base):
    def test_33_abas_do_dfm(self):
        esperadas = [n['nome'] for n in self.nos if n['classe'] == 'TTabSheet']
        self.assertEqual(len(esperadas), 33)
        self.assertEqual([t.nome for t in R.listar_abas(self.raiz)], esperadas)

    def test_contagem_de_controles_por_aba_bate_com_o_dfm(self):
        for t in R.listar_abas(self.raiz):
            with self.subTest(aba=t.nome):
                pagina = self.render(t.nome)
                achados = [a['data-nome'] for a in controles(pagina, t.nome)]
                self.assertEqual(sorted(achados), sorted(esperado_por_aba(self.nos, t.nome)))

    def test_cabecalhos_de_todas_as_abas_do_pagecontrol_principal(self):
        pagina = self.render('TabCambio')
        cab = re.findall(r'data-cabecalho="(\w+)"', pagina)
        principais = [n['nome'] for n in self.nos if n['classe'] == 'TTabSheet' and n['pai']['nome'] == 'PageControl']
        self.assertEqual(cab, [n for n in principais if n not in self._ocultas()] +
                         [n for n in principais if n in self._ocultas()])

    def _ocultas(self):
        return {n['nome'] for n in self.nos if n['props'].get('TabVisible') == 'False'} - {'TabCambio'}


class TestPosicoes(Base):
    def test_anti_stallo_na_posicao_do_dfm(self):
        p = self.render('TabIniezione')
        _, px, _ = atributos(p, 'CheckAntiStallo')
        self.assertEqual((px['left'], px['top'], px['width'], px['height']), (38, 1120, 715, 32))
        _, px, _ = atributos(p, 'EditAntiStallo0')
        self.assertEqual((px['left'], px['top'], px['width'], px['height']), (548, 1159, 72, 37))

    def test_scrollbox_desfaz_a_rolagem_de_projeto(self):
        # ScrollBoxCambio tem VertScrollBar.Position = 671 no DFM: Top -612 vira 59 no conteúdo desenrolado
        _, px, _ = atributos(self.render('TabCambio'), 'LabelTipoIniettore')
        self.assertEqual((px['left'], px['top']), (38, -612 + 671))

    def test_todos_os_controles_sem_align_tem_left_top_do_dfm(self):
        por_nome = {n['nome']: n for n in self.nos}
        for aba in ('TabCambio', 'TabLambda', 'TabSensore', 'TabIniezione', 'TabBenzina', 'TabKTempi'):
            p = self.render(aba)
            for a in controles(p, aba):
                n = por_nome[a['data-nome']]
                pr = n['props']
                if 'Align' in pr or 'Left' not in pr or 'Top' not in pr or n['pai']['classe'] == 'TGridPanel':
                    continue
                pos = 0
                if n['pai']['classe'] == 'TScrollBox':
                    pos = int(n['pai']['props'].get('VertScrollBar.Position', 0))
                _, px, _ = atributos(p, a['data-nome'])
                with self.subTest(aba=aba, nome=a['data-nome']):
                    self.assertEqual((px['left'], px['top']), (int(pr['Left']), int(pr['Top']) + pos))
                    self.assertEqual(px['width'], int(pr['Width']))

    def test_cor_tcolor_bgr(self):
        self.assertEqual(R.cor_css(10158080), '#00009B')
        _, _, tag = atributos(self.render('TabIniezione'), 'CheckAntiStallo')
        self.assertIn('background:#E3E3E3', tag)   # cl3DLight, ParentColor=False


class TestRegras(Base):
    def test_toda_regra_tem_marca_valida(self):
        for r in self.regras['regras']:
            self.assertIn(r['marca'], ('OBS-COD', 'INFERIDO', 'DESCONHECIDO'), r['id'])

    def test_anti_stallo_real_caixa_desmarcada_edits_ocultos(self):
        p = self.render('TabIniezione')
        a, _, _ = atributos(p, 'CheckAntiStallo')
        self.assertEqual((a['checked'], a['marca-checked']), ('0', 'OBS-COD'))
        self.assertEqual(a['habilitado'], '?')               # capacidade DESCONHECIDA
        for i in range(5):
            self.assertEqual(atributos(p, 'EditAntiStallo%d' % i)[0]['visivel'], '0')

    def test_anti_stallo_marcado_e_habilitado_mostra_os_5_valores(self):
        v = valores_reais()
        v['vetores']['FLAG_CONF3']['u16'] = [0x20, 0]
        v['controles'] = {'CheckAntiStallo': {'Enabled': True}}
        p = self.render('TabIniezione', v)
        vals = [atributos(p, 'EditAntiStallo%d' % i)[0] for i in range(5)]
        self.assertEqual([x['visivel'] for x in vals], ['1'] * 5)
        self.assertEqual([x['valor'] for x in vals], ['1600', '100', '1500', '2', '20'])

    def test_marcado_com_capacidade_desconhecida_fica_tracejado_nao_inventado(self):
        v = valores_reais()
        v['vetores']['FLAG_CONF3']['u16'] = [0x20, 0]
        a, _, tag = atributos(self.render('TabIniezione', v), 'EditAntiStallo0')
        self.assertEqual(a['visivel'], '?')
        self.assertIn('tracejado', tag)

    def test_sem_valores_tudo_que_vem_da_ecu_fica_desconhecido(self):
        p = self.render('TabIniezione', {})
        a, _, _ = atributos(p, 'CheckAntiStallo')
        self.assertEqual(a['checked'], '?')
        self.assertEqual(atributos(p, 'EditAntiStallo0')[0]['valor'], '—')

    def test_visible_false_do_dfm_sem_regra_fica_tracejado(self):
        a, _, tag = atributos(self.render('TabCambio'), 'LabelRitardoCambioGas')
        self.assertEqual((a['visivel'], a['marca-visible']), ('?', 'DESCONHECIDO'))
        self.assertIn('tracejado', tag)

    def test_outras_caixas_com_bit_provado(self):
        p = self.render('TabBenzina')
        self.assertEqual(atributos(p, 'CheckIstantaneo')[0]['checked'], '1')   # FLAG_CONF1[1]=0x1051 tem 0x1000


class TestTextos(Base):
    @unittest.skipUnless(TEM_I18N, 'i18n_all.json ausente')
    def test_i18n_por_com_acentos_e_fallback_eng(self):
        p = self.render('TabCambio')
        self.assertIn('>Mudança do gás<', p)
        a, _, _ = atributos(self.render('TabIniezione'), 'CheckAntiStallo')
        self.assertEqual(a['texto-fonte'], 'i18n-por')
        self.assertIn('Enable un-stalling strategy', self.render('TabIniezione'))

    @unittest.skipUnless(TEM_I18N, 'i18n_all.json ausente')
    def test_itens_do_combo_vem_do_dfm_traduzidos(self):
        a, _, _ = atributos(self.render('TabCambio'), 'ComboCambio')
        self.assertEqual(len(a['itens'].split(' | ')), 3)
        self.assertEqual(a['valor'], '—')   # R10: qual item é o bit 0 é DESCONHECIDO


class TestDeterminismoECLI(Base):
    def test_idempotente(self):
        self.assertEqual(self.render('TabIniezione'), self.render('TabIniezione'))

    def test_cli_todas_gera_33_html_iguais_em_duas_rodadas(self):
        hashes = []
        for _ in range(2):
            with tempfile.TemporaryDirectory() as d:
                subprocess.run([sys.executable, os.path.join(ROOT, 'tools', 'emulacao', 'dfm_renderer.py'),
                                '--dfm', DFM, '--aba', 'todas', '--valores', FIXTURE, '--saida', d],
                               check=True, capture_output=True)
                arqs = sorted(os.listdir(d))
                self.assertEqual(len(arqs), 33)
                hashes.append([hashlib.sha256(open(os.path.join(d, a), 'rb').read()).hexdigest() for a in arqs])
        self.assertEqual(hashes[0], hashes[1])


class TestMutacao(Base):
    def test_mutar_left_no_dfm_muda_a_tela(self):
        texto = self.texto.replace('          object CheckAntiStallo: TCheckBox\n            Left = 38',
                                   '          object CheckAntiStallo: TCheckBox\n            Left = 45')
        self.assertNotEqual(texto, self.texto)
        _, px, _ = atributos(self.render('TabIniezione', raiz=R.preparar(texto)), 'CheckAntiStallo')
        self.assertEqual(px['left'], 45)

    def test_mutar_mascara_da_regra_inverte_a_caixa(self):
        v = valores_reais()
        v['vetores']['FLAG_CONF3']['u16'] = [0x10, 0]
        self.assertEqual(atributos(self.render('TabIniezione', v), 'CheckAntiStallo')[0]['checked'], '0')
        mut = copy.deepcopy(self.regras)
        next(r for r in mut['regras'] if r['id'] == 'R01')['de']['bit']['mascara'] = 0x10
        self.assertEqual(atributos(self.render('TabIniezione', v, regras=mut), 'CheckAntiStallo')[0]['checked'], '1')

    def test_remover_controle_do_dfm_quebra_a_contagem(self):
        texto = re.sub(r'\n {8}object LabelTipoIniettore: TLabel\n(?: {10}.*\n)+? {8}end', '', self.texto, count=1)
        self.assertNotEqual(texto, self.texto)
        achados = [a['data-nome'] for a in controles(self.render('TabCambio', raiz=R.preparar(texto)), 'TabCambio')]
        self.assertNotEqual(sorted(achados), sorted(esperado_por_aba(self.nos, 'TabCambio')))

    def test_marca_invalida_e_recusada(self):
        mut = copy.deepcopy(self.regras)
        mut['regras'][0]['marca'] = 'CHUTE'
        with self.assertRaises(ValueError):
            self.render('TabIniezione', regras=mut)


if __name__ == '__main__':
    unittest.main()
