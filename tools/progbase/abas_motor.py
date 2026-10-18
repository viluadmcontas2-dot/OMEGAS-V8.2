#!/usr/bin/env python3
"""Motor de engenharia reversa das abas do TFormConfig (ProgBase.exe, estatico; nada e executado aqui).

Le do proprio .exe:
  * a rotina de amarracao 0x48A228 (controle -> 'codigo de dado' [ctl+0x2EC] e indice [ctl+0x2F0]);
  * as 4 tabelas de despacho do TStreamDati: Set escalar 0x436444, Get escalar 0x434CC0,
    Set indexado 0x43734C, Get indexado 0x435C6C (cada caso = uma conversao tela<->ECU);
  * um interpretador abstrato x87/inteiro que transforma cada caso numa formula (texto + funcao Python).
Marcas: OBS-COD = lido do codigo; DERIVADO = calculado a partir do codigo; INFERIDO/DESCONHECIDO.
Depende de pefile e capstone (so para extrair; o JSON gerado e versionado)."""
import os
import re
import struct
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(AQUI, '..', 'emulacao'))
import flags_do_original as F  # noqa: E402

A_BIND = (0x48A228, 0x48AC28)
A_ROUND = 0xA45A58            # trunc: fldcw 0xC01 + fistp -> trunca para zero (OBS-COD)
TAILS_SET_ESC = 0x4372B8
TAILS_SET_IDX = 0x437B78
TAB = {
    'set_esc': dict(fn=0x436444, tab=0x43646F, n=0x7F, byte=None, base=0, tail=0x4372B8),
    'get_esc': dict(fn=0x434CC0, tab=0x434CE0, n=0x7F, byte=None, base=0, tail=0x435BFC),
    'set_idx': dict(fn=0x43734C, tab=0x4373EE, n=0x6B, byte=0x437383, base=0x13, tail=0x437B78),
    'get_idx': dict(fn=0x435C6C, tab=0x435D04, n=0x6B, byte=0x435C99, base=0x13, tail=None),
}


def abrir(exe):
    b = F.Binario(exe)
    r = F.Rtti(b)
    return b, r


def casos(b, nome):
    """{codigo: endereco do caso}"""
    t = TAB[nome]
    out = {}
    for c in range(t['n']):
        if t['byte']:
            o = b.va2off(t['byte'] + c)
            idx = b.raw[o]
        else:
            idx = c
        out[c + t['base']] = b.u32(t['tab'] + idx * 4)
    return out


# ---------------------------------------------------------------- amarracao
def amarracao(b, r):
    """Lista de {controle, offset, prop, valor}: valor = int, ou {'se_LR':a,'senao':b} para o ramo por [obj+0x80D]."""
    fc = r.fields('TFormConfig')
    ins = []
    a = A_BIND[0]
    while a < A_BIND[1]:
        i = b.ins(a)
        ins.append(i)
        a += i.size
    reg, out = {}, []
    for k, i in enumerate(ins):
        s = i.mnemonic + ' ' + i.op_str
        m = re.match(r'mov (e\w\w), dword ptr \[eax \+ (0x[0-9a-f]+)\]$', s)
        if m:
            reg[m.group(1)] = int(m.group(2), 16)
            continue
        m = re.match(r'mov dword ptr \[(e\w\w) \+ (0x2ec|0x2f0|0x330)\], (\w+)$', s)
        if not m:
            continue
        rg, p, v = m.groups()
        off = reg.get(rg)
        prop = {'0x2ec': 'codigo', '0x2f0': 'indice', '0x330': 'prop330'}[p]
        if v.startswith('e'):
            vals, cond, tipo = {}, None, None
            for j in range(k - 1, max(0, k - 16), -1):
                t = ins[j]
                tt = t.mnemonic + ' ' + t.op_str
                mm = re.match(r'mov %s, (0x[0-9a-f]+)$' % v, tt)
                if mm:
                    vals[t.address] = int(mm.group(1), 16)
                if tt == 'or %s, 0xffffffff' % v:
                    vals[t.address] = -1
                if tt == 'xor %s, %s' % (v, v):
                    vals[t.address] = 0
                if 'cmp byte ptr [ecx + 0x80d], 0' in tt:
                    jn = ins[j + 1]
                    cond = jn.mnemonic
                    break
                if tt.startswith('cmp byte ptr'):
                    break
            if cond is None:
                # registrador fixo (ex.: ebx = 0 do 'xor ebx,ebx')
                val = 0 if v == 'ebx' else None
                out.append(dict(offset=off, controle=fc.get(off), prop=prop, valor=val, ramo=None))
                continue
            ordem = sorted(vals)            # menor endereco = ramo fall-through (apos o jcc)
            fall, tomado = vals[ordem[0]], vals[ordem[-1]]
            # je: se [0x80D]==0 salta ao 'tomado'; fall-through => 0x80D!=0
            if cond == 'je':
                lr, nlr = fall, tomado
            else:                           # jne: se !=0 salta ao 'tomado'
                lr, nlr = tomado, fall
            out.append(dict(offset=off, controle=fc.get(off), prop=prop, valor=None,
                            ramo={'familia_LR_0x80D!=0': lr, 'demais': nlr}))
        else:
            out.append(dict(offset=off, controle=fc.get(off), prop=prop, valor=int(v, 0), ramo=None))
    return out


