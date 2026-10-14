"""Execute original PE32 flash routines against an in-memory serial seam.
No Windows process, physical port, socket, or ECU is opened.
OBS-COD: actual SendByte/SendChecksum/Progflash/SendS19flash instructions.
INFERIDO: contract peer supplies host-expected replies; no flash decryption.
DESCONHECIDO: strict peer has no observed bootloader reply.
"""
from pathlib import Path
import struct,hashlib,json
import pefile
from unicorn import Uc,UC_ARCH_X86,UC_MODE_32,UC_HOOK_CODE,UC_HOOK_MEM_INVALID
from unicorn.x86_const import *
SELF=0x20001000;PORT=0x20002000;VTABLE=0x20003000;LINE=0x20004000
STACK=0x100ff000;STOP=0x30001000
BUFFER=0x30000000;WRITE=0x30000010;READ=0x30000020;CLOSE=0x30000030
class UnknownBehavior(Exception):pass
class OriginalCPU:
 def __init__(self,exe,profile='strict',fault=None):
  self.profile=profile;self.fault=fault;self.queue=bytearray();self.events=[];self.writes=[];self.context={};self.blocked=None
  self.binary_sha256=hashlib.sha256(Path(exe).read_bytes()).hexdigest()
  if self.binary_sha256!='8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4':raise ValueError('This emulator is pinned to the supplied Progbase.exe SHA-256')
  pe=pefile.PE(str(exe),fast_load=True);self.u=Uc(UC_ARCH_X86,UC_MODE_32)
  for address,size in [(0,0x10000),(pe.OPTIONAL_HEADER.ImageBase,(pe.OPTIONAL_HEADER.SizeOfImage+4095)&~4095),(0x10000000,0x100000),(0x20000000,0x100000),(0x30000000,0x10000)]:self.u.mem_map(address,size)
  self.u.mem_write(pe.OPTIONAL_HEADER.ImageBase,pe.get_memory_mapped_image());self.put32(SELF+0x40,PORT);self.put32(PORT,VTABLE)
  for off,addr in [(0x58,BUFFER),(0x30,WRITE),(0x2c,READ),(0x24,CLOSE)]:self.put32(VTABLE+off,addr)
  self.u.mem_write(SELF+0x2c0,b'\1');self.u.mem_write(0x30000000,b'\xc3'*0x10000)
  for address in [0xa4a204,BUFFER,WRITE,READ,CLOSE,STOP]:self.u.hook_add(UC_HOOK_CODE,self.hook,begin=address,end=address)
 def put32(self,a,v):self.u.mem_write(a,struct.pack('<I',v&0xffffffff))
 def get32(self,a):return struct.unpack('<I',self.u.mem_read(a,4))[0]
 def ret(self,value=None,extra=0):
  if value is not None:self.u.reg_write(UC_X86_REG_EAX,value)
  sp=self.u.reg_read(UC_X86_REG_ESP);pc=self.get32(sp);self.u.reg_write(UC_X86_REG_ESP,sp+4+extra);self.u.reg_write(UC_X86_REG_EIP,pc)
 def hook(self,u,address,size,_):
  if address==STOP:u.emu_stop();return
  if address==0xa4a204:self.ret();return # Delphi exception registration only, not flash behavior
  if address==BUFFER:self.ret(1);return
  if address==CLOSE:self.ret(1);return
  n=u.reg_read(UC_X86_REG_ECX);ptr=u.reg_read(UC_X86_REG_EDX)
  if address==WRITE:
   tx=bytes(u.mem_read(ptr,n));self.writes.append(tx);self.events.append(dict(kind='TX',hex=tx.hex(),evidence='OBS-COD',**self.context));echo=bytearray(tx)
   if self.fault=='echo' and echo:echo[0]^=1
   self.queue+=echo
   if self.profile=='contract':
    if tx==b'\x93\x93':reply=b'\xa7\xa7'
    elif tx==b'\x85\x85':reply=b'\x53\x53'
    elif tx.startswith(b'S'):reply=b'\x53\x53' if tx[:2]==b'S9' else b'\xa7\xa7'
    else:raise UnknownBehavior('No contract for transmitted command '+tx.hex())
    if self.fault=='ack':reply=b'\xca\xca'
    elif self.fault=='unstable_ack':reply=b'\xa7\x53'
    self.queue+=reply
   self.ret(1,4);return
  if address==READ:
   if len(self.queue)<n:
    self.blocked=dict(state='DESCONHECIDO',reason='Bootloader reply not observed; no synthetic ACK in strict mode',requested_bytes=n,**self.context);u.emu_stop();return
   data=bytes(self.queue[:n]);del self.queue[:n];u.mem_write(ptr,data);self.events.append(dict(kind='RX',hex=data.hex(),evidence='INFERIDO' if self.profile=='contract' else 'INFERIDO-ECHO',**self.context));self.ret(1,4)
 def call(self,address,line=None,context=None):
  if self.queue:raise RuntimeError('Unconsumed serial response')
  self.context=context or {};self.blocked=None;self.put32(STACK,STOP);self.u.reg_write(UC_X86_REG_ESP,STACK);self.u.reg_write(UC_X86_REG_EBP,0)
  self.u.reg_write(UC_X86_REG_EAX,SELF)
  if line is not None:self.u.mem_write(LINE,line.encode()+b'\0');self.u.reg_write(UC_X86_REG_EDX,LINE);self.u.reg_write(UC_X86_REG_ECX,len(line))
  self.u.emu_start(address,STOP,count=2000000)
  if self.blocked:raise UnknownBehavior(self.blocked['reason'])
  if self.u.reg_read(UC_X86_REG_EIP)!=STOP:raise RuntimeError('CPU instruction budget reached')
  return self.u.reg_read(UC_X86_REG_EAX)&255

