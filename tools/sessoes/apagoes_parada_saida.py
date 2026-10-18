"""Apagão na PARADA (volta à marcha lenta / embreagem) e na SAÍDA do lugar: modo, rpm, MAP, injeção, posição na transição.

Só lê gravações antigas; nada é escrito na ECU. NENHUMA decisão por relógio: janelas e posições contam AMOSTRAS (ordem), `t_ms` não é usado.
Uso: python3 -I tools/sessoes/apagoes_parada_saida.py [--extra events_0001.jsonl]
Definições (todas por ordem de amostras):
- APAGAO: rpm < 350 com alguma das 10 amostras anteriores >= 650 (e a anterior >= 350). Quadros repetidos contam como amostras.
- Família de combustível: byte 0x90/0xB0 = GNV; 0x80/0xA0 = GASOLINA; 0x88/0xA8 = TRANSICAO; 0x94/0x95/0xB4 = CORTE; resto = OUTRO.
- RETORNO_A_LENTA (parada/embreagem): rpm cruza de >950 para <=950 tendo ≥1250 nas 6 amostras anteriores e MAP <= 0,60 em todas.
- SAIDA_DO_LUGAR: 8 amostras de lenta estável (780–960 rpm, amplitude < 80) seguidas, nas 14 seguintes, de MAP > 0,60 (acelerador).
"""
import os
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

GNV = (0x90, 0xB0)
PET = (0x80, 0xA0)
TRANS = (0x88, 0xA8)
CORTE = (0x94, 0x95, 0xB4)
IDLE_LO, IDLE_HI, IDLE_RANGE, IDLE_N = 780, 960, 80, 8
RTI_FROM, RTI_TO, RTI_BACK, RTI_MAP = 1250, 950, 6, 0.60
LAUNCH_MAP, LAUNCH_AFTER = 0.60, 14
STALL_TO, STALL_FROM, LOOK = 350, 650, 10
FOLLOW = 12


def familia(b):
    if b is None:
        return 'OUTRO'
    b = int(b)
    if b in GNV:
        return 'GNV'
    if b in PET:
        return 'GASOLINA'
    if b in TRANS:
        return 'TRANSICAO'
    if b in CORTE:
        return 'CORTE'
    if b == 0:
        return 'DESLIGADO'
    return 'OUTRO'


def sessoes(rows):
    d = defaultdict(list)
    for r in rows:
        d[r['sessao']].append(r)
    return d


def _v(r, k):
    x = r.get(k)
    return None if x is None else float(x)


def apagoes(s):
    out, i = [], LOOK
    while i < len(s):
        rpm = _v(s[i], 'rpm')
        if rpm is not None and rpm < STALL_TO:
            prev = [_v(x, 'rpm') for x in s[i - LOOK:i] if _v(x, 'rpm') is not None]
            if prev and max(prev) >= STALL_FROM and prev[-1] >= STALL_TO:
                out.append(i)
                j = i
                while j < len(s) and (_v(s[j], 'rpm') is None or _v(s[j], 'rpm') < 500):
                    j += 1
                i = j + 1
                continue
        i += 1
    return out


def modo_antes(s, i):
    """Família do último quadro GNV/GASOLINA/TRANSICAO antes de i (ignora corte e os bytes de agonia 0x08/0x09/0x0C; só ordem)."""
    for k in range(i - 1, max(i - 40, -1), -1):
        f = familia(s[k].get('fuel_byte'))
        if f in ('GNV', 'GASOLINA', 'TRANSICAO'):
            return f
    return 'OUTRO'


def amostras_desde(s, i, pred, maximo=400):
    for n in range(0, min(i, maximo)):
        if pred(s[i - 1 - n]):
            return n
    return None


def retornos(s):
    out, i = [], RTI_BACK
    while i < len(s) - 1:
        rpm = _v(s[i], 'rpm')
        prev = _v(s[i - 1], 'rpm')
        if rpm is not None and prev is not None and prev > RTI_TO >= rpm >= 600:
            back = [_v(x, 'rpm') for x in s[i - RTI_BACK:i]]
            maps = [_v(x, 'map_bar') for x in s[i - RTI_BACK:i + 1]]
            if all(b is not None for b in back) and max(back) >= RTI_FROM and all(m is not None and m <= RTI_MAP for m in maps):
                out.append(i)
                i += FOLLOW
                continue
        i += 1
    return out


def saidas(s):
    out, i = [], IDLE_N
    while i < len(s) - LAUNCH_AFTER:
        w = [_v(x, 'rpm') for x in s[i - IDLE_N:i]]
        if all(r is not None and IDLE_LO <= r <= IDLE_HI for r in w) and max(w) - min(w) < IDLE_RANGE:
            fut = [_v(x, 'map_bar') for x in s[i:i + LAUNCH_AFTER]]
            if any(m is not None and m > LAUNCH_MAP for m in fut):
                out.append(i)
                i += LAUNCH_AFTER + IDLE_N
                continue
        i += 1
    return out


def arrastos(s):
    """ARRASTO (saída do lugar com a embreagem solta): rpm cai de >=1000 a <=700 enquanto o MAP sobe de <=0,45 a >=0,50."""
    out, i = [], 6
    while i < len(s) - 1:
        rpm, m = _v(s[i], 'rpm'), _v(s[i], 'map_bar')
        back = [_v(x, 'rpm') for x in s[i - 6:i]]
        m0 = _v(s[i - 6], 'map_bar')
        if (rpm is not None and m is not None and m0 is not None and rpm <= 700 and rpm >= 350 and m >= 0.50 and m0 <= 0.45
                and all(b is not None for b in back) and max(back) >= 1000):
            out.append(i)
            i += FOLLOW
            continue
        i += 1
    return out


