#!/usr/bin/env python3
"""Referência histórica descritiva; nunca produz comando de escrita."""
import argparse
from collections import defaultdict
import hashlib
import json
import math
import re
from pathlib import Path
from statistics import median
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from tools.sessoes import corpus

ORIGIN = 'docs/evidence/2026-10-18-quadro-gasolina-gnv-k.md'
JSON_PATH = ROOT / 'app/src/main/assets/ui/data/gasolina-referencia.json'
DOC_PATH = ROOT / ORIGIN
EXTRA = Path('/tmp/claude-0/-home-user/57e20309-4640-5e31-b02d-4b2faf9cf2b3/scratchpad/s1838/events_0001.jsonl')


def positive(v):
    return isinstance(v, (int, float)) and math.isfinite(v) and v > 0


def aggregate(rows):
    buckets = defaultdict(lambda: {'p': [], 'g': [], 'q': [], 'ps': set(), 'gs': set()})
    for r in rows:
        rpm, pressure, p = r.get('rpm'), r.get('map_bar'), r.get('petrol_ms')
        if not (positive(rpm) and rpm >= 700 and positive(pressure) and positive(p)):
            continue
        b = buckets[(math.floor(pressure * 10), math.floor(rpm / 500))]
        gnv = r.get('state') == 'GNV_ATIVO'
        b['g' if gnv else 'p'].append(p)
        b['gs' if gnv else 'ps'].add(r['sessao'])
        if gnv and positive(r.get('gas_ms')):
            b['q'].append(r['gas_ms'] / p)
    out = []
    def med(values):
        return median(values) if len(values) >= 30 else None
    for (mp, rpm), b in sorted(buckets.items()):
        p, g = med(b['p']), med(b['g'])
        out.append(dict(mapMin=mp / 10, mapMax=(mp + 1) / 10, rpmMin=rpm * 500, rpmMax=(rpm + 1) * 500,
                        petrolMs=p, n=len(b['p']), gnvPetrolMs=g, gnvN=len(b['g']),
                        ratio=g / p if p and g else None, gasPetrolRatio=med(b['q']), gasPetrolN=len(b['q']),
                        petrolSessions=sorted(b['ps']), gnvSessions=sorted(b['gs'])))
    return out


def raw30(field):
    if not field:
        return None
    # Bytes preservados mesmo quando o decodificador antigo esperava 18 elementos.
    payload = field.get('rawPayloadHex') or ''
    try:
        data = bytes.fromhex(payload)
    except ValueError:
        return None
    if len(data) != 60:
        return None
    return [int.from_bytes(data[i:i+2], 'little') for i in range(0, 60, 2)]


def curves(events, sessions):
    last = {}
    for event in events:
        s = event.get('sessao')
        if event.get('type') == 'autocal_native_snapshot' and s in sessions:
            if s not in last or event.get('sequence', -1) > last[s].get('sequence', -1):
                last[s] = event
    out = []
    for s in sorted(sessions):
        event = last.get(s, {})
        fields = {f['key']: f for f in event.get('data', {}).get('fields', [])}
        k, axis = raw30(fields.get('MUL_ACT')), raw30(fields.get('PETR_INJ_TBP'))
        out.append(dict(sessao=s, sequence=event.get('sequence'), recordedAtMs=event.get('recordedAtMs'),
                        snapshotHash=event.get('data', {}).get('snapshotHash'),
                        mulActQ14=k, k=[v / 16384 for v in k] if k else None,
                        petrolAxisRaw=axis, petrolAxisMs=[v / 512 for v in axis] if axis else None,
                        fieldStatus={key: fields.get(key, {}).get('status') for key in ('MUL_ACT', 'PETR_INJ_TBP')}))
    return out


def encode(obj):
    return json.dumps(obj, ensure_ascii=False, indent=2, allow_nan=False) + '\n'


def display(v):
    return '—' if v is None else f'{v:.6f}'.rstrip('0').rstrip('.')


