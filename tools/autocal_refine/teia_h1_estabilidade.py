import pickle,numpy as np,polars as pl,collections
from scipy.stats import spearmanr
R=[r for r in pickle.load(open('snaps.pkl','rb')) if min(len(r['pm']),len(r['pt']),len(r['gm']),len(r['gt']),len(r['nug']),len(r['nup']))>=16]
tel=pl.read_parquet('telemetry.parquet').sort(['session','t']).with_columns([
  (pl.col('t').diff().over('session')).alias('dt'),pl.col('load_bar').diff().over('session').abs().alias('dm'),pl.col('rpm').diff().over('session').abs().alias('dr')])
T={s:d for (s,),d in tel.group_by(['session'])}
def cur(r,f):
    m,t,n=(r['gm'],r['gt'],r['nug']) if f=='GAS' else (r['pm'],r['pt'],r['nup'])
    return [(m[i]/1024,t[i]/512,n[i]) for i in range(16)]
def dev(c,i):
    if c[i][0]<=0 or c[i][1]<=0: return None
    L=[j for j in range(i-1,-1,-1) if c[j][0]>0 and c[j][1]>0][:1]; U=[j for j in range(i+1,16) if c[j][0]>0 and c[j][1]>0][:1]
    if not L or not U: return None
    (m0,t0,_),(m1,t1,_)=c[L[0]],c[U[0]]
    return None if m1-m0<1e-3 else c[i][1]/(t0+(t1-t0)*(c[i][0]-m0)/(m1-m0))-1
S=collections.defaultdict(list)
for r in R:
    if len(r['pm'])==18 and len(r['gm'])==18: S[r['s']].append(r)
U=[]
for s,L in S.items():
    L.sort(key=lambda r:r['t']); a,b=L[0],L[-1]
    if s not in T or len(L)<2: continue
    d=T[s].filter((pl.col('t')>=a['t'])&(pl.col('t')<=b['t'])&(pl.col('dt')<1000))
    for f,lab in (('PETROL','GASOLINA'),('GAS','GNV')):
        ca,cb=cur(a,f),cur(b,f); x=d.filter(pl.col('fuel')==lab)
        for i in range(16):
            if cb[i][2]<3 or cb[i][1]<=0 or (ca[i][1]==cb[i][1] and ca[i][2]>=cb[i][2]): continue  # so bandas aprendidas na sessao
            e=dev(cb,i)
            if e is None: continue
            near=x.filter(((pl.col('load_bar')-cb[i][0]).abs()<0.025)&(pl.col('rpm')>800)&(pl.col('rpm')<3000))
            if near.height<5: continue
            st=near.filter((pl.col('dm')<0.01)&(pl.col('dr')<50)).height/near.height
            U.append((s,f,i,abs(e),st,near.height))
print('unidades (banda aprendida na sessao):',len(U),'sessoes',len({u[0] for u in U}))
rng=np.random.default_rng(7)
for f in ('PETROL','GAS',None):
    V=[u for u in U if f is None or u[1]==f]; e=np.array([u[3] for u in V]); st=np.array([u[4] for u in V])
    rho=spearmanr(st,e).statistic
    ss=sorted({u[0] for u in V}); by={s:[k for k,u in enumerate(V) if u[0]==s] for s in ss}; bs=[]
    for _ in range(2000):
        k=np.concatenate([by[s] for s in rng.choice(ss,len(ss))]); bs.append(spearmanr(st[k],e[k]).statistic)
    q=np.quantile(st,[1/3,2/3]); lo,hi=e[st<=q[0]],e[st>=q[1]]
    print(f'{f or "AMBOS"}: n={len(V)} sess={len(ss)} spearman(estab,|desvio|)={rho:+.3f} IC95[{np.nanpercentile(bs,2.5):+.3f};{np.nanpercentile(bs,97.5):+.3f}] | |desvio| mediano: coleta INSTAVEL {np.median(lo):.1%} vs ESTAVEL {np.median(hi):.1%} | frac>6%: {np.mean(lo>.06):.0%} vs {np.mean(hi>.06):.0%}')
print('--- contra-teste: dentro da mesma banda e controlando n de amostras')
V=[u for u in U if u[1]=='GAS']
import itertools
E=np.array([u[3] for u in V]); ST=np.array([u[4] for u in V]); NB=np.array([u[2] for u in V]); NN=np.log(np.array([u[5] for u in V]))
# residualiza |desvio| e estabilidade por efeito fixo de banda (rank) e log n
from scipy.stats import rankdata
def resid(y):
    y=rankdata(y).astype(float); X=np.c_[np.ones(len(y)),NN,np.eye(16)[NB][:,1:]]; b=np.linalg.lstsq(X,y,rcond=None)[0]; return y-X@b
re,rs=resid(E),resid(ST); rho=np.corrcoef(re,rs)[0,1]
ss=sorted({u[0] for u in V}); by={s:[k for k,u in enumerate(V) if u[0]==s] for s in ss}; bs=[]
for _ in range(2000):
    k=np.concatenate([by[s] for s in rng.choice(ss,len(ss))]); bs.append(np.corrcoef(re[k],rs[k])[0,1])
print(f'GAS parcial (banda + log n): rho={rho:+.3f} IC95[{np.percentile(bs,2.5):+.3f};{np.percentile(bs,97.5):+.3f}]')
print('estabilidade x banda (spearman):',round(spearmanr(ST,NB).statistic,3),'| n amostras x |desvio|:',round(spearmanr(NN,E).statistic,3))
def resid2(y):
    y=rankdata(y).astype(float); X=np.c_[np.ones(len(y)),np.eye(16)[NB][:,1:]]; b=np.linalg.lstsq(X,y,rcond=None)[0]; return y-X@b
re,rn=resid2(E),resid2(NN); rho=np.corrcoef(re,rn)[0,1]; bs=[]
for _ in range(2000):
    k=np.concatenate([by[s] for s in rng.choice(ss,len(ss))]); bs.append(np.corrcoef(re[k],rn[k])[0,1])
print(f'GAS parcial n_amostras|banda: rho={rho:+.3f} IC95[{np.percentile(bs,2.5):+.3f};{np.percentile(bs,97.5):+.3f}]')
q=np.quantile(NN,[1/3,2/3]); print('|desvio| mediano por tercil de amostras na banda:',[f'{np.median(E[(NN>=a)&(NN<b)]):.1%}' for a,b in ((-1,q[0]),(q[0],q[1]),(q[1],99))],'| n amostras tercis:',np.exp(q).round(0))
