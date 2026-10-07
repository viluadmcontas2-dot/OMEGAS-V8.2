import pickle,json,numpy as np,datetime
S='session_2026-10-06_20-30-45_8c9e340c'
R=[r for r in pickle.load(open('snaps.pkl','rb')) if r['s']==S and min(len(r['pm']),len(r['gm']),len(r['nug']))>=16]
ev=[json.loads(l) for l in open(f'C:/Users/hugov/teia_data/{S}/events.jsonl',errors='replace') if l.strip() and '"telemetry"' not in l[:60]]
mk=[]
for e in ev:
    t=e['type']; d=e.get('data') or {}
    if t in('k_factor_batch_confirmed','k_batch_confirmed','autocal_native_automatch_epoch','autocal_native_calibration_epoch','refinement_verdict','autocal_native_action','refino_gas_learning_reset','reference_frozen','engine_stall'):
        mk.append((e['recordedAtMs'],t+(':'+str(d.get('action')) if t=='autocal_native_action' else '')+(':'+str(d.get('verdict') or d.get('kind') or '') if t=='refinement_verdict' else '')))
def P(m,t): return np.array(sorted([(m[i]/1024,t[i]/512) for i in range(16) if m[i]>0 and t[i]>0]))
f=lambda ms: datetime.datetime.fromtimestamp(ms/1000).strftime('%H:%M:%S')
rows=[]
for r in R:
    pp=P(r['pm'],r['pt'])
    if len(pp)<6: continue
    e=[r['gt'][i]/512/np.interp(r['gm'][i]/1024,pp[:,0],pp[:,1])-1 for i in range(16) if r['gm'][i]>0 and r['gt'][i]>0 and r['nug'][i]>=5 and pp[0,0]<=r['gm'][i]/1024<=pp[-1,0]]
    rows.append((r['t'],np.median(np.abs(e)) if e else None,len(e),sum(1 for x in r['nug'][:16] if x>=5),sum(1 for x in r['nup'][:16] if x>=5),np.median(e) if e else None))
allm=sorted([(r[0],'SNAP',r[1:]) for r in rows]+[(t,n,None) for t,n in mk],key=lambda z:z[0])
last=None
for t,n,x in allm:
    if n=='SNAP':
        s=f"|erro|={x[0]:.1%} vies={x[4]:+.1%} bandas_julgadas={x[1]} GNV_maduras={x[2]} GASOLINA_maduras={x[3]}" if x[0] is not None else f"sem bandas julgaveis GNV_maduras={x[2]} GASOLINA={x[3]}"
        if s!=last: print(f(t),s); last=s
    else: print(f(t),'   >>>',n)
