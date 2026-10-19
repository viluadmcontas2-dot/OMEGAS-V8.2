"""Transporte PTY/TCP: preserva bytes e registra as duas direções, sem inventar respostas."""
import importlib.util
import io
import json
import os
from pathlib import Path
import socket
import tty
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('serial_virtual', ROOT / 'tools/progbase/serial_virtual.py')
transport = importlib.util.module_from_spec(spec)
spec.loader.exec_module(transport)


class SerialVirtualTest(unittest.TestCase):
    def test_fragmentos_e_respostas_preservados_sem_eco_extra(self):
        master, slave = os.openpty()
        tty.setraw(slave)
        client, server = socket.socketpair()
        log = io.StringIO()
        try:
            for chunk in (b'\x00', b'\x29\x03', b'\x86\xb2'):
                os.write(slave, chunk)
                self.assertTrue(transport.relay_once(master, client, log, 1))
                self.assertEqual(server.recv(4096), chunk)
            answer = b'\x53\x00\x53'
            server.sendall(answer)
            self.assertTrue(transport.relay_once(master, client, log, 1))
            self.assertEqual(os.read(slave, 4096), answer)
            rows = [json.loads(line) for line in log.getvalue().splitlines()]
            self.assertEqual([r['direction'] for r in rows], ['original->emulator'] * 3 + ['emulator->original'])
            self.assertEqual([bytes.fromhex(r['hex']) for r in rows], [b'\x00', b'\x29\x03', b'\x86\xb2', answer])
            self.assertEqual([r['sequence'] for r in rows], [1, 2, 3, 4])
            server.close()
            self.assertFalse(transport.relay_once(master, client, log, 1))
        finally:
            os.close(master)
            os.close(slave)
            client.close()
            server.close()


if __name__ == '__main__':
    unittest.main()
