"""Higieniza cópias crescentes de events_0001.jsonl* no Drive.
Mantém a MAIOR por pasta; só marca para apagar a menor que seja PREFIXO BYTE-A-BYTE da maior.
Uso: python teia_dedup.py <pasta_drive> [--apply]   (sem --apply = dry-run, lista o que apagaria)
"""
import os, sys


def is_prefix(small, big):
    with open(small, 'rb') as a, open(big, 'rb') as b:
        while True:
            x = a.read(1 << 20)
            if not x:
                return True
            if x != b.read(len(x)):
                return False


def main(src, apply):
    keep = dele = nope = 0
    freed = 0
    for root, _, files in os.walk(src):
        ev = [f for f in files if f.startswith('events_0001')]
        if len(ev) < 2:
            continue
        sz = {f: os.path.getsize(os.path.join(root, f)) for f in ev}
        big = max(ev, key=lambda f: sz[f])
        keep += 1
        for f in ev:
            if f == big:
                continue
            p = os.path.join(root, f)
            if sz[f] <= 400 or is_prefix(p, os.path.join(root, big)):
                dele += 1
                freed += sz[f]
                if apply:
                    os.remove(p)
            else:
                nope += 1
                print('NAO-PREFIXO (mantido):', p)
    print(f'pastas={keep} apagaveis={dele} nao_prefixo={nope} liberado_MB={freed/1e6:.0f} apply={apply}')


if __name__ == '__main__':
    main(sys.argv[1], '--apply' in sys.argv)
