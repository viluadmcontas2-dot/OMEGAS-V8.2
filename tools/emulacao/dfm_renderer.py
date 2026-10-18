#!/usr/bin/env python3
"""Renderer genérico de DFM: desenha cada aba (TTabSheet) do TFormConfig do ProgBase original como HTML.

Entradas: DFM decodificado (TFORMCONFIG.txt), i18n_all.json (chave = NOME em maiúsculas; 'por', senão
'eng', senão Caption do DFM), tabela de regras (regras_tformconfig.json) e valores da ECU (JSON opcional).
Saída: HTML autocontido por aba; com --png, foto via Chromium headless. Só stdlib; determinístico.

O que vem do DFM (OBS-COD: recurso do executável): árvore, classe, Left/Top/Width/Height, Align,
Caption/Text, fonte, cores TColor (BGR), Visible/Enabled/TabVisible de projeto, TabOrder, itens de combo,
posição das barras de rolagem. O que depende de código fica na tabela de regras com marca própria;
DESCONHECIDO é desenhado tracejado e nunca vira valor.

Uso: dfm_renderer.py --aba TabIniezione|todas --valores v.json --saida out.html|pasta [--png]
"""
import argparse
import glob
import html
import json
import os
import re
import subprocess
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.normpath(os.path.join(AQUI, '..', '..'))
DFM_PADRAO = os.path.join(RAIZ, 'docs', 'evidence', 'dfm-original', 'TFORMCONFIG.txt')
I18N_PADRAO = '/home/user/protocolo-landi/dfm/i18n_all.json'
FRAMES_PADRAO = '/home/user/protocolo-landi/dfm'
REGRAS_PADRAO = os.path.join(AQUI, 'regras_tformconfig.json')
LEGENDA = 'EMULADO DO DFM'
MARCAS = ('OBS-COD', 'INFERIDO', 'DESCONHECIDO')

# Classes sem janela própria (não são controles na tela). Séries de TChart entram no próprio gráfico.
NAO_VISUAIS = {'TMainMenu', 'TMenuItem', 'TPopupMenu', 'TTimer', 'TBalloonHint', 'TImageList', 'TActionList',
               'TAction', 'TOpenDialog', 'TSaveDialog', 'TDataSource'}
# Cores de sistema (o DFM só dá o nome): valores do Windows 10 padrão -> INFERIDO.
CORES_SISTEMA = {'cl3DLight': '#E3E3E3', 'clBtnFace': '#F0F0F0', 'clWindow': '#FFFFFF', 'clWindowText': '#000000',
                 'clBlack': '#000000', 'clWhite': '#FFFFFF', 'clRed': '#FF0000', 'clBlue': '#0000FF',
                 'clNavy': '#000080', 'clSilver': '#C0C0C0', 'clGray': '#808080', 'clYellow': '#FFFF00',
                 'clInfoBk': '#FFFFE1', 'clGreen': '#008000', 'clLime': '#00FF00', 'clMaroon': '#800000',
                 'clBtnText': '#000000', 'clGrayText': '#6D6D6D', 'clHighlight': '#0078D7', 'cl3DDkShadow': '#696969'}
FONTE_PADRAO = {'nome': 'Tahoma', 'px': 11, 'cor': '#000000', 'negrito': False}
CLASSES_EDIT = ('TEdit', 'TNumericEdit', 'TValueEditEx', 'TMaskEdit')
CLASSES_COMBO = ('TKeyComboBox', 'TComboBox')
CLASSES_CAIXA = ('TCheckBox', 'TRadioButton', 'TAebCheckBox')
TAB_PRINCIPAL_BORDA = 4      # INFERIDO: 1162 - 994 (folha) - 160 (TabHeight) = 8 = 2 x 4
TAB_BOTOES_ALTURA = 34       # INFERIDO: faixa de botões de um TPageControl Style=tsButtons


# ------------------------------------------------------------------ DFM
class No:
    def __init__(self, nome, classe, linha, origem='dfm'):
        self.nome, self.classe, self.linha, self.origem = nome, classe, linha, origem
        self.props, self.filhos, self.pai = {}, [], None
        self.fonte = FONTE_PADRAO        # fonte efetiva, preenchida ao desenhar

    def p(self, k, d=None):
        return self.props.get(k, d)


def _valor(v):
    v = v.strip()
    if v.startswith("'") or (v.startswith('#') and re.match(r"#\d", v)):
        partes = re.findall(r"'((?:[^']|'')*)'|#(\d+)", v)
        return ''.join(chr(int(n)) if n else s.replace("''", "'") for s, n in partes)
    if re.fullmatch(r'-?\d+', v):
        return int(v)
    if re.fullmatch(r'-?\d+\.\d+(?:[eE][-+]?\d+)?', v):
        return float(v)
    return v