def run(exe,firmware,profile,out):
 cpu=OriginalCPU(exe,profile);lines=Path(firmware).read_text().splitlines();status='NOT_STARTED';accepted=0
 try:
  if not cpu.call(0x92839c,context={'phase':'PROGFLASH'}):status='HOST_REJECTED_PROGFLASH'
  else:
   # OBS-COD 0x929528..0x9295d1: encrypted path discards one first line.
   for n,line in enumerate(lines[1:],2):
    if not cpu.call(0x9285a8,line,{'phase':'TRANSFER','source_line':n,'record_type':line[:2]}):status='HOST_REJECTED_LINE';break
    accepted+=1
   else:
    status='TRANSPORT_CONTRACT_COMPLETE' if cpu.call(0x92845c,context={'phase':'ENDFLASH_STANDALONE'}) else 'HOST_REJECTED_ENDFLASH'
 except UnknownBehavior:status='STOPPED_AT_UNKNOWN_BOOTLOADER_RESPONSE'
 out=Path(out);out.mkdir(parents=True,exist_ok=True)
 result=dict(firmware=Path(firmware).name,profile=profile,status=status,source_records=len(lines),accepted_records=accepted,transmitted_bytes=sum(map(len,cpu.writes)),binary_sha256=cpu.binary_sha256,blocked=cpu.blocked,preconditions='INFERIDO: already in compatible bootloader, erase completed, buffering enabled; full PreparaProg/Cancflash wrapper not executed',payload_decryption='DESCONHECIDO',ecu_flash_written=False,physical_port_opened=False,original_machine_code_executed=True)
 (out/'result.json').write_text(json.dumps(result,indent=2));(out/'trace.jsonl').write_text(''.join(json.dumps(x)+'\n' for x in cpu.events));(out/'wire.bin').write_bytes(b''.join(cpu.writes));print(json.dumps(result,indent=2));return result
if __name__=='__main__':
 import argparse
 p=argparse.ArgumentParser();p.add_argument('exe');p.add_argument('firmware');p.add_argument('--profile',choices=['strict','contract'],default='strict');p.add_argument('--out',required=True);a=p.parse_args();run(a.exe,a.firmware,a.profile,a.out)
