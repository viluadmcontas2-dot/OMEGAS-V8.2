#!/usr/bin/env python3
"""Catálogo de comandos do protocolo AEB/MP48 do ProgBase (D2 PROTOCOLO).

Fonte única (dados curados + verificação) de docs/progbase/protocolo/comandos.json e comandos.md.
- Cada afirmação leva uma marca: OBS-FIO (captura Portmon), OBS-COD (desmontagem do ProgBase.exe 4.2.0.6),
  DERIVADO (cálculo sobre OBS-*), INFERIDO, DESCONHECIDO.
- `verificar_codigo(exe)` reconfere no executável cada endereço/constante citada (instrução por instrução,
  capstone); `contar_no_log(trans)` conta cada comando e cita a primeira linha da captura.
- Só descreve o protocolo de calibração. Nada de licença/criptografia de firmware.

Endereços `RE:0x...` são VAs do ProgBase.exe (sha256 8a2d297c...68ac36f4, imagem base 0x00400000).
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import protocolo_log as plog  # noqa: E402

EXE_SHA256 = '8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4'

# ---------------------------------------------------------------------------------------------------------
# Verificações no executável: (VA, texto exato da instrução desmontada, o que prova)
# ---------------------------------------------------------------------------------------------------------
CHECKS = [
    (0x924A58, 'mov dword ptr [ebp - 0x14], 8', 'GetNumber: opcode base 0x08 (+1 em v2)'),
    (0x924D38, 'mov dword ptr [ebp - 0x14], 9', 'GetNumber+idx: base 0x09 (+1 em v2)'),
    (0x925024, 'mov dword ptr [ebp - 0x14], 0xa', 'GetNumber+linha+coluna: base 0x0A (+1 em v2)'),
    (0x92531C, 'mov dword ptr [ebp - 0x14], 0x28', 'GetVector: base 0x28 (+1 em v2)'),
    (0x925634, 'mov dword ptr [ebp - 0x14], 0x29', 'GetVector+linha: base 0x29 (+1 em v2)'),
    (0x9258D6, 'mov dword ptr [ebp - 0x10], 0x48', 'GetPacket: opcode 0x48'),
    (0x925A13, 'mov dword ptr [ebp - 0x14], 0x68', 'GetByAddress: base 0x68'),
    (0x925BBF, 'mov dword ptr [ebp - 0x14], 0x10', 'SetNumber: base 0x10'),
    (0x926240, 'mov dword ptr [ebp - 0x14], 0x30', 'SetVector: base 0x30'),
    (0x926646, 'mov dword ptr [ebp - 0x14], 0x70', 'SetByAddress: base 0x70'),
    (0x9267EA, 'mov dword ptr [ebp - 0x14], 8', 'GetElementDimension: base 0x08'),
    (0x92438B, 'mov dl, 1', 'Disconnect: código 0x01'),
    (0x923E26, 'mov dl, 2', 'GetIdentification: código 0x02'),
    (0x924080, 'mov dl, 1', 'Connect: nº de bytes de carga = 1'),
    (0x924094, 'mov dl, 0x3a', 'Connect: carga 0x3A'),
    (0x92475C, 'mov dl, 2', 'SetDuty (valor>=0): 2 bytes de carga'),
    (0x924768, 'mov dl, 1', 'SetDuty (valor<0): 1 byte de carga'),
    (0x924772, 'mov dl, 5', 'SetDuty: código 0x05'),
    (0x92482F, 'mov dl, 2', 'SetEcuBaudRate: 2 bytes de carga'),
    (0x924916, 'mov dl, 2', 'SetNewEcuBaudRate: 2 bytes de carga'),
    (0x924920, 'mov dl, 0x19', 'SetNewEcuBaudRate: código 0x19'),
    (0x928196, 'mov dl, 0xb', 'ExitBoot: código 0x0B'),
    (0x928246, 'mov dl, 0xa', 'ExitBootNotForced: código 0x0A'),
    (0x928315, 'mov dl, 9', 'Cancflash: código 0x09'),
    (0x9283C3, 'mov dl, 0x93', 'Progflash: byte 0x93'),
    (0x928483, 'mov dl, 0x85', 'Endflash: byte 0x85'),
    (0x5122B2, 'mov byte ptr [ebp - 0x28], 4', 'Ação AutoCal: 1º byte de carga = 4'),
    (0x5122CE, 'mov edx, 0x24', 'Ação AutoCal: comando 0x24'),
    (0x512332, 'mov byte ptr [ebp - 0x28], 5', 'AutoCal "05": carga 05'),
    (0x92392F, 'cmp edx, -0x6a', 'ReadAck: 0x96 = retry'),
    (0x923986, 'cmp eax, 0x53', 'ReadAck: 0x53 = ACK'),
    (0x9239AB, 'cmp eax, -0x36', 'ReadAck: 0xCA = NAK'),
    (0x920274, 'mov byte ptr [eax + 0x2c9], 0x25', 'ProtocolCommand padrão 0x25'),
    (0x92027E, 'mov dword ptr [edx + 0x258], 0x3e8', 'timeout do 1º byte da resposta = 1000 ms'),
    (0x92028B, 'mov dword ptr [ecx + 0x25c], 0x190', 'pausa após 0x96 = 400 ms'),
    (0x920298, 'mov dword ptr [eax + 0x260], 5', 'máx. de retries = 5'),
    (0x9202EC, 'mov dword ptr [eax + 0x2b8], 0x1f40', 'CancFlashTimeout = 8000 ms'),
    (0x9202F9, 'mov byte ptr [edx + 0x2c8], 0xff', 'ByteExtended = 0xFF'),
    (0x920323, 'mov dword ptr [edx + 0x2dc], 0x7d0', 'espera de boot = 2000 ms'),
    (0x923A57, 'push 0x1f4', 'byte inesperado: Sleep(500) antes de reconectar'),
    (0x9286E2, 'push 0xc8', 'linha de flash: resposta em 200 ms'),
    (0x9283DA, 'push 0x3e8', 'Progflash: resposta em 1000 ms'),
    (0x92849A, 'push 0x3e8', 'Endflash: resposta em 1000 ms'),
]

# Tabela de baud: 10 dwords em 0x00AB6020 (OBS-COD). Corrige o doc 16 do protocolo-landi (que dava divisores).
TABELA_BAUD_VA = 0x00AB6020
TABELA_BAUD = [110, 300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200]

# Corrigidos no VMT: RTTI da classe TAebProtocol (OBS-COD), instância 748 bytes, VMT 0x00AB8C08.
OFFSETS_TAEBPROTOCOL = {
    '0x40': 'ponteiro da porta (TAebBee/TAebTcp); 0 = sem porta',
    '0x44': 'ComFail (máscara de erros; a camada de porta também grava nele)',
    '0x48': 'acumulador do checksum (TX/RX)',
    '0x49': 'buffer de transmissão (até ~260 B)',
    '0x14D': 'buffer de recepção',
    '0x254': 'nº de bytes no buffer de TX',
    '0x258': 'timeout 1º byte da resposta (ms) = 1000',
    '0x25C': 'pausa após 0x96 (ms) = 400',
    '0x260': 'máximo de retries = 5',
    '0x264': 'falhas consecutivas de checksum',
    '0x26C': 'flag "baixando arquivo"',
    '0x28A': 'evento de log (OnLog); != 0 liga as mensagens de depuração',
    '0x294/0x298': 'índice de baud (padrão 5 = 9600) normal/alternativo',
    '0x2B0': 'conectado',
    '0x2B1': 'conectando',
    '0x2B2': 'hardware legado (família 0 e versão < 2)',
    '0x2B4': 'permite trocar o baud do boot',
    '0x2B8': 'timeout do Cancflash (ms) = 8000',
    '0x2BC': 'reconexão automática (padrão 0)',
    '0x2BD': 'pula o handshake do Connect (padrão 0)',
    '0x2BE': 'modo sem reconexão (retry só se ComFail & 0x100)',
    '0x2BF': 'fecha a porta no Disconnect (padrão 1)',
    '0x2C0': 'bit 7 da versão de hardware (flash duplo)',
    '0x2C1': 'usa SetNewEcuBaudRate',
    '0x2C4': 'ProtocolVersion (0, 1 ou 2)',
    '0x2C8': 'ByteExtended = 0xFF',
    '0x2C9': 'ProtocolCommand = 0x25',
    '0x2DC': 'espera de boot (ms) = 2000',
    '0x2E0': 'usa transporte TCP (TAebTcp) em vez de serial',
}

COMFAIL = [
    ('0x0001', 'leitura incompleta/timeout na porta', 'OBS-COD', 'gravado pela porta (RE:0x0091F29D); bloqueia novos envios (máscara 0x29)'),
    ('0x0002', 'NAK (CA) sem o bit 0x10 do 2º byte de status', 'OBS-COD', 'RE:0x00923A04; retry até 5'),
    ('0x0004', 'NAK (CA) com bit 0x10 = parâmetro/comando não suportado; ou endereço >= 0xFF com versão 0', 'OBS-COD', 'sem retry (máscara 0x44); o objeto fica "inválido" mas a leitura não é erro de transporte'),
    ('0x0008', 'eco divergente', 'OBS-COD', 'RE:0x00922FA5/0x00923393; bloqueia envios (0x29); retry'),
    ('0x0010', 'checksum da resposta divergente', 'OBS-COD', 'RE:0x00923B7D (VerifyCk); conta falhas consecutivas em +0x264'),
    ('0x0020', 'endereço >= 0x1FF com versão < 2; ou resposta inválida (0x22 = 0x20|0x02)', 'OBS-COD', 'bloqueia envios (0x29)'),
    ('0x0040', 'reconexão falhou', 'OBS-COD', 'sem retry (máscara 0x44)'),
    ('0x0100', 'pedido de retry (0x96 recebido com checksum certo, 0x102 = 0x100|0x02)', 'OBS-COD', 'única condição de retry no modo +0x2BE'),
    ('0x0400', 'falha ao abrir/configurar a porta', 'OBS-COD', 'gravado pela porta'),
    ('0x8000', 'tamanho da resposta diferente do esperado (0x8020 = 0x8000|0x20)', 'OBS-COD', 'GetNumber/GetVector/GetPacket; retorna falso SEM retry'),
]

RESPOSTAS = {
    'ECO': {
        'o_que_e': 'O adaptador USB-serial devolve cada byte enviado (meia-duplex com eco). O original manda o quadro inteiro '
                   'num único IRP_MJ_WRITE e depois lê N bytes de eco (N = tamanho do quadro) comparando byte a byte.',
        'marca': 'OBS-FIO (quadro inteiro num WRITE; eco no início da resposta) + OBS-COD (RE:0x00923608, laço de comparação '
                 'em 0x009237EB..0x00923830)',
        'tratamento': 'divergência -> ComFail |= 0x08, zera o buffer, falha do comando -> Retry (RE:0x009226E4)',
    },
    'ACK_53': {
        'o_que_e': '`53 n d1..dn ck`, ck = (0x53 + n + soma(d)) & 0xFF. O eco NÃO entra no checksum.',
        'marca': 'OBS-FIO (16 788 respostas, 100 % com checksum certo) + OBS-COD (ReadAck RE:0x00923860, VerifyCk RE:0x00923B30)',
        'tratamento': 'ReadAck só consome o byte 0x53; quem chama lê n, os dados e chama VerifyCk. Falha de checksum -> '
                      'ComFail |= 0x10 -> Retry.',
    },
    'NAK_CA': {
        'o_que_e': '`CA s1 s2 ck`, ck = (0xCA + s1 + s2) & 0xFF. Na captura toda NAK é `CA 01 10 DB` (3 499 casos).',
        'marca': 'OBS-FIO (3 499 casos, todos `CA 01 10 DB`) + OBS-COD (RE:0x009239A1..0x00923A25)',
        'tratamento': 's2 & 0x10 -> ComFail |= 0x04 (parâmetro não suportado, SEM retry; o objeto TAebNumber/Vector marca '
                      'sucesso=1 e válido=0). Sem o bit 0x10 -> ComFail |= 0x02 (retry até 5). Checksum do NAK errado -> 0x22.',
    },
    'RETRY_96': {
        'o_que_e': '`96 b ck` (ECU ocupada).',
        'marca': 'OBS-COD (RE:0x0092392F..0x00923962); 0 ocorrências na captura -> comportamento não observado no fio',
        'tratamento': 'lê 1 byte + checksum; se ck ok: Sleep(+0x25C = 400 ms), ComFail |= 0x102 (retry); senão 0x22.',
    },
    'OUTRO_BYTE': {
        'o_que_e': 'qualquer outro valor no lugar do ACK',
        'marca': 'OBS-COD (RE:0x00923A27..0x00923B1D); 0 ocorrências na captura',
        'tratamento': 'com reconexão automática ligada (+0x2BC) e conectado: Sleep(500), tenta Connect por até 2000 ms, '
                      'Sleep(500); em qualquer caso ComFail |= 0x22 e falha. Sem reconexão automática: só ComFail |= 0x22.',
    },
    'TIMEOUT': {
        'o_que_e': 'nenhum byte em 1000 ms (RC=1200 no SET_TIMEOUTS do Portmon: 1000 + 200 de folga da porta)',
        'marca': 'OBS-FIO (RC 1200 antes de toda leitura do ACK, RC 200 nos bytes seguintes; 2 timeouts reais, ambos em '
                 'Serial1/COM sem ECU) + OBS-COD (+0x258 = 1000; a porta grava ComFail |= 1 se a leitura veio curta)',
        'tratamento': 'ComFail 0x01 -> Retry: até 5 reenvios do mesmo quadro (+0x260); depois, se +0x2BC: pergunta ao usuário '
                      '(retorno 6 = Sim) e tenta Connect; senão marca desconectado e fecha a porta.',
    },
}

RETRY_PSEUDOCODIGO = [
    'Retry(self, &tentativa):                       # RE:0x009226E4',
    '  se self.modo_sem_reconexao(+0x2BE):',
    '      se ComFail & 0x100 e tentativa < +0x260: tentativa += 1; devolve VERDADEIRO',
    '      devolve FALSO',
    '  se ComFail == 0: devolve FALSO                # nada a refazer',
    '  se ComFail & 0x44: devolve FALSO              # NAK "não suportado" ou reconexão falhou: sem retry',
    '  se tentativa < +0x260 (=5): tentativa += 1; devolve VERDADEIRO',
    '  se self.conectando(+0x2B1): devolve FALSO',
    '  # esgotou: o enlace é dado como perdido',
    '  conectado(+0x2B0) = FALSO; fecha a porta',
    '  se self.reconexao_automatica(+0x2BC):          # padrão 0 (desligada)',
    '      mostra diálogo Sim/Não (retorno 6 = Sim)',
    '      se Sim: se Connect() ok: tentativa = 0; devolve VERDADEIRO',
    '              senão ComFail |= 0x40',
    '      conectado = FALSO; fecha a porta',
    '  dispara OnConnect(conectado, ComFail)',
    '  devolve FALSO',
]

# ---------------------------------------------------------------------------------------------------------
# Predicados para contar cada comando na captura (req = bytes enviados)
# ---------------------------------------------------------------------------------------------------------
def _p_sys(b1, n=None):
    def f(req):
        return len(req) >= 3 and req[1] == b1 and (req[0] & 7) + 3 == len(req) and req[0] < 0x08 and (n is None or req[0] == n)
    return f


def _p_op(*ops, tam=None):
    def f(req):
        return len(req) > 1 and req[0] in ops and (tam is None or len(req) == tam)
    return f


PREDICADOS = {
    'wake': lambda r: r == b'\x00',
    'ident': _p_sys(0x02, 0), 'connect': lambda r: r[:3] == b'\x01\x00\x3a' and len(r) == 4,
    'versao': _p_sys(0x25, 0), 'disconnect': _p_sys(0x01, 0), 'cancflash': _p_sys(0x09, 0),
    'exitboot_nf': _p_sys(0x0A, 0), 'exitboot': _p_sys(0x0B, 0), 'setduty_on': _p_sys(0x05, 1),
    'setduty_set': _p_sys(0x05, 2), 'setbaud': _p_sys(0x03, 2), 'setnewbaud': _p_sys(0x19, 2),
    'autocal_acao': lambda r: len(r) == 5 and r[:3] == b'\x02\x24\x04', 'autocal_05': lambda r: r[:3] == b'\x01\x24\x05' and len(r) == 4,
    'cmd_13': _p_sys(0x13, 0), 'cmd_14': _p_sys(0x14, 0), 'cmd_12': _p_sys(0x12, 1), 'cmd_04': _p_sys(0x04, 1),
    'getnumber': _p_op(0x09, tam=4), 'getnumber_idx': _p_op(0x0A, tam=5), 'getnumber_2': _p_op(0x0B, tam=6),
    'getvector': _p_op(0x29, tam=4), 'getvector_row': _p_op(0x2A, tam=5),
    'getpacket': _p_op(0x48, tam=3),
    'getbyaddr': _p_op(0x6A, 0x6C), 'setbyaddr': _p_op(0x73, 0x74, 0x75, 0x76),
    'setnumber': lambda r: len(r) > 1 and 0x10 <= r[0] <= 0x17, 'setvector': lambda r: len(r) > 1 and 0x30 <= r[0] <= 0x37,
    'v01_getnumber': _p_op(0x08), 'v01_getvector': _p_op(0x28),
    'progflash': lambda r: r == b'\x93\x93', 'endflash': lambda r: r == b'\x85\x85',
}

# ---------------------------------------------------------------------------------------------------------
# Catálogo
# ---------------------------------------------------------------------------------------------------------
def _c(id, nome, grupo, quadro, **kw):
    d = dict(id=id, nome=nome, grupo=grupo, quadro=quadro)
    d.update(kw)
    return d


COMANDOS = [
    # ------------------------------------------------------------------ transporte / descoberta
    _c('wake', 'Sonda de descoberta (00 a 38400 baud)', 'handshake',
       '`00` (1 byte; sem checksum e SEM eco)',
       resposta_esperada=['`80` (1 byte, sem eco) = ECU presente', 'timeout (RC 200 ms) = nada na porta'],
       tratamento_original='DESCONHECIDO: a rotina que envia este byte não foi localizada no executável (não está em '
                           'TAebProtocol). Visto 2x: Serial1/COM sem ECU -> timeout (linha 32); Silabser0 -> `80` (linha 70). '
                           'Depois o ProgBase fecha a porta e reabre a 9600.',
       rotina='DESCONHECIDA', marca='OBS-FIO (envio/resposta); DESCONHECIDO (rotina)', predicado='wake',
       omegas={'usa': 'nao', 'nota': 'o app abre direto a 9600 e começa por `00 02 02`'}),
    _c('ident', 'GetIdentification', 'identificacao', '`00 02 02` (b0=0 bytes de carga, b1=0x02, ck)',
       resposta_esperada=['`53 04 b0 b1 b2 b3 ck` (n=4) — observado `53 04 FE 4F 45 0B F4`',
                          '`53 06 ... ck` (n=6: dois bytes extras lidos)', 'NAK/timeout -> Retry'],
       tratamento_original='RE:0x00923DA4: ReadAck; n=ReadByte; com buffer: lê 4 bytes (6 se n==6) em b0..b3(+b4,b5); sem '
                           'buffer: lê e descarta n bytes; VerifyCk; falha -> Retry. O Connect de alto nível (RE:0x00927D80) '
                           'interpreta b1==3 como "ECU no bootloader", b2=família, b3&0x7F=versão de hardware, b3&0x80=flash '
                           'duplo — só em modo boot; em modo aplicação a ECU capturada devolveu FE 4F 45 0B.',
       rotina='RE:0x00923DA4 (GetIdentification)', marca='OBS-FIO (4 ocorrências, 3 ACK + 1 timeout em COM); OBS-COD (rotina)',
       predicado='ident', omegas={'usa': 'sim', 'ref': 'Mp48Protocol.kt CMD_INIT_1'}),
    _c('connect', 'Connect', 'handshake', '`01 00 3A ck` (3B) — b0=1 byte de carga, b1=0x00, carga 0x3A',
       resposta_esperada=['`53 00 53`', 'NAK `CA 01 08` visto só na reconexão de outra captura (doc 17 protocolo-landi)'],
       tratamento_original='RE:0x00923FDC: zera versão (+0x2C4=0); marca conectando(+0x2B1)=1; laço de Retry: envia Connect, '
                           'ReadAck, ReadByte(n), VerifyCk. Sem ComFail -> conectado(+0x2B0)=1 e segue para GetProtocolVersion. '
                           'Falha -> conectado=0 e, se +0x2BF, fecha a porta. Dispara OnConnect.',
       rotina='RE:0x00923FDC (Connect)', marca='OBS-FIO (4x, todas ACK); OBS-COD', predicado='connect',
       omegas={'usa': 'sim', 'ref': 'Mp48Protocol.kt CMD_INIT_2'}),
    _c('versao', 'GetProtocolVersion (ProtocolCommand 0x25)', 'handshake', '`00 25 25` (byte do comando = +0x2C9, padrão 0x25)',
       resposta_esperada=['`53 01 vv ck` — observado `53 01 02 56` (versão 2)'],
       tratamento_original='dentro do Connect (RE:0x00924112..0x009241A6): lê n, aloca n dwords, lê n bytes, VerifyCk; se ok '
                           'ProtocolVersion(+0x2C4) = 1º byte. Define TODO o endereçamento seguinte.',
       rotina='RE:0x00923FDC (trecho GetProtocolVersion)', marca='OBS-FIO (4x, versão 2); OBS-COD', predicado='versao',
       omegas={'usa': 'sim', 'ref': 'Mp48Protocol.kt CMD_IDENTIFY (nome enganoso: é a versão do protocolo)'}),
    _c('disconnect', 'Disconnect', 'handshake', '`00 01 01`',
       resposta_esperada=['`53 00 53`'],
       tratamento_original='RE:0x00924324: UMA tentativa, sem Retry. Depois conectado=0, versão=0, fecha a porta se +0x2BF, '
                           'dispara OnConnect(0).',
       rotina='RE:0x00924324 (Disconnect)', marca='OBS-FIO (3x); OBS-COD', predicado='disconnect',
       omegas={'usa': 'sim', 'ref': 'Mp48Protocol.kt CMD_DISCONNECT'}),
    # ------------------------------------------------------------------ leitura
    _c('getnumber', 'GetNumber (escalar)', 'leitura',
       'v2: `09 lo hi ck`; v0/v1: `08 end ck` (v1: end>=0xFF vira `FF (end+1)&FF`)',
       resposta_esperada=['`53 n valor ck` com n == tamanho do elemento (1, 2 ou 4); valor little-endian, com sinal se Signed',
                          'NAK `CA 01 10 DB` = SerialCode inexistente nesta ECU'],
       tratamento_original='RE:0x00924994. Se n != tamanho: lê e descarta n bytes, VerifyCk, ComFail |= 0x8020 e devolve FALSO '
                           'SEM retry. NAK com bit 0x10 -> ComFail 4 (sem retry; TAebNumber marca válido=0). Demais falhas -> '
                           'Retry (5x).',
       rotina='RE:0x00924994', marca='OBS-FIO (502 pedidos: 460 ACK, 42 NAK); OBS-COD', predicado='getnumber',
       omegas={'usa': 'sim', 'ref': 'AutoCalProtocol.kt READ_SCALAR'}),
    _c('getnumber_idx', 'GetNumber + índice (elemento de vetor)', 'leitura',
       'v2: `0A lo hi idx ck`; v0/v1: `09 end idx ck`', resposta_esperada=['igual ao GetNumber'],
       tratamento_original='RE:0x00924C74 (mesma lógica de tamanho/retry). Atenção: mesmo quadro do GetElementDimension de vetor.',
       rotina='RE:0x00924C74', marca='OBS-FIO (299: 251 ACK, 48 NAK); OBS-COD', predicado='getnumber_idx',
       omegas={'usa': 'sim', 'ref': 'AutoCalProtocol.kt READ_INDEXED'}),
    _c('getnumber_2', 'GetNumber + linha + coluna (elemento de matriz)', 'leitura',
       'v2: `0B lo hi linha coluna ck`; v0/v1: `0A end linha coluna ck` (1º byte extra = linha, 2º = coluna)',
       resposta_esperada=['igual ao GetNumber'],
       tratamento_original='RE:0x00924F60. NOTA: o doc 06 do protocolo-landi chamava os extras de "sub/idx"; no código o 1º '
                           'byte extra é RowIndex (+0x50) e o 2º ColIndex (+0x4C).',
       rotina='RE:0x00924F60', marca='OBS-COD; 0 ocorrências na captura (nenhum objeto lido assim)', predicado='getnumber_2',
       omegas={'usa': 'parcial', 'ref': 'AebProtocolFramesV7.kt getNumber(address,row,column), sem uso comprovado em runtime'}),
    _c('getvector', 'GetVector (vetor inteiro)', 'leitura', 'v2: `29 lo hi ck`; v0/v1: `28 end ck`',
       resposta_esperada=['`53 n d... ck`, n = dimensão x tamanho (ex. 60 = 30 x u16); NAK se SerialCode inexistente'],
       tratamento_original='RE:0x00925258: lê n; se n == 4*cont... (elemento 4/2/1 B) copia; n incompatível -> ComFail 0x8020 sem '
                           'retry. A dimensão esperada vem do objeto (ArrayDimension); a ECU capturada devolve 30 elementos nos '
                           'vetores AutoCal cujo DFM diz 18 (vetores.json: tamanho_confere_com_dfm=false).',
       rotina='RE:0x00925258', marca='OBS-FIO (1 556: 1 502 ACK, 54 NAK); OBS-COD', predicado='getvector',
       omegas={'usa': 'sim', 'ref': 'AutoCalProtocol.kt READ_VECTOR; Mp48Protocol.kt'}),
    _c('getvector_row', 'GetVector + linha (uma linha de matriz)', 'leitura', 'v2: `2A lo hi linha ck`; v0/v1: `29 end linha ck`',
       resposta_esperada=['`53 n d... ck`, n = colunas x tamanho (ex. MAP_K: 12)'],
       tratamento_original='RE:0x00925570 (igual ao GetVector).', rotina='RE:0x00925570',
       marca='OBS-FIO (207: 189 ACK, 18 NAK); OBS-COD', predicado='getvector_row',
       omegas={'usa': 'sim', 'ref': 'Mp48Protocol.kt readKRow'}),
    _c('getpacket', 'GetPacket (canal de dados em tempo real)', 'leitura', '`48 canal ck`',
       resposta_esperada=['`53 n d... ck` — canal 01: 34 bytes (telemetria); canal 0B: 14 bytes (status AutoCal)',
                          'NAK `CA 01 10 DB` no canal 08 (3 334x) = canal não implementado nesta ECU'],
       tratamento_original='RE:0x00925894: n é lido com MOVSX (byte com sinal) -> pacotes > 127 B não seriam suportados; copia '
                           'min(n, buffer); n > buffer -> ComFail 0x8020 sem retry. NAK (0x10) sem retry: o 48 08 repete 3 334x '
                           'porque o relógio da tela o pede sempre.',
       rotina='RE:0x00925894', marca='OBS-FIO (12 765: canal 01 x9 408, 08 x3 334 NAK, 0B x23); OBS-COD', predicado='getpacket',
       omegas={'usa': 'sim', 'ref': 'Mp48Protocol.kt CMD_TELEMETRY/CMD_SECONDARY_STATUS; AutoCalProtocol.kt CMD_NATIVE_STATUS'}),
    _c('getbyaddr', 'GetByAddress (memória por endereço)', 'leitura',
       '`6A flags a_lo a_hi ck` (endereço 16 bits) ou `6C flags a0 a1 a2 a3 ck` (32 bits). flags: bit7=valor 16 bits, bit5=endereço 32 bits',
       resposta_esperada=['`53 n valor ck` (n lido e IGNORADO; valor de 1 ou 2 bytes conforme flags, devolvido com extensão de sinal)'],
       tratamento_original='RE:0x009259C4. Não confere n com o tamanho pedido.', rotina='RE:0x009259C4',
       marca='OBS-COD; 0 ocorrências na captura', predicado='getbyaddr', omegas={'usa': 'nao'}),
    _c('elemdim', 'GetElementDimension', 'leitura',
       'v2: número `09 lo hi`, vetor `0A lo hi 00`, matriz `0B lo hi 00 00` (v0/v1: 08/09/0A)',
       resposta_esperada=['igual ao GetNumber: o tamanho n da resposta é a "dimensão"'],
       tratamento_original='RE:0x00926730: lê n e descarta; devolve -1 (sem porta), -3 (endereço fora da faixa), -2 (falha). '
                           'No fio é INDISTINGUÍVEL de GetNumber+idx(0).',
       rotina='RE:0x00926730', marca='OBS-COD; chamado por RE:0x0045C16D/0x0045C61A/0x0047F4D1 (tela TFormConfig); não separável no fio',
       predicado=None, omegas={'usa': 'nao'}),
    # ------------------------------------------------------------------ escrita
    _c('setnumber', 'SetNumber (escalar / elemento de vetor / elemento de matriz)', 'escrita',
       'op = 0x10 | carga (carga < 7), carga = tamanho do valor (1,2,4) + 1 (v2: byte alto do endereço) + 1 (índice) ou +2 (linha+coluna). '
       'v2 sem índice, 1 B: `12 lo hi val ck`; 2 B: `13`; 4 B: `15`. Com índice: `13 lo hi idx val`; com linha+coluna: `14 lo hi linha coluna val`. '
       'carga >= 7: op `17`, depois `carga+1` (RE:0x00925BF5..0x00925C59)',
       resposta_esperada=['`53 00 53` (n=0, SEM byte de resultado — corrige o doc 07 do protocolo-landi)', 'NAK -> Retry/erro'],
       tratamento_original='RE:0x00925AFC/0x00925D18/0x00925F44: ReadAck; ReadByte (n, ignorado); VerifyCk; falha -> Retry. '
                           'Porta fechada ou sem Connect: devolve VERDADEIRO sem enviar nada. O quadro sozinho é AMBÍGUO: '
                           '`14 lo hi a b c` = (linha,coluna,valor 1 B) OU (idx, valor 2 B); só o objeto desambigua.',
       rotina='RE:0x00925AFC, 0x00925D18, 0x00925F44', marca='OBS-FIO (op 12 x4, 14 x216; todos `53 00 53`); OBS-COD', predicado='setnumber',
       omegas={'usa': 'sim', 'ref': 'AutoCalProtocol.kt frameWriteU8/U16, Mp48Protocol.kt writeKCell'}),
    _c('setvector', 'SetVector (vetor inteiro ou linha)', 'escrita',
       'op = 0x30 | carga (carga < 7), carga = dim x tamanho + 1 (v2) (+1 com linha); `35 lo hi d1 d2 d3 d4 ck` p.ex. FLAG_CONF1. '
       'carga >= 7: op `37`, `carga+1`, endereço, [linha], dados',
       resposta_esperada=['`53 00 53`'],
       tratamento_original='RE:0x0092617C (inteiro) / RE:0x009263B0 (+linha): mesmo fluxo do SetNumber.',
       rotina='RE:0x0092617C, 0x009263B0', marca='OBS-FIO (op 35 x19, todas `53 00 53`); OBS-COD; op 37 sem ocorrência na captura',
       predicado='setvector', omegas={'usa': 'sim', 'ref': 'AutoCalProtocol.kt WRITE_VECTOR_EXTENDED; Mp48Protocol.kt kInsertionMode'}),
    _c('setbyaddr', 'SetByAddress', 'escrita',
       '`73 flags a_lo a_hi val` (8 bits/end16), `74 ... v_lo v_hi`, `75 flags a0..a3 val`, `76 flags a0..a3 v_lo v_hi`',
       resposta_esperada=['`53 00 53`'], tratamento_original='RE:0x009265F8 (mesmo fluxo).', rotina='RE:0x009265F8',
       marca='OBS-COD; 0 ocorrências', predicado='setbyaddr', omegas={'usa': 'nao'}),
    # ------------------------------------------------------------------ comandos de sistema (SendCommand genérico)
    _c('sendcommand', 'SendCommand genérico (b0 = nº de bytes de carga, b1 = código)', 'sistema',
       '`n cmd c1..cn ck` com n<7; n>=7: `07 cmd (n+1) c1..cn ck`',
       resposta_esperada=['`53 m d1..dm ck` (m bytes devolvidos ao chamador)'],
       tratamento_original='RE:0x009244C8: ReadAck; m=ReadByte; copia min(m, buffer); *tamanho=m; VerifyCk; falha -> Retry. '
                           'Usado pela interface (TFormCalibra, TFormConfig, TFormDiagnosi, TFormLandiConnect, TFormSwitch) '
                           'com os códigos 04,0E,10,11,12,13,14,15,21,24,26,27,28 e a variável de TFormSwitch.',
       rotina='RE:0x009244C8', marca='OBS-COD', predicado=None, omegas={'usa': 'parcial'}),
    _c('cmd_12', 'Comando 0x12 (carga 00)', 'sistema', '`01 12 00 13`', resposta_esperada=['`53 00 53`'],
       tratamento_original='SendCommand(0x12). Chamadores: RE:0x0042B8A3, 0x0048031D (TFormConfig/TFormMain), 0x0048640B '
                           '(TFormCalibra.TimerBlinkTimer), 0x004B0998 (TFormConfig.ButtonRicercaParametriClick), 0x004FBC64. '
                           'Na captura aparece sempre imediatamente antes do Disconnect `00 01 01` (linhas 434818 e 1102680). '
                           'Significado: INFERIDO "encerrar modo de edição/liberar a ECU"; DESCONHECIDO o efeito na ECU.',
       rotina='RE:0x009244C8', marca='OBS-FIO (2x); significado INFERIDO', predicado='cmd_12', omegas={'usa': 'nao'}),
    _c('cmd_13', 'Comando 0x13 (sem carga)', 'sistema', '`00 13 13`', resposta_esperada=['`53 00 53`'],
       tratamento_original='SendCommand(0x13). Chamador no código: RE:0x00486B6A (TFormCalibra.FormKeyPress = tecla na tela de '
                           'calibração). Seis ocorrências na captura, em sequência com o polling da tela. Efeito DESCONHECIDO.',
       rotina='RE:0x009244C8', marca='OBS-FIO (6x); OBS-COD (chamador)', predicado='cmd_13', omegas={'usa': 'nao'}),
    _c('cmd_14', 'Comando 0x14 (sem carga) — polling da calibração', 'sistema', '`00 14 14`',
       resposta_esperada=['`53 02 02 00 57` (2 bytes de dados `02 00`, valor constante em 4 693/4 693)'],
       tratamento_original='SendCommand(0x14). Chamador: RE:0x004857ED (TFormCalibra.TimerBlinkTimer = relógio da tela de '
                           'calibração). É o comando mais frequente depois do 48 01. Significado DESCONHECIDO (resposta constante).',
       rotina='RE:0x009244C8', marca='OBS-FIO (4 693x, resposta única); OBS-COD (chamador)', predicado='cmd_14', omegas={'usa': 'nao'}),
    _c('cmd_04', 'Comando 0x04 (carga 54)', 'sistema', '`01 04 54 59`', resposta_esperada=['NAK `CA 01 10 DB` (3/3)'],
       tratamento_original='SendCommand(0x04) (RE:0x0043C761). A ECU capturada NÃO implementa o comando: NAK com bit 0x10 -> '
                           'ComFail 4, sem retry. Enviado uma vez no início de cada sessão longa (linhas 992, 435194, 1103056).',
       rotina='RE:0x009244C8', marca='OBS-FIO (3x NAK); finalidade DESCONHECIDA', predicado='cmd_04', omegas={'usa': 'nao'}),
    _c('setduty', 'SetDuty (teste de saída por PWM)', 'sistema',
       '`02 05 id valor ck` (valor >= 0) ou `01 05 id ck` (valor < 0)', resposta_esperada=['`53 00 53`'],
       tratamento_original='RE:0x00924708 (id em dl, valor em ecx). Nome herdado do doc 12; o significado exato é INFERIDO.',
       rotina='RE:0x00924708', marca='OBS-COD; 0 ocorrências na captura', predicado='setduty_set', omegas={'usa': 'nao'}),
    _c('setbaud', 'SetEcuBaudRate (hardware antigo)', 'sistema', '`02 03 hi lo ck`, valor = 8 000 000 / (baud x 16) em 16 bits, ALTO primeiro',
       resposta_esperada=['`53 00 53`'], tratamento_original='RE:0x009247F0; usado no flash (RE:0x00928E78) e em RE:0x00927D80.',
       rotina='RE:0x009247F0', marca='OBS-COD; 0 ocorrências', predicado='setbaud', omegas={'usa': 'nao'}),
    _c('setnewbaud', 'SetNewEcuBaudRate (hardware novo)', 'sistema', '`02 19 hi lo ck`, valor = baud / 2 em 16 bits, ALTO primeiro; baud em [1200, 115200]',
       resposta_esperada=['`53 00 53`'], tratamento_original='RE:0x009248B0 (recusa baud fora da faixa sem enviar).',
       rotina='RE:0x009248B0', marca='OBS-COD; 0 ocorrências', predicado='setnewbaud', omegas={'usa': 'nao'}),
    _c('autocal_acao', 'Ação AutoCal (02 24 04 modo)', 'acao', '`02 24 04 modo ck` — modo 1=gasolina, 2=GNV, 4=Reset all, 8=automatch manual',
       resposta_esperada=['`53 00 53` (a ECU executa na hora; o efeito aparece nos vetores AutoCal lidos em seguida)'],
       tratamento_original='RE:0x00512280 (portão IsConnect RE:0x00512264; devolve 0 ok, 1 falha de envio, 2 sem conexão). '
                           'Chamado por CommonAction RE:0x00517568, que antes pede confirmação ao usuário e depois espera 1 s '
                           '(Sleep 1000) e recarrega a tela. Só o modo 04 foi visto no fio (linha 69944); 01/02/08 vêm do código/doc 17.',
       rotina='RE:0x00512280', marca='OBS-FIO (modo 04 x1); OBS-COD (envio e portão); modos 01/02/08 INFERIDO', predicado='autocal_acao',
       omegas={'usa': 'sim', 'ref': 'AutoCalProtocol.kt manualAction'}),
    _c('autocal_05', 'Ação AutoCal 01 24 05', 'acao', '`01 24 05 2A`', resposta_esperada=['`53 00 53` (esperado)'],
       tratamento_original='RE:0x00512300, chamado por RE:0x00512858 depois de gravar dois vetores de flags por ponto '
                           '(TAutoCalSettings). Nunca visto no fio.', rotina='RE:0x00512300',
       marca='OBS-COD; 0 ocorrências; efeito DESCONHECIDO', predicado='autocal_05', omegas={'usa': 'nao'}),
    # ------------------------------------------------------------------ flash
    _c('cancflash', 'Cancflash (apaga flash)', 'flash', '`00 09 09`', resposta_esperada=['`53 m st ck` (st = status com sinal)'],
       tratamento_original='RE:0x009282B0: troca o timeout de leitura para +0x2B8 (8000 ms), envia, lê m e st, VerifyCk, restaura; '
                           'falha de todas as tentativas -> -2. Chamado só pelo download de firmware (RE:0x00929315, 0x0092A00E).',
       rotina='RE:0x009282B0', marca='OBS-COD; 0 ocorrências', predicado='cancflash', omegas={'usa': 'nao'}),
    _c('exitboot_nf', 'ExitBootNotForced', 'flash', '`00 0A 0A`', resposta_esperada=['`53 m st ck`'],
       tratamento_original='RE:0x00928200.', rotina='RE:0x00928200', marca='OBS-COD; 0 ocorrências', predicado='exitboot_nf', omegas={'usa': 'nao'}),
    _c('exitboot', 'ExitBoot (forçado)', 'flash', '`00 0B 0B`', resposta_esperada=['`53 m st ck`'],
       tratamento_original='RE:0x00928150; também via SendCommand(0x0B) em RE:0x0092929A (limpeza após falha no download).',
       rotina='RE:0x00928150', marca='OBS-COD; 0 ocorrências', predicado='exitboot', omegas={'usa': 'nao'}),
    _c('progflash', 'Progflash (entra em programação)', 'flash', '`93 93` (1 byte + ck)',
       resposta_esperada=['`A7 A7` (byte A7 e o mesmo valor como checksum)'],
       tratamento_original='RE:0x0092839C: 4 tentativas; lê 1 byte em 1000 ms, depois outro em 0 ms; exige igual e == 0xA7.',
       rotina='RE:0x0092839C', marca='OBS-COD; 0 ocorrências', predicado='progflash', omegas={'usa': 'nao'}),
    _c('endflash', 'Endflash', 'flash', '`85 85`', resposta_esperada=['`53 53`'],
       tratamento_original='RE:0x0092845C: 4 tentativas; ok se o byte lido == 0x53; fecha a porta e marca desconectado.',
       rotina='RE:0x0092845C', marca='OBS-COD; 0 ocorrências', predicado='endflash', omegas={'usa': 'nao'}),
    _c('flash_linha', 'Linha de firmware (S-record em texto -> binário)', 'flash',
       '`53 31 cnt addr_hi addr_lo dados.. srec_ck` (a linha ASCII do arquivo vira bytes; 1ª letra e tipo vão como ASCII, o resto '
       'em pares hexa). EOF: `53 39 03 FF FF FE`. Sem checksum adicional do protocolo.',
       resposta_esperada=['`A7 A7` (linha aceita) ou `53 53`'],
       tratamento_original='RE:0x009285A8 (linhas) e RE:0x00928968 (fim): até 8 tentativas por linha, resposta em 200 ms; '
                           'falha -> restaura o baud, Disconnect, devolve -1000. Fluxo completo em RE:0x00928E78 (ScaricaFile).',
       rotina='RE:0x009285A8, 0x00928968, 0x00928E78', marca='OBS-COD; 0 ocorrências (a captura não tem flash)',
       predicado=None, omegas={'usa': 'nao'}),
    # ------------------------------------------------------------------ v0/v1
    _c('v01_getnumber', 'GetNumber v0/v1 (endereço de 1 byte)', 'leitura', '`08 end ck` / v1 estendido `08 FF (end+1) ck`',
       resposta_esperada=['igual ao GetNumber v2'], tratamento_original='mesmas rotinas; só o formato do endereço muda (RE:0x00924A58 '
       'base 8; +1 se versão >= 2). Limites: end >= 0xFF com versão 0 -> ComFail 4; end >= 0x1FF com versão < 2 -> ComFail 0x20. '
       'Corrige o doc 04: o limiar é 0xFF (não 0xFE) e não há colisão FE/1FE.',
       rotina='RE:0x00924994', marca='OBS-COD; 0 ocorrências (a ECU capturada é versão 2)', predicado='v01_getnumber', omegas={'usa': 'nao'}),
    _c('v01_getvector', 'GetVector v0/v1', 'leitura', '`28 end ck`', resposta_esperada=['igual ao GetVector v2'],
       tratamento_original='RE:0x00925258 com versão < 2.', rotina='RE:0x00925258', marca='OBS-COD; 0 ocorrências',
       predicado='v01_getvector', omegas={'usa': 'nao'}),
]

# Chamadores de SendCommand (código -> lista de (rotina, dono RTTI se houver)) — OBS-COD, tabela estática
SENDCOMMAND_CHAMADORES = [
    (0x04, '0x0043C761', 'função grande RE:0x00439E94 (sem nome RTTI)'),
    (0x0B, '0x0092929A', 'ScaricaFile RE:0x00928E78'),
    (0x0E, '0x00485DEC', 'TFormCalibra.TimerBlinkTimer'), (0x0E, '0x00486A5D', 'TFormCalibra.FormKeyPress'),
    (0x10, '0x00486AAD', 'TFormCalibra.FormKeyPress'),
    (0x11, '0x0043A265', 'função grande RE:0x00439E94'),
    (0x12, '0x0042B8A3', 'RE:0x0042B834'), (0x12, '0x0048031D', 'RE:0x00439E94'),
    (0x12, '0x0048640B', 'TFormCalibra.TimerBlinkTimer'), (0x12, '0x004B0998', 'TFormConfig.ButtonRicercaParametriClick'),
    (0x12, '0x004FBC64', 'RE:0x004FBA40'),
    (0x13, '0x00486B6A', 'TFormCalibra.FormKeyPress'),
    (0x14, '0x004857ED', 'TFormCalibra.TimerBlinkTimer'),
    (0x15, '0x004846EA', 'RE:0x00482F34'), (0x15, '0x004858AD', 'TFormCalibra.TimerBlinkTimer'),
    (0x15, '0x00485EAB', 'TFormCalibra.TimerBlinkTimer'), (0x15, '0x00486C65', 'TFormCalibra.FormKeyPress'),
    (0x21, '0x004D7952', 'TFormDiagnosi.PanelTestOnClick'), (0x21, '0x004D7A36', 'TFormDiagnosi.PanelTestOnClick'),
    (0x21, '0x004D7CF0', 'TFormDiagnosi.PanelTestOffClick'),
    (0x24, '0x005122D6', 'AutoCal: ação (RE:0x00512280)'), (0x24, '0x00512350', 'AutoCal: 01 24 05 (RE:0x00512300)'),
    (0x26, '0x00434610', 'RE:0x0043457C'),
    (0x27, '0x004A5E75', 'TFormConfig.TimerDatiTimer'), (0x27, '0x004A628C', 'TFormConfig.CheckInjAcquisitionClick'),
    (0x27, '0x004A6319', 'TFormConfig.ButtonSequenceAcquisitionClick'),
    (0x27, '0x004A6548', 'TFormConfig.EditRpmTachimetroKeyPress'), (0x27, '0x004C940E', 'TFormConfig.ButtonSwitchSettingsClick'),
    (0x27, '0x004C94D3', 'TFormConfig.ButtonSwitchSettingsClick'),
    (0x28, '0x004EDA8E', 'TFormLandiConnect.ButtonResetClick'),
    ('var', '0x00412916..0x00413094', 'TFormSwitch (código vem da propriedade +0x3D8; ComboBuzzerKeyPress)'),
]

# ---------------------------------------------------------------------------------------------------------
# Backlog: o que o original envia e o OMEGAS não usa. classe: A = leitura/diagnóstico sem risco; B = escrita de
# calibração; C = atuadores/teste de bancada; D = firmware/boot/baud (NUNCA no app); E = legado/sem valor.
# prioridade: alta / media / baixa / nenhuma. Tudo é recomendação de projeto (INFERIDO), não fato do protocolo.
# ---------------------------------------------------------------------------------------------------------
BACKLOG = {
    'wake': dict(classe='A', prioridade='baixa', risco='nenhum',
                 recomendacao='Só serviria para descobrir qual adaptador tem ECU. O app já abre o USB pelo VID/PID; não precisa.'),
    'getnumber_2': dict(classe='A', prioridade='baixa', risco='nenhum',
                        recomendacao='Ler 1 célula de matriz sem baixar a linha. Otimização; o app lê a linha `2A`.'),
    'getbyaddr': dict(classe='C', prioridade='baixa', risco='medio',
                      recomendacao='Leitura de memória por endereço físico. Útil só para engenharia; não expor ao usuário.'),
    'setbyaddr': dict(classe='D', prioridade='nenhuma', risco='alto',
                      recomendacao='Escrita em endereço físico arbitrário. NUNCA expor.'),
    'elemdim': dict(classe='A', prioridade='baixa', risco='nenhum',
                    recomendacao='Descobrir o tamanho de um parâmetro. No fio é um GetNumber+idx(0); o app já conhece os tamanhos.'),
    'cmd_12': dict(classe='B', prioridade='media', risco='baixo',
                   recomendacao='O original manda `01 12 00` antes de todo Disconnect. Efeito desconhecido; capturar uma sessão '
                                'só com esse comando (e comparar `48 01`/`48 0B` antes e depois) antes de decidir.'),
    'cmd_13': dict(classe='B', prioridade='baixa', risco='desconhecido',
                   recomendacao='Tecla na tela de Calibração (Taratura). Só se o app ganhar a tela de Taratura.'),
    'cmd_14': dict(classe='A', prioridade='media', risco='nenhum',
                   recomendacao='Polling da tela de Calibração (resposta constante `02 00` em 4 693/4 693). Pode ser o "estado do '
                                'modo taratura"; capturar com o modo ligado/desligado para ver se o valor muda.'),
    'cmd_04': dict(classe='E', prioridade='nenhuma', risco='nenhum',
                   recomendacao='A ECU capturada responde NAK (não suportado). Ignorar.'),
    'setduty': dict(classe='C', prioridade='baixa', risco='medio',
                    recomendacao='Teste de saída por PWM (aciona atuador). Só com tela de diagnóstico dedicada e confirmação.'),
    'setbaud': dict(classe='D', prioridade='nenhuma', risco='alto', recomendacao='Troca de baud para flash. Nunca no app.'),
    'setnewbaud': dict(classe='D', prioridade='nenhuma', risco='alto', recomendacao='Troca de baud para flash. Nunca no app.'),
    'autocal_05': dict(classe='B', prioridade='baixa', risco='desconhecido',
                       recomendacao='Enviado após gravar dois vetores de flags por ponto; nunca visto. Capturar antes de usar.'),
    'cancflash': dict(classe='D', prioridade='nenhuma', risco='alto', recomendacao='Apaga a flash. Nunca no app.'),
    'exitboot_nf': dict(classe='D', prioridade='nenhuma', risco='alto', recomendacao='Sai do bootloader. Nunca no app.'),
    'exitboot': dict(classe='D', prioridade='nenhuma', risco='alto', recomendacao='Sai do bootloader (forçado). Nunca no app.'),
    'progflash': dict(classe='D', prioridade='nenhuma', risco='alto', recomendacao='Entra em programação de flash. Nunca no app.'),
    'endflash': dict(classe='D', prioridade='nenhuma', risco='alto', recomendacao='Encerra programação de flash. Nunca no app.'),
    'flash_linha': dict(classe='D', prioridade='nenhuma', risco='alto', recomendacao='Grava firmware. Nunca no app.'),
    'v01_getnumber': dict(classe='E', prioridade='nenhuma', risco='nenhum', recomendacao='ECUs de protocolo 0/1; a capturada é v2.'),
    'v01_getvector': dict(classe='E', prioridade='nenhuma', risco='nenhum', recomendacao='ECUs de protocolo 0/1; a capturada é v2.'),
    'sendcommand': dict(classe='B', prioridade='media', risco='variavel',
                        recomendacao='Os códigos 0E,10,11,13,14,15,21,26,27,28 só aparecem nas telas Calibração/Diagnóstico/Config/'
                                     'LandiConnect. (pelo NOME do evento, INFERIDO) 0x21 = teste de saída ligar/desligar (TFormDiagnosi.PanelTestOn/OffClick); 0x27 = '
                                     'aquisição de injetor/sequência e tacômetro (TFormConfig.CheckInjAcquisitionClick, ButtonSequenceAcquisitionClick, '
                                     'EditRpmTachimetroKeyPress); 0x28 = botão Reset (TFormLandiConnect.ButtonResetClick). '
                                     'Nada disso está no log; capturar cada botão antes de implementar.'),
}

# Comportamentos de enlace do original que o OMEGAS trata diferente (comparação direta, com referência no app)
ENLACE_ORIGINAL_X_OMEGAS = [
    dict(item='Retries', original='até 5 reenvios do mesmo quadro (6 tentativas), depois diálogo de reconexão (se ligado)',
         omegas='sem retry cego em NAK não-retentável; reconecta pelo ciclo de vida USB (ResponseDrivenEcuEngine.kt, UsbRecoveryPolicy.kt)',
         ref='ResponseDrivenEcuEngine.kt:25-26', classe='diferente-de-proposito'),
    dict(item='NAK `CA 01 10` / `CA 01 08`', original='testa o bit 0x10 do 2º byte de status (qualquer outro valor com bit 0x10 = não suportado; sem o bit = retry)',
         omegas='compara igualdade com 0x10 e 0x08; outros códigos viram EXTENDED_UNKNOWN',
         ref='UsbProtocolReply.kt:13-23', classe='lacuna-pequena'),
    dict(item='Purge antes de cada comando', original='IOCTL_SERIAL_PURGE (TX+RX) antes de TODO WRITE (20 290/20 290)',
         omegas='purge antes de cada transação (purgeBefore=true por padrão)', ref='UsbSerialManager.kt:353,372', classe='igual'),
    dict(item='Eco', original='compara o eco byte a byte; divergência -> ComFail 8 -> retry',
         omegas='readExact(request.size) e compara o eco inteiro', ref='UsbSerialManager.kt:375-377', classe='igual'),
    dict(item='Timeout do 1º byte da resposta', original='1000 ms (+200 do driver)', omegas='timeoutMs padrão 1800 ms',
         ref='UsbSerialManager.kt:352', classe='diferente-de-proposito'),
    dict(item='Tamanho máximo de resposta', original='GetPacket lê o tamanho como byte COM SINAL (<= 127 B); Get* confere n com o tamanho do objeto',
         omegas='rejeita n > 192', ref='UsbSerialManager.kt:385', classe='lacuna-pequena'),
    dict(item='Resposta `96` (ECU ocupada)', original='espera 400 ms e reenvia (até 5x)', omegas='sem tratamento específico',
         ref='UsbProtocolReply.kt', classe='lacuna-nao-observada-no-fio'),
    dict(item='Sequência de encerramento', original='`01 12 00` e depois `00 01 01` (Disconnect, uma tentativa, sem retry)',
         omegas='só Mp48Protocol.CMD_DISCONNECT', ref='Mp48Protocol.kt:18', classe='lacuna-pequena'),
    dict(item='Escrita com porta fechada', original='SetNumber/SetVector devolvem VERDADEIRO sem enviar nada',
         omegas='escrita falha explicitamente; gravado só após ACK + readback (AGENTS.md)', ref='AGENTS.md invariantes',
         classe='diferente-de-proposito'),
    dict(item='Ordem do handshake', original='ident `00 02` -> Connect `01 00 3A` -> versão `00 25` -> (REGISTRO_EE `09 02 00`) -> ident de novo',
         omegas='CMD_INIT_1 (`00 02 02`), CMD_INIT_2 (`01 00 3A`), depois leitura de versão', ref='ResponseDrivenEcuEngine.kt:341-369',
         classe='igual'),
]


def desmontar_em(exe_bytes, pe, va):
    """Texto 'mnem ops' da instrução em `va` (capstone x86-32)."""
    import capstone
    base = pe.OPTIONAL_HEADER.ImageBase
    for s in pe.sections:
        ini = base + s.VirtualAddress
        if ini <= va < ini + max(s.Misc_VirtualSize, s.SizeOfRawData):
            off = s.PointerToRawData + (va - ini)
            md = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)
            for i in md.disasm(exe_bytes[off:off + 16], va):
                return '%s %s' % (i.mnemonic, i.op_str)
            return None
    return None


def verificar_codigo(exe):
    """Reconfere cada CHECK no executável. Devolve lista de falhas (vazia = tudo confere)."""
    import hashlib
    import pefile
    b = open(exe, 'rb').read()
    falhas = []
    if hashlib.sha256(b).hexdigest() != EXE_SHA256:
        falhas.append(('sha256', 'executável diferente do analisado'))
        return falhas
    pe = pefile.PE(exe, fast_load=True)
    for va, esperado, prova in CHECKS:
        got = desmontar_em(b, pe, va)
        if got != esperado:
            falhas.append((hex(va), 'esperado %r obtido %r (%s)' % (esperado, got, prova)))
    base = pe.OPTIONAL_HEADER.ImageBase
    for s in pe.sections:
        ini = base + s.VirtualAddress
        if ini <= TABELA_BAUD_VA < ini + s.Misc_VirtualSize:
            off = s.PointerToRawData + TABELA_BAUD_VA - ini
            import struct
            tab = list(struct.unpack('<10I', b[off:off + 40]))
            if tab != TABELA_BAUD:
                falhas.append((hex(TABELA_BAUD_VA), 'tabela de baud %r' % tab))
    return falhas


def contar_no_log(trans):
    """{id: {total, classes, primeira_linha, exemplo}} para os comandos com predicado."""
    out = {}
    for c in COMANDOS:
        nome = c.get('predicado')
        if not nome:
            continue
        pred = PREDICADOS[nome]
        n, cls, prim, ex = 0, {}, None, None
        for t in trans:
            r = t['req']
            try:
                ok = pred(r)
            except IndexError:
                ok = False
            if not ok:
                continue
            n += 1
            k = plog.classificar(t)[0]
            cls[k] = cls.get(k, 0) + 1
            if prim is None:
                prim = t['line']
                ex = dict(req=r.hex(' ').upper(), resp=t['resp'].hex(' ').upper()[:90])
        out[c['id']] = dict(total=n, classes=dict(sorted(cls.items())), primeira_linha=prim, exemplo=ex)
    return out


def montar(log=None):
    contagem = contar_no_log(plog.transacoes(log)) if log else None
    cmds = []
    for c in COMANDOS:
        d = dict(c)
        d.pop('predicado', None)
        if c['id'] in BACKLOG:
            d['backlog'] = BACKLOG[c['id']]
        if contagem is not None and c['id'] in contagem:
            d['no_log'] = contagem[c['id']]
        cmds.append(d)
    return {
        'versao_esquema': 1, 'exe_sha256': EXE_SHA256, 'log_sha256': plog.sha256_arquivo(log) if log else None,
        'framing': {
            'quadro_tx': 'b0 b1 [carga] ck — ck = soma mod 256 de TODOS os bytes anteriores do quadro (inclui b0). '
                         'b0 = classe | n, n (3 bits) = nº de bytes entre o 2º byte e o ck; n==7 => estendido (byte seguinte = n+1).',
            'quadro_rx': 'eco do quadro + ACK + n + dados + ck; ck = (ACK + n + soma(dados)) mod 256 — o eco não entra.',
            'comprimento_do_quadro_tx': '(b0 & 7) + 3 para b0 & 7 < 7 (confere com 100 % dos 20 290 pedidos da captura)',
            'ordem_dos_bytes': 'little-endian em endereços e valores; exceção: SetEcuBaudRate/SetNewEcuBaudRate enviam ALTO primeiro',
            'marca': 'OBS-FIO + OBS-COD',
        },
        'timeouts': {
            'primeiro_byte_da_resposta_ms': 1000, 'bytes_seguintes_ms': 0, 'pausa_apos_96_ms': 400, 'retries': 5,
            'cancflash_ms': 8000, 'linha_de_flash_ms': 200, 'progflash_endflash_ms': 1000, 'espera_de_boot_ms': 2000,
            'portmon_rc_ms': {'ack': 1200, 'demais_bytes': 200},
            'marca': 'OBS-COD (construtor RE:0x00920128) + OBS-FIO (SET_TIMEOUTS RC)',
        },
        'tabela_baud': {'va': hex(TABELA_BAUD_VA), 'baud_por_indice': TABELA_BAUD, 'indice_padrao': 5,
                        'indice_flash': '6 (7 se a porta for TAebBee)', 'marca': 'OBS-COD'},
        'offsets_taebprotocol': OFFSETS_TAEBPROTOCOL,
        'comfail': [dict(mascara=m, significado=s, marca=k, onde=o) for m, s, k, o in COMFAIL],
        'respostas': RESPOSTAS,
        'retry': RETRY_PSEUDOCODIGO,
        'enlace_original_x_omegas': ENLACE_ORIGINAL_X_OMEGAS,
        'sendcommand_chamadores': [dict(codigo=('0x%02X' % c) if isinstance(c, int) else c, endereco=a, dono=d)
                                   for c, a, d in SENDCOMMAND_CHAMADORES],
        'comandos': cmds,
    }


def para_markdown(cat):
    L = ['# Comandos do protocolo AEB/MP48 (gerado — não editar à mão)', '']
    L.append('Gerado por `tools/progbase/protocolo_comandos.py`. Executável analisado: sha256 `%s`%s.' % (
        cat['exe_sha256'], (' · captura Portmon sha256 `%s`' % cat['log_sha256']) if cat['log_sha256'] else ''))
    L.append('')
    f = cat['framing']
    L += ['## Quadro e checksum (%s)' % f['marca'], '', '- TX: ' + f['quadro_tx'], '- RX: ' + f['quadro_rx'],
          '- Tamanho do quadro TX: ' + f['comprimento_do_quadro_tx'], '- Bytes: ' + f['ordem_dos_bytes'], '']
    t = cat['timeouts']
    L += ['## Timeouts e contagens (%s)' % t['marca'], '',
          '- 1º byte da resposta: **%d ms** (Portmon: RC=%d); bytes seguintes: %d ms (RC=%d).' % (
              t['primeiro_byte_da_resposta_ms'], t['portmon_rc_ms']['ack'], t['bytes_seguintes_ms'], t['portmon_rc_ms']['demais_bytes']),
          '- Retries por comando: **%d** (6 tentativas no total); pausa após `96`: %d ms; Cancflash: %d ms; linha de flash: %d ms; '
          'Progflash/Endflash: %d ms; espera de boot: %d ms.' % (t['retries'], t['pausa_apos_96_ms'], t['cancflash_ms'],
                                                                 t['linha_de_flash_ms'], t['progflash_endflash_ms'], t['espera_de_boot_ms']),
          '- Tabela de baud (índice -> baud): %s (padrão = índice 5 = 9600).' % ', '.join(
              '%d=%d' % (i, b) for i, b in enumerate(cat['tabela_baud']['baud_por_indice'])), '']
    L += ['## Respostas e como o original as trata', '']
    for k, v in cat['respostas'].items():
        L += ['### %s' % k, '', '- O que é: ' + v['o_que_e'], '- Marca: ' + v['marca'], '- Tratamento: ' + v['tratamento'], '']
    L += ['### Retry (RE:0x009226E4) — pseudocódigo', '', '```'] + cat['retry'] + ['```', '']
    L += ['## ComFail (máscara de erros em +0x44)', '', '| bit | significado | marca | onde / efeito |', '|---|---|---|---|']
    for c in cat['comfail']:
        L.append('| `%s` | %s | %s | %s |' % (c['mascara'], c['significado'], c['marca'], c['onde']))
    L += ['', '## Comandos', '']
    L.append('| id | comando | quadro | resposta esperada | log | OMEGAS |')
    L.append('|---|---|---|---|---|---|')
    for c in cat['comandos']:
        lg = c.get('no_log')
        lgt = '—' if not lg else ('0' if lg['total'] == 0 else '%d (linha %d)' % (lg['total'], lg['primeira_linha']))
        L.append('| `%s` | %s | %s | %s | %s | %s |' % (c['id'], c['nome'], c['quadro'], '<br>'.join(c['resposta_esperada']),
                                                        lgt, c['omegas']['usa']))
    L.append('')
    for c in cat['comandos']:
        L += ['### `%s` — %s' % (c['id'], c['nome']), '', '- Grupo: %s · Rotina: %s' % (c['grupo'], c['rotina']),
              '- Marca: ' + c['marca'], '- Tratamento no original: ' + c['tratamento_original']]
        lg = c.get('no_log')
        if lg and lg['total']:
            L.append('- Captura: %d ocorrências %s; primeira na linha %d: `%s` -> `%s`' % (
                lg['total'], lg['classes'], lg['primeira_linha'], lg['exemplo']['req'], lg['exemplo']['resp']))
        L.append('- OMEGAS: %s%s' % (c['omegas']['usa'], (' — ' + c['omegas'].get('ref', c['omegas'].get('nota', ''))) if
                                     (c['omegas'].get('ref') or c['omegas'].get('nota')) else ''))
        L.append('')
    L += ['## Chamadores de SendCommand (código -> dono)', '', '| código | endereço | dono |', '|---|---|---|']
    for s in cat['sendcommand_chamadores']:
        L.append('| %s | RE:%s | %s |' % (s['codigo'], s['endereco'], s['dono']))
    L += ['', '## Campos da instância TAebProtocol (OBS-COD)', '', '| offset | significado |', '|---|---|']
    for k, v in cat['offsets_taebprotocol'].items():
        L.append('| %s | %s |' % (k, v))
    return '\n'.join(L) + '\n'


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument('--exe')
    ap.add_argument('--log')
    ap.add_argument('--json')
    ap.add_argument('--md')
    a = ap.parse_args(argv)
    if a.exe:
        f = verificar_codigo(a.exe)
        if f:
            for x in f:
                print('FALHA', x)
            return 1
        print('executável: %d verificações conferem' % len(CHECKS))
    cat = montar(a.log)
    if a.json:
        with open(a.json, 'w', encoding='utf-8') as fh:
            json.dump(cat, fh, ensure_ascii=False, indent=1)
            fh.write('\n')
    if a.md:
        with open(a.md, 'w', encoding='utf-8') as fh:
            fh.write(para_markdown(cat))
    print('comandos:', len(cat['comandos']))
    return 0


if __name__ == '__main__':
    sys.exit(main())
