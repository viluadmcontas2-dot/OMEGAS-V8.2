#!/usr/bin/env python3
"""Leitor determinístico de capturas Portmon do ProgBase (D2 PROTOCOLO).

Transforma o .LOG em uma lista de transações (um IRP_MJ_WRITE do ProgBase + as leituras que o seguem)
e calcula a matriz comando x resposta. Só leitura, só stdlib, nunca abre porta.

Formato do Portmon (OBS-FIO): cada IRP ocupa 2 linhas com o mesmo número de sequência:
  `<seq> <dur> ProgBase.exe <IRP> <dispositivo> <detalhe>`   e   `<seq> <dur> <STATUS> [Length N: hex]`.
O tempo absoluto NÃO existe na captura (coluna = duração do IRP); por isso nenhuma regra aqui usa relógio.

Uso:  protocolo_log.py LOG [--json saida.json]
"""
import argparse
import hashlib
import json
import re
import sys
from collections import Counter, OrderedDict

_REQ = re.compile(r'^(\d+)\s+([\d.]+)\s+ProgBase\.exe\s+(\S+)\s+(\S+)\s*(.*)$')
_RES = re.compile(r'^(\d+)\s+([\d.]+)\s+(\S+)\s*(.*)$')
_HEX = re.compile(r'Length\s+(\d+):\s*((?:[0-9A-Fa-f]{2}\s*)*)$')
_LEN = re.compile(r'Length\s+(\d+)')
_TMO = re.compile(r'RC:(\d+)')
_BAUD = re.compile(r'Rate:\s*(\d+)')

ACK_OK, ACK_NAK, ACK_RETRY, ACK_FLASH, WAKE = 0x53, 0xCA, 0x96, 0xA7, 0x80


def soma(bs):
    return sum(bs) & 0xFF


def hex_de(texto):
    m = _HEX.search(texto.strip() + ' ')
    if not m:
        return b''
    return bytes.fromhex(m.group(2).replace(' ', ''))


def sha256_arquivo(caminho):
    h = hashlib.sha256()
    with open(caminho, 'rb') as fh:
        for bloco in iter(lambda: fh.read(1 << 20), b''):
            h.update(bloco)
    return h.hexdigest()


def ler_eventos(caminho):
    """Gera eventos na ordem do arquivo: dict(kind, line, seq, dev, ...). kind: create, close, baud, timeouts,
    purge, write, read. `read` traz req_len, status, dados (bytes) e dur."""
    pend = {}
    with open(caminho, encoding='latin1', errors='replace') as fh:
        for n, linha in enumerate(fh, 1):
            s = linha.rstrip('\r\n')
            m = _REQ.match(s)
            if m:
                pend[int(m.group(1))] = (n, m.group(3), m.group(4), m.group(5))
                continue
            r = _RES.match(s)
            if not r:
                continue
            seq = int(r.group(1))
            if seq not in pend:
                continue
            ln, irp, dev, det = pend.pop(seq)
            status, resto, dur = r.group(3), r.group(4), float(r.group(2))
            if irp == 'IRP_MJ_CREATE':
                yield dict(kind='create', line=ln, seq=seq, dev=dev)
            elif irp == 'IRP_MJ_CLOSE':
                yield dict(kind='close', line=ln, seq=seq, dev=dev)
            elif irp == 'IOCTL_SERIAL_SET_BAUD_RATE':
                b = _BAUD.search(det)
                yield dict(kind='baud', line=ln, seq=seq, dev=dev, baud=int(b.group(1)) if b else None)
            elif irp == 'IOCTL_SERIAL_SET_TIMEOUTS':
                t = _TMO.search(det)
                yield dict(kind='timeouts', line=ln, seq=seq, dev=dev, rc=int(t.group(1)) if t else None)
            elif irp == 'IOCTL_SERIAL_PURGE':
                yield dict(kind='purge', line=ln, seq=seq, dev=dev)
            elif irp == 'IRP_MJ_WRITE':
                yield dict(kind='write', line=ln, seq=seq, dev=dev, dados=hex_de(det))
            elif irp == 'IRP_MJ_READ':
                q = _LEN.search(det)
                yield dict(kind='read', line=ln, seq=seq, dev=dev, req_len=int(q.group(1)) if q else None,
                           status=status, dados=hex_de(resto), dur=dur)


