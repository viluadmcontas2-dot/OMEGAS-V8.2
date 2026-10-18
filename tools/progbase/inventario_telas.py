#!/usr/bin/env python3
"""Árvore tela -> controle -> evento -> handler (endereço) -> o que faz, para todos os formulários do ProgBase.exe.

Fontes (todas estáticas): DFM (componentes, legendas, atalhos, eventos), RTTI (tabela de métodos publicados:
nome->endereço), análise de código por descida recursiva (chamadas, APIs do Windows, textos i18n por índice,
literais, campos tocados). O texto 'o_que_faz' vem de dados/descricoes_handlers.json (curado, com marca de
evidência) ou, se ausente, é montado automaticamente a partir dos fatos (marca INFERIDO).
Saída: docs/progbase/inventario/telas.json, telas.md (índice) e telas/<Classe>.md.
O formulário TFormConfig (33 abas) é só listado aqui: o detalhe é de docs/progbase/abas."""
import argparse
import json
import os
import re
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from inventario_comum import AQUI, Binario, SAIDA, achar_exe, escrever, escrever_json, h  # noqa: E402
import inventario_codigo as cdg  # noqa: E402
import inventario_dfm as dfm  # noqa: E402
import inventario_i18n as i18n  # noqa: E402
import inventario_rtti as rtti  # noqa: E402

DESCRICOES = os.path.join(AQUI, 'dados', 'descricoes_handlers.json')
VK = {8: 'Backspace', 9: 'Tab', 13: 'Enter', 27: 'Esc', 32: 'Space', 33: 'PgUp', 34: 'PgDn', 35: 'End', 36: 'Home',
      37: 'Left', 38: 'Up', 39: 'Right', 40: 'Down', 45: 'Ins', 46: 'Del'}
CLASSES_DIALOGO = re.compile(r'T(Open|Save|Print|PrinterSetup|Color|Font|Find|Replace|OpenPicture|SavePicture|FileOpen|FileSave)Dialog$')
CLASSES_ACIONAVEIS = re.compile(r'(Button|SpeedButton|BitBtn|MenuItem|Action$|CheckBox|RadioButton|RadioGroup|ComboBox|Timer|TabSheet|Image|Panel|Label|Chart|Grid|ListBox|Edit)', re.I)


def atalho(v):
    try:
        n = int(v)
    except (TypeError, ValueError):
        return None
    if not n:
        return None
    mods = []
    if n & 0x2000:
        mods.append('Shift')
    if n & 0x4000:
        mods.append('Ctrl')
    if n & 0x8000:
        mods.append('Alt')
    k = n & 0xFFF
    if 0x70 <= k <= 0x87:
        t = 'F%d' % (k - 0x6F)
    elif 0x30 <= k <= 0x5A:
        t = chr(k)
    else:
        t = VK.get(k, 'VK_0x%X' % k)
    return '+'.join(mods + [t])


def val(no, nome):
    for pn, v, t in no['props']:
        if pn == nome:
            return v, t
    return None, None


def texto(no, nome):
    v, t = val(no, nome)
    if v is None or t not in (6, 12, 18, 20):
        return None
    import ast
    try:
        return ast.literal_eval(v)
    except Exception:
        return v


def percorrer(no, pai=None, caminho=()):
    yield no, pai, caminho
    for f in no['filhos']:
        yield from percorrer(f, no, caminho + (no['nome'],))


class Tradutor:
    def __init__(self, lbl):
        self.por_ita = {}
        self.por_eng = {}
        for i, x in enumerate(lbl):
            self.por_ita.setdefault(str(x.get('ita', '')), i)
            self.por_eng.setdefault(str(x.get('eng', '')), i)
        self.lbl = lbl

    def __call__(self, txt):
        if not txt or len(txt) < 2:
            return None
        i = self.por_ita.get(txt)
        if i is None:
            i = self.por_eng.get(txt)
        if i is None:
            return None
        x = self.lbl[i]
        return {'chave': x['id'], 'ita': x.get('ita'), 'eng': x.get('eng'), 'por': x.get('por')}