def parse_dfm(texto, origem='dfm'):
    """Árvore do DFM texto (object/inherited/inline ... end; uma propriedade por linha)."""
    raiz, pilha = None, []
    for n, linha in enumerate(texto.split('\n'), 1):
        s = linha.strip()
        m = re.match(r'(?:object|inherited|inline) (\w+): (\w+)$', s)
        if m:
            o = No(m.group(1), m.group(2), n, origem)
            if pilha:
                o.pai = pilha[-1]
                pilha[-1].filhos.append(o)
            else:
                raiz = o
            pilha.append(o)
        elif s == 'end' and pilha:
            pilha.pop()
        elif pilha and ' = ' in s:
            k, v = s.split(' = ', 1)
            pilha[-1].props[k] = _valor(v)
    return raiz


def _fundir(base, inline):
    """Frame (TAutoCalUI...): propriedades do DFM-base + sobrescritas do formulário; filhos casados pelo nome."""
    base.props.update(inline.props)
    base.nome, base.linha, base.origem = inline.nome, inline.linha, inline.origem
    por_nome = {f.nome: f for f in base.filhos}
    for f in inline.filhos:
        if f.nome in por_nome:
            _fundir(por_nome[f.nome], f)
        else:
            f.pai = base
            base.filhos.append(f)
    return base


def expandir_frames(raiz, pasta):
    """Troca cada frame cujo DFM-base existe em `pasta` (<CLASSE>.txt) pela fusão base+sobrescritas."""
    if not pasta:
        return raiz
    for i, f in enumerate(list(raiz.filhos)):
        arq = os.path.join(pasta, f.classe.upper() + '.txt')
        if f.pai is not None and f.classe.endswith('UI') and os.path.isfile(arq):
            with open(arq, encoding='utf-8') as fh:
                base = parse_dfm(fh.read(), origem='frame')
            novo = _fundir(base, f)
            novo.pai = raiz
            raiz.filhos[i] = novo
        expandir_frames(raiz.filhos[i], pasta)
    return raiz


def iterar(o):
    yield o
    for f in o.filhos:
        yield from iterar(f)


def achar(o, nome):
    for x in iterar(o):
        if x.nome == nome:
            return x
    return None


def visual(o):
    return o.classe not in NAO_VISUAIS and not o.classe.endswith('Series')


def listar_abas(raiz):
    return [o for o in iterar(raiz) if o.classe == 'TTabSheet']


# ------------------------------------------------------------------ textos e cores
def conserta(s):
    """O i18n_all.json guarda UTF-8 lido como Latin-1 ('MudanÃ§a'); desfaz quando der."""
    if not isinstance(s, str):
        return s
    try:
        return s.encode('latin-1').decode('utf-8')
    except (UnicodeEncodeError, UnicodeDecodeError):
        return s


def carregar_i18n(caminho):
    if not caminho or not os.path.isfile(caminho):
        return {}
    with open(caminho, encoding='utf-8') as fh:
        return json.load(fh)


def traduz(chave, i18n, idioma, padrao):
    ent = i18n.get(chave.upper()) or {}
    for k, fonte in ((idioma, 'i18n-' + idioma), ('eng', 'i18n-eng')):
        t = ent.get(k)
        if t and t != '#ERROR!':
            return conserta(t), fonte
    return padrao, 'dfm'


def sem_atalho(s):
    return str(s).replace('&&', '\0').replace('&', '').replace('\0', '&')


def cor_css(c, padrao=None):
    if c is None:
        return padrao
    if isinstance(c, str):
        return CORES_SISTEMA.get(c, padrao)
    if c < 0:
        return padrao
    return '#%02X%02X%02X' % (c & 0xFF, (c >> 8) & 0xFF, (c >> 16) & 0xFF)


def fonte_de(o, herdada):
    """Fonte efetiva: Font.* próprio quando ParentFont=False, senão herda (VCL)."""
    if o.p('ParentFont') != 'False' and 'Font.Height' not in o.props:
        return herdada
    f = dict(herdada)
    if 'Font.Name' in o.props:
        f['nome'] = o.p('Font.Name')
    h = o.p('Font.Height')
    if isinstance(h, int):
        f['px'] = -h if h < 0 else max(1, round(h * 0.8))
    if 'Font.Color' in o.props:
        f['cor'] = cor_css(o.p('Font.Color'), f['cor'])
    if 'Font.Style' in o.props:
        f['negrito'] = 'fsBold' in str(o.p('Font.Style'))
    return f


def css_fonte(f):
    return "font:%s %dpx '%s',Arial,sans-serif;color:%s" % ('bold' if f['negrito'] else 'normal', f['px'],
                                                             f['nome'], f['cor'])


