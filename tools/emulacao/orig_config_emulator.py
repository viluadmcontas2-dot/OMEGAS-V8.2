#!/usr/bin/env python3
"""Emulador da tela TFormConfig > aba 'Iniettori' do ProgBase original, região do anti-stallo.

Entradas: DFM decodificado (TFORMCONFIG.txt), i18n_all.json e valores da ECU (ANTI_STALLO cód. 307,
FLAG_CONF3 cód. 250, lidos do Portmon). Saída: HTML autocontido. Só stdlib; determinístico.

Regras (detalhe e marcas em docs/evidence/2026-10-18-emulacao-tela-config.md):
  OBS-COD  CheckAntiStallo.Checked = FLAG_CONF3[0] & 0x20          (0x42E754 caso 4, [obj+0x2994])
  OBS-COD  CheckAntiStallo.Enabled = capacidade; cor 0x9B0000 (on) / 0x808080 (off), cores em BGR
  DESCONHECIDO  origem da capacidade ([obj+0x2A24]) -> opção --capacidade on|off (padrão on)
  OBS-COD  Edit/Label/LabAntiStalloN.Visible = Checked e Enabled; EditAntiStalloN mostra ANTI_STALLO[N]
  Demais controles da faixa: Visible do DFM (todos False aqui) -> só contorno tracejado.
"""
import argparse
import html
import json
import os
import re
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.normpath(os.path.join(AQUI, '..', '..'))
DFM_PADRAO = os.path.join(RAIZ, 'docs', 'evidence', 'dfm-original', 'TFORMCONFIG.txt')
I18N_PADRAO = '/home/user/protocolo-landi/dfm/i18n_all.json'
Y0_PADRAO, Y1_PADRAO = 1020, 1420          # faixa do PanelSubInjection mostrada
BIT_ANTI_STALLO = 0x20                     # palavra 0 de FLAG_CONF3
COR_HABILITADA, COR_DESABILITADA = 0x9B0000, 0x808080   # BGR (Delphi TColor)
CORES_SISTEMA = {'cl3DLight': '#E3E3E3', 'clBtnFace': '#F0F0F0', 'clWindow': '#FFFFFF'}  # INFERIDO (Win10)
LEGENDA = 'EMULADO — regras: OBS-COD, valores: OBS-FIO'


def html_esc(s):
    return html.escape(str(s), quote=True)


# ------------------------------------------------------------------ DFM
class Obj:
    def __init__(self, nome, classe):
        self.nome, self.classe, self.props, self.filhos = nome, classe, {}, []


def _valor(v):
    v = v.strip()
    if v.startswith("'") or v.startswith('#'):
        partes = re.findall(r"'((?:[^']|'')*)'|#(\d+)", v)
        return ''.join(chr(int(n)) if n else s.replace("''", "'") for s, n in partes)
    if re.fullmatch(r'-?\d+', v):
        return int(v)
    if re.fullmatch(r'-?\d+\.\d+', v):
        return float(v)
    return v


def parse_dfm(texto):
    """Árvore de objetos do DFM texto (object/end + 'Prop = valor' de uma linha)."""
    raiz, pilha = None, []
    for linha in texto.split('\n'):
        s = linha.strip()
        m = re.match(r'(?:object|inherited|inline) (\w+): (\w+)$', s)
        if m:
            o = Obj(m.group(1), m.group(2))
            if pilha:
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


def achar(o, nome):
    if o.nome == nome:
        return o
    for f in o.filhos:
        r = achar(f, nome)
        if r is not None:
            return r
    return None


def carregar_i18n(caminho):
    with open(caminho, encoding='utf-8') as fh:
        return json.load(fh)


def texto_de(o, i18n, idioma):
    """Legenda: i18n[NOME][idioma] -> i18n[NOME]['eng'] -> Caption/Text do DFM."""
    ent = i18n.get(o.nome.upper()) or {}
    return ent.get(idioma) or ent.get('eng') or str(o.props.get('Caption', o.props.get('Text', '')))


# ------------------------------------------------------------------ regras
def anti_stallo_marcado(flag_conf3):
    return bool(flag_conf3[0] & BIT_ANTI_STALLO)


def bgr_css(c):
    if isinstance(c, str):
        return CORES_SISTEMA.get(c, '#000000')
    return '#%02X%02X%02X' % (c & 0xFF, (c >> 8) & 0xFF, (c >> 16) & 0xFF)


def estado(valores, caixa=None, capacidade=True):
    marcado = anti_stallo_marcado(valores['FLAG_CONF3']) if caixa in (None, 'auto') else caixa == 'on'
    return {'marcado': marcado, 'habilitado': bool(capacidade), 'edits': marcado and bool(capacidade)}


# ------------------------------------------------------------------ HTML
CSS = """body{margin:0;background:#FFFFFF;font-family:Arial,sans-serif;color:#000}
.cab{padding:8px 12px;font-size:14px;width:796px}
.cab b{font-size:16px;color:#B00000}
.painel{position:relative;width:820px;height:%dpx;overflow:hidden;background:%s;outline:1px solid #999;margin-left:12px}
.c{position:absolute;box-sizing:border-box;white-space:nowrap;font:bold 24px Arial,sans-serif}
.oculto{visibility:hidden}
.fantasma{border:1px dashed #AAA;color:#AAA;font:11px Arial,sans-serif;padding:1px 3px}
.edit{background:#FFFFFF;border:2px solid #7A7A7A;text-align:right;padding:0 4px;line-height:33px}
.box{display:inline-block;width:20px;height:20px;border:2px solid #333;background:#FFF;vertical-align:-2px;
margin-right:8px;font-size:18px;line-height:18px;text-align:center}
.cinza .box{border-color:#999;color:#999}
"""

