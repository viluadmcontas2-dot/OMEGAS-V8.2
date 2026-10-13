"""Executa o código x87 REAL do ProgBase.exe (ExportToK) no Unicorn e compara com a conta em JS.
Funções reais: 0x42EE14 (x do TEMPI), 0x512708 (acha o par de nós), 0x51280C (interpolação), 0x0A45A58 (Trunc).
Só 4 chamadas internas são substituídas por stubs: 0x433FFC (campo F), 0x42F338 (nº de linhas), 0x979E74 (Items[i] da lista)."""
import json,struct,sys
import pefile
from unicorn import *
from unicorn.x86_const import *
EXE,FIX=sys.argv[1],sys.argv[2]
pe=pefile.PE(EXE);B=pe.OPTIONAL_HEADER.ImageBase
fx=json.load(open(FIX))
ph=lambda n:next(f for f in fx['snapshot']['fields'] if f['key']==n)['physicalValues']
TBP,K=ph('PETR_INJ_TBP'),ph('MUL_ACT')
TEMPI=[781,977,1172,1367,1758,2344,3125,3906,4687,5469,6250,7031]
OBJ,ARR,DATA,STK,RES,STUB=0x20000000,0x21000000,0x21100000,0x22000000,0x23000000,0x30000000
def make(cw):
    u=Uc(UC_ARCH_X86,UC_MODE_32)
    for page in (0x42e000,0x512000,0x513000,0xa45000,0x979000,0xaec000,0xaed000):
        u.mem_map(page,0x1000)
        try:u.mem_write(page,pe.get_data(page-B,0x1000))
        except Exception:pass
    for a,sz in ((OBJ,0x10000),(ARR,0x1000),(DATA,0x1000),(STK,0x10000),(RES,0x1000),(STUB,0x1000)):u.mem_map(a,sz)
    u.mem_write(0x433ffc,bytes.fromhex('b8000a0000c3')) if False else None
    return u
def build(cw):
    u=Uc(UC_ARCH_X86,UC_MODE_32)
    pages=set(range(0x42e000,0x430000,0x1000))|{0x512000,0x513000,0xa45000,0xa46000,0x979000,0x97a000,0xaec000,0xaed000,0x433000,0x434000}
    for p in sorted(pages):
        u.mem_map(p,0x1000)
        try:u.mem_write(p,pe.get_data(p-B,0x1000))
        except Exception:pass
    for a,sz in ((OBJ,0x10000),(ARR,0x1000),(DATA,0x1000),(STK,0x10000),(RES,0x1000),(STUB,0x1000)):u.mem_map(a,sz)
    u.mem_write(0x433ffc,bytes.fromhex('b8000a0000c3'))          # campo F = 2560
    u.mem_write(0x42f338,bytes.fromhex('b80b000000c3'))          # última linha = 11 (loop até 12 linhas)
    u.mem_write(0x979e74,bytes.fromhex('8b4804dd04d1c3'))        # TList.Items[i] -> double
    u.mem_write(OBJ+0x70,struct.pack('<I',ARR)); u.mem_write(ARR+0x44,struct.pack('<I',len(TBP))); u.mem_write(ARR+4,struct.pack('<I',DATA))
    u.mem_write(DATA,b''.join(struct.pack('<d',v) for v in TBP))
    for i,v in enumerate(TEMPI):u.mem_write(OBJ+0xc98+i*4,struct.pack('<I',v))
    # após o retorno: fstp qword [RES]
    u.mem_write(STUB,bytes.fromhex('dd1d')+struct.pack('<I',RES)+b'\x90'); 
    u.mem_write(STUB+0x10,b'\x90')
    return u
def call(u,target,args,cw,fpret=False):
    sp=STK+0x8000
    blob=b''.join(a if isinstance(a,bytes) else struct.pack('<I',a) for a in args)
    ret=STUB+0x10
    u.mem_write(sp,struct.pack('<I',ret)+blob); u.reg_write(UC_X86_REG_ESP,sp)
    u.reg_write(UC_X86_REG_EBP,0)
    return sp
