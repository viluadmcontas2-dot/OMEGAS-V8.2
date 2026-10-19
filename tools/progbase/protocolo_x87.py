#!/usr/bin/env python3
"""Conversão raw <-> físico dos objetos TAebNumber/Vector/Matrix: referência em Python + execução da rotina
ORIGINAL do ProgBase.exe (x87) dentro do unicorn, para provar que a fórmula desmontada é a que o programa usa.

OBS-COD (ProgBase.exe 4.2.0.6, sha256 8a2d297c...):
- RE:0x009757DC  raw -> físico   (cdecl: self, raw:u32 ; resultado em ST0)
- RE:0x009752C8  físico -> raw   (cdecl: self, físico:double, ponteiro_valor_atual ; resultado em EAX)
- RE:0x009766CC  carrega Coeffs (A,B,C,D = doubles em self+0x88, +0x90, +0x98, +0xA0)
Campos do objeto: +0x54 DataMask · +0x58 DataLength · +0x68 Signed · +0x69 Single(float32) · +0x7C Transformation
(0 ttNone, 1 ttFormula, 2 ttTable, 3 ttNonLinearTable, 4 ttCustom) · +0xA8 Precision · +0xB0/+0xB8 ValueMin/Max ·
+0xC8 tabela habilitada · +0xCC raw[] · +0xD0 físico[] · +0xD4 contagem.
Constantes de erro: -10000002.0 (divisão por zero da fórmula) e -10000001.0 (valor ausente da tabela).

Só leitura do .exe; nunca roda o programa, só as duas rotinas, em memória emulada.
"""
import math
import os
import struct
import sys

ERRO_FORMULA = -10000002.0
ERRO_TABELA = -10000001.0
VA_RAW_PARA_FIS = 0x009757DC
VA_FIS_PARA_RAW = 0x009752C8


# ----------------------------------------------------------------------------------------------- referência
def _sinal(raw, tamanho, sinal):
    raw &= (1 << (8 * tamanho)) - 1
    if sinal and raw >= 1 << (8 * tamanho - 1):
        raw -= 1 << (8 * tamanho)
    return raw


def _arredonda_precisao(x, precisao):
    """Ao múltiplo de `precisao` mais próximo; empate vai para o MAIOR (RE:0x00975B96..0x00975C21: compara
    (hi - x) com (x - lo); só escolhe `lo` quando (hi - x) > (x - lo)). Confirmado contra a rotina original."""
    if precisao == 0.0:
        return x
    q = x / precisao
    lo = math.floor(q) * precisao
    hi = math.ceil(q) * precisao
    return lo if (hi - x) > (x - lo) else hi


def raw_para_fisico(raw, tamanho=1, sinal=False, coef=(1.0, 0.0, 0.0, 1.0), precisao=0.0, mascara=None,
                    transformacao='ttFormula'):
    """Referência (Python) do caminho ttNone/ttFormula do RE:0x009757DC."""
    mascara = (1 << (8 * tamanho)) - 1 if mascara is None else mascara
    r = _sinal(raw & mascara, tamanho, sinal)
    if transformacao == 'ttNone':
        fis = float(r)
    else:
        a, b, c, d = coef
        den = r * c + d
        fis = ERRO_FORMULA if den == 0 else (r * a + b) / den
        if den == 0:
            return fis
    return _arredonda_precisao(fis, precisao)


def fisico_para_raw(fis, tamanho=1, sinal=False, coef=(1.0, 0.0, 0.0, 1.0), vmin=0.0, vmax=0.0,
                    transformacao='ttFormula'):
    """Referência (Python) do caminho ttNone/ttFormula do RE:0x009752C8 (sem mesclagem de máscara)."""
    # O par inteiro está ativo quando qualquer extremo difere de zero.
    if vmin != 0.0 or vmax != 0.0:
        if fis > vmax:
            fis = vmax
        elif fis < vmin:
            fis = vmin
    if transformacao == 'ttNone':
        return int(fis)  # original chama trunc, sem deslocamento de meio ponto
    else:
        a, b, c, d = coef
        t = fis * c - a
        if t == 0:
            return int(ERRO_FORMULA)
        x = (b - fis * d) / t
    x = x - 0.5 if sinal and x < 0 else x + 0.5
    return int(x)  # trunc para zero


