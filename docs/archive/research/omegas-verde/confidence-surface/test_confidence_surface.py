"""Synthetic tests: no external corpus access and no threshold selection."""
import unittest
import numpy as np
import pandas as pd
import confidence_surface as c

def source(sid,residual,n=1):
 return pd.DataFrame([dict(session=sid,rpm=1800+v,map=.4,residual=residual,visits=100000,frames=100000)
                      for v in np.linspace(-10,10,n)])
class ConfidenceTests(unittest.TestCase):
 def test_f2_anchor(self):
  self.assertAlmostEqual(float(c.f2(1800,.4)),2.14396620+np.exp(.34083653),12)
 def test_reference_exists_without_action_authority(self):
  e=c.support([],(1800,.4))
  self.assertGreater(float(c.f2(1800,.4))+e['predicted_residual'],0)
  for family in c.WEIGHTS:
   df=pd.DataFrame([e]);self.assertEqual(c.classify(df,c.scores(df,family),.7,.9)[0],'WAIT')
 def test_frames_cannot_create_independent_sources(self):
  d=source('only_one',.2,60);e=c.support(c.groups_for(d),(1800,.4))
  self.assertEqual(e['n_sources'],1);self.assertEqual(e['maturity'],1)
  self.assertNotEqual(c.classify(pd.DataFrame([e]),np.array([1.]),.7,.9)[0],'TRUST')
 def test_source_balance_not_frame_balance(self):
  d=pd.concat([source('one',.2,60),source('two',-.2,1)])
  e=c.support(c.groups_for(d),(1800,.4))
  self.assertAlmostEqual(e['predicted_residual'],0,10);self.assertEqual(e['n_sources'],2)
 def test_mad_robust_to_one_large_source(self):
  d=pd.concat([source('one',.2),source('two',.2),source('three',100)])
  e=c.support(c.groups_for(d),(1800,.4))
  self.assertAlmostEqual(e['predicted_residual'],.2);self.assertAlmostEqual(e['mad_ms'],0)
 def test_jeffreys_conflict_blocks_trust(self):
  d=pd.concat([source('one',.2),source('two',.2),source('three',-.2)])
  e=c.support(c.groups_for(d),(1800,.4))
  self.assertLess(e['sign_posterior'],.8)
  self.assertNotEqual(c.classify(pd.DataFrame([e]),np.array([1.]),.7,.9)[0],'TRUST')
 def test_distance_reduces_authority(self):
  groups=c.groups_for(source('one',.2))
  self.assertGreater(c.support(groups,(1800,.4))['distance'],c.support(groups,(2200,.4))['distance'])
  self.assertEqual(c.support(groups,(4000,.9))['n_sources'],0)
 def test_wrong_direction_is_unsafe_above_deadband(self):
  d=pd.DataFrame([dict(session='s',true_residual=.2,predicted_residual=-.01,abs_error=.21)])
  self.assertEqual(c.metrics(d)['safe_rate'],0)
 def test_deadband_is_evaluation_only(self):
  d=pd.DataFrame([dict(session='s',true_residual=.1,predicted_residual=-.01,abs_error=.11)])
  self.assertEqual(c.metrics(d,db=.12)['safe_rate'],1)
  self.assertEqual(c.metrics(d,db=.08)['safe_rate'],0)
 def test_tied_sign_is_not_consensus(self):
  d=pd.concat([source('one',.2),source('two',-.2)])
  e=c.support(c.groups_for(d),(1800,.4))
  self.assertAlmostEqual(e['sign_posterior'],.5);self.assertAlmostEqual(e['sign'],0)
if __name__=='__main__':unittest.main()
