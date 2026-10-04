"""Prepara os dados do render: snapshot e telemetria REAIS de fixtures/autocal/real (resto é sintético no mock-bridge.js)."""
import gzip
import json
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.environ.get('TMPDIR', '/tmp'), 'omegas-render-data.json')
REAL = os.path.join(ROOT, 'fixtures', 'autocal', 'real')

automatch = json.load(gzip.open(os.path.join(REAL, 'automatch_2026-10-01_1301.json.gz')))
reference = json.load(gzip.open(os.path.join(REAL, 'ref_2026-10-01_1719.json.gz')))
snapshot = automatch['snapshots'][-1]
telemetry = reference['telemetry']
start = next(i for i in range(len(telemetry) - 400) if all(telemetry[j]['rpm'] > 1200 for j in range(i, i + 60)))
frames = telemetry[start:start + 700]
t0 = frames[0]['t']
for frame in frames:
    frame['dt'] = frame['t'] - t0
json.dump({'snapshot': snapshot, 'frames': frames, 'label_snap': automatch['label'], 'label_tel': reference['label'], 'start': start}, open(OUT, 'w'))
print(OUT)