# ----------------------------------------------------------------------------------------------- execução original
class ConversorOriginal:
    """Carrega o PE do ProgBase em memória emulada (unicorn) e chama as rotinas de conversão originais."""

    def __init__(self, exe):
        import pefile
        from unicorn import Uc, UC_ARCH_X86, UC_MODE_32
        self._b = open(exe, 'rb').read()
        pe = pefile.PE(exe, fast_load=True)
        base = pe.OPTIONAL_HEADER.ImageBase
        self.mu = Uc(UC_ARCH_X86, UC_MODE_32)
        for s in pe.sections:
            va = base + s.VirtualAddress
            tam = (max(s.Misc_VirtualSize, s.SizeOfRawData) + 0xFFF) & ~0xFFF
            self.mu.mem_map(va, tam)
            self.mu.mem_write(va, self._b[s.PointerToRawData:s.PointerToRawData + s.SizeOfRawData])
        self.STACK, self.OBJ, self.RET, self.OUT = 0x7000000, 0x6000000, 0x6100000, 0x6200000
        self.mu.mem_map(self.STACK - 0x100000, 0x200000)
        self.mu.mem_map(self.OBJ, 0x10000)
        self.mu.mem_map(self.RET, 0x1000)
        self.mu.mem_map(self.OUT, 0x1000)
        # retorno: fstp qword [OUT]; hlt
        self.mu.mem_write(self.RET, b'\xdd\x1d' + struct.pack('<I', self.OUT) + b'\xf4')
        self.RET_SEM_X87 = self.RET + 0x10
        self.mu.mem_write(self.RET_SEM_X87, b'\xf4')

    def _objeto(self, mascara, tamanho, sinal, transf, coef, precisao, vmin, vmax, single=False):
        self.mu.mem_write(self.OBJ, b'\x00' * 0x200)
        w32 = lambda o, v: self.mu.mem_write(self.OBJ + o, struct.pack('<I', v & 0xFFFFFFFF))  # noqa: E731
        wd = lambda o, v: self.mu.mem_write(self.OBJ + o, struct.pack('<d', v))  # noqa: E731
        w32(0x54, mascara)
        w32(0x58, tamanho)
        self.mu.mem_write(self.OBJ + 0x68, bytes([1 if sinal else 0]))
        self.mu.mem_write(self.OBJ + 0x69, bytes([1 if single else 0]))
        w32(0x7C, {'ttNone': 0, 'ttFormula': 1, 'ttTable': 2, 'ttNonLinearTable': 3, 'ttCustom': 4}[transf])
        for i, c in enumerate(coef):
            wd(0x88 + 8 * i, c)
        wd(0xA8, precisao)
        wd(0xB0, vmin)
        wd(0xB8, vmax)

    def _chamar(self, va, args, ret):
        from unicorn.x86_const import UC_X86_REG_ESP, UC_X86_REG_EBP, UC_X86_REG_EAX, UC_X86_REG_FPSW, UC_X86_REG_FPCW
        esp = self.STACK - 0x100
        dados = struct.pack('<I', ret) + args
        self.mu.mem_write(esp, dados)
        self.mu.reg_write(UC_X86_REG_ESP, esp)
        self.mu.reg_write(UC_X86_REG_EBP, 0)
        self.mu.reg_write(UC_X86_REG_FPSW, 0)
        self.mu.reg_write(UC_X86_REG_FPCW, 0x37F)
        self.mu.emu_start(va, ret + (6 if ret == self.RET else 0), count=100000)
        return self.mu.reg_read(UC_X86_REG_EAX)

    def raw_para_fisico(self, raw, tamanho=1, sinal=False, coef=(1.0, 0.0, 0.0, 1.0), precisao=0.0, mascara=None,
                        transformacao='ttFormula', vmin=0.0, vmax=0.0):
        mascara = (1 << (8 * tamanho)) - 1 if mascara is None else mascara
        self._objeto(mascara, tamanho, sinal, transformacao, coef, precisao, vmin, vmax)
        self._chamar(VA_RAW_PARA_FIS, struct.pack('<II', self.OBJ, raw & 0xFFFFFFFF), self.RET)
        return struct.unpack('<d', self.mu.mem_read(self.OUT, 8))[0]

    def fisico_para_raw(self, fis, tamanho=1, sinal=False, coef=(1.0, 0.0, 0.0, 1.0), vmin=0.0, vmax=0.0,
                        transformacao='ttFormula', precisao=0.0):
        self._objeto((1 << (8 * tamanho)) - 1, tamanho, sinal, transformacao, coef, precisao, vmin, vmax)
        eax = self._chamar(VA_FIS_PARA_RAW, struct.pack('<IdI', self.OBJ, fis, 0), self.RET_SEM_X87)
        return struct.unpack('<i', struct.pack('<I', eax))[0]


if __name__ == '__main__':
    c = ConversorOriginal(sys.argv[1])
    print(c.raw_para_fisico(0x0600, 2, False, (1, 0, 0, 512.0)), c.fisico_para_raw(3.0, 2, False, (1, 0, 0, 512.0)))
