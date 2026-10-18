#!/usr/bin/env python3
"""Executa NA CPU EMULADA (Unicorn, x86-32) o código original do ProgBase.exe que grava a caixa
'Abilitazione anti-stallo' e captura os bytes que iriam para a porta serial.

Cadeia executada de verdade (código do .exe, mapeado 1:1; o binário só é lido):
  TStreamDati.Set 0x42E8D8 (caso 4 = bit 0x20 de FLAG_CONF3[0])
   -> TAebVector.SetValues 0x978F8C -> 0x97909C -> protocolo 0x92617C
   -> 0x922BF8/0x922C1C/0x922FD0 (monta bytes) -> 0x923608 (envia) -> TAebCom.Write 0x91DF00 -> WriteFile
   -> 0x923860/0x922B0C/0x923B30 (lê o ACK da ECU)
O que é STUB (não executa código do .exe): as chamadas da KERNEL32 (WriteFile/ReadFile/PurgeComm/...,
preenchidas na IAT) e o próprio ECU (eco + ACK '53 00 53'). Os objetos (TStreamDati, TAebVector, conexão,
protocolo, porta, lock) são montados na heap emulada com os campos mínimos lidos pelo código; cada campo
está documentado em docs/evidence/2026-10-18-cpu-setter-original.md. Nada do .exe é modificado.
Determinístico. Precisa de unicorn e pefile (capstone não é usado)."""
import argparse
import hashlib
import json
import os
import struct
import sys

SHA256_ESPERADO = '8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4'
EXE_CANDIDATOS = [os.environ.get('PROGBASE_EXE', ''),
                  os.path.join(os.environ.get('OMEGAS_CACHE', os.path.expanduser('~/.cache/omegas')), 'ProgBase.exe'),
                  '/tmp/claude-0/-home-user/57e20309-4640-5e31-b02d-4b2faf9cf2b3/scratchpad/progbase/pb.exe']

# endereços do original (OBS-COD)
A_SETTER = 0x42E8D8
A_SETVALUES = 0x978F8C
VMT_VETOR = 0xAC9BA8       # TAebVector
VMT_PORTA = 0xAB55CC       # TAebCom (mesmos slots 0x24/0x28/0x2C/0x30/0x58 de TAebBeeProg)
VMT_LOCK = 0x65D5FC        # classe do [conexão+0x44] (Acquire 0x65DF68 / Release 0x65DF7C)

# memória emulada (fora da imagem 0x400000..0x115B000)
HEAP, HEAP_TAM = 0x02000000, 0x00100000
PILHA, PILHA_TAM = 0x03000000, 0x00100000
TEB = 0x03800000
GDT = 0x03900000
STUBS = 0x04000000
STOP = STUBS + 0xFF0


def achar_exe(pedido):
    for c in ([pedido] if pedido else []) + EXE_CANDIDATOS:
        if c and os.path.isfile(c):
            return c
    return None


class ErroEmulacao(Exception):
    pass


class _Ecu:
    """Stub do outro lado da serial: half-duplex (cada byte escrito volta como eco) e ACK '53 00 53'
    ao completar um quadro de escrita 0x3n com checksum correto. Modelo mínimo (INFERIDO do log)."""

    def __init__(self, responde):
        self.responde = responde
        self.rx = bytearray()
        self.acc = bytearray()
        self.quadros = []

    def escreve(self, dados):
        for b in dados:
            self.acc.append(b)
            if self.responde:
                self.rx.append(b)
            n = self._tam(self.acc)
            if n is not None and len(self.acc) >= n:
                q = bytes(self.acc[:n])
                del self.acc[:n]
                self.quadros.append(q)
                if self.responde and (sum(q[:-1]) & 0xFF) == q[-1]:
                    self.rx += bytes([0x53, 0x00, 0x53])

    @staticmethod
    def _tam(acc):
        op = acc[0]
        if op & 0xF8 in (0x10, 0x30) and (op & 7) < 7:   # 0x1n = SetNumber/indexado (OBS-FIO: `14 54 00 00 00 64 CC` -> `53 00 53`); 0x3n = SetVector
            return (op & 7) + 3
        return None

    def le(self, n):
        out = bytes(self.rx[:n])
        del self.rx[:n]
        return out


