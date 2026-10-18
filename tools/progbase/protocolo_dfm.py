#!/usr/bin/env python3
"""Leitor estruturado de formulários Delphi (recurso RCDATA `TPF0`) do ProgBase.exe (D2 PROTOCOLO).

Diferença para tools/emulacao/dfm_renderer.py e tools/progbase/inventario_dfm.py: aqui o valor de cada
propriedade volta como objeto Python (int, float, str, {'id': ...}, lista, bytes), o que permite ler
`SerialCode`, `Coeffs`, `DefaultValue` etc. sem reparsear texto. Só stdlib + pefile; nunca executa o .exe.

OBS-COD: formato = TValueType do Delphi (System.Classes). Floats `vaExtended` são 80 bits x87.
"""
import struct
import sys


class _R:
    def __init__(self, b, p=0):
        self.b, self.p = b, p

    def u8(self):
        v = self.b[self.p]
        self.p += 1
        return v

    def unpack(self, fmt, n):
        v = struct.unpack_from(fmt, self.b, self.p)[0]
        self.p += n
        return v

    def sstr(self):
        n = self.u8()
        v = self.b[self.p:self.p + n].decode('latin1')
        self.p += n
        return v


def extended80(b):
    """x87 80 bits (mantissa explícita de 64 bits) -> float de Python."""
    m = int.from_bytes(b[:8], 'little')
    e = int.from_bytes(b[8:10], 'little')
    sign = -1.0 if e & 0x8000 else 1.0
    e &= 0x7FFF
    if e == 0 and m == 0:
        return 0.0
    return sign * m * 2.0 ** (e - 16383 - 63)


def _valor(r):
    t = r.u8()
    if t in (0, 13):
        return None
    if t == 1:
        out = []
        while r.b[r.p] != 0:
            out.append(_valor(r))
        r.p += 1
        return out
    if t == 2:
        return r.unpack('<b', 1)
    if t == 3:
        return r.unpack('<h', 2)
    if t == 4:
        return r.unpack('<i', 4)
    if t == 5:
        v = extended80(r.b[r.p:r.p + 10])
        r.p += 10
        return v
    if t == 6:
        return r.sstr()
    if t == 7:
        return {'id': r.sstr()}
    if t == 8:
        return False
    if t == 9:
        return True
    if t == 10:
        n = r.unpack('<I', 4)
        d = r.b[r.p:r.p + n]
        r.p += n
        return bytes(d)
    if t == 11:
        out = []
        while True:
            s = r.sstr()
            if not s:
                break
            out.append(s)
        return {'set': out}
    if t == 12:
        n = r.unpack('<I', 4)
        v = r.b[r.p:r.p + n].decode('latin1')
        r.p += n
        return v
    if t == 14:
        itens = []
        while r.b[r.p] != 0:
            if r.b[r.p] in (2, 3, 4):
                _valor(r)
            assert r.u8() == 1, 'item de coleção sem vaList'
            props = {}
            while r.b[r.p] != 0:
                n = r.sstr()
                props[n] = _valor(r)
            r.p += 1
            itens.append(props)
        r.p += 1
        return {'colecao': itens}
    if t == 15:
        return r.unpack('<f', 4)
    if t == 16:
        return r.unpack('<q', 8) / 10000
    if t == 17:
        return r.unpack('<d', 8)
    if t == 18:
        n = r.unpack('<I', 4)
        v = r.b[r.p:r.p + 2 * n].decode('utf-16le')
        r.p += 2 * n
        return v
    if t == 19:
        return r.unpack('<q', 8)
    if t == 20:
        n = r.unpack('<I', 4)
        v = r.b[r.p:r.p + n].decode('utf8', 'replace')
        r.p += n
        return v
    raise ValueError('TValueType %d em %d' % (t, r.p - 1))


def _objeto(r, caminho, saida):
    c = r.b[r.p]
    if c & 0xF0 == 0xF0:
        r.p += 1
        if c & 2:
            _valor(r)
    cls = r.sstr()
    nome = r.sstr()
    props = {}
    while r.b[r.p] != 0:
        n = r.sstr()
        props[n] = _valor(r)
    r.p += 1
    cam = caminho + [nome]
    saida.append({'classe': cls, 'nome': nome, 'caminho': '/'.join(cam), 'props': props})
    while r.b[r.p] != 0:
        _objeto(r, cam, saida)
    r.p += 1


def parse_tpf0(blob):
    """Lista plana de objetos (pré-ordem) do formulário `blob`. Cada item: classe, nome, caminho, props."""
    assert blob[:4] == b'TPF0', 'não é DFM binário'
    r = _R(blob, 4)
    saida = []
    _objeto(r, [], saida)
    return saida, r.p


def recursos_dfm(exe):
    """{nome do recurso RCDATA: bytes} só dos que começam com TPF0."""
    import pefile
    pe = pefile.PE(exe, fast_load=True)
    pe.parse_data_directories(pefile.DIRECTORY_ENTRY['IMAGE_DIRECTORY_ENTRY_RESOURCE'])
    out = {}
    for t in pe.DIRECTORY_ENTRY_RESOURCE.entries:
        if t.id != 10:
            continue
        for e in t.directory.entries:
            nome = (e.name or b'')
            nome = nome.decode() if isinstance(nome, bytes) else str(nome)
            d = e.directory.entries[0].data.struct
            blob = pe.get_data(d.OffsetToData, d.Size)
            if blob[:4] == b'TPF0':
                out[nome] = bytes(blob)
    return out


if __name__ == '__main__':
    rs = recursos_dfm(sys.argv[1])
    print(len(rs), 'formulários;', sum(len(parse_tpf0(b)[0]) for b in rs.values()), 'objetos')
