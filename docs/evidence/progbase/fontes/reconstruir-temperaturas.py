"""Reconstrucao passiva de tabelas e verificacao aritmetica. Nao executa ProgBase/ECU."""
import argparse,hashlib,json,pathlib,struct,math
from fractions import Fraction as F

TEXT_SHA='979aef013c6b507cf3153c9f7d374261a93ad1c1fa3592de033681d79bccc772'
DATA_SHA='4ef7cfe00254f2ff80533da88dd0633d33a0a761f903dff3c5249ef2e5e9d9b0'
PE_SHA='8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4'
RED=[(0,0x430a46,0xa5c830),(1,0x430ae1,0xa5c9b0),(2,0x430b7c,0xa5ca1c),(3,0x430c17,0xa5cc30),(4,0x430cb2,0xa5cba4),(5,0x430d4d,0xa5cc30)]
GAS=[(0,0x430fed,0xa5c830),(1,0x430eb7,0xa5cd44),(2,0x430f52,0xa5cc30)]
RTTI={'FLAG_CONF1':0x6708f1,'TIPO_SENSORE_TEMPERATURA':0x6712ed,'PARAMETRI_TEMP':0x67136e,'ECU_TEMP':0x671383}
SPANS=[('identification',0x923da4,0x923fd8),('family_loader',0x42b8d4,0x42b964),('family_offline',0x42b964,0x42b9c9),('red_initializer',0x430a0c,0x430dfc),('gas_initializer',0x430dfc,0x43108a),('red_helper',0x42a52c,0x42a692),('gas_helper',0x42a788,0x42a8ee),('trunc_helper',0xa45a58,0xa45a88),('vector_copy',0x978dcc,0x978ec3),('temperature_caption',0x50bce1,0x50be0b),('raw_zero_dispatch',0x50b694,0x50b781)]

def sha(b):return hashlib.sha256(b).hexdigest()
def read_checked(p,h):
 b=p.read_bytes()
 if sha(b)!=h:raise ValueError('SHA diferente: '+str(p))
 return b
def params(text,entry,base):
 out=[]
 for i in range(5):
  o=entry-0x401000+10*i
  assert text[o:o+2]==b'\xc7\x05'
  assert struct.unpack_from('<I',text,o+2)[0]==base+4*i
  out.append(struct.unpack_from('<i',text,o+6)[0])
 return out
def convert(raw,nodes,origin,step):
 """Leitura da busca 42A53E/42A79A e ramos de interpolacao x87."""
 i=0
 while raw<nodes[i] and i<len(nodes)-1:i+=1
 if i<=0:
  left,right=nodes[0],nodes[1]
  fraction=F(1,2) if left==right else F(raw-left,left-right)
 elif i>len(nodes)-1:
  left,right=nodes[-1],nodes[-2]
  fraction=F(1,2) if left==right else F(raw-left,right-left)
 else:
  left,right=nodes[i],nodes[i-1]
  fraction=F(1,2) if left==right else F(raw-left,right-left)
 return (F(i)-fraction)*step+origin
def project(raw,value):
 return None if raw==0 or value==0 else int(value)

