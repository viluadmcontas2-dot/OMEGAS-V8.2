#!/usr/bin/env python3
"""Anti-apagão (aba F9): executa NA CPU EMULADA (Unicorn) o código ORIGINAL que os handlers da aba chamam e devolve
os bytes que iriam para a serial.

Executado do .exe (OBS-COD):
- EditAntiStalloKeyPress 0x4C97D8 (Enter num EditAntiStallo0..4): valor = TNumericEdit.GetValue 0x96B924 (arredonda),
  depois 0x979E48(ANTI_STALLO = [TStreamDati+0x4EC], DataIndex = [edit+0x2F0], valor) -> 0x979534 -> protocolo -> WriteFile.
  Aqui chamamos 0x979E48 direto (mesmos registradores que o handler monta).
- CheckAntiStalloClick 0x4C982C: setter 0x42E8D8(SD, 4, Checked) (já provado em cpu_setter_original.py; repetido aqui).
A ECU é STUB (eco + ACK `53 00 53`, INFERIDO do LOG). Determinístico. Uso:
  antiapagao_original.py [--exe ProgBase.exe] [--json]
"""
import argparse
import json
import os
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.abspath(os.path.join(AQUI, '..', '..'))
sys.path.insert(0, AQUI)
sys.path.insert(0, os.path.join(RAIZ, 'tools', 'progbase'))

DFM_SD = os.path.join(RAIZ, 'docs', 'evidence', 'dfm-original', 'TSTREAMDATI.txt')
OFF_ANTI_STALLO = 0x4EC      # campo ANTI_STALLO no TStreamDati (RTTI, OBS-COD)
FABRICA = (1600, 100, 1500, 2, 20)


def _hex(q):
    return ' '.join('%02X' % c for c in q)


def _ecu_classe():
    import abas_emul as E

    class EcuEstendida(E._EcuAck):
        """Igual a _EcuAck, mas também fecha o SetVector estendido 0x37 (`37 lo n hi dados soma`, total n+3)
        e o indexado estendido 0x17, se aparecerem; guarda TODOS os bytes escritos em `bruto`."""

        def __init__(self, responde):
            super().__init__(responde)
            self.bruto = bytearray()
            self.vetores = {}          # SC -> bytes atuais (estado da ECU stub), para responder `29 lo hi ck`
            self.leituras = []

        def escreve(self, dados):
            self.bruto += bytes(dados)
            for b in dados:
                self.acc.append(b)
                if self.responde:
                    self.rx.append(b)
                n = self._tam(self.acc)
                if n is None or len(self.acc) < n:
                    continue
                q = bytes(self.acc[:n])
                del self.acc[:n]
                if (sum(q[:-1]) & 0xFF) != q[-1]:
                    self.quadros.append(q)
                    continue
                if q[0] == 0x29:       # GetVector: resposta = eco + 53 n dados soma(53+n+dados) (OBS-FIO, ecu_emulator.py)
                    self.leituras.append(q)
                    d = self.vetores.get(q[1] | (q[2] << 8))
                    if d is not None and self.responde:
                        corpo = bytes([0x53, len(d)]) + d
                        self.rx += corpo + bytes([sum(corpo) & 0xFF])
                    continue
                self.quadros.append(q)
                if q[0] & 0xF8 == 0x30:   # SetVector muda o estado
                    if q[0] == 0x37:
                        self.vetores[q[1] | (q[3] << 8)] = q[4:-1]
                    else:
                        self.vetores[q[1] | (q[2] << 8)] = q[3:-1]
                if self.responde:
                    self.rx += bytes([0x53, 0x00, 0x53])

        @staticmethod
        def _tam(acc):
            op = acc[0]
            if op & 0xF8 in (0x10, 0x30) and (op & 7) < 7:
                return (op & 7) + 3
            if op in (0x17, 0x37) and len(acc) >= 3:
                return acc[2] + 3
            if op == 0x29:
                return 4
            return None
    return EcuEstendida


NEW_CPP = 0x00A33394     # operator new (RTL C++Builder, cdecl: [esp+4] = tamanho); o parser da leitura 29 pede buffer aqui
DELETE_CPP = 0x00A2A3FC  # operator delete (cdecl); vira no-op


