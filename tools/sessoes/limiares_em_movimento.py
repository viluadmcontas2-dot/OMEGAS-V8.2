#!/usr/bin/env python3
"""Rotação máxima do anti-apagão a partir das quedas EM MOVIMENTO de todas as sessões reais (determinístico, só leitura).

Só entram quedas que começam de 1.200 rpm ou mais (carro engatado/andando; o dono confirmou que houve apagões em movimento;
motor morrendo em marcha lenta, ~870 rpm, fica de fora). Tempo = leituras x 0,25 s (relógio da central não é confiável).
Para cada Rotação máxima M e cada velocidade de queda R, conta quantos apagões a função teria a chance de ver e quantas quedas
comuns (trocas de marcha, desacelerações) também a acionariam por hora. NÃO simula o algoritmo da central; é triagem por evidência.
Uso: python3 tools/sessoes/limiares_em_movimento.py [--escrever]"""
import collections, csv, gzip, json, pathlib, statistics, sys

RAIZ = pathlib.Path(__file__).resolve().parents[2]
DT = 0.25
PICO_MIN = 1200
QUEDA_MIN = 400
MAXIMAS = (1400, 1600, 1800, 2000, 2200)
VELOCIDADES = (150, 250, 350, 450, 600)  # rpm/s entre duas leituras


def carregar(raiz=RAIZ):
    rows = collections.defaultdict(list)
    for r in csv.DictReader(gzip.open(raiz / 'tests/fixtures/sessoes-uteis/telemetria.csv.gz', 'rt')):
        try: rows[r['sessao']].append(int(r['rpm']))
        except Exception: pass
    return rows


def episodios(v):
    """Descidas que partem de um pico >= PICO_MIN e caem >= QUEDA_MIN em até 3 s. parou = fica < 350 rpm por 3+ leituras; quase = fundo < 500 e voltou."""
    n = len(v); i = 1; out = []
    while i < n - 1:
        if v[i] < v[i - 1] and v[i - 1] >= PICO_MIN:
            s = i - 1
            while s > 0 and v[s - 1] >= v[s] and i - s < 12: s -= 1
            e = i
            while e + 1 < n and v[e + 1] <= v[e] + 30 and e - s < 12: e += 1
            if v[s] - v[e] >= QUEDA_MIN:
                fundo = min(v[s:e + 1])
                parou = sum(1 for x in v[e:e + 12] if x < 350) >= 3
                out.append(dict(s=s, e=e, pico=v[s], fundo=fundo, parou=parou, quase=(not parou) and fundo < 500))
            i = e + 1
        else: i += 1
    return out


def aciona(v, ep, maxima, velocidade):
    """Primeira leitura da queda em que rpm <= maxima e a queda desde a leitura anterior é >= velocidade (rpm/s)."""
    for k in range(ep['s'] + 1, ep['e'] + 1):
        if v[k] <= maxima and (v[k - 1] - v[k]) >= velocidade * DT: return k
    return None


def analisar(raiz=RAIZ):
    rows = carregar(raiz)
    horas = sum(len(v) for v in rows.values()) * DT / 3600
    eps = [(s, e) for s, v in rows.items() for e in episodios(v)]
    parou = [(s, e) for s, e in eps if e['parou']]
    quase = [(s, e) for s, e in eps if e['quase']]
    comuns = [(s, e) for s, e in eps if not e['parou'] and not e['quase']]
    tabela = []
    for M in MAXIMAS:
        for R in VELOCIDADES:
            pegou = sum(1 for s, e in parou if aciona(rows[s], e, M, R) is not None)
            q = sum(1 for s, e in quase if aciona(rows[s], e, M, R) is not None)
            c = sum(1 for s, e in comuns if aciona(rows[s], e, M, R) is not None)
            tabela.append(dict(maxima=M, velocidade_rpm_s=R, apagoes_vistos=pegou, quase_apagoes_vistos=q, quedas_comuns_por_hora=round(c / horas, 1)))
    baixo700 = []
    for s, e in quase:
        v = rows[s]; k = next((j for j in range(e['s'], e['e'] + 1) if v[j] < 700), None)
        if k is None: continue
        j = k
        while j < len(v) - 1 and (v[j] < 700 or v[j + 1] < 700): j += 1
        baixo700.append((j - k) * DT)
    baixo700.sort()
    return dict(
        schema='limiares-em-movimento/1', horas=round(horas, 1), sessoes=len(rows), pico_minimo=PICO_MIN, queda_minima=QUEDA_MIN,
        apagoes=len(parou), quase_apagoes=len(quase), quedas_comuns=len(comuns),
        picos_dos_apagoes=sorted(e['pico'] for _, e in parou), tabela=tabela,
        tempo_abaixo_de_700_nos_quase_apagoes_s=dict(n=len(baixo700), p50=statistics.median(baixo700), p90=baixo700[int(.9 * (len(baixo700) - 1))], max=baixo700[-1]),
    )


