#!/usr/bin/env python3
"""Sugestão dos 5 números do anti-apagão PARA O CARRO DO DONO, calculada dos logs reais (determinístico, sem relógio: conta amostras).
Uso: python3 tools/sessoes/sugerir_antiapagao.py <omegasaudit.LOG> [saida.json]
Fontes: log novo (11 liga/desliga do dono, 14.932 amostras) + apagão real de 2026-10-13 (tests/fixtures/sessoes-uteis/apagao-2026-10-13-1613.json).
Regra (OBS-FIO): enquanto os valores lidos na central (FLAG ANTI_STALLO = 1600 100 1500 2 20) deixaram a rotação pós-queda >= 80% da marcha lenta
com a função LIGADA, a sugestão é MANTER esses valores; só a Rotação máxima tem faixa de ajuste derivada dos dados (a maior rotação em que uma queda começou).
Os 4 extras NÃO têm dado para sugerir além do valor em uso (efeito não confirmado): ficam como estão."""
import json, re, sys, statistics, pathlib
JANELA = 40  # amostras
def amostras(path):
    ev = []; lido = set()
    for l in open(path, errors='replace'):
        t = l.split(None, 1)
        if not t: continue
        if 'IRP_MJ_READ' in l: lido.add(t[0]); continue
        m = re.search(r'Length (\d+): ((?:[0-9A-F]{2} ?)+)', l)
        if not m: continue
        if 'IRP_MJ_WRITE' in l: ev.append(('W', bytes.fromhex(m.group(2).replace(' ', ''))))
        elif t[0] in lido: ev.append(('R', bytes.fromhex(m.group(2).replace(' ', '')))); lido.discard(t[0])
    S, M, cur, buf = [], [], None, bytearray()
    def flush():
        b = bytes(buf); i = b.find(b'\x53\x22')
        if i >= 0 and len(b) >= i + 36:
            p = b[i + 2:i + 36]; S.append(dict(rpm=p[0] | (p[1] << 8), map=p[17] | (p[18] << 8), fuel=p[11]))
    for k, d in ev:
        if k == 'W':
            if cur == 'tel': flush()
            buf.clear(); cur = None
            if d == b'\x48\x01\x49': cur = 'tel'
            elif d[:3] == b'\x35\xfa\x00' and len(d) == 8: M.append((len(S), 'LIGADO' if d[3] & 0x20 else 'DESLIGADO'))
        elif cur == 'tel': buf.extend(d)
    if cur == 'tel': flush()
    return S, M
def gasolina_por_estado(S, est):
    """Quanto do tempo o carro ficou FORA do GNV (byte 144 = GNV) e quantas trocas GNV->outro houve, com a função ligada x desligada (conta leituras)."""
    ag = {}
    for i in range(1, len(S)):
        e = est(i)
        if e is None: continue
        a = ag.setdefault(e, dict(leituras=0, fora_do_gnv=0, trocas=0)); a['leituras'] += 1
        if S[i]['fuel'] != 144: a['fora_do_gnv'] += 1
        if S[i - 1]['fuel'] == 144 and S[i]['fuel'] != 144 and S[i]['rpm'] > 0: a['trocas'] += 1
    for a in ag.values(): a['pct_fora_do_gnv'] = round(100.0 * a['fora_do_gnv'] / a['leituras'], 1)
    return ag

