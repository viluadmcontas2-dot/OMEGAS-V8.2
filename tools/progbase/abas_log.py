#!/usr/bin/env python3
"""Le o Portmon do ProgBase (PortmonLOGNOVO.LOG, ECU do dono, 12/08/2026) e extrai as LEITURAS por SerialCode.

Quadro de pedido: [op][sc_lo][sc_hi]([idx])[ck]; resposta: eco do pedido + 53 n dados ck (DERIVADO do log, ver
tools/sessoes/portmon_ler_vetor.py). Ops: 09 escalar, 0A escalar+indice, 0B matriz(sub,idx), 28/29 vetor, 2A linha de matriz.
Saida deterministica: {"SC:op:args": {linha, hex, n, leituras, distintas}} guardando a ULTIMA leitura valida."""
import hashlib
import json
import re
import sys

W = re.compile(r'IRP_MJ_WRITE\s+\S+\s+Length (\d+): ((?:[0-9A-F]{2} ?)+)')
R = re.compile(r'SUCCESS\s+Length (\d+): ((?:[0-9A-F]{2} ?)+)')
TAM_PEDIDO = {0x09: 4, 0x0A: 5, 0x0B: 6, 0x29: 4, 0x2A: 5, 0x28: 4}


def ler_log(caminho):
    with open(caminho, 'rb') as fh:
        dados = fh.read()
    sha = hashlib.sha256(dados).hexdigest()
    linhas = dados.decode('latin-1').split('\n')
    saida, escritas = {}, []
    pend = None

    def fechar():
        nonlocal pend
        if pend is None:
            return
        q, rx, ln = pend
        pend = None
        op = q[0]
        if op in (0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37):
            escritas.append(dict(linha=ln, quadro=bytes(q).hex(' ').upper()))
            return
        if op not in TAM_PEDIDO or len(q) != TAM_PEDIDO[op]:
            return
        if len(rx) < len(q) + 3 or bytes(rx[:len(q)]) != bytes(q) or rx[len(q)] != 0x53:
            return
        n = rx[len(q) + 1]
        dados_ = bytes(rx[len(q) + 2:len(q) + 2 + n])
        if len(dados_) != n or len(rx) < len(q) + 3 + n:
            return
        if ((0x53 + n + sum(dados_)) & 0xFF) != rx[len(q) + 2 + n]:
            return
        sc = q[1] | (q[2] << 8)
        args = tuple(q[3:-1])
        chave = '%d:%02X:%s' % (sc, op, ','.join(str(a) for a in args))
        e = saida.setdefault(chave, dict(linha=ln, hex=dados_.hex(' ').upper(), n=n, leituras=0, _v=set()))
        e['leituras'] += 1
        e['_v'].add(dados_.hex())
        e['linha'], e['hex'] = ln, dados_.hex(' ').upper()

    for ln, l in enumerate(linhas, 1):
        if 'IRP_MJ_WRITE' in l and 'Silabser0' in l:
            fechar()
            m = W.search(l)
            if m:
                pend = (bytes(int(x, 16) for x in m.group(2).split()), bytearray(), ln)
            continue
        if pend is not None and 'SUCCESS' in l and 'Length' in l and ':' in l:
            m = R.search(l)
            if m and 'READ' not in l:
                pend[1].extend(int(x, 16) for x in m.group(2).split())
    fechar()
    for e in saida.values():
        e['distintas'] = len(e.pop('_v'))
    return dict(fonte='PortmonLOGNOVO.LOG', sha256=sha, leituras=dict(sorted(saida.items())), escritas=escritas)


if __name__ == '__main__':
    r = ler_log(sys.argv[1])
    json.dump(r, open(sys.argv[2], 'w'), indent=1, sort_keys=True)
    print(len(r['leituras']), 'leituras;', len(r['escritas']), 'escritas')
