#!/usr/bin/env python3
"""Tabela das caixas de flag do ProgBase original, tirada do próprio ProgBase.exe (estático; nada é executado).

O que faz (cada passo lê o binário; nenhuma máscara está escrita à mão aqui):
  1. RTTI do TStreamDati (tabela de campos: offset u32, tipo u16, nome shortstring) -> offset de cada
     TAebVector FLAG_CONF1/FLAG_CONF2/FLAG_CONF2_LR/FLAG_CONF3/MASK_FUNCTION.
  2. Acha TAebVector.SetValues como o alvo de `push buf; mov r,[ebx+VETOR]; push r; call X`.
  3. Acha todos os despachos `jmp [reg*4+tabela]` cujos casos fazem `test byte [eax+C],imm` (getter) ou
     `mov r,[ebx+C]; or r,imm` (setter); C = cópia local do vetor no TStreamDati. O vetor de cada cópia
     sai do envio no fim do setter (SetValues). Caminhos `cmp byte [obj+0x80D],0` viram condições.
  4. Predicados de MASK_FUNCTION: funções `cmp byte [eax+VALIDO],0; je; test byte [eax+M+i],imm`.
  5. Chamadas a cada getter/setter/predicado: caso = edx imediato; controle = campo lido/escrito com
     GetChecked/SetChecked (vtable +0x104/+0x108) ou recebendo o resultado; nome do campo pela RTTI da
     classe dona da rotina; legenda pelo DFM + i18n.
Uso: flags_do_original.py [--exe ProgBase.exe] [--out JSON] [--check] [--i18n i18n_all.json]
Precisa de pefile e capstone. Determinístico (mesmo .exe -> mesmo JSON, byte a byte)."""
import argparse
import glob
import hashlib
import json
import os
import re
import struct
import sys

try:  # só a extração do .exe precisa deles; ler o JSON versionado não
    import capstone
    import pefile
except ImportError:  # pragma: no cover
    capstone = pefile = None

AQUI = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(AQUI, '..', '..'))
sys.path.insert(0, AQUI)
import orig_config_emulator as emu  # noqa: E402  (parse_dfm)

JSON_PADRAO = os.path.join(ROOT, 'docs', 'evidence', 'flags-do-original.json')
DFM_DIRS = [os.path.join(ROOT, 'docs', 'evidence', 'dfm-original'), '/home/user/protocolo-landi/dfm']
I18N_CANDIDATOS = ['/home/user/protocolo-landi/dfm/i18n_all.json',
                   os.path.join(ROOT, 'docs', 'evidence', 'dfm-original', 'i18n_TFORMCONFIG.json')]
EXE_CANDIDATOS = [os.environ.get('PROGBASE_EXE', ''),
                  os.path.join(os.environ.get('OMEGAS_CACHE', os.path.expanduser('~/.cache/omegas')), 'ProgBase.exe'),
                  '/tmp/claude-0/-home-user/57e20309-4640-5e31-b02d-4b2faf9cf2b3/scratchpad/progbase/pb.exe']
VETORES = ['FLAG_CONF1', 'FLAG_CONF2', 'FLAG_CONF2_LR', 'FLAG_CONF3', 'MASK_FUNCTION']
SERIAL = {'FLAG_CONF1': 3, 'FLAG_CONF2': 192, 'FLAG_CONF2_LR': 193, 'FLAG_CONF3': 250, 'MASK_FUNCTION': 194}
DISP = re.compile(r'\[(\w+) \+ (0x[0-9a-f]+)\]')


def achar_exe(pedido):
    for c in ([pedido] if pedido else []) + EXE_CANDIDATOS:
        if c and os.path.isfile(c):
            return c
    return None


def h(x, w=0):
    return '0x%0*X' % (w, x)


# ------------------------------------------------------------------ binário
class Binario:
    def __init__(self, caminho):
        self.pe = pefile.PE(caminho, fast_load=True)
        with open(caminho, 'rb') as fh:
            self.raw = fh.read()
        self.sha256 = hashlib.sha256(self.raw).hexdigest()
        self.base = self.pe.OPTIONAL_HEADER.ImageBase
        t = self.pe.sections[0]
        self.cb = self.base + t.VirtualAddress
        self.code = self.raw[t.PointerToRawData:t.PointerToRawData + t.SizeOfRawData]
        self.ce = self.cb + len(self.code)
        self.md = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)
        self._dis = {}

    def va2off(self, v):
        return self.pe.get_offset_from_rva(v - self.base)

    def u32(self, v):
        o = self.va2off(v)
        return struct.unpack('<I', self.raw[o:o + 4])[0]

    def u16(self, v):
        o = self.va2off(v)
        return struct.unpack('<H', self.raw[o:o + 2])[0]

    def sstr(self, v):
        o = self.va2off(v)
        return self.raw[o + 1:o + 1 + self.raw[o]].decode('latin-1')

    def ins(self, a):
        """Uma instrução em a (cache)."""
        if a is None or not (self.cb <= a < self.ce - 16):
            return None
        if a not in self._dis:
            o = a - self.cb
            r = next(self.md.disasm(self.code[o:o + 16], a), None)
            self._dis[a] = r
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


