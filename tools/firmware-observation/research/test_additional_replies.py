from pathlib import Path
import sys,json,os
sys.path.insert(0,os.environ.get('CPU_EMULATION_DIR',str(Path(__file__).resolve().parents[1])))
from progbase_cpu import OriginalCPU,WRITE,BUFFER,PORT,VTABLE,SELF,UC_X86_REG_ECX,UC_X86_REG_EDX
EXE=Path(os.environ['PROGBASE_EXE']) if 'PROGBASE_EXE' in os.environ else next(Path('/workspace/attachments').rglob('Progbase.exe'))
class ReplyCPU(OriginalCPU):
 def __init__(self,result=0,ident=None):
  self.result=result;self.ident=ident;super().__init__(EXE,'contract');self.put32(VTABLE+0x28,BUFFER);self.put32(SELF+0x2b8,8000);self.put32(SELF+0x258,250)
 def hook(self,u,address,size,_):
  if address==WRITE:
   n=u.reg_read(UC_X86_REG_ECX);p=u.reg_read(UC_X86_REG_EDX);tx=bytes(u.mem_read(p,n))
   if tx in [bytes.fromhex('000909'),bytes.fromhex('000202')]:
    data=bytes([self.result]) if tx==bytes.fromhex('000909') else self.ident
    if data is None:raise ValueError('Identification was not provided')
    body=bytes([0x53,len(data)])+data;body+=bytes([sum(body)&255]);self.writes.append(tx);self.queue+=tx+body;self.events.append({'kind':'TX','hex':tx.hex(),'evidence':'OBS-COD'});self.events.append({'kind':'MODEL_REPLY','hex':body.hex(),'evidence':'INFERIDO synthetic values'});self.ret(1,4);return
  super().hook(u,address,size,_)
results=[]
for status in [0,1]:
 c=ReplyCPU(status);returned=c.call(0x9282b0);assert returned==status;results.append({'routine':'Cancflash','synthetic_status':status,'returned':returned,'wire':c.writes[0].hex(),'response':next(e['hex'] for e in c.events if e['kind']=='MODEL_REPLY'),'evidence':'OBS-COD acceptance; INFERIDO device data'})
c=ReplyCPU(ident=bytes.fromhex('63030b01'));returned=c.call(0x923da4,line='');results.append({'routine':'GetIdentification','returned':returned,'wire':[x.hex() for x in c.writes],'response':next(e['hex'] for e in c.events if e['kind']=='MODEL_REPLY'),'decoded_memory':bytes(c.u.mem_read(0x20004000,8)).hex(),'evidence':'OBS-COD acceptance; identification bytes are synthetic, not an observed ECU'})
Path(__file__).with_name('additional-replies.json').write_text(json.dumps(results,indent=2));print(json.dumps(results,indent=2))
