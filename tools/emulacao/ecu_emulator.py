#!/usr/bin/env python3
"""ECU emulada (MP48) montada só por observação de uma captura Portmon do ProgBase.

Regras (marcas em docs/evidence/2026-10-18-ecu-emulada.md):
- OBS-FIO: resposta = eco do pedido + ACK (53 ok / CA nak) + n + n bytes + checksum,
  checksum = (ACK + n + dados) mod 256, sem o eco. Pedido = op + parâmetros + (soma mod 256).
- Leituras (09, 0A, 29, 2A, 48, sistema 00/01/02 vistos): o estado de cada pedido é a ÚLTIMA
  resposta vista na captura até o instante, mais os efeitos das escritas recebidas depois dela.
- Escritas (10..17 SetNumber, 30..37 SetVector): atualizam o estado e respondem 53 00 53
  (OBS-FIO para 12/14/35; DERIVADO de 07-COMANDOS-ESCRITA.md para as outras formas).
- AutoCal 02 24 04 <modo>: ACK 53 00 53 (visto uma vez); o efeito no firmware é NÃO MODELADO.
- Pedido nunca visto / checksum do pedido errado: NÃO MODELADO -> só o eco (nenhum ACK inventado).
Só stdlib. CLI: ecu_emulator.py --captura LOG [--porta 5555] (servidor TCP em 127.0.0.1).
"""
import argparse
import re
import socket
import socketserver
import sys

ACK, NAK = 0x53, 0xCA
NAO_MODELADO = 'NAO_MODELADO'

_RE_WRITE = re.compile(r'IRP_MJ_WRITE\s+(\S+)\s+Length \d+: ([0-9A-Fa-f ]+)')
_RE_LIDO = re.compile(r'SUCCESS\s+Length \d+: ([0-9A-Fa-f ]+)')


def soma(bs):
    return sum(bs) & 0xFF


def tamanho_pedido(buf):
    """Comprimento do quadro a partir do opcode (DERIVADO: total = (op & 7) + 3; se op & 7 == 7, o byte
    seguinte é o comprimento estendido). Confere com todos os pedidos da captura. None = faltam bytes."""
    if not buf:
        return None
    low = buf[0] & 7
    if low == 7:
        if len(buf) < 2:
            return None
        return buf[1] + 3
    return low + 3


def ler_captura(caminho, dispositivo='Silabser0'):
    """Lista de (pedido, resposta_bruta) na ordem do fio. resposta_bruta inclui o eco."""
    trans, cur = [], None
    with open(caminho, encoding='latin1') as fh:
        for linha in fh:
            m = _RE_WRITE.search(linha)
            if m:
                if m.group(1) == dispositivo:
                    cur = [bytes.fromhex(m.group(2)), bytearray()]
                    trans.append(cur)
                else:
                    cur = None
                continue
            m = _RE_LIDO.search(linha)
            if m and cur is not None and 'IRP_MJ' not in linha:
                cur[1] += bytes.fromhex(m.group(1))
    return [(p, bytes(r)) for p, r in trans]


def decodificar_resposta(pedido, bruta):
    """('quadro', ack, dados) quando bem formada e com checksum certo; senão ('bruta', bytes)."""
    if bruta[:len(pedido)] == pedido:
        corpo = bruta[len(pedido):]
        if len(corpo) >= 3 and corpo[0] in (ACK, NAK):
            n = corpo[1]
            if len(corpo) == n + 3 and soma(corpo[:-1]) == corpo[-1]:
                return ('quadro', corpo[0], bytes(corpo[2:2 + n]))
    return ('bruta', bytes(bruta))


def montar_quadro(ack, dados):
    corpo = bytes([ack, len(dados)]) + bytes(dados)
    return corpo + bytes([soma(corpo)])


def pedido(op, *params):
    corpo = bytes([op, *params])
    return corpo + bytes([soma(corpo)])


def _e_escrita(p):
    return 0x10 <= p[0] <= 0x17 or 0x30 <= p[0] <= 0x37