def transacoes(caminho):
    """Lista de transações. Cada uma: line, sessao, dev, baud, rc (timeout de leitura vigente), purge (houve purge
    logo antes), req (bytes), leituras [(req_len, status, bytes)], resp (bytes concatenados)."""
    out, cur = [], None
    sessao, baud, rc, purge = 0, None, None, False
    baud_dev = {}
    for ev in ler_eventos(caminho):
        k = ev['kind']
        if k == 'create':
            sessao += 1
        elif k == 'baud':
            baud_dev[ev['dev']] = ev['baud']
        elif k == 'timeouts':
            rc = ev['rc']
            if cur is not None and ev['dev'] == cur['dev']:
                cur['rcs'].append(ev['rc'])
        elif k == 'purge':
            purge = True
        elif k == 'write':
            cur = dict(line=ev['line'], sessao=sessao, dev=ev['dev'], baud=baud_dev.get(ev['dev']), rc=rc,
                       purge=purge, req=ev['dados'], leituras=[], resp=b'', rcs=[])
            purge = False
            out.append(cur)
        elif k == 'read' and cur is not None and ev['dev'] == cur['dev']:
            cur['leituras'].append((ev['req_len'], ev['status'], ev['dados']))
            cur['resp'] += ev['dados']
        elif k == 'close':
            cur = None
    return out


def classificar(t):
    """Classifica a resposta de uma transação. Devolve (classe, detalhe).
    classes: ACK_53 (53 n dados ck válido), NAK_CA, RETRY_96, FLASH_A7, WAKE_80, ECO_DIVERGENTE, SEM_RESPOSTA,
    ECO_SO (só eco, sem ACK), CK_INVALIDO, CURTA."""
    req, resp = t['req'], t['resp']
    if len(req) == 1:
        if resp == bytes([WAKE]):
            return 'WAKE_80', ''
        return ('SEM_RESPOSTA', '') if not resp else ('CURTA', resp.hex(' ').upper())
    if not resp:
        return 'SEM_RESPOSTA', ''
    if resp[:len(req)] != req:
        return 'ECO_DIVERGENTE', resp.hex(' ').upper()
    corpo = resp[len(req):]
    if not corpo:
        return 'ECO_SO', ''
    a = corpo[0]
    if a == ACK_OK:
        if len(corpo) < 3:
            return 'CURTA', corpo.hex(' ').upper()
        n = corpo[1]
        if len(corpo) < n + 3:
            return 'CURTA', corpo.hex(' ').upper()
        if soma(corpo[:n + 2]) != corpo[n + 2]:
            return 'CK_INVALIDO', corpo.hex(' ').upper()
        return 'ACK_53', ''
    if a == ACK_NAK:
        return 'NAK_CA', corpo.hex(' ').upper()
    if a == ACK_RETRY:
        return 'RETRY_96', corpo.hex(' ').upper()
    if a == ACK_FLASH:
        return 'FLASH_A7', corpo.hex(' ').upper()
    return 'CURTA', corpo.hex(' ').upper()


def familia(req):
    """Chave estável do comando: opcode + comprimento do quadro (+ sub-comando para o grupo 00/01/02)."""
    if not req:
        return 'vazio'
    if len(req) == 1:
        return '%02X (1 byte)' % req[0]
    op = req[0]
    if op in (0x00, 0x01, 0x02) and len(req) >= 3:
        return '%02X %02X /%d' % (op, req[1], len(req))
    return '%02X /%d' % (op, len(req))


def timeouts_rc(t):
    """Número de leituras que estouraram o tempo (status TIMEOUT) nesta transação."""
    return sum(1 for _, st, _ in t['leituras'] if st == 'TIMEOUT')