def disp(i, reg=None):
    m = DISP.search(i.op_str)
    if not m or (reg and m.group(1) != reg):
        return None
    return int(m.group(2), 16)


def imm_final(i):
    m = re.search(r', (0x[0-9a-f]+|\d+)$', i.op_str)
    return int(m.group(1), 0) if m else None


def alvo(i):
    try:
        return int(i.op_str, 16)
    except ValueError:
        return None


# ------------------------------------------------------------------ RTTI
class Rtti:
    """VMT de C++Builder/Delphi 2009+: self em -88, campos -68, métodos -64, nome -56."""

    def __init__(self, b):
        self.b = b
        self.vmts = {}
        for sec in b.pe.sections:
            d = sec.get_data()
            va0 = b.base + sec.VirtualAddress
            for i in range(0, len(d) - 4, 4):
                if struct.unpack_from('<I', d, i)[0] == va0 + i + 88:
                    vmt = va0 + i + 88
                    try:
                        nome = b.sstr(b.u32(vmt - 56))
                    except Exception:
                        continue
                    if re.fullmatch(r'T[\w.]+', nome) and nome not in self.vmts:
                        self.vmts[nome] = vmt
        self.metodos = {}   # endereço -> (classe, método)
        self.faixa = {}     # classe -> (min, max) dos métodos publicados
        for nome in sorted(self.vmts):
            ms = self.methods(nome)
            for a, m in ms.items():
                self.metodos.setdefault(a, (nome, m))
            if ms:
                self.faixa[nome] = (min(ms), max(ms))

    def fields(self, classe):
        b = self.b
        ft = b.u32(self.vmts[classe] - 68)
        out = {}
        if not ft:
            return out
        n, p = b.u16(ft), ft + 6
        for _ in range(n):
            nm = b.sstr(p + 6)
            out[b.u32(p)] = nm
            p += 7 + len(nm)
        return out

    def methods(self, classe):
        b = self.b
        mt = b.u32(self.vmts[classe] - 64)
        out = {}
        if not mt:
            return out
        n, p = b.u16(mt), mt + 2
        for _ in range(n):
            sz = b.u16(p)
            out[b.u32(p + 2)] = b.sstr(p + 6)
            p += sz
        return out


# ------------------------------------------------------------------ varredura
def chamadas(b):
    """{alvo: [endereços de call rel32]} para alvos no código."""
    out = {}
    c = b.code
    for m in re.finditer(rb'\xe8', c):
        o = m.start()
        if o + 5 > len(c):
            continue
        t = (b.cb + o + 5 + struct.unpack('<i', c[o + 1:o + 5])[0]) & 0xFFFFFFFF
        if b.cb <= t < b.ce:
            out.setdefault(t, []).append(b.cb + o)
    return out


def janela_ate(b, a, voltar=0x60):
    """Instruções que terminam exatamente em a (a incluída), alinhando para trás."""
    for st in range(voltar, 0, -1):
        lst = b.linear(a - st, 64)
        idx = [k for k, i in enumerate(lst) if i.address == a]
        if idx:
            return lst[:idx[0] + 1]
    return [b.ins(a)]


def achar_setvalues(b, calls, campos_vetor):
    cont = {}
    for t, srcs in calls.items():
        for a in srcs:
            w = janela_ate(b, a, 0x20)
            if len(w) >= 4 and w[-2].mnemonic == 'push' and w[-3].mnemonic == 'mov' and w[-4].mnemonic == 'push':
                d = disp(w[-3], 'ebx') or disp(w[-3])
                if d in campos_vetor and w[-3].op_str.split(',')[0].strip() == w[-2].op_str:
                    cont[t] = cont.get(t, 0) + 1
    return max(sorted(cont), key=lambda t: cont[t]) if cont else None