def construir(b, cl, cod, nomes, arvores, lbl, usos_i18n, desc):
    trad = Tradutor(lbl)
    starts = sorted(cod.entradas)
    import bisect
    vmts = {int(c['vmt'], 16): c['nome'] for c in cl.values()}
    vmt_form = {int(c['vmt'], 16): c for c in cl.values() if c['app']}
    # xrefs a VMT de classes do programa: quem cria/usa a classe
    xref_vmt = {}
    for vm in vmt_form:
        pat = struct.pack('<I', vm - 88)   # BCB: __classid(T) = endereço de vmt-88 (OBS-COD)
        pos = 0
        while True:
            k = b.code.find(pat, pos)
            if k < 0:
                break
            pos = k + 1
            ad = None
            if k >= 1 and cod.vis[k - 1] and b.code[k - 1] in (0x68, 0xA1, 0xB8, 0xB9, 0xBA, 0xBB, 0xBE, 0xBF):
                ad = b.cb + k - 1
            elif k >= 2 and cod.vis[k - 2] and (b.code[k - 2] == 0x8B and b.code[k - 1] in (0x05, 0x0D, 0x15, 0x1D, 0x35, 0x3D)
                                                 or b.code[k - 2] == 0xFF and b.code[k - 1] == 0x35):
                ad = b.cb + k - 2
            if ad is not None:
                j = bisect.bisect_right(starts, ad) - 1
                if j >= 0:
                    xref_vmt.setdefault(vm, set()).add(starts[j])
    memo = {}

    def extent(a):
        j = bisect.bisect_left(starts, a)
        return starts[j + 1] if j + 1 < len(starts) else b.ce

    def nome_de(a):
        if a in nomes:
            return nomes[a][0]
        if a in cod.thunk:
            return 'thunk -> %s' % cod.thunk[a]
        return None

    def resumo(a, campos_classe, prof=0):
        key = (a, prof)
        if key in memo:
            return memo[key]
        r = cdg.resumo_funcao(cod, a, extent(a), nomes, campos_classe, vmts, vmts)
        r['i18n_chaves'] = sorted({lbl[i]['id'] for i in r['i18n_idx'] if 0 <= i < len(lbl)})
        r['chamadas_nomeadas'] = sorted({nome_de(t) for t in r['chamadas'] if nome_de(t) and not nome_de(t).startswith('unit ')})
        r['n_chamadas_sem_nome'] = len([t for t in r['chamadas'] if not nome_de(t)])
        if prof < 2:
            ind_api, ind_msg = set(), set()
            for t in r['chamadas']:
                if nome_de(t) is None and any(g0 <= t < g1 for g0, g1 in cdg.grupos_app(cdg_unidades)):
                    s = resumo(t, campos_classe, prof + 1)
                    ind_api |= set(s['apis']) | set(s['apis_indiretas'])
                    ind_msg |= set(s['i18n_chaves']) | set(s['i18n_chaves_indiretas'])
            r['apis_indiretas'] = sorted(ind_api - set(r['apis']))[:15]
            r['i18n_chaves_indiretas'] = sorted(ind_msg - set(r['i18n_chaves']))[:15]
        else:
            r['apis_indiretas'], r['i18n_chaves_indiretas'] = [], []
        memo[key] = r
        return r

    cdg_unidades = cdg.unidades(b)
    faixas_app = cdg.grupos_app(cdg_unidades)

    def em_app(t):
        return any(g0 <= t < g1 for g0, g1 in faixas_app)

    def alcance(a, campos_classe, prof_max=3, teto=60):
        """Subfunções sem nome do programa alcançáveis (BFS, profundidade <= prof_max): resumo direto de cada uma."""
        vistos, fila, out = {a}, [(a, 0)], []
        while fila and len(out) < teto:
            f, d = fila.pop(0)
            if d >= prof_max:
                continue
            r = resumo(f, campos_classe, 2)
            for t in r['chamadas']:
                if t in vistos or not em_app(t) or (nome_de(t) and not nome_de(t).startswith('thunk')):
                    continue
                vistos.add(t)
                rs = resumo(t, campos_classe, 2)
                out.append({'endereco': h(t), 'profundidade': d + 1, 'tamanho_bytes': rs['tamanho_bytes'],
                            'mensagens': rs['i18n_chaves'], 'apis': rs['apis'], 'literais': rs['literais'][:4],
                            'chamadas_nomeadas': [c for c in rs['chamadas_nomeadas'] if not c.startswith('thunk')][:8]})
                fila.append((t, d + 1))
        return out

    telas = []
    for rec, (arv, nbytes) in arvores.items():
        cls = arv['classe']
        c = next((x for x in cl.values() if x['nome'] == cls), None)
        metodos = {m['nome'].lower(): m for m in (c['metodos'] if c else [])}
        # herda métodos publicados dos ancestrais do programa
        if c:
            cur = c
            by_vmt = {x['vmt']: x for x in cl.values()}
            while cur and cur['pai_vmt'] in by_vmt:
                cur = by_vmt[cur['pai_vmt']]
                for m in cur['metodos']:
                    metodos.setdefault(m['nome'].lower(), dict(m, herdado_de=cur['nome']))
        campos_classe = {f['offset']: f['nome'] for f in (c['campos'] if c else [])}
        acoes = {}
        for no, pai, cam in percorrer(arv):
            if no['classe'].endswith('Action'):
                acoes[no['nome']] = no
        tela = {'recurso': rec, 'classe': cls, 'nome_instancia': arv['nome'], 'bytes_dfm': nbytes,
                'unidade': c['unidade'] if c else None, 'ancestral': c['pai'] if c else None,
                'tamanho_instancia': c['tamanho_instancia'] if c else None,
                'titulo': {'dfm': texto(arv, 'Caption'), 'traducao': trad(texto(arv, 'Caption'))},
                'n_componentes': sum(1 for _ in percorrer(arv)), 'rtti': c is not None,
                'aberta_por': [{'funcao': h(f), 'nome': nome_de(f)} for f in sorted(xref_vmt.get(int(c['vmt'], 16), []))] if c else [],
                'controles': [], 'menus': [], 'acoes': [], 'dialogos': [], 'timers': [], 'handlers': {}}
        vistos_h = {}

        def registrar_handler(hn):
            m = metodos.get(hn.lower())
            if not m:
                return None
            if hn not in vistos_h:
                a = int(m['endereco'], 16)
                r = resumo(a, campos_classe)
                key = '%s.%s' % (cls, m['nome'])
                d = desc.get(key)
                vistos_h[hn] = {'nome': m['nome'], 'endereco': m['endereco'], 'herdado_de': m.get('herdado_de'),
                                'tamanho_bytes': r['tamanho_bytes'], 'n_instrucoes': r['n_instrucoes'],
                                'chamadas_nomeadas': r['chamadas_nomeadas'][:25], 'n_chamadas_sem_nome': r['n_chamadas_sem_nome'],
                                'apis': r['apis'], 'apis_indiretas': r['apis_indiretas'],
                                'mensagens': r['i18n_chaves'], 'mensagens_indiretas': r['i18n_chaves_indiretas'],
                                'literais': r['literais'][:8], 'campos_tocados': r['campos'],
                                'classes_referidas': r['classes_referidas'], 'vazio': r['so_ret'],
                                'saltos_externos': [nome_de(t) or h(t) for t in r['saltos_externos']]}
                vistos_h[hn]['subfuncoes'] = alcance(a, campos_classe)
                vistos_h[hn]['o_que_faz'] = d if d else auto_descricao(vistos_h[hn], key)
            return vistos_h[hn]
        # menus
        for no, pai, cam in percorrer(arv):
            cl_no = no['classe']
            ev = [(pn, v) for pn, v, t in no['props'] if pn.startswith('On') and t == 7]
            leg_txt = texto(no, 'Caption')
            act = val(no, 'Action')[0]
            if act and act in acoes:
                leg_txt = leg_txt or texto(acoes[act], 'Caption')
            if cl_no in ('TMenuItem',) or CLASSES_DIALOGO.match(cl_no) or cl_no.endswith('Action') or ev or act:
                item = {'componente': no['nome'], 'classe': cl_no, 'legenda': leg_txt, 'traducao': trad(leg_txt),
                        'dica': texto(no, 'Hint'), 'atalho': atalho(val(no, 'ShortCut')[0]), 'acao': act,
                        'caminho': '/'.join(cam[1:] + (no['nome'],)), 'eventos': []}
                for pn, hn in ev:
                    hd = registrar_handler(hn)
                    item['eventos'].append({'evento': pn, 'handler': hn, 'endereco': hd['endereco'] if hd else None,
                                            'resolvido': hd is not None})
                if act and act in acoes:
                    for pn, v, t in acoes[act]['props']:
                        if pn.startswith('On') and t == 7:
                            hd = registrar_handler(v)
                            item['eventos'].append({'evento': act + '.' + pn, 'handler': v,
                                                    'endereco': hd['endereco'] if hd else None, 'resolvido': hd is not None})
                if cl_no == 'TMenuItem':
                    tela['menus'].append(item)
                elif cl_no.endswith('Action'):
                    tela['acoes'].append(item)
                elif CLASSES_DIALOGO.match(cl_no):
                    item['filtro'] = texto(no, 'Filter')
                    item['extensao_padrao'] = texto(no, 'DefaultExt')
                    tela['dialogos'].append(item)
                elif cl_no == 'TTimer':
                    item['intervalo_ms'] = val(no, 'Interval')[0]
                    tela['timers'].append(item)
                elif ev or act:
                    tela['controles'].append(item)
            elif cl_no == 'TTimer':
                pass
        # dialogos modais por ModalResult
        for no, pai, cam in percorrer(arv):
            mr = val(no, 'ModalResult')[0]
            if mr and mr != '0' and not any(x['componente'] == no['nome'] for x in tela['controles']):
                tela['controles'].append({'componente': no['nome'], 'classe': no['classe'], 'legenda': texto(no, 'Caption'),
                                          'traducao': trad(texto(no, 'Caption')), 'dica': None, 'atalho': None, 'acao': None,
                                          'caminho': '/'.join(cam[1:] + (no['nome'],)), 'eventos': [],
                                          'modal_result': mr})
        # métodos publicados sem ligação por DFM (ligados em código, p.ex. FormShow ou handlers de mensagens)
        ligados = set(vistos_h)
        for ml, m in sorted(metodos.items()):
            if m['nome'] not in ligados and ml not in {k.lower() for k in ligados} and not m.get('herdado_de'):
                registrar_handler(m['nome'])
                vistos_h[m['nome']]['sem_evento_no_dfm'] = True
        tela['handlers'] = dict(sorted(vistos_h.items()))
        telas.append(tela)
    return telas


