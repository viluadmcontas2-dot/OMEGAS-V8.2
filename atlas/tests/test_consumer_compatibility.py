import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]

def load(name,path):
    spec=importlib.util.spec_from_file_location(name,ROOT/path)
    mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
    return mod

REPLAY=load("replay","atlas/tools/verify_consumer_replay.py")
DRIFT=load("drift","atlas/tools/audit_consumer_action_map.py")

class ConsumerReplayValidatorTest(unittest.TestCase):
    def test_action_drift_detects_shifted_consumer(self):
        consumer={"actions":[
            {"action":"MANUAL_AUTOMATCH","handler":"ActionAutoMatchExecute","handlerVa":"0x005189C0","mode":"0x01","frame":"02 24 04 01 2B"},
            {"action":"RESET_PETROL","handler":"ActionResetPetrolExecute","handlerVa":"0x005189CC","mode":"0x02","frame":"02 24 04 02 2C"},
            {"action":"RESET_GAS","handler":"ActionResetGasExecute","handlerVa":"0x005189D8","mode":"0x04","frame":"02 24 04 04 2E"},
        ],"separateActions":{"modifyMapRefs":{"handler":"ActionAutoCalRifExecute","handlerVa":"0x005189B4","mode":"0x08","frame":"02 24 04 08 32"}}}
        result=DRIFT.audit(consumer)
        self.assertEqual(result["status"],"DRIFT_DETECTED")
        self.assertGreaterEqual(len(result["conflicts"]),4)

    def test_all_completed_portmon_operations_advance_clock(self):
        with tempfile.TemporaryDirectory() as tmp:
            z=Path(tmp)/"x.zip"
            log=(
                "1 0.000 ProgBase.exe IOCTL_SERIAL_SET_TIMEOUTS Silabser0\n"
                "1 0.050 SUCCESS\n"
                "2 0.000 ProgBase.exe IRP_MJ_WRITE Silabser0 Length 3: 48 01 49\n"
                "2 0.001 SUCCESS\n"
                "3 0.000 ProgBase.exe IRP_MJ_READ Silabser0 Length 3: 48 01 49\n"
                "3 0.001 SUCCESS Length 3: 48 01 49\n"
            )
            with zipfile.ZipFile(z,"w") as zf:
                zf.writestr("x.log",log)
            rows=REPLAY.canonical_transactions(z,"x.log")
            self.assertAlmostEqual(rows[2]["at_ms"],50.0,places=6)

    def test_frame_and_response_checksum_helpers(self):
        self.assertTrue(REPLAY._frame_ok("02 24 04 04 2E"))
        self.assertTrue(REPLAY._response_ok("02 24 04 04 2E","02 24 04 04 2E 53 00 53"))
        self.assertFalse(REPLAY._frame_ok("02 24 04 04 00"))

if __name__=="__main__":
    unittest.main()
