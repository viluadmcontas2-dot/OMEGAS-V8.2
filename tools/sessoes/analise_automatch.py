"""O que o AutoMatch da ECU faz com a curva K e com as zonas adquiridas, medido nas sessões reais (corpus).
MUL_ACT é decodificado do rawPayloadHex (30 × u16 LE, Q14), porque o app antigo marcava o campo como INVÁLIDO (esperava 18 nós).
Uso: python3 -I tools/sessoes/analise_automatch.py"""
import collections, os, statistics as st, struct, sys
sys.path.insert(0, os.path.dirname(__file__))
import corpus

S = collections.defaultdict(list)
for e in corpus.eventos():
    if e['type'] != 'autocal_native_snapshot':
        continue
    d = e['data']; F = {f['key']: f for f in d['fields']}
    m = F.get('MUL_ACT')
    if not m or not m.get('rawPayloadHex'):
        continue
    raw = bytes.fromhex(m['rawPayloadHex'])
    k = [struct.unpack('<H', raw[i:i + 2])[0] / 16384 for i in range(0, len(raw), 2)]
    g = lambda key: (F.get(key, {}).get('rawValues') or [None])
    S[e['sessao']].append(dict(t=d['capturedAtMs'], k=k, n=g('NUM_AUTOMATCH_EXECUTED')[0], zg=g('ACQUIRED_ZONES_GAS'), zp=g('ACQUIRED_ZONES_PETROL')))
steps, gas_zero, pet_kept = [], 0, 0
for L in S.values():
    L.sort(key=lambda x: x['t'])
    for a, b in zip(L, L[1:]):
        if a['n'] is None or b['n'] != a['n'] + 1:
            continue
        f = lambda lo, hi: st.mean(b['k'][i] / a['k'][i] - 1 for i in range(lo, hi)) * 100
        steps.append((f(0, 8), f(8, 16), f(16, 24)))
        gas_zero += all(v == 0 for v in b['zg']); pet_kept += b['zp'] == a['zp']
print('eventos de AutoMatch observados (contador +1):', len(steps))
print('zonas de GNV zeradas logo depois do AutoMatch:', gas_zero, 'de', len(steps), '| zonas de gasolina mantidas:', pet_kept, 'de', len(steps))
for name, i in (('nós 0–7  (0,5–4 ms)', 0), ('nós 8–15 (4,5–8,5 ms)', 1), ('nós 16–23 (9–14 ms)', 2)):
    v = [s[i] for s in steps]
    print(f'{name}: mediana {st.median(v):+.1f}% · mín {min(v):+.1f}% · máx {max(v):+.1f}% · negativos {sum(x < -0.5 for x in v)}/{len(v)}')
