#!/usr/bin/env python3
"""Utilitários comuns do inventário do ProgBase.exe (estático; nada é executado).

O .exe NÃO é versionado. Caminho: variável de ambiente PROGBASE_EXE; se ausente, tenta
$OMEGAS_CACHE/ProgBase.exe (padrão ~/.cache/omegas/ProgBase.exe, o mesmo de tools/obter_progbase.py).
SHA-256 esperado: 8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4.
Precisa de pefile (e capstone para a análise de código)."""
import hashlib
import json
import os
import re
import struct

SHA_ESPERADO = '8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4'
AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.abspath(os.path.join(AQUI, '..', '..'))
SAIDA = os.path.join(RAIZ, 'docs', 'progbase', 'inventario')


def achar_exe(pedido=None):
    cache = os.environ.get('OMEGAS_CACHE', os.path.join(os.path.expanduser('~'), '.cache', 'omegas'))
    for c in (pedido, os.environ.get('PROGBASE_EXE'), os.path.join(cache, 'ProgBase.exe')):
        if c and os.path.isfile(c):
            return c
    return None


def escrever(caminho, texto):
    """Grava texto UTF-8 com \\n final, só se mudou (idempotente, determinístico)."""
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    if not texto.endswith('\n'):
        texto += '\n'
    dados = texto.encode('utf-8')
    try:
        with open(caminho, 'rb') as fh:
            if fh.read() == dados:
                return False
    except OSError:
        pass
    with open(caminho, 'wb') as fh:
        fh.write(dados)
    return True


def escrever_json(caminho, obj):
    return escrever(caminho, json.dumps(obj, indent=1, sort_keys=True, ensure_ascii=False))


class Binario:
    def __init__(self, caminho):
        import pefile
        self.pe = pefile.PE(caminho, fast_load=True)
        self.pe.parse_data_directories(pefile.DIRECTORY_ENTRY['IMAGE_DIRECTORY_ENTRY_RESOURCE'])
        with open(caminho, 'rb') as fh:
            self.raw = fh.read()
        self.sha256 = hashlib.sha256(self.raw).hexdigest()
        self.base = self.pe.OPTIONAL_HEADER.ImageBase
        t = self.pe.sections[0]
        self.cb = self.base + t.VirtualAddress
        self.code = self.raw[t.PointerToRawData:t.PointerToRawData + t.SizeOfRawData]
        self.ce = self.cb + len(self.code)
        self._md = None
        self._dis = {}

    # ---- memória
    def va2off(self, v):
        return self.pe.get_offset_from_rva(v - self.base)

    def u8(self, v):
        return self.raw[self.va2off(v)]

    def u16(self, v):
        o = self.va2off(v)
        return struct.unpack_from('<H', self.raw, o)[0]

    def u32(self, v):
        o = self.va2off(v)
        return struct.unpack_from('<I', self.raw, o)[0]

    def sstr(self, v):
        o = self.va2off(v)
        return self.raw[o + 1:o + 1 + self.raw[o]].decode('latin-1')

    def mapeado(self, v):
        try:
            self.va2off(v)
            return v >= self.base
        except Exception:
            return False

    # ---- recursos
    def rcdata(self):
        """{nome: bytes} de RT_RCDATA (10), ordenado por nome."""
        out = {}
        for t in self.pe.DIRECTORY_ENTRY_RESOURCE.entries:
            if t.id != 10:
                continue
            for e in t.directory.entries:
                d = e.directory.entries[0].data.struct
                out[(e.name or b'').decode()] = self.pe.get_data(d.OffsetToData, d.Size)
        return dict(sorted(out.items()))

    def string_table(self):
        """RT_STRING (6): {id: texto} (cada bloco tem 16 strings; id = (bloco-1)*16 + i)."""
        out = {}
        for t in self.pe.DIRECTORY_ENTRY_RESOURCE.entries:
            if t.id != 6:
                continue
            for blk in t.directory.entries:
                for lang in blk.directory.entries:
                    d = lang.data.struct
                    data = self.pe.get_data(d.OffsetToData, d.Size)
                    p = 0
                    for i in range(16):
                        if p + 2 > len(data):
                            break
                        n = struct.unpack_from('<H', data, p)[0]
                        p += 2
                        if n:
                            out[(blk.id - 1) * 16 + i] = data[p:p + 2 * n].decode('utf-16le', 'replace')
                        p += 2 * n
        return dict(sorted(out.items()))

    # ---- disassembly
    def md(self):
        if self._md is None:
            import capstone
            self._md = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)
        return self._md

    def ins(self, a):
        if a is None or not (self.cb <= a < self.ce - 16):
            return None
        if a not in self._dis:
            o = a - self.cb
            self._dis[a] = next(self.md().disasm(self.code[o:o + 16], a), None)
        return self._dis[a]

    def linear(self, a, n):
        out = []
        while len(out) < n:
            i = self.ins(a)
            if i is None:
                break
            out.append(i)
            a += i.size
        return out


def h(x, w=8):
    return '0x%0*X' % (w, x)
