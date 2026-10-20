"""Diálogo REAL (byte a byte) entre o anti-apagão e a ECU EMULADA, congelado em tests/fixtures/anti-apagao-dialogo-ecu.json.

A ECU emulada (tools/emulacao/ecu_emulator.py) reproduz as respostas das capturas reais e aplica as escritas (efeito DERIVADO do código do original).
Este teste: (1) regera o diálogo a partir do emulador e exige que o arquivo congelado seja idêntico; (2) prova que a ECU emulada MUDOU de verdade
(lê de volta o que foi gravado). O teste Kotlin AntiStalloDialogoEcuEmuladaTest faz o escritor real do app conversar com ESTE diálogo, em ordem estrita:
qualquer quadro diferente, fora de ordem ou a mais derruba o teste.  Regerar: python3 tests/test_dialogo_antiapagao_ecu.py --escrever
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.emulacao.ecu_emulator import ACK, EcuEmulada, decodificar_resposta, montar_quadro, pedido  # noqa: E402

FIXTURE = ROOT / 'tests/fixtures/anti-apagao-dialogo-ecu.json'
LER_FLAG = pedido(0x29, 0xFA, 0x00)       # 29 FA 00 23 (OBS-FIO)
LER_VALORES = pedido(0x29, 0x33, 0x01)    # 29 33 01 5D (OBS-FIO)


def hexa(b):
    return ' '.join('%02X' % x for x in b)


def u16(valores):
    return b''.join(v.to_bytes(2, 'little') for v in valores)


def passo(ecu, p):
    bruta = ecu.responder(p)
    tipo = decodificar_resposta(p, bruta)
    assert tipo[0] == 'quadro' and tipo[1] == ACK, (hexa(p), hexa(bruta))
    return dict(pedido=hexa(p), resposta=hexa(bruta), status=tipo[1], payload=hexa(tipo[2]))


def ecu_flag(palavras):
    e = EcuEmulada()
    e.observar(LER_FLAG, LER_FLAG + montar_quadro(ACK, u16(palavras)))
    return e


def ecu_valores(valores):
    e = EcuEmulada()
    e.observar(LER_VALORES, LER_VALORES + montar_quadro(ACK, u16(valores)))
    return e


def quadro_flag(palavras):
    corpo = bytes([0x35, 0xFA, 0x00, *u16(palavras)])
    return corpo + bytes([sum(corpo) & 0xFF])


def quadro_valores(valores):
    corpo = bytes([0x37, 0x33, 0x0C, 0x01, *u16(valores)])
    return corpo + bytes([sum(corpo) & 0xFF])


def dialogo_flag(antes, depois):
    e = ecu_flag(antes)
    # ordem do app: ler, reler (conferência), gravar, ler de volta
    passos = [passo(e, LER_FLAG), passo(e, LER_FLAG), passo(e, quadro_flag(depois)), passo(e, LER_FLAG)]
    return dict(antes=antes, depois=depois, passos=passos)


def dialogo_valores(antes, depois):
    e = ecu_valores(antes)
    passos = [passo(e, LER_VALORES), passo(e, LER_VALORES), passo(e, quadro_valores(depois)), passo(e, LER_VALORES)]
    return dict(antes=antes, depois=depois, passos=passos)


def gerar():
    return {
        'schema': 'anti-apagao-dialogo-ecu/1',
        'fonte': 'tools/emulacao/ecu_emulator.py (respostas das capturas reais; efeito da escrita DERIVADO do código do original)',
        'flag_ligar': dialogo_flag([0x0010, 0x1234], [0x0030, 0x1234]),
        'flag_desligar': dialogo_flag([0x0030, 0x1234], [0x0010, 0x1234]),
        'valores_rotacao_maxima': dialogo_valores([1600, 100, 1500, 2, 20], [1800, 100, 1500, 2, 20]),
        'valores_restaurar_fabrica': dialogo_valores([1800, 150, 2000, 4, 30], [1600, 100, 1500, 2, 20]),
    }


def test_arquivo_congelado_e_igual_ao_que_o_emulador_gera():
    assert json.loads(FIXTURE.read_text(encoding='utf-8')) == gerar()


def test_a_ecu_emulada_mudou_de_verdade():
    for nome, ler, ecu_de, quadro, antes, depois in (
        ('flag', LER_FLAG, ecu_flag, quadro_flag, [0x0010, 0x1234], [0x0030, 0x1234]),
        ('valores', LER_VALORES, ecu_valores, quadro_valores, [1600, 100, 1500, 2, 20], [1800, 100, 1500, 2, 20]),
    ):
        e = ecu_de(antes)
        assert decodificar_resposta(ler, e.responder(ler))[2] == u16(antes), nome
        r = e.responder(quadro(depois))
        assert decodificar_resposta(quadro(depois), r)[:2] == ('quadro', ACK), nome
        assert decodificar_resposta(ler, e.responder(ler))[2] == u16(depois), nome


def test_quadro_errado_nao_muda_a_ecu():
    e = ecu_valores([1600, 100, 1500, 2, 20])
    ruim = bytearray(quadro_valores([1800, 100, 1500, 2, 20])); ruim[-1] ^= 0xFF  # soma errada
    e.responder(bytes(ruim))
    assert decodificar_resposta(LER_VALORES, e.responder(LER_VALORES))[2] == u16([1600, 100, 1500, 2, 20])


if __name__ == '__main__':
    if '--escrever' in sys.argv:
        FIXTURE.write_text(json.dumps(gerar(), indent=1, ensure_ascii=False) + '\n', encoding='utf-8')
    for nome, fn in list(globals().items()):
        if nome.startswith('test_'):
            fn()
    print('DIALOGO_ANTIAPAGAO_ECU=PASS')
