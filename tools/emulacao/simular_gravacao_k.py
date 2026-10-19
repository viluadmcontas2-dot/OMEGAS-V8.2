#!/usr/bin/env python3
"""Conta transações da gravação da Curva K / Mapa K: fluxo ANTIGO x NOVO, contra o emulador da ECU.

Só conta quadros e confere o estado final da ECU emulada (sem tempo). Os bytes dos quadros são os do app:
leitura de linha 2A 54 00 r, escrita de célula 14 54 00 r c v, insertion 35 03 00 86 {2C|24} 51 10,
vetor da curva 29 61 01 e escrita indexada 14 61 01 idx lo hi. Uso: python3 tools/emulacao/simular_gravacao_k.py
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ecu_emulator import EcuEmulada, montar_quadro, soma


def frame(body):
    return bytes(body) + bytes([soma(body)])


def ler_linha(r): return frame([0x2A, 0x54, 0x00, r])
def escrever_celula(r, c, v): return frame([0x14, 0x54, 0x00, r, c, v])
def insertion(on): return frame([0x35, 0x03, 0x00, 0x86, 0x2C if on else 0x24, 0x51, 0x10])
LER_CURVA = frame([0x29, 0x61, 0x01])
def escrever_ponto(i, raw): return frame([0x14, 0x61, 0x01, i, raw & 0xFF, raw >> 8])


class Barramento:
    def __init__(self):
        self.ecu, self.n = EcuEmulada(), 0
        for r in range(13):
            p = ler_linha(r)
            self.ecu.estado[p] = ('quadro', 0x53, bytes([100 + r] * 12))
        self.ecu.estado[LER_CURVA] = ('quadro', 0x53, b''.join((0x4000).to_bytes(2, 'little') for _ in range(30)))

    def tx(self, p):
        self.n += 1
        resp = self.ecu.responder(p)
        assert resp[:len(p)] == p and resp[len(p)] == 0x53, resp.hex()  # eco + ACK por quadro
        return resp

    def linha(self, r):
        self.tx(ler_linha(r))
        return list(self.ecu.estado[ler_linha(r)][2])

    def curva(self):
        self.tx(LER_CURVA)
        d = self.ecu.estado[LER_CURVA][2]
        return [d[i] | d[i + 1] << 8 for i in range(0, 60, 2)]


def mapa_antigo(cells):
    b = Barramento(); linhas = sorted({r for r, _, _ in cells})
    for r in linhas: b.linha(r)                      # conferência prévia
    b.tx(insertion(True))
    for r, c, v in cells: b.tx(escrever_celula(r, c, v))
    b.tx(insertion(False))
    for r in linhas:
        lida = b.linha(r)
        assert all(lida[c] == v for rr, c, v in cells if rr == r)
    return b.n


def mapa_novo(cells):
    b = Barramento(); linhas = sorted({r for r, _, _ in cells})
    b.tx(insertion(True))
    for r, c, v in cells: b.tx(escrever_celula(r, c, v))
    b.tx(insertion(False))
    for r in linhas:
        lida = b.linha(r)
        assert all(lida[c] == v for rr, c, v in cells if rr == r)
    return b.n


def curva(pontos, confere_antes=True):
    b = Barramento()
    b.curva()                                        # vetor inteiro (1 transação)
    for i, raw in pontos: b.tx(escrever_ponto(i, raw))
    got = b.curva()                                  # vetor inteiro de volta (1 transação)
    assert all(got[i] == raw for i, raw in pontos), got
    return b.n


def curva_vetor_unico(pontos):
    """PROPOSTA (não ativa): AutoCalProtocol.writeVectorU16 = 1 quadro 0x37 com os 30 pontos. Exige OK do dono e ECU real."""
    b = Barramento(); atual = b.curva()
    for i, raw in pontos: atual[i] = raw
    corpo = b''.join(v.to_bytes(2, 'little') for v in atual)
    b.tx(frame([0x37, 0x61, len(corpo) + 2, 0x01] + list(corpo)))
    got = b.curva()
    assert all(got[i] == raw for i, raw in pontos), got
    return b.n


def curva_antiga_celula_a_celula(pontos):
    """Referência do que seria conferir ponto a ponto (leitura do vetor a cada escrita)."""
    b = Barramento(); b.curva()
    for i, raw in pontos:
        b.tx(escrever_ponto(i, raw)); b.curva()
    return b.n


def main():
    print('cenário                         antes  depois  redução')
    for nome, cells in (
        ('Mapa K 24 céls em 12 linhas', [(r, c, 120) for r in range(12) for c in (0, 5)]),
        ('Mapa K 24 céls em 2 linhas', [(r, c, 120) for r in (3, 4) for c in range(12)]),
        ('Mapa K 144 céls (12x12)', [(r, c, 120) for r in range(12) for c in range(12)]),
        ('Mapa K 1 célula', [(2, 2, 130)]),
    ):
        a, n = mapa_antigo(cells), mapa_novo(cells)
        print(f'{nome:<32}{a:>5}{n:>8}{(a - n) * 100 // a:>7}%')
    for nome, pts in (('Curva K 30 pontos', [(i, 0x4000 + 100 + i) for i in range(30)]),
                      ('Curva K 12 pontos', [(i, 0x4100 + i) for i in range(12)])):
        ref, novo = curva_antiga_celula_a_celula(pts), curva(pts)
        print(f'{nome:<32}{ref:>5}{novo:>8}{(ref - novo) * 100 // ref:>7}%   (referência: ler o vetor a cada ponto)')
        u = curva_vetor_unico(pts)
        print(f'  {nome} + vetor único 0x37 (proposta)   {novo:>3}{u:>8}{(novo - u) * 100 // novo:>7}%   (sobre o fluxo novo; precisa ECU real)')


if __name__ == '__main__':
    main()
