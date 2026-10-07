import pickle,json,struct,numpy as np,collections
R=pickle.load(open('snaps.pkl','rb'))
# MUL_ACT hex por (sessao,t) do npy
A=np.load('mulact.npy'); idx=json.load(open('mulact_idx.json')); mul={(s,t):A[i] for i,(s,t) in enumerate(idx)}
AX=[256*i for i in range(1,21)]+[5632,6144,6656,7168,7680,8192,8704,9216,10240,11264]; AXms=np.array(AX)/512
def P(m,t): 
    p=sorted([(m[i]/1024,t[i]/512) for i in range(16) if m[i]>0 and t[i]>0]); return np.array(p)
rows=[]
for r in R:
    pp,gp=P(r['pm'],r['pt']),P(r['gm'],r['gt'])
    k=mul.get((r['s'],r['t']))
    if len(pp)<8 or len(gp)<8 or k is None: continue
    nug=r['nug']
    for i in range(16):
        if r['gm'][i]>0 and r['gt'][i]>0 and nug[i]>=5:
            m=r['gm'][i]/1024; tg=r['gt'][i]/512
            if pp[0,0]<=m<=pp[-1,0]:
                tp=np.interp(m,pp[:,0],pp[:,1]); kk=np.interp(tp,AXms,k/16384)
                rows.append((r['s'],i,m,tp,tg,tg/tp,kk))
X=np.array([(x[2],x[3],x[4],x[5],x[6]) for x in rows]); S=[x[0] for x in rows]; B=np.array([x[1] for x in rows])
print('pares (banda GNV madura n>=5 x gasolina interpolada x MUL_ACT):',len(X),'sessoes',len(set(S)))
print('razao gas/pet: mediana',np.median(X[:,3]).round(3),'IQR',np.percentile(X[:,3],[25,75]).round(3),'| MUL_ACT mediano',np.median(X[:,4]).round(3))
print('corr(razao, MUL_ACT) pearson',np.corrcoef(X[:,3],X[:,4])[0,1].round(3))
print('razao/MUL_ACT mediano',np.median(X[:,3]/X[:,4]).round(3),'IQR',np.percentile(X[:,3]/X[:,4],[25,75]).round(3))
# por faixa de ms gasolina
for lo,hi in ((1,2.5),(2.5,3.5),(3.5,5),(5,7),(7,12)):
    m=(X[:,1]>=lo)&(X[:,1]<hi)
    if m.sum()>10: print(f'ms gas {lo}-{hi}: n={m.sum():4d} razao {np.median(X[m,3]):.3f} (IQR {np.percentile(X[m,3],25):.3f}-{np.percentile(X[m,3],75):.3f})  MUL_ACT {np.median(X[m,4]):.3f}  razao/MUL {np.median(X[m,3]/X[m,4]):.3f}')
