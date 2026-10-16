#!/usr/bin/env python3
"""Janela do AutoMatch (Q1 do plano de 16/10): depois que a ECU escreve a curva (contador +1 e zonas de GNV zeradas),
quanto tempo passa até ela voltar a marcar a primeira zona de GNV? E com que cadência o app enxerga os campos do AutoCal?
Também mede se MUL_ACT muda entre leituras SEM AutoMatch (escrita nossa ou da ECU fora do AutoMatch).
Uso: python3 -I tools/sessoes/analise_janela_automatch.py   (corpus em tests/fixtures/sessoes-uteis)"""
import os, sys, struct, collections, statistics as st
sys.path.insert(0, os.path.dirname(__file__))
import corpus
S = collections.defaultdict(list); tipos = collections.Counter(); kwrites = collections.defaultdict(list)
for e in corpus.eventos():
    tipos[e['type']] += 1
    if e['type'] == 'k_batch_confirmed':
        kwrites[e['sessao']].append((e['recordedAtMs'], e['data'].get('calibrationType')))
    if e['type'] != 'autocal_native_snapshot':
        continue
    d = e['data']; F = {f['key']: f for f in d['fields']}
    m = F.get('MUL_ACT')
    if not m or not m.get('rawPayloadHex'):
        continue
    raw = bytes.fromhex(m['rawPayloadHex'])
    k = [struct.unpack('<H', raw[i:i + 2])[0] for i in range(0, len(raw), 2)]
    g = lambda key: (F.get(key, {}).get('rawValues') or [None])
    S[e['sessao']].append(dict(t=d['capturedAtMs'], k=k, n=g('NUM_AUTOMATCH_EXECUTED')[0], zg=g('ACQUIRED_ZONES_GAS'), en=g('AUTO_CAL_ENABLE')[0]))
print('tipos no corpus:', dict(tipos.most_common(12)))
cad, janela, drift_sem_am, drift_com_am, zonas_antes = [], [], [], [], []
for sess, L in S.items():
    L.sort(key=lambda x: x['t'])
    cad += [b['t'] - a['t'] for a, b in zip(L, L[1:]) if 0 < b['t'] - a['t'] < 60000]
    for i, (a, b) in enumerate(zip(L, L[1:])):
        mudou = sum(1 for x, y in zip(a['k'], b['k']) if x != y)
        if a['n'] is not None and b['n'] == a['n'] + 1:
            drift_com_am.append(mudou); zonas_antes.append(sum(1 for v in a['zg'] if v))
            # primeira leitura posterior com alguma zona de GNV marcada de novo
            prox = next((c for c in L[i + 2:] if any(v for v in c['zg'])), None)
            janela.append((prox['t'] - b['t']) / 1000 if prox else None)
        elif a['n'] == b['n']:
            drift_sem_am.append(mudou)
med = lambda v: round(st.median(v), 1) if v else None
print(f'leituras do AutoCal: {sum(len(v) for v in S.values())} em {len(S)} sessões · cadência mediana entre leituras: {med(cad)} ms (p90 {sorted(cad)[len(cad)*9//10] if cad else None} ms)')
print(f'AutoMatch (contador +1): {len(drift_com_am)} · nós da curva alterados pela ECU por AutoMatch: mediana {med(drift_com_am)} de 30 · zonas GNV marcadas antes: {collections.Counter(zonas_antes)}')
j = [x for x in janela if x is not None]
print(f'janela até a 1ª zona GNV voltar: mediana {med(j)} s · mín {min(j) if j else None} s · máx {max(j) if j else None} s · sem volta na sessão: {sum(1 for x in janela if x is None)}')
print(f'leituras consecutivas SEM AutoMatch em que MUL_ACT mudou: {sum(1 for x in drift_sem_am if x)} de {len(drift_sem_am)} (nós alterados nesses casos: {sorted(collections.Counter(x for x in drift_sem_am if x).items())[:8]})')
print('gravações nossas (k_batch_confirmed) por sessão:', {k: len(v) for k, v in kwrites.items()} or 'nenhuma no corpus')
