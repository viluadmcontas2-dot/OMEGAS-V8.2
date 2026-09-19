#!/usr/bin/env python3
import argparse, json, sys
from pathlib import Path
import numpy as np
sys.path.insert(0,str(Path(__file__).resolve().parent))
import observer_falsification_20x10 as o
ap=argparse.ArgumentParser();ap.add_argument("--cache",default=o.CACHE_DEFAULT);ap.add_argument("--out",default="gpu_dataset.npz")
a=ap.parse_args();d=o.load(a.cache);X=[];y=[];groups=[];events=[];sessions=[]
for ei,ep in enumerate(o.episodes(d)):
    g=ep["gas"].copy();corr=o.signal(d,ep,"gauss",(250.,.03))
    if len(g)>400:
        ix=np.linspace(0,len(g)-1,400).astype(int);g=g.iloc[ix].reset_index(drop=True);corr=corr[ix]
    for j,r in enumerate(g.itertuples()):
        vals=[corr[j],r.petrol_ms,r.gas_ms,r.map_bar,r.rpm/1000.,r.gas_pressure_raw,r.gas_temp_raw,
              r.dmap if np.isfinite(r.dmap) else 0.,r.drpm if np.isfinite(r.drpm) else 0.,j/max(1,len(g)-1)]
        if all(np.isfinite(vals)):
            X.append(vals);y.append(ep["truth"]);groups.append(ep["session"]);events.append(ei)
            sessions.append(ep["session"])
np.savez_compressed(a.out,X=np.asarray(X,dtype=np.float32),y=np.asarray(y,dtype=np.float32),
                    groups=np.asarray(groups),events=np.asarray(events,dtype=np.int32))
print(json.dumps({"rows":len(X),"features":10,"episodes":len(set(events)),"sessions":len(set(sessions)),"out":a.out}))
