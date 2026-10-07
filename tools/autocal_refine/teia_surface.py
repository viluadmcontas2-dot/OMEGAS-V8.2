import polars as pl, numpy as np
df=pl.read_parquet('telemetry.parquet').sort(['session','t'])
df=df.with_columns([(pl.col('t').diff().over('session')/1000).alias('dts'),pl.col('load_bar').diff().over('session').abs().alias('dm'),pl.col('rpm').diff().over('session').abs().alias('dr')])
g=df.filter((pl.col('fuel')=='GASOLINA')&(pl.col('rpm')>600)&(pl.col('dts')<1.0)&(pl.col('dm')<0.015)&(pl.col('dr')<60)&(pl.col('petrol_ms')>0))
print('GASOLINA estacionario n',g.height,'sessoes',g['session'].n_unique())
x=np.c_[np.ones(g.height),g['load_bar'].to_numpy(),g['rpm'].to_numpy()/1000]; y=g['petrol_ms'].to_numpy()
for name,cols in (('MAP',[0,1]),('MAP+rpm',[0,1,2])):
    b,res,_,_=np.linalg.lstsq(x[:,cols],y,rcond=None); r=y-x[:,cols]@b; print(name,'coef',b.round(3),'R2',round(1-r.var()/y.var(),3),'resid sd ms',r.std().round(3))
# fatias de rpm: ms mediano por MAP em 3 faixas de rpm
g=g.with_columns((pl.col('load_bar')*20).round().alias('mb')); 
for lo,hi in ((600,1200),(1200,2000),(2000,3200)):
    s=g.filter((pl.col('rpm')>=lo)&(pl.col('rpm')<hi)).group_by('mb').agg(pl.col('petrol_ms').median().alias('ms'),pl.len().alias('n')).filter(pl.col('n')>=25).sort('mb')
    print(f'rpm {lo}-{hi}:',{round(a/20,2):round(b,2) for a,b in zip(s['mb'],s['ms'])})
