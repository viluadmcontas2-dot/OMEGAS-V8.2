#!/usr/bin/env python3
"""Independent deterministic replay for OMEGAS WU-006.

Never consumes runtime sample_state acceptance. RPM/MAP define comparable
operating region; petrol_ms is the quantity learned/compared across fuels.
"""
from __future__ import annotations
import argparse, gzip, hashlib, io, json, math, re, statistics, zipfile
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Sequence
try:
    import orjson as _orjson
except ImportError:  # optional accelerator
    _orjson = None

V8_SCHEMA = "mp48-progbase-v2"
V8_APP = "8.0.0-test-debug"
FUELS = {"GASOLINA", "GNV"}

def _loads(b): return _orjson.loads(b) if _orjson is not None else json.loads(b)
def _sha(b: bytes) -> str: return hashlib.sha256(b).hexdigest()
def _med(xs) -> float: return float(statistics.median(xs))
def privacy_session_key(s: str) -> str: return _sha(s.encode())[:16]

def _sha_file(p: Path) -> str:
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1<<20),b''): h.update(b)
    return h.hexdigest()

@dataclass(frozen=True)
class SessionCandidate:
    session_id:str; path:str; package_sha256:str; declared_event_bytes:int
    created_at_ms:int; app_version:str|None; depth:int=0; package_bytes:bytes|None=None

@dataclass(frozen=True)
class StableWindow:
    fuel:str; start_ms:int; end_ms:int; rpm:float; map_bar:float; petrol_ms:float; frame_count:int

@dataclass(frozen=True)
class Episode:
    session_key:str; order:int; fuel:str; start_ms:int; end_ms:int; rpm:float
    map_bar:float; petrol_ms:float; window_count:int; rpm_bin:int; map_bin:int

def choose_representative(xs:Sequence[SessionCandidate])->SessionCandidate:
    if not xs: raise ValueError('no candidates')
    return sorted(xs,key=lambda c:(-c.declared_event_bytes,c.package_sha256,c.path))[0]

def _window_ok(c):
    if len(c)!=10 or len({x['fuel'] for x in c})!=1 or c[0]['fuel'] not in FUELS:return False
    dur=c[-1]['t_ms']-c[0]['t_ms']
    if not 1200<=dur<=5000:return False
    r=[x['rpm'] for x in c];m=[x['map_bar'] for x in c];p=[x['petrol_ms'] for x in c]
    return (max(r)-min(r)<=max(60,.03*_med(r)) and max(m)-min(m)<=.06 and
            max(p)-min(p)<=max(.25,.15*_med(p)))

def derive_stable_windows(frames):
    out=[];i=0
    while i+10<=len(frames):
        c=frames[i:i+10]
        if _window_ok(c):
            out.append(StableWindow(c[0]['fuel'],c[0]['t_ms'],c[-1]['t_ms'],_med([x['rpm'] for x in c]),
                                    _med([x['map_bar'] for x in c]),_med([x['petrol_ms'] for x in c]),10));i+=10
        else:i+=1
    return out

def _rb(r):return int(math.floor(r/160.0))
def _mb(m):return int(math.floor(m/.04))

def merge_windows_to_episodes(windows,*,session_key,order,max_gap_ms=15000):
    out=[];cur=[]
    def flush():
        if not cur:return
        out.append(Episode(session_key,order,cur[0].fuel,cur[0].start_ms,cur[-1].end_ms,
                           _med([x.rpm for x in cur]),_med([x.map_bar for x in cur]),_med([x.petrol_ms for x in cur]),
                           len(cur),_rb(cur[0].rpm),_mb(cur[0].map_bar)));cur.clear()
    for w in sorted(windows,key=lambda x:(x.start_ms,x.end_ms)):
        if not cur:cur.append(w);continue
        p=cur[-1];same=w.fuel==p.fuel and _rb(w.rpm)==_rb(p.rpm) and _mb(w.map_bar)==_mb(p.map_bar)
        if same and w.start_ms-p.end_ms<=max_gap_ms:cur.append(w)
        else:flush();cur.append(w)
    flush();return out