# ------------------------------------------------------------------ regras (três estados: True/False/None)
class Regras:
    def __init__(self, tabela, valores):
        self.especificas, self.genericas = {}, []
        for r in tabela.get('regras', []):
            if r.get('marca') not in MARCAS:
                raise ValueError('regra %s sem marca valida' % r.get('id'))
            if r.get('generica'):
                self.genericas.append(r)
            else:
                for a in r['alvos']:
                    self.especificas[(a, r['prop'])] = r
        self.vetores = {}
        for k, v in (valores.get('vetores') or {}).items():
            self.vetores[k] = v['u16'] if isinstance(v, dict) else list(v)
        self.controles = valores.get('controles') or {}
        self.nos, self._cache, self._pilha = {}, {}, set()

    def indexar(self, raiz):
        for o in iterar(raiz):
            self.nos.setdefault(o.nome, o)

    def _regra(self, o, prop):
        r = self.especificas.get((o.nome, prop))
        if r:
            return r
        for g in self.genericas:
            if g['prop'] != prop:
                continue
            if 'classes' in g and o.classe not in g['classes']:
                continue
            if any(str(o.p(k)) != str(v) for k, v in g.get('quando_dfm', {}).items()):
                continue
            return g
        return None

    def valor(self, o, prop):
        """(valor, marca, id_regra). valor None = DESCONHECIDO."""
        chave = (o.nome, prop)
        if chave in self._cache:
            return self._cache[chave]
        if o.nome in self.controles and prop in self.controles[o.nome]:
            res = (self.controles[o.nome][prop], 'VALOR-DADO', 'valores')
        elif chave in self._pilha:
            res = (None, 'DESCONHECIDO', 'ciclo')
        else:
            self._pilha.add(chave)
            r = self._regra(o, prop)
            if r is None:
                res = (self._dfm(o, prop), 'OBS-COD', 'dfm')
            else:
                res = (self._avaliar(r['de'], o, prop), r['marca'], r['id'])
            self._pilha.discard(chave)
        self._cache[chave] = res
        return res

    def _dfm(self, o, prop):
        if prop in ('Visible', 'Enabled', 'TabVisible'):
            return o.p(prop) != 'False'
        if prop == 'Checked':
            return o.p('Checked') == 'True' or o.p('State') == 'cbChecked'
        return None

    def _avaliar(self, de, o, prop):
        if de.get('desconhecido'):
            return None
        if de.get('dfm'):
            return self._dfm(o, prop)
        if 'bit' in de:
            b = de['bit']
            vet = self.vetores.get(b['vetor'])
            if vet is None or len(vet) <= b['palavra']:
                return None
            return bool(vet[b['palavra']] & b['mascara'])
        if 'vetor_por_dataindex' in de:
            vet = self.vetores.get(de['vetor_por_dataindex'])
            i = o.p('DataIndex')
            i = int(i) if isinstance(i, (int, str)) and str(i).lstrip('-').isdigit() else None
            if vet is None or i is None or not 0 <= i < len(vet):
                return None
            return vet[i]
        if 'e' in de:
            vs = []
            for ref in de['e']:
                alvo = self.nos.get(ref['controle'])
                vs.append(None if alvo is None else self.valor(alvo, ref['prop'])[0])
            if any(v is False for v in vs):
                return False
            return None if any(v is None for v in vs) else True
        return None


# ------------------------------------------------------------------ geometria (VCL simplificada)
ORDEM_ALIGN = ('alTop', 'alBottom', 'alLeft', 'alRight', 'alClient')


def _num(v, d=0):
    return v if isinstance(v, int) else (int(v) if isinstance(v, float) else d)


def rolagem(o):
    if o.classe != 'TScrollBox':
        return 0, 0
    return _num(o.p('HorzScrollBar.Position')), _num(o.p('VertScrollBar.Position'))


def margens(o):
    if o.p('AlignWithMargins') != 'True':
        return 0, 0, 0, 0
    return tuple(_num(o.p('Margins.' + k), 3) for k in ('Left', 'Top', 'Right', 'Bottom'))


def area_cliente(o, w, h):
    """Retângulo onde o Align distribui os filhos (AdjustClientRect)."""
    i = 0
    if o.classe in ('TPanel', 'TGridPanel'):
        i = (o.p('BevelOuter') != 'bvNone') + (o.p('BevelInner') not in (None, 'bvNone'))
        i += _num(o.p('BorderWidth'))
    if o.classe == 'TGroupBox':
        return 2, 18, max(0, w - 4), max(0, h - 20)       # INFERIDO
    return i, i, max(0, w - 2 * i), max(0, h - 2 * i)


def extensao(o):
    """Altura que o controle pede quando a rolagem é expandida (scrollbox desenrolado)."""
    h = _num(o.p('Height'), 0)
    if o.classe == 'TScrollBox':
        _, dy = rolagem(o)
        fundo = _num(o.p('VertScrollBar.Range'), 0)
        for f in o.filhos:
            if visual(f) and 'Top' in f.props:
                fundo = max(fundo, _num(f.p('Top')) + dy + extensao(f))
        return max(h, fundo)
    extra = 0
    for f in o.filhos:
        if visual(f) and f.p('Align') == 'alClient':
            extra = max(extra, extensao(f) - _num(f.p('Height'), h))
    return h + max(0, extra)


def extensao_folha(t):
    """TTabSheet não tem Height no DFM: altura pedida pelos filhos (alClient expandido ou Top+altura)."""
    alt = 0
    for f in t.filhos:
        if not visual(f) or f.classe == 'TTabSheet':
            continue
        if f.p('Align') == 'alClient':
            alt = max(alt, extensao(f))
        elif 'Top' in f.props:
            alt = max(alt, _num(f.p('Top')) + extensao(f))
    return alt


