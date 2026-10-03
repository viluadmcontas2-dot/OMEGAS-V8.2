"""Confere trechos mínimos contra a seção original; passivo/offline."""
import argparse,hashlib,json,pathlib
p=argparse.ArgumentParser();p.add_argument('section',type=pathlib.Path);p.add_argument('excerpts',type=pathlib.Path);p.add_argument('--pe',type=pathlib.Path);a=p.parse_args();b=a.section.read_bytes();d=json.loads(a.excerpts.read_text());assert hashlib.sha256(b).hexdigest()==d['sha256'];vma=int(d['vma'],16)
for x in d['excerpts']+d.get('constants',[]):
 off=int(x['offset_text'],16);expected=bytes.fromhex(x['bytes']);assert off==int(x['va'],16)-vma;assert b[off:off+len(expected)]==expected,x.get('id',x['va'])
print(json.dumps({'source_sha256':d['sha256'],'checked_excerpts':len(d['excerpts']),'checked_bytes':sum(len(bytes.fromhex(x['bytes'])) for x in d['excerpts']),'status':'PASS'},indent=2))

if a.pe:
 pe=a.pe.read_bytes();assert hashlib.sha256(pe).hexdigest()==d['pe_sha256']
 for x in d['rtti']:
  off=int(x['pe_file_offset'],16);assert pe[off:off+len(x['name'])]==x['name'].encode();assert pe[off-8:off]==bytes.fromhex(x['preceding_bytes']),x['name']
 print(json.dumps({'checked_rtti':len(d['rtti']),'pe_status':'PASS'}))
