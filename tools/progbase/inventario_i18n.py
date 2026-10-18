#!/usr/bin/env python3
"""Textos traduzidos do ProgBase.exe: recurso LANG_MAIN (JSON, 26 idiomas) cruzado com componentes e código.

Mecanismo (OBS-COD): o código chama TTranslatorExt::GetString(int,int) (export @TTranslatorExt@GetString$qii,
0x009864B0) com o ÍNDICE da chave em LANG_MAIN["LBL"] (push imediato). As chaves NÃO aparecem como literais no
código (verificado). Ligações chave -> componente são DERIVADAS por (1) nome do componente == chave (maiúsculas),
(2) legenda/dica do DFM == texto italiano da chave, (3) LANG_ID__<resto> <-> componente que termina em <resto>.
Saída: docs/progbase/inventario/i18n.json (todos os idiomas), i18n.md (ita/eng/por + uso), strings_rt.json
(RT_STRING, textos da RTL/VCL)."""
import argparse
import ast
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from inventario_comum import Binario, SAIDA, achar_exe, escrever, escrever_json, h  # noqa: E402
import inventario_dfm as dfm  # noqa: E402

PROPS_TEXTO = ('Caption', 'Hint', 'EditLabel.Caption', 'Text', 'UnitLabel.Caption', 'LegendTitle', 'Title.Text.Strings',
               'BottomAxis.Title.Caption', 'LeftAxis.Title.Caption', 'Items.Strings', 'Title', 'Filter')


def carregar_lbl(b):
    return json.loads(b.rcdata()['LANG_MAIN'])['LBL']


def componentes(arvores):
    """[(recurso, nome, classe, propriedade, texto)] de todos os textos de DFM."""
    out = []

    def walk(n, rec):
        for pn, v, t in n['props']:
            if pn in PROPS_TEXTO and t in (6, 12, 18, 20):
                try:
                    s = ast.literal_eval(v)
                except Exception:
                    continue
                out.append((rec, n['nome'], n['classe'], pn, s))
        for f in n['filhos']:
            walk(f, rec)
    for rec, (arv, _) in arvores.items():
        walk(arv, rec)
    return out


def montar(b, arvores, usos):
    lbl = carregar_lbl(b)
    por_ita = {}
    for i, x in enumerate(lbl):
        por_ita.setdefault(str(x.get('ita', '')), []).append(i)
    por_eng = {}
    for i, x in enumerate(lbl):
        por_eng.setdefault(str(x.get('eng', '')), []).append(i)
    comps = componentes(arvores)
    nomes_comp = {}
    for rec, nome, cls, pn, txt in comps:
        nomes_comp.setdefault(nome.upper(), set()).add((rec, nome, cls))
    uso_comp = {i: [] for i in range(len(lbl))}
    for rec, nome, cls, pn, txt in comps:
        if len(txt) < 2:
            continue
        for i in por_ita.get(txt, []):
            uso_comp[i].append(('texto-ita', rec, nome, cls, pn))
        if txt not in por_ita:
            for i in por_eng.get(txt, []):
                uso_comp[i].append(('texto-eng', rec, nome, cls, pn))
    for i, x in enumerate(lbl):
        for rec, nome, cls in sorted(nomes_comp.get(x['id'].upper(), [])):
            uso_comp[i].append(('nome', rec, nome, cls, ''))
        m = re.match(r'LANG_ID__(.+)$', x['id'])
        if m and len(m.group(1)) >= 6:
            suf = m.group(1).lower()
            for nm in sorted(nomes_comp):
                if nm.lower().endswith(suf):
                    for rec, nome, cls in sorted(nomes_comp[nm]):
                        uso_comp[i].append(('sufixo-LANG_ID', rec, nome, cls, ''))
    uso_cod = {}
    for ad, idx, fn in usos:
        if idx is not None and 0 <= idx < len(lbl):
            uso_cod.setdefault(idx, []).append((fn, ad))
    return lbl, uso_comp, uso_cod