def table(cells):
    lines = ['| MAP bar [início,fim) | RPM [início,fim) | Gasolina ms | n | Equivalente GNV ms | n GNV | Razão GNV/gasolina | gas_ms/petrol_ms | n pares | sessões gasolina / GNV |',
             '|---|---|---:|---:|---:|---:|---:|---:|---:|---|']
    for c in cells:
        lines.append('| ' + ' | '.join([f"{c['mapMin']}–{c['mapMax']}", f"{c['rpmMin']}–{c['rpmMax']}",
            *[display(c[k]) for k in ('petrolMs', 'n', 'gnvPetrolMs', 'gnvN', 'ratio', 'gasPetrolRatio', 'gasPetrolN')],
            ', '.join(c['petrolSessions']) + ' / ' + ', '.join(c['gnvSessions'])]) + ' |')
    return '\n'.join(lines)


def generate():
    cells = aggregate(corpus.telemetria())
    sessions = sorted({s for c in cells for key in ('petrolSessions', 'gnvSessions') for s in c[key]})
    sources = {name: hashlib.sha256((Path(corpus.BASE) / name).read_bytes()).hexdigest()
               for name in ('telemetria.csv.gz', 'eventos-calibracao.jsonl.gz', 'drive-ids.json')}
    data = dict(schema=1, historical=True, readOnly=True, fonte=ORIGIN, minSamples=30,
                bins=dict(mapBar=.1, rpm=500, interval='[min,max)'), sources=sources,
                sessions=sessions, cells=cells, curves=curves(corpus.eventos(), sessions))
    doc = '''# Gasolina × GNV × K — referência histórica, somente consulta

Gerado por `python3 -B tools/sessoes/gerar_referencia_gasolina.py`. Fonte única do JSON:
`tests/fixtures/sessoes-uteis`, lida por `tools/sessoes/corpus.py`.
O mesmo comando embute no módulo JS apenas as células consultáveis do JSON, pois o WebView
file:// bloqueia fetch local. Não há segunda fonte nem acesso de rede.
IDs completos das sessões estão em cada célula e abaixo; os IDs dos arquivos originais do Drive
estão em `tests/fixtures/sessoes-uteis/drive-ids.json`. Hashes dos arquivos de entrada:

'''
    doc += '\n'.join(f'- `{k}`: `{v}`' for k, v in sources.items())
    doc += '''

## Como ler e reproduzir os números

Célula = floor(MAP × 10)/10 bar × floor(RPM/500)×500 RPM, intervalo fechado à esquerda e aberto à direita.
Só entram RPM ≥ 700, MAP e petrol_ms finitos e positivos. Gasolina segue o filtro pedido:
state != GNV_ATIVO (inclui outros estados; não prova combustão em gasolina). GNV exige state == GNV_ATIVO.
Gasolina ms = mediana de petrol_ms em gasolina; equivalente GNV ms = mediana do MESMO canal petrol_ms
com GNV ativo, não o tempo do injetor de gás. n é a quantidade de leituras válidas, não viagens independentes.
Razão = mediana(petrol_ms em GNV) / mediana(petrol_ms em gasolina); diferença percentual = 100×(razão−1).
gas_ms/petrol_ms é a mediana das razões por leitura GNV, só com gas_ms positivo; n pares é seu denominador.
Cada mediana exige 30 leituras do seu grupo; a razão exige os dois grupos. Abaixo disso: — (null no JSON),
sem preencher com zero, interpolar ou extrapolar. As listas de sessões de cada grupo dão a origem dos números.

K vem do último autocal_native_snapshot por sequence dentro de cada sessão, nunca de escrita confirmada
nem de snapshot anterior quando o último está incompleto. MUL_ACT e PETR_INJ_TBP: 30 uint16 little-endian
nos rawPayloadHex; K = MUL_ACT/16384 (Q14); eixo ms = PETR_INJ_TBP/512.
Alguns logs antigos dizem INVALID porque esperavam 18 elementos: preservamos esse status e decodificamos
somente payloads completos de 60 bytes. Isso é recuperação histórica, não validação atual da ECU.
Ausência de snapshot ou vetor completo = —. A curva final não necessariamente era a curva de cada amostra.

## O que NÃO está provado

Não é mapa de escrita, proposta de ajuste, equivalência causal, mistura/lambda, nem garantia para a sessão atual.
Amostras são correlacionadas, condições e versões do app variam; contagem não representa confiança estatística.
O fechamento relata divergência de 8–12% em MAP <0,5 e 0–4% na maior parte das faixas; esses valores não são
limites nem resultado garantido desta discretização. A tabela permite verificar cada faixa, inclusive divergências maiores.
Não corrige escalas antigas da telemetria nem mistura esta base com o Ledger, propostas ou comandos.
Não prova runtime/bridge/WebView ou comportamento físico; só descreve o corpus registrado.

## Quadro por célula

'''
    doc += table(cells) + '\n\n## Último snapshot por sessão (nós na ordem original)\n\n'
    for c in data['curves']:
        doc += f"### {c['sessao']}\n\nSequence: {display(c['sequence'])}; snapshotHash: {c['snapshotHash'] or '—'}; status: {json.dumps(c['fieldStatus'], ensure_ascii=False)}.\n\n"
        for key in ('mulActQ14', 'k', 'petrolAxisRaw', 'petrolAxisMs'):
            doc += f"- {key}: " + (', '.join(display(v) for v in c[key]) if c[key] else '—') + '\n'
        doc += '\n'
    doc += '''## Entrada suplementar separada

Quando disponível, `s1838/events_0001.jsonl` (sessão 2026-10-16 18-38) é lido com `--extra CAMINHO`.
O comando grava `quadro-gasolina-gnv-k-extra.json` ao lado do arquivo de entrada, com SHA-256, sequência
por leitura e quadro próprio. Esse segundo arquivo de entrada normalizado NÃO participa do JSON oficial
nem desta tabela. `load_bar` vira MAP, `gas_ms_diagnostic` vira gas_ms; sem valor, fica desconhecido.
Exemplo: `python3 -B tools/sessoes/gerar_referencia_gasolina.py --extra /caminho/s1838/events_0001.jsonl`.
'''
    return data, doc


