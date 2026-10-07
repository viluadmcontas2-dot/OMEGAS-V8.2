import pickle,numpy as np,polars as pl,collections
from scipy.stats import spearmanr
R=pickle.load(open('snaps.pkl','rb'))
tel=pl.read_parquet('telemetry.parquet').sort(['session','t'])
w=3
tel=tel.with_columns([pl.col('load_bar').rolling_std(w).over('session').alias('sm'),pl.col('rpm').rolling_std(w).over('session').alias('sr'),pl.col('t').diff().over('session').alias('dt')])
S=collections.defaultdict(list)
for r in R:
    if len(r['pm'])==18 and len(r['gm'])==18: S[r['s']].append(r)
def P(m,t): return np.array(sorted([(m[i]/1024,t[i]/512) for i in range(16) if m[i]>0 and t[i]>0]))
rows=[]
for s,L in S.items():
    L.sort(key=lambda r:r['t']); b=L[-1]; a=L[0]
    pp,gp=P(b['pm'],b['pt']),P(b['gm'],b['gt'])
    if len(pp)<6 or len(gp)<6: continue
    err=[abs(b['gt'][i]/512/np.interp(b['gm'][i]/1024,pp[:,0],pp[:,1])-1) for i in range(16) if b['gm'][i]>0 and b['gt'][i]>0 and b['nug'][i]>=5 and pp[0,0]<=b['gm'][i]/1024<=pp[-1,0]]
    if len(err)<4: continue
    d=tel.filter((pl.col('session')==s)&(pl.col('dt')<1000)&(pl.col('fuel')=='GNV')&(pl.col('rpm')>600))
    if d.height<200: continue
    cru=d.filter((pl.col('rpm')>1500)&(pl.col('rpm')<3000)&(pl.col('sm')<0.01)&(pl.col('sr')<40)).height/d.height
    rows.append((s,np.median(err),cru,d.height,len(L)))
E=np.array([r[1] for r in rows]); C=np.array([r[2] for r in rows]); N=np.array([r[3] for r in rows])
print('sessoes',len(rows),'| erro equiv mediano',np.median(E).round(3),'| cruzeiro frac mediana',np.median(C).round(3))
print('spearman(cruzeiro, erro)',round(spearmanr(C,E).statistic,3),'p',round(spearmanr(C,E).pvalue,3),'| spearman(duracao, erro)',round(spearmanr(N,E).statistic,3))
q=np.quantile(C,[1/3,2/3]); print('erro mediano por tercil de cruzeiro:',[f'{np.median(E[(C>=a)&(C<b)]):.1%}' for a,b in ((-1,q[0]),(q[0],q[1]),(q[1],9))],'| tercis',q.round(3))
