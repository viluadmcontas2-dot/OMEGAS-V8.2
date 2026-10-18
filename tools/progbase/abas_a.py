#!/usr/bin/env python3
"""Documenta, controle a controle, as 16 primeiras abas do TFormConfig do ProgBase.exe (grupo D3a).

Abas (ordem de docs/evidence/2026-10-18-catalogo-funcoes-originais.md secao 1): TabCambio, TabLambda, TabEmissioni,
TabSensore, TabPressione, TabTemp, TabKCoefficienti e as sub-abas TabCorrettori, TabKOpenLoop, TabKFactor, TabKTempRid,
TabKTempMot, TabKPressDiff, TabPressAss, TabKTempi, TabKTempiEx.

Fontes (todas lidas; nada e executado fora do emulador):
  * DFM do TFormConfig/TStreamDati (recurso do .exe)            -> OBS-COD
  * ProgBase.exe estatico (pefile+capstone) e emulado (Unicorn)  -> OBS-COD / DERIVADO
  * Portmon PortmonLOGNOVO.LOG (ECU do dono, 12/08/2026)         -> OBS-FIO
  * Manual Landi Renzo Omegas SW 2.16.4 C (sw.txt)              -> citado por pagina/linha
Uso:
  abas_a.py [--exe ProgBase.exe] [--log PortmonLOGNOVO.LOG] [--manual sw.txt] [--saida docs/progbase/abas] [--check]
Idempotente: mesmas entradas -> mesmos bytes. Sem o .exe, nada e regerado (os arquivos versionados permanecem).
Escreve so em docs/progbase/abas/ (um <Aba>.md e um <Aba>.json por aba, mais _indice.json e _log_leituras.json)."""
import argparse
import hashlib
import json
import os
import re
import struct
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.normpath(os.path.join(AQUI, '..', '..'))
sys.path.insert(0, AQUI)
sys.path.insert(0, os.path.join(RAIZ, 'tools', 'emulacao'))

import abas_motor as M  # noqa: E402
import abas_textos as T  # noqa: E402
import dfm_renderer as D  # noqa: E402

ABAS = ['TabCambio', 'TabLambda', 'TabEmissioni', 'TabSensore', 'TabPressione', 'TabTemp', 'TabKCoefficienti',
        'TabCorrettori', 'TabKOpenLoop', 'TabKFactor', 'TabKTempRid', 'TabKTempMot', 'TabKPressDiff', 'TabPressAss',
        'TabKTempi', 'TabKTempiEx']
DFM_FORM = os.path.join(RAIZ, 'docs', 'evidence', 'dfm-original', 'TFORMCONFIG.txt')
DFM_SD = os.path.join(RAIZ, 'docs', 'evidence', 'dfm-original', 'TSTREAMDATI.txt')
FLAGS_JSON = os.path.join(RAIZ, 'docs', 'evidence', 'flags-do-original.json')
I18N = ['/home/user/protocolo-landi/dfm/i18n_all.json',
        os.path.join(RAIZ, 'docs', 'evidence', 'dfm-original', 'i18n_TFORMCONFIG.json')]
SAIDA_PADRAO = os.path.join(RAIZ, 'docs', 'progbase', 'abas')
EXE_CAND = [os.environ.get('PROGBASE_EXE', ''), os.path.join(os.path.expanduser('~/.cache/omegas'), 'ProgBase.exe'),
            '/tmp/claude-0/-home-user/57e20309-4640-5e31-b02d-4b2faf9cf2b3/scratchpad/progbase/pb.exe']
LOG_CAND = [os.environ.get('PORTMON_LOG', ''),
            '/tmp/claude-0/-home-user/57e20309-4640-5e31-b02d-4b2faf9cf2b3/scratchpad/portmon/x/PortmonLOGNOVO.LOG']
MANUAL_CAND = [os.environ.get('MANUAL_SW_TXT', ''),
               '/tmp/claude-0/-home-user/57e20309-4640-5e31-b02d-4b2faf9cf2b3/scratchpad/web/sw.txt']
SHA_EXE = '8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4'
TIPO_DE_CLASSE = {'TNumericEdit': 'edit numerico', 'TValueEditEx': 'edit numerico (ValueEditEx)', 'TEdit': 'edit',
                  'TKeyComboBox': 'combo', 'TCheckBox': 'caixa de marcar', 'TRadioButton': 'radio',
                  'TButton': 'botao', 'TDrawGrid': 'grade', 'TLabel': 'rotulo', 'TPanel': 'painel',
                  'TGroupBox': 'grupo', 'TScrollBox': 'rolagem', 'TStaticText': 'texto', 'TBevel': 'moldura',
                  'TShape': 'forma', 'TPageControl': 'abas'}
INTERATIVOS = ('TNumericEdit', 'TValueEditEx', 'TEdit', 'TKeyComboBox', 'TCheckBox', 'TRadioButton', 'TButton', 'TDrawGrid')
UNIDADES = {'rpm', 'ms', 's', '°C', '%', 'V', 'mV', 'bar', 'min', 'ohm', 'mbar', 'l', 'Hz', 'A', 'mA', 'us', 'kPa', 'mm',
            'cc', 'deg', '°', 'kohm', 'KOhm', 'Ohm', 'k', 'x'}


def achar(cands, descr):
    for c in cands:
        if c and os.path.isfile(c):
            return c
    return None


# ------------------------------------------------------------------ DFM
def todos(o):
    for f in o.filhos:
        yield f
        yield from todos(f)


def acha(raiz, nome):
    if raiz.nome == nome:
        return raiz
    for f in raiz.filhos:
        x = acha(f, nome)
        if x:
            return x
    return None


def controles_da_aba(tab):
    """Descendentes da aba, sem entrar em sub-abas (TTabSheet). Ordem do DFM."""
    out = []

    def rec(o, pai):
        for f in o.filhos:
            if f.classe == 'TTabSheet':
                continue
            out.append((f, pai))
            rec(f, f.nome)
    rec(tab, tab.nome)
    return out


def itens_do_combo(c):
    v = c.p('Items.Strings')
    if not isinstance(v, str):
        return []
    return [x.replace("''", "'") for x in re.findall(r"'((?:[^']|'')*)'", v)]


def numero(x):
    return x if isinstance(x, (int, float)) else None


def tr(i18n, chave, idioma, padrao=None):
    ent = i18n.get(chave.upper()) or {}
    t = ent.get(idioma)
    if t and t != '#ERROR!':
        return D.conserta(t)
    return padrao


def rotulos(i18n, nome, caption_dfm):
    """{ita, eng, por, fonte} do componente; 'ita' vem do Caption do DFM (idioma do projeto)."""
    d = {'ita': caption_dfm if isinstance(caption_dfm, str) and caption_dfm else tr(i18n, nome, 'ita'),
         'eng': tr(i18n, nome, 'eng'), 'por': tr(i18n, nome, 'por')}
    fonte = []
    if d['ita'] and isinstance(caption_dfm, str) and caption_dfm:
        fonte.append('ita=Caption do DFM')
    elif d['ita']:
        fonte.append('ita=i18n')
    if d['eng'] or d['por']:
        fonte.append('eng/por=i18n_all.json[%s]' % nome.upper())
    d['fonte'] = '; '.join(fonte) or None
    d['marca'] = 'OBS-COD' if fonte else 'DESCONHECIDO'
    return d


PREFIXOS = ('DrawGrid', 'Label', 'Lab', 'Edit', 'Combo', 'Check', 'Radio', 'Button', 'chk', 'Panel', 'Group')


def sufixo(nome):
    for p in PREFIXOS:
        if nome.startswith(p) and len(nome) > len(p):
            return nome[len(p):]
    return nome


