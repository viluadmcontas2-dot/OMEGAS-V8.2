"""Troca a barra do topo das telas 800×480 já prontas (17) pela versão de uma linha por célula. Idempotente."""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import autocal_800 as G
import retopbar as RT

MARK = '<div style="position:absolute;left:0;top:0;width:800px;height:40px'
ALVOS = {'17-Ajuste-local-800x480.dc.html': dict(inj='3,12', mapv='0,39', rpm='1.850', tensao='13,8')}

for nome, vals in ALVOS.items():
    p = os.path.join(G.PROJ, nome)
    h = open(p, encoding='utf-8').read()
    a = h.find(MARK)
    assert a >= 0, nome
    b = RT.balanced(h, a)
    novo = G.topbar800(**vals)
    if h[a:b] != novo:
        open(p, 'w', encoding='utf-8').write(h[:a] + novo + h[b:])
        print('barra do topo refeita:', nome)
