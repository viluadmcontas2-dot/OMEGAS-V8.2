"""Quedas de rotação (quase-apagadas) no corpus, GNV x gasolina. Uso: python3 -I tools/sessoes/analise_quedas.py"""
import os, sys, statistics as st, collections
sys.path.insert(0, os.path.dirname(__file__))
import corpus

por = collections.defaultdict(list)
for r in corpus.telemetria():
    if r['rpm'] is not None:
        por[r['sessao']].append(r)
quedas, horas = [], collections.Counter()
for s, T in por.items():
    T.sort(key=lambda r: r['t_ms'])
    for a, b in zip(T, T[1:]):
        dt = (b['t_ms'] - a['t_ms']) / 3.6e6
        if 0 < dt < 0.001: horas[a['fuel']] += dt
    i = 0
    while i < len(T):
        r = T[i]
        if r['rpm'] < 450 and r['rpm'] > 0:
            prev = [u for u in T[max(0, i - 14):i] if r['t_ms'] - u['t_ms'] <= 2500 and u['rpm'] >= 650]
            if prev:
                p = prev[0]; lo = min(prev, key=lambda u: u['petrol_ms'] or 99)
                quedas.append(dict(s=s, fuel=p['fuel'], rpm0=p['rpm'], rpm=r['rpm'], pet0=p['petrol_ms'], pet_min=lo['petrol_ms'], map0=p['map_bar'], map_min=min((u['map_bar'] for u in prev if u['map_bar'] is not None), default=None), dt=(r['t_ms'] - p['t_ms']) / 1000))
                i += 20; continue
        i += 1
print('horas medidas:', {k: round(v, 2) for k, v in horas.items()})
for f in ('GNV', 'Gasolina', 'GASOLINA', 'PETROL'):
    pass
c = collections.Counter(q['fuel'] for q in quedas)
print('quedas por combustível:', dict(c))
for fuel in c:
    q = [x for x in quedas if x['fuel'] == fuel]
    h = horas.get(fuel, 0) or 1e-9
    pm = [x['pet_min'] for x in q if x['pet_min']]
    mm = [x['map_min'] for x in q if x['map_min'] is not None]
    print(f"{fuel}: {len(q)} quedas em {h:.2f} h = {len(q)/h:.1f}/h | petrol_ms mínimo antes: mediana {st.median(pm):.2f} (p25 {sorted(pm)[len(pm)//4]:.2f}, p75 {sorted(pm)[3*len(pm)//4]:.2f}) | MAP mínimo antes: mediana {st.median(mm):.2f} | rpm antes mediana {st.median(x['rpm0'] for x in q):.0f}")
