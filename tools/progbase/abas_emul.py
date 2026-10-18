#!/usr/bin/env python3
"""Executa NA CPU EMULADA (Unicorn, x86-32) o código REAL do ProgBase.exe das abas do TFormConfig.

Estende `tools/emulacao/cpu_setter_original.py` (mesma máquina: imagem do .exe mapeada 1:1, KERNEL32 em stub,
ECU em stub que só ecoa e dá ACK). O que é novo aqui:
  * TStreamDati (o objeto global [[0xAEBED8]]) montado com TODOS os 320 componentes do DFM (TSTREAMDATI.txt):
    SerialCode, DataLength, DataMask, ArrayDimension, Signed, Coeffs etc. vêm do DFM (nada escrito à mão).
  * TFormConfig montado com um objeto falso por controle do DFM (VMT real da classe do controle).
  * Pontos de interceptação (hooks) só na camada de janela do VCL: SetVisible/SetEnabled/SetChecked/SetValue...
    gravam o efeito "na tela" em vez de chamar a USER32. A LÓGICA do ProgBase (handlers, getters/setters do
    TStreamDati, conversões x87, TAebVector/TAebNumber, protocolo) é executada de verdade.
Determinístico. Precisa de unicorn e pefile.
"""
import os
import struct
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(AQUI, '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'tools', 'emulacao'))
sys.path.insert(0, AQUI)
import cpu_setter_original as C  # noqa: E402
import abas_re as A  # noqa: E402

# memória maior que a do módulo base (320 vetores + ~900 objetos de controle)
C.HEAP_TAM = 0x00800000
C.PILHA_TAM = 0x00200000

G_SD = 0xAEBED8        # [G_SD] -> variável global; [variável] -> instância TStreamDati
VAR_SD = 0xAECFB4

# layout dos controles falsos (OBS-COD: lidos nos getters/setters do VCL do próprio .exe)
OFF_VISIBLE, OFF_ENABLED = 0x69, 0x6A
OFF_TAG = 0x30            # TComponent.FTag
OFF_CHECK_STATE = 0x29A   # TCheckBox: cbChecked = 1 (0x758DFC)
OFF_NUM_VALUE = 0x2E0     # TNumericEdit: double
OFF_NUM_DATAINDEX = 0x2F0

CLASSES_CONTROLE = ('TLabel', 'TNumericEdit', 'TCheckBox', 'TValueEditEx', 'TKeyComboBox', 'TButton', 'TRadioButton',
                    'TPanel', 'TGroupBox', 'TComboBox', 'TShape', 'TBevel', 'TImage', 'TDrawGrid', 'TScrollBox',
                    'TStaticText', 'TBitBtn', 'TTabSheet', 'TPageControl', 'TChart', 'TGridPanel', 'TPanelImage')

# argumentos (stdcall) das APIs que o código das abas pode tocar; sem tabela => erro explícito
STDCALL_EXTRA = {
    'GetDeviceCaps': 2, 'SendMessageA': 4, 'SendMessageW': 4, 'PostMessageA': 4, 'IsWindow': 1, 'GetFocus': 0,
    'GetDC': 1, 'ReleaseDC': 2, 'SelectObject': 2, 'GetTextExtentPoint32A': 4, 'GetTextExtentPoint32W': 4,
    'GetSystemMetrics': 1, 'InvalidateRect': 3, 'GetWindowLongA': 2, 'GetKeyState': 1, 'MessageBeep': 1,
    'GetCurrentThreadId': 0, 'TlsGetValue': 1, 'GetProcAddress': 2, 'GetModuleHandleA': 1, 'InterlockedIncrement': 1,
    'InterlockedDecrement': 1, 'GetCurrentProcessId': 0, 'EnterCriticalSection': 1, 'LeaveCriticalSection': 1,
}


class MaquinaAbas(C._Maquina):
    """Máquina com TStreamDati completo + formulário falso + hooks de UI."""

    def __init__(self, exe, ctx, max_instr=6_000_000, ecu_responde=True):
        super().__init__(exe, None, ecu_responde, max_instr)
        self.ctx = ctx
        self.stdcall.update(STDCALL_EXTRA)
        self.hooks = {}          # endereço -> (função, nº de args empilhados limpos pelo callee)
        self.efeitos = []        # eventos de UI na ordem em que aconteceram
        self.escritas_sd = []    # escritas de memória no cache do TStreamDati
        self.vetores = {}        # nome -> endereço do objeto
        self.vetor_de = {}       # endereço -> nome
        self.controles = {}      # nome -> endereço
        self.controle_de = {}    # endereço -> nome
        self.sentinela = {}
        self.uc.hook_add(self.uc_mod.UC_HOOK_CODE, self._hook_hooks)
        # a IAT tem 772 entradas (0x10 cada): o módulo base só mapeou 0x1000 bytes
        self.uc.mem_map(C.STUBS + 0x1000, 0x6000)
        self.codigo_stub = C.STUBS + 0x5000
        self.redirs = {}
        self.uc.hook_add(self.uc_mod.UC_HOOK_CODE, self._hook_stubs_altos, begin=C.STUBS + 0x1000, end=C.STUBS + 0x4FFF)

    # ------------------------------------------------------------------ hooks
    def hook(self, addr, fn, nargs=0):
        self.hooks[addr] = (fn, nargs)

    def _hook_hooks(self, uc, addr, size, _):
        if addr in self.redirs:
            self.uc.reg_write(self.X.UC_X86_REG_EIP, self.redirs[addr])
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

    def _hook_stubs_altos(self, uc, addr, size, _):
        self._prev = None
        self._stub(self.stub_nome.get(addr, 'desconhecido_%x' % addr))

    def redireciona(self, addr, codigo):
        """Troca a função em addr por um trecho de código x86 curto escrito na área de stubs."""
        a = self.codigo_stub
        self.uc.mem_write(a, codigo)
        self.codigo_stub += (len(codigo) + 15) & ~15
        self.redirs[addr] = a

    # ------------------------------------------------------------------ montagem
    def montar_sd(self, dfm_sd, valores_flag=None):
        """TStreamDati com todos os componentes do DFM."""
        c, b = self.ctx, self.ctx.b
        self.montar((0, 0), 2, 1, 0, 1)    # conexão/porta/protocolo/lock + SD base (obj80D=0, obj80E=1)
        sd = self.sd
        self.w32(VAR_SD, sd)
        self.w32(sd + 0x6C, self.conexao)
        vmts = c.rtti.vmts
        for nome, o in dfm_sd.items():
            off = c.inv_sd.get(nome)
            if off is None or o.classe not in ('TAebVector', 'TAebNumber', 'TAebMatrix'):
                continue
            serial = o.props.get('SerialCode', 0)
            dl = o.props.get('DataLength', 1)
            mask = o.props.get('DataMask', 255)
            dim = o.props.get('ArrayDimension', 1)
            if o.classe == 'TAebMatrix':
                dim = max(1, o.props.get('ColCount', 1) * o.props.get('RowCount', 1))
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
            self.w32(obj + OFF_TAG, int(o.props.get('Tag', 0)) if str(o.props.get('Tag', 0)).lstrip('-').isdigit() else 0)
            di = o.props.get('DataIndex')
            if di is not None:
                self.w32(obj + OFF_NUM_DATAINDEX, int(di))
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
        return bool(self.uc.mem_read(self.controles[nome] + OFF_CHECK_STATE, 1)[0] == 1)

    def valor(self, nome):
        return self.f64(self.controles[nome] + OFF_NUM_VALUE)


# ====================================================================== sessões de emulação
class Sessao:
    """Uma máquina montada uma vez, restaurada (snapshot) antes de cada cenário."""

    def __init__(self, exe, ctx=None, dfm_form=None, dfm_sd=None):
        self.ctx = ctx or A.Ctx(exe)
        self.exe = exe
        self.form_idx = dfm_form or A.indexa(A.dfm_arvore())
        self.sd_idx = dfm_sd or A.indexa(A.dfm_arvore(A.DFM_STREAM))
        self.m = MaquinaAbas(exe, self.ctx)
        self.m.montar_sd(self.sd_idx)
        self.m.montar_form(self.form_idx)
        self._ui_hooks()
        self._snap = None

    # -------------------------------------------------------------- hooks de UI (camada de janela do VCL)
    def _ui_hooks(self):
        m = self.m

        def set_visible(mm, r, a):
            mm.efeitos.append(('visible', mm.controle_de.get(r['eax'], hex(r['eax'])), r['edx'] & 1))
            mm.w8(r['eax'] + OFF_VISIBLE, r['edx'] & 1)
        m.hook(0x7EDFA8, set_visible, 0)
        # TNumericEdit: "valor atual" = double em +0x2E0 (a função original lê o Text; aqui devolve o valor do campo)
        m.redireciona(0x96BA2C, bytes.fromhex('DD80E0020000C3'))

    # -------------------------------------------------------------- snapshot
    def _regioes(self):
        m = self.m
        sec = {s.Name.rstrip(b'\0'): s for s in m.ctx.b.pe.sections}
        d = sec[b'.data']
        return [(m.base + d.VirtualAddress, (d.Misc_VirtualSize + 0xFFF) & ~0xFFF),
                (C.HEAP, m._heap - C.HEAP + 0x100), (C.PILHA, C.PILHA_TAM), (C.TEB, 0x1000)]

    def salvar(self):
        self._snap = [(a, bytes(self.m.uc.mem_read(a, n))) for a, n in self._regioes()]
        self._snap_state = (self.m._heap, dict(self.m.sentinela))

    def restaurar(self):
        for a, d in self._snap:
            self.m.uc.mem_write(a, d)
        m = self.m
        m._heap = self._snap_state[0]
        m.ecu.quadros = []
        m.ecu.acc = bytearray()
        m.ecu.rx = bytearray()
        m.escritas = []
        m.efeitos = []
        m.erro = None
        m.instr = 0
        m.entradas = set()
        m.contagem_stubs = {}
        m._prev = None

    def cenario(self):
        if self._snap is None:
            self.salvar()
        self.restaurar()
        return self.m

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
                'stubs': r['stubs'], 'funcoes': r['funcoes_executadas']}
