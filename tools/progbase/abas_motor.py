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
REGS = ('eax', 'ecx', 'edx', 'esi', 'edi', 'ebx')
HELPERS_LEITURA_VETOR = (0x976D98, 0x97947C)     # le o vetor da ECU para o cache (efeito, sem valor)


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
    if op == '*' and (a == 1 or a == 1.0):
        return c
    if op == '*' and (c == 1 or c == 1.0):
        return a
    return '(%s %s %s)' % (fmt(a), op, fmt(c))


class Sim:
    def __init__(self, b, modo):
        self.b, self.modo = b, modo
        self.reg = {}
        self.fpu = []
        self.mem = {}
        self.stack = []
        self.notas = []
        self.leituras = set()
        self.efeitos = []
        self.chamadas = []
        if modo == 'set_idx':
            self.reg['edi'] = 'i'
        if modo == 'get_idx':
            self.reg['esi'] = 'i'

    VAL = {'set_esc': 0x10, 'set_idx': 0x14}

    def ea(self, e):
        """Endereco efetivo [a + b*k + d] -> (tipo, deslocamento, indice)."""
        e = e.strip()
        N = r'(0x[0-9a-f]+|\d+)'
        m = re.fullmatch(r'ebp ([+-]) ' + N, e)
        if m:
            return ('ebp', int(m.group(2), 0) * (1 if m.group(1) == '+' else -1), None)
        m = re.fullmatch(r'(e\w\w)(?: \+ (e\w\w)\*(\d))?(?: \+ ' + N + ')?', e)
        if m and (m.group(1) == 'ebx' or self.reg.get(m.group(1)) == 'SD'):
            idx = None
            if m.group(2):
                idx = '%s*%s' % (fmt(self.val(m.group(2))), m.group(3))
            return ('sd', int(m.group(4), 0) if m.group(4) else 0, idx)
        m = re.fullmatch(N, e)
        if m:
            return ('abs', int(m.group(1), 0), None)
        m = re.fullmatch(r'(e\w\w)\*(\d) \+ ' + N, e)
        if m:
            return ('tab', int(m.group(3), 0), '%s*%s' % (fmt(self.val(m.group(1))), m.group(2)))
        m2 = re.fullmatch(r'(e\w\w)(?: \+ (e\w\w)\*(\d))?(?: \+ ' + N + ')?', e)
        if m2:
            return ('reg', m2, None)
        return ('?', e, None)

    def val(self, op):
        op = op.strip()
        if op == 'ebx':
            return 'SD'
        if op in REGS or op in ('ebp', 'esp'):
            return self.reg.get(op, op + '?')
        if re.fullmatch(r'-?(0x[0-9a-f]+|\d+)', op):
            return int(op, 0)
        m = re.match(r'(?:(\w+) ptr )?\[(.*)\]$', op)
        if m:
            return self.load(m.group(2))
        return op + '?'

    def load(self, e):
        k, d, idx = self.ea(e)
        if k == 'ebp':
            if d == self.VAL.get(self.modo):
                return 'v'
            if d == 0xc and self.modo != 'none':
                return 'codigo'
            return self.mem.get(d, 'local%+d' % d)
        if k == 'sd':
            self.leituras.add(d)
            if idx:
                return 'SD[0x%X + %s]' % (d, idx)
            return 'SD[0x%X]' % d
        if k == 'abs':
            return ('K', d)
        if k == 'tab':
            return 'TAB[0x%X + %s]' % (d, idx)
        return '?mem'

    def const(self, a, tam):
        b = self.b
        o = b.va2off(a)
        if tam == 4:
            return struct.unpack('<f', b.raw[o:o + 4])[0]
        if tam == 8:
            return struct.unpack('<d', b.raw[o:o + 8])[0]
        mant, ex = struct.unpack('<QH', b.raw[o:o + 10])
        s = -1 if ex & 0x8000 else 1
        return s * mant / (1 << 63) * 2.0 ** ((ex & 0x7FFF) - 16383)

    def fl(self, op):
        m = re.match(r'(dword|qword|xword|tbyte) ptr \[(.*)\]$', op)
        if not m:
            return None
        sz = {'dword': 4, 'qword': 8, 'xword': 10, 'tbyte': 10}[m.group(1)]
        v = self.load(m.group(2))
        if isinstance(v, tuple) and v[0] == 'K':
            return self.const(v[1], sz)
        return v

    def store(self, d, v):
        m = re.match(r'(?:\w+ ptr )?\[(.*)\]$', d)
        k, off, idx = self.ea(m.group(1))
        if k == 'ebp':
            self.mem[off] = v
        elif k == 'sd':
            self.efeitos.append('SD[0x%X%s] := %s' % (off, '+' + idx if idx else '', fmt(v)))

    def setreg(self, d, v):
        if d in REGS:
            self.reg[d] = v