def corpus_quedas(raiz=None):
    """Quedas bruscas (>= 350 rpm/s em 2 leituras) das 93 sessões reais do corpus: quantas por hora cruzam cada Rotação máxima e quantas terminam em
    quase-apagão (rpm < 350 nas 24 leituras seguintes). Tempo = leituras x 0,25 s (o relógio da central é desacertado em parte das sessões)."""
    import gzip, csv, collections
    raiz = raiz or pathlib.Path(__file__).resolve().parents[2]
    rows = collections.defaultdict(list)
    for r in csv.DictReader(gzip.open(raiz / 'tests/fixtures/sessoes-uteis/telemetria.csv.gz', 'rt')):
        try: rows[r['sessao']].append((int(r['t_ms']), int(r['rpm'])))
        except Exception: pass
    LIM = 350; Ms = list(range(1200, 2201, 200))
    ativ = {M: 0 for M in Ms}; fim = {M: 0 for M in Ms}; horas = 0.0
    for ses in sorted(rows):
        v = sorted(rows[ses]); n = len(v); horas += n * 0.25 / 3600
        for i in range(2, n - 24):
            dt = (v[i][0] - v[i - 2][0]) / 1000
            if dt <= 0 or dt > 2 or v[i][1] <= 0 or (v[i - 2][1] - v[i][1]) / dt < LIM: continue
            for M in Ms:
                if v[i - 2][1] >= M > v[i][1]:
                    ativ[M] += 1
                    if min(x[1] for x in v[i:i + 24]) < 350: fim[M] += 1
    return dict(horas=round(horas, 1), por_rotacao_maxima={str(M): dict(ativacoes_por_hora=round(ativ[M] / horas, 1), terminaram_abaixo_de_350=fim[M]) for M in Ms})

def calcular(path):
    S, M = amostras(path)
    est = lambda i: next((t for p, t in reversed(M) if p <= i), None)
    marcha = [s['rpm'] for s in S if 600 <= s['rpm'] <= 1100]
    idle = int(statistics.median(marcha)) if marcha else None
    eventos = []; i = JANELA
    while i < len(S):
        r = S[i]['rpm']
        if 0 < r < 800 and max(s['rpm'] for s in S[i - JANELA:i]) >= 1100:
            j = max(range(i - JANELA, i), key=lambda k: S[k]['rpm'])
            eventos.append(dict(amostra=i, estado=est(i), rpm_inicio_da_queda=S[j]['rpm'], rpm_minimo=min(s['rpm'] for s in S[i:i + JANELA])))
            i += 60
        else: i += 1
    por = {}
    for e in eventos:
        d = por.setdefault(e['estado'], dict(eventos=0, pior_minimo=9999)); d['eventos'] += 1; d['pior_minimo'] = min(d['pior_minimo'], e['rpm_minimo'])
    maior_inicio = max(e['rpm_inicio_da_queda'] for e in eventos)
    apagao = json.load(open(pathlib.Path(__file__).resolve().parents[2] / 'tests/fixtures/sessoes-uteis/apagao-2026-10-13-1613.json'))
    rpms = [q['rpm'] for q in apagao['quadros'] if q.get('rpm')]
    ligado = por.get('LIGADO', {}); deslig = por.get('DESLIGADO', {})
    gas = gasolina_por_estado(S, est); corpus = corpus_quedas()
    return dict(
        fonte='omegasaudit.LOG (log novo do dono) + apagão real 2026-10-13',
        marcha_lenta_tipica_rpm=idle, amostras=len(S), eventos=eventos, por_estado=por,
        apagao_real_13_10=dict(rpm_antes=apagao['engine_stall_registrado']['rpmBefore'], rpm_maximo_na_janela=max(rpms), funcao='desligada'),
        sugerido=dict(valores=[1600, 100, 1500, 2, 20], motivo='manter: com estes valores e a função ligada a rotação pós-queda nunca passou abaixo de %s rpm (marcha lenta típica %s)' % (ligado.get('pior_minimo'), idle)),
        faixa_rotacao_maxima=dict(minimo=1600, maximo=int(-(-maior_inicio // 100) * 100), porque='a maior rotação em que uma queda do seu carro começou foi %d rpm' % maior_inicio),
        gasolina=gas, corpus_quedas=corpus,
        resultado=dict(ligado_pior_minimo=ligado.get('pior_minimo'), desligado_pior_minimo=deslig.get('pior_minimo'), ligado_eventos=ligado.get('eventos'), desligado_eventos=deslig.get('eventos')),
    )
if __name__ == '__main__':
    out = calcular(sys.argv[1])
    texto = json.dumps(out, indent=1, ensure_ascii=False)
    if len(sys.argv) > 2: pathlib.Path(sys.argv[2]).write_text(texto + '\n', encoding='utf-8')
    print(texto)
