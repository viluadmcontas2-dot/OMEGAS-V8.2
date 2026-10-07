"""TEIA: extrai todos os autocal_native_snapshot (e o 'after' das ações) de TODAS as sessões do staging.
MUL_ACT sempre decodificado do rawPayloadHex (30×u16 LE), independente do status do app.
Uso: python teia_snaps.py <staging> <saida_dir>
"""
import json, glob, os, pickle, struct, sys
import numpy as np

KEYS = dict(pm='MNFLD_PRESS_BUF', pt='PETR_INJ_TBUF', gm='MNFLD_PRESS_BUF_GAS', gt='PETR_INJ_TBUF_GAS',
            gmp='MNFLD_PRESS_BUF_GAS_PREV', gtp='PETR_INJ_TBUF_GAS_PREV', nup='NUM_BUF_UPD_PETR', nug='NUM_BUF_UPD_GAS',
            thd='MNFLD_PRESS_THD', az_p='ACQUIRED_ZONES_PETROL', az_g='ACQUIRED_ZONES_GAS', en='AUTO_CAL_ENABLE',
            nam='NUM_AUTOMATCH_EXECUTED')


def rec(s, t, d, src):
    F = {x['key']: x for x in d.get('fields', [])}
    r = dict(s=s, t=t, src=src)
    for k, key in KEYS.items():
        r[k] = list(F[key].get('rawValues') or []) if key in F else []
    h = F.get('MUL_ACT', {}).get('rawPayloadHex')
    r['mul'] = list(struct.unpack('<%dH' % (len(h) // 4), bytes.fromhex(h))) if h else []
    return r


def main(stg, out):
    R = []
    for sd in sorted(glob.glob(os.path.join(stg, 'session_*'))):
        s = os.path.basename(sd)
        for line in open(os.path.join(sd, 'events.jsonl'), errors='replace'):
            if 'autocal_native' not in line:
                continue
            try:
                e = json.loads(line)
            except Exception:
                continue
            d = e.get('data') or {}
            if e.get('type') == 'autocal_native_snapshot':
                R.append(rec(s, d.get('capturedAtMs') or e['recordedAtMs'], d, 'snapshot'))
            elif e.get('type') == 'autocal_native_action' and isinstance(d.get('after'), dict) and d['after'].get('fields'):
                R.append(rec(s, d.get('finishedAtMs') or e['recordedAtMs'], d['after'], 'after:' + str(d.get('action'))))
    R.sort(key=lambda r: (r['s'], r['t']))
    pickle.dump(R, open(os.path.join(out, 'snaps.pkl'), 'wb'))
    M = [r for r in R if len(r['mul']) == 30]
    np.save(os.path.join(out, 'mulact.npy'), np.array([r['mul'] for r in M]))
    json.dump([(r['s'], r['t']) for r in M], open(os.path.join(out, 'mulact_idx.json'), 'w'))
    print('registros', len(R), 'sessoes', len({r['s'] for r in R}), 'com MUL_ACT', len(M))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
