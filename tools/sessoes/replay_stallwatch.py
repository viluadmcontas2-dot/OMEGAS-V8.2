"""Réplica fiel (Python) da lógica nova do StallWatch para validar no corpus real antes de portar/ver a CI do Kotlin.
Uso: python3 -I tools/sessoes/replay_stallwatch.py  -> contagens de apagões, desligamentos na lenta e tropeços."""
import collections, os, sys
sys.path.insert(0, os.path.dirname(__file__))
import corpus

RUNNING, DEAD, NEAR, CONFIRM, LOOK, NEAR_MIN_MS = 600.0, 300.0, 500.0, 800, 4000, 300
KEYOFF_MAX_RPM = 1000.0   # nunca passou de 1000 rpm nos últimos segundos = desligou na lenta (chave), não apagou

def replay(T):
    recent, stalls, nears, dropAt, ctx, stalled, near = [], [], [], None, [], False, None
    for f in T:
        recent.append(f)
        while recent and f['t_ms'] - recent[0]['t_ms'] > LOOK + CONFIRM + 2000: recent.pop(0)
        rpm = f['rpm']
        if rpm >= RUNNING:
            if near is not None and not stalled and f['t_ms'] - near['t'] >= NEAR_MIN_MS:
                nears.append(near)
            near = None; stalled = False; dropAt = None
            continue
        if stalled: continue
        before = [u for u in recent if u['t_ms'] < f['t_ms'] and f['t_ms'] - u['t_ms'] <= LOOK and u['rpm'] >= RUNNING]
        gnv = before and sum(1 for u in before if u['fuel'] in ('GNV', 'CUTOFF')) * 2 >= len(before)
        revved = max((u['rpm'] for u in recent if u['t_ms'] < f['t_ms']), default=0) >= KEYOFF_MAX_RPM
        if rpm < NEAR and near is None and dropAt is None and gnv and revved:
            last = [u for u in before if f['t_ms'] - u['t_ms'] <= 2000] or before
            near = dict(t=f['t_ms'], minRpm=rpm, pet=min(u['petrol_ms'] for u in last), cutoff=any(u['fuel'] == 'CUTOFF' for u in recent))
        elif near is not None: near['minRpm'] = min(near['minRpm'], rpm)
        if rpm >= DEAD: continue
        if dropAt is None:
            if not gnv or not revved: continue
            dropAt = f['t_ms']; ctx = before; continue
        if f['t_ms'] - dropAt < CONFIRM: continue
        stalled = True; dropAt = None; near = None
        stalls.append(dict(t=dropAt, rpm=ctx[-1]['rpm'], pet=ctx[-1]['petrol_ms']))
    return stalls, nears

por = collections.defaultdict(list)
for r in corpus.telemetria():
    if r['rpm'] is not None: por[r['sessao']].append(r)
S = N = 0
for s, T in por.items():
    T.sort(key=lambda r: r['t_ms'])
    a, b = replay(T); S += len(a); N += len(b)
print('apagões (rpm<300 por 800 ms, motor já girando >1000 nos últimos segundos):', S)
print('tropeços (rpm<500 e voltou, sem apagar):', N)
