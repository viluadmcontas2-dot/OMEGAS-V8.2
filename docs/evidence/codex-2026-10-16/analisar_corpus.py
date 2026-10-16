#!/usr/bin/env python3
"""Análise somente leitura. Executar: python3 -I codex/analisar_corpus.py.
Padrões reutilizados: corpus.py (CSV/gzip), analise_automatch.py (Q14 LE),
analise_equivalencia_celulas.py (faixas RPM), analise_quedas.py (janela temporal).
Saídas ficam exclusivamente junto deste script; nenhuma dependência externa.
"""
import bisect, collections as C, csv, gzip, hashlib, json, math, pathlib, statistics as S, struct, zipfile
OUT=pathlib.Path(__file__).resolve().parent
BASE=OUT.parent/'novas'
OLD=pathlib.Path('/home/user/OMEGAS-V8.2/tests/fixtures/sessoes-uteis')
RPM=[(700,1100,850),(1100,1600,1350),(1600,2200,1850),(2200,2750,2500),(2750,3250,3000),(3250,4000,3500)]
T=C.defaultdict(list); E=C.defaultdict(list); meta={}; counts=C.Counter(); manifest=[]
def med(v): return S.median(v) if v else None
def pct(v,p):
 if not v:return None
 v=sorted(v); x=(len(v)-1)*p; i=int(x);return v[i]+(v[min(i+1,len(v)-1)]-v[i])*(x-i)
def fmt(v,n=3):return '—' if v is None else f'{v:.{n}f}'
def table(headers,rows):return '\n| '+' | '.join(headers)+' |\n| '+' | '.join(['---']*len(headers))+' |\n'+''.join('| '+' | '.join(map(str,r))+' |\n' for r in rows)+'\n'
def save(name,rows):
 if not rows:return
 with open(OUT/name,'w',newline='') as f:
  w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
