#!/usr/bin/env python3
"""Descoberta de funções do ProgBase.exe (descida recursiva), nomes, unidades e cobertura.

Fontes de nome (todas OBS-COD): métodos publicados (RTTI), handlers de mensagem (tabela dinâmica),
exportações do PE (nomes C++Builder mangled: componentes TAeb*, @@Unidade@Initialize/Finalize),
slots de vtable das classes do programa, thunks de import (jmp [IAT] -> DLL!função).
Unidade de cada função: pelos marcadores @@Unidade@Initialize/Finalize (fim de cada unidade) — DERIVADO.
Saída: docs/progbase/inventario/funcoes.json (só código das unidades do programa) e funcoes.md.
Uso: inventario_codigo.py [--exe PATH] [--out DIR] [--atualizar-cobertura FONTE...]
Determinístico: mesma entrada -> mesmos bytes."""
import argparse
import bisect
import json
import os
import re
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from inventario_comum import Binario, RAIZ, SAIDA, achar_exe, escrever, escrever_json, h  # noqa: E402
import inventario_rtti as rtti  # noqa: E402

COBERTURA = os.path.join(RAIZ, 'tools', 'progbase', 'dados', 'cobertura_externa.json')
HEX = re.compile(r'^0x[0-9a-f]+$')
TERMINA = {'ret', 'retn', 'jmp', 'int3', 'ud2', 'hlt'}
PROLOGOS = (b'\x55\x8b\xec', b'\x53\x56', b'\x56\x57', b'\x53\x8b', b'\x56\x8b', b'\x57\x8b', b'\x83\xc4', b'\x33\xc0', b'\x8b\x44\x24',
            b'\x8b\xc0', b'\x53\x83', b'\x56\x83', b'\x55\x89', b'\x55\x8b', b'\xff\x25', b'\x50\x51', b'\x53\x51', b'\x55\x56')


def demangle(n):
    """Desmangling leve de nomes C++Builder: só classe::método (assinatura omitida). Não é um demangler completo."""
    if n.startswith('@$xp$'):
        return 'typeinfo ' + re.sub(r'^\d+', '', n[5:])
    m = re.match(r'^@@(\w+)@(Initialize|Finalize)$', n)
    if m:
        return 'unit %s::%s' % (m.group(1), m.group(2))
    base = n.split('$q')[0].split('$b')[0] if '$' in n else n
    suf = ''
    if '$bctr' in n:
        suf = ' [ctor]'
    elif '$bdtr' in n:
        suf = ' [dtor]'
    return base.lstrip('@').replace('@', '::') + suf


