import json,numpy as np,datetime,glob
f=lambda ms: datetime.datetime.fromtimestamp(ms/1000).strftime('%m-%d %H:%M:%S')
AXms=np.array([256*i for i in range(1,21)]+[5632,6144,6656,7168,7680,8192,8704,9216,10240,11264])/512
for sd in sorted(glob.glob('C:/Users/hugov/teia_data/session_*')):
    for l in open(sd+'/events.jsonl',errors='replace'):
        if 'automatch_epoch' not in l: continue
        e=json.loads(l)
        if e['type']!='autocal_native_automatch_epoch': continue
        ev=e['data'].get('evidence') or {}
        b,a=ev.get('beforeRaw'),ev.get('afterRaw')
        if not b or not a: print(f(e['recordedAtMs']),'sem evidencia',list(e['data'].keys())[:8]); continue
        b,a=np.array(b)/16384,np.array(a)/16384; d=a/b-1; m=AXms<=10
        big=np.argsort(-abs(d[m]))[:3]
        print(f(e['recordedAtMs']),f'mudanca media {np.mean(d[m]):+.1%} max|{np.max(abs(d[m])):.1%}| pontos mudados {int((abs(d[m])>1e-6).sum())}/20 | maiores:',', '.join(f'{AXms[i]:.1f}ms {d[i]:+.1%}' for i in big),'| teto 1.5 apos:',int((a[m]>=1.5).sum()))
