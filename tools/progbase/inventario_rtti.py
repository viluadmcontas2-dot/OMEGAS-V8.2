#!/usr/bin/env python3
"""Inventário de classes com RTTI do ProgBase.exe (Delphi/VCL, layout de VMT 2009+).

Para cada classe (VMT achada por varredura do auto-ponteiro em vmt-88): nome, unidade, ancestral,
tamanho de instância, propriedades publicadas (TypeInfo), campos publicados (offset/tipo/nome),
tabela de métodos publicados (endereço/nome), tabela dinâmica (mensagens), slots de vtable.
Saída: docs/progbase/inventario/classes.json e classes.md.
Uso: inventario_rtti.py [--exe PATH] [--out DIR]    (PROGBASE_EXE como padrão para o .exe)
Evidência: OBS-COD = lido do binário; DERIVADO = cálculo sobre OBS-COD (tipo de classe pela cadeia de ancestrais)."""
import argparse
import os
import re
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from inventario_comum import (Binario, SAIDA, SHA_ESPERADO, achar_exe, escrever, escrever_json, h)  # noqa: E402

NOME_OK = re.compile(r'[A-Za-z_][\w.<>,$ &@:\[\]]*$')
KIND_CLASS = 7
BASES_TIPO = {'TCustomForm': 'form', 'TDataModule': 'datamodule', 'TCustomFrame': 'frame',
              'TCustomAction': 'action', 'TComponent': 'componente', 'TThread': 'thread',
              'TCustomApplication': 'aplicacao', 'Exception': 'excecao'}


def achar_vmts(b):
    """{vmt: nome} de todas as classes com auto-ponteiro em vmt-88 e TypeInfo coerente."""
    out = {}
    for sec in b.pe.sections:
        d = sec.get_data()
        va0 = b.base + sec.VirtualAddress
        for i in range(0, len(d) - 4, 4):
            if struct.unpack_from('<I', d, i)[0] != va0 + i + 88:
                continue
            vmt = va0 + i + 88
            try:
                nome = b.sstr(b.u32(vmt - 56))
                ti = b.u32(vmt - 72)
                if b.u8(ti) != KIND_CLASS or b.sstr(ti + 1) != nome or not NOME_OK.match(nome):
                    continue
                if b.u32(vmt - 52) > 0x100000:
                    continue
            except Exception:
                continue
            out[vmt] = nome
    return dict(sorted(out.items()))


def tipo_nome(b, tp):
    """Nome do tipo apontado por PropType (ponteiro duplo) ou None."""
    try:
        for cand in (b.u32(tp), tp):
            if b.mapeado(cand) and b.u8(cand) < 30:
                n = b.sstr(cand + 1)
                if n and NOME_OK.match(n):
                    return n
    except Exception:
        pass
    return None


def acessor(b, v):
    if v == 0:
        return None
    if v >> 24 == 0xFF:
        return 'campo+0x%X' % (v & 0xFFFF)
    if v >> 24 == 0xFE:
        return 'virtual[0x%X]' % (v & 0xFFFF)
    return h(v)


def propriedades(b, ti):
    """Propriedades publicadas da própria classe (TypeInfo tkClass)."""
    nm = b.sstr(ti + 1)
    p = ti + 2 + len(nm)
    unit = b.sstr(p + 10)
    q = p + 10 + 1 + len(unit)
    n = b.u16(q)
    q += 2
    out = []
    for _ in range(n):
        tp, g, s, st, idx, dfl = struct.unpack_from('<IIIIiI', b.raw, b.va2off(q))
        ni = b.u16(q + 24)
        pn = b.sstr(q + 26)
        out.append({'nome': pn, 'tipo': tipo_nome(b, tp), 'leitura': acessor(b, g), 'escrita': acessor(b, s),
                    'indice': idx if idx != -0x80000000 else None})
        q += 26 + 1 + len(pn)
    return out


def campos(b, vmt, vmts):
    ft = b.u32(vmt - 68)
    if not ft:
        return []
    n = b.u16(ft)
    ct = b.u32(ft + 2)
    ctab = []
    if ct:
        cn = b.u16(ct)
        for i in range(cn):
            r = b.u32(ct + 2 + 4 * i)
            try:
                c = b.u32(r) if r not in vmts else r
            except Exception:
                c = r
            ctab.append(vmts.get(c) or vmts.get(r))
    p = ft + 6
    out = []
    for _ in range(n):
        off = b.u32(p)
        ti = b.u16(p + 4)
        nm = b.sstr(p + 6)
        out.append({'offset': off, 'tipo': ctab[ti] if ti < len(ctab) else None, 'nome': nm})
        p += 7 + len(nm)
    return out


def metodos(b, vmt):
    mt = b.u32(vmt - 64)
    if not mt:
        return []
    n = b.u16(mt)
    p = mt + 2
    out = []
    for _ in range(n):
        sz = b.u16(p)
        out.append({'endereco': h(b.u32(p + 2)), 'nome': b.sstr(p + 6)})
        p += sz
    return out


def dinamicos(b, vmt):
    dt = b.u32(vmt - 60)
    if not dt:
        return []
    n = b.u16(dt)
    ids = [b.u16(dt + 2 + 2 * i) for i in range(n)]
    base = dt + 2 + 2 * n
    return [{'mensagem': '0x%04X' % i, 'endereco': h(b.u32(base + 4 * k))} for k, i in enumerate(ids)]


