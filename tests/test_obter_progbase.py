import hashlib, http.server, json, os, subprocess, sys, tempfile, threading, unittest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
SCRIPT = os.path.join(ROOT, 'tools', 'obter_progbase.py')
DADOS = b'MZ' + b'omegas' * 1000


class _Handler(http.server.BaseHTTPRequestHandler):
    chamadas = 0

    def do_GET(self):
        type(self).chamadas += 1
        self.send_response(200)
        self.send_header('Content-Length', str(len(DADOS)))
        self.end_headers()
        self.wfile.write(DADOS)

    def log_message(self, *a):
        pass


class ObterProgBaseTest(unittest.TestCase):
    def setUp(self):
        _Handler.chamadas = 0
        self.srv = http.server.HTTPServer(('127.0.0.1', 0), _Handler)
        threading.Thread(target=self.srv.serve_forever, daemon=True).start()
        self.tmp = tempfile.mkdtemp()
        self.url = f'http://127.0.0.1:{self.srv.server_port}/x'

    def tearDown(self):
        self.srv.shutdown()

    def rodar(self, sha, *extra):
        return subprocess.run([sys.executable, '-B', SCRIPT, '--dest', self.tmp, '--url', self.url,
                               '--sha256', sha, '--bytes', str(len(DADOS)), *extra], capture_output=True, text=True)

    def test_baixa_confere_e_nao_baixa_de_novo(self):
        sha = hashlib.sha256(DADOS).hexdigest()
        r1 = self.rodar(sha)
        self.assertEqual(r1.returncode, 0, r1.stderr)
        self.assertTrue(os.path.exists(os.path.join(self.tmp, 'ProgBase.exe')))
        r2 = self.rodar(sha)
        self.assertEqual(r2.returncode, 0, r2.stderr)
        self.assertEqual(_Handler.chamadas, 1, 'segunda execução não pode baixar de novo (idempotente)')
        self.assertEqual(r1.stdout.strip(), r2.stdout.strip())

    def test_sha_errado_falha_e_nao_deixa_arquivo(self):
        r = self.rodar('0' * 64)
        self.assertNotEqual(r.returncode, 0)
        self.assertFalse(os.path.exists(os.path.join(self.tmp, 'ProgBase.exe')))
        self.assertEqual([f for f in os.listdir(self.tmp)], [], 'sem sobras de download parcial')

    def test_arquivo_corrompido_e_rebaixado(self):
        sha = hashlib.sha256(DADOS).hexdigest()
        self.assertEqual(self.rodar(sha).returncode, 0)
        with open(os.path.join(self.tmp, 'ProgBase.exe'), 'wb') as fh:
            fh.write(b'lixo')
        self.assertEqual(self.rodar(sha).returncode, 0)
        self.assertEqual(open(os.path.join(self.tmp, 'ProgBase.exe'), 'rb').read(), DADOS)

    def test_metadados_do_repo_estao_completos(self):
        meta = json.load(open(os.path.join(ROOT, 'docs', 'mapa-de-fontes', 'progbase.json'), encoding='utf-8'))
        self.assertRegex(meta['sha256'], r'^[0-9a-f]{64}$')
        self.assertGreater(meta['bytes'], 10_000_000)
        self.assertTrue(meta['origens'])


if __name__ == '__main__':
    unittest.main()