ANTI = ['CheckAntiStallo'] + ['%s%d' % (p, i) for p in ('EditAntiStallo', 'LabelAntiStallo', 'LabAntiStallo')
                              for i in range(5)]


def _div(o, cls, extra, conteudo, y0, cor=None):
    p = o.props
    est = 'left:%dpx;top:%dpx;width:%dpx;height:%dpx' % (p['Left'], p['Top'] - y0, p['Width'], p['Height'])
    if cor is not None:
        est += ';color:' + cor
    return '<div class="c %s" data-nome="%s"%s style="%s">%s</div>' % (cls, o.nome, extra, est, conteudo)


def render(dfm_texto, i18n, valores, caixa=None, capacidade=True, idioma='por', y0=Y0_PADRAO, y1=Y1_PADRAO):
    painel = achar(parse_dfm(dfm_texto), 'PanelSubInjection')
    st = estado(valores, caixa, capacidade)
    partes = []
    for o in painel.filhos:          # ordem do DFM (determinística)
        p = o.props
        if 'Top' not in p or p['Top'] + p.get('Height', 0) <= y0 or p['Top'] >= y1:
            continue
        if o.nome not in ANTI:
            if p.get('Visible', True) is False or p.get('Visible') == 'False':
                partes.append(_div(o, 'fantasma', ' data-visivel="0"', html_esc(o.nome), y0))
            continue
        if o.nome == 'CheckAntiStallo':
            cor = bgr_css(COR_HABILITADA if st['habilitado'] else COR_DESABILITADA)
            extra = ' data-checked="%d" data-habilitado="%d" data-visivel="1"' % (st['marcado'], st['habilitado'])
            box = '<span class="box">%s</span>' % ('✓' if st['marcado'] else '')
            partes.append(_div(o, '' if st['habilitado'] else 'cinza', extra,
                               box + '<span>%s</span>' % html_esc(texto_de(o, i18n, idioma)), y0, cor))
            continue
        vis = st['edits']
        cls = '' if vis else 'oculto'
        cor = bgr_css(p.get('Font.Color', COR_HABILITADA))
        if o.nome.startswith('EditAntiStallo'):
            n = int(p['DataIndex'])
            conteudo = '<span>%d</span>' % valores['ANTI_STALLO'][n]
            partes.append(_div(o, 'edit ' + cls, ' data-visivel="%d" data-indice="%d"' % (vis, n), conteudo, y0, cor))
        else:
            partes.append(_div(o, cls, ' data-visivel="%d"' % vis,
                               '<span>%s</span>' % html_esc(texto_de(o, i18n, idioma)), y0, cor))
    origem = 'SIMULADA (--caixa %s)' % caixa if caixa in ('on', 'off') else 'real: FLAG_CONF3[0]&amp;0x20'
    cab = ('<div class="cab"><b>%s</b><br>Caixa: %s = %s. Capacidade da ECU ([obj+0x2A24], origem DESCONHECIDA): '
           'simulada %s. ANTI_STALLO = %s. Faixa y %d..%d do PanelSubInjection. Tracejado = controle com '
           'Visible=False no DFM cuja regra de exibi&ccedil;&atilde;o &eacute; DESCONHECIDA.</div>') % (
        html_esc(LEGENDA), origem, 'marcada' if st['marcado'] else 'desmarcada',
        'on' if st['habilitado'] else 'off', html_esc(valores['ANTI_STALLO']), y0, y1)
    return ('<!doctype html>\n<html lang="pt-BR"><head><meta charset="utf-8"><title>TFormConfig Iniettori EMULADO'
            '</title><style>%s</style></head><body>\n%s\n<div class="painel">\n%s\n</div>\n</body></html>\n') % (
        CSS % (y1 - y0, CORES_SISTEMA['cl3DLight']), cab, '\n'.join(partes))


def carregar_valores(caminho):
    with open(caminho, encoding='utf-8') as fh:
        d = json.load(fh)
    if 'vetores' in d:   # formato da fixture tests/fixtures/config-iniettori-*.json
        d = {k: d['vetores'][k]['u16'] for k in ('ANTI_STALLO', 'FLAG_CONF3')}
    return d


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--dfm', default=DFM_PADRAO)
    ap.add_argument('--i18n', default=I18N_PADRAO)
    ap.add_argument('--valores', required=True)
    ap.add_argument('--caixa', choices=['on', 'off', 'auto'], default='auto')
    ap.add_argument('--capacidade', choices=['on', 'off'], default='on')
    ap.add_argument('--idioma', default='por')
    ap.add_argument('--saida', required=True)
    a = ap.parse_args(argv)
    with open(a.dfm, encoding='utf-8') as fh:
        dfm = fh.read()
    out = render(dfm, carregar_i18n(a.i18n), carregar_valores(a.valores), caixa=a.caixa,
                 capacidade=a.capacidade == 'on', idioma=a.idioma)
    with open(a.saida, 'w', encoding='utf-8', newline='\n') as fh:
        fh.write(out)
    return 0


if __name__ == '__main__':
    sys.exit(main())