def derive_episodes_from_frames(frames,*,session_key,order):
    trajectories=[];cur=[]
    for f in frames:
        if cur and (f['fuel']!=cur[-1]['fuel'] or f['t_ms']-cur[-1]['t_ms']>1500):
            trajectories.append(cur);cur=[]
        cur.append(f)
    if cur:trajectories.append(cur)
    out=[]
    for tr in trajectories:out.extend(merge_windows_to_episodes(derive_stable_windows(tr),session_key=session_key,order=order))
    return out

def walk_forward_pairs(eps):
    e=sorted(eps,key=lambda x:(x.order,x.session_key,x.start_ms))
    return [(a,b) for a in e for b in e if a.order<b.order]

_PORTMON=re.compile(r'(?:IRP_MJ_WRITE[^\r\n]*)?Length\s+7:\s*'+r'\s+'.join([r'([0-9A-Fa-f]{2})']*7))
def parse_portmon_map_k_writes(text):
    out=[]
    for m in _PORTMON.finditer(text):
        b=[int(x,16) for x in m.groups()]
        if b[:3]!=[0x14,0x54,0] or (sum(b[:6])&255)!=b[6]:continue
        r,c,v=b[3:6]
        if 0<=r<12 and 0<=c<12:out.append({'row':r,'column':c,'value':v,'checksum':b[6]})
    return out

def _pick(d,*ks):
    for k in ks:
        if k in d:return d[k]

def normalize_confirmed_k_history(events):
    out=[]
    for e in events:
        before=_pick(e,'before','beforeValue','valueBefore','previousValue');after=_pick(e,'after','afterValue','valueAfter','newValue')
        rb=_pick(e,'readback','readbackValue','confirmedValue');fh=_pick(e,'finalMapHash','mapHashAfter','final_hash')
        if not bool(_pick(e,'confirmed','isConfirmed')) or before is None or after is None or rb is None or int(rb)!=int(after) or not bool(_pick(e,'batchFinalized','finalized')) or not fh:
            raise ValueError('confirmed K history event missing causal proof envelope')
        r=int(_pick(e,'row','cellRow'));c=int(_pick(e,'column','col','cellColumn'))
        if not(0<=r<12 and 0<=c<12):raise ValueError('K history cell outside writable map')
        adj=str(_pick(e,'adjustmentId','adjustment_id','batchId') or '')
        out.append({'timestamp_ms':int(_pick(e,'timestampMs','timestamp','recordedAtMs') or 0),'adjustment_key':_sha(adj.encode())[:16] if adj else None,
                    'row':r,'column':c,'before':int(before),'after':int(after),'readback':int(rb),
                    'rpm':_pick(e,'rpm','rpmAxisValue'),'petrol_ms':_pick(e,'petrolMs','petrol_ms','petrolAxisValue'),'final_map_hash':str(fh)})
    return out

def _event_files(s):return [f for f in s.get('files',[]) if str(f.get('path','')).startswith('events_') and str(f.get('path','')).endswith('.jsonl')]

def _candidate(data,path,depth):
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            if not {'manifest.json','export_summary.json'}<=set(z.namelist()):return None
            m=_loads(z.read('manifest.json'));s=_loads(z.read('export_summary.json'))
            if m.get('format')!='omegas-session-log-v1':return None
            sid=str(m.get('sessionId') or s.get('session',{}).get('sessionId') or '')
            if not sid:return None
            return SessionCandidate(sid,path,_sha(data),sum(int(f.get('bytes',0)) for f in _event_files(s)),int(m.get('createdAtMs') or 0),(m.get('metadata') or {}).get('appVersion'),depth,data)
    except (zipfile.BadZipFile,KeyError,ValueError,UnicodeDecodeError):return None