def raw(F,key):
 f=F.get(key,{})
 if f.get('rawPayloadHex'):
  b=bytes.fromhex(f['rawPayloadHex'])
  if key in ['NUM_AUTOMATCH_EXECUTED','AUTO_CAL_ENABLE','ACQUIRED_ZONES_GAS','ACQUIRED_ZONES_PETROL']:return list(b)
  if len(b)%2==0:return list(struct.unpack('<' + ('h' if 'MNFLD' in key else 'H')*(len(b)//2),b))
 return f.get('rawValues') or []
def gap(k):return 100*(S.median(k[:4])/S.median(k[8:23])-1) if k and len(k)==30 and all(x>0 for x in k) else None
def curve(v):return [x/16384 for x in v] if len(v)==30 and all(x>0 for x in v) else None
def register(p):manifest.append({'path':str(p),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
for p in [OLD/'sessoes.json',OLD/'telemetria.csv.gz',OLD/'eventos-calibracao.jsonl.gz']:register(p)
for s in json.load(open(OLD/'sessoes.json'))['sessoes']:meta[s['sessao']]={'group':'antiga','id':s.get('resumo',{}).get('sessionId',s['sessao'])}
with gzip.open(OLD/'telemetria.csv.gz','rt') as f:
 for r in csv.DictReader(f):
  for k in ['t_ms','rpm','gas_ms','petrol_ms','map_bar','gas_c','water_c']:r[k]=float(r[k]) if r[k] else None
  T[r['sessao']].append(r)
with gzip.open(OLD/'eventos-calibracao.jsonl.gz','rt') as f:
 for l in f:
  e=json.loads(l);E[e['sessao']].append(e)
# Arquivos expandidos e membros ZIP são deduplicados por conteúdo, sem extrair nada.
seen=set(); sources=[]
for root in [BASE/'zip',BASE/'zip2']:
 for p in sorted(root.rglob('events_*.jsonl')):sources.append((str(p),p.read_bytes(),p.parent))
 for p in sorted(root.rglob('*.zip')):
  register(p)
  with zipfile.ZipFile(p) as z:
   for name in sorted(z.namelist()):
    if pathlib.PurePosixPath(name).name.startswith('events_') and name.endswith('.jsonl'):sources.append((str(p)+'!'+name,z.read(name),None))
for name,b,parent in sources:
 h=hashlib.sha256(b).hexdigest()
 if h in seen:counts['duplicate_files']+=1;continue
 seen.add(h);manifest.append({'path':name,'bytes':len(b),'sha256':h})
 events=[json.loads(l) for l in b.decode('utf-8-sig').splitlines() if l.strip()]
 sid=None
 if parent and (parent/'session_summary.json').exists():sid=json.load(open(parent/'session_summary.json')).get('sessionId')
 if not sid:
  for e in events:
   if e['type']=='session_started':sid=e['data'].get('sessionId');break
 sid=sid or pathlib.Path(name.split('!')[-1]).parent.name
 meta.setdefault(sid,{'group':'nova','id':sid})
 for e in events:
  if e['type']=='telemetry':
   d=e['data'];T[sid].append(dict(t_ms=e['recordedAtMs'],rpm=d.get('rpm'),gas_ms=d.get('gas_ms_diagnostic') if d.get('gas_pulse_present') else None,petrol_ms=d.get('petrol_ms'),map_bar=d.get('load_bar'),fuel=d.get('fuel'),gas_c=d.get('gas_c'),water_c=d.get('water_c'),sample_state=d.get('sample_state')))
  else:E[sid].append(e)
for sid in meta:
 T[sid].sort(key=lambda r:r['t_ms']);E[sid].sort(key=lambda e:e['recordedAtMs'])
short={}
for group,prefix in [('antiga','A'),('nova','N')]:
 for i,sid in enumerate(sorted(s for s in meta if meta[s]['group']==group)):short[sid]=prefix+f'{i+1:02d}'
# Estados de referência distintos dentro da sessão: polling idêntico não é aquisição nova.
refs=[]; snaps=[]; matches=[]; updates=C.defaultdict(list); ref_by_session=C.defaultdict(list)
for sid,events in E.items():
 previous=None;ref_seen=set();epoch=1;last_full=None
 for e in events:
  typ=e['type'];d=e['data'];t=e['recordedAtMs'];counts[typ]+=1
  if typ=='autocal_native_snapshot':
   F={f['key']:f for f in d.get('fields',[])}; k=curve(raw(F,'MUL_ACT'));n=(raw(F,'NUM_AUTOMATCH_EXECUTED') or [None])[0]
   ct=max([F[x].get('capturedAtMs',t) or t for x in ['MUL_ACT','NUM_AUTOMATCH_EXECUTED'] if x in F] or [t])
   snap=dict(sid=sid,t=ct,n=n,k=k,F=F,coherent=d.get('temporalCoherent'));snaps.append(snap)
   if previous and n is not None and previous['n'] is not None:
    if n<previous['n']:epoch+=1;counts['counter_resets']+=1
    if n>previous['n']+1:counts['counter_jumps']+=1
    if n==previous['n']+1 and k:
     intervening=sum(x['type']=='k_factor_batch_confirmed' and previous['t']<x['recordedAtMs']<=ct for x in events)
     matches.append(dict(sid=sid,t=ct,n=n,k=k,axis=[v/512 for v in raw(F,'PETR_INJ_TBP')],interval_s=(ct-previous['t'])/1000,app_writes=intervening,coherent=d.get('temporalCoherent')))
   if n is not None:previous=snap
   if k:updates[sid].append((ct,k,'snapshot'))
   pr=raw(F,'PETR_INJ_TBUF');mp=raw(F,'MNFLD_PRESS_BUF');num=raw(F,'NUM_BUF_UPD_PETR')
   key=(tuple(pr),tuple(mp),epoch)
   pts=[(m/1024,p/512) for i,(m,p) in enumerate(zip(mp,pr)) if m>0 and p>0 and (not num or i<len(num) and num[i]>0)]
   if pts and key not in ref_seen:
    ref_seen.add(key);r=dict(sid=sid,t=ct,epoch=epoch,pts=pts);refs.append(r);ref_by_session[sid].append(r)
  elif typ=='k_factor_batch_confirmed':
   k=curve(d.get('curve',{}).get('factorsRaw',[]))
   if k:updates[sid].append((t,k,'app'))
  elif typ=='full_snapshot':
   f=d.get('k_factor',{});v=f.get('details',{});v=v.get('curve',v);k=curve(v.get('factorsRaw',[]))
   stamp=f.get('updatedAt',0)
   # Cache somente quando updatedAt muda; não refrescar cache antigo a cada full_snapshot.
   if k and (stamp,tuple(k))!=last_full:
    last_full=(stamp,tuple(k))
    if stamp and stamp<=t and (not updates[sid] or stamp>max(x[0] for x in updates[sid])):updates[sid].append((t,k,'cache_full_snapshot'))
for sid in updates:updates[sid].sort(key=lambda x:x[0])
refrows=[];refbins=C.defaultdict(list);sessionbins=C.defaultdict(list)
for i,r in enumerate(refs):
 bins=C.defaultdict(list)
 for m,p in r['pts']:bins[int(math.floor(m*10))].append(p)
 for b,v in bins.items():
  refrows.append(dict(sessao=short[r['sid']],referencia=i+1,epoca_observada=r['epoch'],t_ms=r['t'],map_lo=b/10,n=len(v),mediana_ms=med(v),p10_ms=pct(v,.1),p90_ms=pct(v,.9)))
  refbins[b].append(med(v));sessionbins[(r['sid'],b)].append(med(v))
save('referencias_por_aquisicao_observada.csv',refrows)
matchrows=[]
for i,m in enumerate(matches):
 row=dict(automatch=i+1,sessao=short[m['sid']],t_ms=m['t'],contador=m['n'],intervalo_s=m['interval_s'],escritas_app_intervalo=m['app_writes'],coerente=m['coherent'],diferenca_pct=gap(m['k']))
 row.update({f'K_{j:02d}':v for j,v in enumerate(m['k'])});matchrows.append(row)
save('automatches_por_no.csv',matchrows)
# Exposição temporal e eventos: janela retrospectiva de 2,5 s, mesclar episódios <=5 s.
stats=[];eventrows=[];ratio=C.defaultdict(list);eq=C.defaultdict(list);eqs=C.defaultdict(list); stable=C.Counter();hours=C.Counter()
def state_at(sid,t,maxage=None):
 u=updates[sid];j=bisect.bisect_right([x[0] for x in u],t)-1
 if j<0 or maxage is not None and t-u[j][0]>maxage:return None
 return u[j][1]
def wmed(pairs):
 if not pairs:return None
 z=sorted(pairs);half=sum(w for v,w in z)/2;acc=0
 for v,w in z:
  acc+=w
  if acc>=half:return v
def merge(ts):
 out=[]
 for t in sorted(ts):
  if not out or t-out[-1]>5000:out.append(t)
 return out
for sid,rows in T.items():
 ut=[x[0] for x in updates[sid]];weighted=[];strictweighted=[];known=0;gnv=0;strictknown=0;drop=[];reported=[];lastdrop=-1e30
 for i,r in enumerate(rows):
  if i+1<len(rows):
   b=rows[i+1];dt=b['t_ms']-r['t_ms']
   if 0<dt<=3600:
    hours[r['fuel']]+=dt/3600000
    if r['fuel']=='GNV' and b['fuel']=='GNV':
     gnv+=dt
     # Divide intervalo nos instantes de leitura/escrita da curva.
     boundaries=sorted(set([r['t_ms']]+[x for x in ut if r['t_ms']<x<b['t_ms']]+[x+60000 for x in ut if r['t_ms']<x+60000<b['t_ms']]+[b['t_ms']]))
     for a,z in zip(boundaries,boundaries[1:]):
      k=state_at(sid,a)
      if k:known+=z-a;weighted.append((gap(k),z-a))
      if state_at(sid,a+0.001,60000):strictknown+=z-a;strictweighted.append((gap(k),z-a))
  if r['fuel']=='GNV' and r['rpm'] is not None and r['rpm']<600 and r['t_ms']-lastdrop>5000:
   prior=[]
   for a in reversed(rows[max(0,i-100):i]):
    if r['t_ms']-a['t_ms']>2500 or a['fuel']!='GNV':break
    prior.append(a)
   if any(a['rpm'] is not None and a['rpm']>=800 for a in prior):drop.append(r['t_ms']);lastdrop=r['t_ms']
  if i==0 or i+1==len(rows):continue
  a,b=rows[i-1],rows[i+1]
  if r['fuel'] not in ['GNV','GASOLINA'] or a['fuel']!=r['fuel'] or b['fuel']!=r['fuel']:continue
  if any(x['rpm'] is None or x['map_bar'] is None or not x['petrol_ms'] for x in [a,r,b]):continue
  dt1=(r['t_ms']-a['t_ms'])/1000;dt2=(b['t_ms']-r['t_ms'])/1000
  if not (0<dt1<=1.5 and 0<dt2<=1.5):continue
  if max(abs(r['rpm']-a['rpm'])/dt1,abs(b['rpm']-r['rpm'])/dt2)>300:continue
  if max(x['map_bar'] for x in [a,r,b])-min(x['map_bar'] for x in [a,r,b])>.03:continue
  rb=next((c for lo,hi,c in RPM if lo<=r['rpm']<hi),None)
  if rb is None:continue
  stable[r['fuel']]+=1;mb=int(math.floor(r['map_bar']*10))
  eq[(rb,mb,r['fuel'])].append(r['petrol_ms']);eqs[(sid,rb,mb,r['fuel'])].append(r['petrol_ms'])
  if r['fuel']=='GNV' and r['gas_ms'] and r['gas_ms']>0:ratio[(rb,int(math.floor(r['petrol_ms'])))].append(r['gas_ms']/r['petrol_ms'])
 times=[r['t_ms'] for r in rows]
 for e in E[sid]:
  if e['type']=='engine_stall':
   t=e['data'].get('at',e['recordedAtMs']);j=bisect.bisect_right(times,t)-1
   if j>=0 and t-times[j]<=2500 and rows[j]['fuel']=='GNV':reported.append(t)
 events=merge(drop+reported);known_events=0;strict_events=0
 for t in events:
  k=state_at(sid,t);known_events+=k is not None;strict_events+=state_at(sid,t,60000) is not None
  eventrows.append(dict(sessao=short[sid],t_ms=t,diferenca_pct=gap(k),rpm_detectado=any(abs(t-x)<=5000 for x in drop),engine_stall=any(abs(t-x)<=5000 for x in reported)))
 stats.append(dict(sessao=short[sid],origem=meta[sid]['group'],id=meta[sid]['id'],chave_corpus=sid,quadros=len(rows),gnv_h=gnv/3600000,curva_h=known/3600000,cobertura_pct=100*known/gnv if gnv else None,diferenca_pct=wmed(weighted),quedas=len(drop),engine_stall=len(reported),uniao=len(events),eventos_h=len(events)/(gnv/3600000) if gnv else None,eventos_curva=known_events,eventos_curva_h=known_events/(known/3600000) if known else None,diferenca_60s_pct=wmed(strictweighted),curva_60s_h=strictknown/3600000,eventos_60s_h=strict_events/(strictknown/3600000) if strictknown else None))
save('sessoes.csv',stats);save('afundamentos.csv',eventrows)
ratrows=[dict(rpm=r,petrol_lo_ms=p,n=len(v),mediana=med(v),p10=pct(v,.1),p90=pct(v,.9)) for (r,p),v in sorted(ratio.items())];save('razao_gas_petrol.csv',ratrows)
eqrows=[];paired=[]
for rb,mb in sorted({(r,m) for r,m,f in eq}):
 g=eq[(rb,mb,'GNV')];p=eq[(rb,mb,'GASOLINA')];rs=[]
 for sid in meta:
  gs=eqs[(sid,rb,mb,'GNV')];ps=eqs[(sid,rb,mb,'GASOLINA')]
  if len(gs)>=15 and len(ps)>=15:
   v=med(gs)/med(ps);rs.append(v);paired.append(dict(sessao=short[sid],rpm=rb,map_lo=mb/10,n_gnv=len(gs),n_gasolina=len(ps),razao=v))
 if len(g)<15 or len(p)<15:continue
 consistent=len(rs)>=3 and max(sum(v<.96 for v in rs),sum(v>1.04 for v in rs))/len(rs)>=.8
 eqrows.append(dict(rpm=rb,map_lo=mb/10,n_gnv=len(g),n_gasolina=len(p),razao_agregada=med(g)/med(p),sessoes_pareadas=len(rs),razao_mediana_pareada=med(rs),p10_pareado=pct(rs,.1),p90_pareado=pct(rs,.9),abaixo=sum(v<.96 for v in rs),acima=sum(v>1.04 for v in rs),consistente=consistent))
save('equivalencia_celulas.csv',eqrows);save('equivalencia_por_sessao.csv',paired)
def corr(x,y):
 if len(x)<3:return None
 mx=S.mean(x);my=S.mean(y);d=math.sqrt(sum((v-mx)**2 for v in x)*sum((v-my)**2 for v in y));return sum((a-mx)*(b-my) for a,b in zip(x,y))/d if d else None
def ranks(x):return [1+sum(v<a for v in x)+(sum(v==a for v in x)-1)/2 for a in x]
def correlations(rows,key):
 x=[r['diferenca_pct'] for r in rows];y=[r[key] for r in rows];return corr(x,y),corr(ranks(x),ranks(y))
usable=[r for r in stats if r['curva_h']>=5/60 and r['diferenca_pct'] is not None];pear,spear=correlations(usable,'eventos_curva_h')
complete=[r for r in usable if r['cobertura_pct']>=80];pc,sc=correlations(complete,'eventos_curva_h')
strictusable=[dict(r,diferenca_pct=r['diferenca_60s_pct']) for r in stats if r['curva_60s_h']>=5/60 and r['diferenca_60s_pct'] is not None]
ps60,ss60=correlations(strictusable,'eventos_60s_h')
unique_refs=len({tuple(r['pts']) for r in refs})
# Relatório reproduzível.
report=['# Análise do corpus OMEGAS\n',f'Leitura local; nenhum arquivo do repositório alterado. {len(meta)} sessões: {sum(m["group"]=="antiga" for m in meta.values())} antigas e {sum(m["group"]=="nova" for m in meta.values())} novas; {sum(map(len,T.values())):,} quadros. O catálogo tem 93, não 85 sessões antigas; o LEIA-ME também cita 93. {counts["duplicate_files"]} arquivos JSONL repetidos nos ZIPs descartados por SHA-256. Todos os segmentos encontrados recursivamente foram lidos. Não se duplicou telemetria dos full_snapshot.\n',f'Tempo observado por combustível (intervalos >0 e ≤3,6 s): '+', '.join(f'{k}: {v:.3f} h' for k,v in hours.items())+'. Lacunas maiores não entram. IDs curtos e caminhos completos: `sessoes.csv`; hashes: `entradas.json`.\n','Reprodução: `python3 -I codex/analisar_corpus.py`. Só biblioteca padrão. CSVs preservam resultados completos; tabelas abaixo são resumos. Fontes de método: `tools/sessoes/corpus.py`, `analise_automatch.py`, `analise_quedas.py`, `analise_equivalencia_celulas.py`; unidades: `AutoCalProtocol.kt`/`AutoCalScale`.\n', '## 1. Curva da gasolina por AutoCal\n',f'{len(snaps)} snapshots nativos em {len(set(s["sid"] for s in snaps))} sessões; {len(refs)} estados distintos de referência em {len(ref_by_session)} sessões. Buffer de injeção = u16 LE/512 ms; MAP = s16 LE/1024 bar. Não usei physicalValues antigo (/500 e /1000). Pares positivos e NUM_BUF_UPD_PETR>0, quando presente. Não usei buffers *_GAS como gasolina.\n','**Aquisição não é sinônimo de snapshot.** Sem ID persistente comum a todas as versões, reporto todas as referências observadas: par de buffers distinto por sessão e época observada (contador diminuiu). Repetição idêntica na mesma época conta uma vez. Referência herdada de sessão anterior continua sendo referência observada, não prova de coleta nova. A tabela completa por estado/faixa está em `referencias_por_aquisicao_observada.csv`.\n','Cada estado contribui uma mediana por faixa [MAP, MAP+0,1). p10/p90 são percentis dessas medianas, não intervalos de confiança. A última coluna dá dispersão entre medianas de sessões, para não dar peso maior a sessões com mais polling.\n']
r=[]
for b,v in sorted(refbins.items()):
 sv=[med(w) for (sid,bb),w in sessionbins.items() if bb==b]
 r.append([f'{b/10:.1f}–{(b+1)/10:.1f}',len(v),len(sv),fmt(med(v)),fmt(pct(v,.1)),fmt(pct(v,.9)),f'{fmt(pct(sv,.1))}–{fmt(pct(sv,.9))}'])
report.append(table(['MAP bar','Estados','Sessões','Mediana ms','p10','p90','p10–p90 entre sessões'],r))
report.append(f'As {len(refs)} referências observadas contêm {unique_refs} conjuntos distintos de pares válidos no corpus inteiro; repetições entre sessões podem ser herança da ECU, não replicações independentes.\n')
report.append('Não há evidência de uma curva canônica estável a ±4%: por exemplo, em 0,6–0,7 bar o p10–p90 entre sessões é 4,459–5,563 ms (amplitude de 24,8% sobre o p10). Uma curva mediana descritiva é possível. A mistura de RPM, temperatura, referências herdadas e épocas impede interpretar esta tabela como calibração universal. Não há identificação inequívoca de TODAS as aquisições físicas.\n')
report+=['## 2. Curva K por AutoMatch\n',f'{len(matches)} incrementos exatamente +1 observados em {len(set(m["sid"] for m in matches))} sessões. Saltos >1: {counts["counter_jumps"]}; reduções do contador: {counts["counter_resets"]}. Não inventei os AutoMatches intermediários. {sum(m["app_writes"]>0 for m in matches)} intervalos têm escrita do app; {sum(m["coherent"] is not True for m in matches)} snapshots pós-evento não declaram coerência temporal. São curvas observadas depois do incremento, não necessariamente imediatamente depois da escrita da ECU.\n','`automatches_por_no.csv` contém cada AutoMatch × 30 nós, contador, instante, intervalo de observação e possível intervenção. Decodificação de MUL_ACT aceita 60 bytes mesmo quando o app antigo marcava INVALID por esperar 18 nós.\n']
zone=[]
for lo,hi in [(0,8),(8,16),(16,24),(24,30)]:
 v=[med(m['k'][lo:hi]) for m in matches];zone.append([f'{lo}–{hi-1}',fmt(med(v)),fmt(pct(v,.1)),fmt(pct(v,.9))])
report.append(table(['Zona (índices)','Mediana K¹','p10','p90'],zone));report.append('¹ Mediana entre AutoMatches das medianas dos nós de cada zona; cada incremento tem o mesmo peso.\n')
gaps=[gap(m['k']) for m in matches]
report.append(f'Diferença = 100 × (mediana K[0:4] / mediana K[8:23] − 1). Mediana **{fmt(med(gaps),1)}%**; nós baixos inferiores em **{sum(v<0 for v in gaps)}/{len(gaps)}** incrementos. Índices 0–3 são 0,5–2 ms; “0–3 ms” incluiria também índices 4–5. Índices 8–22 são 4,5–13 ms neste eixo, não 8–22 ms.\n')
report.append(table(['AutoMatch','Sessão','Contador','K 0–3','K 8–22','Δ %','Janela s'],[[i+1,short[m['sid']],m['n'],fmt(med(m['k'][:4])),fmt(med(m['k'][8:23])),fmt(gap(m['k']),1),fmt(m['interval_s'],1)] for i,m in enumerate(matches)]))
report.append(table(['Nó','Eixo ms (mediana)','K mediano','p10','p90'],[[j,fmt(med([m['axis'][j] for m in matches if len(m['axis'])==30]),1),fmt(med([m['k'][j] for m in matches])),fmt(pct([m['k'][j] for m in matches],.1)),fmt(pct([m['k'][j] for m in matches],.9))] for j in range(30)]))
report+=['## 3. Hipótese dos nós baixos e apagões\n','Curva vigente estimada por última leitura disponível, atualizada por snapshot nativo, curva completa confirmada pelo app e mudanças de cache k_factor em full_snapshot (updatedAt mudou). Sem retroagir o primeiro snapshot e sem transportar curva entre sessões. A curva pode mudar pela ECU entre leituras: a continuidade é uma hipótese, não observação contínua. Diferença por sessão = mediana ponderada pelo tempo de GNV coberto.\n','Afundamento: rpm <600, com ≥800 nos 2,5 s anteriores, combustível continuamente GNV na janela. Inclui rpm=0. Episódios separados por até 5 s são agrupados; engine_stall entra somente com telemetria GNV até 2,5 s antes. União elimina sobreposição a 5 s. Não distingue parada voluntária. Denominador GNV exige ambos os extremos GNV e intervalo ≤3,6 s.\n','Tabela só de sessões com GNV; CSV inclui todas. Taxa comparável entre versões = quedas de RPM; união inclui também eventos do detector nativo, ausentes do corpus antigo. A correlação principal usa apenas eventos e tempo com curva conhecida.\n']
report.append(table(['ID','Sessão nova'],[[short[sid],meta[sid]['id']] for sid in sorted(meta) if meta[sid]['group']=='nova']))
report.append(table(['Sessão','GNV h','Cobertura K %','ΔK %','RPM / nativo / união','União/h GNV','União/h com K'],[[r['sessao'],fmt(r['gnv_h']),fmt(r['cobertura_pct'],1),fmt(r['diferenca_pct'],1),f'{r["quedas"]}/{r["engine_stall"]}/{r["uniao"]}',fmt(r['eventos_h'],1),fmt(r['eventos_curva_h'],1)] for r in stats if r['gnv_h']>0 and r['origem']=='nova']))
report.append('<details><summary>Tabela completa das sessões antigas com GNV</summary>\n')
report.append(table(['Sessão','GNV h','Cobertura K %','ΔK %','RPM / nativo / união','União/h GNV','União/h com K'],[[r['sessao'],fmt(r['gnv_h']),fmt(r['cobertura_pct'],1),fmt(r['diferenca_pct'],1),f'{r["quedas"]}/{r["engine_stall"]}/{r["uniao"]}',fmt(r['eventos_h'],1),fmt(r['eventos_curva_h'],1)] for r in stats if r['gnv_h']>0 and r['origem']=='antiga']))
report.append('</details>\n')
report.append(f'Com ≥5 min de exposição com curva: n={len(usable)}, **Pearson r={fmt(pear)}, Spearman ρ={fmt(spear)}** (ΔK × união/h com K). Exigindo também cobertura ≥80%: n={len(complete)}, r={fmt(pc)}, ρ={fmt(sc)}. Correlações entre sessões, sem ajuste por trajeto, mapa, temperatura ou motorista; não demonstram causalidade.\n')
report.append(f'Sensibilidade: leitura da curva com idade ≤60 s e ≥5 min cobertos: n={len(strictusable)}, r={fmt(ps60)}, ρ={fmt(ss60)}; diferença e taxa recalculados só nessa exposição. O sinal positivo da correlação principal não sustenta a previsão simples de mais eventos quando ΔK é mais negativo. Isso também não refuta benefício em uma situação específica.\n')
report.append('A hipótese “subir os nós baixos elimina apagões” exige comparação antes/depois sob condições semelhantes, curva e mapa sincronizados e exposição suficiente. Estes registros não isolam essa intervenção. Não se pode atribuir os episódios só à Curva K nem inferir mistura rica/pobre apenas pelo tempo de pulso. O CSV traz sensibilidade de cobertura quando a leitura tem no máximo 60 s.\n')
report+=['## 4. Razões por RPM e equivalência GNV/gasolina\n',f'Faixas RPM herdadas do script existente: '+', '.join(f'{c}=[{lo},{hi})' for lo,hi,c in RPM)+f'. Estabilidade: quadro e vizinhos no mesmo combustível, pulsos gasolina positivos, intervalo adjacente ≤1,5 s, |Δrpm|/Δt ≤300 rpm/s em ambos os lados, amplitude MAP ≤0,03 bar nos três quadros. Não usa filtro térmico; temperaturas/adaptação continuam confundidores. Quadros estáveis: GNV={stable["GNV"]:,}; gasolina={stable["GASOLINA"]:,}.\n','Razão gas_ms_diagnostic/petrol_ms só com pulso de gás presente e positivo. Faixa de petrol [inteiro, inteiro+1) ms. Cada célula mostra mediana (n); mínimo 15 quadros. Todos os valores, inclusive células pequenas e p10/p90, estão em `razao_gas_petrol.csv`. Não são fatores K nem medição de massa de combustível.\n']
ps=sorted({p for (r,p),v in ratio.items() if len(v)>=15})
report.append(table(['Petrol ms']+[str(c) for _,_,c in RPM],[[f'{p}–{p+1}']+[f'{med(ratio[(c,p)]):.3f} ({len(ratio[(c,p)])})' if len(ratio[(c,p)])>=15 else '—' for _,_,c in RPM] for p in ps]))
report.append('Equivalência = mediana petrol_ms(GNV) / mediana petrol_ms(gasolina), mesma célula RPM × MAP de 0,1 bar; ≥15 quadros de cada combustível. Agregado usa todo o corpus. Pareado calcula primeiro a razão dentro de cada sessão. “Consistente” exige ≥3 sessões pareadas e ≥80% delas fora de ±4% no mesmo sentido. Critério descritivo, não teste estatístico; quadros vizinhos são dependentes.\n')
report.append(table(['RPM','MAP','n G/P','Razão agregada','Sessões pareadas','Mediana pareada','p10–p90 pareado','Consistente'],[[r['rpm'],f'{r["map_lo"]:.1f}–{r["map_lo"]+.1:.1f}',f'{r["n_gnv"]}/{r["n_gasolina"]}',fmt(r['razao_agregada']),r['sessoes_pareadas'],fmt(r['razao_mediana_pareada']),f'{fmt(r["p10_pareado"])}–{fmt(r["p90_pareado"])}','sim' if r['consistente'] else 'não'] for r in eqrows]))
report.append(f'{sum(r["consistente"] for r in eqrows)}/{len(eqrows)} células cumprem consistência; {sum(r["razao_agregada"]<.96 or r["razao_agregada"]>1.04 for r in eqrows)} ficam fora de ±4% no agregado. Em 850 RPM × 0,4–0,5 bar, 9/12 sessões estão acima de +4% (75%): sinal frequente, abaixo do critério de 80%. Não confundir divergência agregada com repetição entre sessões. Sem célula comparável a 3500 RPM por falta de ≥15 quadros de gasolina. CSVs: `equivalencia_celulas.csv` e `equivalencia_por_sessao.csv`.\n')
report+=['## 5. Melhorias na exportação (até 8)\n', '1. `acquisition_id` persistente, `acquisition_started/finished/reset`, combustível, motivo e referência de origem; distinguir coleta nova de buffer herdado.\n','2. `calibration_changed`: contador antes/depois, causa ECU/app/reset, curvas completas antes/depois, eixo, hash, ACK/readback e instante efetivo; inclusive a leitura inicial.\n','3. `calibration_state_id` em cada telemetry: hashes e versões da Curva K e Mapa K, com mapa/eixos completos no início e em toda mudança.\n','4. Schema versionado por campo: raw, encoding, divisor, unidade e versão do decodificador; preservar 30 nós independentemente da validação antiga.\n','5. Em buffers: número de amostras por posição, início/fim da coleta, RPM/temperatura e instante por campo, máscara de validade/coerência.\n','6. Relógio monotônico comum, timestamp de captura e gravação, sequência do frame, perdas e lacunas; manter identidade da sessão em todos os segmentos.\n','7. `engine_stall` com ID do episódio, combustível, janela de telemetria antes/depois, curva/mapa vigentes, cutoff, recuperação e distinção de desligamento intencional. Preservar esse evento no corpus compacto.\n','8. Exportar condições de comparação: janela estável e motivo de rejeição, temperatura/pressão, velocidade/carga e, quando disponíveis, lambda/STFT/LTFT; usar null e origem quando indisponíveis.\n']
report.append('Verificação: `python3 -I codex/verificar_resultados.py` confere hashes, IDs, totais, limites de exposição e as três curvas novas contra os eventos nativos de AutoMatch, nó a nó. O analisador legado confirma independentemente os 14 incrementos antigos. A telemetria compacta antiga não conserva todos os raw/schema originais; as razões usam as unidades exportadas, sem recalibrar retrospectivamente esses frames.\n')
(OUT/'relatorio-corpus.md').write_text('\n'.join(report))
(OUT/'entradas.json').write_text(json.dumps(manifest,indent=2,ensure_ascii=False))
summary=dict(sessoes=len(meta),telemetria=sum(map(len,T.values())),snapshots=len(snaps),referencias=len(refs),automatches=len(matches),delta_automatch_mediana_pct=med(gaps),automatches_baixos=sum(x<0 for x in gaps),gnv_h=sum(r['gnv_h'] for r in stats),quedas=sum(r['quedas'] for r in stats),stall_gnv=sum(r['engine_stall'] for r in stats),uniao=sum(r['uniao'] for r in stats),correlacao_n=len(usable),pearson=pear,spearman=spear,estaveis=dict(stable),celulas_equivalencia=len(eqrows),consistentes=sum(r['consistente'] for r in eqrows))
(OUT/'resumo.json').write_text(json.dumps(summary,indent=2));print(json.dumps(summary,indent=2))
