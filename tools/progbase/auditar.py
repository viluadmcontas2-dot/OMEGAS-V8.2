#!/usr/bin/env python3
"""Auditor das evidências do ProgBase (stdlib apenas, idempotente, saída determinística).

Índice mestre: docs/progbase/evidencias/INDEX.json (lista de objetos).
Campos: id, afirmacao, marca, fonte, versao_progbase, sha_commit, usado_por; opcionais:
chave (afirmação-chave, para achar contradição), funcoes (endereços do decomp, ex. "0x004C982C"),
uso_pendente (true = fato ainda não consumido por app/teste; só vale com motivo na afirmação).

Falha (--check devolve 1) em: (a) id duplicado ou fora do padrão EV-<AREA>-<nnn>; (b) marca inválida;
(c) fonte/usado_por inexistente (ou usado_por vazio sem ser DESCONHECIDO/uso_pendente);
(d) id citado em docs/**/*.md, app/** ou tests/** que não está no índice (órfão);
(e) mesma `chave` com marcas diferentes; (g) função citada que não existe no decomp.
Só RELATÓRIO (não falha): (f) funções nomeadas do programa (decomp) sem evidência.

Uso: python3 -B tools/progbase/auditar.py [--check] [--root RAIZ]
"""
import argparse
import gzip
import json
import re
import sys
from pathlib import Path

MARCAS = ('OBS-FIO', 'OBS-COD', 'DERIVADO', 'INFERIDO', 'DESCONHECIDO')
PADRAO_ID = re.compile(r'^EV-[A-Z]+-\d{3}$')
CITACAO = re.compile(r'(?<![A-Za-z0-9])EV-[A-Z]+-\d{3}(?!\d)')
PADRAO_SHA = re.compile(r'^[0-9a-f]{7,40}$')
PADRAO_FUNC = re.compile(r'^0x[0-9A-F]{8}$')
CAMPOS = ('id', 'afirmacao', 'marca', 'fonte', 'versao_progbase', 'sha_commit', 'usado_por')
INDICE = 'docs/progbase/evidencias/INDEX.json'
DECOMP = 'docs/progbase/decomp/funcoes.json.gz'
PULAR = {'.git', 'build', '.gradle', 'node_modules', '__pycache__', '.idea', 'graphify-out'}
EXT_TEXTO = {'.md', '.kt', '.java', '.js', '.cjs', '.mjs', '.py', '.json', '.html', '.css', '.xml',
             '.txt', '.sh', '.yml', '.yaml', '.gradle', '.kts', '.properties', '.jsonl', '.tsv'}
LIMITE_BYTES = 5_000_000


def _arquivos_texto(root, base, so_md=False):
    b = root / base
    if not b.is_dir():
        return
    for p in sorted(b.rglob('*')):
        if any(parte in PULAR for parte in p.relative_to(root).parts):
            continue
        if not p.is_file() or p.suffix.lower() not in (('.md',) if so_md else EXT_TEXTO):
            continue
        try:
            if p.stat().st_size > LIMITE_BYTES:
                continue
        except OSError:
            continue
        yield p


def _citacoes(root):
    achados = {}
    for base, so_md in (('docs', True), ('app', False), ('tests', False)):
        for p in _arquivos_texto(root, base, so_md):
            try:
                texto = p.read_text(encoding='utf-8')
            except (UnicodeDecodeError, OSError):
                continue
            for m in CITACAO.findall(texto):
                achados.setdefault(m, set()).add(p.relative_to(root).as_posix())
    return achados


def _funcoes_decomp(root):
    p = root / DECOMP
    if not p.is_file():
        return None
    try:
        return json.load(gzip.open(p, 'rt', encoding='utf-8'))['funcoes']
    except (OSError, ValueError, KeyError):
        return None