def inicio_funcao(b, a, inicios):
    """Maior início conhecido (alvo de call ou método publicado) <= a."""
    lo, hi = 0, len(inicios)
    while lo < hi:
        m = (lo + hi) // 2
        if inicios[m] <= a:
            lo = m + 1
        else:
            hi = m
    return inicios[lo - 1] if lo else None


def despachos(b):
    """[(endereço do jmp, tabela, n casos, função)] de `cmp r,N ... ja ... jmp [r*4+T]`."""
    out = []
    for m in re.finditer(rb'\xff\x24[\x85\x8d\x95\x9d\xb5\xbd]', b.code):
        a = b.cb + m.start()
        j = b.ins(a)
        if j is None or j.mnemonic != 'jmp':
            continue
        mm = re.match(r'dword ptr \[(\w+)\*4 \+ (0x[0-9a-f]+)\]', j.op_str)
        if not mm:
            continue
        reg, tab = mm.group(1), int(mm.group(2), 16)
        n = None
        for i in reversed(janela_ate(b, a, 0x40)[:-1]):
            if i.mnemonic == 'cmp' and i.op_str.startswith(reg + ','):
                n = imm_final(i)
                break
        if n is None or n > 64:
            continue
        out.append((a, tab, n + 1))
    return out


def walk(b, a, fim, limite=40):
    """Instruções de um caso até ret/jmp (inclui o jmp)."""
    out = []
    for _ in range(limite):
        i = b.ins(a)
        if i is None:
            break
        out.append(i)
        if i.mnemonic in ('ret', 'jmp') or a >= fim:
            break
        a += i.size
    return out


def bit_de(base_cache, off, imm):
    rel = off - base_cache
    return rel // 4, imm << (8 * (rel % 4))


def caso_getter(b, a):
    """[(condição, (off, imm) | None)] seguindo `cmp byte [eax+0x80D],0; je`."""
    def seg(x, cond):
        for _ in range(12):
            i = b.ins(x)
            if i is None:
                return [(cond, None)]
            if i.mnemonic == 'cmp' and i.op_str.startswith('byte ptr [eax + ') and i.op_str.endswith(', 0'):
                flag = disp(i)
                j = b.ins(x + i.size)
                if j.mnemonic == 'je':
                    return (seg(x + i.size + j.size, 'obj+%s!=0' % h(flag, 3)) +
                            seg(alvo(j), 'obj+%s==0' % h(flag, 3)))
            if i.mnemonic == 'test' and i.op_str.startswith('byte ptr [eax + '):
                return [(cond, (disp(i), imm_final(i)))]
            if i.mnemonic == 'xor' and i.op_str == 'eax, eax':
                return [(cond, None)]
            if i.mnemonic == 'ret':
                return [(cond, None)]
            x += i.size
        return [(cond, None)]
    return seg(a, 'sempre')


def caso_setter(b, a, saida_sem_envio):
    """[(condição, (off, imm_or), (off, imm_and), destino)] seguindo 0x80D e o teste do valor (cl ou [esp+4])."""
    def seg(x, cond):
        for _ in range(14):
            i = b.ins(x)
            if i is None:
                return [(cond, None, None, None)]
            if i.mnemonic == 'cmp' and i.op_str.startswith('byte ptr [ebx + ') and i.op_str.endswith(', 0'):
                flag = disp(i)
                j = b.ins(x + i.size)
                if j.mnemonic == 'je':
                    return (seg(x + i.size + j.size, 'obj+%s!=0' % h(flag, 3)) +
                            seg(alvo(j), 'obj+%s==0' % h(flag, 3)))
            if (i.mnemonic == 'test' and i.op_str == 'cl, cl') or \
               (i.mnemonic == 'cmp' and i.op_str.startswith('byte ptr [esp + ') and i.op_str.endswith(', 0')):
                j = b.ins(x + i.size)
                liga = b.linear(x + i.size + j.size, 4)
                desl = b.linear(alvo(j), 4)
                po = next(((disp(k), imm_final(k)) for n, k in enumerate(liga[1:], 1)
                           if k.mnemonic == 'or' and liga[n - 1].mnemonic == 'mov'), None)
                if po:
                    po = (disp(liga[[k.mnemonic for k in liga].index('or') - 1]), po[1])
                pa = None
                for n, k in enumerate(desl[1:], 1):
                    if k.mnemonic == 'and' and desl[n - 1].mnemonic == 'mov':
                        pa = (disp(desl[n - 1]), (~imm_final(k)) & 0xFFFFFFFF)
                jm = next((k for k in b.linear(x + i.size + j.size, 6) if k.mnemonic == 'jmp'), None)
                return [(cond, po, pa, alvo(jm) if jm else None)]
            if i.mnemonic == 'jmp':
                t = alvo(i)
                if t == saida_sem_envio:
                    return [(cond, None, None, t)]
                x = t
                continue
            x += i.size
        return [(cond, None, None, None)]
    return seg(a, 'sempre')


