"""Linearidade gasolina × GNV na desaceleração (pedido do dono, 2026-10-13): o que a gasolina faz, no mesmo momento em
que o GNV engasga, e que o GNV não faz? Uso: python3 -I tools/sessoes/analise_linearidade_queda.py

Método (só observação do corpus; nada vira regra):
 1. Evento = desaceleração: rpm cai de >= 1300 para <= 1000 em até 1,5 s (alívio do acelerador).
 2. Para cada evento mede: afundamento = rpm_lenta_final - rpm_mínimo (lenta final = mediana de rpm de +3 s a +6 s),
    petrol_ms e MAP no instante do mínimo, e (no GNV) a razão gas_ms / petrol_ms vs a razão em regime no mesmo petrol_ms.
 3. Compara gasolina × GNV (e GNV após corte). Se o GNV afunda mais, a razão gás/gasolina diz se ele fica POBRE ou RICO."""
import bisect, collections, os, statistics as st, sys
sys.path.insert(0, os.path.dirname(__file__))
import corpus

def pct(v, p):
    v = sorted(v)
    return v[min(len(v) - 1, int(len(v) * p))] if v else float('nan')

por = collections.defaultdict(list)
for r in corpus.telemetria():
    if r['rpm'] is not None and r['petrol_ms'] is not None:
        por[r['sessao']].append(r)

# razão gás/gasolina em regime (GNV): por faixa de petrol_ms (0,5 ms), mediana das amostras estáveis (rpm 1500-3500, sem corte)
steady = collections.defaultdict(list)
for T in por.values():
    for r in T:
        if r['fuel'] == 'GNV' and r['gas_ms'] and r['petrol_ms'] > 1.0 and 1500 <= r['rpm'] <= 3500:
            steady[round(r['petrol_ms'] * 2) / 2].append(r['gas_ms'] / r['petrol_ms'])
steady_ratio = {k: st.median(v) for k, v in steady.items() if len(v) >= 200}

events = []
for s, T in por.items():
    T.sort(key=lambda r: r['t_ms'])
    ts = [r['t_ms'] for r in T]
    i = 0
    while i < len(T):
        r = T[i]
        if r['rpm'] >= 1300:
            j = bisect.bisect_right(ts, r['t_ms'] + 1500)
            win = T[i:j]
            low = [u for u in win if u['rpm'] <= 1000]
            if low and (win[-1]['t_ms'] - r['t_ms']) > 400:
                t0 = low[0]['t_ms']
                after = [u for u in T[bisect.bisect_left(ts, t0 + 3000):bisect.bisect_right(ts, t0 + 6000)]]
                near = T[bisect.bisect_left(ts, t0):bisect.bisect_right(ts, t0 + 3000)]
                if len(after) >= 8 and near:
                    idle = st.median(u['rpm'] for u in after)
                    mn = min(near, key=lambda u: u['rpm'])
                    if 500 <= idle <= 1100:
                        fuel = r['fuel']
                        cut = any(u['fuel'] == 'CUTOFF' for u in T[max(0, i - 6):i + 30])
                        ratio = None
                        if fuel == 'GNV' and mn['gas_ms'] and mn['petrol_ms'] > 1.0:
                            ref = steady_ratio.get(round(mn['petrol_ms'] * 2) / 2)
                            if ref: ratio = (mn['gas_ms'] / mn['petrol_ms']) / ref
                        events.append(dict(fuel='GNV+corte' if fuel == 'GNV' and cut else fuel, under=idle - mn['rpm'], pet=mn['petrol_ms'], mp=mn['map_bar'], ratio=ratio, dyn=mn['dyn']))
                        i = bisect.bisect_right(ts, t0 + 3000); continue
        i += 1

print(f'eventos de desaceleração: {len(events)}')
print('fuel | n | afundamento rpm (mediana/p90) | % com afundamento > 300 | petrol_ms no mínimo (mediana) | MAP no mínimo | razão gás/gasolina vs regime (mediana, p10–p90)')
for fuel in ('GASOLINA', 'GNV', 'GNV+corte'):
    e = [x for x in events if x['fuel'] == fuel]
    if not e: continue
    u = [x['under'] for x in e]
    rr = [x['ratio'] for x in e if x['ratio']]
    mp = [x['mp'] for x in e if x['mp'] is not None]
    rt = f"{st.median(rr):.3f} ({pct(rr, .1):.3f}–{pct(rr, .9):.3f}, n={len(rr)})" if rr else '—'
    print(f"{fuel} | {len(e)} | {st.median(u):.0f}/{pct(u, .9):.0f} | {100 * sum(1 for x in u if x > 300) / len(u):.0f}% | {st.median(x['pet'] for x in e):.2f} | {st.median(mp):.2f} | {rt}")

