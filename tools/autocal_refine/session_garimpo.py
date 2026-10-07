"""Garimpo de sessões OMEGAS (omegas-session-log-v1). Passivo. Saída: JSON para o painel."""
import json, sys, glob, math, statistics as st, collections, os

RB = [(600, 1100), (1100, 1500), (1500, 2000), (2000, 2600), (2600, 3200), (3200, 4500)]
RBN = ['lenta', '1100-1500', '1500-2000', '2000-2600', '2600-3200', '3200+']
MAPS = [round(0.15 + 0.05 * i, 2) for i in range(18)]
MUL_MAX = 24576


def rbi(r):
    for i, (lo, hi) in enumerate(RB):
        if lo < r <= hi:
            return i
    return None


def mi(m):
    i = round((m - 0.15) / 0.05)
    return i if 0 <= i < len(MAPS) else None


def bands(F, fuel):
    if fuel == 'GNV':
        m, t = F.get('MNFLD_PRESS_BUF_GAS'), F.get('PETR_INJ_TBUF_GAS')
    else:
        m, t = F.get('MNFLD_PRESS_BUF'), F.get('PETR_INJ_TBUF')
    if not m or not t:
        return []
    return [(i, m[i] / 1024, t[i] / 512) for i in range(min(len(m), len(t), 16)) if m[i] > 0 and t[i] > 0]


def outliers(pts, lim=0.06):
    out = []
    for k in range(1, len(pts) - 1):
        (_, m0, t0), (b, m, t), (_, m1, t1) = pts[k - 1], pts[k], pts[k + 1]
        if m1 - m0 < 1e-6:
            continue
        exp = t0 + (t1 - t0) * (m - m0) / (m1 - m0)
        dev = t / exp - 1
        if abs(dev) > lim:
            out.append(dict(band=b + 1, map=round(m, 3), ms=round(t, 2), expected=round(exp, 2), dev=round(100 * dev, 1)))
    return out


