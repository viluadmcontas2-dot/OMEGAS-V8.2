"""Carrega o corpus compacto de tests/fixtures/sessoes-uteis (ver LEIA-ME.md)."""
import csv, gzip, json, os
BASE = os.path.join(os.path.dirname(__file__), '..', '..', 'tests', 'fixtures', 'sessoes-uteis')

def telemetria(somente_gnv=False):
    """Gera dicionários numéricos por amostra (campo ausente = None)."""
    with gzip.open(os.path.join(BASE, 'telemetria.csv.gz'), 'rt') as fh:
        for r in csv.DictReader(fh):
            if somente_gnv and r['fuel'] != 'GNV':
                continue
            out = {'sessao': r['sessao'], 'fuel': r['fuel'], 'state': r['state'], 'sample_state': r['sample_state']}
            for k in ('t_ms', 'rpm', 'gas_ms', 'petrol_ms', 'fuel_byte', 'map_bar', 'dyn', 'gas_bar', 'water_c', 'gas_c'):
                out[k] = float(r[k]) if r[k] != '' else None
            yield out

def eventos():
    with gzip.open(os.path.join(BASE, 'eventos-calibracao.jsonl.gz'), 'rt') as fh:
        for line in fh:
            yield json.loads(line)