class _Maquina:
    def __init__(self, exe, patches=None, ecu_responde=True, max_instr=3_000_000):
        import pefile
        import unicorn
        from unicorn import x86_const as X
        self.uc_mod, self.X = unicorn, X
        with open(exe, 'rb') as fh:
            raw = fh.read()
        self.sha256 = hashlib.sha256(raw).hexdigest()
        pe = pefile.PE(data=raw, fast_load=True)
        pe.parse_data_directories()
        self.base = pe.OPTIONAL_HEADER.ImageBase
        uc = self.uc = unicorn.Uc(unicorn.UC_ARCH_X86, unicorn.UC_MODE_32)
        # imagem 1:1 (cabeçalhos + seções)
        uc.mem_map(self.base, 0x1000)
        uc.mem_write(self.base, raw[:0x400])
        for s in pe.sections:
            va = self.base + s.VirtualAddress
            tam = (max(s.Misc_VirtualSize, s.SizeOfRawData) + 0xFFF) & ~0xFFF
            uc.mem_map(va, tam)
            uc.mem_write(va, raw[s.PointerToRawData:s.PointerToRawData + s.SizeOfRawData])
        self.code_lo = self.base + pe.sections[0].VirtualAddress
        uc.mem_map(HEAP, HEAP_TAM)
        uc.mem_map(PILHA, PILHA_TAM)
        uc.mem_map(TEB, 0x1000)
        uc.mem_map(GDT, 0x1000)
        uc.mem_map(STUBS, 0x1000)
        self._heap = HEAP + 0x100
        # FS -> TEB (cadeia SEH em fs:[0] usada pelos prólogos __InitExceptBlockLDTC 0xA4A204)
        uc.mem_write(TEB, struct.pack('<IIII', 0xFFFFFFFF, PILHA + PILHA_TAM, PILHA, 0))
        uc.mem_write(TEB + 0x18, struct.pack('<I', TEB))
        def desc(base, acc):  # descritor flat de 4 GB (ring 0), limite 0xFFFFF com granularidade de página
            return struct.pack('<HHBBBB', 0xFFFF, base & 0xFFFF, (base >> 16) & 0xFF, acc, 0xCF, base >> 24)
        uc.mem_write(GDT + 8, desc(TEB, 0x92))      # 1: FS -> TEB
        uc.mem_write(GDT + 16, desc(0, 0x92))       # 2: dados flat (DS/ES/SS)
        uc.mem_write(GDT + 24, desc(0, 0x9A))       # 3: código flat (CS)
        uc.reg_write(X.UC_X86_REG_GDTR, (0, GDT, 0xFF, 0))
        for nome, idx in (('DS', 2), ('ES', 2), ('SS', 2), ('CS', 3), ('FS', 1)):
            uc.reg_write(getattr(X, 'UC_X86_REG_' + nome), idx << 3)
        # patches SÓ na memória emulada (teste de mutação)
        for a, v in sorted((patches or {}).items()):
            uc.mem_write(a, bytes([v]))
        self.ecu = _Ecu(ecu_responde)
        self.escritas = []
        self.contagem_stubs = {}
        self.erro = None
        self.executados = set()
        self.entradas = set()
        self.instr = 0
        self.max_instr = max_instr
        self._tick = 1000
        self._prev = None
        self._montar_iat(pe)
        uc.hook_add(unicorn.UC_HOOK_CODE, self._hook_codigo)

    # ---------------------------------------------------------------- heap / memória
    def alloc(self, n):
        a = self._heap
        self._heap = (self._heap + n + 15) & ~15
        if self._heap > HEAP + HEAP_TAM:
            raise ErroEmulacao('heap emulada esgotada')
        return a

    def w32(self, a, v):
        self.uc.mem_write(a, struct.pack('<I', v & 0xFFFFFFFF))

    def w16(self, a, v):
        self.uc.mem_write(a, struct.pack('<H', v & 0xFFFF))

    def w8(self, a, v):
        self.uc.mem_write(a, bytes([v & 0xFF]))

    def r32(self, a):
        return struct.unpack('<I', bytes(self.uc.mem_read(a, 4)))[0]

    # ---------------------------------------------------------------- stubs da KERNEL32
    def _montar_iat(self, pe):
        self.stubs = {}
        self.stub_nome = {}
        k = 0
        for e in pe.DIRECTORY_ENTRY_IMPORT:
            for i in e.imports:
                nome = i.name.decode() if i.name else 'ord%s' % i.ordinal
                a = STUBS + 0x10 * k
                k += 1
                self.w32(i.address, a)
                self.stub_nome[a] = nome
        self.stub_nome[STOP] = '<fim>'
        self.stdcall = {  # nome: nº de argumentos (retorno stdcall)
            'WriteFile': 5, 'ReadFile': 5, 'PurgeComm': 2, 'GetCommTimeouts': 2, 'SetCommTimeouts': 2,
            'EnterCriticalSection': 1, 'LeaveCriticalSection': 1, 'Sleep': 1, 'GetTickCount': 0,
            'GetLastError': 0, 'ClearCommError': 3, 'CloseHandle': 1}

    def _arg(self, n):
        esp = self.uc.reg_read(self.X.UC_X86_REG_ESP)
        return self.r32(esp + 4 + 4 * n)

    def _stub(self, nome):
        X, uc = self.X, self.uc
        self.contagem_stubs[nome] = self.contagem_stubs.get(nome, 0) + 1
        if nome == '<fim>':
            uc.emu_stop()
            return None
        if nome not in self.stdcall:
            self.erro = 'API sem stub: %s' % nome
            uc.emu_stop()
            return None
        ret = 1
        if nome == 'WriteFile':
            buf, n, pw = self._arg(1), self._arg(2), self._arg(3)
            dados = bytes(uc.mem_read(buf, n))
            self.escritas.append(dados)
            self.ecu.escreve(dados)
            if pw:
                self.w32(pw, n)
        elif nome == 'ReadFile':
            buf, n, pr = self._arg(1), self._arg(2), self._arg(3)
            d = self.ecu.le(n)
            if d:
                uc.mem_write(buf, d)
            if pr:
                self.w32(pr, len(d))
        elif nome == 'GetTickCount':
            self._tick += 10
            ret = self._tick
        elif nome == 'Sleep':
            self._tick += self._arg(0)
            ret = 0
        elif nome == 'GetLastError':
            ret = 0
        elif nome == 'ClearCommError':
            for k in (1, 2):
                p = self._arg(k)
                if p:
                    uc.mem_write(p, b'\0' * 4)
        elif nome == 'GetCommTimeouts':
            uc.mem_write(self._arg(1), b'\0' * 20)
        esp = uc.reg_read(X.UC_X86_REG_ESP)
        r = self.r32(esp)
        uc.reg_write(X.UC_X86_REG_EAX, ret & 0xFFFFFFFF)
        uc.reg_write(X.UC_X86_REG_ESP, esp + 4 + 4 * self.stdcall[nome])
        uc.reg_write(X.UC_X86_REG_EIP, r)

    def _hook_codigo(self, uc, addr, size, _):
        if addr == getattr(self, '_parar_em', None):
            uc.emu_stop()
            return
        if addr >= STUBS and addr < STUBS + 0x1000:
            self._prev = None
            self._stub(self.stub_nome.get(addr, 'desconhecido_%x' % addr))
            return
        self.instr += 1
        if self.instr > self.max_instr:
            self.erro = 'limite de instruções'
            uc.emu_stop()
            return
        self.executados.add(addr)
        esp = uc.reg_read(self.X.UC_X86_REG_ESP)
        if self._prev is not None:  # call = esp-4 e [esp] = endereço seguinte do call
            pa, ps, pe_ = self._prev
            if esp == pe_ - 4 and addr != pa + ps and self.r32(esp) == pa + ps:
                self.entradas.add(addr)
        self._prev = (addr, size, esp)

    # ---------------------------------------------------------------- objetos
    def montar(self, flag3, versao_proto, buffered, obj80D, obj80E, serial_code=250, cache=None):
        self.lock = self.alloc(0x40)
        self.w32(self.lock, VMT_LOCK)
        self.status_porta = self.alloc(16)
        self.porta = self.alloc(0x200)
        self.w32(self.porta, VMT_PORTA)
        self.w32(self.porta + 0x40, 100)
        self.w32(self.porta + 0x44, self.status_porta)
        self.w8(self.porta + 0x48, 1)
        self.w8(self.porta + 0x49, 1 if buffered else 0)
        self.w32(self.porta + 0x88, 0x1234)
        self.proto = P = self.alloc(0x300)
        self.w32(P + 0x40, self.porta)
        for off, v in ((0x258, 1000), (0x25C, 400), (0x260, 5), (0x294, 5), (0x298, 5)):
            self.w32(P + off, v)
        self.w8(P + 0x2A4, 1)
        self.w8(P + 0x2B0, 1)
        self.w32(P + 0x2B8, 0x1F40)
        self.w32(P + 0x2C4, versao_proto)
        self.conexao = self.alloc(0x100)
        self.w32(self.conexao + 0x40, P)
        self.w32(self.conexao + 0x44, self.lock)
        self.sd = self.alloc(0x3EC0)
        self.w32(self.sd + 0x2994, flag3[0])
        self.w32(self.sd + 0x2998, flag3[1])
        self.w8(self.sd + 0x80D, obj80D)
        self.w8(self.sd + 0x80E, obj80E)
        self.vetor = self.criar_vetor(serial_code)
        self.w32(self.sd + 0x39C, self.vetor)

    def criar_vetor(self, serial_code, dim=2, comp=2):
        v = self.alloc(0x180)
        self.w32(v, VMT_VETOR)
        self.w32(v + 0x44, dim)
        self.w32(v + 0x48, 1)
        self.w32(v + 0x4C, 0xFFFFFFFF)
        self.w32(v + 0x50, 0xFFFFFFFF)
        self.w32(v + 0x54, (1 << (8 * comp)) - 1)
        self.w32(v + 0x58, comp)
        self.w32(v + 0x64, serial_code)
        self.w32(v + 0x6C, serial_code)
        self.w32(v + 0x70, dim)
        self.w32(v + 0x74, comp)
        self.w32(v + 0xC0, self.conexao)
        lista = self.alloc(0x20)  # TList vazia (sem dependentes registrados)
        self.w32(v + 0x11C, lista)
        self.w8(v + 0x124, 1)
        self.w32(v + 0x148, self.alloc(4 * dim))
        return v

    # ---------------------------------------------------------------- execução
    def chamar(self, addr, regs=None, pilha=(), ate=None):
        X, uc = self.X, self.uc
        esp = PILHA + PILHA_TAM - 0x100
        for v in reversed(list(pilha)):
            esp -= 4
            self.w32(esp, v)
        esp -= 4
        self.w32(esp, STOP)
        uc.reg_write(X.UC_X86_REG_ESP, esp)
        for r, v in (regs or {}).items():
            uc.reg_write(getattr(X, 'UC_X86_REG_' + r.upper()), v)
        self.entradas.add(addr)  # ponto de entrada chamado diretamente
        try:
            self._parar_em = ate   # parada por hook (emu_start(until=) deixava o hook de stub perder a troca de EIP)
            uc.emu_start(addr, STOP)
        except self.uc_mod.UcError as e:
            self.erro = 'UcError: %s em eip=%#x' % (e, uc.reg_read(X.UC_X86_REG_EIP))
        return uc.reg_read(X.UC_X86_REG_EAX) & 0xFF

    def resultado(self, ret):
        quadros = list(self.ecu.quadros)
        if self.ecu.acc:
            quadros.append(bytes(self.ecu.acc))
        return {
            'quadros': quadros,
            'escritas_writefile': list(self.escritas),
            'cache_final': (self.r32(self.sd + 0x2994), self.r32(self.sd + 0x2998)),
            'retorno_al': ret,
            'flags_erro_protocolo': self.r32(self.proto + 0x44),
            'funcoes_executadas': sorted(self.entradas),
            'instrucoes': self.instr,
            'stubs': dict(sorted(self.contagem_stubs.items())),
            'erro': self.erro,
            'sha256': self.sha256,
        }


