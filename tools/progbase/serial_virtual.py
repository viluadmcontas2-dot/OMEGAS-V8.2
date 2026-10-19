#!/usr/bin/env python3
"""Ponte transparente PTY→TCP para Wine; não altera o protocolo nem o executável.

Inicie ecu_emulator.py --captura LOG --porta 5555; execute este arquivo com
--port 5555 --log quadros.jsonl --ready /tmp/pty.json. Ligue dosdevices/com1
no prefix Wine isolado ao slave publicado em ready. O log contém fragmentos do
transporte, NÃO pressupõe que cada recv/read seja um quadro completo.
"""
import argparse
import json
import os
from pathlib import Path
import select
import socket
import tty


def relay_once(master, conn, log, timeout=None):
    ready, _, _ = select.select([master, conn], [], [], timeout)
    for source in ready:
        chunk = os.read(master, 4096) if source == master else conn.recv(4096)
        if not chunk:
            return False
        if source == master:
            conn.sendall(chunk)
            direction = 'original->emulator'
        else:
            pending = memoryview(chunk)
            while pending:
                pending = pending[os.write(master, pending):]
            direction = 'emulator->original'
        log._serial_sequence = getattr(log, '_serial_sequence', 0) + 1
        log.write(json.dumps({'sequence': log._serial_sequence, 'direction': direction,
                              'hex': chunk.hex(' '), 'unit': 'transport_chunk'}) + '\n')
        log.flush()
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', required=True, type=int)
    parser.add_argument('--log', required=True, type=Path)
    parser.add_argument('--ready', required=True, type=Path)
    args = parser.parse_args()
    master, slave = os.openpty()
    tty.setraw(slave)
    try:
        with socket.create_connection(('127.0.0.1', args.port)) as conn, args.log.open('x') as log:
            conn.settimeout(None)
            args.ready.write_text(json.dumps({'slave': os.ttyname(slave), 'emulator': f'127.0.0.1:{args.port}'}) + '\n')
            try:
                while relay_once(master, conn, log):
                    pass
            except KeyboardInterrupt:
                pass
    finally:
        os.close(master)
        os.close(slave)


if __name__ == '__main__':
    main()
