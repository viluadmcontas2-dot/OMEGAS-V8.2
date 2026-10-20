#!/usr/bin/env python3
"""Grade-oráculo da volta à gasolina: executa os setters ORIGINAIS (Unicorn, mesmo caminho de volta_gasolina_original.py)
para vários tempos e vários pares (pressão digitada, RIF_PRESS_DIFF[10]) e grava os quadros em
tests/fixtures/volta_gasolina_original_grade.json. O teste JUnit VoltaGasolinaCoreTest compara o app quadro a quadro com
este arquivo (o .exe não existe no CI). Determinístico. Uso: volta_gasolina_grade.py --exe ProgBase.exe [--out caminho]
"""
import argparse
import json
import os
import struct
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.abspath(os.path.join(AQUI, '..', '..'))
sys.path.insert(0, AQUI)
sys.path.insert(0, os.path.join(RAIZ, 'tools', 'progbase'))
import volta_gasolina_original as V  # noqa: E402

SAIDA = os.path.join(RAIZ, 'tests', 'fixtures', 'volta_gasolina_original_grade.json')
TEMPOS = list(range(0, 21)) + [255]
P0S = [1000, 1250, 1500, 1750, 2000, 2200, 3000]
PMPS = [0.10, 0.80, 1.00, 1.10, 1.20, 1.25, 1.30, 1.35, 1.40, 1.50]


def gerar(exe):
    import abas_emul as E
    import abas_motor as M
    b, r = M.abrir(exe)
    campos = r.fields('TStreamDati')
    objs = E.objetos_sd(open(V.DFM_SD, encoding='utf-8').read())
    em = E.Emul(exe, campos, objs, lr=0)
    E.todos_vetores(em, objs, campos)
    tempos = {}
    V._fpcw(em)
    for t in TEMPOS:
        res = em.set_esc(28, float(t))
        assert res['erro'] is None, res
        tempos[str(t)] = [V._hex(q) for q in res['quadros']]
    tabelas = []
    for p0 in P0S:
        em = E.Emul(exe, campos, objs, lr=0)   # máquina nova por P0: o emulador tem limite de instruções acumulado
        E.todos_vetores(em, objs, campos)
        for pmp in PMPS:
            a, bb = V.tabela_original(em, pmp, p0)
            quadros = [q for _, qs, _, _ in V.quadros_tabela(em, a, bb) for q in qs]
            tabelas.append(dict(pmp_bar=pmp, p0_bruto=p0, quadros=quadros,
                                a_double_hex=[struct.pack('>d', x).hex() for x in a],
                                b_double_hex=[struct.pack('>d', x).hex() for x in bb]))
    return dict(exe_sha256=b.sha256, flag_SD_0x1DD4=0,
                fonte='conta de 0x4C526C e setters 0x4327C0/0x432670/0x436444 executados do .exe (Unicorn, FPCW 0x1332)',
                tempo_ritorno_benzina=tempos, tabelas=tabelas)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--exe', default=os.environ.get('PROGBASE_EXE', ''))
    ap.add_argument('--out', default=SAIDA)
    a = ap.parse_args(argv)
    if not a.exe or not os.path.isfile(a.exe):
        print('ProgBase.exe não encontrado (--exe ou PROGBASE_EXE)', file=sys.stderr)
        return 2
    with open(a.out, 'w', encoding='utf-8') as f:
        json.dump(gerar(a.exe), f, ensure_ascii=False, indent=1, sort_keys=True)
        f.write('\n')
    return 0


if __name__ == '__main__':
    sys.exit(main())
