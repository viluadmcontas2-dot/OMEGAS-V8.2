#!/usr/bin/env python3
"""Equivalência GNV × gasolina por célula RPM×MAP, como o Ledger faz (dono, 2026-10-16: "ajuste por RPM vai no mapa").
No mesmo RPM×MAP, divide o petrol_ms que a ECU da gasolina comanda rodando em GNV pelo petrol_ms rodando em gasolina.
< 0,96 = a ECU tira combustível no GNV (mistura RICA); > 1,04 = põe (POBRE); entre = dentro da margem do dono (±4 %).
Só quadros estáveis (|Δrpm| ≤ 300 rpm/s) e células com ≥ 15 quadros de cada combustível.
Uso: python3 -I tools/sessoes/analise_equivalencia_celulas.py <pasta(s) com events_*.jsonl em qualquer profundidade>"""
import json, sys, glob, statistics as st, collections
RPM_BINS = [(700, 1100, '850'), (1100, 1600, '1350'), (1600, 2200, '1850'), (2200, 2750, '2500'), (2750, 3250, '3000'), (3250, 4000, '3500')]
def faixa(r): return next((n for lo, hi, n in RPM_BINS if lo <= r < hi), None)
cells = collections.defaultdict(lambda: {'GNV': [], 'GASOLINA': []})
for raiz in sys.argv[1:]:
    for f in glob.glob(raiz + '/**/*.jsonl', recursive=True):
        rows = []
        for line in open(f, encoding='utf-8', errors='replace'):
            try: e = json.loads(line)
            except Exception: continue
            if e.get('type') != 'telemetry': continue
            d = e['data']
            if d.get('fuel') not in ('GNV', 'GASOLINA') or d.get('rpm') is None or not d.get('petrol_ms') or d.get('load_bar') is None: continue
            rows.append((e['recordedAtMs'], d['rpm'], d['fuel'], d['load_bar'], d['petrol_ms']))
        rows.sort()
        for i in range(1, len(rows) - 1):
            t, rpm, fuel, mp, pm = rows[i]
            if abs(rows[i + 1][1] - rows[i - 1][1]) / max(1, (rows[i + 1][0] - rows[i - 1][0]) / 1000) > 300: continue
            b = faixa(rpm)
            if b: cells[(b, round(mp * 10) / 10)][fuel].append(pm)
print('faixa RPM | MAP(bar) | n GNV / n gasolina | petrol_ms GNV / gasolina | leitura')
por_rpm = collections.defaultdict(list)
ordem = {n: i for i, (_, _, n) in enumerate(RPM_BINS)}
for (b, mp), v in sorted(cells.items(), key=lambda kv: (ordem[kv[0][0]], kv[0][1])):
    if len(v['GNV']) < 15 or len(v['GASOLINA']) < 15: continue
    r = st.median(v['GNV']) / st.median(v['GASOLINA']); por_rpm[b].append(r)
    print(f'{b:>5} | {mp:4.1f} | {len(v["GNV"]):5d} / {len(v["GASOLINA"]):5d} | {r:5.3f} ({(r - 1) * 100:+.1f}%) | ' + ('RICO' if r < 0.96 else 'POBRE' if r > 1.04 else 'ok (±4%)'))
print('\nresumo por faixa de RPM (mediana das células comparáveis):', {b: f'{(st.median(v) - 1) * 100:+.1f}% ({len(v)} células)' for b, v in por_rpm.items()})
