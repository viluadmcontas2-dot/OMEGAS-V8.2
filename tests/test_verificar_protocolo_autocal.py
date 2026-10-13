"""O monitor do AutoCal só LÊ (nenhuma escrita) e continua lendo todo o bloco A do original (PROTOCOLO-LANDI doc 17)."""
import os, subprocess, sys, unittest
ROOT = os.path.join(os.path.dirname(__file__), '..')

class VerificarProtocoloAutoCal(unittest.TestCase):
    def test_monitor_so_le_e_cobre_o_bloco_a(self):
        r = subprocess.run([sys.executable, '-I', os.path.join(ROOT, 'tools/sessoes/verificar_protocolo_autocal.py')], capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertIn('Escritas no monitor (deve ser nenhuma): nenhuma', r.stdout)
        self.assertNotIn('AUSENTE', r.stdout)

if __name__ == '__main__':
    unittest.main()