def emular(exe, acao='marcar', flag3=(0, 0), buffered=1, versao_proto=2, ecu_responde=True,
           obj80D=0, obj80E=1, patches=None):
    """Roda TStreamDati.Set(caso 4, marcar/desmarcar) com FLAG_CONF3 inicial `flag3` (2 palavras)."""
    if acao not in ('marcar', 'desmarcar'):
        raise ValueError(acao)
    m = _Maquina(exe, patches, ecu_responde)
    m.montar(flag3, versao_proto, buffered, obj80D, obj80E)
    ret = m.chamar(A_SETTER, {'eax': m.sd, 'edx': 4, 'ecx': 1 if acao == 'marcar' else 0})
    return m.resultado(ret)


def calibrar_flag_conf1(exe, valores=(0x2C86, 0x1051), buffered=1, versao_proto=2):
    """Chama SetValues 0x978F8C direto num vetor de código 3 (FLAG_CONF1). O fio (Portmon) mostra
    `35 03 00 86 2C 51 10 4B` para 2C86 1051: valida a emulação contra um quadro real."""
    m = _Maquina(exe)
    m.montar((0, 0), versao_proto, buffered, 0, 1, serial_code=3)
    vet = m.criar_vetor(3)
    buf = m.alloc(16)
    m.uc.mem_write(buf, struct.pack('<II', *valores))
    ret = m.chamar(A_SETVALUES, pilha=(vet, buf))
    m.sd_cache = None
    r = m.resultado(ret)
    r['cache_final'] = tuple(m.r32(m.r32(vet + 0x148) + 4 * i) for i in range(2))
    return r