def _int(x):
    return isinstance(x, int)


def executar(b, ini, modo, limite=220):
    s = Sim(b, modo)
    tails = {'set_esc': 0x4372B8, 'set_idx': 0x437B78, 'get_esc': 0x435BFC, 'get_idx': 0x4363D5}
    a, n = ini, 0
    res = dict(ok=False, motivo=None, ramos=0, escrita=None, valor=None)
    nr = 0
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
                    raise ValueError('fld st')
                s.fpu.append(x)
            elif m == 'fld1':
                s.fpu.append(1.0)
            elif m == 'fldz':
                s.fpu.append(0.0)
            elif m == 'fild':
                mm = re.match(r'(\w+) ptr \[(.*)\]$', o)
                s.fpu.append(s.load(mm.group(2)))
            elif m in ('fmul', 'fdiv', 'fadd', 'fsub', 'fdivr', 'fsubr'):
                opx = {'fmul': '*', 'fdiv': '/', 'fadd': '+', 'fsub': '-', 'fdivr': '/', 'fsubr': '-'}[m]
                if not o.startswith(('dword', 'qword')):
                    raise ValueError('forma ' + m)
                y = s.fl(o)
                x = s.fpu.pop()
                s.fpu.append(_bin(opx, y, x) if m in ('fdivr', 'fsubr') else _bin(opx, x, y))
            elif m in ('fmulp', 'fdivp', 'faddp', 'fsubp', 'fdivrp', 'fsubrp'):
                opx = {'fmulp': '*', 'fdivp': '/', 'faddp': '+', 'fsubp': '-', 'fdivrp': '/', 'fsubrp': '-'}[m]
                st0, st1 = s.fpu.pop(), s.fpu.pop()
                s.fpu.append(_bin(opx, st0, st1) if m in ('fdivrp', 'fsubrp') else _bin(opx, st1, st0))
            elif m == 'fxch':
                s.fpu[-1], s.fpu[-2] = s.fpu[-2], s.fpu[-1]
            elif m == 'fchs':
                s.fpu[-1] = _bin('*', -1, s.fpu[-1])
            elif m == 'fstp':
                if o == 'dword ptr [esp]':
                    s.stack[-1] = s.fpu.pop()
                else:
                    s.store(o, s.fpu.pop())
            elif m in ('wait', 'nop'):
                pass
            elif m == 'call':
                t = int(o, 16)
                if t == A_ROUND:
                    s.reg['eax'] = 'trunc(%s)' % fmt(s.fpu.pop())
                    s.reg['edx'] = 'edx?'
                elif t in HELPERS_LEITURA_VETOR:
                    s.efeitos.append('le_vetor_da_ecu(0x%X)' % t)
                    s.stack.clear()
                elif t == 0x433FFC:
                    s.reg['eax'] = 'SD[0x2980]'
                    s.leituras.add(0x2980)
                    s.stack.clear()
                else:
                    args = list(reversed(s.stack))
                    s.chamadas.append(t)
                    s.reg['eax'] = 'f_%X(%s)' % (t, ', '.join(fmt(x) for x in args))
                    if '0x42a8f4' in o or t in (0x42A788, 0x42A52C, 0x42A698, 0x42A8F4):
                        pass
                    if modo.startswith('get'):
                        s.fpu.append(s.reg['eax'])
                    s.stack.clear()
            elif m == 'push':
                s.stack.append(s.val(o))
            elif m == 'pop':
                v_ = s.stack.pop() if s.stack else 'pop?'
                if o in REGS:
                    s.reg[o] = v_
            elif m == 'add' and o.startswith('esp, '):
                k = int(o.split(', ')[1], 0)
                if k < 0:
                    s.stack.extend(['slot'] * (-k // 4))
                else:
                    for _ in range(k // 4):
                        if s.stack:
                            s.stack.pop()
            elif m == 'mov':
                d, sr = [x.strip() for x in o.split(', ', 1)]
                if '[' in d:
                    s.store(d, s.val(sr))
                else:
                    s.setreg(d, s.val(sr))
            elif m == 'movzx':
                d, sr = [x.strip() for x in o.split(', ', 1)]
                s.setreg(d, s.val(sr))
            elif m == 'lea':
                d, sr = [x.strip() for x in o.split(', ', 1)]
                inner = re.match(r'(?:\w+ ptr )?\[(.*)\]$', sr).group(1)
                k, off, idx = s.ea(inner)
                if k == 'sd':
                    s.setreg(d, 'EA[0x%X%s]' % (off, '+' + idx if idx else ''))
                elif k == 'reg':
                    mm = off
                    r1 = s.val(mm.group(1))
                    t = r1
                    if mm.group(2):
                        t = _bin('+', t, _bin('*', s.val(mm.group(2)), int(mm.group(3))))
                    if mm.group(4):
                        t = _bin('+', t, int(mm.group(4), 0))
                    s.setreg(d, t)
                else:
                    s.setreg(d, 'lea?')
            elif m in ('and', 'or', 'xor', 'add', 'sub', 'imul', 'shl', 'shr', 'sar', 'neg', 'inc', 'dec'):
                parts = [x.strip() for x in o.split(', ')]
                d = parts[0]
                if m == 'xor' and len(parts) > 1 and parts[0] == parts[1]:
                    s.setreg(d, 0)
                elif m in ('neg',):
                    s.setreg(d, _bin('*', -1, s.val(d)))
                elif m in ('inc', 'dec'):
                    s.setreg(d, _bin('+' if m == 'inc' else '-', s.val(d), 1))
                else:
                    x = s.val(d)
                    y = s.val(parts[1]) if len(parts) > 1 else None
                    sym = {'and': '&', 'or': '|', 'xor': '^', 'add': '+', 'sub': '-', 'imul': '*', 'shl': '<<',
                           'shr': '>>', 'sar': '>>'}[m]
                    if _int(x) and _int(y):
                        x &= 0xFFFFFFFF
                        y &= 0xFFFFFFFF
                        r_ = {'&': x & y, '|': x | y, '^': x ^ y, '+': x + y, '-': x - y, '*': x * y,
                              '<<': x << y, '>>': x >> y}[sym]
                        v_ = r_ & 0xFFFFFFFF
                    else:
                        v_ = '(%s %s %s)' % (fmt(x), sym, fmt(y))
                    if '[' in d:
                        s.store(d, v_)
                    else:
                        s.setreg(d, v_)
            elif m == 'cdq':
                s.reg['edx'] = 'sign(eax)'
            elif m in ('idiv', 'div'):
                dv = s.val(o)
                ax = s.reg.get('eax')
                s.reg['eax'] = int(ax / dv) if _int(ax) and _int(dv) and dv else '(%s // %s)' % (fmt(ax), fmt(dv))
            elif m == 'jmp':
                t = int(o, 16) if re.fullmatch(r'0x[0-9a-f]+', o) else None
                if t is None:
                    res['motivo'] = 'jmp indireto'
                    break
                if t == tails[modo]:
                    if modo in ('set_esc', 'set_idx'):
                        res.update(ok=True, escrita=dict(valor=s.reg.get('esi'), vetor=s.reg.get('eax'),
                                                         cache=(s.reg.get('edi') if modo == 'set_esc' else
                                                                s.mem.get(-4))))
                    else:
                        res.update(ok=True, valor=s.fpu[-1] if s.fpu else None)
                    break
                a = t
                continue
            elif m.startswith('j'):
                nr += 1
                s.notas.append('desvio %s %s em 0x%X' % (m, o, a))
            elif m == 'ret':
                res['motivo'] = 'ret (caso sem escrita)'
                break
            elif m in ('cmp', 'test'):
                pass
            else:
                res['motivo'] = 'instrucao %s %s em 0x%X' % (m, o, a)
                break
        except Exception as e:  # noqa: BLE001
            res['motivo'] = 'erro %s em 0x%X %s %s' % (e, a, m, o)
            break
        a = nxt
    res['ramos'] = nr
    res['leituras'] = sorted(s.leituras)
    res['notas'] = s.notas
    res['efeitos'] = s.efeitos
    res['chamadas'] = s.chamadas
    res['passos'] = n
    return res


# ---------------------------------------------------------------- catalogo de codigos
def sc_por_nome(dfm_sd):
    """{nome: (SerialCode, classe)} do TSTREAMDATI.txt"""
    out, cur = {}, None
    for l in dfm_sd.split('\n'):
        m = re.match(r'  object (\w+): (\w+)', l)
        if m:
            cur = [m.group(1), m.group(2)]
            continue
        m = re.match(r'    SerialCode = (\d+)', l)
        if m and cur:
            out[cur[0]] = (int(m.group(1)), cur[1])
    return out


def _off(txt):
    m = re.fullmatch(r'SD\[(0x[0-9A-Fa-f]+)\]', str(txt)) if txt is not None else None
    return int(m.group(1), 16) if m else None


def catalogo_codigos(b, r, dfm_sd_texto):
    campos = r.fields('TStreamDati')
    scn = sc_por_nome(dfm_sd_texto)
    out = {}
    for esc, (sn, gn) in (('esc', ('set_esc', 'get_esc')), ('idx', ('set_idx', 'get_idx'))):
        cs, cg = casos(b, sn), casos(b, gn)
        for c in sorted(cs):
            rs = executar(b, cs[c], sn)
            rg = executar(b, cg[c], gn)
            if not (rs['ok'] or rg['ok']):
                continue
            e = out.setdefault(c, {})
            rec = dict(caso_set='0x%X' % cs[c], caso_get='0x%X' % cg[c])
            if rs['ok']:
                w = rs['escrita']
                off = _off(w['vetor'])
                nome = campos.get(off)
                rec.update(vetor=nome, vetor_offset=off, serial_code=scn.get(nome, (None,))[0],
                           classe_vetor=scn.get(nome, (None, None))[1],
                           set_expr=fmt(w['valor']), set_cache=fmt(w['cache']), set_notas=rs['notas'],
                           set_chamadas=['0x%X' % x for x in rs['chamadas']], set_ramos=rs['ramos'])
            else:
                rec.update(set_motivo=rs['motivo'])
            if rg['ok']:
                rec.update(get_expr=fmt(rg['valor']), get_notas=rg['notas'], get_ramos=rg['ramos'],
                           get_chamadas=['0x%X' % x for x in rg['chamadas']], get_efeitos=rg['efeitos'])
            else:
                rec.update(get_motivo=rg['motivo'])
            e[esc] = rec
    return out


# ---------------------------------------------------------------- varredura de metodos do formulario
ACESSORES = {
    0x436444: ('SetEsc', 'Set escalar por codigo (SD, codigo, double)'),
    0x43734C: ('SetIdx', 'Set indexado por codigo (SD, codigo, indice, double)'),
    0x434CC0: ('GetEsc', 'Get escalar por codigo (SD, codigo)'),
    0x435C6C: ('GetIdx', 'Get indexado por codigo (SD, codigo, indice)'),
    0x42CFB8: ('GetFlag1', 'caixa -> bit de FLAG_CONF1 (edx=caso)'),
    0x42D300: ('SetFlag1', 'bit de FLAG_CONF1 := ecx (edx=caso)'),
    0x42DF14: ('GetFlag2', 'bit de FLAG_CONF2 (edx=caso)'),
    0x42E16C: ('SetFlag2', 'bit de FLAG_CONF2 := ecx (edx=caso)'),
    0x42E754: ('GetFlag3', 'bit de FLAG_CONF3 (edx=caso)'),
    0x42E8D8: ('SetFlag3', 'bit de FLAG_CONF3 := ecx (edx=caso)'),
    0x42FFC8: ('GetCombo', 'valor de combo (edx=indice 0/1)'),
    0x43074C: ('SetCombo', 'grava combo (edx=caso)'),
    0x9244C8: ('EnviaComando', 'protocolo: comando direto (edx=id, buffer, n)'),
}
# alvos de acao sobre controles (Delphi/VCL): inferidos pelo uso (INFERIDO)
ACOES_CTRL = {0x7EDFA8: 'Visible', 0x96C73C: 'Visible', 0x7EDFE0: 'Enabled', 0x7EE08C: 'Text', 0x7EE0E0: 'Caption'}


def metodo_instrucoes(b, r, ini, fim_extra=0x3000):
    """Instrucoes alcancaveis do metodo a partir de `ini` (segue jcc/jmp para frente e para tras, ate o proximo metodo)."""
    ms = sorted(r.methods('TFormConfig'))
    prox = [s for s in ms if s > ini]
    lim = (prox[0] if prox else ini + fim_extra)
    seen, pilha, out = set(), [ini], []
    while pilha:
        x = pilha.pop()
        while x not in seen and ini <= x < lim:
            seen.add(x)
            i = b.ins(x)
            if i is None:
                break
            out.append(i)
            if i.mnemonic == 'ret':
                break
            if i.mnemonic == 'jmp':
                try:
                    x = int(i.op_str, 16)
                    continue
                except ValueError:
                    break
            if i.mnemonic.startswith('j'):
                try:
                    pilha.append(int(i.op_str, 16))
                except ValueError:
                    pass
            x += i.size
    return sorted(out, key=lambda i: i.address), lim


def _imm_reg(seq, k, reg, janela=14):
    """Valor imediato de `reg` antes da instrucao k (constante simples) ou None."""
    for j in range(k - 1, max(-1, k - janela), -1):
        t = seq[j]
        s = t.mnemonic + ' ' + t.op_str
        m = re.fullmatch(r'mov %s, (0x[0-9a-f]+|\d+)' % reg, s)
        if m:
            return int(m.group(1), 0)
        if s == 'xor %s, %s' % (reg, reg):
            return 0
        if s == 'or %s, 0xffffffff' % reg:
            return -1
        if t.mnemonic in ('call',):
            return None
        if re.match(r'(mov|lea|movzx|pop) %s,' % reg, s) or re.match(r'(mov|lea|movzx) %s,' % reg[1:], s):
            return None
    return None


def _pushes(seq, k, janela=24):
    """Argumentos empilhados antes da chamada k, do mais proximo (arg1) ao mais distante. Registrador -> tenta constante."""
    args = []
    for j in range(k - 1, max(-1, k - janela), -1):
        t = seq[j]
        if t.mnemonic == 'push':
            m = re.fullmatch(r'(0x[0-9a-f]+|\d+)', t.op_str)
            if m:
                args.append(int(m.group(1), 0))
            elif t.op_str in ('eax', 'ecx', 'edx', 'ebx', 'esi', 'edi'):
                v = _imm_reg(seq, j, t.op_str)
                args.append(v if v is not None else t.op_str)
            else:
                args.append(t.op_str)
        elif t.mnemonic in ('call', 'jmp') or t.mnemonic.startswith('j'):
            break
    return args


def eventos_metodo(b, r, nome_metodo, campos_form):
    """Resumo estruturado de um metodo: acessos ao TStreamDati, comandos diretos, acoes sobre controles."""
    ms = r.methods('TFormConfig')
    inv = {v: k for k, v in ms.items()}
    if nome_metodo not in inv:
        return None
    seq, lim = metodo_instrucoes(b, r, inv[nome_metodo])
    ev = []
    for k, i in enumerate(seq):
        if i.mnemonic != 'call':
            continue
        try:
            t = int(i.op_str, 16)
        except ValueError:
            continue
        if t in ACESSORES:
            nome = ACESSORES[t][0]
            d = dict(endereco='0x%X' % i.address, funcao=nome)
            if nome in ('GetFlag1', 'SetFlag1', 'GetFlag2', 'SetFlag2', 'GetFlag3', 'SetFlag3', 'GetCombo', 'SetCombo'):
                d['caso'] = _imm_reg(seq, k, 'edx')
                if nome.startswith('Set') and nome != 'SetCombo':
                    d['valor_ecx'] = _imm_reg(seq, k, 'ecx')
            elif nome in ('SetEsc', 'GetEsc', 'SetIdx', 'GetIdx'):
                p = _pushes(seq, k)
                d['codigo'] = p[1] if len(p) > 1 else None
                if nome in ('SetIdx', 'GetIdx'):
                    d['indice'] = p[2] if len(p) > 2 else None
                d['pushes'] = p
            elif nome == 'EnviaComando':
                d['comando_id'] = _imm_reg(seq, k, 'edx')
                d['pushes'] = _pushes(seq, k)
            ev.append(d)
        elif t in ACOES_CTRL:
            # eax = controle: ultimo `mov eax,[ebx+OFF]` antes
            ctl = None
            for j in range(k - 1, max(-1, k - 8), -1):
                mm = re.fullmatch(r'mov eax, dword ptr \[ebx \+ (0x[0-9a-f]+)\]', seq[j].mnemonic + ' ' + seq[j].op_str)
                if mm:
                    ctl = campos_form.get(int(mm.group(1), 16))
                    break
            ev.append(dict(endereco='0x%X' % i.address, funcao=ACOES_CTRL[t], controle=ctl,
                           valor_edx=_imm_reg(seq, k, 'edx')))
    return dict(metodo=nome_metodo, endereco='0x%X' % inv[nome_metodo], fim='0x%X' % lim, n_instr=len(seq), eventos=ev)


# ---------------------------------------------------------------- condicoes (estrutura if/else do compilador)
NEG = {'je': 'jne', 'jne': 'je', 'jl': 'jge', 'jge': 'jl', 'jg': 'jle', 'jle': 'jg', 'jb': 'jae', 'jae': 'jb',
       'ja': 'jbe', 'jbe': 'ja', 'js': 'jns', 'jns': 'js', 'jz': 'jnz', 'jnz': 'jz'}
FORMA = {'je': '== 0', 'jne': '!= 0', 'jz': '== 0', 'jnz': '!= 0'}


def _desc_chamada(seq, k, nomes):
    """Descricao da chamada anterior ao teste em seq[k]."""
    for j in range(k - 1, max(-1, k - 10), -1):
        t = seq[j]
        if t.mnemonic == 'call':
            try:
                a = int(t.op_str, 16)
            except ValueError:
                return 'chamada virtual %s' % t.op_str
            if a in ACOES_CTRL or a == A_ROUND:
                continue
            if a in ACESSORES:
                nm = ACESSORES[a][0]
                if nm.endswith(('Flag1', 'Flag2', 'Flag3', 'Combo')):
                    return '%s(caso %s)' % (nm, _imm_reg(seq, j, 'edx'))
                if nm in ('GetEsc', 'GetIdx'):
                    p = _pushes(seq, j)
                    return '%s(codigo %s)' % (nm, p[1] if len(p) > 1 else '?')
                return nm
            return nomes.get(a) or 'sub_%X' % a
        if t.mnemonic.startswith('j') or t.mnemonic == 'ret':
            break
    return None


def teste_de(seq, k, nomes, campos_sd):
    """(lhs, rhs, tipo) do cmp/test que antecede o jcc seq[k]. tipo: 'cmp' | 'and' (test lhs & rhs)."""
    if k == 0:
        return ('?', '?', 'cmp')
    t = seq[k - 1]
    s = t.mnemonic + ' ' + t.op_str
    m = re.fullmatch(r'cmp byte ptr \[(\w+) \+ 0x(80d|80e)\], 0', s)
    if m:
        return ({'80d': 'SD.familia_LR[+0x80D]', '80e': 'SD.conectado[+0x80E]'}[m.group(2)], '0', 'cmp')
    m = re.fullmatch(r'cmp (?:byte|dword) ptr \[(\w+) \+ (0x[0-9a-f]+)\], (0x[0-9a-f]+|\d+)', s)
    if m:
        off = int(m.group(2), 16)
        return ('[obj+0x%X]%s' % (off, ('(' + campos_sd[off] + ')') if off in campos_sd else ''), m.group(3), 'cmp')
    m = re.fullmatch(r'test byte ptr \[(\w+) \+ (0x[0-9a-f]+)\], (0x[0-9a-f]+|\d+)', s)
    if m:
        off = int(m.group(2), 16)
        return ('byte[obj+0x%X]%s' % (off, ('(' + campos_sd[off] + ')') if off in campos_sd else ''), m.group(3), 'and')
    if s in ('test al, al', 'test eax, eax', 'test dl, dl', 'test cl, cl'):
        return (_desc_chamada(seq, k - 1, nomes) or s, '0', 'cmp')
    m = re.fullmatch(r'cmp e?ax, (0x[0-9a-f]+|\d+)|cmp al, (0x[0-9a-f]+|\d+)', s)
    if m:
        return (_desc_chamada(seq, k - 1, nomes) or 'eax', m.group(1) or m.group(2), 'cmp')
    m = re.fullmatch(r'cmp (e\w\w), (0x[0-9a-f]+|\d+)', s)
    if m:
        return (m.group(1), m.group(2), 'cmp')
    return (s, '', 'cmp')


REL = {'je': '==', 'jz': '==', 'jne': '!=', 'jnz': '!=', 'jl': '<', 'jge': '>=', 'jg': '>', 'jle': '<=',
       'jb': '<', 'jae': '>=', 'ja': '>', 'jbe': '<=', 'js': '<', 'jns': '>='}
INV = {'==': '!=', '!=': '==', '<': '>=', '>=': '<', '>': '<=', '<=': '>'}


def render_cond(teste, jcc, tomado):
    """Condicao para o bloco executar: tomado=False => bloco no fall-through do jcc (condicao do jcc invertida)."""
    lhs, rhs, tipo = teste
    rel = REL.get(jcc, '?')
    # o bloco do fall-through roda quando o salto NAO e tomado
    if not tomado:
        rel = INV.get(rel, rel)
    if tipo == 'and':
        return '(%s & %s) %s 0' % (lhs, rhs, '!=' if rel == '!=' else '==')
    if rhs == '0' and rel in ('==', '!=') and ('(' in lhs and lhs.endswith(')') and lhs.split('(')[0] in
                                                  ('GetFlag1', 'GetFlag2', 'GetFlag3', 'GetCombo')) is False:
        pass
    return '%s %s %s' % (lhs, rel, rhs)


def condicoes_por_endereco(seq, nomes, campos_sd):
    """{endereco: [texto, ...]} com as condicoes ativas (regioes if/else a partir dos jcc para frente)."""
    regioes = []         # (inicio, fim, teste, jcc, tomado)
    out = {}
    for k, i in enumerate(seq):
        out[i.address] = [render_cond(r[2], r[3], r[4]) for r in regioes if r[0] <= i.address < r[1]]
        if i.mnemonic.startswith('j') and i.mnemonic != 'jmp':
            try:
                alvo_ = int(i.op_str, 16)
            except ValueError:
                continue
            if alvo_ > i.address:
                regioes.append((i.address + i.size, alvo_, teste_de(seq, k, nomes, campos_sd), i.mnemonic, False))
        elif i.mnemonic == 'jmp':
            try:
                alvo_ = int(i.op_str, 16)
            except ValueError:
                continue
            if alvo_ > i.address + i.size:
                fim_then = [r for r in regioes if r[1] == i.address + i.size]
                if fim_then:
                    r = fim_then[-1]
                    regioes.append((i.address + i.size, alvo_, r[2], r[3], True))
    return out


def _origem_valor(seq, k, nomes):
    """Descreve de onde vem o valor (edx/dl) passado a um setter de propriedade em seq[k]."""
    for j in range(k - 1, max(-1, k - 8), -1):
        t = seq[j]
        sj = t.mnemonic + ' ' + t.op_str
        if re.fullmatch(r'(mov edx, eax|mov dl, al|xor edx, edx|mov dl, \w+|setne \w+|sete \w+|and ecx, 1|and edx, 1)', sj) or t.mnemonic.startswith('set'):
            d = _desc_chamada(seq, j, nomes)
            if d:
                return d
        if re.fullmatch(r'mov e?dx, (0x[0-9a-f]+|\d+)|mov dl, (0x[0-9a-f]+|\d+)', sj):
            return None
    return None


def acoes_metodo(b, r, nome, nomes_pub, campos_form, campos_sd, prof=2, _visto=None, ini=None, via=None):
    """Acoes sobre controles (Visible/Enabled/Caption/Text/Checked) com as condicoes que as guardam."""
    ms = r.methods('TFormConfig')
    inv = {v: k for k, v in ms.items()}
    a0 = ini if ini is not None else inv.get(nome)
    if a0 is None:
        return []
    _visto = _visto if _visto is not None else set()
    if a0 in _visto:
        return []
    _visto.add(a0)
    seq, lim = metodo_instrucoes(b, r, a0)
    conds = condicoes_por_endereco(seq, nomes_pub, campos_sd)
    out = []
    for k, i in enumerate(seq):
        if i.mnemonic != 'call':
            continue
        try:
            t = int(i.op_str, 16)
        except ValueError:
            # chamada virtual: Checked
            m = re.fullmatch(r'dword ptr \[(\w+) \+ (0x10[48])\]', i.op_str)
            if m:
                ctl = None
                for j in range(k - 1, max(-1, k - 8), -1):
                    mm = re.fullmatch(r'mov eax, dword ptr \[ebx \+ (0x[0-9a-f]+)\]', seq[j].mnemonic + ' ' + seq[j].op_str)
                    if mm:
                        ctl = campos_form.get(int(mm.group(1), 16))
                        break
                if ctl:
                    out.append(dict(endereco='0x%X' % i.address, propriedade='Checked' if m.group(2) == '0x108' else 'lê Checked',
                                    controle=ctl, valor_imediato=None if m.group(2) == '0x108' else None,
                                    origem=_origem_valor(seq, k, nomes_pub) if m.group(2) == '0x108' else None,
                                    condicoes=conds.get(i.address, []), via=via))
            continue
        if t in ACOES_CTRL:
            ctl = None
            for j in range(k - 1, max(-1, k - 8), -1):
                mm = re.fullmatch(r'mov eax, dword ptr \[ebx \+ (0x[0-9a-f]+)\]', seq[j].mnemonic + ' ' + seq[j].op_str)
                if mm:
                    ctl = campos_form.get(int(mm.group(1), 16))
                    break
            out.append(dict(endereco='0x%X' % i.address, propriedade=ACOES_CTRL[t], controle=ctl,
                            valor_imediato=_imm_reg(seq, k, 'edx') if ACOES_CTRL[t] in ('Visible', 'Enabled') else None,
                            origem=_origem_valor(seq, k, nomes_pub) if ACOES_CTRL[t] in ('Visible', 'Enabled') else None,
                            condicoes=conds.get(i.address, []), via=via))
        elif prof > 0 and 0x490028 <= t < 0x4CB358 and t not in ms:
            sub = acoes_metodo(b, r, nome, nomes_pub, campos_form, campos_sd, prof - 1, _visto, ini=t,
                               via='%s via sub_%X' % (nome, t))
            for e in sub:
                e = dict(e)
                e['condicoes_chamada'] = conds.get(i.address, [])
                out.append(e)
    return out
