#!/usr/bin/env python3
"""Conferência independente das saídas contra hashes e evidência de AutoMatch."""
import csv, hashlib, json, pathlib, struct, zipfile
P=pathlib.Path(__file__).resolve().parent
manifest=json.load(open(P/'entradas.json'))
for r in manifest:
 if '!' in r['path']:
  path,member=r['path'].split('!',1)
  with zipfile.ZipFile(path) as z:b=z.read(member)
 else:b=pathlib.Path(r['path']).read_bytes()
 assert len(b)==r['bytes'] and hashlib.sha256(b).hexdigest()==r['sha256'],r['path']
rows=list(csv.DictReader(open(P/'automatches_por_no.csv')))
new=[r for r in rows if r['sessao'].startswith('N')]
assert len(rows)==17 and len(new)==3
p=next((P.parent/'novas/zip2').rglob('events_*.jsonl'))
epochs=[]
for l in open(p):
 e=json.loads(l)
 if e['type']=='autocal_native_automatch_epoch':epochs.append(e['data'])
assert len(epochs)==3
for r,e in zip(new,epochs):
 assert int(r['contador'])==e['afterCount']
 assert [round(float(r[f'K_{j:02d}'])*16384) for j in range(30)]==e['evidence']['afterRaw']
sessions=list(csv.DictReader(open(P/'sessoes.csv')))
assert len(sessions)==len({r['id'] for r in sessions})==99
assert sum(int(r['quadros']) for r in sessions)==202441
for r in sessions:
 assert float(r['curva_h'])<=float(r['gnv_h'])+1e-9
 assert float(r['curva_60s_h'])<=float(r['curva_h'])+1e-9
for r in csv.DictReader(open(P/'equivalencia_celulas.csv')):
 assert int(r['n_gnv'])>=15 and int(r['n_gasolina'])>=15
print(f'OK: {len(manifest)} hashes de entrada; 99 IDs únicos; 202441 quadros; 17 AutoMatches; 3 curvas novas iguais à evidência nativa nó a nó; exposições e mínimos de células válidos.')