# ---- função 'Marcha lenta' (Gás / Rientro a benzina / Benzina): RadioGasClick 0x4BFAD8 (OBS-COD, 2026-10-18) ----
A_FLAG1_SETTER = 0x42D300     # TStreamDati.Set de FLAG_CONF1 (edx = caso 0x22 -> bit 0x0400 da palavra 1)
A_SET_FISICO = 0x43734C       # (obj, código, índice, double): código 0x4C -> GIRI_PER_BENZINA[índice]
CASO_MINIMO, CODIGO_GIRI = 0x22, 0x4C
RPM_IDENTIFICACAO = 1100.0    # constante 0x40913000_00000000 empilhada em RadioGasClick (tags 1 e 2)
# Tag do DFM: RadioGas = 0 (padrão), RadioRientro = 1, RadioBenzina = 2. O handler faz `sub edx,1; jb -> tag 0; je -> tag 1; dec; je -> tag 2`.
MODOS_LENTA = {'gas': 0, 'volta': 1, 'gasolina': 2}


def emular_modo_lenta(exe, modo, flag1=(0x2486, 0x1051), giri=(0, 8500), buffered=1, versao_proto=2,
                      ecu_responde=True, obj80D=0, obj80E=1, patches=None, handler=True):
    """Clique em um dos três botões de 'Funcionamento al minimo'. Com handler=True (padrão) executa o PRÓPRIO
    TFormConfig.RadioGasClick (0x4BFAD8) do .exe, com o Sender de Tag 0/1/2, até 0x4BFBC8 (fim das duas gravações): a escolha
    do botão, o bit e o 1100.0 vêm do código. Com handler=False chama direto 0x42D300 e 0x43734C (valores tirados do
    desmontado). Os dois vetores são montados na heap com os valores iniciais dados. Quadros na ordem em que saíram."""
    if modo not in MODOS_LENTA:
        raise ValueError(modo)
    tag = MODOS_LENTA[modo]
    m = _Maquina(exe, patches, ecu_responde)
    m.montar((0, 0), versao_proto, buffered, obj80D, obj80E)
    m.w32(m.sd + 0x2984, flag1[0])
    m.w32(m.sd + 0x2988, flag1[1])
    v1 = m.criar_vetor(3)
    m.w32(m.sd + 0x90, v1)
    for i, w in enumerate(flag1):
        m.w32(m.r32(v1 + 0x148) + 4 * i, w)
    lr = obj80D != 0   # família [obj+0x80D]!=0: código físico 0x57 -> GIRI_PER_BENZINA_LR (SerialCode 176) em [obj+0x464]
    vg = m.criar_vetor(176 if lr else 161)
    m.w32(m.sd + (0x464 if lr else 0x2A0), vg)
    for i, w in enumerate(giri):
        m.w32(m.r32(vg + 0x148) + 4 * i, w)
        m.w32(m.sd + 0x2CBC + 4 * i, w)
    if handler:
        form = m.alloc(0x2000)                 # TFormConfig (só [ebx+0x1280] = 0: não está carregando a tela)
        sender = m.alloc(0x40)
        m.w32(sender + 0xC, tag)               # TComponent.Tag do botão
        ponteiro = m.alloc(16)                 # a global 0xAEBED8 aponta para um ponteiro para o TStreamDati
        m.w32(ponteiro, m.sd)
        m.w32(0xAEBED8, ponteiro)
        ret = m.chamar(0x4BFAD8, {'eax': form, 'edx': sender}, ate=0x4BFBC8)
        n1 = len(m.ecu.quadros)
        r = m.resultado(ret)
        r['quadros_flag1'] = [q for q in r['quadros'] if q[0] == 0x35]
        r['quadros_giri'] = [q for q in r['quadros'] if q[0] == 0x14]
    else:
        bit = 1 if tag == 2 else 0
        rpm = 0.0 if tag == 0 else RPM_IDENTIFICACAO
        ret1 = m.chamar(A_FLAG1_SETTER, {'eax': m.sd, 'edx': CASO_MINIMO, 'ecx': bit})
        n1 = len(m.ecu.quadros)
        bits = struct.unpack('<II', struct.pack('<d', rpm))  # (baixo, alto) do double empilhado como em 0x4BFB5E
        ret2 = m.chamar(A_SET_FISICO, pilha=(m.sd, 0x57 if lr else CODIGO_GIRI, 0, bits[0], bits[1]))
        r = m.resultado(ret2)
        r['quadros_flag1'] = r['quadros'][:n1]
        r['quadros_giri'] = r['quadros'][n1:]
    r['cache_flag1'] = (m.r32(m.sd + 0x2984), m.r32(m.sd + 0x2988))
    r['cache_giri'] = (m.r32(m.sd + 0x2CBC), m.r32(m.sd + 0x2CC0))
    r['handler_executado'] = handler
    return r