class EcuEmulada:
    def __init__(self):
        self.estado = {}           # pedido (bytes) -> ('quadro', ack, dados) | ('bruta', bytes)
        self.transacoes = []
        self.ultimo_rotulo = None  # OBS-FIO | DERIVADO | NAO_MODELADO (da última resposta)
        self.efeitos_nao_modelados = []

    # ---------- carga ----------
    def carregar_captura(self, caminho, ate=None):
        """Lê a captura e reconstrói o estado até a transação `ate` (exclusivo; None = toda)."""
        self.transacoes = ler_captura(caminho)
        fim = len(self.transacoes) if ate is None else ate
        for p, r in self.transacoes[:fim]:
            self.observar(p, r)
        return self.estado

    def regenerar(self, transacoes=None):
        """Regra do instante: antes do pedido i, o estado tem todas as transações < i; numa leitura, o
        valor que a ECU tem no instante i é o que ela respondeu em i (observar), e o quadro é remontado
        a partir de (ACK, dados) com checksum recalculado. Escritas NÃO são consultadas: a resposta sai
        da regra (53 00 53). Devolve (pares_ok, pares_falha), conjuntos de (pedido, resposta)."""
        trans = self.transacoes if transacoes is None else transacoes
        ecu, ok, falha = EcuEmulada(), set(), set()
        for p, r in trans:
            if len(p) > 1 and _e_escrita(p):
                got = ecu.responder(p)
            else:
                ecu.observar(p, r)
                got = ecu.responder(p)
            (ok if got == r else falha).add((p, r))
        return ok, falha

    def prever(self, transacoes=None):
        """Regra estrita (sem olhar a resposta do instante): conta leituras previstas pelo estado anterior
        + escritas. Devolve dict com acertos/erros/novos e a lista das leituras logo após escrita."""
        trans = self.transacoes if transacoes is None else transacoes
        ecu, tocado, cont, pos = EcuEmulada(), {}, {'ok': 0, 'mudou': 0, 'novo': 0}, []
        for i, (p, r) in enumerate(trans):
            if len(p) > 1 and _e_escrita(p):
                antes = dict(ecu.estado)
                ecu.responder(p)
                for k, v in ecu.estado.items():
                    if antes.get(k) != v:
                        tocado[k] = i
                continue
            got = ecu.responder(p)
            if ecu.ultimo_rotulo == NAO_MODELADO:
                cont['novo'] += 1
            else:
                cont['ok' if got == r else 'mudou'] += 1
            if p in tocado:
                pos.append((tocado.pop(p), i, p, got == r))
            ecu.observar(p, r)
        return cont, pos

    def observar(self, p, bruta):
        """Aplica uma transação vista: leitura -> estado; escrita -> efeito (a resposta vista é ACK)."""
        p = bytes(p)
        if len(p) > 1 and _e_escrita(p):
            self._aplicar_escrita(p)
            dec = decodificar_resposta(p, bruta)
            if dec[0] == 'quadro' and dec[1] == NAK:
                self.estado[p] = dec   # NAK de escrita: só onde visto
            return
        self.estado[p] = decodificar_resposta(p, bruta)

    # ---------- resposta ----------
    def responder(self, p):
        p = bytes(p)
        if len(p) == 1:   # byte de acordar 00 (visto: resposta 80, sem eco)
            st = self.estado.get(p)
            self.ultimo_rotulo = 'OBS-FIO' if st else NAO_MODELADO
            return st[1] if st and st[0] == 'bruta' else b''
        if soma(p[:-1]) != p[-1] or tamanho_pedido(p) != len(p):
            self.ultimo_rotulo = NAO_MODELADO   # captura não tem pedido com checksum errado
            return p                            # só o eco: a ECU não confirma nada
        if _e_escrita(p):
            st = self.estado.get(p)
            if st and st[0] == 'quadro' and st[1] == NAK:
                self.ultimo_rotulo = 'OBS-FIO'
                return p + montar_quadro(NAK, st[2])
            self.ultimo_rotulo = self._aplicar_escrita(p)
            return p + montar_quadro(ACK, b'')
        if p[:3] == b'\x02\x24\x04':           # ação AutoCal: ACK genérico, efeito não modelado
            self.ultimo_rotulo = NAO_MODELADO
            self.efeitos_nao_modelados.append(('autocal', p[3]))
            return p + montar_quadro(ACK, b'')
        st = self.estado.get(p)
        if st is None:
            self.ultimo_rotulo = NAO_MODELADO
            return p
        self.ultimo_rotulo = 'OBS-FIO'
        if st[0] == 'bruta':
            return st[1]
        return p + montar_quadro(st[1], st[2])

    # ---------- efeitos de escrita ----------
    def _put(self, chave, dados):
        self.estado[chave] = ('quadro', ACK, bytes(dados))

    def _dados(self, chave):
        st = self.estado.get(chave)
        return st[2] if st and st[0] == 'quadro' and st[1] == ACK else None

    def _aplicar_escrita(self, p):
        """Atualiza o estado; devolve o rótulo da regra usada."""
        op = p[0]
        if op & 7 == 7:                       # forma estendida: op, len, a_lo, a_hi, dados, ck (DERIVADO)
            a_lo, a_hi, corpo, rot = p[2], p[3], p[4:-1], 'DERIVADO'
        else:
            a_lo, a_hi, corpo, rot = p[1], p[2], p[3:-1], 'OBS-FIO'
        k_vet = pedido(0x29, a_lo, a_hi)
        if 0x30 <= op <= 0x37:                # SetVector: vetor inteiro no endereço
            self._put(k_vet, corpo)
            for idx in range(256):            # leituras indexadas 0A do mesmo vetor, se já vistas
                k = pedido(0x0A, a_lo, a_hi, idx)
                d = self._dados(k)
                if d is not None and (idx + 1) * len(d) <= len(corpo):
                    self._put(k, corpo[idx * len(d):(idx + 1) * len(d)])
            return rot if op != 0x35 else 'OBS-FIO'
        # SetNumber: a forma é decidida pelo que já se sabe do endereço
        k_esc = pedido(0x09, a_lo, a_hi)
        d_esc = self._dados(k_esc)
        if d_esc is not None and len(d_esc) == len(corpo):
            self._put(k_esc, corpo)           # escalar
            return rot if op != 0x12 else 'OBS-FIO'
        if len(corpo) >= 3:                   # matriz: linha, coluna, valor (visto em 14 a a r c v)
            k_lin = pedido(0x2A, a_lo, a_hi, corpo[0])
            d = self._dados(k_lin)
            s = len(corpo) - 2
            if d is not None and (corpo[1] + 1) * s <= len(d):
                novo = bytearray(d)
                novo[corpo[1] * s:(corpo[1] + 1) * s] = corpo[2:]
                self._put(k_lin, novo)
                return 'OBS-FIO'
        if len(corpo) >= 2:                   # vetor: índice, valor
            d = self._dados(k_vet)
            s = len(corpo) - 1
            if d is not None and (corpo[0] + 1) * s <= len(d):
                novo = bytearray(d)
                novo[corpo[0] * s:(corpo[0] + 1) * s] = corpo[1:]
                self._put(k_vet, novo)
                k_idx = pedido(0x0A, a_lo, a_hi, corpo[0])
                if self._dados(k_idx) is not None:
                    self._put(k_idx, corpo[1:])
                return 'DERIVADO'
        if len(corpo) in (1, 2, 4) and d_esc is None:
            self._put(k_esc, corpo)           # escalar nunca lido: assume largura do valor
            return 'INFERIDO'
        self.efeitos_nao_modelados.append(('setnumber', bytes(p)))
        return NAO_MODELADO