class Codigo:
    def __init__(self, b):
        self.b = b
        self.t0 = b.cb
        self.vis = bytearray(len(b.code))      # 1 = início de instrução visitada
        self.entradas = {}                      # endereço -> origem
        self.iat = {}
        self.nomes = {}                         # endereço -> (nome, fonte)
        self.thunk = {}
        self.fim_visto = {}
        self._importacoes()

    def _importacoes(self):
        for ent in self.b.pe.DIRECTORY_ENTRY_IMPORT:
            dll = ent.dll.decode()
            for imp in ent.imports:
                nm = imp.name.decode() if imp.name else 'ord%d' % imp.ordinal
                self.iat[imp.address] = '%s!%s' % (dll, nm)

    def no_codigo(self, a):
        return self.t0 <= a < self.b.ce

    def parece_inicio(self, a):
        if not self.no_codigo(a):
            return False
        o = a - self.t0
        if o and self.b.code[o - 1] not in (0xC3, 0xCC, 0x90, 0x00, 0x8B, 0xC0) and self.b.code[o - 3] != 0xC2:
            return False
        c = self.b.code[o:o + 3]
        return any(c.startswith(p) for p in PROLOGOS)

    def descer(self, raizes):
        """Descida recursiva. raizes: iterável de (endereço, origem)."""
        pend = [(a, o) for a, o in raizes]
        b = self.b
        code, t0 = b.code, self.t0
        md = b.md()
        while pend:
            a, orig = pend.pop()
            if not self.no_codigo(a):
                continue
            if orig is not None and a not in self.entradas:
                self.entradas[a] = orig
            while self.no_codigo(a) and not self.vis[a - t0]:
                o = a - t0
                termina = False
                got = False
                recente = []   # últimas instruções (para jump table)
                for (ad, sz, mn, op) in md.disasm_lite(code[o:o + 4096], a):
                    got = True
                    if self.vis[ad - t0]:
                        termina = True
                        break
                    self.vis[ad - t0] = 1
                    recente.append((mn, op))
                    if len(recente) > 8:
                        recente.pop(0)
                    nxt = ad + sz
                    if mn == 'call':
                        if HEX.match(op):
                            tgt = int(op, 16)
                            if self.no_codigo(tgt):
                                pend.append((tgt, 'call'))
                        elif op.startswith('dword ptr [0x'):
                            v = int(op[11:-1], 16)
                            nm = self.iat.get(v)
                            if nm:
                                self.thunk.setdefault(('api', ad), nm)
                    elif mn == 'jmp':
                        if HEX.match(op):
                            tgt = int(op, 16)
                            if self.no_codigo(tgt):
                                pend.append((tgt, None))
                        else:
                            m = re.match(r'dword ptr \[(\w+)\*4 \+ (0x[0-9a-f]+)\]', op)
                            if m:
                                n = 128
                                for (m2, o2) in reversed(recente[:-1]):
                                    if m2 == 'cmp' and o2.startswith(m.group(1) + ', '):
                                        try:
                                            n = int(o2.split(', ')[1], 0) + 1
                                        except ValueError:
                                            pass
                                        break
                                tab = int(m.group(2), 16)
                                for k in range(min(n, 256)):
                                    try:
                                        tgt = b.u32(tab + 4 * k)
                                    except Exception:
                                        break
                                    if not self.no_codigo(tgt):
                                        break
                                    pend.append((tgt, None))
                            elif op.startswith('dword ptr [0x'):
                                v = int(op[11:-1], 16)
                                nm = self.iat.get(v)
                                if nm:
                                    self.thunk[ad] = nm
                    elif mn.startswith('j') and HEX.match(op):
                        tgt = int(op, 16)
                        if self.no_codigo(tgt):
                            pend.append((tgt, None))
                    elif mn in ('push', 'mov') and ', 0x' in op or (mn == 'push' and op.startswith('0x')):
                        m = re.search(r'(0x[0-9a-f]+)$', op)
                        if m:
                            v = int(m.group(1), 16)
                            if self.t0 <= v < self.b.ce and v not in self.entradas and self.parece_inicio(v) \
                                    and not self.vis[v - t0]:
                                pend.append((v, 'ref'))
                    if mn in TERMINA:
                        termina = True
                        a = nxt
                        break
                    a = nxt
                if not got or termina:
                    break
        return self

    def varrer_lacunas(self, ini, fim):
        """Entradas órfãs: lacunas não visitadas em [ini,fim) que começam como função."""
        a = ini
        novas = 0
        while a < fim:
            if not self.vis[a - self.t0] and self.parece_inicio(a) and a not in self.entradas:
                antes = sum(self.vis)
                self.descer([(a, 'varredura')])
                novas += 1
            a += 1
        return novas


def unidades(b):
    """[(nome, fim_inclusivo)] ordenado: marcadores @@X@Initialize/Finalize do PE (DERIVADO: fim de cada unidade)."""
    ini, fin = {}, {}
    for s in b.pe.DIRECTORY_ENTRY_EXPORT.symbols:
        n = s.name.decode()
        m = re.match(r'^@@(\w+)@(Initialize|Finalize)$', n)
        if m:
            (ini if m.group(2) == 'Initialize' else fin)[m.group(1)] = b.base + s.address
    out = sorted((fin.get(k, v) + 0x20, k) for k, v in ini.items())
    return out


def fonte_nomes(b, cl):
    """{endereço: (nome, fonte)} por prioridade: rtti > dinamico > exportacao > vtable."""
    nomes = {}
    def poe(a, n, f):
        if a not in nomes:
            nomes[a] = (n, f)
    for c in cl.values():
        for m in c['metodos']:
            poe(int(m['endereco'], 16), '%s.%s' % (c['nome'], m['nome']), 'rtti-metodo')
    for c in cl.values():
        for m in c['dinamicos']:
            poe(int(m['endereco'], 16), '%s.msg_%s' % (c['nome'], m['mensagem']), 'rtti-dinamico')
    for s in b.pe.DIRECTORY_ENTRY_EXPORT.symbols:
        a = b.base + s.address
        if b.cb <= a < b.ce:
            poe(a, demangle(s.name.decode()), 'exportacao')
    by_vmt = {c['vmt']: c for c in cl.values()}
    for c in sorted(cl.values(), key=lambda c: (len(c['cadeia']), c['nome'])):
        if not c['app']:
            continue
        pai = by_vmt.get(c['pai_vmt'])
        pv = pai['vtable'] if pai else []
        for i, a in enumerate(c['vtable']):
            if i < len(pv) and pv[i] == a:
                continue
            poe(int(a, 16), '%s.vmt[0x%X]' % (c['nome'], 4 * i), 'vtable')
    return nomes