def escolher(r):
    """Menor Rotação máxima que vê todos os apagões à velocidade mais baixa testada (o valor é do dado, não de um chute)."""
    n = r['apagoes']
    for M in MAXIMAS:
        linha = next(t for t in r['tabela'] if t['maxima'] == M and t['velocidade_rpm_s'] == VELOCIDADES[0])
        if linha['apagoes_vistos'] == n: return M
    return None


def markdown(r):
    M = escolher(r)
    base = {t['maxima']: t for t in r['tabela'] if t['velocidade_rpm_s'] == VELOCIDADES[0]}
    L = ['# Apagões em movimento: qual Rotação máxima o seu carro pede', '',
         'Gerado por `python3 tools/sessoes/limiares_em_movimento.py --escrever`. Evidência DERIVADA dos logs; nada é gravado na central.', '',
         f"Lidas {r['sessoes']} sessões ({r['horas']} h). Só contam quedas que partem de {r['pico_minimo']} rpm ou mais (em movimento); motor morrendo em marcha lenta ficou de fora.",
         f"Achados: **{r['apagoes']} apagões** (motor caiu abaixo de 350 rpm e ficou), {r['quase_apagoes']} quase-apagões e {r['quedas_comuns']} quedas comuns (troca de marcha, desaceleração).", '',
         '| Rotação máxima | Apagões que a função veria | Quase-apagões vistos | Quedas comuns que a acionariam (por hora) |', '|---|---|---|---|']
    for m in MAXIMAS:
        t = base[m]; L.append(f"| {m} | {t['apagoes_vistos']} de {r['apagoes']} | {t['quase_apagoes_vistos']} de {r['quase_apagoes']} | {t['quedas_comuns_por_hora']} |")
    L += ['', f"**Número sugerido para Rotação máxima: {M} rpm** — o menor valor que vê todos os apagões em movimento. Acima disso só aumenta o acionamento à toa.", '',
          f"Tempo que o motor ficou abaixo de 700 rpm nos quase-apagões: metade até {r['tempo_abaixo_de_700_nos_quase_apagoes_s']['p50']} s, 90% até {r['tempo_abaixo_de_700_nos_quase_apagoes_s']['p90']} s, o pior {r['tempo_abaixo_de_700_nos_quase_apagoes_s']['max']} s. É a medida que "
          'sustenta olhar a Espera para sair do gás (hoje 1500 ms); sem prova de efeito, **não** é sugestão de mudança.', '',
          '## Limites', '',
          '- Não há velocidade, embreagem nem chave no log: "em movimento" = a rotação vinha de 1.200 rpm ou mais; o dono confirmou que houve apagões em movimento.',
          '- Isto não simula a função da central: mostra o que passa pelo limiar, não que a função teria salvado o motor.',
          '- A velocidade de queda (rpm por leitura de 0,25 s) não vira o número do cartão "Queda de rotação": a unidade da central é desconhecida. Nenhuma sugestão para ela.',
          '- Os quatro ajustes extras continuam sem dado para sugerir mudança.']
    return '\n'.join(L) + '\n'


if __name__ == '__main__':
    r = analisar()
    r['rotacao_maxima_sugerida'] = escolher(r)
    if '--escrever' in sys.argv:
        d = RAIZ / 'docs/progbase/bins'
        (d / 'limiares-em-movimento.json').write_text(json.dumps(r, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')
        (d / 'limiares-em-movimento.md').write_text(markdown(r), encoding='utf-8')
    print(json.dumps({k: v for k, v in r.items() if k != 'tabela'}, ensure_ascii=False))
