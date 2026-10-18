#!/usr/bin/env python3
"""Matriz comando x resposta verificada contra a captura Portmon (D2 PROTOCOLO).

Saídas determinísticas: matriz-comando-resposta.json e .md. Para cada família de comando (opcode + tamanho do
quadro) conta ACK `53`, NAK `CA`, sem resposta, etc., cita a PRIMEIRA linha do .LOG de cada classe de resposta e
mostra quantos pares (pedido, resposta) distintos o ecu_emulator existente (tools/emulacao/ecu_emulator.py)
regenera byte a byte. Marca: OBS-FIO.

Uso: protocolo_matriz.py --log ROTULO=LOG [--log ROTULO2=LOG2 ...] --json matriz.json --md matriz.md
(o rótulo identifica a captura nos documentos; caminhos locais nunca são gravados nas saídas)
"""
import argparse
import collections
import json
import os
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.dirname(os.path.dirname(AQUI))
sys.path.insert(0, AQUI)
sys.path.insert(0, RAIZ)
import protocolo_log as plog  # noqa: E402


def regeneracao_por_familia(log):
    """Pares distintos (pedido, resposta) que a ECU emulada regenera, por família. Devolve (ok, falha, total_pares)."""
    from tools.emulacao.ecu_emulator import EcuEmulada
    e = EcuEmulada()
    e.carregar_captura(log)
    ok, falha = e.regenerar()
    r_ok, r_falha = collections.Counter(), collections.Counter()
    for p, _ in ok:
        r_ok[plog.familia(p)] += 1
    for p, _ in falha:
        r_falha[plog.familia(p)] += 1
    return dict(r_ok), dict(r_falha), len(e.transacoes)


def montar(log):
    r = plog.resumo(log)
    ok, falha, ntrans_emu = regeneracao_por_familia(log)
    for f, d in r['matriz'].items():
        d['pares_distintos_regenerados'] = ok.get(f, 0)
        d['pares_distintos_divergentes'] = falha.get(f, 0)
    r['emulador'] = {'transacoes_na_ecu_emulada': ntrans_emu, 'pares_regenerados': sum(ok.values()),
                     'pares_divergentes': sum(falha.values()),
                     'nota': 'a ECU emulada só vê o dispositivo Silabser0; as 2 transações em Serial1 (sem ECU) ficam fora'}
    tot = collections.Counter()
    for d in r['matriz'].values():
        for k, v in d['classes'].items():
            tot[k] += v
    r['totais_por_classe'] = dict(sorted(tot.items()))
    return r


def para_markdown(r, rotulo=''):
    L = ['## Captura %s' % rotulo, '',
         'Fonte: captura Portmon (sha256 `%s`), %d transações (um IRP_MJ_WRITE do ProgBase + as leituras seguintes), '
         '%d aberturas de porta. Marca: **OBS-FIO**.' % (r['arquivo_sha256'], r['transacoes'], len(r['sessoes'])), '',
         'Classes de resposta: `ACK_53` = `53 n dados ck` válido; `NAK_CA` = `CA s1 s2 ck`; `SEM_RESPOSTA` = timeout; '
         '`WAKE_80` = `80` sem eco. Totais: %s.' % ', '.join('%s=%d' % kv for kv in r['totais_por_classe'].items()), '',
         'ECU emulada (`tools/emulacao/ecu_emulator.py`): %d pares distintos regenerados, %d divergentes.' % (
             r['emulador']['pares_regenerados'], r['emulador']['pares_divergentes']), '',
         '### Aberturas de porta', '', '| # | dispositivo | baud | escritas | linha de abertura | RC de leitura vistos |', '|---|---|---|---|---|---|']
    for s in r['sessoes']:
        L.append('| %d | %s | %s | %d | %d | %s |' % (s['sessao'], s['dev'], s['baud'], s['escritas'], s['linha_abertura'],
                                                    ', '.join('%s ms x%d' % kv for kv in s['timeouts_rc'].items())))
    L += ['', '### Famílias de comando', '',
          '| família | total | ACK | NAK | sem resp. | outras | pedidos distintos | pares (pedido,resposta) distintos regenerados | 1ª linha ACK | 1ª linha NAK |',
          '|---|---|---|---|---|---|---|---|---|---|']
    for f, d in r['matriz'].items():
        c = d['classes']
        outras = sum(v for k, v in c.items() if k not in ('ACK_53', 'NAK_CA', 'SEM_RESPOSTA'))
        ex = d['exemplos']
        L.append('| `%s` | %d | %d | %d | %d | %d | %d | %d/%d | %s | %s |' % (
            f, d['total'], c.get('ACK_53', 0), c.get('NAK_CA', 0), c.get('SEM_RESPOSTA', 0), outras, d['pedidos_distintos'],
            d['pares_distintos_regenerados'], d['pares_distintos_regenerados'] + d['pares_distintos_divergentes'],
            ex['ACK_53']['line'] if 'ACK_53' in ex else '—', ex['NAK_CA']['line'] if 'NAK_CA' in ex else '—'))
    L += ['', '### Exemplos reais (primeira ocorrência de cada classe)', '']
    for f, d in r['matriz'].items():
        L.append('#### `%s`' % f)
        L.append('')
        for cl, e in d['exemplos'].items():
            L.append('- %s — linha %d (abertura %d): `%s` -> `%s`' % (cl, e['line'], e['sessao'], e['req'], e['resp']))
        if d['nak']:
            L.append('- NAK observados: %s' % ', '.join('`%s` x%d' % kv for kv in d['nak'].items()))
        L.append('')
    return '\n'.join(L)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument('--log', action='append', required=True, help='ROTULO=caminho (repetível)')
    ap.add_argument('--json')
    ap.add_argument('--md')
    a = ap.parse_args(argv)
    res = collections.OrderedDict()
    for item in a.log:
        rot, _, cam = item.partition('=')
        res[rot] = montar(cam)
    if a.json:
        with open(a.json, 'w', encoding='utf-8') as fh:
            json.dump(res, fh, ensure_ascii=False, indent=1)
            fh.write('\n')
    if a.md:
        L = ['# Matriz comando x resposta (gerado — não editar à mão)', '',
             'Gerado por `tools/progbase/protocolo_matriz.py`. Cada captura é lida do zero (nada é copiado de documentos '
             'anteriores). Linhas citadas = número da linha do arquivo `.LOG`.', '']
        for rot, r in res.items():
            L.append(para_markdown(r, rot))
        with open(a.md, 'w', encoding='utf-8') as fh:
            fh.write('\n'.join(L) + '\n')
    for rot, r in res.items():
        print(rot, 'famílias:', len(r['matriz']), 'regenerados:', r['emulador']['pares_regenerados'], 'divergentes:',
              r['emulador']['pares_divergentes'])
    return 0


if __name__ == '__main__':
    sys.exit(main())