def _instalar_heap(em):
    """A RTL de heap do C++Builder chama KERNEL32 que não está stubada; troca operator new por alocação no heap emulado
    (só memória, sem efeito no protocolo)."""
    import unicorn
    from unicorn import x86_const as X
    m = em.m

    def hook(uc, addr, size, _):
        if addr not in (NEW_CPP, DELETE_CPP):
            return
        esp = uc.reg_read(X.UC_X86_REG_ESP)
        if addr == NEW_CPP:
            tam = m.r32(esp + 4)
            uc.reg_write(X.UC_X86_REG_EAX, m.alloc(max(16, (tam + 15) & ~15)))
        uc.reg_write(X.UC_X86_REG_ESP, esp + 4)
        uc.reg_write(X.UC_X86_REG_EIP, m.r32(esp))
    for a in (NEW_CPP, DELETE_CPP):
        m.uc.hook_add(unicorn.UC_HOOK_CODE, hook, begin=a, end=a)


def _novo(exe, campos, objs, cache=FABRICA):
    import abas_emul as E
    em = E.Emul(exe, campos, objs, lr=0)
    _instalar_heap(em)
    em.m.ecu = _ecu_classe()(True)
    em.m.ecu.vetores[307] = b''.join(x.to_bytes(2, 'little') for x in cache)
    E.todos_vetores(em, objs, campos)
    v = em.m.r32(em.m.sd + OFF_ANTI_STALLO)
    tab = em.m.r32(v + 0x148)
    for i, x in enumerate(cache):
        em.m.w32(tab + 4 * i, x)
    return em, v


def gravar_campo(em, vetor, indice, valor):
    """Mesmo caminho do Enter em EditAntiStallo<indice>: 0x979E48(eax=vetor, edx=DataIndex, ecx=valor inteiro)."""
    import abas_emul as E
    em.m.ecu.bruto.clear()
    r = E.chamar_registros(em, 0x979E48, eax=vetor, edx=indice, ecx=valor)
    tab = em.m.r32(vetor + 0x148)
    return dict(indice=indice, valor=valor, quadros=[_hex(q) for q in r['quadros']],
                bruto=_hex(em.m.ecu.bruto), erro=r['erro'],
                cache_depois=[em.m.r32(tab + 4 * i) for i in range(5)])


def emular(exe):
    import abas_motor as M
    import abas_emul as E
    b, r = M.abrir(exe)
    campos = r.fields('TStreamDati')
    objs = E.objetos_sd(open(DFM_SD, encoding='utf-8').read())
    out = {'exe_sha256': b.sha256, 'cache_inicial': list(FABRICA)}
    casos = []
    for indice, valor in ((0, 1800), (0, 1600), (0, 1601), (1, 150), (2, 1000), (3, 5), (4, 30)):
        em, v = _novo(exe, campos, objs)
        casos.append(gravar_campo(em, v, indice, valor))
    out['campos'] = casos
    caixa = {}
    for nome, marcado, f3 in (('marcar (palavra0=0000)', 1, (0, 0)), ('desmarcar (palavra0=0020)', 0, (0x20, 0))):
        em, _ = _novo(exe, campos, objs)
        em.m.w32(em.m.sd + 0x2994, f3[0])
        em.m.w32(em.m.sd + 0x2998, f3[1])
        em.m.w32(0xAEBED8, em.m.alloc(4))
        em.m.w32(em.m.r32(0xAEBED8), em.m.sd)
        em.m.ecu.bruto.clear()
        rr = E.chamar_registros(em, 0x42E8D8, eax=em.m.sd, edx=4, ecx=marcado)
        caixa[nome] = dict(quadros=[_hex(q) for q in rr['quadros']], bruto=_hex(em.m.ecu.bruto), erro=rr['erro'])
    out['caixa'] = caixa
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--exe', default=os.environ.get('PROGBASE_EXE', ''))
    a = ap.parse_args(argv)
    if not a.exe or not os.path.isfile(a.exe):
        print('ProgBase.exe não encontrado (--exe ou PROGBASE_EXE)', file=sys.stderr)
        return 2
    print(json.dumps(emular(a.exe), ensure_ascii=False, indent=1, sort_keys=True))
    return 0


if __name__ == '__main__':
    sys.exit(main())