def achar_rotulo(c, irmaos):
    """Rotulo (TLabel) do controle: mesmo sufixo de nome, senao geometria (mesma linha, a esquerda)."""
    if c.classe in ('TCheckBox', 'TRadioButton', 'TButton', 'TLabel', 'TGroupBox', 'TPanel'):
        return None
    suf = sufixo(c.nome).lower()
    labs = [x for x in irmaos if x.classe == 'TLabel']
    for pref in ('Label', 'Lab'):
        for x in labs:
            if x.nome.lower() == (pref + sufixo(c.nome)).lower():
                return x, 'nome (%s%s)' % (pref, sufixo(c.nome))
    # sufixo com numero final comum (EditFlashLube0 -> LabelFlashLube)
    base = re.sub(r'\d+$', '', suf)
    if base != suf:
        for x in labs:
            if x.nome.lower() in ('label' + base, 'lab' + base):
                return x, 'nome (base sem indice)'
    cy = (numero(c.p('Top')) or 0) + (numero(c.p('Height')) or 0) / 2
    cx = numero(c.p('Left')) or 0
    melhor = None
    for x in labs:
        if x.p('Caption') in (None, '') or x.nome.lower().startswith('lab') and False:
            continue
        ly = (numero(x.p('Top')) or 0) + (numero(x.p('Height')) or 0) / 2
        lx = numero(x.p('Left')) or 0
        if abs(ly - cy) <= 16 and lx < cx and (cx - lx) < 700:
            if melhor is None or lx > (numero(melhor.p('Left')) or 0):
                melhor = x
    if melhor:
        return melhor, 'geometria (mesma linha, a esquerda)'
    return None


def achar_unidade(c, irmaos):
    if c.classe not in ('TNumericEdit', 'TValueEditEx', 'TEdit', 'TKeyComboBox'):
        return None
    cy = (numero(c.p('Top')) or 0) + (numero(c.p('Height')) or 0) / 2
    cx = (numero(c.p('Left')) or 0) + (numero(c.p('Width')) or 0)
    melhor = None
    for x in irmaos:
        if x.classe != 'TLabel':
            continue
        cap = x.p('Caption')
        if not isinstance(cap, str) or len(cap) > 6:
            continue
        ly = (numero(x.p('Top')) or 0) + (numero(x.p('Height')) or 0) / 2
        lx = numero(x.p('Left')) or 0
        if abs(ly - cy) <= 16 and 0 <= lx - cx <= 80:
            if melhor is None or lx < (numero(melhor.p('Left')) or 0):
                melhor = x
    if melhor and melhor.p('Caption').strip():
        return melhor
    return None


def hint_de(i18n, c, rot):
    cand = ['HINT' + c.nome.upper(), 'HINT' + sufixo(c.nome).upper()]
    if rot is not None:
        cand += ['HINT' + rot.nome.upper(), 'HINT' + sufixo(rot.nome).upper()]
    for k in cand:
        if k in i18n:
            return dict(chave=k, ita=tr(i18n, k, 'ita'), eng=tr(i18n, k, 'eng'), por=tr(i18n, k, 'por'),
                        marca='INFERIDO',
                        fonte='chave HINT+nome do componente em i18n_all.json (ControlMouseDown 0x4C6020 monta o balao; a regra exata de nome nao foi decodificada)')
    return None


# ------------------------------------------------------------------ expressoes
def avaliar(expr, v=None, i=None, sd=None):
    """Avalia a formula textual do motor (Python) para checar contra o Unicorn. Nao usa nome de funcao externa."""
    if expr is None or 'f_' in expr or 'TAB[' in expr or 'sign(' in expr or '?' in expr:
        raise ValueError('expressao nao avaliavel')
    sd = sd or {}

    class SDmap(dict):
        def __missing__(self, k):
            raise KeyError(k)
    e = re.sub(r'SD\[0x([0-9A-Fa-f]+)(?: \+ i\*(\d+))?\]',
               lambda m: 'SD[%d]' % (int(m.group(1), 16) + (int(m.group(2)) * i if m.group(2) else 0)), expr)
    e = e.replace('//', '//')
    return eval(e, {'__builtins__': {}}, {'v': v, 'i': i, 'trunc': int, 'SD': SDmap(sd)})  # noqa: S307


# ------------------------------------------------------------------ contexto
class Contexto:
    def __init__(self, exe, log, manual):
        self.exe = exe
        self.b, self.r = M.abrir(exe)
        self.sha = self.b.sha256
        self.campos_form = self.r.fields('TFormConfig')
        self.campos_sd = self.r.fields('TStreamDati')
        ms = self.r.methods('TFormConfig')
        self.metodos = ms
        self.metodo_end = {v: k for k, v in ms.items()}
        self.nomes_pub = {a: 'TFormConfig.' + n for a, n in ms.items()}
        with open(DFM_FORM, encoding='utf-8') as fh:
            self.raiz = D.parse_dfm(fh.read())
        with open(DFM_SD, encoding='utf-8') as fh:
            self.sd_texto = fh.read()
        self.sc_nome = M.sc_por_nome(self.sd_texto)
        import abas_emul as E
        self.E = E
        self.objs = E.objetos_sd(self.sd_texto)
        i18n_path = achar(I18N, 'i18n')
        self.i18n = D.carregar_i18n(i18n_path)
        with open(FLAGS_JSON, encoding='utf-8') as fh:
            self.flags = json.load(fh)
        self.flags_por_controle = {}
        for f in self.flags['flags']:
            for c in f['controles']:
                self.flags_por_controle.setdefault(c['nome'], []).append(f)
        self.log = log
        self.manual = manual
        self.cat = M.catalogo_codigos(self.b, self.r, self.sd_texto)
        self.bind = {}
        for x in M.amarracao(self.b, self.r):
            self.bind.setdefault(x['controle'], {})[x['prop']] = x
        # cache offset -> vetor (do catalogo)
        self.cache_nome = {}
        for c, d in self.cat.items():
            for kind, rec in d.items():
                m = re.fullmatch(r'EA\[0x([0-9A-F]+)(?:\+i\*4)?\]', rec.get('set_cache') or '')
                if m and rec.get('vetor'):
                    self.cache_nome.setdefault(int(m.group(1), 16), rec['vetor'])
        self.sd_nomes = dict(self.campos_sd)
        for off, nm in self.cache_nome.items():
            self.sd_nomes.setdefault(off, 'cache de ' + nm)
        self._ev = {}
        self._toca = {}
        self._todas = None
        self._flag_em = None
        self._acoes = {}

    def flag_emul(self):
        if self._flag_em is None:
            self._flag_em = self.E.Emul(self.exe, self.campos_sd, self.objs, lr=0)
            self._flag_em.preparar(['FLAG_CONF1', 'FLAG_CONF2', 'FLAG_CONF3'])
        return self._flag_em

    def todas_acoes(self):
        if self._todas is None:
            self._todas = []
            for h in sorted(self.metodos.values()):
                self._todas.append((h, self.acoes(h)))
        return self._todas

    def toca(self, alvo):
        if alvo not in self._toca:
            self._toca[alvo] = M.toca_sd(self.b, alvo, self.sd_nomes)
        return self._toca[alvo]

    def eventos(self, nome):
        if nome not in self._ev:
            self._ev[nome] = M.eventos_metodo(self.b, self.r, nome, self.campos_form)
        return self._ev[nome]

    def acoes(self, nome):
        if nome not in self._acoes:
            self._acoes[nome] = M.acoes_metodo(self.b, self.r, nome, self.nomes_pub, self.campos_form, self.sd_nomes)
        return self._acoes[nome]


# ------------------------------------------------------------------ LOG -> valores
def bruto_do_log(log, sc, nome_vetor, ctx, indice=None):
    """Valor(es) cru(s) da ECU do dono para o vetor/escalar (SC) a partir do LOG. -> (lista de ints, linha, op) ou None"""
    if not log:
        return None
    o = ctx.objs.get(nome_vetor)
    comp = o['props'].get('DataLength', 1) if o else 1
    for chave in ('%d:09:' % sc, '%d:29:' % sc):
        e = log['leituras'].get(chave)
        if e:
            dados = bytes(int(x, 16) for x in e['hex'].split())
            if chave.endswith('09:'):
                return [int.from_bytes(dados, 'little')], e['linha'], '09 (GetNumber)'
            n = len(dados) // comp
            return [int.from_bytes(dados[k * comp:(k + 1) * comp], 'little') for k in range(n)], e['linha'], '29 (GetVector)'
    if indice is not None:
        e = log['leituras'].get('%d:0A:%d' % (sc, indice))
        if e:
            dados = bytes(int(x, 16) for x in e['hex'].split())
            return [int.from_bytes(dados, 'little')], e['linha'], '0A (GetNumber+indice)'
    return None