def layout(o, w, h):
    """{filho: (x, y, w, h)} no espaço do pai (já somada a rolagem do scrollbox)."""
    cx, cy, cw, ch = area_cliente(o, w, h)
    sx, sy = rolagem(o)
    filhos = [f for f in o.filhos if visual(f)]
    out = {}
    if o.classe == 'TGridPanel':
        cols = [float(x) for x in re.findall(r'Value=([\d.]+)', str(o.p('ColumnCollection', '')))] or [100.0]
        lins = [float(x) for x in re.findall(r'Value=([\d.]+)', str(o.p('RowCollection', '')))] or [100.0]
        cws = [cw * c / sum(cols) for c in cols]
        rhs = [ch * r / sum(lins) for r in lins]
        cel = {m.group(2).split('.')[-1]: (int(m.group(1)), int(m.group(3))) for m in
               re.finditer(r'Column=(\d+); Control=([\w.]+); Row=(\d+)', str(o.p('ControlCollection', '')))}
        for f in filhos:
            if f.nome in cel:
                c, r = cel[f.nome]
                c, r = min(c, len(cws) - 1), min(r, len(rhs) - 1)
                out[f] = (cx + round(sum(cws[:c])), cy + round(sum(rhs[:r])), round(cws[c]), round(rhs[r]))
    resto = [cx, cy, cw, ch]
    for al in ORDEM_ALIGN:
        grupo = [f for f in filhos if f.p('Align') == al and f not in out]
        chave = {'alTop': lambda f: _num(f.p('Top')), 'alBottom': lambda f: -_num(f.p('Top')),
                 'alLeft': lambda f: _num(f.p('Left')), 'alRight': lambda f: -_num(f.p('Left'))}.get(al)
        if chave:
            grupo.sort(key=chave)
        for f in grupo:
            ml, mt, mr, mb = margens(f)
            x, y, rw, rh = resto
            fw = _num(f.p('Width'), 50) + ml + mr
            fh = (extensao(f) if al in ('alTop', 'alBottom') else _num(f.p('Height'), 20)) + mt + mb
            if al == 'alTop':
                r = (x, y, rw, fh); resto = [x, y + fh, rw, max(0, rh - fh)]
            elif al == 'alBottom':
                r = (x, y + rh - fh, rw, fh); resto = [x, y, rw, max(0, rh - fh)]
            elif al == 'alLeft':
                r = (x, y, fw, rh); resto = [x + fw, y, max(0, rw - fw), rh]
            elif al == 'alRight':
                r = (x + rw - fw, y, fw, rh); resto = [x, y, max(0, rw - fw), rh]
            else:
                r = tuple(resto)
            out[f] = (r[0] + ml, r[1] + mt, max(0, r[2] - ml - mr), max(0, r[3] - mt - mb))
    for f in filhos:
        if f not in out:
            fh = extensao(f) if f.classe == 'TScrollBox' else _num(f.p('Height'), 0)
            out[f] = (_num(f.p('Left')) + sx, _num(f.p('Top')) + sy, _num(f.p('Width'), 0), fh)
    return {f: out[f] for f in filhos}       # ordem do DFM


def tem_geometria(o):
    return o.p('Align') not in (None, 'alNone') or ('Left' in o.props or 'Top' in o.props) and 'Width' in o.props


# ------------------------------------------------------------------ desenho
CSS = """*{box-sizing:border-box}
body{margin:0;background:#FFFFFF;font-family:Arial,sans-serif;color:#000}
.cab{padding:4px 10px;font:12px/16px Arial,sans-serif;background:#FFF;border-bottom:1px solid #999;width:%dpx;
height:44px;overflow:hidden}
.cab b{font-size:16px;color:#B00000;letter-spacing:1px}
.form{position:relative;width:%dpx;height:%dpx;overflow:hidden}
.c{position:absolute;white-space:nowrap;overflow:hidden}
.solto{overflow:visible}
.lbl{line-height:1.15}
.lbl.auto{overflow:visible}
.lbl.quebra{white-space:normal}
.btn{border:1px solid #ADADAD;background:#E1E1E1;display:flex;align-items:center;justify-content:center}
.edit{background:#FFF;border:2px solid #7A7A7A;padding:0 4px;display:flex;align-items:center}
.combo{background:#FFF;border:1px solid #7A7A7A;padding:0 4px;display:flex;align-items:center}
.combo:after{content:'\\25BE';position:absolute;right:4px;color:#333}
.caixa{display:flex;align-items:center;gap:6px}
.box{flex:none;width:13px;height:13px;border:1px solid #333;background:#FFF;font:bold 11px/11px Arial;
text-align:center;color:#000}
.radio .box{border-radius:50%%}
.grupo{border:1px solid #A0A0A0}
.grupo>.leg{position:absolute;left:8px;top:-2px;padding:0 3px;background:inherit}
.grade{border:1px solid #888;background:#FFF}
.grade td{border:1px solid #C8C8C8;padding:0 2px;color:#999;font-size:11px;overflow:hidden}
.grafico{border:1px solid #888;background:#FFF;font:11px Arial;color:#444;padding:2px;white-space:normal}
.img{background:repeating-linear-gradient(45deg,#DDD 0 6px,#EEE 6px 12px);border:1px solid #BBB;font:10px Arial;color:#777}
.desab{color:#808080 !important}
.desab .box{border-color:#999}
.tracejado{outline:2px dashed #D07000;outline-offset:-2px}
.fantasma{opacity:.45;background-image:repeating-linear-gradient(135deg,rgba(208,112,0,.10) 0 4px,transparent 4px 8px)}
.fantasma>.nome{position:absolute;right:1px;top:0;font:9px Arial;color:#D07000;background:#FFF;z-index:2}
.oculto{visibility:hidden}
.tab{position:absolute;display:flex;align-items:center;padding:0 8px;border:1px solid #A0A0A0;background:#F0F0F0;
white-space:nowrap;overflow:hidden}
.tab.ativa{background:#FFFFFF;border-color:#555}
.semgeo{position:relative;display:inline-block;margin:2px;padding:1px 4px;font:10px Arial;color:#D07000;
border:1px dashed #D07000}
"""


