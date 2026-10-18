#!/usr/bin/env python3
"""Executa NA CPU EMULADA (Unicorn, x86-32) o código REAL do ProgBase.exe das abas do TFormConfig (D3b).

Estende `tools/emulacao/cpu_setter_original.py` (mesma máquina: imagem do .exe mapeada 1:1, KERNEL32 em stub).
Novo aqui:
  * TStreamDati (o objeto global [[0xAEBED8]]) montado com TODOS os componentes do DFM (TSTREAMDATI.txt):
    SerialCode, DataLength, DataMask, ArrayDimension etc. vêm do DFM (nada escrito à mão).
  * TFormConfig montado com um objeto falso por controle do DFM (VMT real da classe do controle).
  * A ECU falsa (EcuDono) responde às LEITURAS com o que a ECU do dono respondeu no log Portmon (OBS-FIO),
    guarda as escritas e responde ACK `53 00 53`.
  * O gerenciador de memória (borlndmm.dll) é substituído por um alocador simples (LoadLibrary/GetProcAddress em stub).
  * Interceptações (hooks) só na camada de janela do VCL (SetVisible etc.): gravam o efeito "na tela" em vez de
    chamar a USER32. A LÓGICA do ProgBase (handlers, getters/setters do TStreamDati, conversões x87,
    TAebVector/TAebNumber, protocolo) é executada de verdade.
Determinístico. Precisa de unicorn e pefile."""
import os
import struct
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(AQUI, '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'tools', 'emulacao'))
sys.path.insert(0, AQUI)
import cpu_setter_original as C  # noqa: E402
import abas_b_re as A  # noqa: E402

# memória maior que a do módulo base (320 vetores + ~900 objetos de controle)
C.HEAP_TAM = 0x00800000
C.PILHA_TAM = 0x00200000

VAR_SD = 0xAECFB4      # [[0xAEBED8]] -> variável global -> instância TStreamDati

# layout dos controles falsos (OBS-COD: lidos nos getters/setters do VCL do próprio .exe)
OFF_VISIBLE, OFF_ENABLED = 0x69, 0x6A
OFF_TAG = 0x0C            # TComponent.FTag (os handlers fazem `mov r,[sender+0xC]` para despachar por Tag)
OFF_CHECK_STATE = 0x29A   # TCheckBox: cbChecked = 1 (0x758DFC)
OFF_NUM_VALUE = 0x2E0     # TNumericEdit: double (0x96BB88 grava, 0x96BA2C lê)
OFF_NUM_CODE = 0x2EC      # código do dado (amarração 0x48A228)
OFF_NUM_DATAINDEX = 0x2F0

# argumentos (stdcall) das APIs que o código das abas pode tocar; sem tabela => erro explícito
STDCALL_EXTRA = {
    'GetDeviceCaps': 2, 'SendMessageA': 4, 'SendMessageW': 4, 'PostMessageA': 4, 'IsWindow': 1, 'GetFocus': 0,
    'GetDC': 1, 'ReleaseDC': 2, 'SelectObject': 2, 'GetTextExtentPoint32A': 4, 'GetTextExtentPoint32W': 4,
    'GetSystemMetrics': 1, 'InvalidateRect': 3, 'GetWindowLongA': 2, 'GetKeyState': 1, 'MessageBeep': 1,
    'GetCurrentThreadId': 0, 'TlsGetValue': 1, 'InterlockedIncrement': 1, 'InterlockedDecrement': 1,
    'GetCurrentProcessId': 0, 'GetTickCount': 0, 'MessageBoxA': 4, 'MessageBoxW': 4,
}


# funções de VCL/RTL "de janela" sem efeito sobre a lógica (cada uma conferida no disassembly) -- ver docs
NOOPS = [0x7C8840, 0x7AED4C]    # TTimer.SetEnabled; TScreen.GetFormCount (0 forms)


class EcuDono(C._Ecu):
    """ECU falsa: responde às leituras com o que a ECU do dono respondeu no log (OBS-FIO) e guarda as escritas.

    `tab`   = saída de abas_b_log.ler_log / de_json ({serial: {'vetor','escalar','indexado','linhas'}}).
    `forma` = {serial: (bytes_por_elemento, nº de elementos)} tirado do DFM do TStreamDati.
    Quadros: leitura 09/0A/29/2A (4 ou 5 bytes); escrita 1n/3n ((n&7)+3 bytes). Resposta: eco + 53 n dados ck."""

    def __init__(self, tab, forma, responde=True):
        super().__init__(responde)
        self.mem = {}
        for sc, e in tab.items():
            b = e.get('vetor')
            if b is None and 'escalar' in e:
                b = e['escalar']
            if b is None and e['indexado']:
                dl = forma.get(sc, (1, 1))[0]
                n = max(e['indexado']) + 1
                buf = bytearray(dl * n)
                for i, v in e['indexado'].items():
                    buf[i * dl:(i + 1) * dl] = v
                b = bytes(buf)
            if b is not None:
                self.mem[sc] = bytearray(b)
        self.forma = forma
        self.linhas = {sc: dict(e['linhas']) for sc, e in tab.items() if e['linhas']}
        self.lidos = []         # (opcode, serial, extra)
        self.escritas_ecu = []  # (opcode, serial, dados) já aplicadas na memória

    @staticmethod
    def _tam(acc):
        op = acc[0]
        if op in (0x09, 0x29, 0x01):
            return 4
        if op in (0x0A, 0x2A):
            return 5
        if op & 0xF8 in (0x10, 0x30) and (op & 7) < 7:
            return (op & 7) + 3
        return None

    @staticmethod
    def _resp(dados):
        return bytes([0x53, len(dados)]) + bytes(dados) + bytes([(0x53 + len(dados) + sum(dados)) & 0xFF])

    def escreve(self, dados):
        for b in dados:
            self.acc.append(b)
            if self.responde:
                self.rx.append(b)
            n = self._tam(self.acc)
            if n is None or len(self.acc) < n:
                continue
            q = bytes(self.acc[:n])
            del self.acc[:n]
            self.quadros.append(q)
            if not self.responde:
                continue
            op, sc = q[0], q[1] | (q[2] << 8)
            if op == 0x01:            # SetParameter do sistema: no log a ECU respondeu NACK (CA) a `01 04 54 59` (OBS-FIO)
                self.rx += bytes([0x53, 0x00, 0x53])
                continue
            dl, dim = self.forma.get(sc, (1, 1))
            if op in (0x09, 0x29, 0x0A, 0x2A):
                self.lidos.append((op, sc, q[3] if n == 5 else None))
                m = self.mem.get(sc, bytearray(dl * dim))
                if op == 0x09:
                    r = m[:dl]
                elif op == 0x29:
                    r = m
                elif op == 0x0A:
                    r = m[q[3] * dl:(q[3] + 1) * dl]
                else:
                    r = self.linhas.get(sc, {}).get(q[3], b'')
                self.rx += self._resp(r)
            elif (sum(q[:-1]) & 0xFF) == q[-1]:
                corpo = q[3:-1]
                m = self.mem.setdefault(sc, bytearray(dl * dim))
                if op & 0xF0 == 0x10:
                    if len(corpo) == dl + 1:
                        i = corpo[0]
                        m[i * dl:(i + 1) * dl] = corpo[1:]
                    elif len(corpo) == dl:
                        m[:dl] = corpo
                else:
                    m[:len(corpo)] = corpo[-len(m):] if len(corpo) > len(m) else corpo
                self.escritas_ecu.append((op, sc, bytes(corpo)))
                self.rx += bytes([0x53, 0x00, 0x53])


class MaquinaAbas(C._Maquina):
    """Máquina com TStreamDati completo + formulário falso + hooks de UI."""

    def __init__(self, exe, ctx, max_instr=6_000_000, ecu_responde=True):
        super().__init__(exe, None, ecu_responde, max_instr)
        self.ctx = ctx
        self.stdcall.update(STDCALL_EXTRA)
        self.hooks = {}          # endereço -> (função, nº de args empilhados limpos pelo callee)
        self.efeitos = []        # eventos de UI na ordem em que aconteceram
        self.vetores = {}        # nome -> endereço do objeto
        self.vetor_de = {}       # endereço -> nome
        self.controles = {}      # nome -> endereço
        self.controle_de = {}    # endereço -> nome
        self.redirs = {}
        self.ordem = []
        self.api_py = {}
        self.chamador_erro = 0
        self.pilha_erro = []
        self.uc.hook_add(self.uc_mod.UC_HOOK_CODE, self._hook_hooks)
        # a IAT tem 772 entradas (0x10 cada): o módulo base só mapeou 0x1000 bytes
        self.uc.mem_map(C.STUBS + 0x1000, 0x6000)
        self.codigo_stub = C.STUBS + 0x5000
        self._montar_mm()
        self.uc.mem_map(0, 0x10000)      # página nula legível: objetos ausentes leem 0 em vez de derrubar a emulação
        self.uc.mem_map(0xFFFFF000, 0x1000)   # [nil-4] de string Delphi vazia (comprimento 0)
        self.uc.hook_add(self.uc_mod.UC_HOOK_CODE, self._hook_stubs_altos, begin=C.STUBS + 0x1000, end=C.STUBS + 0x4FFF)

    # ------------------------------------------------------------------ hooks
    def hook(self, addr, fn, nargs=0):
        self.hooks[addr] = (fn, nargs)

    def _hook_hooks(self, uc, addr, size, _):
        if addr in self.redirs:
            uc.reg_write(self.X.UC_X86_REG_EIP, self.redirs[addr])
            return
        h = self.hooks.get(addr)
        if not h:
            return
        fn, nargs = h
        X = self.X
        esp = uc.reg_read(X.UC_X86_REG_ESP)
        ret = self.r32(esp)
        args = [self.r32(esp + 4 + 4 * k) for k in range(nargs)]
        regs = {n: uc.reg_read(getattr(X, 'UC_X86_REG_' + n.upper())) for n in ('eax', 'edx', 'ecx')}
        r = fn(self, regs, args)
        uc.reg_write(X.UC_X86_REG_EAX, (r or 0) & 0xFFFFFFFF)
        uc.reg_write(X.UC_X86_REG_ESP, esp + 4 + 4 * nargs)
        uc.reg_write(X.UC_X86_REG_EIP, ret)

    def _hook_codigo(self, uc, addr, size, _):
        n = len(self.entradas)
        super()._hook_codigo(uc, addr, size, _)
        if len(self.entradas) != n:
            self.ordem.append(addr)

    def _hook_stubs_altos(self, uc, addr, size, _):
        self._prev = None
        self._stub(self.stub_nome.get(addr, 'desconhecido_%x' % addr))

    def redireciona(self, addr, codigo):
        """Troca a função em addr por um trecho de código x86 curto escrito na área de stubs."""
        a = self.codigo_stub
        self.uc.mem_write(a, codigo)
        self.codigo_stub += (len(codigo) + 15) & ~15
        self.redirs[addr] = a

    # ------------------------------------------------------------------ APIs em Python
    def _stub(self, nome):
        if nome in self.api_py:
            X, uc = self.X, self.uc
            self.contagem_stubs[nome] = self.contagem_stubs.get(nome, 0) + 1
            ret = self.api_py[nome](self) or 0
            esp = uc.reg_read(X.UC_X86_REG_ESP)
            r = self.r32(esp)
            uc.reg_write(X.UC_X86_REG_EAX, ret & 0xFFFFFFFF)
            uc.reg_write(X.UC_X86_REG_ESP, esp + 4 + 4 * self.stdcall[nome])
            uc.reg_write(X.UC_X86_REG_EIP, r)
            return
        if nome != '<fim>' and nome not in self.stdcall:
            esp = self.uc.reg_read(self.X.UC_X86_REG_ESP)
            self.chamador_erro = self.r32(esp)
            self.pilha_erro = [self.r32(esp + 4 * k) for k in range(8)]
        super()._stub(nome)

    def _cstr(self, a):
        out = bytearray()
        while True:
            b = self.uc.mem_read(a, 1)[0]
            if not b:
                break
            out.append(b)
            a += 1
        return out.decode('latin-1')

    # ---- gerenciador de memória do C++Builder (borlndmm.dll): alocador simples em área própria
    def _montar_mm(self):
        self.mm_base, self.mm_tam = 0x10000000, 0x04000000
        self.uc.mem_map(self.mm_base, self.mm_tam)
        self.mm_topo = self.mm_base + 0x1000
        self.mm_tamanhos = {}
        self.stdcall.update({'LoadLibraryA': 1, 'GetModuleHandleA': 1, 'GetProcAddress': 2, 'FreeLibrary': 1})

        def getmem(mm, r, a):
            n = max(r['eax'], 1)
            p = mm.mm_topo
            mm.mm_topo = (mm.mm_topo + n + 8 + 15) & ~15
            if mm.mm_topo > mm.mm_base + mm.mm_tam:
                raise C.ErroEmulacao('mm esgotado')
            mm.mm_tamanhos[p] = n
            return p

        def allocmem(mm, r, a):
            p = getmem(mm, r, a)
            mm.uc.mem_write(p, b'\0' * max(r['eax'], 1))
            return p

        def realloc(mm, r, a):
            p, n = r['eax'], r['edx']
            q = getmem(mm, {'eax': n}, a)
            if p:
                k = min(mm.mm_tamanhos.get(p, 0), n)
                if k:
                    mm.uc.mem_write(q, bytes(mm.uc.mem_read(p, k)))
            return q

        def nada(mm, r, a):
            return 0
        self.mm_funcs = {}
        for nome, fn in (('@Borlndmm@SysGetMem$qqri', getmem), ('@Borlndmm@SysFreeMem$qqrpv', nada),
                         ('@Borlndmm@SysReallocMem$qqrpvi', realloc), ('@Borlndmm@SysAllocMem$qqri', allocmem),
                         ('@Borlndmm@SysRegisterExpectedMemoryLeak$qqrpi', nada),
                         ('@Borlndmm@SysUnregisterExpectedMemoryLeak$qqrpi', nada),
                         ('@Borlndmm@HeapAddRef$qqrv', nada), ('@Borlndmm@HeapRelease$qqrv', nada)):
            a_ = self.codigo_stub
            self.codigo_stub += 16
            self.uc.mem_write(a_, b'\xC3')
            self.hook(a_, fn, 0)
            self.mm_funcs[nome] = a_

        def load(mm):
            return 0x70000000

        def getproc(mm):
            esp = mm.uc.reg_read(mm.X.UC_X86_REG_ESP)
            return mm.mm_funcs.get(mm._cstr(mm.r32(esp + 8)), 0)
        def arg(mm, k):
            return mm.r32(mm.uc.reg_read(mm.X.UC_X86_REG_ESP) + 4 + 4 * k)

        def mb2wc(mm):          # MultiByteToWideChar(cp, flags, src, cb, dst, cch): só ASCII/Latin-1
            src, cb, dst, cch = arg(mm, 2), arg(mm, 3), arg(mm, 4), arg(mm, 5)
            cb = cb if cb < 0x80000000 else len(mm._cstr(src)) + 1
            if cch == 0:
                return cb
            n = min(cb, cch)
            mm.uc.mem_write(dst, b''.join(struct.pack('<H', b) for b in bytes(mm.uc.mem_read(src, n))))
            return n

        def wc2mb(mm):          # WideCharToMultiByte(cp, flags, src, cch, dst, cb, def, used)
            src, cch, dst, cb = arg(mm, 2), arg(mm, 3), arg(mm, 4), arg(mm, 5)
            if cch >= 0x80000000:
                cch, p = 0, src
                while mm.uc.mem_read(p, 2) != b'\0\0':
                    cch += 1
                    p += 2
                cch += 1
            if cb == 0:
                return cch
            w = bytes(mm.uc.mem_read(src, 2 * cch))
            n = min(cch, cb)
            mm.uc.mem_write(dst, bytes(w[2 * i] if w[2 * i + 1] == 0 else 63 for i in range(n)))
            return n
        self.stdcall.update({'MultiByteToWideChar': 6, 'WideCharToMultiByte': 8, 'GetACP': 0, 'GetCurrentThreadId': 0,
                             'GetLocaleInfoA': 4, 'IsDBCSLeadByte': 1, 'GetThreadLocale': 0, 'CharNextA': 1,
                             'CharUpperA': 1, 'CharLowerA': 1, 'lstrlenA': 1, 'GetCPInfo': 2,
                             'GetStringTypeW': 5, 'LCMapStringA': 6, 'CompareStringA': 6, 'GetSystemDefaultLCID': 0})
        self.stdcall.update({'MessageBoxA': 4, 'MessageBoxW': 4})
        self.caixas = []

        def msgbox(mm):             # MessageBoxA(hwnd, texto, titulo, tipo): guarda o texto e "clica" OK
            mm.caixas.append(mm._cstr(arg(mm, 1)))
            return 1
        self.api_py = {'MessageBoxA': msgbox, 'LoadLibraryA': load, 'GetModuleHandleA': load, 'GetProcAddress': getproc,
                       'FreeLibrary': lambda mm: 1, 'MultiByteToWideChar': mb2wc, 'WideCharToMultiByte': wc2mb,
                       'GetACP': lambda mm: 1252, 'GetThreadLocale': lambda mm: 0x416,
                       'GetSystemDefaultLCID': lambda mm: 0x416, 'GetCurrentThreadId': lambda mm: 1,
                       'IsDBCSLeadByte': lambda mm: 0}

    # ------------------------------------------------------------------ montagem
    def montar_sd(self, dfm_sd):
        """TStreamDati com todos os componentes do DFM."""
        c = self.ctx
        self.montar((0, 0), 2, 1, 0, 1)    # conexão/porta/protocolo/lock + SD base (obj80D=0, obj80E=1)
        sd = self.sd
        self.w32(VAR_SD, sd)
        self.w32(sd + 0x6C, self.conexao)
        vmts = c.rtti.vmts
        for nome, o in dfm_sd.items():       # componentes que não são vetores: objeto vazio com a VMT real
            off = c.inv_sd.get(nome)
            if off is not None and o.classe in ('TTimer', 'TAebRegistryData', 'TTranslatorExt', 'TAebStrings', 'TAebProtocol') \
                    and o.classe in vmts and nome != 'AebConnection':
                ob = self.alloc(0x400)
                self.w32(ob, vmts[o.classe])
                self.w32(sd + off, ob)
        self.w32(sd + c.inv_sd['AebConnection'], self.conexao)
        for nome, o in dfm_sd.items():
            off = c.inv_sd.get(nome)
            if off is None or o.classe not in ('TAebVector', 'TAebNumber', 'TAebMatrix'):
                continue
            serial = int(o.props.get('SerialCode', 0))
            dl = int(o.props.get('DataLength', 1))
            mask = int(o.props.get('DataMask', 255))
            dim = int(o.props.get('ArrayDimension', 1))
            if o.classe == 'TAebMatrix':
                dim = max(1, int(o.props.get('ColCount', 1)) * int(o.props.get('RowCount', 1)))
            v = self.alloc(0x200)
            self.w32(v, vmts[o.classe])
            self.w32(v + 0x44, dim)
            self.w32(v + 0x48, 1)
            self.w32(v + 0x4C, 0xFFFFFFFF)
            self.w32(v + 0x50, 0xFFFFFFFF)
            self.w32(v + 0x54, mask)
            self.w32(v + 0x58, dl)
            self.w32(v + 0x64, serial)
            self.w32(v + 0x6C, serial)
            self.w32(v + 0x70, dim)
            self.w32(v + 0x74, dl)
            self.w32(v + 0xC0, self.conexao)
            self.w32(v + 0x11C, self.alloc(0x20))
            self.w8(v + 0x124, 1)
            if o.classe != 'TAebNumber':
                self.w32(v + 0x148, self.alloc(4 * dim + 16))
            self.vetores[nome] = v
            self.vetor_de[v] = nome
            self.w32(sd + off, v)
        return sd

    def montar_form(self, dfm_idx, tamanho=0x1500):
        c = self.ctx
        form = self.alloc(tamanho)
        self.w32(form, c.rtti.vmts['TFormConfig'])
        for off, nome in c.campos.items():
            o = dfm_idx.get(nome)
            if o is None or o.classe not in c.rtti.vmts:
                continue
            obj = self.alloc(0x500)
            self.w32(obj, c.rtti.vmts[o.classe])
            self.w8(obj + OFF_VISIBLE, 0 if str(o.props.get('Visible', 'True')) == 'False' else 1)
            self.w8(obj + OFF_ENABLED, 0 if str(o.props.get('Enabled', 'True')) == 'False' else 1)
            tag = str(o.props.get('Tag', 0))
            self.w32(obj + OFF_TAG, int(tag) if tag.lstrip('-').isdigit() else 0)
            self.w32(form + off, obj)
            self.controles[nome] = obj
            self.controle_de[obj] = nome
        self.form = form
        return form

    # ------------------------------------------------------------------ utilidades de leitura
    def f64(self, a):
        return struct.unpack('<d', bytes(self.uc.mem_read(a, 8)))[0]

    def wf64(self, a, v):
        self.uc.mem_write(a, struct.pack('<d', v))

    def ctl(self, nome):
        return self.controles[nome]

    def visivel(self, nome):
        return bool(self.uc.mem_read(self.controles[nome] + OFF_VISIBLE, 1)[0])

    def habilitado(self, nome):
        return bool(self.uc.mem_read(self.controles[nome] + OFF_ENABLED, 1)[0])

    def marcado(self, nome):
        return self.uc.mem_read(self.controles[nome] + OFF_CHECK_STATE, 1)[0] == 1

    def valor(self, nome):
        return self.f64(self.controles[nome] + OFF_NUM_VALUE)


# ====================================================================== sessões de emulação
class Sessao:
    """Uma máquina montada uma vez, restaurada (snapshot) antes de cada cenário."""

    def __init__(self, exe, ctx=None, dfm_form=None, dfm_sd=None, tab_ecu=None):
        self.tab_ecu = tab_ecu or {}
        self.ctx = ctx or A.Ctx(exe)
        self.exe = exe
        self.form_idx = dfm_form or A.indexa(A.dfm_arvore())
        self.sd_idx = dfm_sd or A.indexa(A.dfm_arvore(A.DFM_STREAM))
        self.m = MaquinaAbas(exe, self.ctx)
        self.m.montar_sd(self.sd_idx)
        self.m.montar_form(self.form_idx)
        self.defaults = A.defaults_construtor(self.ctx)
        for off, sz, val, _ in self.defaults:        # estado inicial do TStreamDati = o que o construtor grava
            self.m.uc.mem_write(self.m.sd + off, (val & ((1 << (8 * sz)) - 1)).to_bytes(sz, 'little'))
        self._ui_hooks()
        self._dll_personalizacao()
        self._snap = None
        self.forma = {}
        for o in self.sd_idx.values():
            if o.classe in ('TAebVector', 'TAebNumber') and 'SerialCode' in o.props:
                self.forma[int(o.props['SerialCode'])] = (int(o.props.get('DataLength', 1)),
                                                          int(o.props.get('ArrayDimension', 1)))
        self.m.ecu = EcuDono(self.tab_ecu, self.forma)

    # -------------------------------------------------------------- hooks de UI (camada de janela do VCL)
    def _ui_hooks(self):
        m = self.m

        def set_visible(mm, r, a):
            mm.efeitos.append(('visible', mm.controle_de.get(r['eax'], hex(r['eax'])), r['edx'] & 1))
            mm.w8(r['eax'] + OFF_VISIBLE, r['edx'] & 1)
        m.hook(0x7EDFA8, set_visible, 0)

        def perform(mm, r, a):          # TControl.Perform(Msg, WParam, LParam): sem janelas => nada
            mm.efeitos.append(('perform', mm.controle_de.get(r['eax'], hex(r['eax'])), hex(r['edx'])))
            return 0
        m.hook(0x7EF5F4, perform, 1)

        def set_text(mm, r, a):         # TControl.SetText(string)
            mm.efeitos.append(('texto', mm.controle_de.get(r['eax'], hex(r['eax'])), mm.ctx.string_em(r['edx']) if r['edx'] else ''))
            return 0
        m.hook(0x7EE0E0, set_text, 0)

        def num_para_texto(mm, r, a):   # FloatToStr/IntToStr(dest, valor) cdecl: texto vazio (não afeta o dado enviado)
            esp = mm.uc.reg_read(mm.X.UC_X86_REG_ESP)
            dest = mm.r32(esp + 4)
            mm.w32(dest, 0)
            return dest
        for ad in (0xA4ED60, 0x4080D4):
            m.hook(ad, num_para_texto, 0)

        def nada(mm, r, a):
            return 0
        for ad in NOOPS:
            m.hook(ad, nada, 0)
        # TNumericEdit: "valor atual" = double em +0x2E0 (a função original lê o Text; aqui devolve o campo)
        m.redireciona(0x96BA2C, bytes.fromhex('DD80E0020000C3'))

    def _amarrar(self):
        """Roda a rotina de amarração 0x48A228 (controle -> código do dado [ctl+0x2EC] e índice [ctl+0x2F0]);
        os controles falsos recebem exatamente o que o ProgBase grava neles na criação do formulário."""
        m = self.m
        m.chamar(0x48A228, {'eax': m.form})
        self.amarracao_erro = m.erro
        m.erro = None
        # DataIndex do DFM vale para quem a rotina não amarra (propriedade publicada do TNumericEdit)
        for nome, obj in m.controles.items():
            o = self.form_idx[nome]
            di = o.props.get('DataIndex')
            if di is not None and m.r32(obj + OFF_NUM_DATAINDEX) == 0:
                m.w32(obj + OFF_NUM_DATAINDEX, int(di))

    def _dll_personalizacao(self):
        """O ProgBase carrega uma DLL de personalização (_GetDefRowKMap, _GetDefColKMap, ..., ponteiros em
        0xAECD6C..0xAECD94, preenchidos em 0x4111F8). Ela não existe aqui: cada ponteiro vira uma função cdecl em
        Python; linhas/colunas do mapa K vêm do DFM (MAP_K: RowCount/ColCount); o resto devolve 0 (DESCONHECIDO)."""
        m = self.m
        mapk = self.sd_idx['MAP_K'].props
        valores = {0xAECD7C: int(mapk.get('RowCount', 13)), 0xAECD80: int(mapk.get('ColCount', 12))}
        for g in range(0xAECD6C, 0xAECD98, 4):
            a_ = m.codigo_stub
            m.codigo_stub += 16
            m.uc.mem_write(a_, b'\xC3')
            v = valores.get(g, 0)
            m.hook(a_, (lambda vv: (lambda mm, r, a: vv))(v), 0)
            m.w32(g, a_)

    # -------------------------------------------------------------- snapshot
    def _regioes(self):
        m = self.m
        sec = {s.Name.rstrip(b'\0'): s for s in m.ctx.b.pe.sections}
        d = sec[b'.data']
        return [(m.base + d.VirtualAddress, (d.Misc_VirtualSize + 0xFFF) & ~0xFFF),
                (C.HEAP, m._heap - C.HEAP + 0x100), (C.PILHA, C.PILHA_TAM), (C.TEB, 0x1000),
                (m.mm_base, m.mm_topo - m.mm_base + 0x100)]

    def salvar(self):
        self._snap = [(a, bytes(self.m.uc.mem_read(a, n))) for a, n in self._regioes()]
        self._snap_state = (self.m._heap, self.m.mm_topo)

    def restaurar(self):
        for a, d in self._snap:
            self.m.uc.mem_write(a, d)
        m = self.m
        m._heap, m.mm_topo = self._snap_state
        m.ecu = EcuDono(self.tab_ecu, self.forma)
        m.escritas = []
        m.caixas = []
        m.efeitos = []
        m.erro = None
        m.instr = 0
        m.entradas = set()
        m.contagem_stubs = {}
        m._prev = None

    def estado_dono(self):
        """Estado do TStreamDati que o ProgBase teria com a ECU do dono (OBS-FIO do log):
        modelo = 2º byte da identificação (`53 04 FE 4F 45 0B`) = 0x4F; MASK_FUNCTION (SC 194) nos 30 dwords de
        [SD+0x29EC] e [SD+0x2A64]=1 (máscara válida). Sem log, só o modelo."""
        est = {0x271D: (1, 0x4F)}
        mf = self.tab_ecu.get(194, {}).get('vetor')
        if mf:
            for i, b in enumerate(mf[:30]):
                est[0x29EC + 4 * i] = (4, b)
            est[0x2A64] = (1, 1)
        return est

    def cenario(self, lr=False, modelo=None, estado=None):
        """Volta ao estado inicial. lr=True liga [SD+0x80D] (família "LR"); modelo grava [SD+0x271D];
        estado = {offset: (tamanho, valor)} grava campos do TStreamDati."""
        if self._snap is None:
            self.salvar()
        self.restaurar()
        m = self.m
        m.w8(m.sd + 0x80D, 1 if lr else 0)
        if modelo is not None:
            m.w8(m.sd + 0x271D, modelo)
        for off, (sz, val) in (estado or {}).items():
            m.uc.mem_write(m.sd + off, (val & ((1 << (8 * sz)) - 1)).to_bytes(sz, 'little'))
        self._amarrar()          # a amarração depende de [SD+0x80D] (família): roda a cada cenário
        return m

    # -------------------------------------------------------------- execução de um handler
    def rodar(self, m, endereco, sender, extra=None):
        """Chama handler(Self=form, Sender, ...) e devolve dict {quadros, efeitos, erro, instr}."""
        key = m.alloc(16)
        m.w32(key, 13)
        regs = {'eax': m.form, 'edx': sender, 'ecx': key}
        regs.update(extra or {})
        ret = m.chamar(endereco, regs)
        r = m.resultado(ret)
        return {'quadros': r['quadros'], 'efeitos': list(m.efeitos), 'erro': r['erro'], 'instr': r['instrucoes'],
                'stubs': r['stubs'], 'funcoes': r['funcoes_executadas'],
                'lidos': list(m.ecu.lidos), 'escritas_ecu': list(m.ecu.escritas_ecu), 'caixas': list(m.caixas)}
