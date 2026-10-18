#!/usr/bin/env python3
"""Gera TODO o inventário do ProgBase.exe em docs/progbase/inventario/ (determinístico e idempotente).

Uso:  PROGBASE_EXE=/caminho/ProgBase.exe python3 tools/progbase/inventario_tudo.py [--out DIR] [--atualizar-cobertura FONTE=CAMINHO ...]
  PROGBASE_EXE   caminho do .exe (sha256 8a2d297c…); padrão $OMEGAS_CACHE/ProgBase.exe (~/.cache/omegas/ProgBase.exe),
                 o mesmo que tools/obter_progbase.py baixa. O .exe NUNCA é versionado.
  --atualizar-cobertura RÓTULO=CAMINHO  reescreve tools/progbase/dados/cobertura_externa.json com os endereços citados em documentos
                 (ex.: docs-evidence=docs/evidence protocolo-landi=/caminho/protocolo-landi). Opcional; o JSON versionado basta.
Precisa de Python 3 com pefile e capstone. Tempo típico: ~2 min."""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from inventario_comum import Binario, RAIZ, SAIDA, SHA_ESPERADO, achar_exe, escrever  # noqa: E402
import inventario_backlog as bkl  # noqa: E402
import inventario_codigo as cdg  # noqa: E402
import inventario_dfm as dfm  # noqa: E402
import inventario_funcionalidades as fun  # noqa: E402
import inventario_i18n as i18n  # noqa: E402
import inventario_rtti as rtti  # noqa: E402
import inventario_telas as telas_mod  # noqa: E402


def executar(exe, saida, verboso=False):
    b = Binario(exe)
    log = (lambda *a: print(*a, file=sys.stderr)) if verboso else (lambda *a: None)
    cl = rtti.gerar(exe, saida)
    log('rtti', len(cl))
    arv, nao = dfm.gerar(exe, saida)
    log('dfm', len(arv), len(nao))
    cod, nomes, un, grupos = cdg.montar(b, cl)
    funcs, meta = cdg.funcoes(b, cod, nomes, un, grupos)
    usos = cdg.usos_traducao(cod)
    log('codigo', len(funcs), len(usos))
    lbl = i18n.carregar_lbl(b)
    imeta = i18n.gerar(b, arv, usos, lambda fn: nomes.get(fn, (None,))[0], saida)
    telas = telas_mod.gerar_com(b, cl, cod, nomes, arv, lbl, usos, saida)
    fun.gerar(saida)
    bmeta, n_sem, n_lac = bkl.gerar(b, cl, cod, funcs, meta, un, grupos, saida)
    return {'classes': len(cl), 'dfm': len(arv), 'telas': len(telas), 'funcoes_programa': bmeta['n_funcoes_programa'],
            'sem_nome': n_sem, 'lacunas': n_lac, 'chaves_i18n': imeta['n_chaves']}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--exe')
    ap.add_argument('--out', default=SAIDA)
    ap.add_argument('--atualizar-cobertura', nargs='*', metavar='ROTULO=CAMINHO')
    a = ap.parse_args()
    exe = achar_exe(a.exe)
    if not exe:
        sys.exit('ProgBase.exe não encontrado: defina PROGBASE_EXE (veja o cabeçalho deste arquivo)')
    if a.atualizar_cobertura:
        fontes = [tuple(x.split('=', 1)) for x in a.atualizar_cobertura]
        print('cobertura:', cdg.atualizar_cobertura(fontes), 'endereços citados')
    r = executar(exe, a.out, verboso=True)
    print(r)


if __name__ == '__main__':
    main()
