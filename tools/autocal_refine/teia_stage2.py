"""TEIA etapa 0b: reconstitui sessões que vieram dentro de zips (partes por byte e pacotes aninhados).
Valida contiguidade e sha256 das partes. Uso: python teia_stage2.py <staging>   (usa <staging>/_zips)
"""
import hashlib, json, os, shutil, sys, glob, re


def join_parts(zdir, stg):
    by = {}
    for pj in glob.glob(os.path.join(zdir, '*_parte_*', '**', 'parte.json'), recursive=True):
        j = json.load(open(pj))
        by.setdefault(j['sessionId'], []).append((j['part'], os.path.dirname(pj), j))
    for sid, parts in by.items():
        parts.sort(key=lambda x: x[0])
        chunks, pos, ok = [], 0, True
        for _, d, j in parts:
            for e in j['entries']:
                if e.get('source') != 'events_0001.jsonl':
                    continue
                raw = open(os.path.join(d, e['entry']), 'rb').read()
                if hashlib.sha256(raw).hexdigest() != e['sha256'] or e['fromByte'] != pos or len(raw) != e['toByte'] - e['fromByte']:
                    print('AVISO parte', j['part'], 'sha/contiguidade falhou'); ok = False
                pos = e['toByte']; chunks.append(raw)
        out = os.path.join(stg, sid); os.makedirs(out, exist_ok=True)
        open(os.path.join(out, 'events.jsonl'), 'wb').write(b''.join(chunks))
        print(sid, 'partes', len(parts), 'bytes', pos, 'integro' if ok else 'COM FALHAS')


def nested(zdir, stg):
    for ev in glob.glob(os.path.join(zdir, 'Sessaoutil', '**', 'events_0001.jsonl'), recursive=True):
        sid = os.path.basename(os.path.dirname(ev))
        out = os.path.join(stg, sid); os.makedirs(out, exist_ok=True)
        shutil.copyfile(ev, os.path.join(out, 'events.jsonl'))
        for f in ('manifest.json', 'session_summary.json', 'RESUMO.md'):
            p = os.path.join(os.path.dirname(ev), f)
            if os.path.exists(p): shutil.copyfile(p, os.path.join(out, f))
        print('sessaoutil', sid, os.path.getsize(ev))


if __name__ == '__main__':
    stg = sys.argv[1]; z = os.path.join(stg, '_zips')
    join_parts(z, stg); nested(z, stg)