def matriz(trans):
    """Matriz comando x resposta. Para cada família: contagem por classe, exemplos (primeira linha do log por
    classe), RC de leitura vistos, quantos pedidos exatos distintos."""
    fam = OrderedDict()
    for t in trans:
        f = familia(t['req'])
        d = fam.setdefault(f, dict(total=0, classes=Counter(), exemplos={}, distintos=set(), rc=Counter(), timeouts=0,
                                   ck_pedido_ok=0, baud=Counter(), nak_detalhes=Counter(), pedidos={}))
        pr = d['pedidos'].setdefault(bytes(t['req']), dict(total=0, classes=Counter(), primeira_linha=t['line'],
                                                           tam=Counter()))
        pr['total'] += 1
        pr['classes'][classificar(t)[0]] += 1
        if classificar(t)[0] == 'ACK_53':
            pr['tam'][t['resp'][len(t['req']) + 1]] += 1
        d['total'] += 1
        cl, det = classificar(t)
        d['classes'][cl] += 1
        d['distintos'].add(bytes(t['req']))
        d['rc'][max(t['rcs']) if t['rcs'] else None] += 1
        d['baud'][t['baud']] += 1
        d['timeouts'] += timeouts_rc(t)
        if len(t['req']) > 1 and soma(t['req'][:-1]) == t['req'][-1]:
            d['ck_pedido_ok'] += 1
        if cl == 'NAK_CA':
            d['nak_detalhes'][det] += 1
        if cl not in d['exemplos']:
            d['exemplos'][cl] = dict(line=t['line'], req=t['req'].hex(' ').upper(),
                                     resp=t['resp'].hex(' ').upper()[:160], sessao=t['sessao'])
    out = OrderedDict()
    for f in sorted(fam):
        d = fam[f]
        out[f] = dict(total=d['total'], classes=dict(sorted(d['classes'].items())), distintos=len(d['distintos']),
                      ck_pedido_ok=d['ck_pedido_ok'], leituras_com_timeout=d['timeouts'],
                      rc_leitura_ms={str(k): v for k, v in sorted(d['rc'].items(), key=lambda kv: str(kv[0]))},
                      baud={str(k): v for k, v in sorted(d['baud'].items(), key=lambda kv: str(kv[0]))},
                      nak=dict(sorted(d['nak_detalhes'].items())),
                      pedidos=[dict(req=k.hex(' ').upper(), total=v['total'], classes=dict(sorted(v['classes'].items())),
                                    primeira_linha=v['primeira_linha'],
                                    bytes_de_dados=dict(sorted(v['tam'].items())))
                               for k, v in sorted(d['pedidos'].items(), key=lambda kv: (-kv[1]['total'], kv[0]))[:40]],
                      pedidos_distintos=len(d['pedidos']),
                      exemplos=dict(sorted(d['exemplos'].items())))
    return out


def sessoes(caminho):
    """Resumo por abertura de porta (IRP_MJ_CREATE): dispositivo, baud, nº de transações, primeira linha."""
    res, cur = [], None
    baud_dev = {}
    for ev in ler_eventos(caminho):
        k = ev['kind']
        if k == 'create':
            cur = dict(sessao=len(res) + 1, dev=ev['dev'], linha_abertura=ev['line'], baud=None, escritas=0,
                       timeouts_rc=Counter())
            res.append(cur)
        elif k == 'baud' and cur is not None:
            cur['baud'] = ev['baud']
        elif k == 'timeouts' and cur is not None:
            cur['timeouts_rc'][ev['rc']] += 1
        elif k == 'write' and cur is not None:
            cur['escritas'] += 1
        elif k == 'close' and cur is not None:
            cur['linha_fecho'] = ev['line']
    for s in res:
        s['timeouts_rc'] = {str(k): v for k, v in sorted(s['timeouts_rc'].items(), key=lambda kv: str(kv[0]))}
    return res


def resumo(caminho):
    trans = transacoes(caminho)
    return OrderedDict(
        arquivo_sha256=sha256_arquivo(caminho),
        transacoes=len(trans),
        sessoes=sessoes(caminho),
        matriz=matriz(trans),
    )


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument('log')
    ap.add_argument('--json')
    a = ap.parse_args(argv)
    r = resumo(a.log)
    txt = json.dumps(r, ensure_ascii=False, indent=1, sort_keys=False)
    if a.json:
        with open(a.json, 'w', encoding='utf-8') as fh:
            fh.write(txt + '\n')
    print('transações:', r['transacoes'], 'famílias:', len(r['matriz']), 'sessões:', len(r['sessoes']))
    return 0


if __name__ == '__main__':
    sys.exit(main())
