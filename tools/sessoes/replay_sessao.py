#!/usr/bin/env python3
"""Replay de uma sessão gravada (dono, 2026-10-13: "a sessão tem que funcionar como um replay").
Uso: python3 -I tools/sessoes/replay_sessao.py <pasta com as partes extraídas OU com events_*.jsonl> [--min 5]
Lê todos os events_*.jsonl (sessão em ZIP único ou em partes), ordena pelo relógio do app e mostra, de 5 em 5 minutos:
combustível, RPM, quedas de rotação (afundamentos), apagões, gravações de K e o estado do piloto do Refino; depois uma tabela
por gravação de K (quantos pontos melhoraram/pioraram segundo o próprio app) e a cobertura da superfície (células RPM×MAP
estáveis acumuladas) por combustível. Só observação: nada vira regra."""
import bisect, collections, glob, json, os, statistics as st, sys

def carregar(pasta):
    ev = []
    for f in sorted(glob.glob(os.path.join(pasta, '**', 'events_*.jsonl*'), recursive=True)):
        for line in open(f, encoding='utf-8', errors='replace'):
            try: ev.append(json.loads(line))
            except ValueError: pass
    ev.sort(key=lambda e: e.get('recordedAtMs', 0))
    return ev

def quedas(tel):
    """Afundamento = RPM cai >= 20% abaixo da mediana dos 3 s anteriores e fica < 1100 (marcha lenta/transição)."""
    ts = [t[0] for t in tel]; out = []; ultimo = -1e9
    for i, (t, rpm, *_r) in enumerate(tel):
        j = bisect.bisect_left(ts, t - 3000)
        prev = [x[1] for x in tel[j:i]]
        if len(prev) >= 4 and rpm < 1100 and rpm < 0.8 * st.median(prev) and st.median(prev) > 700 and t - ultimo > 4000:
            out.append(t); ultimo = t
    return out

def main(pasta, janela):
    ev = carregar(pasta)
    if not ev: sys.exit('nenhum events_*.jsonl em ' + pasta)
    t0 = ev[0]['recordedAtMs']; minuto = lambda t: (t - t0) / 60000
    tel = [(e['recordedAtMs'], e['data'].get('rpm') or 0, e['data'].get('fuel'), e['data'].get('load_bar'), e['data'].get('petrol_ms'), e['data'].get('gas_ms_diagnostic') if e['data'].get('gas_pulse_present') else None)
           for e in ev if e['type'] == 'telemetry' and e['data'].get('rpm') is not None]
    escritas = [e for e in ev if e['type'] == 'k_factor_batch_confirmed']
    fases = [e for e in ev if e['type'] == 'refinement_phase']
    apagoes = [e for e in ev if e['type'] == 'engine_stall']
    acoes = [e for e in ev if e['type'] == 'autocal_native_action']
    dips = quedas(tel)
    print(f"Sessão: {minuto(ev[-1]['recordedAtMs']):.1f} min · {len(tel)} quadros de telemetria · {len(escritas)} gravações de K · {len(apagoes)} apagão(ões) · {len(dips)} afundamentos de RPM")
    print('\n== Linha do tempo (janelas de %d min) ==' % janela)
    print('janela  | GNV% gasol% corte% | rpm med | afund. | apagões | grav.K | fase final do Refino')
    n = int(minuto(ev[-1]['recordedAtMs']) // janela) + 1
    for k in range(n):
        a, b = t0 + k * janela * 60000, t0 + (k + 1) * janela * 60000
        w = [x for x in tel if a <= x[0] < b]
        if not w: continue
        c = collections.Counter(x[2] for x in w); tot = len(w)
        f = [p for p in fases if a <= p['recordedAtMs'] < b]
        print('%3d-%3d | %4.0f %5.0f %6.0f | %7.0f | %6d | %7d | %6d | %s' % (k * janela, (k + 1) * janela, 100 * c['GNV'] / tot, 100 * c['GASOLINA'] / tot, 100 * c['CUTOFF'] / tot,
              st.median([x[1] for x in w]), sum(a <= d < b for d in dips), sum(a <= e['recordedAtMs'] < b for e in apagoes), sum(a <= e['recordedAtMs'] < b for e in escritas),
              (f[-1]['data']['phase'] if f else '—')))
    print('\n== Gravações de K e o que o app mediu depois ==')
    for i, w in enumerate(escritas):
        nxt = escritas[i + 1]['recordedAtMs'] if i + 1 < len(escritas) else ev[-1]['recordedAtMs'] + 1
        res = [p['data']['result'] for p in fases if w['recordedAtMs'] < p['recordedAtMs'] <= nxt and p['data'].get('result') and (p['data']['result'].get('improved') or p['data']['result'].get('worse') or p['data']['result'].get('measuring'))]
        pts = w['data']['points']; ch = [(p['targetFactor'] / p['currentFactor'] - 1) * 100 for p in pts if p.get('currentFactor')]
        r = res[-1] if res else {}
        print('  %5.1f min · %2d pontos (%+.1f%%..%+.1f%%) · depois: melhorou %s, piorou %s, medindo %s' % (minuto(w['recordedAtMs']), len(pts), min(ch), max(ch), r.get('improved', '—'), r.get('worse', '—'), r.get('measuring', '—')))
    for a in acoes:
        print('  %5.1f min · ação da ECU: %s (%s)' % (minuto(a['recordedAtMs']), a['data'].get('label') or a['data'].get('action'), a['data'].get('outcome')))
    print('\n== Cobertura da superfície (células RPM×MAP com leitura estável de 3 quadros), acumulada ==')
    cel = {'GNV': set(), 'GASOLINA': set()}; marcos = {}
    for i in range(2, len(tel)):
        a, b, c = tel[i - 2], tel[i - 1], tel[i]
        if not (a[2] == b[2] == c[2] in cel) or c[0] - a[0] > 1500 or None in (a[3], b[3], c[3]): continue
        if max(a[1], b[1], c[1]) - min(a[1], b[1], c[1]) > 80 or max(a[3], b[3], c[3]) - min(a[3], b[3], c[3]) > 0.04: continue
        cel[c[2]].add((int(b[1] // 250), int(b[3] // 0.05)))
        marcos.setdefault(int(minuto(c[0]) // 15), {k: len(v) for k, v in cel.items()})
    for k in sorted(marcos): print('  até %3d min: GNV %3d células · gasolina %3d células' % ((k + 1) * 15, marcos[k]['GNV'], marcos[k]['GASOLINA']))
    print('  total: GNV %d células · gasolina %d células' % (len(cel['GNV']), len(cel['GASOLINA'])))

if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    janela = int(sys.argv[sys.argv.index('--min') + 1]) if '--min' in sys.argv else 5
    if not args: sys.exit(__doc__)
    main(args[0], janela)
