#!/usr/bin/env python3
"""Textos didaticos (linguagem leiga) e citacoes do manual Landi Renzo Omegas SW 2.16.4 C para os controles das abas."""
import re

TEXTOS = {}


def carregar_manual(caminho):
    """Indice do manual: lista de (linha, pagina, texto_normalizado)."""
    if not caminho:
        return None
    with open(caminho, encoding='utf-8', errors='replace') as fh:
        linhas = fh.read().split('\n')
    ff, out = 0, []
    for n, l in enumerate(linhas, 1):
        ff += l.count('\f')
        t = re.sub(r'\s+', ' ', l.replace('\f', ' ')).strip()
        out.append((n, ff + 1, t))
    return out


def norm(s):
    return re.sub(r'[^a-z0-9 ]+', ' ', str(s).lower().replace('&&', ' ')).split()


def citar_manual(idx, rec):
    if not idx:
        return None
    cands = []
    for k in ('rotulo', 'rotulo_do_campo'):
        r = rec.get(k) or {}
        if r.get('eng'):
            cands.append(r['eng'])
    for e in cands:
        pal = norm(e)
        if len(pal) < 2:
            continue
        alvo = ' '.join(pal)
        for n, pg, t in idx:
            if alvo in ' '.join(norm(t)):
                trecho = ' '.join(x[2] for x in idx[n - 1:n + 3] if x[2])
                return dict(pagina=pg, linha=n, trecho=trecho[:400], chave=e, marca='OBS-COD',
                            fonte='sw.txt (manual SW 2.16.4 C), linha %d, pagina %d do PDF' % (n, pg))
    return None


def explicar(c, rec, idx, i18n):
    t = TEXTOS.get(c.nome)
    if t:
        return dict(texto=t[0], fonte=t[1], marca=t[2])
    return dict(texto=None, fonte='sem fonte', marca='DESCONHECIDO')