def envio(b, cauda, setvalues, campo_nome, limite=40):
    """Caminhos da cauda comum do setter: [(condições, vetor|None)]."""
    res = []

    def seg(x, conds, n):
        while n > 0:
            n -= 1
            i = b.ins(x)
            if i is None:
                return
            if i.mnemonic == 'cmp' and i.op_str.startswith('byte ptr [ebx + ') and i.op_str.endswith(', 0'):
                flag = h(disp(i), 3)
                j = b.ins(x + i.size)
                if j.mnemonic in ('je', 'jne'):
                    zero = 'obj+%s==0' % flag
                    nz = 'obj+%s!=0' % flag
                    salta, cai = (zero, nz) if j.mnemonic == 'je' else (nz, zero)
                    seg(alvo(j), conds + [salta], n)
                    x, conds = x + i.size + j.size, conds + [cai]
                    continue
            if i.mnemonic == 'call':
                if alvo(i) == setvalues:
                    vet = None
                    for k in reversed(janela_ate(b, i.address, 0x14)[:-1]):
                        if k.mnemonic == 'mov' and disp(k) in campo_nome:
                            vet = campo_nome[disp(k)]
                            break
                    res.append((conds, vet))
                else:
                    res.append((conds, None))
                return
            if i.mnemonic == 'ret':
                res.append((conds, None))
                return
            if i.mnemonic == 'jmp':
                x = alvo(i)
                continue
            x += i.size
    seg(cauda, [], limite)
    return sorted(res, key=lambda r: (r[0], r[1] or ''))


# ------------------------------------------------------------------ DFM / i18n
def carregar_dfms(dirs):
    out = {}
    for d in dirs:
        for p in sorted(glob.glob(os.path.join(d, 'T*.txt'))):
            cls = os.path.basename(p)[:-4].upper()
            if cls in out:
                continue
            with open(p, encoding='utf-8', errors='replace') as fh:
                raiz = emu.parse_dfm(fh.read())
            objs = {}

            def anda(o):
                objs[o.nome] = o
                for f in o.filhos:
                    anda(f)
            if raiz:
                anda(raiz)
            out[cls] = (os.path.relpath(p, ROOT) if p.startswith(ROOT) else p, objs)
    return out


def legenda(i18n, nome, idioma):
    e = i18n.get(nome.upper()) or {}
    return e.get(idioma)


# ------------------------------------------------------------------ chamadas
def le_edx(w):
    """Valor de edx (caso) antes do call no fim de w."""
    for i in reversed(w[:-1]):
        if i.mnemonic == 'call':
            return None
        ops = i.op_str.split(', ')
        if ops[0] in ('edx', 'dl', 'dx'):
            if i.mnemonic == 'mov':
                try:
                    return int(ops[1], 0)
                except ValueError:
                    return None
            if i.mnemonic == 'xor' and ops[1] == ops[0]:
                return 0
            return None
    return None


def propriedades(b, nomes=('Checked', 'Enabled', 'Visible')):
    """PropInfo da RTTI (PropType, GetProc, SetProc, ..., Name em +26): {('get'|'set', alvo): nome}.
    alvo = endereço estático ou ('vt', slot) para 0xFE0000xx (método virtual)."""
    res = {}
    for nm in nomes:
        pat = bytes([len(nm)]) + nm.encode()
        for m in re.finditer(re.escape(pat), b.raw):
            o = m.start()
            if o < 22:
                continue
            g, st = struct.unpack('<II', b.raw[o - 22:o - 14])
            for tipo, v in (('get', g), ('set', st)):
                if v >> 24 == 0xFE:
                    k = (tipo, ('vt', v & 0xFFFF))
                elif b.cb <= v < b.ce:
                    k = (tipo, v)
                else:
                    continue
                res.setdefault(k, set()).add(nm)
    return {k: sorted(v)[0] for k, v in res.items() if len(v) == 1}


def alvo_call(i):
    """Alvo de um call: endereço, ('vt', slot) para call [reg+slot], ou None."""
    t = alvo(i)
    if t is not None:
        return t
    m = re.fullmatch(r'dword ptr \[(\w+) \+ (0x[0-9a-f]+)\]', i.op_str)
    return ('vt', int(m.group(2), 16)) if m else None


