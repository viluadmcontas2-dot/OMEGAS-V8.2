#!/usr/bin/env python3
"""Baixa um arquivo do Drive pelo link direto (arquivo ou pasta compartilhada por link), idempotente.
Uso: baixar_drive.py <id> [--dest PASTA] [--nome ARQ] [--bytes N] [--sha256 H]
- Se o arquivo já existe e confere (tamanho e/ou SHA-256 informados), não baixa de novo.
- Se o Drive devolver a página de login/aviso (HTML) em vez do arquivo, falha com mensagem clara e não deixa sobras.
Só biblioteca padrão. Imprime o caminho do arquivo."""
import argparse, hashlib, os, sys, tempfile, urllib.request

URL = 'https://drive.usercontent.google.com/download?id={id}&export=download&confirm=t'


def sha256_de(caminho):
    h = hashlib.sha256()
    with open(caminho, 'rb') as fh:
        for bloco in iter(lambda: fh.read(1 << 20), b''):
            h.update(bloco)
    return h.hexdigest()


def confere(caminho, tamanho, sha):
    if not os.path.isfile(caminho):
        return False
    if tamanho is not None and os.path.getsize(caminho) != tamanho:
        return False
    if sha is not None and sha256_de(caminho) != sha:
        return False
    return tamanho is not None or sha is not None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('id')
    ap.add_argument('--dest', default=os.environ.get('OMEGAS_CACHE', os.path.join(os.path.expanduser('~'), '.cache', 'omegas')))
    ap.add_argument('--nome')
    ap.add_argument('--bytes', type=int)
    ap.add_argument('--sha256')
    ap.add_argument('--url', help='sobrescreve a URL (testes)')
    a = ap.parse_args()
    os.makedirs(a.dest, exist_ok=True)
    alvo = os.path.join(a.dest, a.nome or a.id)
    if confere(alvo, a.bytes, a.sha256):
        print(alvo)
        return 0
    fd, tmp = tempfile.mkstemp(dir=a.dest, prefix='.drive-')
    try:
        with os.fdopen(fd, 'wb') as out, urllib.request.urlopen(a.url or URL.format(id=a.id), timeout=180) as resp:
            primeiro = resp.read(1 << 20)
            if primeiro.lstrip()[:15].lower().startswith((b'<!doctype html', b'<html')):
                print('o Drive devolveu uma página HTML (login/aviso): o arquivo não está compartilhado por link', file=sys.stderr)
                return 2
            out.write(primeiro)
            while True:
                bloco = resp.read(1 << 20)
                if not bloco:
                    break
                out.write(bloco)
        if (a.bytes is not None and os.path.getsize(tmp) != a.bytes) or (a.sha256 and sha256_de(tmp) != a.sha256):
            print('o arquivo baixado não confere com o tamanho/SHA-256 esperado', file=sys.stderr)
            return 3
        os.replace(tmp, alvo)
        print(alvo)
        return 0
    except Exception as erro:
        print(f'falha no download: {erro}', file=sys.stderr)
        return 1
    finally:
        if os.path.exists(tmp):
            os.remove(tmp)


if __name__ == '__main__':
    sys.exit(main())
