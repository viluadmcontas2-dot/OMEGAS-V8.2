import os, sys, unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'tools', 'sessoes'))
import portmon_ler_vetor as p


def bloco(dados):
    n = len(dados)
    ck = (0x53 + n + sum(dados)) & 0xFF
    linhas = ['10  0.0  ProgBase.exe  IRP_MJ_WRITE  Silabser0  Length 4: 29 FA 00 23 ', '10  0.0  SUCCESS  ']
    for b in [0x29, 0x53, n, *dados, ck]:
        linhas += ['11  0.0  ProgBase.exe  IRP_MJ_READ  Silabser0  Length 1', f'11  0.0  SUCCESS  Length 1: {b:02X} ']
    return linhas


class LerVetorTest(unittest.TestCase):
    def test_pedido_do_flag_conf3(self):
        self.assertEqual(p.frame_pedido(250), '29 FA 00 23')

    def test_le_u16_e_confere_checksum(self):
        r = p.ler(bloco([0x20, 0x00, 0x00, 0x00]), 250)
        self.assertEqual(r[0]['u16'], [0x20, 0])
        self.assertTrue(r[0]['checksum_ok'])

    def test_tudo_zero_como_no_log_real(self):
        r = p.ler(bloco([0, 0, 0, 0]), 250)
        self.assertEqual(r[0]['u16'], [0, 0])
        self.assertTrue(r[0]['checksum_ok'])

    def test_log_real_se_existir(self):
        real = '/tmp/claude-0/-home-user/57e20309-4640-5e31-b02d-4b2faf9cf2b3/scratchpad/portmon/x/PortmonLOGNOVO.LOG'
        if not os.path.exists(real):
            self.skipTest('log real fora do repositório')
        r = p.ler(open(real, encoding='latin1').read().split('\n'), 250)
        self.assertEqual(len(r), 3)
        self.assertTrue(all(x['u16'] == [0, 0] and x['checksum_ok'] for x in r))


if __name__ == '__main__':
    unittest.main()