def montar_emul(ctx, log):
    """Emulador com TODOS os vetores do catalogo criados e o cache preenchido com o LOG (ECU do dono)."""
    em = ctx.E.Emul(ctx.exe, ctx.campos_sd, ctx.objs, lr=0)
    nomes = sorted({rec['vetor'] for d in ctx.cat.values() for rec in d.values() if rec.get('vetor') in ctx.objs})
    em.preparar(nomes)
    ctx.E.carregar_tabelas_temperatura(em, 0)    # curvas NTC padrao (tipo 0); tipo real da ECU: INFERIDO
    preenchidos = {}
    for c, d in ctx.cat.items():
        for kind, rec in d.items():
            nome = rec.get('vetor')
            cache = rec.get('set_cache') or ''
            m = re.fullmatch(r'EA\[0x([0-9A-F]+)(?:\+i\*4)?\]', cache)
            if not nome or not m or nome not in ctx.objs:
                continue
            base = int(m.group(1), 16)
            sc = ctx.objs[nome]['props'].get('SerialCode')
            bruto = bruto_do_log(log, sc, nome, ctx)
            if not bruto:
                continue
            vals = bruto[0]
            if kind == 'esc':
                em.m.w32(em.m.sd + base, vals[0])
                preenchidos[(c, 'esc')] = (base, vals[0], bruto[1])
            else:
                for k, vv in enumerate(vals):
                    em.m.w32(em.m.sd + base + 4 * k, vv)
                preenchidos[(c, 'idx')] = (base, vals, bruto[1])
    return em, preenchidos


# ------------------------------------------------------------------ montagem do registro de um controle
def formula_texto(rec, kind):
    if rec is None:
        return None
    return dict(tela_para_ecu=rec.get('set_expr'), ecu_para_tela=rec.get('get_expr'),
                cache_sd=rec.get('set_cache'), tipo=('indexado' if kind == 'idx' else 'escalar'),
                funcoes_auxiliares=sorted(set((rec.get('set_chamadas') or []) + (rec.get('get_chamadas') or []))),
                caso_set=rec.get('caso_set'), caso_get=rec.get('caso_get'))


def ligacao_de(ctx, c):
    """Ligacao controle -> codigo do formulario -> vetor da ECU. Cobre a rotina de amarracao 0x48A228
    (edits: [ctl+0x2EC] codigo e [ctl+0x2F0] indice; combos: [ctl+0x330] codigo)."""
    b = ctx.bind.get(c.nome)
    if not b:
        return None
    if 'codigo' in b:
        cod, via = b['codigo'], 'campo +0x2EC (DataCode do TNumericEdit)'
    elif 'prop330' in b:
        cod, via = b['prop330'], 'campo +0x330 (codigo do TKeyComboBox)'
    else:
        return None
    idx = b.get('indice')
    out = {'offset_campo_form': '0x%X' % cod['offset'], 'campo_codigo': via, 'marca': 'OBS-COD',
           'fonte': 'TFormConfig 0x48A228 (rotina de amarracao dos controles aos codigos de dado)'}
    if cod['ramo']:
        out['codigo_por_familia'] = {'LR ([SD+0x80D]!=0)': cod['ramo']['familia_LR_0x80D!=0'],
                                     'demais ([SD+0x80D]==0)': cod['ramo']['demais']}
    else:
        out['codigo'] = cod['valor']
    dfm_idx = c.p('DataIndex')
    if idx is not None:
        out['indice_codigo'] = idx['valor'] if idx['valor'] is not None else 0
    elif dfm_idx is not None:
        out['indice_dfm'] = dfm_idx
    return out


def codigos_efetivos(lig):
    if not lig:
        return []
    if 'codigo' in lig:
        return [('unico', lig['codigo'])]
    return [('LR', lig['codigo_por_familia']['LR ([SD+0x80D]!=0)']), ('demais', lig['codigo_por_familia']['demais ([SD+0x80D]==0)'])]


def vetores_do_codigo(ctx, cod, indexado):
    if cod is None or cod < 0:
        return None
    d = ctx.cat.get(cod, {})
    return d.get('idx' if indexado else 'esc')


def valor_tela_do_log(ctx, em, preench, cod, indexado, indice):
    """Valor mostrado na tela com o cache da ECU do dono, calculado pelo getter ORIGINAL emulado."""
    k = (cod, 'idx' if indexado else 'esc')
    if k not in preench:
        return None
    try:
        v = em.get(cod, indice if indexado else None)
    except Exception:  # noqa: BLE001
        return None
    return v


def registro_controle(ctx, em, preench, log, aba, c, pai, irmaos, ordem, manual_idx):
    cls = c.classe
    tipo = TIPO_DE_CLASSE.get(cls, cls)
    rec = {'nome': c.nome, 'classe': cls, 'tipo': tipo, 'pai': pai, 'ordem_dfm': ordem,
           'dfm_linha': c.linha, 'marca_dfm': 'OBS-COD'}
    g = {k: c.p(k) for k in ('Left', 'Top', 'Width', 'Height', 'TabOrder', 'Tag', 'Visible', 'Enabled', 'ReadOnly', 'Style',
                              'MinValue', 'MaxValue', 'Precision', 'DataIndex', 'DataCode', 'MaxLength', 'ItemIndex',
                              'Checked', 'Text', 'Caption', 'ColCount', 'RowCount', 'FixedCols', 'FixedRows',
                              'NextControl', 'PrevControl') if c.p(k) is not None}
    rec['dfm'] = g
    rec['rotulo'] = rotulos(ctx.i18n, c.nome, c.p('Caption') if cls in ('TLabel', 'TCheckBox', 'TRadioButton', 'TButton',
                                                                          'TGroupBox', 'TStaticText', 'TPanel') else None)
    rl = achar_rotulo(c, irmaos)
    rot = rl[0] if rl else None
    if rot is not None:
        rec['rotulo_do_campo'] = dict(rotulos(ctx.i18n, rot.nome, rot.p('Caption')), componente=rot.nome, via=rl[1],
                                      marca_associacao='INFERIDO')
    un = achar_unidade(c, irmaos)
    if un is not None:
        rec['unidade'] = dict(texto=un.p('Caption'), componente=un.nome, marca='OBS-COD',
                              fonte='rotulo vizinho a direita no DFM')
    h = hint_de(ctx.i18n, c, rot)
    if cls in INTERATIVOS or cls in ('TLabel',):
        rec['hint'] = h
    if cls == 'TKeyComboBox':
        its = itens_do_combo(c)
        rec['itens'] = [dict(indice=k, ita=t, eng=tr(ctx.i18n, c.nome + str(k + 1), 'eng'),
                             por=tr(ctx.i18n, c.nome + str(k + 1), 'por')) for k, t in enumerate(its)]
    # --- ligacao + conversao
    lig = ligacao_de(ctx, c)
    if lig:
        rec['ligacao'] = lig
        conv = []
        indexado_dfm = c.p('DataIndex') is not None or ('indice_codigo' in lig)
        for fam, cod in codigos_efetivos(lig):
            if cod is None or cod < 0:
                conv.append(dict(familia=fam, codigo=cod, nota='sem codigo nesta familia (-1)'))
                continue
            d = ctx.cat.get(cod, {})
            for kind in ('esc', 'idx'):
                if kind in d:
                    item = dict(familia=fam, codigo=cod, vetor=d[kind].get('vetor'), serial_code=d[kind].get('serial_code'),
                                classe_vetor=d[kind].get('classe_vetor'))
                    item.update(formula_texto(d[kind], kind))
                    item['marca'] = 'OBS-COD'
                    item['fonte'] = 'TStreamDati caso %s (%s)' % (d[kind].get('caso_set'), d[kind].get('caso_get'))
                    conv.append(item)
        rec['conversao'] = conv
    # --- faixa
    if cls in ('TNumericEdit', 'TValueEditEx'):
        mn, mx, pr = c.p('MinValue'), c.p('MaxValue'), c.p('Precision')
        rec['faixa'] = dict(minimo=mn if mn is not None else 0, maximo=mx, passo=pr, marca='OBS-COD',
                            fonte='DFM (MinValue/MaxValue/Precision). Enter limita ao intervalo se Min!=0 ou Max!=0 '
                                  '(TNumericEdit.KeyPress 0x96C170); Precision = passo de arredondamento (0x96BB88); '
                                  'ao entrar mostra "(min;max)" no LabelLimite (0x4A98D8)')
    # --- flags (caixas)
    fl = ctx.flags_por_controle.get(c.nome, [])
    if fl:
        rec['flags'] = []
        for f in fl:
            item = dict(vetor=f['vetor'], serial_code=f['serial_code'], palavra=f['palavra'], mascara=f['mascara'],
                        caso=f['caso'], condicao=f['condicao'], getter=f['getter'], setter=f['setter'], marca='OBS-COD',
                        fonte='docs/evidence/flags-do-original.json (flags_do_original.py)')
            papel = [x.get('papel') for x in f['controles'] if x['nome'] == c.nome]
            item['papel'] = papel[0] if papel else None
            rec['flags'].append(item)
    return rec, rot, un, lig