class Desenho:
    def __init__(self, regras, i18n, idioma, forcados):
        self.r, self.i18n, self.idioma, self.forcados = regras, i18n, idioma, forcados
        self.ativas = {}          # TPageControl -> TTabSheet ativa

    def texto(self, o, chave=None, prop='Caption'):
        t, fonte = traduz(chave or o.nome, self.i18n, self.idioma, str(o.p(prop, '') or ''))
        return sem_atalho(t), fonte

    def no(self, o, x, y, w, h, fonte_pai):
        f = o.fonte = fonte_de(o, fonte_pai)
        vis, mvis, _ = self.r.valor(o, 'Visible')
        if o.nome in self.forcados:
            vis = True
        ena, mena, _ = self.r.valor(o, 'Enabled')
        classes, attrs = ['c'], {'nome': o.nome, 'classe': o.classe, 'linha': o.linha, 'origem': o.origem,
                                 'marca-visible': mvis}
        attrs['visivel'] = {True: '1', False: '0', None: '?'}[vis]
        attrs['habilitado'] = {True: '1', False: '0', None: '?'}[ena]
        if 'TabOrder' in o.props:
            attrs['taborder'] = o.p('TabOrder')
        if vis is False:
            classes.append('oculto')
        elif vis is None:
            classes += ['fantasma', 'tracejado']
        if ena is False:
            classes.append('desab')
        elif ena is None:
            classes.append('tracejado')
        estilo = ['left:%dpx;top:%dpx;width:%dpx;height:%dpx' % (x, y, w, h), css_fonte(f)]
        cor = o.p('Color')
        if cor is not None and o.p('ParentColor') != 'True' and not (o.classe == 'TLabel' and
                                                                     o.p('Transparent') == 'True'):
            estilo.append('background:' + cor_css(cor, 'transparent'))
        dentro = self.conteudo(o, w, h, f, classes, attrs)
        if vis is None:
            dentro += '<span class="nome">%s</span>' % html.escape(o.nome)
        a = ''.join(' data-%s="%s"' % (k, html.escape(str(v), quote=True)) for k, v in attrs.items())
        return '<div class="%s"%s style="%s">%s</div>' % (' '.join(classes), a, ';'.join(estilo), dentro)

    # --- conteúdo por classe
    def conteudo(self, o, w, h, f, classes, attrs):
        c = o.classe
        if c in ('TLabel', 'TStaticText'):
            t, attrs['texto-fonte'] = self.texto(o)
            classes.append('lbl')
            if o.p('AutoSize') != 'False':
                classes.append('auto')      # TLabel.AutoSize (padrão True): a largura segue o texto traduzido
            if o.p('WordWrap') == 'True':
                classes.append('quebra')
            al = {'taRightJustify': 'right', 'taCenter': 'center'}.get(o.p('Alignment'), 'left')
            return '<div style="text-align:%s">%s</div>' % (al, html.escape(t))
        if c in ('TButton', 'TBitBtn', 'TSpeedButton'):
            t, attrs['texto-fonte'] = self.texto(o)
            classes.append('btn')
            return html.escape(t)
        if c in CLASSES_CAIXA:
            t, attrs['texto-fonte'] = self.texto(o)
            chk, mchk, _ = self.r.valor(o, 'Checked')
            attrs['checked'] = {True: '1', False: '0', None: '?'}[chk]
            attrs['marca-checked'] = mchk
            classes.append('caixa')
            if c == 'TRadioButton':
                classes.append('radio')
            marca = {True: '&#10003;', False: '', None: '?'}[chk]
            caixa = '<span class="box%s">%s</span>' % (' tracejado' if chk is None else '', marca)
            rot = '<span>%s</span>' % html.escape(t)
            esq = o.p('Alignment') == 'taLeftJustify'
            return (rot + caixa) if esq else (caixa + rot)
        if c in CLASSES_EDIT or c in CLASSES_COMBO:
            v, mv, _ = self.r.valor(o, 'Texto')
            attrs['marca-texto'] = mv
            attrs['valor'] = '—' if v is None else v
            if c in CLASSES_COMBO:
                classes.append('combo')
                itens = re.findall(r"'((?:[^']|'')*)'", str(o.p('Items.Strings', '')))
                trad = [traduz('%s%d' % (o.nome, i + 1), self.i18n, self.idioma, s)[0] for i, s in enumerate(itens)]
                attrs['itens'] = ' | '.join(trad)
            else:
                classes.append('edit')
            just = 'flex-end' if o.p('Alignment') == 'taRightJustify' or c in ('TNumericEdit',) else 'flex-start'
            return '<span style="margin-left:%s">%s</span>' % ('auto' if just == 'flex-end' else '0',
                                                                html.escape(str(attrs['valor'])))
        if c == 'TGroupBox':
            t, attrs['texto-fonte'] = self.texto(o)
            classes.append('grupo')
            return '<span class="leg">%s</span>%s' % (html.escape(t), self.filhos(o, w, h, f))
        if c == 'TDrawGrid':
            return self.grade(o, w, h)
        if c == 'TChart':
            classes.append('grafico')
            series = [s.nome for s in o.filhos if s.classe.endswith('Series')]
            tit = ' '.join(re.findall(r"'((?:[^']|'')*)'", str(o.p('Title.Text.Strings', '') or '')))
            return '<b>%s</b> (TChart; dados em execução: DESCONHECIDO)<br>séries: %s %s' % (
                html.escape(o.nome), html.escape(', '.join(series) or '—'), html.escape(tit))
        if c == 'TImage':
            classes.append('img')
            return 'imagem (%s)' % html.escape(o.nome)
        if c == 'TShape':
            forma = str(o.p('Shape', 'stRectangle'))
            raio = '50%' if 'Circle' in forma or 'Ellipse' in forma else ('8px' if 'Round' in forma else '0')
            return '<div style="position:absolute;inset:0;background:%s;border:%dpx solid %s;border-radius:%s"></div>' % (
                cor_css(o.p('Brush.Color'), '#FFFFFF'), _num(o.p('Pen.Width'), 1), cor_css(o.p('Pen.Color'), '#000000'),
                raio)
        if c == 'TBevel':
            forma = str(o.p('Shape', 'bsBox'))
            lado = {'bsTopLine': 'border-top', 'bsBottomLine': 'border-bottom', 'bsLeftLine': 'border-left',
                    'bsRightLine': 'border-right'}.get(forma, 'border')
            return '<div style="position:absolute;inset:0;%s:2px groove #C0C0C0"></div>' % lado
        if c == 'TPageControl':
            return self.paginas(o, w, h)
        # contêineres: TPanel, TScrollBox, TTabSheet, TGridPanel, TPanelImage, frames e classes desconhecidas
        partes = ''
        if c == 'TPanel' and o.p('Caption') and o.p('ShowCaption') != 'False':
            t, attrs['texto-fonte'] = self.texto(o)
            partes += '<div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center">%s</div>' % html.escape(t)
        if c == 'TPanel' and o.p('BevelOuter') != 'bvNone':
            partes += '<div style="position:absolute;inset:0;border:1px solid;border-color:#FFF #A0A0A0 #A0A0A0 #FFF"></div>'
        if c == 'TScrollBox' or c == 'TTabSheet':
            classes.append('solto')
        if c not in ('TPanel', 'TScrollBox', 'TTabSheet', 'TGridPanel', 'TPanelImage', 'TGroupBox') \
                and not o.filhos:
            classes.append('tracejado')
            partes += '<span style="font:10px Arial;color:#D07000">%s: %s (classe sem desenho conhecido)</span>' % (
                html.escape(o.classe), html.escape(o.nome))
        return partes + self.filhos(o, w, h, f)

    def filhos(self, o, w, h, f):
        partes, sem = [], []
        for fi, (x, y, fw, fh) in layout(o, w, h).items():
            if fi.classe == 'TTabSheet':
                continue
            if not tem_geometria(fi) and fi.origem == 'dfm' and o.origem == 'dfm':
                sem.append(fi)
                continue
            partes.append(self.no(fi, x, y, fw, fh, f))
        partes += [self.sem_geometria(fi) for fi in sem]
        return ''.join(partes)

    def sem_geometria(self, o):
        """Filho de frame cujo DFM-base não está disponível: geometria herdada DESCONHECIDA (ficha tracejada)."""
        dentro = ''.join(self.sem_geometria(f) for f in o.filhos if visual(f))
        return ('<div class="semgeo" data-nome="%s" data-classe="%s" data-linha="%d" data-origem="%s" '
                'data-geo="desconhecida" data-visivel="?">%s: %s%s</div>') % (
            o.nome, o.classe, o.linha, o.origem, html.escape(o.nome), o.classe, dentro)

    def grade(self, o, w, h):
        nc, nl = _num(o.p('ColCount'), 5), _num(o.p('RowCount'), 5)
        fc, fl = _num(o.p('FixedCols'), 1), _num(o.p('FixedRows'), 1)
        cws = [int(x) for x in re.findall(r'-?\d+', str(o.p('ColWidths', '')))] or [_num(o.p('DefaultColWidth'), 64)] * nc
        rhs = [int(x) for x in re.findall(r'-?\d+', str(o.p('RowHeights', '')))] or [_num(o.p('DefaultRowHeight'), 24)] * nl
        fixa = cor_css(o.p('FixedColor'), '#F0F0F0')
        linhas = []
        for r in range(nl):
            tds = []
            for col in range(nc):
                bg = ' style="background:%s"' % fixa if r < fl or col < fc else ''
                tds.append('<td%s width="%d"></td>' % (bg, cws[col] if col < len(cws) else cws[-1]))
            linhas.append('<tr style="height:%dpx">%s</tr>' % (rhs[r] if r < len(rhs) else rhs[-1], ''.join(tds)))
        return ('<table class="grade" style="border-collapse:collapse;table-layout:fixed" title="OnDrawCell: conteúdo '
                'desenhado pelo código (DESCONHECIDO)">%s</table>' % ''.join(linhas))

    def paginas(self, o, w, h):
        folhas = [f for f in o.filhos if f.classe == 'TTabSheet']
        ativa = self.ativas.get(o.nome) or achar(o, str(o.p('ActivePage', ''))) or (folhas[0] if folhas else None)
        f = o.fonte
        partes = []
        if o.p('TabPosition') == 'tpLeft':
            tw, th = _num(o.p('TabHeight'), 120), _num(o.p('TabWidth'), 30)
            sx, sy = tw + TAB_PRINCIPAL_BORDA, TAB_PRINCIPAL_BORDA
            sw, sh = w - tw - 2 * TAB_PRINCIPAL_BORDA, h - 2 * TAB_PRINCIPAL_BORDA
            y = 2
            visiveis = [t for t in folhas if self.r.valor(t, 'TabVisible')[0] is not False and
                        (self.r.valor(t, 'TabVisible')[0] or t is ativa)]
            ocultas = [t for t in folhas if t not in visiveis]
            for t in visiveis + ocultas:
                alt = th if t in visiveis else 26
                partes.append(self.cabecalho(t, 2, y, tw, alt, t is ativa, f))
                y += alt + (0 if t in visiveis else 2)
        else:
            sx, sy, sw, sh = 4, TAB_BOTOES_ALTURA, w - 8, h - TAB_BOTOES_ALTURA - 4
            x = 2
            for t in folhas:
                txt = self.texto(t)[0]
                tw = 16 + int(len(txt) * f['px'] * 0.6)
                partes.append(self.cabecalho(t, x, 2, tw, TAB_BOTOES_ALTURA - 6, t is ativa, f))
                x += tw + 3
        if ativa is not None:
            partes.append(self.folha(ativa, sx, sy, sw, sh, fonte_de(ativa, f)))
        return ''.join(partes)

    def cabecalho(self, t, x, y, w, h, ativa, f):
        txt, fonte = self.texto(t)
        tv, marca, _ = self.r.valor(t, 'TabVisible')
        cls = 'tab' + (' ativa' if ativa else '') + (' tracejado fantasma' if tv is None else '')
        return ('<div class="%s" data-cabecalho="%s" data-texto-fonte="%s" data-tabvisible="%s" data-marca-tabvisible="%s" '
                'style="left:%dpx;top:%dpx;width:%dpx;height:%dpx;%s">%s</div>') % (
            cls, t.nome, fonte, {True: '1', False: '0', None: '?'}[tv], marca, x, y, w, h,
            css_fonte(dict(f, px=min(f['px'], 15 if tv is None else 17))), html.escape(txt))

    def folha(self, t, x, y, w, h, f):
        t.fonte = f
        corpo = self.filhos(t, w, h, f)
        return ('<div class="c solto" data-nome="%s" data-classe="TTabSheet" data-linha="%d" data-origem="%s" '
                'data-folha="1" data-visivel="1" style="left:%dpx;top:%dpx;width:%dpx;height:%dpx;background:#E3E3E3;'
                'border:1px solid #A0A0A0;%s"><!--folha:%s:inicio-->%s<!--folha:%s:fim--></div>') % (
            t.nome, t.linha, t.origem, x, y, w, h, css_fonte(f), t.nome, corpo, t.nome)


