"""Execução EMULADA (CPU x86-32, Unicorn) do setter da caixa 'Abilitazione anti-stallo' do ProgBase original.

Prova qual quadro serial o código original gera (setter 0x42E8D8 caso 4 -> TAebVector.SetValues 0x978F8C ->
0x97909C -> 0x92617C -> ... -> WriteFile). Os testes pulam se o .exe, unicorn, capstone ou pefile faltarem
(CI sem eles). Não dependem de /home/user/protocolo-landi."""
import importlib.util
import os
import sys
import unittest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
sys.path.insert(0, os.path.join(ROOT, 'tools', 'emulacao'))

FALTA = [m for m in ('unicorn', 'capstone', 'pefile') if importlib.util.find_spec(m) is None]
if not FALTA:
    import cpu_setter_original as cso  # noqa: E402  (RED: módulo ainda não existe)
    EXE = cso.achar_exe(None)
else:
    cso = None
    EXE = None


def cks(b):
    return sum(b) & 0xFF


def hexq(q):
    return bytes(q).hex(' ').upper()


@unittest.skipIf(FALTA, 'faltam módulos: %s' % FALTA)
@unittest.skipUnless(EXE, 'ProgBase.exe ausente')
class TestSetterOriginal(unittest.TestCase):
    def un(self, r):
        self.assertEqual(len(r['quadros']), 1, r)
        return r['quadros'][0]

    def test_marcar_de_zero_gera_quadro_previsto(self):
        r = cso.emular(EXE, acao='marcar', flag3=(0x0000, 0x0000))
        self.assertEqual(hexq(self.un(r)), '35 FA 00 20 00 00 00 4F')
        self.assertEqual(r['cache_final'], (0x0020, 0x0000))
        self.assertIsNone(r['erro'])

    def test_desmarcar_apaga_so_o_bit_0x20(self):
        r = cso.emular(EXE, acao='desmarcar', flag3=(0x0020, 0x0000))
        self.assertEqual(hexq(self.un(r)), '35 FA 00 00 00 00 00 2F')
        self.assertEqual(r['cache_final'], (0x0000, 0x0000))

    def test_ler_modificar_escrever_preserva_outros_bits(self):
        r = cso.emular(EXE, acao='marcar', flag3=(0x0C10, 0xABCD))
        q = self.un(r)
        self.assertEqual(bytes(q[3:7]), bytes([0x30, 0x0C, 0xCD, 0xAB]))
        self.assertEqual(q[-1], cks(q[:-1]))
        self.assertEqual(r['cache_final'], (0x0C30, 0xABCD))
        r = cso.emular(EXE, acao='desmarcar', flag3=(0xFFFF, 0xFFFF))
        q = self.un(r)
        self.assertEqual(bytes(q[3:7]), bytes([0xDF, 0xFF, 0xFF, 0xFF]))
        self.assertEqual(q[-1], cks(q[:-1]))

    def test_modo_buffered_e_byte_a_byte_dao_o_mesmo_quadro(self):
        a = cso.emular(EXE, acao='marcar', flag3=(0, 0), buffered=0)
        b = cso.emular(EXE, acao='marcar', flag3=(0, 0), buffered=1)
        self.assertEqual(b''.join(a['quadros']), b''.join(b['quadros']))
        self.assertEqual(hexq(self.un(b)), '35 FA 00 20 00 00 00 4F')

    def test_calibracao_flag_conf1_igual_ao_fio(self):
        # PortmonLOGNOVO.LOG linha 31284 (OBS-FIO): FLAG_CONF1 = 2C86 1051
        r = cso.calibrar_flag_conf1(EXE, (0x2C86, 0x1051))
        self.assertEqual(hexq(self.un(r)), '35 03 00 86 2C 51 10 4B')

    def test_executa_codigo_original_de_verdade(self):
        r = cso.emular(EXE, acao='marcar', flag3=(0, 0))
        for f in (0x42E8D8, 0x978F8C, 0x97909C, 0x92617C, 0x922C1C, 0x922FD0, 0x923608, 0x923860, 0x91DF00):
            self.assertIn(f, r['funcoes_executadas'], hex(f))
        self.assertGreater(r['instrucoes'], 500)

    def test_determinismo(self):
        a = cso.emular(EXE, acao='marcar', flag3=(0x1, 0x2))
        b = cso.emular(EXE, acao='marcar', flag3=(0x1, 0x2))
        self.assertEqual(a, b)

    def test_mutacao_de_um_byte_emulado_muda_o_quadro(self):
        # 42E9EF: 'or ecx,0x20' = 83 C9 20 ; o imediato fica em 0x42E9F1
        base = cso.emular(EXE, acao='marcar', flag3=(0, 0))
        mut = cso.emular(EXE, acao='marcar', flag3=(0, 0), patches={0x42E9F1: 0x40})
        self.assertNotEqual(base['quadros'], mut['quadros'])
        self.assertEqual(hexq(self.un(mut)), '35 FA 00 40 00 00 00 6F')

    def test_mutacao_do_stub_da_serial_muda_o_resultado(self):
        # sem ECU respondendo (sem eco/ACK) o original NÃO considera gravado: cache não muda
        r = cso.emular(EXE, acao='marcar', flag3=(0, 0), ecu_responde=False)
        self.assertIsNone(r['erro'])  # termina limpo (original fecha a porta: CloseHandle), não por stub faltando
        self.assertEqual(r['cache_final'], (0x0000, 0x0000))

    def test_versao_de_protocolo_1_usa_cabecalho_curto(self):
        r = cso.emular(EXE, acao='marcar', flag3=(0, 0), versao_proto=1)
        self.assertEqual(hexq(self.un(r)), '34 FA 20 00 00 00 4E')

    def test_nao_grava_se_80D_ligado(self):
        r = cso.emular(EXE, acao='marcar', flag3=(0, 0), obj80D=1)
        self.assertEqual(r['quadros'], [])
        self.assertEqual(r['cache_final'], (0x0020, 0x0000))


if __name__ == '__main__':
    unittest.main()
