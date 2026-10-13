"""Prova das fronteiras de zona do AutoCal no fio: para cada virada 0->1 das bandeiras 0x016F/0x0170, o MAP da
telemetria (48 01) nos 3 s anteriores; e estatistica dos quadros com byte 11 = 0x94.
Uso: python3 -I tools/evidence/zonas_autocal_portmon.py PortmonLOGNOVO.LOG
Evidencia: docs/evidence/2026-10-13-zonas-autocal-prova-ecu.md"""
import re, sys, json
L = sys.argv[1]
W = re.compile(r'^(\d+)\s+([\d.]+)\s+\S+\s+IRP_MJ_WRITE\s+\S+\s+Length \d+: (.*)$')
Rd = re.compile(r'^(\d+)\s+([\d.]+)\s+SUCCESS\s+Length \d+: (.*)$')
D = re.compile(r'^(\d+)\s+([\d.]+)\s+')
t=0.0; req=None; rx=[]; reqline=0
zones={}; tele=[]  # (t, map, fuelbyte, rpm, petrolMs, gasRaw)
out=[]; c94=[]
def u16(b,i): return b[i]|b[i+1]<<8
def s16(b,i):
    v=u16(b,i); return v-65536 if v>32767 else v
def flush():
    global req,rx
    if req is None: return
    key=" ".join(req.split()[:-1])
    b=[int(x,16) for x in " ".join(rx).split()] if rx else []
    n=len(req.split())
    if len(b)>=n+2 and b[n]==0x53:
        ln=b[n+1]; p=b[n+2:n+2+ln]
        if len(p)==ln:
            if key=='48 01' and ln>=20:
                fr=(round(t,2), s16(p,17)/1000.0, p[11], u16(p,0), u16(p,8)*0.00256, u16(p,6))
                tele.append(fr)
                if p[11]==0x94: c94.append(fr)
            elif key in ('29 6F 01','29 70 01'):
                old=zones.get(key); 
                if old!=p:
                    zones[key]=p
                    if old is not None:
                        for z in range(4):
                            if old[z]==0 and p[z]==1:
                                win=[f for f in tele if f[0]>=t-3.0]
                                maps=[f[1] for f in win]
                                out.append({'t':round(t,1),'line':reqline,'fuel':'PETR' if key=='29 6F 01' else 'GNV','zona':z,
                                    'map_min':round(min(maps),3) if maps else None,'map_max':round(max(maps),3) if maps else None,
                                    'map_ult':round(maps[-1],3) if maps else None,'n':len(maps),'b11_ult':'0x%02X'%win[-1][2] if win else None})
    req=None; rx=[]
with open(L,encoding='latin-1',errors='replace') as f:
    for i,line in enumerate(f,1):
        m=D.match(line)
        if m: t+=float(m.group(2))
        m=W.match(line)
        if m: flush(); req=m.group(3).strip(); reqline=i; continue
        m=Rd.match(line)
        if m and req is not None: rx.append(m.group(3).strip())
flush()
for o in out: print(o)
import statistics as st
print('--- 0x94:', len(c94))
if c94:
    print('map mediana/min/max', round(st.median([f[1] for f in c94]),3), round(min(f[1] for f in c94),3), round(max(f[1] for f in c94),3))
    print('rpm min/max', min(f[3] for f in c94), max(f[3] for f in c94))
    print('petrolMs max', round(max(f[4] for f in c94),3), ' gasRaw max', max(f[5] for f in c94))
    heur=[f for f in c94 if f[3]>=1200 and f[4]<0.70 and f[5]==0 and f[1]<0.35]
    print('capturados pela heurística physicalCutoff:', len(heur), ' ficam UNKNOWN:', len(c94)-len(heur))
    rest=[f for f in c94 if f not in heur]; print('UNKNOWN exemplos (t,map,b11,rpm,petrolMs,gasRaw):', rest[:5])