# ------------------------------------------------------------------ página
def cadeia(t):
    """TTabSheet -> [(PageControl, TabSheet), ...] da raiz até ela."""
    out, o = [], t
    while o is not None:
        if o.classe == 'TTabSheet' and o.pai is not None and o.pai.classe == 'TPageControl':
            out.append((o.pai, o))
        o = o.pai
    return list(reversed(out))


def carregar_valores(caminho):
    if not caminho:
        return {}
    with open(caminho, encoding='utf-8') as fh:
        return json.load(fh)


def carregar_regras(caminho):
    with open(caminho, encoding='utf-8') as fh:
        return json.load(fh)


def preparar(dfm_texto, frames=FRAMES_PADRAO):
    return expandir_frames(parse_dfm(dfm_texto), frames)


def render_aba(raiz, aba, i18n, regras_tab, valores, idioma='por'):
    """HTML da tela inteira com `aba` ativa. Devolve (html, largura, altura)."""
    t = aba if isinstance(aba, No) else achar(raiz, aba)
    if t is None or t.classe != 'TTabSheet':
        raise KeyError('aba %r nao existe no DFM' % aba)
    reg = Regras(regras_tab, valores)
    reg.indexar(raiz)
    ch = cadeia(t)
    forcados = {pc.nome for pc, _ in ch} | {f.nome for _, f in ch}
    d = Desenho(reg, i18n, idioma, forcados)
    for pc, folha in ch:
        d.ativas[pc.nome] = folha
    anc, o = set(), t.pai
    while o is not None:
        anc.add(o.nome)
        o = o.pai
    d.forcados |= anc
    cw = _num(raiz.p('ClientWidth'), _num(raiz.p('Width'), 800) - 16)
    chh = _num(raiz.p('ClientHeight'), _num(raiz.p('Height'), 600) - 59)
    # tela desenrolada: a folha principal cresce até caber o scrollbox expandido
    pc0, t0 = ch[0]
    altura = chh + max(0, extensao_folha(t0) - (_num(pc0.p('Height'), chh) - 2 * TAB_PRINCIPAL_BORDA))
    fonte = raiz.fonte = fonte_de(raiz, FONTE_PADRAO)
    corpo = d.filhos(raiz, cw, altura, fonte)
    txt_aba = d.texto(t)[0]
    nomes = ' &gt; '.join(html.escape(f.nome) for _, f in ch)
    cab = ('<div class="cab"><b>%s</b> &nbsp; %s &mdash; aba %s (&laquo;%s&raquo;). Fonte: DFM TFORMCONFIG + i18n (%s) '
           '+ regras_tformconfig.json. Tracejado laranja = DESCONHECIDO (regra de c&oacute;digo n&atilde;o extra&iacute;da). '
           'Rolagem expandida: altura %d px. N&Atilde;O &eacute; foto do ProgBase.</div>') % (
        LEGENDA, html.escape(sem_atalho(raiz.p('Caption', ''))), nomes, html.escape(txt_aba), idioma, altura)
    pagina = ('<!doctype html>\n<html lang="pt-BR"><head><meta charset="utf-8"><title>%s %s</title><style>%s</style>'
              '</head><body>\n%s\n<div class="form" style="background:%s">\n%s\n</div>\n</body></html>\n') % (
        LEGENDA, html.escape(t.nome), CSS % (cw, cw, altura), cab, cor_css(raiz.p('Color'), '#F0F0F0'), corpo)
    return pagina, cw, altura + 44 + 120   # folga: o headless corta o fim da janela