def inspect(text,data,pe):
 assert pe[0x600:0x600+len(text)]==text
 assert text[0x50d214-0x401000:0x50d218-0x401000]==bytes(4)
 cases=[]
 for group,descriptors,base in [('redutor',RED,0xaecfb8),('motor',GAS,0xaecfcc)]:
  for selector,entry,source in descriptors:
   n,origin,step,denom,scale=params(text,entry,base)
   off=source-0xa51000;source_bytes=data[off:off+4*n];assert len(source_bytes)==4*n
   assert struct.pack('<I',source) in text[entry-0x401000:entry-0x401000+80]
   xs=struct.unpack('<'+'f'*n,source_bytes)
   exact=[F(255)*scale*F(x)/(denom+scale*F(x)) for x in xs]
   nodes=[int(x) for x in exact]
   # Float64 e racional exato derivados dos mesmos float32: estabilidade do inteiro.
   binary64=[math.trunc(255*scale*x/(denom+scale*x)) for x in xs]
   assert nodes==binary64
   assert all(nodes[i]>=nodes[i+1] for i in range(n-1))
   vals=[convert(r,nodes,origin,step) for r in range(256)]
   assert all(vals[r]>=vals[r+1] for r in range(255))
   # Cada primeiro no distinto (sem par plano) deve retornar sua temperatura de grade.
   for i,node in enumerate(nodes):
    if nodes.index(node)==i and ((i==0 and nodes[0]!=nodes[1]) or i>0):assert convert(node,nodes,origin,step)==origin+i*step
   serialized=';'.join(str(v) for v in vals).encode()
   cases.append({'group':group,'selector':selector,'entry_va':hex(entry),'source_va':hex(source),'source_data_offset':hex(off),'source_float32_bytes':4*n,'source_float32_sha256':sha(source_bytes),'count':n,'origin':origin,'step':step,'denominator_constant':denom,'numerator_scale':scale,'nodes':nodes,'mapping_256_rational_sha256':sha(serialized),'duplicate_pairs':[i for i in range(1,n) if nodes[i]==nodes[i-1]],'sample_values':{str(r):str(vals[r]) for r in [0,1,2,8,40,41,68,75,128,252,253,254,255]},'closest_integer_boundary':float(min(abs(x-round(x)) for x in exact))})
 # Caminho custom: valores padrao preservados na .data; identicos ao LN SC138/139.
 cfg=list(struct.unpack_from('<5i',data,0xa5cdd8-0xa51000));nodes=list(struct.unpack_from('<30i',data,0xa5cdec-0xa51000))
 assert cfg==[18,92,5,-20,30]
 vals=[convert(r,nodes,cfg[3],cfg[2]) for r in range(256)]
 assert all(vals[r]>=vals[r+1] for r in range(255))
 cases.append({'group':'motor-custom-default-equal-LN','selector':'FLAG_CONF1[1] & 0x0004 != 0','count':cfg[4],'origin':cfg[3],'step':cfg[2],'cfg_bytes0_1':'12 5C','combined_parameter':(cfg[0]<<8)+cfg[1],'nodes':nodes,'mapping_256_rational_sha256':sha(';'.join(str(v) for v in vals).encode()),'sample_values':{str(r):str(vals[r]) for r in [0,1,2,8,40,41,68,75,128,252,253,254,255]}})
 for case in cases:
  vals=[convert(r,case['nodes'],case['origin'],case['step']) for r in range(256)]
  display=[project(r,v) for r,v in enumerate(vals)]
  assert display[0] is None
  for r,v in enumerate(vals):
   d=display[r]
   if r and v:
    assert d==math.trunc(v) and abs(F(d)-v)<1
   else:assert d is None
  case['mapping_256_rational_semicolon']=';'.join(str(v) for v in vals)
  case['caption_256_integer_or_null_json']=json.dumps(display,separators=(',',':'))
  case['caption_256_integer_or_null_sha256']=sha(json.dumps(display,separators=(',',':')).encode())
  case['caption_absence_raw_values']=[r for r,d in enumerate(display) if d is None]
  case['sample_caption_integers']={str(r):display[r] for r in [0,1,2,8,40,41,68,75,128,195,252,253,254,255]}
 rtti=[]
 for name,off in RTTI.items():
  assert pe[off:off+len(name)]==name.encode(),name
  rtti.append({'name':name,'pe_file_offset':hex(off),'preceding_bytes':pe[off-8:off].hex(' ').upper()})
 spans=[{'id':name,'va_start':hex(a),'va_end_exclusive':hex(z),'bytes':z-a,'sha256':sha(text[a-0x401000:z-0x401000])} for name,a,z in SPANS]
 return {'schema':'progbase.temperature-reconstruction.v1','sources':{'text_sha256':TEXT_SHA,'data_sha256':DATA_SHA,'pe_sha256':PE_SHA},'cases':cases,'rtti':rtti,'code_spans':spans,'verification':{'cases':len(cases),'raw_inputs_per_case':256,'rational_conversions':256*len(cases),'caption_projections':256*len(cases),'builtin_integer_nodes':sum(x['count'] for x in cases[:-1]),'checks':['rational/float64 integer tables agree','monotone tables and all256 conversions','distinct-node interpolation identities','RTTI names and full source hashes','finite rational caption zero/Trunc projections'],'native_execution':'NOT_RUN: ELF32 probe rejected with Exec format error in this runtime','status':'PASS_STATIC_RECONSTRUCTION_ONLY'},'limits':['Racional exato modela a aritmetica lida; nao e replay nativo nem prova fisica.','Precisao x87/Double final nao foi emulada; Trunc do caption e provado no codigo; locale/texto literal da VCL nao foi reproduzido.','Raw0 e bypass em Timer1; zero calculado tambem vai a ausencia. Projecao finita racional, sem alegacao de replay nativo/NaN.','Tipos redutor6..31 deixam tabela anterior intacta; nao ha fallback novo no switch.','Parametro custom count/monotonia pode diferir em outra ECU; nao generalizar a tabela LN.']}

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('text',type=pathlib.Path);p.add_argument('data',type=pathlib.Path);p.add_argument('pe',type=pathlib.Path);p.add_argument('--log',type=pathlib.Path);p.add_argument('--parser',type=pathlib.Path);p.add_argument('--excerpts',type=pathlib.Path);a=p.parse_args()
 result=inspect(read_checked(a.text,TEXT_SHA),read_checked(a.data,DATA_SHA),read_checked(a.pe,PE_SHA))
 if a.excerpts:
  d=json.loads(a.excerpts.read_text());text=a.text.read_bytes()
  assert d['text_sha256']==TEXT_SHA and int(d['vma'],16)==0x401000
  for e in d['excerpts']:
   off=int(e['offset_text'],16);b=bytes.fromhex(e['bytes'])
   assert off==int(e['va'],16)-0x401000 and text[off:off+len(b)]==b,e['id']
  result['excerpt_verification']={'count':len(d['excerpts']),'bytes':sum(len(bytes.fromhex(e['bytes'])) for e in d['excerpts']),'status':'PASS_BYTE_MATCH'}
 if a.log:
  import importlib.util,sys,collections
  if not a.parser:raise ValueError('--parser necessario com --log')
  expected='43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64'
  if sha(a.log.read_bytes())!=expected:raise ValueError('LOG diferente')
  parser_bytes=a.parser.read_bytes();blob=hashlib.sha1(b'blob '+str(len(parser_bytes)).encode()+b'\0'+parser_bytes).hexdigest()
  if blob!='17252b0a06e6091ae77d0d44843a8902c32d2172':raise ValueError('Parser diferente do SHA remoto pinado')
  spec=importlib.util.spec_from_file_location('ln_parser',a.parser);mod=importlib.util.module_from_spec(spec);sys.modules[spec.name]=mod;spec.loader.exec_module(mod)
  selected=[];setting_writes=[];flag_second=[];n=0
  for tx in mod.parse(a.log):
   n+=1;q=bytes.fromhex(tx.request_hex);r=bytes.fromhex(tx.response_hex);e=r[len(q):]
   if len(q)>=3 and q[1:3] in [bytes.fromhex(x) for x in ['86 00','8A 00','8B 00']] and q[0] in [0x12,0x13,0x14,0x32,0x33,0x34,0x35,0x36,0x37]:setting_writes.append({'seq':tx.sequence,'request':tx.request_hex})
   if tx.request_hex.startswith(('00 02','09 86 00','29 8A 00','29 8B 00','29 03 00','35 03 00')) or tx.sequence in [6073,6109]:
    assert r[:len(q)]==q and len(e)==e[1]+3 and sum(e[:-1])%256==e[-1]
    selected.append({'seq':tx.sequence,'idx':tx.request_index,'request':tx.request_hex,'response':tx.response_hex})
    if tx.request_hex.startswith('29 03 00'):flag_second.append(int.from_bytes(e[4:6],'little'))
    if tx.request_hex.startswith('35 03 00'):flag_second.append(int.from_bytes(q[5:7],'little'))
  assert n==39517 and set(flag_second)=={0x1051}
  assert not setting_writes
  result['ln_observations']={'log_sha256':expected,'parser_git_blob':blob,'transactions':n,'selected':selected,'observed_FLAG_CONF1_second_values':['0x1051'],'custom_temperature_bit_0004_observed':False,'targeted_setting_writes':setting_writes,'limits':'Valores observados e caminho estatico; nao e snapshot de memoria/UI. Ausencia de writer nao prova imutabilidade na ECU.'}
 print(json.dumps(result,ensure_ascii=False,indent=2))
