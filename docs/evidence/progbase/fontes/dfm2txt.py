import struct,math,json,hashlib,pathlib
class DFM:
 def __init__(self,b):self.b=b;self.p=4;assert b[:4]==b'TPF0'
 def get(self,n):p=self.p;self.p+=n;assert self.p<=len(self.b);return self.b[p:self.p]
 def u8(self):return self.get(1)[0]
 def s(self):return self.get(self.u8()).decode('cp1252')
 def val(self):
  t=self.u8()
  if t==0:return None
  if t==1:
   a=[]
   while self.b[self.p]!=0:a.append(self.val())
   self.p+=1;return a
  if t in (2,3,4):return int.from_bytes(self.get({2:1,3:2,4:4}[t]),'little',signed=True)
  if t==5:
   b=self.get(10);m=int.from_bytes(b[:8],'little');se=int.from_bytes(b[8:],'little');exp=(se&32767)-16383
   return (-1 if se&32768 else 1)*math.ldexp(m/2**63,exp) if m else 0.0
  if t in (6,7):return self.s()
  if t==8:return False
  if t==9:return True
  if t==10:
   n=int.from_bytes(self.get(4),'little');off=self.p;b=self.get(n);return {'binary_bytes':n,'offset':off,'sha256':hashlib.sha256(b).hexdigest()}
  if t==11:
   a=[]
   while True:
    s=self.s()
    if not s:break
    a.append(s)
   return a
  if t in (12,20):return self.get(int.from_bytes(self.get(4),'little')).decode('utf-8' if t==20 else 'cp1252')
  if t==13:return None
  if t==14:
   a=[]
   while self.b[self.p]!=0:
    assert self.u8()==1;a.append(self.props())
   self.p+=1;return a
  if t==15:return struct.unpack('<f',self.get(4))[0]
  if t==16:return int.from_bytes(self.get(8),'little',signed=True)/10000
  if t==17:return struct.unpack('<d',self.get(8))[0]
  if t==18:return self.get(int.from_bytes(self.get(4),'little')*2).decode('utf-16le')
  if t==19:return int.from_bytes(self.get(8),'little',signed=True)
  raise ValueError((hex(self.p-1),t))
 def props(self):
  a=[]
  while True:
   p=self.p;n=self.s()
   if not n:break
   a.append({'name':n,'offset':p,'value_offset':self.p,'value':self.val()})
  return a
 def obj(self,parent=''):
  p=self.p;flags=0
  if self.b[self.p]&0xf0==0xf0:
   flags=self.u8()&15
   if flags&2:self.val()
  cls=self.s();name=self.s();full=(parent+'.' if parent else '')+name
  o={'offset':p,'class':cls,'name':name,'path':full,'properties':self.props(),'children':[]}
  while self.b[self.p]!=0:o['children'].append(self.obj(full))
  self.p+=1;return o
 def parse(self):
  o=self.obj();assert self.p==len(self.b),(self.p,len(self.b));return o
def walk(o):
 yield o
 for ch in o['children']:yield from walk(ch)
if __name__=='__main__':
 import sys
 b=pathlib.Path(sys.argv[1]).read_bytes();print(json.dumps(DFM(b).parse(),ensure_ascii=False,indent=2))