def gerar(b, arvores, usos, nome_fn, saida):
    lbl, uso_comp, uso_cod = montar(b, arvores, usos)
    chaves = []
    for i, x in enumerate(lbl):
        e = {'idx': i, 'id': x['id'], 'idiomas': {k: v for k, v in x.items() if k != 'id'}}
        e['componentes'] = [{'regra': r, 'recurso': a, 'componente': c, 'classe': d, 'propriedade': p}
                            for (r, a, c, d, p) in sorted(set(uso_comp[i]))]
        e['usado_em_codigo'] = [{'funcao': h(fn), 'nome': nome_fn(fn), 'chamada': h(ad)} for fn, ad in sorted(set(uso_cod.get(i, [])))]
        chaves.append(e)
    n_comp = sum(1 for e in chaves if e['componentes'])
    n_cod = sum(1 for e in chaves if e['usado_em_codigo'])
    n_ambos = sum(1 for e in chaves if e['componentes'] or e['usado_em_codigo'])
    idiomas = sorted({k for e in chaves for k in e['idiomas']})
    meta = {'exe_sha256': b.sha256, 'n_chaves': len(chaves), 'idiomas': idiomas,
            'chaves_com_componente': n_comp, 'chaves_com_uso_em_codigo': n_cod, 'chaves_com_alguma_ligacao': n_ambos,
            'chamadas_gettranslation_com_indice': len(usos),
            'legenda': 'OBS-COD: textos e índices; ligações a componentes = DERIVADO (regra indicada).'}
    escrever_json(os.path.join(saida, 'i18n.json'), {'meta': meta, 'chaves': chaves})
    rt = b.string_table()
    escrever_json(os.path.join(saida, 'strings_rt.json'), {'meta': {'n': len(rt), 'origem': 'RT_STRING (RTL/VCL/pngimage); OBS-COD'},
                                                          'strings': {str(k): v for k, v in rt.items()}})

    def cel(e, l):
        v = str(e['idiomas'].get(l, '')).replace('|', '\\|').replace('\n', ' ⏎ ')
        return v[:140] + ('…' if len(v) > 140 else '')
    L = ['# Textos traduzidos (i18n) do ProgBase.exe', '',
         'Gerado por `tools/progbase/inventario_i18n.py`. SHA-256 do binário: `%s`.' % b.sha256, '',
         '- **%d chaves** em %d idiomas (`LANG_MAIN`, JSON UTF-8). Completo, todos os idiomas: `i18n.json`.' % (len(chaves), len(idiomas)),
         '- Idiomas: %s.' % ', '.join(idiomas),
         '- **Mecanismo (OBS-COD):** o código chama `TTranslatorExt::GetString(int,int)` (0x009864B0) passando o ÍNDICE da chave '
         '(`push imm`). As chaves não existem como literais no código. Há %d chamadas com índice imediato.' % len(usos),
         '- **Ligação chave→componente (DERIVADO):** `nome` = nome do componente igual à chave; `texto-ita`/`texto-eng` = legenda do DFM igual '
         'ao texto italiano/inglês da chave; `sufixo-LANG_ID` = parâmetro `LANG_ID__X` ↔ componente que termina em `X`.',
         '- Chaves com componente: %d; com uso direto em código: %d; com alguma ligação: %d; **sem ligação identificada: %d** (DESCONHECIDO: '
         'índice calculado ou fora do alcance da análise; ver `backlog.md`).' % (n_comp, n_cod, n_ambos, len(chaves) - n_ambos),
         '- Textos da RTL/VCL (RT_STRING, %d): `strings_rt.json`.' % len(rt), '',
         '| # | Chave | ita | eng | por | Componentes (regra) | Função(ões) que usam |', '|---:|---|---|---|---|---|---|']
    for e in chaves:
        comps = '; '.join('%s.%s (%s)' % (c['recurso'].replace('TFORM', '').title(), c['componente'], c['regra']) for c in e['componentes'][:4])
        if len(e['componentes']) > 4:
            comps += ' +%d' % (len(e['componentes']) - 4)
        fns = '; '.join('`%s`' % (u['nome'] or u['funcao']) for u in e['usado_em_codigo'][:3])
        if len(e['usado_em_codigo']) > 3:
            fns += ' +%d' % (len(e['usado_em_codigo']) - 3)
        L.append('| %d | `%s` | %s | %s | %s | %s | %s |' % (e['idx'], e['id'], cel(e, 'ita'), cel(e, 'eng'), cel(e, 'por'), comps, fns))
    escrever(os.path.join(saida, 'i18n.md'), '\n'.join(L))
    return meta


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--exe')
    ap.add_argument('--out', default=SAIDA)
    a = ap.parse_args()
    exe = achar_exe(a.exe)
    if not exe:
        sys.exit('ProgBase.exe não encontrado: defina PROGBASE_EXE')
    import inventario_codigo as cdg
    b = Binario(exe)
    arv, _ = dfm.carregar_todos(b)
    import inventario_rtti as rtti
    cl = rtti.construir(b)
    cod, nomes, un, grupos = cdg.montar(b, cl)
    usos = cdg.usos_traducao(cod)
    print(gerar(b, arv, usos, lambda fn: nomes.get(fn, (None,))[0], a.out))


if __name__ == '__main__':
    main()
