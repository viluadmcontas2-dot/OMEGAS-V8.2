#!/usr/bin/env python3
import argparse,json
from pathlib import Path
import numpy as np, xgboost as xgb
ap=argparse.ArgumentParser();ap.add_argument("--data",default="gpu_dataset.npz");ap.add_argument("--out",default="gpu_results.json")
a=ap.parse_args();z=np.load(a.data,allow_pickle=True);X=z["X"];y=z["y"];groups=z["groups"].astype(str);events=z["events"]
configs=[
 {"max_depth":2,"eta":.03,"rounds":120},{"max_depth":2,"eta":.07,"rounds":100},
 {"max_depth":3,"eta":.03,"rounds":150},{"max_depth":3,"eta":.07,"rounds":120},
 {"max_depth":4,"eta":.03,"rounds":160},{"max_depth":4,"eta":.07,"rounds":120},
 {"max_depth":5,"eta":.03,"rounds":180},{"max_depth":5,"eta":.05,"rounds":150},
 {"max_depth":6,"eta":.03,"rounds":180},{"max_depth":6,"eta":.05,"rounds":150}]
def met(e):
 a=np.abs(np.asarray(e,float))
 return {"n":int(len(a)),"mae_pct":float(a.mean()),"median_pct":float(np.median(a)),
 "p90_pct":float(np.quantile(a,.9)),"within4_pct":float((a<=4).mean()*100),"worst_pct":float(a.max())}
results=[]
for ci,cfg in enumerate(configs):
 errs=[];pred_rows=[]
 for s in sorted(set(groups)):
  tr=groups!=s;te=groups==s
  if tr.sum()<20 or te.sum()<2:continue
  dtr=xgb.DMatrix(X[tr],label=y[tr]);dte=xgb.DMatrix(X[te])
  bst=xgb.train({"objective":"reg:squarederror","tree_method":"hist","device":"cuda","max_depth":cfg["max_depth"],
                 "eta":cfg["eta"],"subsample":.85,"colsample_bytree":.9,"seed":1900+ci},dtr,num_boost_round=cfg["rounds"])
  p=bst.predict(dte);ev=events[te];yt=y[te]
  for e in sorted(set(ev.tolist())):
   m=ev==e;pred=float(np.median(p[m]));truth=float(np.median(yt[m]));errs.append(pred-truth)
   pred_rows.append({"session":s,"event":int(e),"pred":pred,"truth":truth})
 results.append({"config":cfg,"metrics":met(errs),"predictions":pred_rows})
results.sort(key=lambda r:r["metrics"]["mae_pct"])
out={"schema":"omegas.gpu-observer.v1","device":"cuda","xgboost":xgb.__version__,"configs":10,"champion":results[0],"runner_up":results[1],"results":results}
Path(a.out).write_text(json.dumps(out,indent=2),encoding="utf-8")
print(json.dumps({"device":"cuda","xgboost":xgb.__version__,"champion":results[0]["config"],"metrics":results[0]["metrics"]}))