# ------------------------------------------------------------------ foto
def achar_chrome():
    for padrao in ('/opt/pw-browsers/chromium-*/chrome-linux/chrome',
                   '/opt/pw-browsers/chromium_headless_shell-*/chrome-linux/headless_shell'):
        achados = sorted(glob.glob(padrao))
        if achados:
            return achados[-1]
    return None


def fotografar(html_path, png_path, largura, altura, chrome=None):
    chrome = chrome or achar_chrome()
    if not chrome:
        raise RuntimeError('Chromium nao encontrado')
    cmd = [chrome, '--headless', '--no-sandbox', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
           '--screenshot=' + os.path.abspath(png_path), '--window-size=%d,%d' % (largura, altura),
           'file://' + os.path.abspath(html_path)]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)


# ------------------------------------------------------------------ CLI
def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--dfm', default=DFM_PADRAO)
    ap.add_argument('--aba', required=True, help='nome do TTabSheet ou "todas"')
    ap.add_argument('--valores', default=None, help='JSON {"vetores": {...}, "controles": {...}}')
    ap.add_argument('--regras', default=REGRAS_PADRAO)
    ap.add_argument('--i18n', default=I18N_PADRAO)
    ap.add_argument('--frames', default=FRAMES_PADRAO, help='pasta com DFM-base dos frames (<CLASSE>.txt)')
    ap.add_argument('--idioma', default='por')
    ap.add_argument('--saida', required=True, help='arquivo .html (uma aba) ou pasta (todas)')
    ap.add_argument('--png', action='store_true', help='também fotografa com Chromium headless')
    a = ap.parse_args(argv)
    with open(a.dfm, encoding='utf-8') as fh:
        raiz = preparar(fh.read(), a.frames)
    i18n, regras, valores = carregar_i18n(a.i18n), carregar_regras(a.regras), carregar_valores(a.valores)
    abas = listar_abas(raiz) if a.aba == 'todas' else [achar(raiz, a.aba)]
    if abas == [None]:
        ap.error('aba %s nao existe' % a.aba)
    if a.aba == 'todas':
        os.makedirs(a.saida, exist_ok=True)
    for t in abas:
        pagina, w, h = render_aba(raiz, t, i18n, regras, valores, a.idioma)
        destino = os.path.join(a.saida, t.nome + '.html') if a.aba == 'todas' else a.saida
        with open(destino, 'w', encoding='utf-8', newline='\n') as fh:
            fh.write(pagina)
        if a.png:
            fotografar(destino, os.path.splitext(destino)[0] + '.png', w, h)
    print('%d aba(s) -> %s' % (len(abas), a.saida))
    return 0


if __name__ == '__main__':
    sys.exit(main())
