"""TEIA etapa 0: normaliza o Drive em um staging local (fora do repo). Passivo, só leitura na origem.

Para cada pasta session_*: escolhe a MAIOR cópia de events_0001.jsonl* e grava staging/<sessao>/events.jsonl
(+ manifest/summary). Extrai .zip recursivamente (zip dentro de zip) em staging/_zips/<nome>.
Uso: python teia_stage.py <pasta_drive> <pasta_staging>
"""
import io, json, os, re, shutil, sys, zipfile


def biggest_events(folder):
    c = [f for f in os.listdir(folder) if f.startswith('events_0001')]
    if not c:
        return None
    return max(c, key=lambda f: os.path.getsize(os.path.join(folder, f)))


def unzip_rec(data, dest, depth=0, log=None):
    try:
        z = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile:
        log.append(('bad', dest))
        return
    for i in z.infolist():
        if i.is_dir():
            continue
        name = re.sub(r'[<>:"|?*]', '_', i.filename).lstrip('/')
        raw = z.read(i)
        if name.lower().endswith('.zip') and depth < 6:
            unzip_rec(raw, os.path.join(dest, name[:-4]), depth + 1, log)
        else:
            out = os.path.join(dest, name)
            os.makedirs(os.path.dirname(out), exist_ok=True)
            with open(out, 'wb') as fh:
                fh.write(raw)


def main(src, dst):
    os.makedirs(dst, exist_ok=True)
    log, seen = [], {}
    for root, dirs, files in os.walk(src):
        base = os.path.basename(root)
        if base.startswith('session_') and any(f.startswith('events_0001') for f in files):
            best = biggest_events(root)
            size = os.path.getsize(os.path.join(root, best))
            if base in seen and seen[base] >= size:
                log.append(('dup_menor', root))
                continue
            seen[base] = size
            d = os.path.join(dst, base)
            os.makedirs(d, exist_ok=True)
            shutil.copyfile(os.path.join(root, best), os.path.join(d, 'events.jsonl'))
            for f in ('manifest.json', 'session_summary.json', 'RESUMO.md'):
                if f in files:
                    shutil.copyfile(os.path.join(root, f), os.path.join(d, f))
        for f in files:
            if f.lower().endswith('.zip'):
                with open(os.path.join(root, f), 'rb') as fh:
                    unzip_rec(fh.read(), os.path.join(dst, '_zips', f[:-4]), 0, log)
    json.dump({'sessoes': len(seen), 'log': log}, open(os.path.join(dst, '_stage_log.json'), 'w'), indent=1)
    print('sessoes:', len(seen), 'avisos:', len(log))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