def houve_apagao(s, i, n=FOLLOW + 8):
    return any(_v(x, 'rpm') is not None and _v(x, 'rpm') < STALL_TO for x in s[i:i + n])


def min_rpm(s, i, n=FOLLOW + 8):
    v = [_v(x, 'rpm') for x in s[i:i + n] if _v(x, 'rpm') is not None]
    return min(v) if v else None


def taxa(titulo, casos):
    print('\n### ' + titulo)
    print('| modo | casos | apagou | taxa | quase (rpm min < 600) |')
    print('|---|---|---|---|---|')
    tot = Counter()
    for modo in ('GNV', 'TRANSICAO', 'GASOLINA', 'OUTRO'):
        c = [x for x in casos if x[0] == modo]
        if not c:
            continue
        a = sum(1 for x in c if x[1])
        q = sum(1 for x in c if x[2] is not None and x[2] < 600)
        print('| %s | %d | %d | %.1f%% | %d |' % (modo, len(c), a, 100.0 * a / len(c), q))
    return tot


def main(extra):
    import corpus
    import apagoes_x_antistallo as A
    rows = list(corpus.telemetria())
    stalls = []
    if extra:
        er, stalls = A.load_extra_rows(extra)
        rows += er
    ss = sessoes(rows)
    print('# Apagão na parada e na saída (amostras: %d, sessões: %d)' % (len(rows), len(ss)))

    ev = []
    for k, s in ss.items():
        for i in apagoes(s):
            ev.append((k, s, i))
    print('\n## Apagões/quase-apagões (rpm >=650 -> <350): %d' % len(ev))
    classes = Counter()
    for k, s, i in ev:
        w = [_v(x, 'rpm') for x in s[i - LOOK:i]]
        w = [x for x in w if x is not None]
        estavel = max(w[:-3]) - min(w[:-3]) < 120 and max(w[:-3]) <= 960 if len(w) > 4 else False
        maps = [_v(x, 'map_bar') for x in s[i - LOOK:i + 1]]
        maps = [m for m in maps if m is not None]
        modo = modo_antes(s, i)
        if estavel:
            cls = 'LENTA_QUE_MORREU (rpm ~lenta estável, depois zera)'
        elif max(w) >= 1100 and maps and maps[-2] > 0.6 if len(maps) > 2 else False:
            cls = 'RPM_CAINDO_COM_CARGA (MAP sobe: saída do lugar/arrastando)'
        elif max(w) >= 1100:
            cls = 'RPM_CAINDO_SEM_CARGA (volta à lenta/embreagem)'
        else:
            cls = 'OUTRO'
        classes[(cls, modo)] += 1
        desde_corte = amostras_desde(s, i, lambda r: familia(r.get('fuel_byte')) == 'CORTE')
        desde_gnv = None
        for n in range(1, min(i, 400)):
            if familia(s[i - n].get('fuel_byte')) != 'GNV' and familia(s[i - n + 1].get('fuel_byte')) == 'GNV':
                desde_gnv = n - 1
                break
        print('- %s #%d modo=%s classe=%s | amostras desde corte=%s, desde entrada no GNV=%s' % (k[:14], i, modo, cls, desde_corte, desde_gnv))
        print('    rpm  ' + ' '.join('—' if _v(x, 'rpm') is None else '%d' % _v(x, 'rpm') for x in s[i - LOOK:i + 2]))
        print('    byte ' + ' '.join('--' if x.get('fuel_byte') is None else '%02X' % int(x['fuel_byte']) for x in s[i - LOOK:i + 2]))
        print('    MAP  ' + ' '.join('—' if _v(x, 'map_bar') is None else '%.2f' % _v(x, 'map_bar') for x in s[i - LOOK:i + 2]))
        print('    gás  ' + ' '.join('—' if _v(x, 'gas_ms') is None else '%.1f' % _v(x, 'gas_ms') for x in s[i - LOOK:i + 2]))
        print('    gaso ' + ' '.join('—' if _v(x, 'petrol_ms') is None else '%.1f' % _v(x, 'petrol_ms') for x in s[i - LOOK:i + 2]))
    print('\n### Classes x modo')
    for (c, m), n in sorted(classes.items()):
        print('- %s | %s: %d' % (c, m, n))

    rti, sai, arr = [], [], []
    for k, s in ss.items():
        for i in retornos(s):
            rti.append((modo_antes(s, i), houve_apagao(s, i), min_rpm(s, i)))
        for i in arrastos(s):
            arr.append((modo_antes(s, i), houve_apagao(s, i, 8), min_rpm(s, i, 8)))
        for i in saidas(s):
            sai.append((modo_antes(s, i), houve_apagao(s, i), min_rpm(s, i)))
    taxa('RETORNO À LENTA (rpm >=1250 -> <=950, MAP <= 0,60): taxa de apagão nas %d amostras seguintes' % (FOLLOW + 8), rti)
    taxa('SAÍDA DO LUGAR (lenta estável e depois MAP > 0,60): taxa de apagão', sai)
    taxa('ARRASTO (rpm cai de >=1000 a <=700 com MAP subindo de <=0,45 a >=0,50 = embreagem solta sob carga): taxa de apagão nas 8 amostras seguintes', arr)
    if stalls:
        print('\n### engine_stall registrados pelo app na sessão extra')
        for s_ in stalls:
            print('- ' + str(s_))


if __name__ == '__main__':
    extra = None
    a = sys.argv[1:]
    if a[:1] == ['--extra']:
        extra = a[1]
    main(extra)