# ---------------------------------------------------------------- interpretador abstrato
class Sim:
    """Interpreta um caso das tabelas. Valores simbolicos = strings (ou numeros). Entrada 'v' (double da tela)."""
    FPU_HELP = {A_ROUND: 'trunc'}

    def __init__(self, b, modo):
        self.b, self.modo = b, modo
        self.reg = {}
        self.fpu = []
        self.mem = {}
        self.stack = []
        self.notas = []
        self.leituras = set()
        self.ramos = 0

    # ---- helpers
    def rd(self, op):
        op = op.strip()
        if op in ('eax', 'ebx', 'ecx', 'edx', 'esi', 'edi', 'ebp', 'esp'):
            if op == 'ebx':
                return 'SD'
            return self.reg.get(op, op + '?')
        if re.fullmatch(r'-?(0x[0-9a-f]+|\d+)', op):
            return int(op, 0)
        m = re.match(r'(?:(dword|word|byte|qword|xword|tbyte) ptr )?\[(.*)\]$', op)
        if m:
            return self.rdmem(m.group(2))
        return op + '?'

    def rdmem(self, ea):
        ea = ea.strip()
        m = re.fullmatch(r'ebp ([+-]) (0x[0-9a-f]+)', ea)
        if m:
            d = int(m.group(2), 16) * (1 if m.group(1) == '+' else -1)
            if d == 0x10 and self.modo.startswith('set'):
                return 'v'
            return self.mem.get(d, 'local%+d' % d)
        m = re.fullmatch(r'ebx \+ (0x[0-9a-f]+)', ea)
        if m:
            off = int(m.group(1), 16)
            self.leituras.add(off)
            return 'SD[0x%X]' % off
        m = re.fullmatch(r'(0x[0-9a-f]+)', ea)
        if m:
            return ('K', int(m.group(1), 16))
        return ea + '?'

    def const(self, a, tam):
        b = self.b
        o = b.va2off(a)
        if tam == 4:
            return struct.unpack('<f', b.raw[o:o + 4])[0]
        if tam == 8:
            return struct.unpack('<d', b.raw[o:o + 8])[0]
        if tam == 10:
            mant, ex = struct.unpack('<QH', b.raw[o:o + 10])
            s = -1 if ex & 0x8000 else 1
            e = (ex & 0x7FFF) - 16383
            return s * mant / (1 << 63) * 2.0 ** e
        raise ValueError

    def fl(self, op):
        m = re.match(r'(dword|qword|xword|tbyte) ptr \[(.*)\]$', op)
        if not m:
            return None
        sz = {'dword': 4, 'qword': 8, 'xword': 10, 'tbyte': 10}[m.group(1)]
        v = self.rdmem(m.group(2))
        if isinstance(v, tuple) and v[0] == 'K':
            return self.const(v[1], sz)
        return v


