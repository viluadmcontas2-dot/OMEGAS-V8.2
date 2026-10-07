import pickle,numpy as np,polars as pl,collections
R=[r for r in pickle.load(open('snaps.pkl','rb')) if min(len(r['pm']),len(r['pt']),len(r['gm']),len(r['gt']),len(r['nug']),len(r['nup']))>=16]
tel=pl.read_parquet('telemetry.parquet').sort(['session','t'])
T={s:d for (s,),d in tel.group_by(['session'])}
S=collections.defaultdict(list)
for r in R: S[r['s']].append(r)
ev=[]
for s,L in S.items():
    if s not in T: continue
    L.sort(key=lambda r:r['t'])
    for a,b in zip(L,L[1:]):
        if b['t']-a['t']>120000: continue   # janela maxima 2 min
        d=T[s].filter((pl.col('t')>a['t'])&(pl.col('t')<=b['t']))
        for f,lab,m,t,n in (('GASOLINA','GASOLINA','pm','pt','nup'),('GNV','GNV','gm','gt','nug')):
            x=d.filter(pl.col('fuel')==lab)
            for i in range(16):
                if b[n][i]>a[n][i]>=0 and b[n][i]-a[n][i]<=3 and b[m][i]>0 and b[t][i]>0:   # contador subiu (sem reset no meio)
                    mp=b[m][i]/1024; near=x.filter((pl.col('load_bar')-mp).abs()<0.03)
                    if near.height<3: ev.append((s,f,i,mp,b[t][i]/512,None,near.height)); continue
                    ev.append((s,f,i,mp,b[t][i]/512,(near['rpm']<1000).mean(),near.height))
print('incrementos de contador:',len(ev),'| com telemetria na MAP da banda:',sum(e[5] is not None for e in ev),'| sessoes',len({e[0] for e in ev}))
for f in ('GASOLINA','GNV'):
    print('==',f)
    for i in range(16):
        E=[e for e in ev if e[1]==f and e[2]==i and e[5] is not None]
        if len(E)<3: continue
        lenta=[e for e in E if e[5]>=0.8]; anda=[e for e in E if e[5]<=0.2]
        ms=lambda g: f'{np.median([e[4] for e in g]):.2f}ms(n={len(g)},{len({e[0] for e in g})}s)' if g else '-'
        print(f' b{i:2d} MAP~{np.median([e[3] for e in E]):.2f} eventos={len(E):3d} sess={len({e[0] for e in E}):2d} frac_lenta(>=80% rpm<1000)={len(lenta)/len(E):.0%} | ms lenta {ms(lenta)} | ms andando {ms(anda)}')
print('--- razao ms lenta/andando por banda GNV, IC95 bootstrap por sessao')
rng=np.random.default_rng(3)
for i in range(2,10):
    E=[e for e in ev if e[1]=='GNV' and e[2]==i and e[5] is not None]
    ss=sorted({e[0] for e in E}); bs=[]
    def rat(sel):
        l=[e[4] for e in sel if e[5]>=0.8]; a=[e[4] for e in sel if e[5]<=0.2]
        return np.median(l)/np.median(a) if l and a else np.nan
    for _ in range(2000):
        pick=rng.choice(ss,len(ss)); sel=[e for s in pick for e in E if e[0]==s]; bs.append(rat(sel))
    print(f' b{i} lenta/andando={rat(E):.3f} IC95[{np.nanpercentile(bs,2.5):.3f};{np.nanpercentile(bs,97.5):.3f}]')
