#!/usr/bin/env python3
"""Lê um log Portmon do ProgBase (PortmonLOGNOVO.LOG, ECU do dono) e monta o que a ECU respondeu a cada leitura.

Formato do fio (v2, OBS-FIO): pedido `OP lo hi [extra] ck`; a ECU ecoa o pedido e responde `53 n dados ck`.
  OP 0x09 = número escalar; 0x0A = número com índice; 0x29 = vetor inteiro; 0x2A = linha de matriz.
Saída: {código_serial: {'vetor': bytes, 'escalar': bytes, 'indexado': {i: bytes}, 'linhas': {r: bytes}, 'linha_log': n}}
A PRIMEIRA leitura de cada forma vale (as seguintes só entram em 'releituras' quando diferem).
Uso: abas_log.py <log> [--json saida.json]
"""
import json
import os
import re
import sys

LOG_ENV = 'PROGBASE_LOG'
_RX_W = re.compile(rb'IRP_MJ_WRITE\s+\S+\s+Length (\d+): ((?:[0-9A-F]{2} )+)')
_RX_R = re.compile(rb'SUCCESS\s+Length (\d+): ((?:[0-9A-F]{2} )+)')


def achar_log(pedido=None):
    cands = [pedido, os.environ.get(LOG_ENV)]
    for c in cands:
        if c and os.path.isfile(c):
            return c
    return None


def tamanho_pedido(op):
    return {0x09: 4, 0x0A: 5, 0x29: 4, 0x2A: 5}.get(op)


def ler_log(caminho):
    """Percorre o log uma vez e devolve (tabela, escritas)."""
    tab = {}
    escritas = []
    pend = None          # (pedido bytes, linha, bytes recebidos)
    rx = bytearray()
    n = 0

    def fecha():
        nonlocal pend, rx
        if pend is None:
            return
        req, ln = pend
        op = req[0]
        if op in (0x09, 0x0A, 0x29, 0x2A):
            ecoa = len(req)
            r = bytes(rx)
            if len(r) >= ecoa + 3 and r[:ecoa] == req and r[ecoa] == 0x53:
                nn = r[ecoa + 1]
                dados = r[ecoa + 2:ecoa + 2 + nn]
                ck = r[ecoa + 2 + nn] if len(r) > ecoa + 2 + nn else None
                if len(dados) == nn and ck is not None and ((0x53 + nn + sum(dados)) & 0xFF) == ck:
                    sc = req[1] | (req[2] << 8)
                    e = tab.setdefault(sc, {'indexado': {}, 'linhas': {}})
                    chave = {0x09: 'escalar', 0x29: 'vetor'}.get(op)
                    if chave:
                        if chave not in e:
                            e[chave] = bytes(dados)
                            e.setdefault('linha_log', ln)
                        elif e[chave] != bytes(dados):
                            e.setdefault('releituras', []).append((ln, chave, bytes(dados).hex()))
                    elif op == 0x0A:
                        e['indexado'].setdefault(req[3], bytes(dados))
                    else:
                        e['linhas'].setdefault(req[3], bytes(dados))
        elif (op & 0xF0) in (0x10, 0x30):
            escritas.append((ln, bytes(req)))
        pend = None
        rx = bytearray()

    with open(caminho, 'rb') as fh:
        for linha in fh:
            n += 1
            if b'IRP_MJ_WRITE' in linha:
                m = _RX_W.search(linha)
                if m:
                    fecha()
                    pend = (bytes.fromhex(m.group(2).decode()), n)
                continue
            if pend is not None and b'SUCCESS' in linha and b'Length' in linha:
                m = _RX_R.search(linha)
                if m:
                    rx += bytes.fromhex(m.group(2).decode())
    fecha()
    return tab, escritas


def u16s(b):
    return [b[i] | (b[i + 1] << 8) for i in range(0, len(b) - 1, 2)]


def para_json(tab):
    out = {}
    for sc, e in sorted(tab.items()):
        d = {'linha_log': e.get('linha_log')}
        for k in ('escalar', 'vetor'):
            if k in e:
                d[k] = e[k].hex()
        if e['indexado']:
            d['indexado'] = {str(i): v.hex() for i, v in sorted(e['indexado'].items())}
        if e['linhas']:
            d['linhas'] = {str(i): v.hex() for i, v in sorted(e['linhas'].items())}
        if e.get('releituras'):
            d['releituras'] = e['releituras'][:6]
        out[str(sc)] = d
    return out


def de_json(d):
    tab = {}
    for sc, e in d.items():
        x = {'indexado': {int(i): bytes.fromhex(v) for i, v in e.get('indexado', {}).items()},
             'linhas': {int(i): bytes.fromhex(v) for i, v in e.get('linhas', {}).items()},
             'linha_log': e.get('linha_log')}
        for k in ('escalar', 'vetor'):
            if k in e:
                x[k] = bytes.fromhex(e[k])
        tab[int(sc)] = x
    return tab


def main(argv=None):
    a = list(argv if argv is not None else sys.argv[1:])
    saida = None
    if '--json' in a:
        i = a.index('--json')
        saida = a[i + 1]
        del a[i:i + 2]
    caminho = achar_log(a[0] if a else None)
    if not caminho:
        print('log não encontrado (use o argumento ou PROGBASE_LOG)', file=sys.stderr)
        return 2
    tab, esc = ler_log(caminho)
    print('vetores lidos: %d, escritas vistas: %d' % (len(tab), len(esc)))
    if saida:
        with open(saida, 'w', encoding='utf-8') as fh:
            json.dump(para_json(tab), fh, indent=1, sort_keys=True)
    return 0


if __name__ == '__main__':
    sys.exit(main())