def vtable(b, vmt):
    """Slots consecutivos de código a partir de vmt (até o primeiro não-código)."""
    out = []
    a = vmt
    while len(out) < 512:
        try:
            v = b.u32(a)
        except Exception:
            break
        if not (b.cb <= v < b.ce):
            break
        out.append(h(v))
        a += 4
    return out


def construir(b):
    vmts = achar_vmts(b)
    por_nome = {}
    classes = {}
    for vmt, nome in vmts.items():
        par = b.u32(vmt - 48)
        pv = None
        if par:
            try:
                pv = b.u32(par)
            except Exception:
                pv = par
        ti = b.u32(vmt - 72)
        nm = b.sstr(ti + 1)
        unit = b.sstr(ti + 2 + len(nm) + 10)
        c = {'nome': nome, 'vmt': h(vmt), 'typeinfo': h(ti), 'unidade': unit,
             'app': unit.startswith('@'), 'tamanho_instancia': b.u32(vmt - 52),
             'pai': vmts.get(pv), 'pai_vmt': h(pv) if pv else None,
             'n_props_total': b.u16(ti + 2 + len(nm) + 8)}
        try:
            c['propriedades'] = propriedades(b, ti)
        except Exception:
            c['propriedades'] = []
        c['campos'] = campos(b, vmt, vmts)
        c['metodos'] = metodos(b, vmt)
        c['dinamicos'] = dinamicos(b, vmt)
        c['vtable'] = vtable(b, vmt) if c['app'] else []
        c['n_vtable'] = len(c['vtable']) if c['app'] else None
        key = nome if nome not in classes else '%s#%s' % (nome, h(vmt))
        classes[key] = c
        por_nome.setdefault(nome, []).append(key)
    # tipo de classe pela cadeia
    by_vmt = {c['vmt']: c for c in classes.values()}
    for c in classes.values():
        cadeia, x, vistos = [], c, set()
        while x and x['vmt'] not in vistos:
            vistos.add(x['vmt'])
            cadeia.append(x['nome'])
            x = by_vmt.get(x['pai_vmt'])
        c['cadeia'] = cadeia[1:]
        c['tipo'] = next((BASES_TIPO[n] for n in cadeia if n in BASES_TIPO), 'outro')
    return dict(sorted(classes.items()))


def gerar(exe, saida):
    b = Binario(exe)
    cl = construir(b)
    app = [c for c in cl.values() if c['app']]
    meta = {'exe_sha256': b.sha256, 'sha256_esperado_ok': b.sha256 == SHA_ESPERADO,
            'n_classes': len(cl), 'n_classes_app': len(app),
            'legenda': 'OBS-COD: todos os campos vêm do RTTI/VMT do binário; tipo/cadeia = DERIVADO.'}
    escrever_json(os.path.join(saida, 'classes.json'), {'meta': meta, 'classes': cl})
    L = ['# Classes com RTTI do ProgBase.exe', '',
         'Gerado por `tools/progbase/inventario_rtti.py` (OBS-COD: RTTI/VMT; tipo e cadeia: DERIVADO).',
         'SHA-256 do binário: `%s`.' % b.sha256, '',
         '- Classes com RTTI: **%d** (das quais **%d** nas unidades do próprio programa, prefixo `@:`).' % (len(cl), len(app)),
         '- Detalhe completo (campos, métodos, propriedades, vtable): `classes.json`.', '']
    for tipo in ('form', 'datamodule', 'frame', 'thread', 'action', 'componente', 'excecao', 'aplicacao', 'outro'):
        sel = [c for c in app if c['tipo'] == tipo]
        if not sel:
            continue
        L += ['## Unidades do programa — tipo `%s` (%d)' % (tipo, len(sel)), '',
              '| Classe | Unidade | Ancestral | Instância | VMT | Campos | Métodos publ. | Props | Vtable |',
              '|---|---|---|---:|---|---:|---:|---:|---:|']
        for c in sorted(sel, key=lambda c: c['nome']):
            L.append('| `%s` | %s | `%s` | 0x%X | %s | %d | %d | %d | %d |' % (
                c['nome'], c['unidade'][2:] if c['unidade'].startswith('@:') else c['unidade'], c['pai'],
                c['tamanho_instancia'], c['vmt'], len(c['campos']), len(c['metodos']),
                len(c['propriedades']), c['n_vtable']))
        L.append('')
    L += ['## Bibliotecas por unidade (contagem de classes)', '', '| Unidade | Classes |', '|---|---:|']
    cont = {}
    for c in cl.values():
        if not c['app']:
            cont[c['unidade']] = cont.get(c['unidade'], 0) + 1
    for u in sorted(cont):
        L.append('| %s | %d |' % (u, cont[u]))
    escrever(os.path.join(saida, 'classes.md'), '\n'.join(L))
    return cl


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--exe')
    ap.add_argument('--out', default=SAIDA)
    a = ap.parse_args()
    exe = achar_exe(a.exe)
    if not exe:
        sys.exit('ProgBase.exe não encontrado: defina PROGBASE_EXE')
    cl = gerar(exe, a.out)
    print('classes:', len(cl), 'app:', sum(c['app'] for c in cl.values()))


if __name__ == '__main__':
    main()