def discover_session_candidates(outer,max_depth=5):
    out=[]
    def visit(data,path,depth):
        if depth>max_depth:return
        c=_candidate(data,path,depth)
        if c is not None:out.append(c);return
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as z:
                for i in sorted(z.infolist(),key=lambda x:x.filename):
                    if not i.is_dir() and i.filename.lower().endswith('.zip'):visit(z.read(i),f'{path}!/{i.filename}',depth+1)
        except zipfile.BadZipFile:pass
    with zipfile.ZipFile(outer) as z:
        for i in sorted(z.infolist(),key=lambda x:x.filename):
            if not i.is_dir() and i.filename.lower().endswith('.zip'):visit(z.read(i),i.filename,1)
    return out

def process_session_candidate(c,*,order):
    if c.package_bytes is None:raise ValueError('candidate has no package bytes')
    checked=[];frames=[]
    with zipfile.ZipFile(io.BytesIO(c.package_bytes)) as z:
        s=_loads(z.read('export_summary.json'))
        for f in s.get('files',[]):
            path=str(f['path']);want_n=int(f['bytes']);want_h=str(f['sha256']).lower();h=hashlib.sha256();n=0;is_ev=path.startswith('events_') and path.endswith('.jsonl')
            with z.open(path) as stream:
                if is_ev:
                    for raw in stream:
                        n+=len(raw);h.update(raw)
                        if c.app_version!=V8_APP:continue
                        try:e=_loads(raw)
                        except Exception:continue
                        if e.get('type')!='telemetry':continue
                        d=e.get('data') or {};fuel=str(d.get('fuel'))
                        if d.get('telemetry_scale_schema')!=V8_SCHEMA or fuel not in FUELS or d.get('plausible') is not True:continue
                        try:r=float(d['rpm']);m=float(d['load_bar']);p=float(d['petrol_ms'])
                        except (KeyError,TypeError,ValueError):continue
                        if 500<=r<=7000 and .15<=m<=1.20 and .5<=p<=25:frames.append({'t_ms':int(e.get('recordedAtMs') or d.get('captured_elapsed_ms') or 0),'fuel':fuel,'rpm':r,'map_bar':m,'petrol_ms':p})
                else:
                    for b in iter(lambda:stream.read(1<<20),b''):n+=len(b);h.update(b)
            got=h.hexdigest()
            if n!=want_n or got!=want_h:raise ValueError(f'integrity mismatch: {c.path}!/{path}')
            checked.append({'path':path,'bytes':n,'sha256':got})
    return {'checked':checked,'declared_event_bytes':c.declared_event_bytes},derive_episodes_from_frames(frames,session_key=privacy_session_key(c.session_id),order=order)

def _j(obj):return (json.dumps(obj,ensure_ascii=False,sort_keys=True,separators=(',',':'))+'\n').encode()
def deterministic_gzip_jsonl_bytes(eps):
    b=io.BytesIO()
    with gzip.GzipFile(filename='',mode='wb',fileobj=b,compresslevel=9,mtime=0) as g:
        for e in eps:g.write(_j(asdict(e)))
    return b.getvalue()
def _pct(v,q):
    if not v:return None
    x=sorted(v);p=(len(x)-1)*q;lo=math.floor(p);hi=math.ceil(p)
    return x[lo] if lo==hi else x[lo]*(hi-p)+x[hi]*(p-lo)

