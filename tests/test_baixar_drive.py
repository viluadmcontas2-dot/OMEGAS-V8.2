import hashlib, http.server, os, subprocess, sys, tempfile, threading, unittest

SCRIPT = os.path.join(os.path.dirname(__file__), '..', 'tools', 'baixar_drive.py')
DADOS = b'PK\x03\x04' + b'omegas' * 500


def servidor(corpo):
    class H(http.server.BaseHTTPRequestHandler):
        chamadas = 0

        def do_GET(self):
            type(self).chamadas += 1
            self.send_response(200)
            self.send_header('Content-Length', str(len(corpo)))
            self.end_headers()
            self.wfile.write(corpo)

        def log_message(self, *a):
            pass
    s = http.server.HTTPServer(('127.0.0.1', 0), H)
    threading.Thread(target=s.serve_forever, daemon=True).start()
    return s, H


class BaixarDriveTest(unittest.TestCase):
    def rodar(self, srv, tmp, *extra):
        return subprocess.run([sys.executable, '-B', SCRIPT, 'X', '--dest', tmp, '--nome', 'a.zip',
                               '--url', f'http://127.0.0.1:{srv.server_port}/x', *extra], capture_output=True, text=True)

    def test_baixa_e_nao_repete(self):
        srv, H = servidor(DADOS)
        tmp = tempfile.mkdtemp()
        args = ['--bytes', str(len(DADOS)), '--sha256', hashlib.sha256(DADOS).hexdigest()]
        self.assertEqual(self.rodar(srv, tmp, *args).returncode, 0)
        self.assertEqual(self.rodar(srv, tmp, *args).returncode, 0)
        self.assertEqual(H.chamadas, 1)
        srv.shutdown()

    def test_pagina_html_de_login_falha_sem_sobras(self):
        srv, _ = servidor(b'<!doctype html><html>login</html>')
        tmp = tempfile.mkdtemp()
        r = self.rodar(srv, tmp)
        self.assertNotEqual(r.returncode, 0)
        self.assertIn('HTML', r.stderr)
        self.assertEqual(os.listdir(tmp), [])
        srv.shutdown()

    def test_sha_errado_falha(self):
        srv, _ = servidor(DADOS)
        tmp = tempfile.mkdtemp()
        self.assertEqual(self.rodar(srv, tmp, '--sha256', '0' * 64).returncode, 3)
        self.assertEqual(os.listdir(tmp), [])
        srv.shutdown()


if __name__ == '__main__':
    unittest.main()