# ------------------------------------------------------------------ eventos
EVENTOS = ('OnClick', 'OnKeyPress', 'OnEnter', 'OnExit', 'OnDblClick', 'OnKeyDown', 'OnDrawCell', 'OnChange', 'OnMouseDown',
           'OnSelectCell', 'OnSetEditText', 'OnGetEditText')


def resumo_acessos(ctx, evs):
    """Texto curto para cada acesso do metodo (nome do vetor onde o codigo e conhecido)."""
    out = []
    for e in evs:
        f = e['funcao']
        d = dict(e)
        if f in ('SetEsc', 'GetEsc', 'SetIdx', 'GetIdx') and isinstance(e.get('codigo'), int):
            cat = ctx.cat.get(e['codigo'], {})
            rec = cat.get('idx' if f.endswith('Idx') else 'esc') or {}
            d['vetor'] = rec.get('vetor')
            d['serial_code'] = rec.get('serial_code')
        if f == 'AcessoSD':
            d['toca'] = ctx.toca(int(e['alvo'], 16))
        if f.endswith(('Flag1', 'Flag2', 'Flag3')) and e.get('caso') is not None:
            n = f[-1]
            ach = [x for x in ctx.flags['flags'] if x['vetor'].startswith('FLAG_CONF' + n) and x['caso'] == e['caso']
                   and x['vetor'] in ('FLAG_CONF1', 'FLAG_CONF2', 'FLAG_CONF2_LR', 'FLAG_CONF3')]
            d['bits'] = [dict(vetor=x['vetor'], palavra=x['palavra'], mascara=x['mascara'], condicao=x['condicao']) for x in ach]
        out.append(d)
    return out


def eventos_do_controle(ctx, c):
    out = []
    for ev in EVENTOS:
        h = c.p(ev)
        if not isinstance(h, str):
            continue
        end = ctx.metodo_end.get(h)
        item = dict(evento=ev, handler=h, endereco=('0x%X' % end) if end else None,
                    marca='OBS-COD', fonte='DFM %s = %s; metodo publicado na RTTI do TFormConfig' % (ev, h))
        if end and ev not in ('OnMouseDown', 'OnDrawCell'):
            s = ctx.eventos(h)
            if s:
                item['n_instr'] = s['n_instr']
                item['acessos'] = resumo_acessos(ctx, s['eventos'])
        elif ev == 'OnMouseDown':
            item['nota'] = 'ControlMouseDown 0x4C6020: botao esquerdo mostra o balao de dica (hint) do controle'
        elif ev == 'OnDrawCell':
            item['nota'] = 'desenho das celulas da grade (exibicao); nao envia nada a ECU'
        out.append(item)
    return out


# ------------------------------------------------------------------ regras de estado
def regras_estado(ctx, aba, nomes_aba):
    """{controle: [acao...]}: acoes (Visible/Enabled/Caption/Text/Checked) em TabXShow, nos handlers da aba e
    em qualquer outro metodo publicado do TFormConfig (com sub-rotinas ate 2 niveis)."""
    por = {}
    handlers = [aba + 'Show']
    for nm in sorted(nomes_aba):
        no = acha(ctx.raiz, nm)
        if no is None:
            continue
        for ev in EVENTOS:
            h = no.p(ev)
            if isinstance(h, str) and h not in handlers and h in ctx.metodo_end:
                handlers.append(h)
    for h, acs in ctx.todas_acoes():
        for a in acs:
            if a['controle'] in nomes_aba:
                a = dict(a)
                a['metodo'] = h
                a['marca'] = 'OBS-COD'
                a['da_aba'] = h in handlers
                por.setdefault(a['controle'], []).append(a)
    return por, handlers


def entrada_log(ctx, log, c, lig, em, preench):
    """Valor atual na ECU do dono: bruto do LOG e valor da tela via getter original emulado."""
    out = []
    if not lig or not log:
        return out
    for fam, cod in codigos_efetivos(lig):
        if fam == 'LR' or cod is None or cod < 0:
            continue
        d = ctx.cat.get(cod, {})
        indexado = 'indice_dfm' in lig or 'indice_codigo' in lig
        kind = 'idx' if indexado and 'idx' in d else ('esc' if 'esc' in d else ('idx' if 'idx' in d else None))
        if kind is None:
            continue
        rec = d[kind]
        nome = rec.get('vetor')
        sc = rec.get('serial_code')
        indice = lig.get('indice_dfm')
        if isinstance(lig.get('indice_codigo'), int):
            indice = lig['indice_codigo']
        bruto = bruto_do_log(log, sc, nome, ctx, indice) if sc is not None else None
        if not bruto:
            out.append(dict(vetor=nome, serial_code=sc, marca='DESCONHECIDO', nota='o LOG nao traz leitura deste SerialCode'))
            continue
        vals, linha, op = bruto
        item = dict(vetor=nome, serial_code=sc, bruto=vals if kind == 'idx' else vals[0], leitura=op,
                    linha_log=linha, marca='OBS-FIO', fonte='PortmonLOGNOVO.LOG linha %d (resposta ao pedido %s)' % (linha, op))
        if kind == 'idx' and indice is not None and indice < len(vals):
            item['elemento'] = indice
            item['bruto_elemento'] = vals[indice]
        v = valor_tela_do_log(ctx, em, preench, cod, kind == 'idx', indice or 0)
        if v is not None:
            item['valor_na_tela'] = round(v, 6)
            item['como'] = 'getter original 0x%s executado no Unicorn com o cache preenchido pelo LOG' % (
                '434CC0' if kind == 'esc' else '435C6C')
            item['marca_tela'] = 'DERIVADO'
        out.append(item)
    return out


# ------------------------------------------------------------------ quadros de escrita
def quadros_exemplo(ctx, em, c, rec, lig, log):
    """Quadros de SetNumber/SetVector que o ProgBase enviaria ao gravar o valor ATUAL da ECU do dono (round-trip) e um
    valor alternativo (min+passo, quando houver faixa), executando o setter original no Unicorn."""
    out = []
    if not lig:
        return out
    for fam, cod in codigos_efetivos(lig):
        if fam == 'LR' or cod is None or cod < 0:
            continue
        d = ctx.cat.get(cod, {})
        indexado = 'indice_dfm' in lig or 'indice_codigo' in lig
        kind = 'idx' if indexado and 'idx' in d else ('esc' if 'esc' in d else ('idx' if 'idx' in d else None))
        if kind is None or not d[kind].get('vetor') or d[kind]['vetor'] not in ctx.objs:
            continue
        indice = lig.get('indice_dfm')
        if isinstance(lig.get('indice_codigo'), int):
            indice = lig['indice_codigo']
        alvos = []
        faixa = rec.get('faixa')
        ent = rec.get('valor_ecu') or []
        atual = next((e.get('valor_na_tela') for e in ent if e.get('valor_na_tela') is not None), None)
        if atual is not None:
            alvos.append(('valor atual da ECU do dono', atual))
        if faixa and faixa.get('maximo') is not None:
            alvos.append(('maximo da faixa do DFM', float(faixa['maximo'])))
        for desc, val in alvos:
            try:
                if kind == 'esc':
                    r = em.set_esc(cod, val)
                else:
                    r = em.set_idx(cod, indice or 0, val)
            except Exception as e:  # noqa: BLE001
                r = {'quadros': [], 'erro': str(e)}
            out.append(dict(cenario=desc, valor_tela=val, codigo=cod, indice=indice if kind == 'idx' else None,
                            quadros=[q.hex(' ').upper() for q in r['quadros']], erro=r.get('erro'),
                            marca='OBS-COD', fonte='TStreamDati.Set%s 0x%X executado no Unicorn (ECU = stub: eco + 53 00 53)' % (
                                'Idx' if kind == 'idx' else 'Esc', 0x43734C if kind == 'idx' else 0x436444)))
    return out