def montar(b, cl):
    cod = Codigo(b)
    nomes = fonte_nomes(b, cl)
    raizes = [(b.pe.OPTIONAL_HEADER.AddressOfEntryPoint + b.base, 'entrada')]
    raizes += [(a, 'nome') for a in sorted(nomes) if b.cb <= a < b.ce]
    cod.descer(raizes)
    un = unidades(b)
    fins = [u[0] for u in un]
    lo = b.base + 0x1000
    # lacunas só dentro das unidades do programa (evita ruído de dados da RTL)
    grupos = grupos_app(un)
    for (g0, g1) in grupos:
        cod.varrer_lacunas(g0, g1)
    return cod, nomes, un, grupos


def grupos_app(un):
    """Faixas de código das unidades do programa: 2 grupos separados por biblioteca (RTL/VCL/Tee) — DERIVADO."""
    fins = sorted(u[0] for u in un)
    g1 = [f for f in fins if f < 0x600000]
    g2 = [f for f in fins if f >= 0x600000]
    return [(0x401000, max(g1)), (min(g2) - 0x20000, max(g2))] if g2 else [(0x401000, max(g1))]


def unidade_de(a, un, fins):
    k = bisect.bisect_left(fins, a)
    if k < len(un):
        return un[k][1]
    return None


def gerar(exe, saida, cobertura=COBERTURA):
    b = Binario(exe)
    cl = rtti.construir(b)
    cod, nomes, un, grupos = montar(b, cl)
    fins = [u[0] for u in un]
    ents = sorted(cod.entradas)
    starts = ents
    cob = {}
    if cobertura and os.path.isfile(cobertura):
        cob = json.load(open(cobertura, encoding='utf-8'))
    docs = cob.get('enderecos', {})
    funcs = []
    for i, a in enumerate(starts):
        nxt = starts[i + 1] if i + 1 < len(starts) else b.ce
        # tamanho útil = última instrução visitada contígua dentro de [a, nxt)
        o = a - cod.t0
        fimu = a
        for k in range(a - cod.t0, min(nxt - cod.t0, len(cod.vis))):
            if cod.vis[k]:
                fimu = cod.t0 + k
        in_app = any(g0 <= a < g1 for g0, g1 in grupos)
        f = {'endereco': h(a), 'tamanho_ate_proximo': nxt - a, 'origem': cod.entradas[a]}
        if a in nomes:
            f['nome'], f['fonte_nome'] = nomes[a]
        elif a in cod.thunk:
            f['nome'], f['fonte_nome'] = 'thunk -> ' + cod.thunk[a], 'import'
        f['unidade'] = unidade_de(a, un, fins) if in_app else None
        f['regiao'] = 'programa' if in_app else 'biblioteca'
        ha = docs.get(h(a))
        if ha:
            f['citada_em'] = ha
        funcs.append(f)
    app = [f for f in funcs if f['regiao'] == 'programa']
    meta = {'exe_sha256': b.sha256, 'n_funcoes_descobertas': len(funcs), 'n_funcoes_programa': len(app),
            'n_funcoes_biblioteca': len(funcs) - len(app),
            'bytes_codigo_visitados': int(sum(cod.vis)) if False else None,
            'unidades': [{'unidade': k, 'marcador_fim': h(a)} for a, k in un],
            'faixas_programa': [[h(x), h(y)] for x, y in grupos],
            'legenda': 'OBS-COD: entradas por descida recursiva a partir de nomes/entrada/chamadas; unidade = DERIVADO.'}
    return b, cl, cod, funcs, meta


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--exe')
    ap.add_argument('--out', default=SAIDA)
    a = ap.parse_args()
    exe = achar_exe(a.exe)
    if not exe:
        sys.exit('ProgBase.exe não encontrado: defina PROGBASE_EXE')
    import time
    t = time.time()
    b, cl, cod, funcs, meta = gerar(exe, a.out)
    print(meta['n_funcoes_descobertas'], meta['n_funcoes_programa'], time.time() - t)


if __name__ == '__main__':
    main()