# ---------- servidor TCP ----------
class _Handler(socketserver.BaseRequestHandler):
    def handle(self):
        ecu, buf = self.server.ecu, bytearray()
        self.request.settimeout(0.05)
        while True:
            try:
                chunk = self.request.recv(4096)
                if not chunk:
                    return
                buf += chunk
            except socket.timeout:
                if buf == b'\x00':            # byte de acordar sozinho
                    self.request.sendall(ecu.responder(bytes(buf)))
                    buf.clear()
                continue
            while True:
                n = tamanho_pedido(buf)
                if n is None or len(buf) < n:
                    break
                self.request.sendall(ecu.responder(bytes(buf[:n])))
                del buf[:n]


def servir(ecu, porta, host='127.0.0.1'):
    socketserver.ThreadingTCPServer.allow_reuse_address = True
    srv = socketserver.ThreadingTCPServer((host, porta), _Handler)
    srv.ecu = ecu
    return srv


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument('--captura', required=True)
    ap.add_argument('--porta', type=int, default=0, help='0 = não abre servidor; só resume o estado')
    a = ap.parse_args(argv)
    ecu = EcuEmulada()
    ecu.carregar_captura(a.captura)
    ok, falha = ecu.regenerar()
    print(f'transações: {len(ecu.transacoes)}  pedidos no estado: {len(ecu.estado)}  '
          f'pares regenerados: {len(ok)}/{len(ok) + len(falha)}')
    if a.porta:
        srv = servir(ecu, a.porta)
        print(f'ECU emulada em 127.0.0.1:{a.porta} (Ctrl+C para sair)')
        try:
            srv.serve_forever()
        except KeyboardInterrupt:
            srv.server_close()


if __name__ == '__main__':
    sys.exit(main())