def quadros_flag(ctx, c, fl, log):
    """Quadro de escrita de cada flag do controle, a partir das palavras ATUAIS do LOG, ligando e desligando o bit."""
    out = []
    if not log:
        return out
    E = ctx.E
    setter = {'FLAG_CONF1': 0x42D300, 'FLAG_CONF2': 0x42E16C, 'FLAG_CONF3': 0x42E8D8}
    cache = {'FLAG_CONF1': 0x2984, 'FLAG_CONF2': 0x298C, 'FLAG_CONF3': 0x2994}
    for f in fl:
        v = f['vetor']
        if v not in setter:
            continue
        sc = f['serial_code']
        ent = log['leituras'].get('%d:29:' % sc)
        if not ent:
            continue
        dados = bytes(int(x, 16) for x in ent['hex'].split())
        palavras = [int.from_bytes(dados[k:k + 2], 'little') for k in range(0, len(dados), 2)]
        mask = int(f['mascara'], 16)
        atual = bool(palavras[f['palavra']] & mask)
        for ligar in (True, False):
            em = ctx.flag_emul()
            for k, w in enumerate(palavras):
                em.m.w32(em.m.sd + cache[v] + 4 * k, w)
            em.m.ecu.quadros.clear()
            em.m.erro = None
            ret = em.m.chamar(setter[v], {'eax': em.m.sd, 'edx': f['caso'], 'ecx': 1 if ligar else 0})
            novas = [em.m.r32(em.m.sd + cache[v] + 4 * k) for k in range(len(palavras))]
            out.append(dict(vetor=v, serial_code=sc, palavra=f['palavra'], mascara=f['mascara'], caso=f['caso'],
                            acao='ligar' if ligar else 'desligar', estado_atual_na_ecu=atual,
                            palavras_antes=['%04X' % w for w in palavras], palavras_depois=['%04X' % w for w in novas],
                            quadros=[q.hex(' ').upper() for q in em.m.ecu.quadros], erro=em.m.erro,
                            marca='OBS-COD', fonte='TStreamDati.%s 0x%X executado no Unicorn' % (
                                'SetFlag' + v[-1], setter[v])))
    return out


# ------------------------------------------------------------------ montagem de uma aba
def construir_aba(ctx, aba, em, preench, log, manual_idx, ordem_aba):
    tab = acha(ctx.raiz, aba)
    if tab is None:
        raise SystemExit('aba nao encontrada no DFM: ' + aba)
    pares = controles_da_aba(tab)
    nomes_aba = {c.nome for c, _ in pares}
    por_pai = {}
    for c, pai in pares:
        por_pai.setdefault(pai, []).append(c)
    estado, handlers_vistos = regras_estado(ctx, aba, nomes_aba)
    controles = []
    for ordem, (c, pai) in enumerate(pares):
        irmaos = por_pai.get(pai, [])
        rec, rot, un, lig = registro_controle(ctx, em, preench, log, aba, c, pai, irmaos, ordem, manual_idx)
        rec['eventos'] = eventos_do_controle(ctx, c)
        rec['estado'] = dict(
            inicial_dfm=dict(Visible=c.p('Visible', True), Enabled=c.p('Enabled', True), marca='OBS-COD',
                             fonte='DFM (padrao VCL: True quando ausente)'),
            regras=estado.get(c.nome, []))
        if rec['classe'] in INTERATIVOS:
            rec['valor_ecu'] = entrada_log(ctx, log, c, lig, em, preench)
            if rec.get('flags') and log:
                st = []
                for f in rec['flags']:
                    ent = log['leituras'].get('%d:29:' % f['serial_code'])
                    if ent:
                        dados = bytes(int(x, 16) for x in ent['hex'].split())
                        pal = [int.from_bytes(dados[k:k + 2], 'little') for k in range(0, len(dados), 2)]
                        if f['palavra'] < len(pal):
                            st.append(dict(vetor=f['vetor'], palavra=f['palavra'], mascara=f['mascara'],
                                           bit_ligado=bool(pal[f['palavra']] & int(f['mascara'], 16)),
                                           palavra_valor='%04X' % pal[f['palavra']], linha_log=ent['linha'],
                                           marca='OBS-FIO'))
                rec['valor_ecu_flags'] = st
            rec['envia'] = dict(
                escrita_valor=quadros_exemplo(ctx, em, c, rec, lig, log) if rec['classe'] in ('TNumericEdit', 'TValueEditEx', 'TEdit') or lig else [],
                escrita_flags=quadros_flag(ctx, c, rec.get('flags', []), log))
        rec['explicacao_leiga'] = T.explicar(c, rec, manual_idx, ctx.i18n)
        rec['manual'] = T.citar_manual(manual_idx, rec)
        controles.append(rec)
    interativos = [x for x in controles if x['classe'] in INTERATIVOS]
    meta = dict(
        aba=aba, ordem=ordem_aba,
        titulo=rotulos(ctx.i18n, aba, tab.p('Caption')),
        tab_show=dict(metodo=aba + 'Show', endereco=('0x%X' % ctx.metodo_end[aba + 'Show']) if (aba + 'Show') in ctx.metodo_end else None,
                      marca='OBS-COD', fonte='RTTI do TFormConfig; OnShow = %s' % tab.p('OnShow')),
        tab_visible_dfm=tab.p('TabVisible', True),
        n_controles=len(controles), n_interativos=len(interativos),
        por_classe={k: sum(1 for x in controles if x['classe'] == k) for k in sorted({x['classe'] for x in controles})},
        handlers_analisados=handlers_vistos,
        n_ligados_ao_vetor=sum(1 for x in controles if x.get('conversao')),
        n_com_flag=sum(1 for x in controles if x.get('flags')),
        n_com_valor_do_log=sum(1 for x in controles if any(e.get('bruto') is not None for e in x.get('valor_ecu', []))
                               or x.get('valor_ecu_flags')))
    return dict(meta=meta, controles=controles)


# ------------------------------------------------------------------ Markdown
def _m(txt, marca):
    return '%s `[%s]`' % (txt, marca)


def _c(x):
    return '`%s`' % x if x not in (None, '') else '`-`'


def _val(v):
    if isinstance(v, bool):
        return 'sim' if v else 'nao'
    return str(v)


def _rotulo_melhor(rec):
    r = rec.get('rotulo') or {}
    rc = rec.get('rotulo_do_campo') or {}
    return r, rc


def _nome_leigo(rec):
    r, rc = _rotulo_melhor(rec)
    return (r.get('por') or r.get('eng') or r.get('ita') or rc.get('por') or rc.get('eng') or rc.get('ita') or rec['nome'])


def _linhas_estado(rec, limite=14):
    out = []
    vistos = set()
    for a in sorted(rec['estado']['regras'], key=lambda x: (not x.get('da_aba', True), x['metodo'], x['endereco'])):
        val = a['valor_imediato']
        v = {1: 'verdadeiro', 0: 'falso'}.get(val) if val is not None else None
        if v is None:
            v = 'calculado' + (' por %s' % a['origem'] if a.get('origem') else '')
        cond = a.get('condicoes', []) + a.get('condicoes_chamada', [])
        chave = (a['propriedade'], v, tuple(cond), a['metodo'])
        if chave in vistos:
            continue
        vistos.add(chave)
        out.append('- `%s := %s` em `%s` (%s%s%s)%s' % (
            a['propriedade'], v, a['endereco'], a['metodo'], (', ' + a['via']) if a.get('via') else '',
            '' if a.get('da_aba', True) else '; metodo de OUTRA aba/tela',
            (' — SE ' + ' E '.join(cond)) if cond else ' — incondicional'))
    extra = len(out) - limite
    out = out[:limite]
    if extra > 0:
        out.append('- (+%d regras iguais ou parecidas; ver o JSON)' % extra)
    return out