def le_valor_setter(w, props):
    """Origem de ecx/cl antes do call de um setter: ('controle', campo, prop, invertido) | ('const', v, inv) | None."""
    inv = False
    reg = ('ecx', 'cl')
    for k in range(len(w) - 2, -1, -1):
        i = w[k]
        ops = i.op_str.split(', ')
        if i.mnemonic == 'call':
            prop = props.get(('get', alvo_call(i)))
            if reg == ('eax', 'al') and prop:
                for j in range(k - 1, max(k - 5, -1), -1):
                    if w[j].mnemonic == 'mov' and w[j].op_str.startswith('eax, dword ptr ['):
                        return ('controle', disp(w[j]), prop, inv)
            return None
        if ops[0] not in reg:
            continue
        if i.mnemonic == 'mov':
            if ops[1] in ('eax', 'al'):
                reg = ('eax', 'al')
                continue
            try:
                return ('const', int(ops[1], 0), inv)
            except ValueError:
                return None
        if i.mnemonic == 'xor' and ops[1] == ops[0]:
            return ('const', 0, inv)
        if i.mnemonic in ('neg', 'sbb'):
            continue
        if (i.mnemonic == 'cmp' and ops[1] == '1') or (i.mnemonic == 'xor' and ops[1] == '1'):
            inv = not inv
            continue
        return None
    return None


def le_controle_depois(b, a, props):
    """Depois de `call getter` em a: ('prop', campo, nome) se o resultado vai direto a uma propriedade de um
    controle; ('condicional',) se é testado; None se não reconhecido."""
    lst = b.linear(a, 10)[1:]
    if lst and lst[0].mnemonic == 'pop' and lst[0].op_str == 'ecx':  # cdecl: limpa o argumento
        lst = lst[1:]
    if not lst:
        return None
    if lst[0].mnemonic in ('test', 'cmp') and lst[0].op_str.startswith('al'):
        return ('condicional',)
    passou, campo, inv = False, None, False
    for i in lst:
        if i.mnemonic == 'mov' and i.op_str in ('edx, eax', 'dl, al'):
            passou = True
        elif i.mnemonic == 'mov' and i.op_str.startswith('eax, dword ptr [') and passou:
            campo = disp(i)
        elif passou and ((i.mnemonic == 'cmp' and i.op_str == 'edx, 1') or
                         (i.mnemonic == 'xor' and i.op_str in ('dl, 1', 'edx, 1'))):
            inv = not inv
        elif i.mnemonic == 'call':
            if passou and campo is not None:
                return ('prop', campo, props.get(('set', alvo_call(i)), 'call ' + i.op_str), inv)
            break
        elif i.mnemonic in ('ret', 'jmp') or i.mnemonic.startswith('j'):
            break
    return None


