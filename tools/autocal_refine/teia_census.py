"""TEIA etapa 1: censo + extração de telemetria para parquet. Passivo.
Uso: python teia_census.py <staging> <saida_dir>
"""
import json, os, sys, collections
import polars as pl

TEL = ['rpm', 'load_bar', 'petrol_ms', 'petrol_2_ms_diagnostic', 'gas_ms_diagnostic', 'gas_2_ms_diagnostic', 'fuel',
       'water_c', 'gas_c', 'gas_pressure_abs_bar', 'pressure_diff_bar', 'dynamic_correction', 'unknown_raw_19',
       'sample_state', 'cell_row', 'cell_column', 'k_interpolated', 'plausible']


def main(stg, out):
    os.makedirs(out, exist_ok=True)
    rows, types, keys, sess = [], collections.Counter(), collections.defaultdict(collections.Counter), []
    for s in sorted(d for d in os.listdir(stg) if d.startswith('session_')):
        p = os.path.join(stg, s, 'events.jsonl')
        n = nt = 0
        t0 = t1 = None
        bad = 0
        for line in open(p, errors='replace'):
            try:
                e = json.loads(line)
            except Exception:
                bad += 1
                continue
            n += 1
            ty = e.get('type')
            types[ty] += 1
            ms = e.get('recordedAtMs')
            if ms:
                t0 = ms if t0 is None else min(t0, ms)
                t1 = ms if t1 is None else max(t1, ms)
            d = e.get('data') or {}
            if ty == 'telemetry':
                nt += 1
                r = {'session': s, 't': ms, 'seq': e.get('sequence')}
                for k in TEL:
                    r[k] = d.get(k)
                rows.append(r)
            if isinstance(d, dict) and len(keys[ty]) < 80:
                for k in d:
                    keys[ty][k] += 1
        sess.append(dict(session=s, events=n, telemetry=nt, bad=bad, dur_s=((t1 - t0) / 1000 if t0 else 0)))
    df = pl.DataFrame(rows, infer_schema_length=None)
    df.write_parquet(os.path.join(out, 'telemetry.parquet'))
    json.dump({'types': types, 'keys': {k: dict(v) for k, v in keys.items()}, 'sessions': sess},
              open(os.path.join(out, 'census.json'), 'w'), indent=1, default=str)
    print(df.shape, dict(types.most_common(25)))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
