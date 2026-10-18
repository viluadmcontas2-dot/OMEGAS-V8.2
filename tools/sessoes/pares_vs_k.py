"""Os pares do Ledger (petrol_ms medido em GNV) seguem o K depois de uma gravação?  Só leitura do corpus.

Para cada `k_factor_batch_confirmed`, compara a mediana de petrol_ms em GNV por célula rpm×MAP (≥ 3 leituras de cada lado) nos
90 s antes e de 8 a 98 s depois, contra ln(K_novo/K_velho) na faixa de ms da célula. Se o Ledger pudesse re-escalar os pares
por K_novo/K_velho, a inclinação seria ≈ −1 (a ECU original compensa) ou ≈ +1; 0 = a grandeza não segue o K.
Uso: python3 -I tools/sessoes/pares_vs_k.py
"""
import collections, math, os, statistics as st, sys

sys.path.insert(0, os.path.dirname(__file__))
import corpus

AX = [0.5 * i for i in range(1, 21)] + [11, 12, 13, 14, 15, 16, 17, 18, 20, 22]


def log_ratio(points, ms):
    lr = [0.0] * 30
    for q in points:
        lr[q['index']] = math.log(q['targetRaw'] / q['currentRaw'])
    if ms <= AX[0]:
        return lr[0]
    for i in range(29):
        if AX[i] <= ms <= AX[i + 1]:
            return lr[i] + (lr[i + 1] - lr[i]) * (ms - AX[i]) / (AX[i + 1] - AX[i])
    return lr[29]


def main():
    events = sorted((e for e in corpus.eventos() if e['type'] == 'k_factor_batch_confirmed'), key=lambda e: (e['sessao'], e['recordedAtMs']))
    rows = collections.defaultdict(list)
    for r in corpus.telemetria():
        if r['t_ms'] is not None and r['petrol_ms'] is not None:
            rows[r['sessao']].append(r)
    by_session = collections.defaultdict(list)
    for e in events:
        by_session[e['sessao']].append(e)
    cells_out = []
    cell = lambda r: (int(r['rpm'] // 300), int(r['map_bar'] // 0.06))
    for session, batch in by_session.items():
        for k, e in enumerate(batch):
            t0 = e['recordedAtMs']
            prev_t = batch[k - 1]['recordedAtMs'] if k else 0
            next_t = batch[k + 1]['recordedAtMs'] if k + 1 < len(batch) else 10 ** 15
            points = e['data']['points']
            if not points:
                continue
            ok = lambda r: r['fuel'] == 'GNV' and r['map_bar'] < 0.7 and r['petrol_ms'] > 0.4
            before, after = collections.defaultdict(list), collections.defaultdict(list)
            for r in rows[session]:
                if ok(r) and max(prev_t + 5000, t0 - 90000) <= r['t_ms'] <= t0 - 2000:
                    before[cell(r)].append(r['petrol_ms'])
                elif ok(r) and t0 + 8000 <= r['t_ms'] <= min(next_t - 2000, t0 + 98000):
                    after[cell(r)].append(r['petrol_ms'])
            for c, b in before.items():
                a = after.get(c, [])
                if len(b) >= 3 and len(a) >= 3:
                    pb, pa = st.median(b), st.median(a)
                    cells_out.append((math.log(pa / pb), log_ratio(points, pb), len(b), len(a), session[:3], round(pb, 2)))
    affected = [x for x in cells_out if abs(x[1]) >= 0.05]
    control = [x for x in cells_out if abs(x[1]) < 0.01]
    slope = sum(d * lr for d, lr, *_ in affected) / sum(lr * lr for _, lr, *_ in affected) if affected else None
    print(f'células comparáveis: {len(cells_out)} · afetadas pelo K: {len(affected)} · controle sem mudança de K: {len(control)}')
    print(f'inclinação Δln(petrol_ms GNV)/ln(K_novo/K_velho) = {slope:.3f}  (−1 = compensa inverso ao K; 0 = não segue)')
    if control:
        print(f'controle: mediana {st.median(x[0] for x in control):+.3f}, desvio {st.pstdev([x[0] for x in control]):.3f}')
    for x in sorted(affected, key=lambda x: -abs(x[1]))[:8]:
        print('  Δln=%+.3f lnK=%+.3f n=%d/%d sessão %s ms≈%s' % x)


if __name__ == '__main__':
    main()
