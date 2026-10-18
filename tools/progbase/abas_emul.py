#!/usr/bin/env python3
"""Executa NA CPU EMULADA (Unicorn x86-32) o codigo ORIGINAL dos setters/getters do TStreamDati do ProgBase.exe.

Reaproveita a maquina de tools/emulacao/cpu_setter_original.py (imagem 1:1 do .exe, stubs de KERNEL32, ECU de eco).
Aqui o ECU tambem responde ACK de SetNumber (`53 00 53`) alem do de SetVector (`53 00 53`).
Os objetos TAebNumber/TAebVector sao montados com os campos lidos do DFM do TStreamDati (DataMask/DataLength/
ArrayDimension/SerialCode); o cache do TStreamDati comeca zerado, salvo o pedido em contrario.
Saida: quadros enviados a serial (bytes), cache apos a chamada, retorno do getter (st0). Deterministico.
Marcas: OBS-COD (executado do .exe) - a ECU e STUB (INFERIDO do log: eco + ACK)."""
import os
import re
import struct
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(AQUI, '..', 'emulacao'))
import cpu_setter_original as C  # noqa: E402

VMT_NUMBER = 0xAC9CAC
VMT_VECTOR = 0xAC9BA8


class _EcuAck(C._Ecu):
    """Eco + ACK. SetNumber 0x1n: `53 00 53`; SetVector 0x3n: `53 00 53`."""

    @staticmethod
    def _tam(acc):
        op = acc[0]
        if op & 0xF8 in (0x10, 0x30) and (op & 7) < 7:
            return (op & 7) + 3
        return None

    def escreve(self, dados):
        for b in dados:
            self.acc.append(b)
            if self.responde:
                self.rx.append(b)
            n = self._tam(self.acc)
            if n is not None and len(self.acc) >= n:
                q = bytes(self.acc[:n])
                del self.acc[:n]
                self.quadros.append(q)
                if self.responde and (sum(q[:-1]) & 0xFF) == q[-1]:
                    self.rx += bytes([0x53, 0x00, 0x53])


def objetos_sd(dfm_sd):
    """{nome: dict(classe, props)} do TSTREAMDATI.txt (so TAebNumber/TAebVector)."""
    out, cur = {}, None
    for l in dfm_sd.split('\n'):
        m = re.match(r'  object (\w+): (\w+)', l)
        if m:
            cur = dict(classe=m.group(2), props={})
            out[m.group(1)] = cur
            continue
        m = re.match(r'    (\w+) = (-?\d+)$', l)
        if m and cur is not None:
            cur['props'][m.group(1)] = int(m.group(2))
    return out


class Emul:
    def __init__(self, exe, rtti_campos, objs, lr=0, proto=2, buffered=1):
        self.m = C._Maquina(exe)
        self.m.ecu = _EcuAck(True)
        self.m.montar((0, 0), proto, buffered, lr, 1)
        self.campos, self.objs = rtti_campos, objs
        self.nome_off = {v: k for k, v in rtti_campos.items()}
        self.criados = {}
        self.m.w32(0xAECFB4, self.m.sd)      # global 'TStreamDati*' lido por 0x433FFC (OBS-COD)
        self.sha256 = self.m.sha256

    def vetor(self, nome):
        if nome in self.criados:
            return self.criados[nome]
        o = self.objs[nome]
        p = o['props']
        dim = p.get('ArrayDimension', 1)
        comp = p.get('DataLength', 1)
        v = self.m.criar_vetor(p['SerialCode'], dim=dim, comp=comp)
        self.m.w32(v, VMT_NUMBER if o['classe'] == 'TAebNumber' else VMT_VECTOR)
        self.m.w32(self.m.sd + self.nome_off[nome], v)
        self.criados[nome] = v
        return v

    def preparar(self, nomes, cache=None):
        for n in nomes:
            self.vetor(n)
        for off, val in (cache or {}).items():
            self.m.w32(self.m.sd + off, val)

    def _double(self, x):
        return struct.unpack('<II', struct.pack('<d', float(x)))

    def set_esc(self, codigo, valor):
        m = self.m
        m.ecu.quadros.clear()
        lo, hi = self._double(valor)
        m.erro = None
        ret = m.chamar(0x436444, pilha=(m.sd, codigo, lo, hi))
        return dict(quadros=[bytes(q) for q in m.ecu.quadros], erro=m.erro, retorno_al=ret)

    def set_idx(self, codigo, indice, valor):
        m = self.m
        m.ecu.quadros.clear()
        lo, hi = self._double(valor)
        m.erro = None
        ret = m.chamar(0x43734C, pilha=(m.sd, codigo, indice, lo, hi))
        return dict(quadros=[bytes(q) for q in m.ecu.quadros], erro=m.erro, retorno_al=ret)

    def _chamar_st0(self, addr, pilha):
        """Como _Maquina.chamar, mas o retorno vai a um trampolim `fstp qword [SAIDA]; jmp STOP` (le st0 como double)."""
        from unicorn import x86_const as X
        m = self.m
        saida = C.HEAP + C.HEAP_TAM - 0x40
        tramp = 0x04100000
        if not getattr(self, '_tramp', False):
            m.uc.mem_map(tramp, 0x1000)
            self._tramp = True
        rel = (C.STOP - (tramp + 6 + 5)) & 0xFFFFFFFF
        m.uc.mem_write(tramp, b'\xdd\x1d' + struct.pack('<I', saida) + b'\xe9' + struct.pack('<I', rel))
        esp = C.PILHA + C.PILHA_TAM - 0x100
        for v in reversed(list(pilha)):
            esp -= 4
            m.w32(esp, v)
        esp -= 4
        m.w32(esp, tramp)
        m.uc.reg_write(X.UC_X86_REG_ESP, esp)
        m.entradas.add(addr)
        m.erro = None
        try:
            m.uc.emu_start(addr, C.STOP)
        except m.uc_mod.UcError as e:
            m.erro = 'UcError: %s' % e
        return struct.unpack('<d', bytes(m.uc.mem_read(saida, 8)))[0]

    def get(self, codigo, indice=None):
        """Getter por codigo (valor da tela, double lido de st0)."""
        m = self.m
        if indice is None:
            return self._chamar_st0(0x434CC0, (m.sd, codigo))
        return self._chamar_st0(0x435C6C, (m.sd, codigo, indice))

    def cache(self, off):
        return self.m.r32(self.m.sd + off)
