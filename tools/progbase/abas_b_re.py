#!/usr/bin/env python3
"""Biblioteca de engenharia reversa estática do ProgBase.exe para as abas do TFormConfig (D3b).

Tudo sai do próprio executável (pefile + capstone; nada é executado aqui) e do DFM versionado.
Reaproveita `tools/emulacao/flags_do_original.py` (Binario, Rtti, chamadas) e `orig_config_emulator.parse_dfm`.
Determinístico. Sem caminhos absolutos locais: o .exe vem de $PROGBASE_EXE, ~/.cache/omegas/ProgBase.exe ou
do scratchpad passado por --exe.
"""
import collections
import os
import re
import struct
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(AQUI, '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'tools', 'emulacao'))
import flags_do_original as F  # noqa: E402
import orig_config_emulator as emu  # noqa: E402

SHA256_ESPERADO = '8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4'
DFM_CONFIG = os.path.join(ROOT, 'docs', 'evidence', 'dfm-original', 'TFORMCONFIG.txt')
DFM_STREAM = os.path.join(ROOT, 'docs', 'evidence', 'dfm-original', 'TSTREAMDATI.txt')
I18N_CAND = [os.path.join(ROOT, 'docs', 'evidence', 'dfm-original', 'i18n_TFORMCONFIG.json'),
             os.environ.get('PROGBASE_I18N', ''),
             os.path.join(os.path.expanduser('~'), 'protocolo-landi', 'dfm', 'i18n_all.json'),
             '/home/user/protocolo-landi/dfm/i18n_all.json']
LOG_CAND = [os.environ.get('PROGBASE_LOG', ''), os.path.join(ROOT, 'tests', 'fixtures', 'config-iniettori-lognovo-v1.json')]


def achar_exe(pedido=None):
    return F.achar_exe(pedido)


def h(x, w=0):
    return '0x%0*X' % (w, x)


class Ctx:
    """Binário + RTTI + índices de funções, carregados uma vez."""

    def __init__(self, exe):
        self.b = F.Binario(exe)
        self.b.md.skipdata = True
        self.rtti = F.Rtti(self.b)
        self.campos = self.rtti.fields('TFormConfig')            # offset -> nome do campo
        self.inv = {v: k for k, v in self.campos.items()}
        self.campos_sd = self.rtti.fields('TStreamDati')
        self.inv_sd = {v: k for k, v in self.campos_sd.items()}
        self.metodos = self.rtti.methods('TFormConfig')          # endereço -> nome
        self.metodos_inv = {v: k for k, v in self.metodos.items()}
        self.calls = F.chamadas(self.b)
        self.inicios = sorted(set(self.calls) | set(self.rtti.metodos))
        self._insn = None
        self._por_off = None

    # ------------------------------------------------------------ varredura completa (cacheada)
    def todas(self):
        if self._insn is None:
            self._insn = [i for i in self.b.md.disasm(self.b.code, self.b.cb)]
        return self._insn

    def func_de(self, a):
        return F.inicio_funcao(self.b, a, self.inicios)

    def nome_func(self, a):
        if a in self.metodos:
            return 'TFormConfig.' + self.metodos[a]
        if a in self.rtti.metodos:
            c, m = self.rtti.metodos[a]
            return c + '.' + m
        return None

    def usos_campo(self, off, classe='TFormConfig'):
        """[(função, [instruções])] que usam [reg+off]. Aproximação: qualquer base de registrador."""
        if self._por_off is None:
            d = collections.defaultdict(list)
            rx = re.compile(r'\[(\w+)(?: \+ (0x[0-9a-f]+))?\]')
            for i in self.todas():
                if '[' not in i.op_str:
                    continue
                for m in rx.finditer(i.op_str):
                    if m.group(2):
                        d[int(m.group(2), 16)].append(i)
            self._por_off = d
        out = collections.defaultdict(list)
        for i in self._por_off.get(off, []):
            out[self.func_de(i.address)].append(i)
        return out

    # ------------------------------------------------------------ strings
    def string_em(self, v):
        """Literal Delphi (AnsiString/UnicodeString) ou PChar em v, se parecer texto."""
        b = self.b
        try:
            o = b.va2off(v)
        except Exception:
            return None
        raw = b.raw
        # UnicodeString: header 12 bytes (cp u16, elem u16, ref i32, len i32) -> UTF-16
        try:
            ln = struct.unpack_from('<I', raw, o - 4)[0]
            el = struct.unpack_from('<H', raw, o - 10)[0]
            if el == 2 and 0 < ln < 200:
                s = raw[o:o + 2 * ln].decode('utf-16le')
                if s.isprintable():
                    return s
            if el == 1 and 0 < ln < 200:
                s = raw[o:o + ln].decode('latin-1')
                if s.isprintable():
                    return s
        except Exception:
            pass
        # asciiz
        e = raw.find(b'\0', o, o + 160)
        if e > o:
            s = raw[o:e]
            if all(32 <= c < 127 for c in s):
                return s.decode()
        # utf16z
        try:
            e = o
            while raw[e:e + 2] != b'\0\0' and e < o + 320:
                e += 2
            s = raw[o:e].decode('utf-16le')
            if 0 < len(s) and s.isprintable():
                return s
        except Exception:
            pass
        return None

    # ------------------------------------------------------------ disassembly anotado
    def anota(self, i):
        """Comentário para uma instrução."""
        notas = []
        for m in re.finditer(r'\[(\w+) \+ (0x[0-9a-f]+)\]', i.op_str):
            reg, off = m.group(1), int(m.group(2), 16)
            if reg == 'ebx' and off in self.campos:
                notas.append(self.campos[off])
            elif off in self.campos_sd and reg != 'ebx':
                notas.append('SD.' + self.campos_sd[off])
        if i.mnemonic == 'call':
            try:
                t = int(i.op_str, 16)
                n = self.nome_func(t)
                if n:
                    notas.append(n)
            except ValueError:
                pass
        if i.mnemonic in ('mov', 'push') and re.search(r'(?:, |^)0x[0-9a-f]{6,7}$', i.op_str):
            v = int(i.op_str.split(', ')[-1], 16)
            if 0x400000 <= v < 0x1200000:
                s = self.string_em(v)
                if s:
                    notas.append(repr(s))
        return ' ; '.join(notas)

    def dump(self, ini, fim=None, n=None, maxn=800):
        out = []
        a = ini
        cont = 0
        while cont < maxn:
            i = self.b.ins(a)
            if i is None:
                break
            nota = self.anota(i)
            out.append('%08x  %-6s %-40s%s' % (i.address, i.mnemonic, i.op_str, ('  ; ' + nota) if nota else ''))
            a += i.size
            cont += 1
            if fim and a >= fim:
                break
            if n and cont >= n:
                break
            if not fim and not n and i.mnemonic == 'ret':
                break
        return '\n'.join(out)


def dfm_arvore(caminho=DFM_CONFIG):
    with open(caminho, encoding='utf-8', errors='replace') as fh:
        return emu.parse_dfm(fh.read())


def indexa(o, idx=None, pai=None):
    idx = {} if idx is None else idx
    o.pai = pai
    idx[o.nome] = o
    for f in o.filhos:
        indexa(f, idx, o)
    return idx


def carregar_i18n():
    import json
    for p in I18N_CAND:
        if p and os.path.isfile(p):
            with open(p, encoding='utf-8') as fh:
                return json.load(fh), p
    return {}, None


def conserta(s):
    """O i18n_all.json tem UTF-8 lido como Latin-1; desfaz quando possível."""
    if not isinstance(s, str):
        return s
    try:
        return s.encode('latin-1').decode('utf-8')
    except Exception:
        return s


def defaults_construtor(ctx, ini=0x42A9E4, fim=0x42B224):
    """Stores de constante no objeto TStreamDati feitos pelo construtor (OBS-COD): [(offset, tamanho, valor, endereço)].
    O construtor é a rotina em 0x42A9E4 (slot DoCreate da VMT do TStreamDati); os stores imediatos
    `mov [reg+off], imm` com off >= 0x80 caem no próprio objeto (reg = [ebx] = Self).
    DERIVADO: assume que todos executam (a varredura é linear; ramos condicionais dentro do trecho não foram resolvidos)."""
    out = []
    a = ini
    rx = re.compile(r'(byte|word|dword) ptr \[e\w\w \+ (0x[0-9a-f]+)\], (0x[0-9a-f]+|\d+)$')
    while a < fim:
        i = ctx.b.ins(a)
        if i is None:
            break
        a += i.size
        if i.mnemonic != 'mov':
            continue
        m = rx.match(i.op_str)
        if not m:
            continue
        off = int(m.group(2), 16)
        if off < 0x80 or off > 0x3EBC:
            continue
        sz = {'byte': 1, 'word': 2, 'dword': 4}[m.group(1)]
        out.append((off, sz, int(m.group(3), 0), i.address))
    return out


# ---------------------------------------------------------------- tabelas de despacho do TStreamDati (OBS-COD)
# Cada uma é `cmp r, N; ja default; jmp [r*4+TAB]` (opcionalmente com tabela de bytes intermediária).
TABELAS_SD = {
    'set_esc': dict(fn=0x436444, tab=0x43646F, n=0x7F, byte=None, base=0),
    'get_esc': dict(fn=0x434CC0, tab=0x434CE0, n=0x7F, byte=None, base=0),
    'set_idx': dict(fn=0x43734C, tab=0x4373EE, n=0x6B, byte=0x437383, base=0x13),
    'get_idx': dict(fn=0x435C6C, tab=0x435D04, n=0x6B, byte=0x435C99, base=0x13),
}


def caso(ctx, tabela, codigo):
    """Endereço do tratamento do `codigo` na tabela de despacho (None se fora da faixa)."""
    t = TABELAS_SD[tabela]
    k = codigo - t['base']
    if k < 0 or k >= t['n']:
        return None
    b = ctx.b
    idx = k
    if t['byte']:
        idx = b.raw[b.va2off(t['byte'] + k)]
    return b.u32(t['tab'] + idx * 4)
