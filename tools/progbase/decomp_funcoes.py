#!/usr/bin/env python3
"""Consolida TODAS as funções do ProgBase.exe: Ghidra (funcs.jsonl) + RTTI (rtti.json) -> funcoes.json.gz,
cobertura de .text e backlog. Determinístico/idempotente. Marcas: OBS-COD (nome do RTTI/imports), INFERIDO (unidade por
intervalo contíguo: o Delphi liga as units em sequência), DESCONHECIDO (FUN_xxxx sem unidade).
Uso: decomp_funcoes.py FUNCS.JSONL [PB.EXE]   -> docs/progbase/decomp/{funcoes.json.gz,cobertura.json,alvos_decomp.txt}"""
import gzip, json, os, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
DEC = os.path.join(ROOT, 'docs', 'progbase', 'decomp')
TEXT0, TEXTN = 0x401000, 0x650000 + 0x401000 - 0x401000   # .text: VA 0x401000, tamanho virtual 0x650000 (ver pefile)


def app_unit(u):
    """Units do próprio ProgBase (no RTTI têm prefixo '@')."""
    return u.startswith('@')


def main():
    funcs = [json.loads(l) for l in open(sys.argv[1], encoding='utf-8')]
    rtti = json.load(open(os.path.join(DEC, 'rtti.json'), encoding='utf-8'))
    cls_unit = {c['nome']: c['unidade'] for c in rtti['classes']}
    nomes = rtti['nomes_por_endereco']
    funcs = [f for f in funcs if not f['ext']]
    funcs.sort(key=lambda f: f['a'])
    por_end = {}
    for f in funcs:
        h = '0x%08X' % f['a']
        r = nomes.get(h)
        o = {'a': h, 'tam': f['s'], 'insn': f['i'], 'thunk': f['th'], 'nome': None, 'fonte': 'DESCONHECIDO', 'classe': None,
             'unidade': None, 'un_fonte': None, 'cc': ['0x%08X' % x for x in f['cc']], 'cr': ['0x%08X' % x for x in f['cr']]}
        if r:
            o.update(nome=r[0]['nome'], fonte='OBS-COD:' + r[0]['fonte'], classe=r[0]['classe'])
            o['unidade'] = cls_unit.get(r[0]['classe']); o['un_fonte'] = 'OBS-COD'
            if len(r) > 1:
                o['aliases'] = [x['nome'] for x in r[1:]]
        elif not f['n'].startswith(('FUN_', 'thunk_FUN_')):
            o.update(nome=f['n'], fonte='OBS-COD:ghidra_simbolo')
        por_end[h] = o
    lst = [por_end['0x%08X' % f['a']] for f in funcs]
    # unidade por intervalo contíguo: entre duas âncoras da mesma unit (INFERIDO)
    anc = [(i, o['unidade']) for i, o in enumerate(lst) if o['un_fonte'] == 'OBS-COD' and o['unidade']]
    for (i, u), (j, v) in zip(anc, anc[1:]):
        if u == v:
            for k in range(i + 1, j):
                if lst[k]['unidade'] is None:
                    lst[k]['unidade'] = u; lst[k]['un_fonte'] = 'INFERIDO'
    # nomes de chamadores/chamados já estão por endereço; calcula cobertura
    tot = sum(o['tam'] for o in lst if TEXT0 <= int(o['a'], 16) < TEXT0 + 0x650000)
    nomeado = sum(o['tam'] for o in lst if o['nome'])
    rtti_b = sum(o['tam'] for o in lst if o['fonte'].startswith('OBS-COD:metodo') or o['fonte'].startswith('OBS-COD:propriedade'))
    com_un = sum(o['tam'] for o in lst if o['unidade'])
    app = [o for o in lst if o['unidade'] and app_unit(o['unidade'])]
    pub = [o for o in lst if o['fonte'] == 'OBS-COD:metodo_publicado']
    alvo = {o['a'] for o in app} | {o['a'] for o in pub}
    # chamados diretos (1 salto) de funções do app, pequenos, sem unidade: ajudam a ler o handler
    for o in app + pub:
        for c in o['cc']:
            p = por_end.get(c)
            if p and p['unidade'] is None and not p['thunk'] and p['tam'] < 3000:
                alvo.add(c)
    alvo = sorted(alvo)
    gigantes = [o['a'] for o in lst if o['tam'] > 60000]
    alvo = [a for a in alvo if a not in gigantes]
    cobertura = {
        'marca': 'OBS-COD (contagens) ; INFERIDO (unidade por intervalo)',
        'text_bytes_virtuais': 0x650000, 'funcoes': len(lst),
        'bytes_em_funcoes_ghidra': tot, 'pct_text_em_funcoes': round(100 * tot / 0x650000, 2),
        'bytes_funcoes_nomeadas': nomeado, 'pct_text_nomeado': round(100 * nomeado / 0x650000, 2),
        'bytes_nomeados_por_rtti': rtti_b, 'bytes_com_unidade': com_un, 'pct_text_com_unidade': round(100 * com_un / 0x650000, 2),
        'n_nomeadas': sum(1 for o in lst if o['nome']), 'n_sem_nome': sum(1 for o in lst if not o['nome']),
        'n_funcoes_app': len(app), 'bytes_app': sum(o['tam'] for o in app), 'n_publicados': len(pub),
        'alvos_decompilacao': len(alvo), 'funcoes_gigantes_fora_do_alvo': gigantes,
        'bytes_text_sem_funcao': 0x650000 - tot,
    }
    os.makedirs(DEC, exist_ok=True)
    with gzip.GzipFile(os.path.join(DEC, 'funcoes.json.gz'), 'wb', mtime=0) as g:
        g.write(json.dumps({'marca': 'OBS-COD', 'origem': 'Ghidra 12.1.4 (ExportFuncs.java) + RTTI (decomp_rtti.py)', 'n': len(lst), 'funcoes': lst},
                           ensure_ascii=False, separators=(',', ':')).encode())
    json.dump(cobertura, open(os.path.join(DEC, 'cobertura.json'), 'w', encoding='utf-8'), indent=1, ensure_ascii=False, sort_keys=True)
    open(os.path.join(DEC, 'alvos_decomp.txt'), 'w').write('\n'.join(alvo) + '\n')
    print(json.dumps(cobertura, indent=1))


if __name__ == '__main__':
    main()
