#!/usr/bin/env python3
"""Extrai todos os formulários embutidos (RT_RCDATA, DFM binário TPF0) do ProgBase.exe em texto.

Formato de texto idêntico ao de tools/dfm.py do repositório do protocolo (object Nome: Classe / prop = valor /
end). Também expõe parse_tpf0(bytes) -> árvore, usada por inventario_telas.py e inventario_i18n.py.
Saída: docs/progbase/inventario/dfm/<RECURSO>.txt + dfm/INDICE.md (recursos que não são DFM ficam só no índice).
Uso: inventario_dfm.py [--exe PATH] [--out DIR]    (PROGBASE_EXE como padrão)
Evidência: OBS-COD (bytes do recurso decodificados sem interpretação)."""
import argparse
import hashlib
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from inventario_comum import Binario, SAIDA, achar_exe, escrever  # noqa: E402


class R:
    def __init__(s, b):
        s.b = b
        s.p = 0

    def u8(s):
        v = s.b[s.p]
        s.p += 1
        return v

    def i(s, fmt, n):
        v = struct.unpack_from(fmt, s.b, s.p)[0]
        s.p += n
        return v

    def sstr(s):
        n = s.u8()
        v = s.b[s.p:s.p + n].decode('latin1')
        s.p += n
        return v


def ext(b):
    m = int.from_bytes(b[:8], 'little')
    e = int.from_bytes(b[8:10], 'little')
    sign = -1 if e & 0x8000 else 1
    e &= 0x7FFF
    if e == 0 and m == 0:
        return 0.0
    return sign * m * 2.0 ** (e - 16383 - 63)


def value(r):
    """Valor de propriedade -> texto (mesmo formato de tools/dfm.py)."""
    t = r.u8()
    if t == 0:
        return 'nil'
    if t == 1:
        out = []
        while r.b[r.p] != 0:
            out.append(value(r))
        r.p += 1
        return '(' + ', '.join(out) + ')'
    if t == 2:
        return str(r.i('<b', 1))
    if t == 3:
        return str(r.i('<h', 2))
    if t == 4:
        return str(r.i('<i', 4))
    if t == 5:
        v = ext(r.b[r.p:r.p + 10])
        r.p += 10
        return repr(round(v, 6))
    if t == 6:
        return repr(r.sstr())
    if t == 7:
        return r.sstr()
    if t == 8:
        return 'False'
    if t == 9:
        return 'True'
    if t == 10:
        n = r.i('<I', 4)
        d = r.b[r.p:r.p + n]
        r.p += n
        return '<binary %dB %s%s>' % (n, d[:24].hex(), '…' if n > 24 else '')
    if t == 11:
        out = []
        while True:
            s = r.sstr()
            if not s:
                break
            out.append(s)
        return '[' + ','.join(out) + ']'
    if t == 12:
        n = r.i('<I', 4)
        v = r.b[r.p:r.p + n].decode('latin1')
        r.p += n
        return repr(v)
    if t == 13:
        return 'nil'
    if t == 14:
        items = []
        while r.b[r.p] != 0:
            if r.b[r.p] in (2, 3, 4):
                value(r)
            assert r.u8() == 1, 'item de coleção sem vaList'
            props = []
            while r.b[r.p] != 0:
                n = r.sstr()
                props.append('%s=%s' % (n, value(r)))
            r.p += 1
            items.append('{' + '; '.join(props) + '}')
        r.p += 1
        return '<' + ' '.join(items) + '>'
    if t == 15:
        return repr(round(r.i('<f', 4), 6))
    if t == 16:
        return repr(r.i('<q', 8) / 10000)
    if t == 17:
        return repr(r.i('<d', 8))
    if t == 18:
        n = r.i('<I', 4)
        v = r.b[r.p:r.p + 2 * n].decode('utf-16le')
        r.p += 2 * n
        return repr(v)
    if t == 19:
        return str(r.i('<q', 8))
    if t == 20:
        n = r.i('<I', 4)
        v = r.b[r.p:r.p + n].decode('utf8', 'replace')
        r.p += n
        return repr(v)
    raise ValueError('tipo %d @ %d' % (t, r.p - 1))


def obj(r):
    c = r.b[r.p]
    herdado = False
    if c & 0xF0 == 0xF0:
        r.p += 1
        herdado = bool(c & 1)
        if c & 2:
            value(r)
    cls = r.sstr()
    name = r.sstr()
    props = []
    while r.b[r.p] != 0:
        pn = r.sstr()
        tipo = r.b[r.p]
        props.append((pn, value(r), tipo))
    r.p += 1
    filhos = []
    while r.b[r.p] != 0:
        filhos.append(obj(r))
    r.p += 1
    return {'nome': name, 'classe': cls, 'herdado': herdado, 'props': props, 'filhos': filhos}


