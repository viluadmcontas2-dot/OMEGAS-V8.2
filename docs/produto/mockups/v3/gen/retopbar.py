"""Reordena a barra do topo de todas as telas 1280×720 (dono, 2026-10-27):
combustível atual · nível · injeção · MAP · RPM · tensão, e troca os rótulos longos por "MAP" e "RPM".
Uso: python3 -I retopbar.py [pasta-do-projeto]      (idempotente: rodar de novo não muda nada)
"""
import glob, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, '..', 'project')
MARK = '<div style="position:absolute;left:0;top:0;width:1280px;height:56px'

def balanced(html, start):
    """Índice logo depois do </div> que fecha o <div> que começa em `start`."""
    depth = 0
    for m in re.finditer(r'<div\b|</div>', html[start:]):
        depth += 1 if m.group(0).startswith('<div') else -1
        if depth == 0:
            return start + m.end()
    raise ValueError('div sem fechar')

def filhos(html):
    """Blocos <div>…</div> de primeiro nível dentro de `html` (o miolo da barra)."""
    out, i = [], 0
    while True:
        j = html.find('<div', i)
        if j < 0:
            return out
        k = balanced(html, j)
        out.append(html[j:k])
        i = k

def texto(bloco):
    return re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', bloco)).strip()

def chave(bloco):
    t = texto(bloco)
    for k, pat in (('tensao', 'Tensão'), ('map', 'Pressão da admissão'), ('map', 'MAP'), ('inj', 'Inj. gasolina'),
                   ('rpm', 'Rotação'), ('rpm', 'RPM'), ('nivel', 'Nível')):
        if t.startswith(pat):
            return k
    return 'lampada'

def refazer(html):
    a = html.find(MARK)
    if a < 0:
        return None
    fim = balanced(html, a)
    topo = html[a:fim]
    abre = topo[:topo.index('>') + 1]
    miolo = topo[len(abre):-len('</div>')]
    blocos = filhos(miolo)
    por = {}
    for b in blocos:
        por[chave(b)] = b
    faltam = {'lampada', 'nivel', 'inj', 'map', 'rpm', 'tensao'} - set(por)
    if faltam:
        raise ValueError('faltam blocos na barra: %s' % sorted(faltam))
    por['map'] = por['map'].replace('Pressão da admissão', 'MAP')
    por['rpm'] = por['rpm'].replace('>Rotação<', '>RPM<')
    ordem = ['lampada', 'nivel', 'inj', 'map', 'rpm', 'tensao']
    novo = abre + '\n' + '\n'.join(por[k] for k in ordem) + '\n</div>'
    return html[:a] + novo + html[fim:]

if __name__ == '__main__':
    mudou = 0
    for f in sorted(glob.glob(os.path.join(PROJ, '*.dc.html'))):
        h = open(f, encoding='utf-8').read()
        n = refazer(h)
        if n is None:
            continue
        if n != h:
            open(f, 'w', encoding='utf-8').write(n)
            mudou += 1
            print('barra do topo refeita:', os.path.basename(f))
    print('%d telas alteradas' % mudou)