def auto_descricao(hd, key):
    """Texto automático (INFERIDO), só a partir de fatos diretos do corpo da função."""
    if hd['vazio']:
        return {'texto': 'Handler vazio: o corpo é só RET (nada acontece neste evento).', 'evidencia': 'OBS-COD', 'origem': 'automatico'}
    p = []
    if hd['saltos_externos'] and hd['tamanho_bytes'] <= 12:
        p.append('encaminha (jmp) para %s' % ', '.join(hd['saltos_externos'][:2]))
    if hd['mensagens']:
        p.append('usa textos %s' % ', '.join(hd['mensagens'][:6]))
    if hd['apis']:
        p.append('chama APIs do Windows %s' % ', '.join(a.split('!')[1] for a in hd['apis'][:6]))
    nom = [c for c in hd['chamadas_nomeadas'] if not c.startswith('thunk ')]
    if nom:
        p.append('chama %s' % ', '.join(nom[:6]))
    if hd['campos_tocados']:
        p.append('toca %s' % ', '.join(hd['campos_tocados'][:8]))
    if not p:
        p.append('sem fatos diretos extraíveis (%d bytes; chama %d funções sem nome)' % (hd['tamanho_bytes'], hd.get('n_chamadas_sem_nome', 0)))
    return {'texto': '; '.join(p), 'evidencia': 'INFERIDO', 'origem': 'automatico'}


