"""Reproduce the frozen experiment from its authorized cache; never opens a ZIP."""
import argparse, pathlib, json, shutil, subprocess, hashlib
import confidence_surface as c
BASE=pathlib.Path(__file__).parent
FROZEN_HASH='4dcd64dc1681e34841b01c54f050b002efd7702ae68d693886496e1a414f4a76'
def main(mode):
 original=BASE/'results';gate=json.loads((original/'frozen_gate.json').read_text(encoding='utf-8'))
 assert c.sha(original/'frozen_gate.json')==FROZEN_HASH
 assert c.sha(BASE/'confidence_surface.py')==gate['script_sha256']
 assert c.sha(BASE/'build_cache.py')==gate['cache_builder_sha256']
 assert c.sha(BASE/'cache/preholdout_frames.csv.gz')==gate['inputs']['cache_sha256']
 assert json.loads((BASE/'cache/preholdout_inputs.json').read_text(encoding='utf-8'))==gate['inputs']['files']
 target=BASE/'verification'/mode
 target.mkdir(parents=True,exist_ok=True)
 c.OUT=target
 if mode=='preholdout':
  c.check();c.freeze()
  newer=json.loads((target/'frozen_gate.json').read_text(encoding='utf-8'))
  old=dict(gate);old.pop('frozen_at_utc');newer.pop('frozen_at_utc');assert old==newer
  for name,h in gate['preholdout_output_hashes'].items():assert c.sha(target/name)==h,name
 else:
  # Reuse the original immutable gate, not the replay's newer audit timestamp.
  for name in ['frozen_gate.json','frozen_gate.sha256','preholdout_loso.csv']:
   shutil.copyfile(original/name,target/name)
  pre=BASE/'verification/preholdout/map_0775_audit.csv'
  assert c.sha(pre)==gate['preholdout_output_hashes'][pre.name]
  shutil.copyfile(pre,target/pre.name)
  c.evaluate()
  for name in ['holdout_21_25.csv','holdout_classes.csv','reliability.csv','sensitivity.csv','map_0775_audit.csv']:
   assert c.sha(original/name)==c.sha(target/name),name
  # Changes to unused target-oracle fields cannot influence a prediction or score.
  _,_,regions,scales=c.prepare('preholdout');_,_,hold,_=c.prepare('holdout')
  small=hold.iloc[:2].copy();a=c.predictions(regions,scales,external=small)
  small['S']=1000;small['residual']=-1000
  b=c.predictions(regions,scales,external=small)
  for col in c.FEATURES+['predicted','predicted_residual','S_carry']:
   assert a[col].equals(b[col]),col
  # Production execution entrypoints reject an accidental second freeze/evaluation.
  c.OUT=original
  for action in [c.freeze,c.evaluate]:
   try:action()
   except AssertionError:pass
   else:raise AssertionError('repeated execution was not rejected')
 assert c.sha(original/'frozen_gate.json')==FROZEN_HASH
 print(mode.upper()+'_DETERMINISTIC_PASS; no ZIP opened; frozen hash unchanged')
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('mode',choices=['preholdout','external']);main(p.parse_args().mode)