def md_controle(rec, log_ok):
    L = []
    r, rc = _rotulo_melhor(rec)
    titulo = _nome_leigo(rec)
    L.append('### `%s` — %s' % (rec['nome'], titulo))
    L.append('')
    L.append('Tipo: **%s** (`%s`), DFM linha %s `[OBS-COD]`. Pai: `%s`.' % (rec['tipo'], rec['classe'], rec['dfm_linha'], rec['pai']))
    L.append('')
    L.append('**O que a pessoa vê**')
    L.append('')
    base = r if (r.get('ita') or r.get('eng')) else rc
    fonte_rot = 'do proprio controle' if base is r else 'do rotulo vizinho `%s` (associacao %s)' % (rc.get('componente'), rc.get('marca_associacao'))
    L.append('- Texto (%s): ita = "%s"; eng = "%s"; por = "%s" `[%s]`.' % (
        fonte_rot, base.get('ita') or '-', base.get('eng') or '-', base.get('por') or '-', base.get('marca', 'DESCONHECIDO')))
    if rec.get('unidade'):
        L.append('- Unidade ao lado: **%s** `[OBS-COD]` (rotulo `%s`).' % (rec['unidade']['texto'], rec['unidade']['componente']))
    g = rec['dfm']
    L.append('- Posicao no DFM: Left=%s Top=%s Largura=%s Altura=%s; visivel de projeto: %s; habilitado de projeto: %s `[OBS-COD]` '
             '(o programa pode mudar isso ao abrir a aba; ver "Regras").' % (
                 g.get('Left'), g.get('Top'), g.get('Width'), g.get('Height'),
                 _val(rec['estado']['inicial_dfm']['Visible']), _val(rec['estado']['inicial_dfm']['Enabled'])))
    if rec.get('itens'):
        L.append('- Opcoes da lista `[OBS-COD]`: ' + '; '.join('%d = "%s"%s' % (
            it['indice'], it['ita'], (' / ' + it['por']) if it.get('por') and it['por'] != it['ita'] else '') for it in rec['itens']) + '.')
    if rec.get('faixa'):
        f = rec['faixa']
        L.append('- Faixa permitida `[OBS-COD]`: minimo %s, maximo %s, passo (Precision) %s. %s' % (f['minimo'], f['maximo'], f['passo'], f['fonte']))
    if rec.get('hint'):
        h = rec['hint']
        L.append('- Dica ao tocar (balao) `[INFERIDO]` chave `%s`: "%s".' % (h['chave'], (h.get('por') or h.get('eng') or h.get('ita') or '').replace('\\n', ' ')))
    L.append('')
    L.append('**O que isso faz (linguagem simples)**')
    L.append('')
    ex = rec['explicacao_leiga']
    L.append('- %s `[%s]` (fonte: %s).' % (ex['texto'] or 'Sem texto: nao ha fonte publica nem prova no codigo sobre o efeito.', ex['marca'], ex['fonte']))
    if rec.get('manual'):
        mn = rec['manual']
        L.append('- Manual SW 2.16.4 C, p.%d (linha %d de sw.txt): "%s" `[OBS-COD]`.' % (mn['pagina'], mn['linha'], mn['trecho'][:300]))
    L.append('')
    L.append('**Ligacao com a ECU e conversao**')
    L.append('')
    if rec.get('conversao'):
        L.append('- Codigo de dado do formulario `[OBS-COD]`: ' + json.dumps(rec['ligacao'].get('codigo', rec['ligacao'].get('codigo_por_familia')), ensure_ascii=False) +
                 (', indice %s' % rec['ligacao'].get('indice_dfm', rec['ligacao'].get('indice_codigo')) if ('indice_dfm' in rec['ligacao'] or 'indice_codigo' in rec['ligacao']) else '') + ' (%s).' % rec['ligacao']['campo_codigo'])
        for cv in rec['conversao']:
            if 'vetor' not in cv:
                L.append('  - familia %s: %s' % (cv['familia'], cv.get('nota')))
                continue
            L.append('  - Familia %s: vetor **%s** (SerialCode %s, %s, %s) `[OBS-COD]`.' % (
                cv['familia'], cv['vetor'], cv['serial_code'], cv['classe_vetor'], cv['tipo']))
            L.append('    - tela -> ECU (setter %s): `valor_bruto = %s` com `v` = valor digitado.' % (cv['caso_set'], cv['tela_para_ecu']))
            L.append('    - ECU -> tela (getter %s): `valor_na_tela = %s`.' % (cv['caso_get'], cv['ecu_para_tela']))
            if cv.get('funcoes_auxiliares'):
                L.append('    - Usa funcoes auxiliares do .exe: %s (tabelas de conversao; ver docs/progbase/abas/_funcoes_auxiliares.json) `[OBS-COD]`.' % ', '.join(cv['funcoes_auxiliares']))
    else:
        L.append('- Sem codigo de dado proprio na rotina de amarracao 0x48A228 (`[OBS-COD]`): o valor nao passa pelo conversor generico; ver os acessos do handler abaixo.')
    if rec.get('flags'):
        for f in rec['flags']:
            L.append('- Bit de configuracao `[OBS-COD]`: **%s** (SerialCode %s), palavra %s, mascara %s, caso do setter %s, papel "%s", condicao "%s".' % (
                f['vetor'], f['serial_code'], f['palavra'], f['mascara'], f['caso'], f.get('papel'), f['condicao']))
    L.append('')
    L.append('**Valor atual na ECU do dono (LOG de 12/08/2026)**')
    L.append('')
    anyv = False
    for e in rec.get('valor_ecu', []):
        if e.get('bruto') is not None:
            anyv = True
            L.append('- %s (SC %s): bruto %s%s; na tela: **%s** `[OBS-FIO, tela DERIVADO]` (LOG linha %s).' % (
                e['vetor'], e['serial_code'], json.dumps(e['bruto']), (' (elemento %s = %s)' % (e['elemento'], e['bruto_elemento'])) if 'elemento' in e else '',
                e.get('valor_na_tela', 'nao calculado'), e['linha_log']))
        elif e.get('nota'):
            L.append('- %s (SC %s): %s `[DESCONHECIDO]`.' % (e['vetor'], e['serial_code'], e['nota']))
    for e in rec.get('valor_ecu_flags', []):
        anyv = True
        L.append('- %s palavra %s = 0x%s -> bit %s: **%s** `[OBS-FIO]` (LOG linha %s).' % (
            e['vetor'], e['palavra'], e['palavra_valor'], e['mascara'], 'LIGADO' if e['bit_ligado'] else 'desligado', e['linha_log']))
    if not anyv and not rec.get('valor_ecu'):
        L.append('- Nao ha valor lido para este controle no LOG `[DESCONHECIDO]`.')
    L.append('')
    L.append('**Ao tocar / usar, o que o programa faz (handlers)**')
    L.append('')
    for ev in rec['eventos']:
        linha = '- `%s` -> `%s` (%s) `[OBS-COD]`' % (ev['evento'], ev['handler'], ev['endereco'] or 'sem metodo publicado')
        if ev.get('nota'):
            linha += ': ' + ev['nota']
        L.append(linha)
        for a in ev.get('acessos', [])[:12]:
            if a['funcao'] in ('Caption', 'Text', 'Visible', 'Enabled') and ev['handler'] in ('EditGiriCambioEnter', 'EditGiriCambioExit', 'EditGiriKFiltroEnter', 'EditGiriKFiltroExit'):
                continue
            L.append('  - %s' % _acesso_txt(a))
    if not rec['eventos']:
        L.append('- Sem evento ligado a este controle no DFM (so exibicao).')
    L.append('')
    L.append('**Comando enviado (bytes) e o que acontece entre um comando e outro**')
    L.append('')
    L.extend(_comando_txt(rec, log_ok))
    L.append('')
    L.append('**Regras de habilitar/mostrar/esconder `[OBS-COD]`**')
    L.append('')
    est = _linhas_estado(rec)
    if est:
        L.extend(est)
    else:
        L.append('- Nenhum dos metodos publicados do TFormConfig (nem sub-rotinas ate 2 niveis) muda Visible/Enabled/Checked deste controle: vale o estado de projeto do DFM (%s) `[DERIVADO da varredura de todos os metodos; rotinas fora da RTTI nao foram varridas]`.' % (
            'OCULTO por projeto e nenhuma rotina o mostra' if rec['estado']['inicial_dfm']['Visible'] is False else 'visivel'))
    L.append('')
    return L


