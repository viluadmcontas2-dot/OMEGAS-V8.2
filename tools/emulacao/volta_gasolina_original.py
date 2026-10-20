#!/usr/bin/env python3
"""Volta automática à gasolina e pressão baixa: executa NA CPU EMULADA (Unicorn) os setters ORIGINAIS do
TStreamDati que os handlers 0x4A9484 (CheckRitornoBenzinaClick) e 0x4C526C (EditBassaPressioneMpKeyPress)
chamam, e devolve os quadros que iriam para a serial.

O que é executado do .exe (OBS-COD): SetEsc 0x436444 (código 28 -> TEMPO_RITORNO_BENZINA SC 62;
código 68 -> PRESS_BASSA_RETROPAS_LR SC 152), 0x4327C0 (RIF_PRESS_DIFF[i], SC 123) e 0x432670
(COEFF_PRESS_DIFF[i], SC 124) com a pilha de protocolo até WriteFile (stub).
O que é reproduzido em Python (DERIVADO da desmontagem, ver docs/progbase/protocolo/volta-automatica-gasolina.md):
a conta da tabela de 15 pontos que 0x4C526C faz antes de chamar os setters.
A ECU é STUB (eco + ACK 53 00 53, INFERIDO do LOG). Determinístico. Uso:
  volta_gasolina_original.py [--exe ProgBase.exe] [--pmp 1.25] [--p0-bruto N] [--json]
"""
import argparse
import json
import math
import os
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.abspath(os.path.join(AQUI, '..', '..'))
sys.path.insert(0, AQUI)
sys.path.insert(0, os.path.join(RAIZ, 'tools', 'progbase'))

DFM_SD = os.path.join(RAIZ, 'docs', 'evidence', 'dfm-original', 'TSTREAMDATI.txt')
OFF_RIF = 0x1D5C        # cache RIF_PRESS_DIFF (15 x u32) no TStreamDati (OBS-COD 0x432784/0x43280F)


def tabela_pressao(pmp, p0):
    """Conta de 0x4C52FD..0x4C53E5 (DERIVADO). pmp = valor digitado (bar); p0 = RIF_PRESS_DIFF[10]*0.001 (bar).
    Devolve (A[15] pressões em bar, B[15] coeficientes)."""
    x = (p0 - 0.95) * 1.176470588235294 + 1.1           # consts 0x4C5474/0x4C547C/0x4C5484
    a = []
    for i in range(15):
        if i == 0:
            v = pmp
        elif i == 14:
            v = x
        elif i == 10:
            v = p0
        elif i < 10:
            v = (p0 - pmp) * i / 10 + pmp
        else:
            v = (x - p0) * (i - 10) / (15 - 10 - 1) + p0
        a.append(v)
    b = [(1.0 - math.sqrt(v / p0)) * 125.0 for v in a]   # 0x691094 = fsqrt; consts 1.0 (0x4C548C) e 125.0 (0x4C5490)
    return a, b


def _hex(q):
    return ' '.join('%02X' % c for c in q)


def emular(exe, pmp=1.25, p0_bruto=None, tempo=3):
    import abas_emul as E
    import abas_motor as M
    b, r = M.abrir(exe)
    campos = r.fields('TStreamDati')
    objs = E.objetos_sd(open(DFM_SD, encoding='utf-8').read())
    out = {'exe_sha256': b.sha256}

    em = E.Emul(exe, campos, objs, lr=0)
    E.todos_vetores(em, objs, campos)
    t = {}
    for nome, v in (('desmarcar_volta_auto (SetEsc 28, 255.0)', 255.0), ('marcar_sem_valor_salvo (SetEsc 28, 0.0)', 0.0),
                    ('marcar_restaura_%d (SetEsc 28, %d.0)' % (tempo, tempo), float(tempo))):
        res = em.set_esc(28, v)
        t[nome] = dict(quadros=[_hex(q) for q in res['quadros']], erro=res['erro'],
                       cache_0x2B94=em.cache(0x2B94))
    out['tempo_ritorno_benzina'] = t

    em = E.Emul(exe, campos, objs, lr=1)
    E.todos_vetores(em, objs, campos)
    res = em.set_esc(68, pmp)
    out['press_bassa_retropas_lr'] = dict(valor_bar=pmp, quadros=[_hex(q) for q in res['quadros']], erro=res['erro'],
                                          cache_0x2D80=em.cache(0x2D80))

    if p0_bruto is not None:
        em = E.Emul(exe, campos, objs, lr=0)
        E.todos_vetores(em, objs, campos)
        em.m.w32(em.m.sd + OFF_RIF + 10 * 4, p0_bruto)
        p0 = p0_bruto * 0.001   # getter 0x43277C: cache[10] * 0.001 (OBS-COD)
        a, bb = tabela_pressao(pmp, p0)
        pontos = []
        for i in range(15):
            ra = E.chamar_registros(em, 0x4327C0, edx=i, pilha=tuple(E.Emul._double(em, a[i])))
            rb = E.chamar_registros(em, 0x432670, edx=i, pilha=tuple(E.Emul._double(em, bb[i])))
            pontos.append(dict(i=i, pressao_bar=round(a[i], 6), coef=round(bb[i], 6),
                               rif_bruto=em.cache(OFF_RIF + 4 * i), coeff_bruto=(em.cache(0x1D98 + 4 * i) + 2**31) % 2**32 - 2**31,
                               quadros=[_hex(q) for q in ra['quadros'] + rb['quadros']],
                               erro=ra['erro'] or rb['erro']))
        out['tabela_press_diff'] = dict(pmp_bar=pmp, p0_bruto=p0_bruto, p0_bar=p0, flag_SD_0x1DD4=0, pontos=pontos)
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--exe', default=os.environ.get('PROGBASE_EXE', ''))
    ap.add_argument('--pmp', type=float, default=1.25)
    ap.add_argument('--p0-bruto', type=int, default=None, help='RIF_PRESS_DIFF[10] cru (mbar) da ECU; sem ele a tabela não é calculada')
    ap.add_argument('--tempo', type=int, default=3)
    a = ap.parse_args(argv)
    if not a.exe or not os.path.isfile(a.exe):
        print('ProgBase.exe não encontrado (--exe ou PROGBASE_EXE)', file=sys.stderr)
        return 2
    print(json.dumps(emular(a.exe, a.pmp, a.p0_bruto, a.tempo), ensure_ascii=False, indent=1, sort_keys=True))
    return 0


if __name__ == '__main__':
    sys.exit(main())
