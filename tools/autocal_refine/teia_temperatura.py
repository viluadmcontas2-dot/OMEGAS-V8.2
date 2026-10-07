import pickle,numpy as np,polars as pl
from scipy.stats import spearmanr
# 1) escalas: ms da telemetria (gasolina, estacionario) vs ms do buffer nativo da gasolina na mesma banda/MAP
R=[r for r in pickle.load(open('snaps.pkl','rb')) if min(len(r['pm']),len(r['pt']))>=16]
tel=pl.read_parquet('telemetry.parquet').sort(['session','t']).with_columns([pl.col('load_bar').diff().over('session').abs().alias('dm'),pl.col('rpm').diff().over('session').abs().alias('dr')])
g=tel.filter((pl.col('fuel')=='GASOLINA')&(pl.col('rpm')>=1200)&(pl.col('dm')<0.015)&(pl.col('dr')<60)&(pl.col('petrol_ms')>0))
rat=[]
for s,d in g.group_by('session'):
    s=s[0]; snaps=[r for r in R if r['s']==s]
    if not snaps: continue
    b=snaps[-1]
    for i in range(16):
        if b['pm'][i]>0 and b['pt'][i]>0 and b['nup'][i]>=5:
            m=b['pm'][i]/1024; x=d.filter((pl.col('load_bar')-m).abs()<0.02)
            if x.height>=15: rat.append(np.median(x['petrol_ms'])/(b['pt'][i]/512))
rat=np.array(rat); print('gasolina: ms telemetria / ms buffer(1/512): n',len(rat),'mediana',np.median(rat).round(3),'IQR',np.percentile(rat,[25,75]).round(3),'(1,31 se escalas divergissem)')
# 2) dynamic_correction explica razao instantanea gas/pet em GNV?
t=tel.filter((pl.col('fuel')=='GNV')&(pl.col('rpm')>=1200)&(pl.col('dm')<0.015)&(pl.col('dr')<60)&(pl.col('petrol_ms')>=3)&(pl.col('gas_ms_diagnostic')>0)&(pl.col('dynamic_correction')>0))
t=t.with_columns((pl.col('gas_ms_diagnostic')/pl.col('petrol_ms')).alias('r'))
print('GNV estacionario n',t.height,'sessoes',t['session'].n_unique(),'razao gas_diag/petrol mediana',round(t['r'].median(),3))
for c in ['dynamic_correction','gas_c','water_c','gas_pressure_abs_bar','pressure_diff_bar','load_bar']:
    x=t.drop_nulls(c); print(f'  spearman(r,{c})={spearmanr(x["r"],x[c]).statistic:+.3f}')
# regressao multipla por sessao-bootstrap: R2 de r ~ dc + gas_c + P
X=t.select(['dynamic_correction','gas_c','gas_pressure_abs_bar','load_bar']).drop_nulls(); y=t.drop_nulls(['dynamic_correction','gas_c','gas_pressure_abs_bar','load_bar'])['r'].to_numpy()
A=np.c_[np.ones(len(y)),X.to_numpy()]; b=np.linalg.lstsq(A,y,rcond=None)[0]; print('R2 r~dc+gas_c+P+MAP',round(1-((y-A@b)**2).sum()/((y-y.mean())**2).sum(),3),'coef',b.round(4))
A2=np.c_[np.ones(len(y)),X['load_bar'].to_numpy()]; b2=np.linalg.lstsq(A2,y,rcond=None)[0]; print('R2 so MAP',round(1-((y-A2@b2)**2).sum()/((y-y.mean())**2).sum(),3))
print('--- razao gas_diag/petrol por temperatura da agua (GNV estacionario, rpm>=1200, ms>=3)')
t=t.with_columns((pl.col('water_c')//5*5).alias('wb'))
s=t.group_by('wb').agg(pl.col('r').median().alias('r'),pl.len().alias('n'),pl.col('session').n_unique().alias('ns')).sort('wb')
for w,r,n,ns in s.iter_rows(): 
    if n>=100: print(f'  agua {w:3.0f}-{w+5:3.0f}C  razao {r:.3f}  n={n:5d} sessoes={ns}')
print('--- por temperatura do gas')
t=t.with_columns((pl.col('gas_c')//10*10).alias('gb'))
for w,r,n,ns in t.group_by('gb').agg(pl.col('r').median(),pl.len(),pl.col('session').n_unique()).sort('gb').iter_rows():
    if n>=100: print(f'  gas {w:3.0f}-{w+10:3.0f}C  razao {r:.3f}  n={n:5d} sessoes={ns}')
w=tel.filter(pl.col('rpm')>600); print('fracao do tempo com agua<70C:',round((w['water_c']<70).mean(),3),'| <60C:',round((w['water_c']<60).mean(),3),'| <50C:',round((w['water_c']<50).mean(),3))
