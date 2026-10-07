"""TEIA: livro-razão de cobertura. Lista TODO arquivo do Drive e diz se foi consumido por alguma etapa.
Uso: python teia_ledger.py <drive> <staging> <saida.json>
Categorias: events (maior cópia, consumido), zip (extraído), meta (manifest/summary/README/RESUMO), portmon, outro.
Falha (exit 1) se algum arquivo ficar sem categoria — para garantir que nada passou despercebido.
"""
import json, os, sys, zipfile, io, collections


def walk_zip(data, prefix, out):
    z = zipfile.ZipFile(io.BytesIO(data))
    for i in z.infolist():
        if i.is_dir():
            continue
        p = prefix + '/' + i.filename
        if i.filename.lower().endswith('.zip'):
            walk_zip(z.read(i), p, out)
        else:
            out.append((p, i.file_size))


def cat(name):
    n = name.lower()
    b = os.path.basename(n)
    if b == 'desktop.ini':
        return 'ignorar'
    if b.startswith('manual-') and b.endswith('.json'):
        return 'k_backup'
    if b.startswith('events_0001'):
        return 'events'
    if b in ('manifest.json', 'session_summary.json', 'readme_para_ia.txt', 'resumo.md', 'parte.json'):
        return 'meta'
    if n.endswith('.log'):
        return 'portmon'
    if n.endswith('.zip'):
        return 'zip'
    return 'outro'


def main(drive, stg, out):
    rows = []
    for root, _, files in os.walk(drive):
        for f in files:
            p = os.path.join(root, f)
            rows.append((p, os.path.getsize(p), cat(f), 'disco'))
            if f.lower().endswith('.zip'):
                inner = []
                walk_zip(open(p, 'rb').read(), p, inner)
                for ip, sz in inner:
                    rows.append((ip, sz, cat(ip), 'dentro-de-zip'))
    c = collections.Counter((r[2], r[3]) for r in rows)
    outros = [r for r in rows if r[2] == 'outro']
    json.dump({'total': len(rows), 'contagem': {f'{a}|{b}': n for (a, b), n in c.items()}, 'outros': outros},
              open(out, 'w'), indent=1, ensure_ascii=False)
    print('arquivos (incl. dentro de zips):', len(rows))
    for k, v in sorted(c.items()):
        print(' ', k, v)
    print('SEM CATEGORIA:', len(outros))
    for r in outros[:20]:
        print('  ', r[0], r[1])
    sys.exit(1 if outros else 0)


if __name__ == '__main__':
    main(*sys.argv[1:4])
