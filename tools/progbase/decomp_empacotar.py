#!/usr/bin/env python3
"""Empacota o pseudo-C do Ghidra (DecompLista.java -> *.jsonl) em fragmentos .gz por faixa de endereço e acrescenta 'pistas'
(strings literais e chamadas nomeadas, INFERIDO do pseudocódigo) e 'proposito' (derivado do nome RTTI) em funcoes.json.gz.
Uso: decomp_empacotar.py dec1.jsonl [dec2.jsonl ...]  -> docs/progbase/decomp/pseudocodigo/pc_NN.jsonl.gz + indice_pseudocodigo.json"""
import gzip, json, os, re, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
DEC = os.path.join(ROOT, 'docs', 'progbase', 'decomp')
POR_FRAG = 1500
STR = re.compile(r'"((?:[^"\\]|\\.){3,60})"')
CALL = re.compile(r'\b([A-Za-z_][A-Za-z0-9_]{3,})\(')
RUIDO = {'if', 'while', 'switch', 'return', 'sizeof', 'halt_baddata', 'FUN'}


def proposito(o, pistas):
    n = o.get('nome')
    if n and '.' in n:
        cl, m = n.split('.', 1)
        if m.startswith('get_'): return f'Leitor da propriedade {m[4:]} de {cl}'
        if m.startswith('set_'): return f'Gravador da propriedade {m[4:]} de {cl}'
        if m.startswith('dyn_'): return f'Método dinâmico/mensagem {m[4:]} de {cl}'
        if m.startswith('vmt_'): return f'Método virtual (slot {m[4:]}) de {cl}'
        if o['fonte'].endswith('metodo_publicado'): return f'Handler/método publicado {m} de {cl}'
    if n: return f'Símbolo {n}'
    if pistas.get('strings'): return 'INFERIDO: usa texto ' + ' | '.join(pistas['strings'][:2])
    return None


def main():
    pc = {}
    for caminho in sys.argv[1:]:
        for l in open(caminho, encoding='utf-8'):
            try:
                r = json.loads(l)
            except ValueError:
                continue
            pc['0x%08X' % r['a']] = r
    gz = os.path.join(DEC, 'funcoes.json.gz')
    doc = json.load(gzip.open(gz))
    lst = doc['funcoes']
    for o in lst:
        r = pc.get(o['a'])
        o.pop('frag', None)
        pist = {}
        if r and r['ok']:
            c = r['c']
            pist['strings'] = sorted(set(STR.findall(c)))[:6]
            pist['chama'] = sorted({m for m in CALL.findall(c) if m not in RUIDO and not m.startswith('FUN_')})[:10]
            o['pistas'] = pist
        p = proposito(o, pist)
        if p: o['proposito'] = p
    ordem = sorted(pc)
    d = os.path.join(DEC, 'pseudocodigo'); os.makedirs(d, exist_ok=True)
    for f in os.listdir(d):
        os.remove(os.path.join(d, f))
    idx = {}
    for i in range(0, len(ordem), POR_FRAG):
        nome = 'pc_%02d.jsonl.gz' % (i // POR_FRAG)
        with gzip.GzipFile(os.path.join(d, nome), 'wb', mtime=0) as g:
            for a in ordem[i:i + POR_FRAG]:
                r = pc[a]
                g.write((json.dumps({'a': a, 'ok': r['ok'], 'c': r['c']}, ensure_ascii=False, separators=(',', ':')) + '\n').encode())
                idx[a] = nome
    by = {o['a']: o for o in lst}
    for a, n in idx.items():
        if a in by: by[a]['frag'] = n
    with gzip.GzipFile(gz, 'wb', mtime=0) as g:
        g.write(json.dumps(doc, ensure_ascii=False, separators=(',', ':')).encode())
    tot = sum(os.path.getsize(os.path.join(d, f)) for f in os.listdir(d))
    ok = sum(1 for r in pc.values() if r['ok'])
    print(f'{len(pc)} funções com pseudo-C ({ok} ok), {len(os.listdir(d))} fragmentos, {tot/1e6:.1f} MB gz')


if __name__ == '__main__':
    main()