# ------------------------------------------------------------------ extração
def extrair(exe, i18n_path=None, dfm_dirs=None):
    b = Binario(exe)
    rt = Rtti(b)
    sd = rt.fields('TStreamDati')
    campo_nome = {off: nm for off, nm in sd.items() if nm in VETORES}
    calls = chamadas(b)
    setvalues = achar_setvalues(b, calls, set(campo_nome))
    inicios = sorted(set(calls) | set(rt.metodos))

    # despachos -> getters / setters de flag
    getters, setters = {}, {}
    for jmp_a, tab, n in despachos(b):
        try:
            alvos = [b.u32(tab + 4 * k) for k in range(n)]
        except Exception:
            continue
        if not all(abs(t - jmp_a) < 0x2000 for t in alvos):
            continue
        f = inicio_funcao(b, jmp_a, inicios)
        g = [caso_getter(b, t) for t in alvos]
        if all(any(r for _, r in c) for c in g[:max(1, n - 1)]):
            offs = {r[0] for c in g for _, r in c if r}
            if len(offs) <= 8 and max(offs) - min(offs) < 8:
                getters[f] = (g, min(offs))
                continue
        # setter: a cauda é o destino do `ja`
        cauda = None
        for i in reversed(janela_ate(b, jmp_a, 0x40)[:-1]):
            if i.mnemonic == 'ja':
                cauda = alvo(i)
                break
        if cauda is None:
            continue
        envs = envio(b, cauda, setvalues, campo_nome)
        if not any(v for _, v in envs):
            continue
        # saída sem envio = epílogo alcançado quando SetValues falha (je após test al,al)
        sem = None
        for i in b.linear(cauda, 40):
            if i.mnemonic == 'je' and b.ins(i.address - 2).mnemonic == 'test':
                sem = alvo(i)
                break
        s = [caso_setter(b, t, sem) for t in alvos]
        offs = {r[1][0] for c in s for r in c if r[1]}
        if offs and max(offs) - min(offs) < 8:
            setters[f] = (s, min(offs), envs)

    # base da cópia local = mov eax,[ebx+C] do prólogo do setter (palavra 0)
    def base_cache(f, mn):
        for i in b.linear(f, 16):
            if i.mnemonic == 'mov' and disp(i, 'ebx') is not None and disp(i, 'ebx') <= mn and \
                    i.op_str.startswith(('eax', 'edx', 'ecx')):
                return disp(i, 'ebx')
        return mn - (mn % 4)

    grupos = []
    for fs, (s, mn, envs) in sorted(setters.items()):
        base = base_cache(fs, mn)
        fg = next((fg for fg, (g, gm) in sorted(getters.items()) if base <= gm < base + 8), None)
        vets = sorted({v for _, v in envs if v})
        grupos.append({'cache': base, 'getter': fg, 'setter': fs, 'envio': envs, 'vetores': vets,
                       'g': getters[fg][0] if fg else None, 's': s})

    # predicados MASK_FUNCTION (buffer de bytes)
    mask_off = next(off for off, nm in campo_nome.items() if nm == 'MASK_FUNCTION')
    mask_buf, mask_valido = None, None
    # buffer = add edx, BUF; push edx; ... push [ebx+MASK]; call GetValues (mesmo campo)
    cand = {}
    for m in re.finditer(re.escape(struct.pack('<I', mask_off)), b.code):
        a0 = b.cb + m.start()
        for back in (2, 3):
            i = b.ins(a0 - back)
            if i and i.size == back + 4 and disp(i) == mask_off:
                w = janela_ate(b, i.address, 0x30)
                # `add r, BUF; push r; mov x,[ebp+8]; mov y,[x+MASK]` (buffer de GetValues do vetor)
                if len(w) >= 4 and w[-4].mnemonic == 'add' and w[-3].mnemonic == 'push' and \
                        w[-3].op_str == w[-4].op_str.split(',')[0] and (imm_final(w[-4]) or 0) >= 0x100:
                    cand[imm_final(w[-4])] = cand.get(imm_final(w[-4]), 0) + 1
    if cand:
        mask_buf = max(sorted(cand), key=lambda x: cand[x])
    predicados = []
    if mask_buf:
        for fa in sorted(calls):
            lst = b.linear(fa, 6)
            if len(lst) < 6 or [i.mnemonic for i in lst[:6]] != ['push', 'mov', 'mov', 'cmp', 'je', 'test']:
                continue
            t = lst[5]
            d = disp(t, 'eax')
            if d is None or not (mask_buf <= d < mask_buf + 30) or not t.op_str.startswith('byte'):
                continue
            mask_valido = disp(lst[3])
            predicados.append((fa, d - mask_buf, imm_final(t)))

    # ----- chamadas e controles
    dfms = carregar_dfms(dfm_dirs or DFM_DIRS)
    i18n_path = i18n_path or next((p for p in I18N_CANDIDATOS if os.path.isfile(p)), None)
    with open(i18n_path, encoding='utf-8') as fh:
        i18n = json.load(fh)
    campos_cache = {}
    props = propriedades(b)

    def dono(a):
        f = inicio_funcao(b, a, inicios)
        if f in rt.metodos:
            cls, m = rt.metodos[f]
            return f, cls, '%s.%s' % (cls, m), 'PROVADO'
        cands = sorted((hi - lo, c) for c, (lo, hi) in rt.faixa.items() if lo <= a <= hi)
        cls = cands[0][1] if cands else None
        return f, cls, 'sub_%X' % f if f else None, 'DERIVADO'

    def controle(cls, campo):
        if cls is None or campo is None:
            return None
        if cls not in campos_cache:
            campos_cache[cls] = rt.fields(cls)
        nome = campos_cache[cls].get(campo)
        if not nome:
            return None
        dfm_rel, objs = dfms.get(cls.upper(), (None, {}))
        o = objs.get(nome)
        return {'nome': nome, 'form': cls, 'campo_offset': h(campo), 'classe': o.classe if o else None,
                'caption_ita': (o.props.get('Caption') if o else None),
                'caption_eng': legenda(i18n, nome, 'eng'), 'caption_por': legenda(i18n, nome, 'por'),
                'dfm': dfm_rel}

    def usos(func, setter):
        out = {}
        for a in calls.get(func, []):
            w = janela_ate(b, a)
            caso = le_edx(w)
            f, cls, rot, marca = dono(a)
            reg = {'chamada': h(a), 'rotina': rot, 'rotina_marca': marca, 'uso': 'escreve' if setter else 'le'}
            ctl = None
            if setter:
                v = le_valor_setter(w, props)
                if v and v[0] == 'controle':
                    ctl = controle(cls, v[1])
                    reg.update({'propriedade': v[2], 'invertido': v[3]})
                    if not ctl:
                        reg['campo'] = h(v[1])
                elif v and v[0] == 'const':
                    reg['valor'] = 'constante %d' % ((1 if v[1] else 0) ^ (1 if v[2] else 0))
                else:
                    reg['valor'] = 'DESCONHECIDO'
            else:
                v = le_controle_depois(b, a, props)
                if v and v[0] == 'prop':
                    ctl = controle(cls, v[1])
                    reg.update({'propriedade': v[2], 'invertido': v[3]})
                    if not ctl:
                        reg['campo'] = h(v[1])
                else:
                    reg['valor'] = 'condicional' if v else 'DESCONHECIDO'
            out.setdefault(caso, []).append((ctl, reg))
        return out

    def agrupar(pares):
        ctls, outros = {}, []
        for ctl, reg in sorted(pares, key=lambda p: int(p[1]['chamada'], 16)):
            if ctl:
                e = ctls.setdefault(ctl['nome'], dict(ctl, acessos=[]))
                e['acessos'].append(reg)
            else:
                outros.append(reg)
        lista = sorted(ctls.values(), key=lambda c: (not any(x.get('propriedade') == 'Checked' for x in c['acessos']),
                                                     c['nome']))
        for c in lista:
            if c.get('classe') and not re.search(r'CheckBox|MenuItem|RadioButton', c['classe']) and \
                    any(x.get('propriedade') == 'Checked' for x in c['acessos']):
                # o slot virtual do GetChecked/SetChecked da RTTI só vale para caixas; noutras classes é outra
                # propriedade no mesmo slot (INFERIDO: ItemIndex de combo)
                for x in c['acessos']:
                    if x.get('propriedade') == 'Checked':
                        x['propriedade'] = 'slot vt de Checked em %s (INFERIDO: ItemIndex)' % c['classe']
                c['papel'] = 'seletor (%s)' % c['classe']
                continue
            c['papel'] = 'caixa (Checked)' if any(x.get('propriedade') == 'Checked' for x in c['acessos']) else \
                'dependente (' + '/'.join(sorted({x.get('propriedade', '?') for x in c['acessos']})) + ')'
        return lista, outros

    flags = []
    for gr in grupos:
        ug = usos(gr['getter'], False) if gr['getter'] else {}
        us = usos(gr['setter'], True)
        n = len(gr['s'])
        vet_padrao = [v for c, v in gr['envio'] if v and not any('!=0' in x and '80D' in x for x in c)]
        vet_alt = [v for c, v in gr['envio'] if v and any('!=0' in x and '80D' in x for x in c)]
        nome_vet = vet_padrao[0] if vet_padrao else gr['vetores'][0]
        tail = gr_tail(b, gr['setter'])
        for caso in range(n):
            gs = gr['g'][caso] if gr['g'] and caso < len(gr['g']) else []
            ss = gr['s'][caso]
            conds = sorted({c for c, _ in gs} | {c for c, *_ in ss})
            for cond in conds:
                gbit = next((r for c, r in gs if c == cond), None)
                if gbit is None and cond != 'sempre' and not any(c == cond for c, _ in gs):
                    gbit = next((r for c, r in gs if c == 'sempre'), None)
                srow = next((r for r in ss if r[0] == cond), None) or next((r for r in ss if r[0] == 'sempre'), None)
                sor = srow[1] if srow else None
                sand = srow[2] if srow else None
                if gbit is None and sor is None:
                    continue
                gb = bit_de(gr['cache'], *gbit) if gbit else None
                sb = bit_de(gr['cache'], *sor) if sor else None
                ab = bit_de(gr['cache'], *sand) if sand else None
                ref = sb or gb
                ctrls, outros = agrupar(ug.get(caso, []) + us.get(caso, []))
                flags.append({
                    'vetor': nome_vet, 'serial_code': SERIAL[nome_vet],
                    'vetor_alternativo': (vet_alt[0] if vet_alt else None),
                    'palavra': ref[0], 'mascara': h(ref[1], 4), 'caso': caso, 'condicao': cond,
                    'getter': h(gr['getter']) if gr['getter'] else None, 'setter': h(gr['setter']),
                    'getter_bit': {'palavra': gb[0], 'mascara': h(gb[1], 4)} if gb else None,
                    'setter_bit': {'palavra': sb[0], 'mascara': h(sb[1], 4)} if sb else None,
                    'coerente': bool(gb and sb and gb == sb and ab == sb),
                    'efeito_extra_setter': (h(srow[3]) if srow and srow[3] and srow[3] != tail else None),
                    'controles': ctrls, 'outros_usos': outros,
                })
    # MASK_FUNCTION
    for fa, idx, imm in predicados:
        pares = []
        for a in calls.get(fa, []):
            f, cls, rot, marca = dono(a)
            v = le_controle_depois(b, a, props)
            reg = {'chamada': h(a), 'rotina': rot, 'rotina_marca': marca, 'uso': 'le'}
            ctl = None
            if v and v[0] == 'prop':
                ctl = controle(cls, v[1])
                reg.update({'propriedade': v[2], 'invertido': v[3]})
                if not ctl:
                    reg['campo'] = h(v[1])
            else:
                reg['valor'] = 'condicional' if v else 'DESCONHECIDO'
            pares.append((ctl, reg))
        ctrls, outros = agrupar(pares)
        flags.append({
            'vetor': 'MASK_FUNCTION', 'serial_code': SERIAL['MASK_FUNCTION'], 'vetor_alternativo': None,
            'palavra': idx, 'mascara': h(imm, 4), 'caso': None,
            'condicao': 'obj+%s!=0' % h(mask_valido, 3),
            'getter': h(fa), 'setter': None, 'getter_bit': {'palavra': idx, 'mascara': h(imm, 4)},
            'setter_bit': None, 'coerente': True, 'efeito_extra_setter': None,
            'nota': 'palavra = índice do BYTE no vetor (DataMask 255); só leitura (predicado de capacidade)',
            'controles': ctrls, 'outros_usos': outros,
        })
    flags.sort(key=lambda e: (e['vetor'], e['palavra'], int(e['mascara'], 16),
                              e['caso'] if e['caso'] is not None else -1, e['condicao']))
    return {
        'fonte': {'exe_sha256': b.sha256, 'i18n': os.path.basename(i18n_path),
                  'gerador': 'tools/emulacao/flags_do_original.py'},
        'vetores_rtti_TStreamDati': {nm: h(off) for off, nm in sorted(campo_nome.items())},
        'TAebVector_SetValues': h(setvalues) if setvalues else None,
        'rotinas': [{'cache': h(g['cache']), 'getter': h(g['getter']) if g['getter'] else None,
                     'setter': h(g['setter']), 'casos': len(g['s']),
                     'envio': [{'condicoes': c, 'vetor': v} for c, v in g['envio']]} for g in grupos],
        'mask_function': {'buffer': h(mask_buf) if mask_buf else None,
                          'valido': h(mask_valido) if mask_valido else None,
                          'predicados': len(predicados)},
        'convencao': {'palavra': 'índice do u16 no vetor (cópia local = 1 dword por palavra)',
                      'condicao': "'sempre' ou o caminho do código por [obj+0x80D] (0x80D!=0 => envio a FLAG_CONF2_LR)",
                      'coerente': 'getter testa, setter liga (or) e desliga (and ~) o mesmo bit'},
        'flags': flags,
    }


def gr_tail(b, setter):
    for i in b.linear(setter, 20):
        if i.mnemonic == 'ja':
            return alvo(i)
    return None


def dump(tab):
    return json.dumps(tab, ensure_ascii=False, indent=1, sort_keys=False) + '\n'


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--exe')
    ap.add_argument('--out', default=JSON_PADRAO)
    ap.add_argument('--i18n')
    ap.add_argument('--check', action='store_true', help='compara com o JSON versionado; não grava')
    a = ap.parse_args(argv)
    exe = achar_exe(a.exe)
    if not exe:
        print('ProgBase.exe não encontrado (use --exe, PROGBASE_EXE ou tools/obter_progbase.py)', file=sys.stderr)
        return 2
    txt = dump(extrair(exe, a.i18n))
    if a.check:
        with open(a.out, encoding='utf-8') as fh:
            ok = fh.read() == txt
        print('OK: igual ao versionado' if ok else 'DIFERENTE do versionado: %s' % a.out)
        return 0 if ok else 1
    with open(a.out, 'w', encoding='utf-8') as fh:
        fh.write(txt)
    print(a.out)
    return 0


if __name__ == '__main__':
    sys.exit(main())