def fmt(x):
    if isinstance(x, float):
        r = repr(round(x, 12))
        return r[:-2] if r.endswith('.0') else r
    return str(x)


def _bin(op, a, c):
    if isinstance(a, (int, float)) and isinstance(c, (int, float)):
        try:
            return {'+': a + c, '-': a - c, '*': a * c, '/': a / c}[op]
        except ZeroDivisionError:
            pass
    return '(%s %s %s)' % (fmt(a), op, fmt(c))


def executar(b, ini, modo, limite=160):
    """Segue o caso a partir de `ini`. Devolve dict(estado final, passos, ok, motivo)."""
    s = Sim(b, modo)
    a, n = ini, 0
    res = dict(ok=False, motivo=None, ramos=0, escrita=None, valor=None, leituras=None)
    while n < limite:
        i = b.ins(a)
        if i is None:
            res['motivo'] = 'instrucao invalida'
            break
        n += 1
        m, o = i.mnemonic, i.op_str
        nxt = a + i.size
        try:
            if m == 'fld':
                x = s.fl(o)
                if x is None:
                    m2 = re.match(r'st\((\d)\)', o)
                    x = s.fpu[-1 - int(m2.group(1))]
                if x is None or isinstance(x, tuple):
                    raise ValueError('fld')
                s.fpu.append(x)
            elif m == 'fld1':
                s.fpu.append(1.0)
            elif m == 'fldz':
                s.fpu.append(0.0)
            elif m == 'fild':
                mm = re.match(r'(dword|qword|word) ptr \[(.*)\]$', o)
                s.fpu.append(s.rdmem(mm.group(2)))
            elif m in ('fmul', 'fdiv', 'fadd', 'fsub', 'fdivr', 'fsubr'):
                opx = {'fmul': '*', 'fdiv': '/', 'fadd': '+', 'fsub': '-', 'fdivr': '/', 'fsubr': '-'}[m]
                rev = m in ('fdivr', 'fsubr')
                if o.startswith(('dword', 'qword')):
                    y = s.fl(o)
                    x = s.fpu.pop()
                    s.fpu.append(_bin(opx, y, x) if rev else _bin(opx, x, y))
                else:
                    raise ValueError('forma ' + m)
            elif m in ('fmulp', 'fdivp', 'faddp', 'fsubp', 'fdivrp', 'fsubrp'):
                opx = {'fmulp': '*', 'fdivp': '/', 'faddp': '+', 'fsubp': '-', 'fdivrp': '/', 'fsubrp': '-'}[m]
                st0 = s.fpu.pop()
                st1 = s.fpu.pop()
                # fmulp st(1): st1 = st1 op st0 (fdivp: st1/st0 ; fdivrp: st0/st1)
                if m in ('fdivrp', 'fsubrp'):
                    s.fpu.append(_bin(opx, st0, st1))
                else:
                    s.fpu.append(_bin(opx, st1, st0))
            elif m == 'fxch':
                s.fpu[-1], s.fpu[-2] = s.fpu[-2], s.fpu[-1]
            elif m == 'fchs':
                s.fpu[-1] = _bin('*', -1, s.fpu[-1])
            elif m in ('wait', 'nop'):
                pass
            elif m == 'call':
                t = int(o, 16)
                if t == A_ROUND:
                    s.reg['eax'] = 'trunc(%s)' % fmt(s.fpu.pop())
                    s.reg['edx'] = 'edx?'
                else:
                    res['motivo'] = 'chamada %s' % o
                    break
            elif m == 'push':
                s.stack.append(s.rd(o))
            elif m == 'pop':
                s.reg[o] = s.stack.pop()
            elif m == 'mov':
                d, sr = [x.strip() for x in o.split(', ', 1)]
                if d.startswith(('dword ptr', 'word ptr', 'byte ptr')):
                    mm = re.match(r'.*\[ebp ([+-]) (0x[0-9a-f]+)\]', d)
                    if mm:
                        s.mem[int(mm.group(2), 16) * (1 if mm.group(1) == '+' else -1)] = s.rd(sr)
                else:
                    if d in ('esi', 'edi', 'eax', 'ecx', 'edx'):
                        s.reg[d] = s.rd(sr)
                    elif d == 'ebx':
                        pass
            elif m == 'lea':
                d, sr = [x.strip() for x in o.split(', ', 1)]
                mm = re.match(r'dword ptr \[ebx \+ (0x[0-9a-f]+)\]$|\[ebx \+ (0x[0-9a-f]+)\]$', sr)
                if mm:
                    s.reg[d] = 'EA[0x%X]' % int(mm.group(1) or mm.group(2), 16)
                else:
                    s.reg[d] = 'lea?'
            elif m in ('and', 'or', 'xor', 'add', 'sub', 'imul', 'shl', 'shr', 'sar', 'neg'):
                parts = [x.strip() for x in o.split(', ')]
                d = parts[0]
                if m == 'xor' and len(parts) > 1 and parts[0] == parts[1]:
                    s.reg[d] = 0
                elif m == 'neg':
                    s.reg[d] = _bin('*', -1, s.rd(d))
                else:
                    x, y = s.rd(d), s.rd(parts[1])
                    sym = {'and': '&', 'or': '|', 'xor': '^', 'add': '+', 'sub': '-', 'imul': '*', 'shl': '<<',
                           'shr': '>>', 'sar': '>>'}[m]
                    if isinstance(x, int) and isinstance(y, int):
                        x &= 0xFFFFFFFF
                        y &= 0xFFFFFFFF
                        r_ = {'&': x & y, '|': x | y, '^': x ^ y, '+': x + y, '-': x - y, '*': x * y,
                              '<<': x << y, '>>': x >> y}[sym]
                        s.reg[d] = r_ & 0xFFFFFFFF
                    else:
                        s.reg[d] = '(%s %s %s)' % (fmt(x), sym, fmt(y))
            elif m == 'cdq':
                s.reg['edx'] = 'sign(eax)'
            elif m in ('idiv', 'div'):
                dv = s.rd(o)
                s.reg['eax'] = '(%s // %s)' % (fmt(s.reg.get('eax')), fmt(dv)) if not (
                    isinstance(s.reg.get('eax'), int) and isinstance(dv, int)) else int(s.reg['eax'] / dv)
            elif m == 'movzx':
                d, sr = [x.strip() for x in o.split(', ', 1)]
                s.reg[d] = s.rd(sr)
            elif m == 'jmp':
                t = int(o, 16) if re.fullmatch(r'0x[0-9a-f]+', o) else None
                if t is None:
                    res['motivo'] = 'jmp indireto'
                    break
                if modo == 'set_esc' and t == TAILS_SET_ESC or modo == 'set_idx' and t == TAILS_SET_IDX:
                    res.update(ok=True, escrita=dict(valor=s.reg.get('esi'), vetor=s.reg.get('eax'),
                                                     cache=s.reg.get('edi')))
                    break
                if modo == 'get_esc' and t == TAB['get_esc']['tail']:
                    res.update(ok=True, valor=s.fpu[-1] if s.fpu else None)
                    break
                a = t
                continue
            elif m.startswith('j') and m != 'jmp':
                s.ramos += 1
                res['ramos'] = s.ramos
                # segue o fall-through; o ramo alternativo fica registrado
                s.notas.append('desvio %s %s em 0x%X' % (m, o, a))
            elif m == 'ret':
                res['motivo'] = 'ret'
                break
            elif m in ('cmp', 'test'):
                pass
            else:
                res['motivo'] = 'instrucao %s %s' % (m, o)
                break
        except Exception as e:  # noqa: BLE001
            res['motivo'] = 'erro %s em 0x%X %s %s' % (e, a, m, o)
            break
        a = nxt
    res['leituras'] = sorted(s.leituras)
    res['notas'] = s.notas
    res['passos'] = n
    return res
