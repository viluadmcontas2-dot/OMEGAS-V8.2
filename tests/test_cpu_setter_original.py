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


@unittest.skipIf(FALTA, 'faltam módulos: %s' % FALTA)
@unittest.skipUnless(EXE, 'ProgBase.exe ausente')
class TestModoMarchaLenta(unittest.TestCase):
    """'Funcionamento al minimo' (Gas / Rientro a benzina / Benzina): RadioGasClick 0x4BFAD8 (OBS-COD).
    Handler: sub edx,1 (Tag 0 = Gas) / Tag 1 = RadioRientro / Tag 2 = RadioBenzina (DFM). Grava, nesta ordem,
    FLAG_CONF1 por 0x42D300 (caso 0x22) e GIRI_PER_BENZINA[0] por 0x43734C (código 0x4C), valor 0 ou 1100.0 (0x40913000:0)."""
    PRONTO = (0x2486, 0x1051)      # FLAG_CONF1 da ECU do dono (OBS-FIO LOG:508)
    GIRI = (0, 8500)               # GIRI_PER_BENZINA (OBS-FIO LOG:6988)

    def rodar(self, modo, flag1=None, giri=None, **kw):
        return cso.emular_modo_lenta(EXE, modo, flag1 or self.PRONTO, giri or self.GIRI, **kw)

    def test_gas_zera_a_rotacao_e_apaga_o_bit(self):
        r = self.rodar('gas', flag1=(0x2486, 0x1151), giri=(1100, 8500))
        self.assertEqual([hexq(q) for q in r['quadros']], ['35 03 00 86 24 51 10 43', '14 A1 00 00 00 00 B5'])
        self.assertEqual(r['cache_flag1'], (0x2486, 0x1051))
        self.assertEqual(r['cache_giri'], (0, 8500))
        self.assertIsNone(r['erro'])

    def test_volta_a_gasolina_1100_rpm_e_bit_apagado(self):
        r = self.rodar('volta')
        self.assertEqual([hexq(q) for q in r['quadros']], ['35 03 00 86 24 51 10 43', '14 A1 00 00 4C 04 05'])
        self.assertEqual(r['cache_giri'], (1100, 8500))

    def test_gasolina_1100_rpm_e_bit_0x0100_da_palavra_1(self):
        r = self.rodar('gasolina')
        self.assertEqual([hexq(q) for q in r['quadros']], ['35 03 00 86 24 51 11 44', '14 A1 00 00 4C 04 05'])
        self.assertEqual(r['cache_flag1'], (0x2486, 0x1151))   # 0x1051 | 0x0100 (NÃO 0x0400: isso é só com [obj+0x80D] != 0)
        self.assertEqual(r['cache_giri'], (1100, 8500))

    def test_ordem_do_handler_flag_primeiro_depois_rotacao(self):
        r = self.rodar('gasolina')
        self.assertEqual([q[0] for q in r['quadros']], [0x35, 0x14])
        self.assertEqual(len(r['quadros_flag1']), 1)
        self.assertEqual(len(r['quadros_giri']), 1)

    def test_preserva_outros_bits_e_a_segunda_palavra_de_giri(self):
        r = self.rodar('gasolina', flag1=(0xABCD, 0x2A55), giri=(0, 7777))
        self.assertEqual(r['cache_flag1'], (0xABCD, 0x2B55))
        self.assertEqual(r['cache_giri'], (1100, 7777))
        self.assertEqual(hexq(r['quadros'][1]), '14 A1 00 00 4C 04 05')   # só o elemento 0 vai no quadro
        r = self.rodar('gas', flag1=(0xABCD, 0xFFFF), giri=(900, 7777))
        self.assertEqual(r['cache_flag1'], (0xABCD, 0xFEFF))

    def test_com_80D_ligado_o_bit_e_outro(self):
        r = self.rodar('gasolina', obj80D=1)
        self.assertEqual(r['cache_flag1'], (0x2486, 0x1451))   # família [obj+0x80D]!=0: bit 0x0400 e GIRI_PER_BENZINA_LR (código 176)
        self.assertEqual(hexq(r['quadros'][1])[:8], '14 B0 00')

    def test_checksum_de_todos_os_quadros(self):
        for modo in ('gas', 'volta', 'gasolina'):
            for q in self.rodar(modo)['quadros']:
                self.assertEqual(q[-1], cks(q[:-1]), hexq(q))

    def test_handler_original_nao_confere_o_resultado_da_primeira_gravacao(self):
        # sem ECU: o 1º SetValues falha (cache do FLAG_CONF1 não muda) e MESMO ASSIM o handler segue para a rotação
        # (0x4BFB1E..0x4BFB44 não testam AL): o original pode deixar a ECU meio configurada sem avisar.
        r = self.rodar('gasolina', ecu_responde=False)
        self.assertEqual(r['cache_flag1'], (0x2486, 0x1051))
        self.assertEqual(r['cache_giri'][0], 1100)
        self.assertTrue(r['handler_executado'])

    def test_handler_e_chamada_direta_dao_os_mesmos_quadros(self):
        for modo in ('gas', 'volta', 'gasolina'):
            a = self.rodar(modo)
            b = self.rodar(modo, handler=False)
            self.assertEqual(a['quadros'], b['quadros'], modo)

    def test_executa_o_handler_radiogasclick_de_verdade(self):
        r = self.rodar('volta')
        for f in (0x4BFAD8, 0x42D300, 0x43734C, 0x9795F0):
            self.assertIn(f, r['funcoes_executadas'], hex(f))

    def test_determinismo(self):
        self.assertEqual(self.rodar('volta'), self.rodar('volta'))

    def test_mutacao_da_constante_1100_muda_o_quadro(self):
        # 0x4BFB5E: push 0x40913000 (68 00 30 91 40): o byte 0x91 fica em 0x4BFB61; 0x92 vira 2200.0 (prova que o 1100 vem do código)
        base = self.rodar('volta')
        mut = cso.emular_modo_lenta(EXE, 'volta', self.PRONTO, self.GIRI, patches={0x4BFB61: 0x92})
        self.assertNotEqual(base['quadros'][1], mut['quadros'][1])
        self.assertEqual(hexq(mut['quadros'][1]), '14 A1 00 00 98 08 51')   # 2200 = 0x0898


if __name__ == '__main__':
    unittest.main()
