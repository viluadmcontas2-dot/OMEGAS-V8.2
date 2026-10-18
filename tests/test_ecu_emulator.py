"""ECU emulada (tools/emulacao/ecu_emulator.py) contra a captura Portmon real ProgBase x MP48.

A captura não fica no repo: caminho em ECU_CAPTURA (padrão: scratchpad da sessão). Sem ela, os testes que
dependem da captura são pulados; (b), (c) e (d) também rodam sem captura.
Regra do instante (a): ver EcuEmulada.regenerar e docs/evidence/2026-10-18-ecu-emulada.md.
"""
import os
import socket
import sys
import threading
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.emulacao.ecu_emulator import (EcuEmulada, NAO_MODELADO, ler_captura, pedido,  # noqa: E402
                                         servir, soma, tamanho_pedido)

CAPTURA = os.environ.get('ECU_CAPTURA', '/tmp/claude-0/-home-user/57e20309-4640-5e31-b02d-4b2faf9cf2b3/'
                                        'scratchpad/portmon/x/PortmonLOGNOVO.LOG')
TEM_CAPTURA = os.path.exists(CAPTURA)
_cache = {}


def ecu_carregada():
    if 'ecu' not in _cache:
        e = EcuEmulada()
        e.carregar_captura(CAPTURA)
        _cache['ecu'] = e
    return _cache['ecu']


ESCRITA_FLAG_CONF3 = bytes.fromhex('35FA0020000000 4F'.replace(' ', ''))
LEITURA_FLAG_CONF3 = bytes.fromhex('29FA0023')
ACK_VAZIO = bytes.fromhex('530053')


@unittest.skipUnless(TEM_CAPTURA, 'captura Portmon ausente')
class RegeneracaoCaptura(unittest.TestCase):
    def test_a_todos_os_pares_distintos_regenerados_byte_a_byte(self):
        e = ecu_carregada()
        ok, falha = e.regenerar()
        self.assertGreaterEqual(len(ok), 2000)
        self.assertEqual(falha, set(), f'{len(falha)} pares divergentes')
        escritas = [(p, r) for p, r in e.transacoes if p[0] in (0x35, 0x12, 0x14)]
        self.assertEqual(sum(1 for p, _ in escritas if p[0] == 0x35), 19)
        for p, r in escritas:          # escritas saem da regra, não de consulta
            self.assertIn((p, r), ok)
            self.assertEqual(r, p + ACK_VAZIO)

    def test_a_framing_e_checksum_da_captura(self):
        trans = ecu_carregada().transacoes
        for p, r in trans:
            if len(p) > 1:
                self.assertEqual(soma(p[:-1]), p[-1])
                self.assertEqual(tamanho_pedido(p), len(p))
                corpo = r[len(p):]
                self.assertEqual(r[:len(p)], p)                     # eco
                self.assertEqual(len(corpo), corpo[1] + 3)          # ACK n dados ck
                self.assertEqual(soma(corpo[:-1]), corpo[-1])       # soma sem o eco

    def test_a_previsao_estrita_apos_escrita(self):
        cont, pos = ecu_carregada().prever()
        # leitura logo após escrita: 3 batem; 13 mudaram por AutoCal/firmware (NÃO MODELADO)
        self.assertEqual(sum(1 for *_, hit in pos if hit), 3)
        self.assertEqual(len(pos), 16)


class EscritaFlagConf3(unittest.TestCase):
    def _checar(self, e):
        self.assertEqual(soma(ESCRITA_FLAG_CONF3[:-1]), 0x4F)
        self.assertEqual(e.responder(ESCRITA_FLAG_CONF3), ESCRITA_FLAG_CONF3 + ACK_VAZIO)
        resp = e.responder(LEITURA_FLAG_CONF3)
        corpo = bytes.fromhex('53 04 20 00 00 00'.replace(' ', ''))
        self.assertEqual((0x53 + 0x04 + 0x20) & 0xFF, 0x77)
        self.assertEqual(resp, LEITURA_FLAG_CONF3 + corpo + bytes([0x77]))

    def test_b_sem_captura(self):
        self._checar(EcuEmulada())

    @unittest.skipUnless(TEM_CAPTURA, 'captura Portmon ausente')
    def test_b_com_captura(self):
        e = EcuEmulada()
        e.carregar_captura(CAPTURA)
        antes = e.responder(LEITURA_FLAG_CONF3)
        self.assertEqual(antes, LEITURA_FLAG_CONF3 + bytes.fromhex('530400000000 57'.replace(' ', '')))
        self._checar(e)