def _hex(b):
    return bytes(b).hex(' ').upper()


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--exe')
    ap.add_argument('--acao', choices=('marcar', 'desmarcar'), default='marcar')
    ap.add_argument('--flag3', default='0000:0000', help='W0:W1 em hex (estado inicial de FLAG_CONF3)')
    ap.add_argument('--buffered', type=int, choices=(0, 1), default=1)
    ap.add_argument('--versao-proto', type=int, default=2)
    ap.add_argument('--sem-ecu', action='store_true')
    ap.add_argument('--obj80D', type=int, default=0)
    ap.add_argument('--patch', action='append', default=[], help='ENDERECO=BYTE (só na memória emulada)')
    ap.add_argument('--modo-lenta', choices=sorted(MODOS_LENTA), help='clique em Gas / Rientro a benzina / Benzina (FLAG_CONF1 + GIRI_PER_BENZINA)')
    ap.add_argument('--flag1', default='2486:1051')
    ap.add_argument('--giri', default='0:8500', help='G0:G1 decimais (GIRI_PER_BENZINA inicial)')
    ap.add_argument('--calibracao', action='store_true', help='FLAG_CONF1 2C86:1051 contra o fio')
    ap.add_argument('--json', action='store_true')
    a = ap.parse_args(argv)
    exe = achar_exe(a.exe)
    if not exe:
        print('ProgBase.exe não encontrado (use --exe ou PROGBASE_EXE)', file=sys.stderr)
        return 2
    patches = {int(p.split('=')[0], 16): int(p.split('=')[1], 16) for p in a.patch}
    if a.modo_lenta:
        f1 = tuple(int(x, 16) for x in a.flag1.split(':'))
        g = tuple(int(x) for x in a.giri.split(':'))
        r = emular_modo_lenta(exe, a.modo_lenta, f1, g, a.buffered, a.versao_proto, not a.sem_ecu, a.obj80D, 1, patches)
        for q in r['quadros']:
            print('quadro:', _hex(q))
        print('cache FLAG_CONF1: %04X %04X  cache GIRI: %d %d  erro: %s' % (r['cache_flag1'] + r['cache_giri'] + (r['erro'],)))
        return 0
    if a.calibracao:
        r = calibrar_flag_conf1(exe, buffered=a.buffered, versao_proto=a.versao_proto)
    else:
        w0, w1 = (int(x, 16) for x in a.flag3.split(':'))
        r = emular(exe, a.acao, (w0, w1), a.buffered, a.versao_proto, not a.sem_ecu, a.obj80D, 1, patches)
    if a.json:
        r = dict(r, quadros=[_hex(q) for q in r['quadros']],
                 escritas_writefile=[_hex(q) for q in r['escritas_writefile']],
                 funcoes_executadas=[hex(f) for f in r['funcoes_executadas']])
        print(json.dumps(r, indent=1, sort_keys=True))
    else:
        for q in r['quadros']:
            print('quadro:', _hex(q))
        print('cache FLAG_CONF3 final: %04X %04X' % r['cache_final'])
        print('instruções: %d  stubs: %s  erro: %s' % (r['instrucoes'], r['stubs'], r['erro']))
    return 0


if __name__ == '__main__':
    sys.exit(main())
