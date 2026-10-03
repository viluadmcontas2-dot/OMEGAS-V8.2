"""Validação passiva/offline do LN original. Nunca acessa ECU, USB ou rede."""
import argparse,collections,hashlib,importlib.util,json,pathlib,sys
EXPECTED='43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64'
def validate(log,parser):
 spec=importlib.util.spec_from_file_location('ln_parser',parser);mod=importlib.util.module_from_spec(spec);sys.modules[spec.name]=mod;spec.loader.exec_module(mod)
 digest=mod.sha256(log)
 if digest!=EXPECTED:raise ValueError('Fonte diferente do LN permitido: '+digest)
 tx=list(mod.parse(log));valid=[];excluded=[];request_invalid=[]
 for t in tx:
  q=bytes.fromhex(t.request_hex);r=bytes.fromhex(t.response_hex);e=r[len(q):]
  if len(q)>1 and sum(q[:-1])%256!=q[-1]:request_invalid.append(t.sequence)
  if r[:len(q)]!=q or len(e)<3 or len(e)!=e[1]+3 or sum(e[:-1])%256!=e[-1]:
   excluded.append({'seq':t.sequence,'idx':t.request_index,'request':t.request_hex,'response':t.response_hex});continue
  valid.append({'seq':t.sequence,'idx':t.request_index,'request':t.request_hex,'status':e[0],'payload':e[2:-1].hex(' ').upper()})
 counts=collections.Counter(t.request_hex for t in tx);states=[];kchanges=[];last_state=None;last_k=None
 for t in valid:
  p=bytes.fromhex(t['payload'])
  if t['request']=='48 0B 53' and p[12:14]!=last_state:states.append(t);last_state=p[12:14]
  if t['request']=='29 61 01 8B' and p!=last_k:kchanges.append(t);last_k=p
 samples=[t for t in valid if t['seq'] in {20201,24671,27874,30178,30096,30149,30196,31338,31483,32238}]
 brackets=[]
 for lo,hi in [(24569,24700),(27778,27900),(30000,30196)]:
  brackets.append([t for t in valid if lo<=t['seq']<=hi and t['request'].startswith(('29 5B 01','29 5C 01','29 5D 01','29 5E 01','29 5F 01','29 60 01','29 61 01','29 62 01','29 63 01','29 6F 01','29 70 01','48 0B','09 74 01'))])
 result={'schema':'progbase.ln-validation.v1','source_drive_id':'10s07RSG4Clg1wC0JclzL1azHKIJ7UZE0','log_sha256':digest,'log_bytes':log.stat().st_size,'transactions':len(tx),'valid_framed_responses':len(valid),'invalid_request_checksums':request_invalid,'excluded_from_frame_counts':excluded,'status_counts':dict(collections.Counter(hex(t['status']) for t in valid)),'nak_payload_counts':dict(collections.Counter(t['payload'] for t in valid if t['status']==202)),'request_counts':dict(sorted(counts.items())),'compact_status_changes':states,'mul_act_changes':kchanges,'targeted_samples':samples,'epoch_polling_brackets':brackets,'limits':['request_timestamp é 0.0 neste arquivo; não prova tempo de parede/cadência.','seq inclui sonda inicial; idx é IRP_MJ_WRITE.','Leituras são sequenciais e não atômicas; bracket não localiza instante interno da ECU.','A captura termina durante a resposta da última telemetria.','Nenhuma ausência no corpus prova impossibilidade do comando no firmware.']}
 assert len(tx)==39517 and len(valid)==39515 and not request_invalid
 assert result['status_counts']=={'0x53':36016,'0xca':3499}
 assert [t['seq'] for t in excluded]==[1,39517]
 assert counts['02 24 04 08 32']==0 and not any(k.startswith('14 61 01') for k in counts)
 return result
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('log',type=pathlib.Path);p.add_argument('--parser',type=pathlib.Path,required=True);a=p.parse_args();print(json.dumps(validate(a.log,a.parser),ensure_ascii=False,indent=2))