class Mutacao(unittest.TestCase):
    def test_c_checksum_errado_no_pedido_so_eco_sem_ack(self):
        e = EcuEmulada()
        e.responder(ESCRITA_FLAG_CONF3)
        estado = dict(e.estado)
        for certo in (LEITURA_FLAG_CONF3, ESCRITA_FLAG_CONF3, pedido(0x48, 0x01)):
            errado = certo[:-1] + bytes([(certo[-1] + 1) & 0xFF])
            self.assertEqual(e.responder(errado), errado)   # só o eco; nenhum 53/CA inventado
            self.assertEqual(e.ultimo_rotulo, NAO_MODELADO)
        self.assertEqual(e.estado, estado)

    @unittest.skipUnless(TEM_CAPTURA, 'captura Portmon ausente')
    def test_c_captura_nao_tem_pedido_com_checksum_errado(self):
        ruins = [p for p, _ in ecu_carregada().transacoes if len(p) > 1 and soma(p[:-1]) != p[-1]]
        self.assertEqual(ruins, [])   # por isso a reação da ECU fica NÃO MODELADO

    def test_c_autocal_ack_generico_rotulado(self):
        e = EcuEmulada()
        p = pedido(0x02, 0x24, 0x04, 0x04)
        self.assertEqual(p, bytes.fromhex('022404042E'))
        self.assertEqual(e.responder(p), p + ACK_VAZIO)
        self.assertEqual(e.ultimo_rotulo, NAO_MODELADO)

    def test_c_pedido_nunca_visto_nao_modelado(self):
        e = EcuEmulada()
        self.assertEqual(e.responder(pedido(0x29, 0x77, 0x00)), pedido(0x29, 0x77, 0x00))
        self.assertEqual(e.ultimo_rotulo, NAO_MODELADO)


class Idempotencia(unittest.TestCase):
    def test_d_escrita_e_leitura_repetidas(self):
        e = EcuEmulada()
        r1 = e.responder(ESCRITA_FLAG_CONF3)
        s1 = dict(e.estado)
        r2 = e.responder(ESCRITA_FLAG_CONF3)
        self.assertEqual((r1, s1), (r2, e.estado))
        self.assertEqual(e.responder(LEITURA_FLAG_CONF3), e.responder(LEITURA_FLAG_CONF3))

    @unittest.skipUnless(TEM_CAPTURA, 'captura Portmon ausente')
    def test_d_carga_dupla_mesmo_estado(self):
        a, b = EcuEmulada(), EcuEmulada()
        a.carregar_captura(CAPTURA)
        b.carregar_captura(CAPTURA)
        self.assertEqual(a.estado, b.estado)
        self.assertEqual(len(ler_captura(CAPTURA)), len(a.transacoes))


class ServidorTcp(unittest.TestCase):
    def test_tcp_localhost(self):
        e = EcuEmulada()
        srv = servir(e, 0)
        threading.Thread(target=srv.serve_forever, daemon=True).start()
        try:
            with socket.create_connection(srv.server_address, timeout=2) as s:
                s.sendall(ESCRITA_FLAG_CONF3 + LEITURA_FLAG_CONF3)   # dois quadros colados
                esperado = ESCRITA_FLAG_CONF3 + ACK_VAZIO + LEITURA_FLAG_CONF3 + bytes.fromhex('53042000000077')
                buf = b''
                while len(buf) < len(esperado):
                    buf += s.recv(256)
            self.assertEqual(buf, esperado)
        finally:
            srv.shutdown()
            srv.server_close()


if __name__ == '__main__':
    unittest.main()