def parse_tpf0(dados):
    """-> (árvore, bytes consumidos). Levanta se não for TPF0."""
    if dados[:4] != b'TPF0':
        raise ValueError('não é TPF0')
    r = R(dados)
    r.p = 4
    return obj(r), r.p


def render(no, ind=0, out=None):
    out = [] if out is None else out
    pad = '  ' * ind
    out.append('%sobject %s: %s' % (pad, no['nome'], no['classe']))
    for pn, v, _ in no['props']:
        out.append('%s  %s = %s' % (pad, pn, v))
    for f in no['filhos']:
        render(f, ind + 1, out)
    out.append(pad + 'end')
    return out


def contar(no):
    return 1 + sum(contar(f) for f in no['filhos'])


def carregar_todos(b):
    """{recurso: árvore} para os RCDATA que são DFM; {recurso: motivo} para os demais."""
    ok, nao = {}, {}
    for nome, dados in b.rcdata().items():
        try:
            arv, usado = parse_tpf0(dados)
            if usado != len(dados):
                raise ValueError('sobraram %d bytes' % (len(dados) - usado))
            ok[nome] = (arv, len(dados))
        except Exception as e:
            nao[nome] = (len(dados), hashlib.sha256(dados).hexdigest(), str(e))
    return ok, nao


def gerar(exe, saida):
    b = Binario(exe)
    ok, nao = carregar_todos(b)
    d = os.path.join(saida, 'dfm')
    L = ['# Recursos RCDATA do ProgBase.exe', '',
         'Gerado por `tools/progbase/inventario_dfm.py` (OBS-COD). SHA-256 do binário: `%s`.' % b.sha256, '',
         '- Recursos RCDATA: **%d**; DFM (TPF0) decodificados por inteiro: **%d**; outros: **%d**.' % (
             len(ok) + len(nao), len(ok), len(nao)), '',
         '## Formulários / módulos (DFM)', '', '| Recurso | Classe raiz | Bytes | Objetos | Arquivo |', '|---|---|---:|---:|---|']
    for nome, (arv, n) in ok.items():
        escrever(os.path.join(d, nome + '.txt'), '\n'.join(render(arv)))
        L.append('| %s | `%s` | %d | %d | `dfm/%s.txt` |' % (nome, arv['classe'], n, contar(arv), nome))
    L += ['', '## Recursos que não são DFM', '', '| Recurso | Bytes | SHA-256 | O que é |', '|---|---:|---|---|']
    quem = {'LANG_MAIN': 'JSON de textos (UTF-8) em 26 idiomas — extraído por `inventario_i18n.py` (i18n.json/i18n.md). OBS-COD.',
            'DVCLAL': 'Marcador de licença da VCL do Delphi (16 bytes opacos, presente em todo executável Delphi). INFERIDO pelo nome padrão; conteúdo DESCONHECIDO e irrelevante.',
            'TEELCDFONT': 'Fonte TrueType embutida (cabeçalho sfnt 00 01 00 00; tabelas GPOS/LTSH/OS/2): fonte de display LCD dos instrumentos. OBS-COD o formato; uso INFERIDO pelo nome.',
            'TEELEDFONT': 'Fonte TrueType embutida (sfnt; tabelas OS/2/cmap): fonte de display LED dos instrumentos. OBS-COD o formato; uso INFERIDO pelo nome.',
            'PLATFORMTARGETS': 'Marcador do Delphi (2 bytes, plataformas-alvo). INFERIDO.'}
    for nome, (n, sha, err) in nao.items():
        L.append('| %s | %d | `%s` | %s |' % (nome, n, sha[:16] + '…', quem.get(nome, 'sem TPF0 (%s). DESCONHECIDO.' % err)))
    escrever(os.path.join(d, 'INDICE.md'), '\n'.join(L))
    return ok, nao


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--exe')
    ap.add_argument('--out', default=SAIDA)
    a = ap.parse_args()
    exe = achar_exe(a.exe)
    if not exe:
        sys.exit('ProgBase.exe não encontrado: defina PROGBASE_EXE')
    ok, nao = gerar(exe, a.out)
    print('DFM:', len(ok), 'outros:', len(nao))


if __name__ == '__main__':
    main()