def extra(path):
    rows = []
    for line in path.read_text().splitlines():
        event = json.loads(line)
        if event.get('type') != 'telemetry':
            continue
        d = event['data']
        rows.append(dict(sessao='2026-10-16_18-38', sequence=event.get('sequence'), state=d.get('state'),
                         rpm=d.get('rpm'), map_bar=d.get('load_bar'), petrol_ms=d.get('petrol_ms'),
                         gas_ms=d.get('gas_ms_diagnostic')))
    result = dict(historical=True, officialReference=False, source=str(path),
                  sha256=hashlib.sha256(path.read_bytes()).hexdigest(), rows=rows, cells=aggregate(rows))
    path.with_name('quadro-gasolina-gnv-k-extra.json').write_text(encode(result))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--extra', type=Path)
    args = parser.parse_args()
    data, doc = generate()
    JSON_PATH.parent.mkdir(parents=True, exist_ok=True)
    JSON_PATH.write_text(encode(data))
    DOC_PATH.write_text(doc)
    # WebView file:// não permite fetch de JSON: empacotar somente células consultáveis.
    js_path = ROOT / 'app/src/main/assets/ui/core/gasoline-reference.js'
    compact = {key: data[key] for key in ('historical', 'readOnly', 'fonte')}
    compact['cells'] = [{key: c[key] for key in ('mapMin', 'mapMax', 'rpmMin', 'rpmMax', 'petrolMs', 'n')}
                        for c in data['cells'] if c['petrolMs'] is not None]
    block = '  // BEGIN GENERATED REFERENCE\n  const bundled = ' + json.dumps(compact, ensure_ascii=False, separators=(',', ':')) + ';\n  // END GENERATED REFERENCE'
    js_path.write_text(re.sub(r'  // BEGIN GENERATED REFERENCE.*?  // END GENERATED REFERENCE',
                             lambda _: block, js_path.read_text(), flags=re.S))
    if args.extra:
        extra(args.extra)