# Nos eventos GNV: afundamento maior acontece com razão menor (pobre) ou maior (rica)?
g = [x for x in events if x['fuel'].startswith('GNV') and x['ratio']]
if len(g) >= 10:
    deep = [x['ratio'] for x in g if x['under'] > 300]
    shallow = [x['ratio'] for x in g if x['under'] <= 150]
    if deep and shallow:
        print(f"GNV: afundamento > 300 rpm → razão mediana {st.median(deep):.3f} (n={len(deep)}); <= 150 rpm → {st.median(shallow):.3f} (n={len(shallow)})")

# ---- controle de viés: mesma carga de partida (petrol_ms no instante do cruzamento) para gasolina e GNV
print('\nafundamento por faixa de petrol_ms NO MÍNIMO (controle de carga):')
for lo, hi in ((0, 3), (3, 6), (6, 9), (9, 99)):
    row = []
    for fuel in ('GASOLINA', 'GNV', 'GNV+corte'):
        u = [x['under'] for x in events if x['fuel'] == fuel and lo <= x['pet'] < hi]
        row.append(f"{fuel}: n={len(u)} mediana {st.median(u):.0f}" if u else f"{fuel}: n=0")
    print(f"  {lo}–{hi} ms | " + ' | '.join(row))
print('\nrazão gás/gasolina em regime (GNV) por faixa de petrol_ms (a conta da ECU, linearidade):')
print('  ' + ' · '.join(f"{k:.1f} ms={v:.2f}" for k, v in sorted(steady_ratio.items()) if k <= 12))

# ---- causa ou reação? razão gás/gasolina ANTES do mínimo (a ECU pode reagir ao afundamento depois que ele começa)
def ratio_at(T, ts, t, steady_ratio):
    k = bisect.bisect_left(ts, t)
    for u in T[max(0, k - 1):k + 2]:
        if u['fuel'] == 'GNV' and u['gas_ms'] and u['petrol_ms'] > 1.0:
            ref = steady_ratio.get(round(u['petrol_ms'] * 2) / 2)
            if ref: return (u['gas_ms'] / u['petrol_ms']) / ref
    return None

lead = {off: {'fundo': [], 'raso': []} for off in (-1500, -1000, -500, -250, 0, 250)}
for s, T in por.items():
    ts = [r['t_ms'] for r in T]
    i = 0
    while i < len(T):
        r = T[i]
        if r['rpm'] >= 1300 and r['fuel'] == 'GNV':
            j = bisect.bisect_right(ts, r['t_ms'] + 1500)
            low = [u for u in T[i:j] if u['rpm'] <= 1000]
            if low:
                t0 = low[0]['t_ms']
                after = T[bisect.bisect_left(ts, t0 + 3000):bisect.bisect_right(ts, t0 + 6000)]
                near = T[bisect.bisect_left(ts, t0):bisect.bisect_right(ts, t0 + 3000)]
                if len(after) >= 8 and near:
                    idle = st.median(u['rpm'] for u in after)
                    mn = min(near, key=lambda u: u['rpm'])
                    if 500 <= idle <= 1100:
                        grupo = 'fundo' if idle - mn['rpm'] > 300 else 'raso' if idle - mn['rpm'] <= 150 else None
                        if grupo:
                            for off in lead:
                                x = ratio_at(T, ts, mn['t_ms'] + off, steady_ratio)
                                if x: lead[off][grupo].append(x)
                        i = bisect.bisect_right(ts, t0 + 3000); continue
        i += 1
print('\nrazão gás/gasolina (÷ regime) em relação ao instante do mínimo de rpm — mediana (n): afundamento > 300 rpm | <= 150 rpm')
for off, d in lead.items():
    f, rz = d['fundo'], d['raso']
    if f and rz:
        print(f"  {off:+5d} ms: fundo {st.median(f):.3f} (n={len(f)}) | raso {st.median(rz):.3f} (n={len(rz)})")
