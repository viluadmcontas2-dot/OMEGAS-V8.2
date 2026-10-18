#!/usr/bin/env python3
"""Baixa o ProgBase.exe original (binário de terceiros; NÃO fica no repositório) e confere tamanho + SHA-256.
Idempotente: se o arquivo já existe e confere, não baixa; se estiver corrompido, baixa de novo; se o download não conferir, não deixa nada.
Uso: python3 tools/obter_progbase.py [--dest PASTA]   -> imprime o caminho do ProgBase.exe.
Só biblioteca padrão. Dados de referência: docs/mapa-de-fontes/progbase.json."""
import argparse, hashlib, json, os, sys, tempfile, urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
META = os.path.join(ROOT, 'docs', 'mapa-de-fontes', 'progbase.json')
NOME = 'ProgBase.exe'


def sha256_de(caminho):
    h = hashlib.sha256()
    with open(caminho, 'rb') as fh:
        for bloco in iter(lambda: fh.read(1 << 20), b''):
            h.update(bloco)
    return h.hexdigest()


def confere(caminho, tamanho, sha):
    return os.path.isfile(caminho) and os.path.getsize(caminho) == tamanho and sha256_de(caminho) == sha


def baixar(url, destino_dir, tamanho, sha):
    fd, tmp = tempfile.mkstemp(dir=destino_dir, prefix='.progbase-')
    try:
        with os.fdopen(fd, 'wb') as out, urllib.request.urlopen(url, timeout=120) as resp:
            while True:
                bloco = resp.read(1 << 20)
                if not bloco:
                    break
                out.write(bloco)
        if not confere(tmp, tamanho, sha):
            return None
        return tmp
    except Exception as erro:
        print(f'falha em {url}: {erro}', file=sys.stderr)
        return None


def main():
    meta = json.load(open(META, encoding='utf-8'))
    ap = argparse.ArgumentParser()
    ap.add_argument('--dest', default=os.environ.get('OMEGAS_CACHE', os.path.join(os.path.expanduser('~'), '.cache', 'omegas')))
    ap.add_argument('--url', action='append', help='sobrescreve as origens (testes)')
    ap.add_argument('--sha256', default=meta['sha256'])
    ap.add_argument('--bytes', type=int, default=meta['bytes'])
    args = ap.parse_args()
    os.makedirs(args.dest, exist_ok=True)
    alvo = os.path.join(args.dest, NOME)
    if confere(alvo, args.bytes, args.sha256):
        print(alvo)
        return 0
    urls = args.url or [o['url'] for o in meta['origens']]
    for url in urls:
        tmp = baixar(url, args.dest, args.bytes, args.sha256)
        if tmp:
            os.replace(tmp, alvo)
            print(alvo)
            return 0
        for sobra in os.listdir(args.dest):
            if sobra.startswith('.progbase-'):
                os.remove(os.path.join(args.dest, sobra))
    print('não consegui obter um ProgBase.exe que confira com o SHA-256 esperado', file=sys.stderr)
    return 1


if __name__ == '__main__':
    sys.exit(main())
