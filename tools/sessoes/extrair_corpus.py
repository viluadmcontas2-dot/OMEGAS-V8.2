"""Gera o corpus compacto das "Sessões Úteis" (Drive: Meu Drive/OMEGAS/Sessoes Uteis) para qualquer sessão futura.
Uso: python3 -I tools/sessoes/extrair_corpus.py <pasta com os pacotes extraídos> <pasta de saída>
Entrada: <pasta>/<pacote>/<dia>/<sessão>/{events_0001.jsonl[.json], session_summary.json}.
Saída (versionável, ~poucos MB): telemetria.csv.gz, eventos-calibracao.jsonl.gz, sessoes.json.
Só leitura dos dados: nada é inventado; campo ausente fica vazio (nunca 0)."""
import csv, glob, gzip, json, os, sys

COLS = ['sessao', 't_ms', 'rpm', 'gas_ms', 'petrol_ms', 'fuel_byte', 'fuel', 'state', 'map_bar', 'dyn', 'gas_bar', 'water_c', 'gas_c', 'sample_state']
KEEP = {'k_factor_batch_confirmed', 'autocal_native_snapshot', 'app_log'}

def sid(path):
    return os.path.basename(os.path.dirname(path))

def main(src, out):
    os.makedirs(out, exist_ok=True)
    files = sorted(glob.glob(os.path.join(src, '*', '*', '*', 'events_0001.jsonl*')))
    sessions, nrows = [], 0
    with gzip.open(os.path.join(out, 'telemetria.csv.gz'), 'wt', newline='') as tf, \
         gzip.open(os.path.join(out, 'eventos-calibracao.jsonl.gz'), 'wt') as ef:
        w = csv.writer(tf); w.writerow(COLS)
        for f in files:
            name = sid(f); n = 0; cng = 0
            for line in open(f, encoding='utf-8', errors='replace'):
                try: e = json.loads(line)
                except ValueError: continue
                t = e.get('type')
                if t == 'telemetry':
                    d = e['data']
                    if d.get('rpm') is None: continue
                    gas = d.get('gas_ms_diagnostic') if d.get('gas_pulse_present') else None
                    row = [name, e['recordedAtMs'], d.get('rpm'), gas, d.get('petrol_ms'), d.get('fuel_byte'), d.get('fuel'), d.get('state'),
                           d.get('load_bar'), d.get('dynamic_correction'), d.get('gas_pressure_abs_bar'), d.get('water_c'), d.get('gas_c'), d.get('sample_state')]
                    w.writerow(['' if v is None else v for v in row]); n += 1; nrows += 1
                    cng += d.get('fuel') == 'GNV'
                elif t in KEEP and (t != 'app_log' or any(k in json.dumps(e['data']) for k in ('K', 'mapa', 'curva', 'reset', 'Reset'))):
                    ef.write(json.dumps({'sessao': name, 'sequence': e.get('sequence'), 'recordedAtMs': e.get('recordedAtMs'), 'type': t, 'data': e['data']}, ensure_ascii=False) + '\n')
            summ = {}
            sp = os.path.join(os.path.dirname(f), 'session_summary.json')
            if os.path.exists(sp):
                try: summ = json.load(open(sp, encoding='utf-8'))
                except ValueError: pass
            sessions.append({'sessao': name, 'telemetria': n, 'ticks_gnv': cng, 'resumo': summ})
    json.dump({'formato': 'omegas-sessoes-uteis-corpus-v1', 'sessoes': sessions, 'linhas_telemetria': nrows}, open(os.path.join(out, 'sessoes.json'), 'w'), ensure_ascii=False)
    print('sessões', len(sessions), 'linhas', nrows)

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
