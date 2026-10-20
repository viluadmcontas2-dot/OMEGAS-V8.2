"""Classificador programa × biblioteca das funções sem nome do ProgBase (tools/progbase/classificar_funcoes.py)."""
import gzip, hashlib, json, os, subprocess, sys, tempfile, unittest
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
sys.path.insert(0, os.path.join(ROOT, 'tools', 'progbase'))
import classificar_funcoes as cf  # noqa: E402

SCRIPT = os.path.join(ROOT, 'tools', 'progbase', 'classificar_funcoes.py')
DEC = os.path.join(ROOT, 'docs', 'progbase', 'decomp')
EXE = os.environ.get('PROGBASE_EXE') or os.environ.get('PB_EXE') or ''


def fn(a, nome=None, unidade=None, cr=()):
    return {'a': '0x%08X' % a, 'nome': nome, 'unidade': unidade, 'un_fonte': 'OBS-COD' if unidade else None, 'cr': list(cr)}


SINT = [
    fn(0x401000, 'TFormX.vmt_000', '@:Config'),
    fn(0x401010),                                   # entre duas âncoras @:Config -> programa/alta
    fn(0x401020, 'TFormX.Click', '@:Config'),
    fn(0x401030),                                   # entre programa e biblioteca -> chamadores
    fn(0x401040, 'TControl.X', 'Vcl.Controls'),
    fn(0x401050, unidade='System.Generics.Collections'),  # genérico: não é âncora
    fn(0x401060),                                   # Vcl.Controls .. imports => biblioteca/media
    fn(0x401070, '__allmul'),
    fn(0x401080, cr=['0x00401000', '0x00401040']),  # vizinhos biblioteca/biblioteca -> biblioteca
]


class Classificador(unittest.TestCase):
    def test_categorias(self):
        self.assertEqual(cf.cat_unidade('@:Config'), 'programa')
        self.assertEqual(cf.cat_unidade('@Shellctrls:AebShellCtrls'), 'programa')
        self.assertEqual(cf.cat_unidade('Vcl.ComCtrls'), 'biblioteca')
        self.assertEqual(cf.cat_unidade('VCLTee.Chart'), 'biblioteca')
        self.assertEqual(cf.cat_nome('@Vcl@Controls@TControl@Click$qqrv', {})[0], 'biblioteca')
        self.assertEqual(cf.cat_nome('@@Cryptengine@Initialize', {})[0], 'programa')
        self.assertEqual(cf.cat_nome('GetModuleHandleW', {})[0], 'biblioteca')
        self.assertEqual(cf.cat_nome('@TAebProtocol@X$qqrv', {'TAebProtocol': '@:AebProtocol'})[0], 'programa')

    def test_sintetico(self):
        F = [dict(f) for f in SINT]
        F[3]['cr'] = ['0x00401000']   # chamador só programa
        linhas, anc = cf.classificar(F, {})
        r = {l[0]: (l[1], l[2], l[3]) for l in linhas}
        self.assertEqual(r['0x00401010'], ('programa', 'alta', 'INFERIDO'))
        self.assertEqual(r['0x00401030'], ('programa', 'baixa', 'INFERIDO'))
        self.assertEqual(r['0x00401060'], ('biblioteca', 'media', 'INFERIDO'))
        self.assertNotIn('0x00401050', r)
        F[3]['cr'] = ['0x00401000', '0x00401040']   # chamadores mistos -> DESCONHECIDO
        r = {l[0]: (l[1], l[3]) for l in cf.classificar(F, {})[0]}
        self.assertEqual(r['0x00401030'], ('desconhecida', 'DESCONHECIDO'))

    def test_idempotente(self):
        with tempfile.TemporaryDirectory() as d:
            src = os.path.join(d, 'funcoes.json.gz')
            with gzip.open(src, 'wt') as fh:
                json.dump({'funcoes': SINT}, fh)
            hs = []
            for _ in range(2):
                subprocess.run([sys.executable, '-B', SCRIPT, src, d], check=True, capture_output=True)
                hs.append(tuple(hashlib.sha256(open(os.path.join(d, n), 'rb').read()).hexdigest()
                                for n in ('classificacao-funcoes.json', 'classificacao-funcoes.md')))
            self.assertEqual(hs[0], hs[1])

    def test_saida_versionada_coerente(self):
        p = os.path.join(DEC, 'classificacao-funcoes.json')
        d = json.load(open(p, encoding='utf-8'))
        self.assertEqual(d['alvo_n'], len(d['linhas']))
        self.assertEqual(sum(d['alvo_por_categoria'].values()), d['alvo_n'])
        self.assertGreater(d['alvo_n'], 7000)
        self.assertGreater(d['validacao_deixa_um_de_fora']['taxa_pct'], 90)
        self.assertEqual(sum(d['todas_funcoes_por_categoria'].values()),
                         len(json.load(gzip.open(os.path.join(DEC, 'funcoes.json.gz')))['funcoes']))

    @unittest.skipUnless(EXE and os.path.exists(EXE), 'ProgBase.exe ausente (PROGBASE_EXE)')
    def test_alvos_estao_no_text_do_exe(self):
        import pefile
        pe = pefile.PE(EXE, fast_load=True)
        ib = pe.OPTIONAL_HEADER.ImageBase
        exe = [(ib + s.VirtualAddress, ib + s.VirtualAddress + s.Misc_VirtualSize) for s in pe.sections
               if s.Characteristics & 0x20000000]   # IMAGE_SCN_MEM_EXECUTE
        d = json.load(open(os.path.join(DEC, 'classificacao-funcoes.json'), encoding='utf-8'))
        for l in d['linhas']:
            a = int(l[0], 16)
            if not any(lo <= a < hi for lo, hi in exe):
                self.assertEqual((l[1], l[4]), ('desconhecida', 'fora_do_text_provavel_dado'), l[0])


if __name__ == '__main__':
    unittest.main()
