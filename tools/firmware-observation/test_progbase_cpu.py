"""Checks for original host behavior; contract peer is synthetic, not ECU evidence."""
import unittest,json,os
from pathlib import Path
from progbase_cpu import OriginalCPU,UnknownBehavior
EXE=Path(os.environ.get('PROGBASE_EXE','/workspace/attachments/df7e3123-6790-4751-ac47-09427c38cae1/Progbase.exe'))
FIRMWARE=Path(os.environ.get('FIRMWARE_S19_DIR','/workspace/firmware-analysis'))
class HostTests(unittest.TestCase):
 def test_strict_stops_without_inventing_bootloader_ack(self):
  cpu=OriginalCPU(EXE)
  with self.assertRaises(UnknownBehavior):cpu.call(0x92839c)
  self.assertEqual(cpu.writes,[bytes.fromhex('9393')]);self.assertEqual(cpu.blocked['state'],'DESCONHECIDO')
 def test_original_progflash_command_and_expected_reply(self):
  cpu=OriginalCPU(EXE,'contract');self.assertEqual(cpu.call(0x92839c),1);self.assertEqual(cpu.writes,[bytes.fromhex('9393')])
 def test_negative_ack_exhausts_four_progflash_attempts(self):
  cpu=OriginalCPU(EXE,'contract','ack');self.assertEqual(cpu.call(0x92839c),0);self.assertEqual(cpu.writes,[bytes.fromhex('9393')]*4)
 def test_echo_mutation_rejects_line_after_nine_attempts(self):
  line=(FIRMWARE/'EVO_L_#00563.s19').read_text().splitlines()[3];cpu=OriginalCPU(EXE,'contract','echo');self.assertEqual(cpu.call(0x9285a8,line),0);self.assertEqual(len(cpu.writes),9)
 def test_unstable_ack_rejects_line(self):
  line=(FIRMWARE/'EVO_#01153.s19').read_text().splitlines()[3];cpu=OriginalCPU(EXE,'contract','unstable_ack');self.assertEqual(cpu.call(0x9285a8,line),0);self.assertEqual(len(cpu.writes),9)
 def test_machine_code_mutation_is_caught_by_wire_oracle(self):
  line=(FIRMWARE/'EVO_L_#00563.s19').read_text().splitlines()[1];expected=line[:2].encode()+bytes.fromhex(line[2:]);cpu=OriginalCPU(EXE,'contract');cpu.u.mem_write(0x9286b1,b'\x03');self.assertEqual(cpu.call(0x9285a8,line),1)
  self.assertNotEqual(cpu.writes,[expected]);self.assertEqual(len(cpu.writes[0]),len(expected))
 def test_real_files_every_original_line_is_transported_unchanged(self):
  for name in ['EVO_L_#00563.s19','EVO_#01153.s19']:
   with self.subTest(firmware=name):
    cpu=OriginalCPU(EXE,'contract');lines=(FIRMWARE/name).read_text().splitlines()[1:]
    for line in lines:
     self.assertEqual(cpu.call(0x9285a8,line),1)
     self.assertEqual(cpu.writes[-1],line[:2].encode()+bytes.fromhex(line[2:]))
    self.assertEqual(len(cpu.writes),len(lines));self.assertEqual(cpu.call(0x92845c),1);self.assertEqual(cpu.writes[-1],bytes.fromhex('8585'))
if __name__=='__main__':unittest.main(verbosity=2)
