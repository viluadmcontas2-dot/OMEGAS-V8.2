#!/usr/bin/env python3
"""Arrancadas (saída da lenta) GNV × gasolina nas sessões gravadas (pedido do dono, 2026-10-16: "o carro demora a sair no GNV").
Uso: python3 -I tools/sessoes/analise_arrancadas.py <pasta com sessões extraídas (procura events_*.jsonl recursivamente)>
Evento = lenta (< 1100 rpm por >= 1,5 s) seguida de subida >= 500 rpm em até 3 s. Para cada um mede: ms até +600 rpm,
trancos (quedas >= 80 rpm durante a subida), MAP, razão gás/gasolina (ms) e temperatura do gás. Também mostra a cadência real
de gravação (dt entre quadros). Só observação: nada vira regra sem a comparação com a gasolina no mesmo MAP."""
import json, sys, glob, os, statistics as st, collections
def med(v): return round(st.median(v), 1) if v else None
fuels = collections.Counter(); out = collections.defaultdict(list); minutes = collections.Counter()
for root in sorted(glob.glob(os.path.join(sys.argv[1], '*', 'session_*_x'))):
    T = []
    for f in glob.glob(os.path.join(root, '**', '*.jsonl'), recursive=True):
        for line in open(f, encoding='utf-8', errors='replace'):
            try: e = json.loads(line)
            except Exception: continue
            if e.get('type') != 'telemetry': continue
            d = e.get('data', {}); r = d.get('rpm'); t = e.get('recordedAtMs')
            if r is None or t is None: continue
            T.append((t, float(r), d.get('fuel'), d.get('load_bar'), d.get('petrol_ms'), d.get('gas_ms_diagnostic'), d.get('gas_c'), d.get('water_c')))
    T.sort()
    dts = [T[i][0]-T[i-1][0] for i in range(1,len(T)) if T[i][0]-T[i-1][0] < 5000]
    if dts: print(os.path.basename(root), '| quadros:', len(T), '| dt mediano entre quadros (ms):', med(dts), '| p10/p90:', round(sorted(dts)[len(dts)//10]), round(sorted(dts)[len(dts)*9//10]))
    for i in range(1, len(T)): minutes[T[i][2]] += (T[i][0] - T[i-1][0]) / 60000 if T[i][0] - T[i-1][0] < 5000 else 0
    for x in T: fuels[x[2]] += 1
    i = 0
    while i < len(T):
        t0, r0, fuel = T[i][0], T[i][1], T[i][2]
        # lenta: >= 1,5 s abaixo de 1100 rpm antes
        j = i
        while j > 0 and t0 - T[j-1][0] <= 10000 and T[j-1][1] < 1100: j -= 1
        idle_ok = (r0 < 1100 and j < i and (t0 - T[j][0]) >= 1500)
        if idle_ok:
            win = [x for x in T[i:] if x[0] - t0 <= 3000]
            peak = max(x[1] for x in win) if win else r0
            if peak - r0 >= 500:
                t600 = next((x[0] - t0 for x in win if x[1] - r0 >= 600), None)
                # tranco: quedas de >= 80 rpm contra o pico ja atingido dentro da subida
                dips, hi = 0, r0
                for x in win:
                    if x[1] > hi: hi = x[1]
                    elif hi - x[1] >= 80 and x[1] < peak: dips += 1; hi = x[1]
                load = [x[3] for x in win if x[3] is not None]
                ratio = [x[5] / x[4] for x in win if x[4] and x[5] and x[4] > 0.5]
                out[fuel].append((t600, dips, med(load), med(ratio), win[0][6]))
                i += len(win); continue
        i += 1
print('frames por combustivel:', dict(fuels)); print('minutos por combustivel:', {k: round(v, 1) for k, v in minutes.items()})
for fuel, L in out.items():
    t600 = [x[0] for x in L if x[0] is not None]
    print(f'{fuel}: arrancadas={len(L)} | sem +600rpm em 3s={sum(1 for x in L if x[0] is None)} | mediana ms ate +600rpm={med(t600)} | com tranco(>=1 queda)={sum(1 for x in L if x[1] >= 1)} | mediana MAP(bar)={med([x[2] for x in L if x[2] is not None])} | mediana gas/petrol ms={med([x[3] for x in L if x[3] is not None])} | gas_c mediana={med([x[4] for x in L if x[4] is not None])}')
