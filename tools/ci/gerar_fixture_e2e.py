#!/usr/bin/env python3
"""Gera tests/ui/fixtures/ecu-estado-lognovo.json: o estado da ECU (MP48) no FIM da captura PortmonLOGNOVO, só as
leituras (29/09/0A/2A) e uma amostra de quadros de telemetria 48 01 49. Alimenta a ECU em JS dos testes e2e de toque.
Uso: python3 -B tools/ci/gerar_fixture_e2e.py CAPTURA.LOG [saida.json]    (idempotente: mesma entrada, mesmo arquivo)"""
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'emulacao'))
import ecu_emulator as E  # noqa: E402


def main(argv):
    log = argv[1]
    out = argv[2] if len(argv) > 2 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'tests', 'ui', 'fixtures', 'ecu-estado-lognovo.json')
    ecu = E.EcuEmulada()
    ecu.carregar_captura(log)
    leituras = {}
    for k, v in sorted(ecu.estado.items()):
        if len(k) > 1 and k[0] in (0x29, 0x09, 0x0A, 0x2A) and v[0] == 'quadro' and v[1] == E.ACK:
            leituras[k.hex(' ').upper()] = v[2].hex(' ').upper()
    telem, vistos = [], set()
    for p, r in ecu.transacoes:
        if p == bytes([0x48, 0x01, 0x49]):
            d = E.decodificar_resposta(p, r)
            if d[0] == 'quadro' and d[1] == E.ACK and d[2] not in vistos and d[2][0:2] != b'\x00\x00':
                vistos.add(d[2])
                telem.append(d[2].hex(' ').upper())
                if len(telem) >= 24:
                    break
    doc = {
        'fonte': 'PortmonLOGNOVO.LOG (12/08/2026), estado ao fim da captura; ver tools/emulacao/ecu_emulator.py',
        'marca': 'OBS-FIO',
        'leituras': leituras,
        'telemetria_48_01': telem,
    }
    with open(out, 'w', encoding='utf-8') as fh:
        json.dump(doc, fh, indent=1, sort_keys=True)
        fh.write('\n')
    print(f'{len(leituras)} leituras, {len(telem)} quadros de telemetria -> {out}')


if __name__ == '__main__':
    main(sys.argv)