def _acesso_txt(a):
    f = a['funcao']
    if f in ('SetEsc', 'GetEsc', 'SetIdx', 'GetIdx'):
        cod = a.get('codigo')
        if isinstance(cod, int):
            return '%s codigo %s -> vetor %s (SC %s) em `%s`' % (f, cod, a.get('vetor'), a.get('serial_code'), a['endereco'])
        return '%s com codigo vindo do campo do controle (`[ctl+0x2EC]`/`[ctl+0x330]`) em `%s` (handler generico)' % (f, a['endereco'])
    if f.endswith(('Flag1', 'Flag2', 'Flag3')):
        bits = ', '.join('%s palavra %s mascara %s' % (x['vetor'], x['palavra'], x['mascara']) for x in a.get('bits', [])) or 'bit nao mapeado'
        return '%s caso %s -> %s em `%s`' % (f, a.get('caso'), bits, a['endereco'])
    if f == 'AcessoSD':
        t = a.get('toca', {})
        return 'chama o metodo do TStreamDati `%s` em `%s` (le: %s; escreve: %s)' % (
            a['alvo'], a['endereco'], ', '.join(t.get('le', [])[:6]) or '-', ', '.join(t.get('escreve', [])[:6]) or '-')
    if f == 'EnviaComando':
        return 'envia comando direto ao protocolo (id %s, argumentos %s) em `%s`' % (a.get('comando_id'), a.get('pushes'), a['endereco'])
    if f in ('Visible', 'Enabled', 'Caption', 'Text'):
        return 'muda %s de `%s` em `%s`' % (f, a.get('controle'), a['endereco'])
    return '%s em `%s`' % (f, a['endereco'])


def _comando_txt(rec, log_ok):
    L = []
    env = rec.get('envia') or {}
    q = env.get('escrita_valor', [])
    qf = env.get('escrita_flags', [])
    tipo = rec['classe']
    if q:
        L.append('- Gravar valor (executado no Unicorn com o codigo original; `[OBS-COD]`):')
        for x in q:
            L.append('  - %s: tela = %s -> quadro `%s`%s.' % (x['cenario'], x['valor_tela'], ' | '.join(x['quadros']) or '(nenhum)',
                                                              (' ERRO: ' + x['erro']) if x.get('erro') else ''))
        L.append('  - Estrutura do quadro: `[op][SC lo][SC hi]([indice])[valor LE][checksum = soma dos bytes anteriores]`; op = 0x10+n (n = bytes depois do SC), '
                 'p.ex. `12 05 00 17 2E` = SC 5, valor 0x17. Formato igual ao visto no LOG: `14 54 00 00 00 64 CC` (linha 48568) `[OBS-FIO]`.')
    if qf:
        L.append('- Mudar a caixa/bit (executado no Unicorn com as palavras ATUAIS da ECU do dono; `[OBS-COD]`):')
        for x in qf:
            L.append('  - %s %s (%s palavra %s mascara %s): %s -> %s, quadro `%s`.' % (
                x['acao'], 'bit' , x['vetor'], x['palavra'], x['mascara'], ' '.join(x['palavras_antes']), ' '.join(x['palavras_depois']),
                ' | '.join(x['quadros'])))
        L.append('  - O ProgBase le o vetor inteiro do cache, liga/desliga so o bit e REGRAVA o vetor completo (`35 03 00 86 2C 51 10 4B` no LOG linha 31284 e o mesmo formato para FLAG_CONF1) `[OBS-FIO]`.')
    if q or qf:
        L.append('- Entre um comando e outro `[OBS-FIO]`: a porta ecoa o quadro enviado; a ECU responde `53 00 53` (OK); o programa limpa os buffers (PURGE) e '
                 'volta a perguntar a telemetria (`48 01 49`, `48 08 50`...). Nao ha releitura do vetor gravado no LOG (linhas 31284-31420).')
        L.append('- Resposta esperada: eco do quadro + `53 00 53`. Resultado esperado: o cache do programa e a ECU passam a ter o novo valor; a tela mostra o valor ja arredondado ao passo.')
    if not q and not qf:
        if tipo in ('TButton',):
            L.append('- Botao: sem escrita de valor padrao. O que o handler faz esta na lista de acessos acima; bytes especificos so quando o handler chama `EnviaComando`/setter `[OBS-COD]` (nao executado aqui) -> **DESCONHECIDO** o quadro exato quando nao listado.')
        elif tipo == 'TDrawGrid':
            L.append('- Grade: cada celula editada chama um setter indexado (SetIdx) do handler `KeyPress`/`DblClick`; quadros por celula: `14 SC lo hi idx valor ck` (SetNumber+indice). Quadros exatos por celula: **DESCONHECIDO** (nao executados aqui).')
        elif rec['classe'] in ('TLabel', 'TPanel', 'TGroupBox', 'TScrollBox', 'TBevel', 'TShape', 'TStaticText', 'TPageControl'):
            L.append('- Controle so de exibicao: nao envia nada a ECU.')
        else:
            L.append('- Quadro exato nao calculado (controle sem conversor generico). Ver acessos do handler: **DESCONHECIDO**.')
    return L


def md_aba(d, log_ok=True):
    m = d['meta']
    L = []
    t = m['titulo']
    L.append('# %s — %s' % (m['aba'], t.get('por') or t.get('eng') or t.get('ita')))
    L.append('')
    L.append('Nome na tela: ita "%s" / eng "%s" / por "%s" `[OBS-COD]`. Aba %d de 16 do grupo D3a. '
             'Rotina de abertura (OnShow): `%s` em `%s` `[OBS-COD]`. TabVisible de projeto: %s.' % (
                 t.get('ita'), t.get('eng'), t.get('por'), m['ordem'], m['tab_show']['metodo'], m['tab_show']['endereco'], _val(m['tab_visible_dfm'])))
    L.append('')
    L.append('Legenda das marcas: `OBS-COD` = lido do DFM/codigo do ProgBase.exe (ou executado no emulador Unicorn); `OBS-FIO` = visto no Portmon da ECU do dono; '
             '`DERIVADO` = calculado a partir dessas observacoes; `INFERIDO` = deducao; `DESCONHECIDO` = sem prova.')
    L.append('')
    L.append('**Resumo**: %d controles no DFM (%s); %d interativos; %d com codigo de dado e formula; %d com caixa/bit; %d com valor lido no LOG. '
             'Fonte do .exe sha256 `%s`.' % (
                 m['n_controles'], ', '.join('%s=%d' % kv for kv in m['por_classe'].items()), m['n_interativos'], m['n_ligados_ao_vetor'],
                 m['n_com_flag'], m['n_com_valor_do_log'], SHA_EXE[:12] + '...'))
    L.append('')
    L.append('## Como o ProgBase trata estes controles (valido para a aba inteira)')
    L.append('')
    L.append(T.GENERICO_EDIT)
    L.append('')
    L.append('## Mapa de todos os controles (ordem do DFM)')
    L.append('')
    L.append('| # | Componente | Tipo | Rotulo (por / eng / ita) | Unidade | Vetor (SC) | Bit | Visivel no DFM |')
    L.append('|---|---|---|---|---|---|---|---|')
    for k, c in enumerate(d['controles']):
        r, rc = _rotulo_melhor(c)
        b = r if (r.get('ita') or r.get('eng')) else rc
        vet = ', '.join(sorted({'%s (%s)' % (x['vetor'], x['serial_code']) for x in c.get('conversao', []) if x.get('vetor')})) or '-'
        bit = ', '.join('%s[%s]&%s' % (f['vetor'], f['palavra'], f['mascara']) for f in c.get('flags', [])) or '-'
        L.append('| %d | `%s` | %s | %s / %s / %s | %s | %s | %s | %s |' % (
            k + 1, c['nome'], c['tipo'], b.get('por') or '-', b.get('eng') or '-', b.get('ita') or '-',
            (c.get('unidade') or {}).get('texto', '-'), vet, bit, _val(c['estado']['inicial_dfm']['Visible'])))
    L.append('')
    L.append('## Controles interativos, um a um')
    L.append('')
    for c in d['controles']:
        if c['classe'] in INTERATIVOS:
            L.extend(md_controle(c, log_ok))
    rot = [c for c in d['controles'] if c['classe'] not in INTERATIVOS and c['classe'] == 'TLabel']
    din = [c for c in rot if c['estado']['regras']]
    if din:
        L.append('## Rotulos com regra de mostrar/esconder ou texto dinamico')
        L.append('')
        for c in din:
            r = c['rotulo']
            L.append('### `%s` — "%s"' % (c['nome'], r.get('por') or r.get('eng') or r.get('ita') or ''))
            L.append('')
            L.append('Texto: ita "%s" / eng "%s" / por "%s" `[OBS-COD]`. Estado de projeto: visivel=%s.' % (
                r.get('ita') or '-', r.get('eng') or '-', r.get('por') or '-', _val(c['estado']['inicial_dfm']['Visible'])))
            L.extend(_linhas_estado(c, 8))
            L.append('')
    L.append('## Lacunas desta aba')
    L.append('')
    for x in d['lacunas']:
        L.append('- ' + x)
    L.append('')
    return '\n'.join(L)