def md_tela(t):
    L = ['# %s (recurso `%s`)' % (t['classe'], t['recurso']), '',
         'Gerado por `tools/progbase/inventario_telas.py`. Evidência: DFM+RTTI = OBS-COD; "o que faz" automático = INFERIDO; curado = marca própria.', '',
         '- Instância DFM: `%s`; unidade: `%s`; ancestral: `%s`; tamanho de instância: %s; componentes no DFM: %d.' % (
             t['nome_instancia'], t['unidade'], t['ancestral'], ('0x%X' % t['tamanho_instancia']) if t['tamanho_instancia'] else '-', t['n_componentes'])]
    tt = t['titulo']
    if tt['dfm']:
        tr = tt['traducao']
        L.append('- Título: "%s"%s' % (tt['dfm'], (' — eng: "%s" / por: "%s"' % (tr['eng'], tr['por'])) if tr else ''))
    if t['aberta_por']:
        L.append('- Aberta/criada por (xref ao ponteiro de classe vmt-88, OBS-COD): %s' % ', '.join('`%s` (%s)' % (x['nome'] or x['funcao'], x['funcao']) for x in t['aberta_por']))
    else:
        L.append('- Aberta/criada por: nenhuma referência direta à VMT encontrada (DESCONHECIDO: criada por tabela/Application.CreateForm indireto).')
    L.append('')

    def tab(titulo, itens, cols):
        if not itens:
            return
        L.extend(['## %s (%d)' % (titulo, len(itens)), '', '| ' + ' | '.join(c[0] for c in cols) + ' |', '|' + '---|' * len(cols)])
        for it in itens:
            L.append('| ' + ' | '.join(str(c[1](it)).replace('|', '\\|').replace('\n', ' ') for c in cols) + ' |')
        L.append('')

    def leg(it):
        tr = it['traducao']
        s = it['legenda'] or ''
        if tr and tr.get('eng') and tr['eng'] != s:
            s += ' / %s / %s' % (tr['eng'], tr['por'])
        return s

    def evs(it):
        return '; '.join('%s→`%s`%s' % (e['evento'], e['handler'], (' (%s)' % e['endereco']) if e['endereco'] else ' (não resolvido)') for e in it['eventos'])
    cols = [('Caminho/componente', lambda i: i['caminho']), ('Classe', lambda i: i['classe']), ('Legenda (ita / eng / por)', leg),
            ('Atalho', lambda i: i['atalho'] or ''), ('Eventos → handler (endereço)', evs)]
    tab('Menus', t['menus'], cols)
    tab('Ações', t['acoes'], cols)
    tab('Controles com evento/ação', t['controles'], cols)
    tab('Caixas de diálogo (componentes)', t['dialogos'], cols + [('Filtro', lambda i: i.get('filtro') or '')])
    tab('Temporizadores', t['timers'], cols + [('Intervalo ms', lambda i: i.get('intervalo_ms') or '')])
    if t['handlers']:
        L.extend(['## Handlers (%d): o que cada um faz' % len(t['handlers']), '',
                  '| Handler | Endereço | Bytes | Evidência | O que faz | Textos diretos | APIs diretas | Indireto (via subfunções) |', '|---|---|---:|---|---|---|---|---|'])
        for n, hd in t['handlers'].items():
            d = hd['o_que_faz']
            ind = ', '.join((hd['mensagens_indiretas'][:4] + [a.split('!')[1] for a in hd['apis_indiretas'][:4]]))
            L.append('| `%s`%s | %s | %d | %s | %s | %s | %s | %s |' % (
                n, ' (sem evento no DFM)' if hd.get('sem_evento_no_dfm') else '', hd['endereco'], hd['tamanho_bytes'], d['evidencia'],
                d['texto'].replace('|', '\\|'), ', '.join(hd['mensagens'][:6]),
                ', '.join(a.split('!')[1] for a in hd['apis'][:6]), ind))
        L.append('')
        cad = [(n, hd) for n, hd in t['handlers'].items() if hd.get('subfuncoes')]
        if cad:
            L.extend(['### Subfunções sem nome com fatos diretos, alcançadas pelos handlers (profundidade <= 3; OBS-COD)', ''])
            for n, hd in cad:
                L.append('- `%s` (%s):' % (n, hd['endereco']))
                com_fato = [sf for sf in hd['subfuncoes'] if sf['mensagens'] or sf['apis'] or sf['chamadas_nomeadas']]
                for sf in com_fato[:12]:
                    fatos = []
                    if sf['mensagens']:
                        fatos.append('textos ' + ','.join(sf['mensagens'][:4]))
                    if sf['apis']:
                        fatos.append('APIs ' + ','.join(a.split('!')[1] for a in sf['apis'][:4]))
                    if sf['chamadas_nomeadas']:
                        fatos.append('chama ' + ','.join(sf['chamadas_nomeadas'][:4]))
                    L.append('  - %ssub_%s (%d B)%s' % ('  ' * (sf['profundidade'] - 1), sf['endereco'][2:].lstrip('0'),
                                                       sf['tamanho_bytes'], (': ' + '; '.join(fatos)) if fatos else ''))
                L.append('  - (+%d subfunções sem fatos diretos ou fora da lista; telas.json)' % (len(hd['subfuncoes']) - min(12, len(com_fato))))
            L.append('')
    return '\n'.join(L)


