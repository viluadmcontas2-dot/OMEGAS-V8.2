import json, os, sys, unittest
RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
sys.path.insert(0, os.path.join(RAIZ, 'tools', 'progbase'))
SAIDA = os.path.join(RAIZ, 'docs', 'progbase', 'inventario')
try:
    from inventario_comum import achar_exe, SHA_ESPERADO
    EXE = achar_exe()
except Exception:
    EXE = None


def J(n):
    return json.load(open(os.path.join(SAIDA, n), encoding='utf-8'))


@unittest.skipUnless(EXE, 'ProgBase.exe ausente (defina PROGBASE_EXE)')
class Inventario(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import pefile, hashlib
        cls.pe = pefile.PE(EXE, fast_load=True)
        cls.pe.parse_data_directories([pefile.DIRECTORY_ENTRY['IMAGE_DIRECTORY_ENTRY_RESOURCE']])
        cls.sha = hashlib.sha256(open(EXE, 'rb').read()).hexdigest()

    def test_sha(self):
        self.assertEqual(self.sha, SHA_ESPERADO)

    def test_dfm_contagem_contra_fonte(self):
        n = 0
        for t in self.pe.DIRECTORY_ENTRY_RESOURCE.entries:
            if t.id == 10:
                for e in t.directory.entries:
                    d = e.directory.entries[0].data.struct
                    if self.pe.get_data(d.OffsetToData, 4) == b'TPF0':
                        n += 1
                        self.assertTrue(os.path.isfile(os.path.join(SAIDA, 'dfm', e.name.decode() + '.txt')))
        self.assertEqual(n, 43)

    def test_i18n_contagem(self):
        for t in self.pe.DIRECTORY_ENTRY_RESOURCE.entries:
            if t.id == 10:
                for e in t.directory.entries:
                    if e.name.decode() == 'LANG_MAIN':
                        d = e.directory.entries[0].data.struct
                        lbl = json.loads(self.pe.get_data(d.OffsetToData, d.Size))['LBL']
        self.assertEqual(len(J('i18n.json')['chaves']), len(lbl))

    def test_classes_e_telas(self):
        c = J('classes.json')
        self.assertEqual(c['meta']['n_classes_app'], 101)
        self.assertEqual(len(J('telas.json')['telas']), 43)

    def test_sem_caminho_local(self):
        for n in ('backlog.md', 'telas.md', 'funcoes.md'):
            self.assertNotIn('/home/', open(os.path.join(SAIDA, n), encoding='utf-8').read())


if __name__ == '__main__':
    unittest.main()
