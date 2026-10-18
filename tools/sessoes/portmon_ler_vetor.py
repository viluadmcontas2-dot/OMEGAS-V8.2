#!/usr/bin/env python3
"""Lê, num log Portmon do ProgBase, as respostas a `29 <código lo> <código hi> <ck>` (leitura de vetor).
Uso: portmon_ler_vetor.py <log> <código decimal>  -> uma linha por leitura: posição da linha e valores u16 little-endian.
Resposta do MP48: eco do comando (29), 53 (ok), n (bytes), n bytes de dados, checksum (soma dos bytes anteriores do quadro, sem o eco)."""
import re, struct, sys


def frame_pedido(codigo):
    ck = (0x29 + (codigo & 0xFF) + (codigo >> 8)) & 0xFF
    return f"29 {codigo & 0xFF:02X} {codigo >> 8:02X} {ck:02X}"


def ler(linhas, codigo):
    pedido = frame_pedido(codigo)
    achados = []
    for i, linha in enumerate(linhas):
        if 'IRP_MJ_WRITE' in linha and linha.rstrip().endswith(pedido):
            bytes_ = []
            for seg in linhas[i + 1:i + 80]:
                m = re.search(r'SUCCESS\s+Length 1: ([0-9A-F]{2})', seg)
                if m:
                    bytes_.append(int(m.group(1), 16))
                if 'IRP_MJ_WRITE' in seg:
                    break
            # bytes_: 29 (eco) 53 n dados... ck
            if len(bytes_) >= 4 and bytes_[1] == 0x53:
                n = bytes_[2]
                dados = bytes_[3:3 + n]
                ck = bytes_[3 + n] if len(bytes_) > 3 + n else None
                ok = ck is not None and ((0x53 + n + sum(dados)) & 0xFF) == ck
                valores = list(struct.unpack('<%dH' % (n // 2), bytes(dados))) if n % 2 == 0 and len(dados) == n else None
                achados.append({'linha': i + 1, 'bytes': dados, 'u16': valores, 'checksum_ok': ok})
    return achados


def main():
    caminho, codigo = sys.argv[1], int(sys.argv[2])
    with open(caminho, encoding='latin1') as fh:
        linhas = fh.read().split('\n')
    for a in ler(linhas, codigo):
        print(f"linha {a['linha']}: u16={a['u16']} bytes={' '.join('%02X' % b for b in a['bytes'])} checksum_ok={a['checksum_ok']}")


if __name__ == '__main__':
    main()
