#!/usr/bin/env python3
"""Anti-apagão no log novo (omegasaudit.LOG): compara telemetria com a função ligada x desligada.
Uso: python3 tools/sessoes/log_novo_antistallo.py <omegasaudit.LOG>. Determinístico; só leitura; sem relógio (conta amostras)."""
import re, sys, json, collections
def u16(b, i): return b[i] | (b[i + 1] << 8)
def main(path):
    ev = []  # ('W', bytes) | ('R', bytes) na ordem do arquivo
    lido = set()
    for l in open(path, errors='replace'):
        t = l.split(None, 1)
        if not t: continue
        if 'IRP_MJ_READ' in l: lido.add(t[0]); continue
        m = re.search(r'Length (\d+): ((?:[0-9A-F]{2} ?)+)', l)
        if not m: continue
        if 'IRP_MJ_WRITE' in l: ev.append(('W', bytes.fromhex(m.group(2).replace(' ', ''))))
        elif t[0] in lido: ev.append(('R', bytes.fromhex(m.group(2).replace(' ', '')))); lido.discard(t[0])
    samples = []; marks = []; cur = None; buf = bytearray()
    def flush():
        # resposta: 48 01 49 | 53 LL payload... ck
        b = bytes(buf)
        i = b.find(b'\x53\x22')
        if i >= 0 and len(b) >= i + 2 + 34:
            p = b[i + 2:i + 2 + 34]
            samples.append(dict(rpm=u16(p, 0), gas=u16(p, 6), petrol=u16(p, 8), fuel=p[11], map=u16(p, 17)))
    for k, d in ev:
        if k == 'W':
            if cur == 'tel': flush()
            buf.clear(); cur = None
            if d == b'\x48\x01\x49': cur = 'tel'
            elif d[:3] == b'\x35\xfa\x00' and len(d) == 8:
                marks.append((len(samples), 'LIGA' if d[3] & 0x20 else 'DESLIGA'))
        elif cur == 'tel': buf.extend(d)
    if cur == 'tel': flush()
    # fases
    fases = []; ini = 0; estado = None
    for pos, t in marks:
        fases.append((estado, ini, pos)); ini, estado = pos, t
    fases.append((estado, ini, len(samples)))
    out = collections.OrderedDict(amostras=len(samples), eventos=marks)
    resumo = collections.defaultdict(lambda: dict(n=0, quedas=0, quase=0, apagou=0, troca_p_gasolina=0, rpm_min_pos_corte=9999, afundou_600=0))
    for est, a, b in fases:
        if est is None: continue
        r = resumo[est]; seg = samples[a:b]; r['n'] += len(seg)
        for i in range(1, len(seg)):
            x, y = seg[i - 1], seg[i]
            if x['rpm'] >= 1000 and y['rpm'] < 700: r['quedas'] += 1      # queda de rotação
            if x['rpm'] >= 700 and y['rpm'] < 450: r['quase'] += 1         # quase apagou
            if x['rpm'] >= 450 and y['rpm'] < 100: r['apagou'] += 1        # apagou
            if x['rpm'] >= 1200:                                           # pós-corte: mínimo nas 40 amostras seguintes
                m = min(q['rpm'] for q in seg[i:i + 40]); r['rpm_min_pos_corte'] = min(r['rpm_min_pos_corte'], m)
                if m < 600: r['afundou_600'] += 1
            if x['fuel'] != y['fuel'] and y['gas'] == 0 and y['rpm'] > 0: r['troca_p_gasolina'] += 1
    out['por_estado'] = resumo
    print(json.dumps(out, indent=1, default=str))
if __name__ == '__main__': main(sys.argv[1])