def gasoline_walk_forward_baseline(eps):
    gas=[e for e in eps if e.fuel=='GASOLINA'];errs=[];tested=supported=0;by=defaultdict(list)
    for te in sorted(gas,key=lambda e:(e.order,e.start_ms)):
        earlier=[tr for tr in gas if tr.order<te.order]
        if not earlier:continue
        tested+=1;ns=[]
        for tr in earlier:
            d=math.hypot((tr.rpm-te.rpm)/80,(tr.map_bar-te.map_bar)/.02)
            if d<=1.5:ns.append((d,tr))
        ns=sorted(ns,key=lambda x:(x[0],x[1].order,x[1].start_ms,x[1].session_key))[:16]
        if not ns:continue
        supported+=1;wv=[(1/(.25+d),tr.petrol_ms) for d,tr in ns];pred=sum(w*v for w,v in wv)/sum(w for w,_ in wv);err=abs(pred-te.petrol_ms)/te.petrol_ms;errs.append(err);by[te.order].append(err)
    return {'kind':'independent_gasoline_reference_baseline_not_production','geometry':{'rpm_scale':80.0,'map_scale_bar':.02,'radius':1.5,'max_neighbors':16},'tested_future_episodes':tested,'supported':supported,'coverage':supported/tested if tested else None,'median_abs_relative_error':_pct(errs,.5),'p90_abs_relative_error':_pct(errs,.9),'p95_abs_relative_error':_pct(errs,.95),'by_test_order':{str(k):{'n':len(v),'median_abs_relative_error':_pct(v,.5),'p90_abs_relative_error':_pct(v,.9)} for k,v in sorted(by.items())}}

def build_corpus_artifacts(outer,outdir):
    cs=discover_session_candidates(outer);g=defaultdict(list)
    for c in cs:g[c.session_id].append(c)
    reps=[choose_representative(v) for v in g.values()];reps.sort(key=lambda c:(c.created_at_ms,c.session_id,c.path));rows=[];eps=[]
    for order,c in enumerate(reps):
        verified,de=process_session_candidate(c,order=order);lane=de if c.app_version==V8_APP else [];eps+=lane
        rows.append({'order':order,'session_key':privacy_session_key(c.session_id),'source_package_sha256':c.package_sha256,'source_path_sha256':_sha(c.path.encode()),'created_at_ms':c.created_at_ms,'app_version':c.app_version,'declared_event_bytes':c.declared_event_bytes,'verified_file_count':len(verified['checked']),'duplicate_occurrences':len(g[c.session_id]),'episode_count':len(lane)})
    eps=sorted(eps,key=lambda e:(e.order,e.start_ms,e.end_ms,e.fuel,e.rpm_bin,e.map_bin));lane={'app_version':V8_APP,'telemetry_scale_schema':V8_SCHEMA}
    manifest={'schema':'omegas-science-corpus-manifest-v1','outer_zip_sha256':_sha_file(Path(outer)),'candidate_occurrences':len(cs),'logical_sessions':len(reps),'deduplication':'sessionId; representative=max declared event bytes; tie=package sha/path','science_lane':lane,'sessions':rows}
    report={'schema':'omegas-science-corpus-report-v1','science_lane':lane,'logical_sessions':len(reps),'episodes_total':len(eps),'episodes_by_fuel':dict(sorted(Counter(e.fuel for e in eps).items())),'sessions_with_episodes_by_fuel':{f:len({e.session_key for e in eps if e.fuel==f}) for f in sorted(FUELS)},'gasoline_walk_forward':gasoline_walk_forward_baseline(eps)}
    out=Path(outdir);out.mkdir(parents=True,exist_ok=True);files={'omegas_corpus_20260828_manifest.json':_j(manifest),'omegas_corpus_20260828_episodes.jsonl.gz':deterministic_gzip_jsonl_bytes(eps),'omegas_corpus_20260828_report.json':_j(report)}
    for n,b in files.items():(out/n).write_bytes(b)
    return {'manifest':manifest,'report':report,'files':{n:{'bytes':len(b),'sha256':_sha(b)} for n,b in files.items()}}

def main():
    p=argparse.ArgumentParser();p.add_argument('outer_zip');p.add_argument('output_dir');a=p.parse_args();print(json.dumps(build_corpus_artifacts(a.outer_zip,a.output_dir),ensure_ascii=False,indent=2,sort_keys=True));return 0
if __name__=='__main__':raise SystemExit(main())