# ------------------------------------------------------------------ lacunas
def lacunas_da_aba(d):
    L = []
    nomes = lambda xs: ', '.join('`%s`' % x['nome'] for x in xs)
    inter = [c for c in d['controles'] if c['classe'] in INTERATIVOS]
    sem_conv = [c for c in inter if not c.get('conversao') and not c.get('flags') and c['classe'] in ('TNumericEdit', 'TValueEditEx')]
    if sem_conv:
        L.append('Edits sem codigo de dado na rotina 0x48A228 (o valor passa por metodo proprio do TStreamDati; formula nao decodificada aqui, `DESCONHECIDO`): ' + nomes(sem_conv) + '.')
    combos = [c for c in inter if c['classe'] == 'TKeyComboBox' and not c.get('conversao') and not c.get('flags')]
    if combos:
        L.append('Combos cujo valor e gravado por metodo proprio (sem bit nem codigo generico): ' + nomes(combos) + '. Os vetores tocados aparecem na lista de acessos do handler; o significado de cada opcao no fio e `DESCONHECIDO` quando nao ha formula.')
    grids = [c for c in inter if c['classe'] == 'TDrawGrid']
    if grids:
        L.append('Grades (`TDrawGrid`): o conteudo e desenhado em `OnDrawCell`; o conteudo e os quadros por celula nao foram decodificados celula a celula: ' + nomes(grids) + '.')
    sem_leigo = [c for c in inter if not c['explicacao_leiga'].get('texto')]
    if sem_leigo:
        L.append('Sem texto leigo (sem fonte publica nem prova): ' + nomes(sem_leigo) + '.')
    sem_log = [c for c in inter if c.get('conversao') and not any(e.get('bruto') is not None for e in c.get('valor_ecu', []))]
    if sem_log:
        L.append('Ligados a vetor mas sem leitura no LOG do dono (a ECU nao foi consultada nesse SerialCode): ' + nomes(sem_log) + '.')
    L.append('Visibilidade/habilitacao: as regras listadas sao as que o codigo escreve; a combinacao final depende de dados da ECU (modelo, familia LR, MASK_FUNCTION) que o LOG so mostra em parte. O dono usa familia nao-LR (INFERIDO: o original leu SC 192, nunca 193).')
    L.append('A associacao rotulo<->campo e por nome/geometria (`INFERIDO`), e a chave da dica (HINT...) segue o nome do componente (`INFERIDO`).')
    return L


# ------------------------------------------------------------------ principal
def sha_arquivo(p):
    h = hashlib.sha256()
    with open(p, 'rb') as fh:
        h.update(fh.read())
    return h.hexdigest()


def gerar(saida, exe, log_path, manual_path, so_abas=None):
    import abas_log
    log = abas_log.ler_log(log_path) if log_path else None
    if log:
        for k in list(log['leituras']):          # SC 0 = numero de serie/chave da ECU: nao versionar
            if k.startswith('0:'):
                del log['leituras'][k]
    manual = T.carregar_manual(manual_path)
    ctx = Contexto(exe, log, manual)
    if ctx.sha != SHA_EXE:
        sys.stderr.write('AVISO: sha256 do .exe difere do esperado (%s)\n' % ctx.sha)
    em, preench = montar_emul(ctx, log)
    os.makedirs(saida, exist_ok=True)
    indice = []
    for n, aba in enumerate(ABAS):
        if so_abas and aba not in so_abas:
            continue
        d = construir_aba(ctx, aba, em, preench, log, manual, n + 1)
        d['lacunas'] = lacunas_da_aba(d)
        d['fonte'] = dict(exe_sha256=ctx.sha, dfm='docs/evidence/dfm-original/TFORMCONFIG.txt',
                          log='PortmonLOGNOVO.LOG sha256 ' + (log['sha256'] if log else 'ausente'),
                          manual='Landi Renzo Omegas SW 2.16.4 C (sw.txt)', gerador='tools/progbase/abas_a.py')
        escrever(os.path.join(saida, aba + '.json'), json.dumps(d, indent=1, ensure_ascii=False, sort_keys=True) + '\n')
        escrever(os.path.join(saida, aba + '.md'), md_aba(d) + '\n')
        indice.append(dict(aba=aba, **{k: d['meta'][k] for k in ('n_controles', 'n_interativos', 'n_ligados_ao_vetor', 'n_com_flag', 'n_com_valor_do_log')}))
    if not so_abas:
        escrever(os.path.join(saida, '_indice.json'), json.dumps(dict(abas=indice, total_controles=sum(x['n_controles'] for x in indice),
                                                                       total_interativos=sum(x['n_interativos'] for x in indice)),
                                                                  indent=1, sort_keys=True) + '\n')
        import abas_emul as E
        aux = dict(
            descricao='Funcoes auxiliares do TStreamDati usadas nas conversoes de temperatura e tabelas (executadas no Unicorn a partir do .exe) [OBS-COD].',
            curvas=E.tabelas_temperatura(exe, ctx.campos_sd, ctx.objs),
            amostras=E.conversoes_amostra(exe, ctx.campos_sd, ctx.objs),
            tipo_da_curva_A='tipo vindo de [SD+0x272C] em TStreamDati.TimerIOTimer 0x43BAD4 -> 0x430A0C (qual valor a ECU do dono usa: DESCONHECIDO; usei 0 para as amostras)',
            marca='OBS-COD')
        escrever(os.path.join(saida, '_funcoes_auxiliares.json'), json.dumps(aux, indent=1, sort_keys=True) + '\n')
        if log:
            escrever(os.path.join(saida, '_log_leituras.json'), json.dumps(log, indent=1, sort_keys=True) + '\n')
    return indice


def escrever(caminho, texto):
    atual = None
    if os.path.isfile(caminho):
        with open(caminho, encoding='utf-8') as fh:
            atual = fh.read()
    if atual != texto:
        with open(caminho, 'w', encoding='utf-8', newline='\n') as fh:
            fh.write(texto)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--exe')
    ap.add_argument('--log')
    ap.add_argument('--manual')
    ap.add_argument('--saida', default=SAIDA_PADRAO)
    ap.add_argument('--aba', action='append')
    a = ap.parse_args(argv)
    exe = achar([a.exe] + EXE_CAND, 'exe')
    if not exe:
        print('ProgBase.exe nao encontrado (use --exe ou PROGBASE_EXE)', file=sys.stderr)
        return 2
    log = achar([a.log] + LOG_CAND, 'log')
    man = achar([a.manual] + MANUAL_CAND, 'manual')
    r = gerar(a.saida, exe, log, man, a.aba)
    for x in r:
        print(x['aba'], x['n_controles'], 'controles;', x['n_interativos'], 'interativos')
    return 0


if __name__ == '__main__':
    sys.exit(main())