def run(cw):
    u=build(cw)
    def exec_(target,eax=0,edx=0,ecx=0,args=(),fp=False,regs=None):
        sp=STK+0x8000
        blob=b''.join(a if isinstance(a,bytes) else struct.pack('<I',a&0xffffffff) for a in args)
        u.mem_write(sp,struct.pack('<I',STUB+0x10)+blob)
        u.reg_write(UC_X86_REG_ESP,sp); u.reg_write(UC_X86_REG_EAX,eax); u.reg_write(UC_X86_REG_EDX,edx); u.reg_write(UC_X86_REG_ECX,ecx)
        u.emu_start(target,STUB+0x10)
        if fp:
            u.mem_write(RES,b'\0'*8); u.emu_start(STUB,STUB+6)
            return struct.unpack('<d',bytes(u.mem_read(RES,8)))[0]
        return u.reg_read(UC_X86_REG_EAX)
    # palavra de controle: 0x37f (80 bits) ou 0x27f (53 bits)
    # fldcw [mem]: usamos stub de uma instrução
    u.mem_write(STUB+0x20,struct.pack('<H',cw)); u.mem_write(STUB+0x30,bytes.fromhex('d92d')+struct.pack('<I',STUB+0x20)+b'\x90'); u.emu_start(STUB+0x30,STUB+0x36)
    out=[]
    for r in range(12):
        x=exec_(0x42ee14,eax=OBJ,edx=r,fp=True)
        u.mem_write(RES+0x100,b'\0'*8)
        ab=RES+0x200; u.mem_write(ab,b'\0'*8)
        ok=exec_(0x512708,args=(OBJ,struct.pack('<d',x),ab,ab+4)) & 0xff
        a,b=struct.unpack('<II',bytes(u.mem_read(ab,8)))
        k=None
        if ok and (a or b):
            k=exec_(0x51280c,args=(OBJ,struct.pack('<d',x),struct.pack('<d',TBP[a]),struct.pack('<d',TBP[b]),struct.pack('<d',K[a]),struct.pack('<d',K[b])),fp=True)
        out.append({'row':r,'x':x,'ok':bool(ok),'a':a,'b':b,'k':k})
    # Trunc real: célula × K
    code=bytes.fromhex('df2d')+struct.pack('<I',RES+0x300)+bytes.fromhex('dc0d')+struct.pack('<I',RES+0x308)+b'\xe8'
    rel=(0xa45a58-(STUB+0x40+len(code)+4))&0xffffffff
    u.mem_write(STUB+0x40,code+struct.pack('<I',rel)+b'\xc3')
    def trunc(cell,k):
        u.mem_write(RES+0x300,struct.pack('<q',cell)); u.mem_write(RES+0x308,struct.pack('<d',k))
        return exec_(STUB+0x40)
    return out,trunc
res={}
for name,cw in (('ext80',0x37f),('dbl53',0x27f)):
    res[name]=run(cw)
rows_ext,tr_ext=res['ext80']; rows_dbl,tr_dbl=res['dbl53']
def interp(x):
    for i in range(len(TBP)-1):
        if TBP[i]<=x<=TBP[i+1]: 
            return K[i]+(K[i+1]-K[i])*(x-TBP[i])/(TBP[i+1]-TBP[i])
    return None
maxdx=maxdk=0;diff=0;cases=0
print('linha  x(ms)       a  b  K(x87)       K(JS)        |dK|')
for re_,rd in zip(rows_ext,rows_dbl):
    kj=interp(re_['x']) if re_['ok'] else None
    dk=abs(re_['k']-kj) if re_['ok'] and kj is not None else float('nan')
    print(f"{re_['row']:>3} {re_['x']:10.6f}  {re_['a']:>2} {re_['b']:>2}  {re_['k'] if re_['k'] is not None else 'nao-muda'!s:<12} {kj if kj is not None else '-'!s:<12} {dk:.2e}")
    if re_['ok']: maxdk=max(maxdk,dk)
    if re_['ok']:
        for cell in range(100,256):
            t1=tr_ext(cell,re_['k']); t2=tr_dbl(cell,rd['k']); import math
            cases+=1
            if t1!=t2: diff+=1
            assert t1==math.floor(cell*re_['k']) or abs(t1-cell*re_['k'])<1e-9,(cell,re_['k'],t1)
print('max |K_x87-K_JS| =',maxdk,'; casos de Trunc =',cases,'; 80bits vs 53bits diferem em',diff)