def run(paths):
    pet = collections.defaultdict(list)
    cell = collections.defaultdict(lambda: {'GNV': [], 'GASOLINA': []})
    sessions, stalls, verdicts, epochs, anomalies, regimes = [], [], [], [], [], collections.defaultdict(list)
    for p in paths:
        name = os.path.basename(p).replace('.jsonl', '').replace('events_0001', os.path.basename(os.path.dirname(p)))[8:27]
        fr, prev = [], None
        info = collections.Counter()
        last_snap = None
        seen_mat = set()
        try:
            fh = open(p, errors='replace')
        except OSError:
            continue
        for line in fh:
            try:
                e = json.loads(line)
            except Exception:
                continue
            t, d = e.get('type'), e.get('data') or {}
            info[t] += 1
            if t == 'telemetry':
                if not d.get('plausible') or d.get('fuel') not in ('GNV', 'GASOLINA'):
                    prev = None
                    continue
                f = (d['rpm'], d['load_bar'], d['petrol_ms'], d['fuel'], d.get('water_c') or 0)
                fr.append(f)
            elif t == 'engine_stall':
                stalls.append(dict(s=name, kind=d.get('kind'), rpm=d.get('rpmBefore'), ms=round(d.get('petrolMs') or 0, 2),
                                   map=d.get('mapBar'), fuel=d.get('fuelBefore'), decel=d.get('decelerating')))
            elif t == 'refinement_verdict':
                verdicts.append(dict(s=name, status=d.get('status'), before=d.get('ratioBefore'), after=d.get('ratioAfter'),
                                     bands=[(b.get('fromMs'), b.get('toMs'), b.get('verdict'), b.get('ratioBefore'), b.get('ratioAfter')) for b in d.get('bands') or []]))
            elif t in ('autocal_native_automatch_epoch',):
                ev = d.get('evidence') or {}
                b, a = ev.get('beforeRaw'), ev.get('afterRaw')
                if b and a and len(a) == len(b):
                    ch = [100 * (y / x - 1) for x, y in zip(b, a) if x]
                    epochs.append(dict(s=name, n=len(a), sat=sum(1 for y in a if y >= MUL_MAX), meanChg=round(st.mean(ch), 1),
                                       maxUp=round(max(ch), 1), maxDown=round(min(ch), 1), before=b, after=a))
            elif t == 'autocal_native_snapshot':
                F = {x['key']: x.get('rawValues') for x in d.get('fields') or [] if x.get('status') == 'VALID'}
                if F.get('PETR_INJ_TBUF_GAS'):
                    last_snap = F
                for ev in d.get('nativeMaturityEvents') or []:
                    k = (ev.get('fuel'), ev.get('bandIndex'), ev.get('rpm'), ev.get('correlatedMapBar'))
                    if ev.get('rpm') and k not in seen_mat:
                        seen_mat.add(k)
                        regimes[(ev.get('fuel'), ev.get('bandIndex'))].append(ev['rpm'])
        if last_snap:
            for fuel in ('GNV', 'GASOLINA'):
                for o in outliers(bands(last_snap, fuel)):
                    anomalies.append(dict(s=name, fuel=fuel, **o))
        for a, b, c in zip(fr, fr[1:], fr[2:]):
            if not (a[3] == b[3] == c[3]) or b[4] < 45 or b[2] <= 0.7:
                continue
            if max(a[0], b[0], c[0]) - min(a[0], b[0], c[0]) > 150:
                continue
            r, m = rbi(b[0]), mi(b[1])
            if r is None or m is None:
                continue
            cell[(r, m)][b[3]].append(b[2])
            if b[3] == 'GASOLINA':
                pet[(r, m)].append(b[2])
        fuels = collections.Counter(f[3] for f in fr)
        sessions.append(dict(s=name, frames=len(fr), gnv=fuels['GNV'], gas=fuels['GASOLINA'],
                             idle=sum(1 for f in fr if f[0] < 1100), kwrites=info['k_factor_batch_confirmed'] + info['k_batch_confirmed'],
                             automatch=info['autocal_native_automatch_epoch'], stalls=info['engine_stall']))
    ve = [[round(st.median(pet[(r, m)]), 3) if len(pet[(r, m)]) >= 8 else None for r in range(len(RB))] for m in range(len(MAPS))]
    ratio = []
    for m in range(len(MAPS)):
        row = []
        for r in range(len(RB)):
            v = cell.get((r, m))
            if v and len(v['GNV']) >= 8 and len(v['GASOLINA']) >= 8:
                row.append([round(st.median(v['GNV']) / st.median(v['GASOLINA']), 3), min(len(v['GNV']), len(v['GASOLINA']))])
            else:
                row.append(None)
        ratio.append(row)
    reg = {f'{k[0]}:{k[1] + 1}': dict(med=int(st.median(v)), lo=min(v), hi=max(v), n=len(v),
                                      idleShare=round(sum(1 for x in v if x < 1100) / len(v), 2)) for k, v in regimes.items() if k[1] is not None}
    return dict(rpmBands=RBN, maps=MAPS, ve=ve, ratio=ratio, sessions=sessions, stalls=stalls, verdicts=verdicts,
                epochs=epochs, anomalies=anomalies, regimes=reg)


if __name__ == '__main__':
    paths = []
    for root in sys.argv[2:]:
        paths += glob.glob(root + '/*.jsonl') + glob.glob(root + '/*/x/*/events_0001.jsonl')
    out = run(sorted(set(paths)))
    json.dump(out, open(sys.argv[1], 'w'))
    print('sessões', len(out['sessions']), 'quadros', sum(s['frames'] for s in out['sessions']), 'AutoMatch', len(out['epochs']),
          'veredictos', len(out['verdicts']), 'quase/apagões', len(out['stalls']), 'anomalias de curva', len(out['anomalies']))