def auditar(root):
    root = Path(root)
    problemas = []
    arq = root / INDICE
    try:
        itens = json.loads(arq.read_text(encoding='utf-8'))
    except (OSError, ValueError) as e:
        return {'problemas': [f'índice ilegível ({INDICE}): {e}'], 'total': 0, 'por_marca': {}, 'relatorio': []}
    if not isinstance(itens, list):
        return {'problemas': [f'índice deve ser uma lista ({INDICE})'], 'total': 0, 'por_marca': {}, 'relatorio': []}

    decomp = _funcoes_decomp(root)
    enderecos = {f['a'] for f in decomp} if decomp else None
    ids = {}
    por_chave = {}
    for n, e in enumerate(itens):
        if not isinstance(e, dict):
            problemas.append(f'item {n}: não é objeto')
            continue
        i = e.get('id')
        rot = i if isinstance(i, str) and i else f'item {n}'
        faltam = [c for c in CAMPOS if c not in e]
        if faltam:
            problemas.append(f'{rot}: campos ausentes: {", ".join(faltam)}')
        if not isinstance(i, str) or not PADRAO_ID.match(i):
            problemas.append(f'{rot}: id fora do padrão EV-<AREA>-<nnn>')
        elif i in ids:
            problemas.append(f'{rot}: id duplicado')
        else:
            ids[i] = e
        if e.get('marca') not in MARCAS:
            problemas.append(f'{rot}: marca inválida ({e.get("marca")!r})')
        if not str(e.get('afirmacao', '')).strip():
            problemas.append(f'{rot}: afirmação vazia')
        if 'sha_commit' in e and not PADRAO_SHA.match(str(e['sha_commit'])):
            problemas.append(f'{rot}: sha_commit inválido ({e["sha_commit"]!r})')
        fonte = e.get('fonte')
        if isinstance(fonte, str) and fonte:
            if not (root / fonte).is_file():
                problemas.append(f'{rot}: fonte inexistente ({fonte})')
        elif 'fonte' in e:
            problemas.append(f'{rot}: fonte inexistente (vazia)')
        uso = e.get('usado_por', [])
        if not isinstance(uso, list):
            problemas.append(f'{rot}: usado_por deve ser lista')
            uso = []
        for u in uso:
            if not (root / str(u)).is_file():
                problemas.append(f'{rot}: usado_por inexistente ({u})')
        if not uso and e.get('marca') != 'DESCONHECIDO' and not e.get('uso_pendente'):
            problemas.append(f'{rot}: usado_por vazio (só permitido para DESCONHECIDO ou uso_pendente)')
        for f in e.get('funcoes', []) or []:
            if not PADRAO_FUNC.match(str(f)):
                problemas.append(f'{rot}: endereço de função fora do padrão ({f})')
            elif enderecos is not None and f not in enderecos:
                problemas.append(f'{rot}: função inexistente no decomp ({f})')
        if e.get('chave'):
            por_chave.setdefault(e['chave'], []).append((rot, e.get('marca')))

    for chave in sorted(por_chave):
        marcas = sorted({m for _, m in por_chave[chave]}, key=str)
        if len(marcas) > 1:
            quem = ', '.join(f'{r}={m}' for r, m in sorted(por_chave[chave]))
            problemas.append(f'contradição na chave {chave!r}: {quem}')

    citados = _citacoes(root)
    for c in sorted(citados):
        if c not in ids:
            problemas.append(f'{c}: órfão (citado em {", ".join(sorted(citados[c]))}) e ausente do índice')

    por_marca = {m: 0 for m in MARCAS}
    for e in ids.values():
        if e.get('marca') in por_marca:
            por_marca[e['marca']] += 1

    relatorio = []
    if decomp:
        nomeadas = [f for f in decomp if f.get('nome') and str(f.get('unidade') or '').startswith('@')]
        cobertas = {f for e in ids.values() for f in (e.get('funcoes') or [])}
        sem = sorted((f for f in nomeadas if f['a'] not in cobertas), key=lambda f: (-f.get('tam', 0), f['a']))
        relatorio.append(f'funções nomeadas do programa (unidade @): {len(nomeadas)}; '
                         f'com evidência: {len(nomeadas) - len(sem)}; sem evidência/dono: {len(sem)}')
        for f in sem[:15]:
            relatorio.append(f'  sem dono: {f["a"]} {f["nome"]} ({f.get("tam", 0)} bytes)')
    else:
        relatorio.append('decomp ausente: relatório de funções não gerado')

    return {'problemas': problemas, 'total': len(itens), 'por_marca': por_marca, 'relatorio': relatorio}


def texto(r):
    linhas = [f'evidências: {r["total"]}']
    linhas += [f'  {m}: {n}' for m, n in sorted(r['por_marca'].items())]
    linhas.append(f'problemas: {len(r["problemas"])}')
    linhas += [f'  ! {p}' for p in r['problemas']]
    linhas.append('relatório (informativo, não falha):')
    linhas += [f'  {x}' for x in r['relatorio']]
    return '\n'.join(linhas)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--check', action='store_true', help='código de saída 1 se houver problema')
    ap.add_argument('--root', default=str(Path(__file__).resolve().parents[2]))
    a = ap.parse_args(argv)
    r = auditar(a.root)
    print(texto(r))
    return 1 if (a.check and r['problemas']) else 0


if __name__ == '__main__':
    sys.exit(main())