def gerar_com(b, cl, cod, nomes, arv, lbl, usos, saida):
    desc = {}
    if os.path.isfile(DESCRICOES):
        desc = json.load(open(DESCRICOES, encoding='utf-8'))
        desc = {k: v for k, v in desc.items() if not k.startswith('_')}
    telas = construir(b, cl, cod, nomes, arv, lbl, usos, desc)
    escrever_json(os.path.join(saida, 'telas.json'), {'meta': {'exe_sha256': b.sha256, 'n_telas': len(telas),
                                                              'legenda': 'OBS-COD: DFM/RTTI/endereços; o_que_faz: ver campo evidencia.'},
                                                    'telas': telas})
    idx = ['# Telas do ProgBase.exe: árvore tela → controle → handler', '',
           'Gerado por `tools/progbase/inventario_telas.py` (SHA-256 `%s`). Um arquivo por tela em `telas/`; dados completos em `telas.json`.' % b.sha256, '',
           '| Tela | Recurso | Título | Componentes | Menus | Ações | Controles c/ evento | Diálogos | Handlers | Aberta por |', '|---|---|---|---:|---:|---:|---:|---:|---:|---|']
    for t in telas:
        escrever(os.path.join(saida, 'telas', t['classe'] + '.md'), md_tela(t))
        idx.append('| [%s](telas/%s.md) | %s | %s | %d | %d | %d | %d | %d | %d | %s |' % (
            t['classe'], t['classe'], t['recurso'], (t['titulo']['dfm'] or '').replace('|', '/'), t['n_componentes'], len(t['menus']),
            len(t['acoes']), len(t['controles']), len(t['dialogos']), len(t['handlers']),
            ', '.join('`%s`' % (x['nome'] or x['funcao']) for x in t['aberta_por'][:2])))
    escrever(os.path.join(saida, 'telas.md'), '\n'.join(idx))
    return telas


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--exe')
    ap.add_argument('--out', default=SAIDA)
    a = ap.parse_args()
    exe = achar_exe(a.exe)
    if not exe:
        sys.exit('ProgBase.exe não encontrado: defina PROGBASE_EXE')
    b = Binario(exe)
    arv, _ = dfm.carregar_todos(b)
    cl = rtti.construir(b)
    cod, nomes, un, grupos = cdg.montar(b, cl)
    usos = cdg.usos_traducao(cod)
    lbl = i18n.carregar_lbl(b)
    telas = gerar_com(b, cl, cod, nomes, arv, lbl, usos, a.out)
    print(len(telas), sum(len(t['handlers']) for t in telas))


if __name__ == '__main__':
    main()
