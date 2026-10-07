import json,glob,numpy as np
def curve(d,fuel):
    F={x['key']:x for x in d['fields']}
    if not all(k in F and F[k].get('rawValues') for k in (('MNFLD_PRESS_BUF_GAS','PETR_INJ_TBUF_GAS','NUM_BUF_UPD_GAS') if fuel=='GAS' else ('MNFLD_PRESS_BUF','PETR_INJ_TBUF','NUM_BUF_UPD_PETR'))): return None
    g=lambda k:F[k]['rawValues']
    m,t,n=(g('MNFLD_PRESS_BUF_GAS'),g('PETR_INJ_TBUF_GAS'),g('NUM_BUF_UPD_GAS')) if fuel=='GAS' else (g('MNFLD_PRESS_BUF'),g('PETR_INJ_TBUF'),g('NUM_BUF_UPD_PETR'))
    return [(m[i]/1024,t[i]/512,n[i]) for i in range(18)]
def dev(c,i):
    if c[i][0]<=0 or c[i][1]<=0: return None
    L=[j for j in range(i-1,-1,-1) if c[j][0]>0 and c[j][1]>0][:1]; U=[j for j in range(i+1,18) if c[j][0]>0 and c[j][1]>0][:1]
    if not L or not U: return None
    (m0,t0,_),(m1,t1,_)=c[L[0]],c[U[0]]
    if m1-m0<1e-3: return None
    return c[i][1]/(t0+(t1-t0)*(c[i][0]-m0)/(m1-m0))-1
fm=lambda x:'-' if x is None else f'{x:+.1%}'
for s in sorted(glob.glob('session_*')):
    evs=[]
    for l in open(f'{s}/events.jsonl',errors='replace'):
        if 'autocal_native' not in l: continue
        try:e=json.loads(l)
        except: continue
        evs.append(e)
    snaps=[(e['data']['capturedAtMs'],e['data']) for e in evs if e['type']=='autocal_native_snapshot']
    acts=[e['data'] for e in evs if e['type']=='autocal_native_action']
    for a in acts:
        if isinstance(a.get('after'),dict) and 'fields' in a['after']: snaps.append((a['finishedAtMs'],a['after']))
    snaps.sort(key=lambda x:x[0])
    for a in acts:
        if a.get('action')!='DELETE_POINT': continue
        tgs=(a.get('details') or {}).get('targets') or ([a['pointDelete']] if a.get('pointDelete') else [])
        if not tgs: print(s[8:27],'DELETE_POINT sem alvo registrado (build antiga)'); continue
        for tg in tgs:
            i,f=tg['index'],tg['fuel']; t0=a['startedAtMs']
            bef=[d for t,d in snaps if t<t0-1 and curve(d,f)]; aft=[(t,d) for t,d in snaps if t>a['finishedAtMs']+1 and curve(d,f)]
            if not bef: print(s[8:27],f,i,'sem snapshot antes'); continue
            cb=curve(bef[-1],f); res=None
            for t,d in aft:
                ca=curve(d,f)
                if ca[i][2]>=3: res=(dev(ca,i),ca[i][2],(t-t0)/1000,ca[i][1]); break
            lastc=curve(aft[-1][1],f) if aft else None
            print(s[8:27],f,'b%d'%i,'| antes ms=%.2f n=%d dev=%s'%(cb[i][1],cb[i][2],fm(dev(cb,i))),'| depois n>=3:',None if not res else f'ms={res[3]:.2f} dev={fm(res[0])} n={res[1]} em {res[2]:.0f}s','| ultimo:',None if not lastc else f'dev={fm(dev(lastc,i))} n={lastc[i][2]}')
