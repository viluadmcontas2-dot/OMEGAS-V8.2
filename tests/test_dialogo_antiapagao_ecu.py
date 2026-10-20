"""Diálogo REAL (byte a byte) entre o anti-apagão e a ECU EMULADA, congelado em tests/fixtures/anti-apagao-dialogo-ecu.json.

A ECU emulada (tools/emulacao/ecu_emulator.py) reproduz as respostas das capturas reais e aplica as escritas (efeito DERIVADO do código do original).
Este teste: (1) regera o diálogo a partir do emulador e exige que o arquivo congelado seja idêntico; (2) prova que a ECU emulada MUDOU de verdade
(lê de volta o que foi gravado). O teste Kotlin AntiStalloDialogoEcuEmuladaTest faz o escritor real do app conversar com ESTE diálogo, em ordem estrita:
qualquer quadro diferente, fora de ordem ou a mais derruba o teste.  Regerar: python3 tests/test_dialogo_antiapagao_ecu.py --escrever

Os 5 números (vetor 307) são gravados COMO O ORIGINAL (docs/progbase/protocolo/anti-apagao-quadros-original.md, OBS-COD por emulação
do código original): UM `14 33 01 <índice> <u16 LE> <soma>` por campo alterado, em ordem 0..4, cada um com eco + `53 00 53`.
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


def quadro_campo(indice, valor):
    """SetNumber indexado do original (OBS-COD): 14 33 01 <índice> <lo> <hi> <soma>."""
    corpo = bytes([0x14, 0x33, 0x01, indice, valor & 0xFF, valor >> 8])
    return corpo + bytes([sum(corpo) & 0xFF])


def quadros_valores(antes, depois):
    return [quadro_campo(i, depois[i]) for i in range(5) if antes[i] != depois[i]]


def sem_resposta(p):
    """A central não respondeu ao quadro (cabo/ruído): nada é aplicado. status -1 = erro de transporte."""
    return dict(pedido=hexa(p), resposta='', status=-1, payload='')


def ack_sem_efeito(p):
    """A central confirmou (eco + 53 00 53) mas não guardou: a leitura de volta mostra o valor antigo."""
    return dict(pedido=hexa(p), resposta=hexa(p + montar_quadro(ACK, b'')), status=ACK, payload='')


def dialogo_flag(antes, depois):
    e = ecu_flag(antes)
    # ordem do app: ler, reler (conferência), gravar, ler de volta
    passos = [passo(e, LER_FLAG), passo(e, LER_FLAG), passo(e, quadro_flag(depois)), passo(e, LER_FLAG)]
    return dict(antes=antes, depois=depois, passos=passos)


def dialogo_valores(antes, depois):
    e = ecu_valores(antes)
    passos = [passo(e, LER_VALORES), passo(e, LER_VALORES), *[passo(e, q) for q in quadros_valores(antes, depois)], passo(e, LER_VALORES)]
    return dict(antes=antes, depois=depois, passos=passos)


def dialogo_falha_segundo_quadro(antes, depois):
    """1º quadro com ACK, 2º sem resposta: o app para, relê (parcial) e o Desfazer volta só o campo já gravado, também por 14."""
    e = ecu_valores(antes)
    q = quadros_valores(antes, depois)
    passos = [passo(e, LER_VALORES), passo(e, LER_VALORES), passo(e, q[0]), sem_resposta(q[1]), passo(e, LER_VALORES)]
    parcial = list(antes); i0 = q[0][3]; parcial[i0] = depois[i0]
    passos += [passo(e, LER_VALORES), passo(e, LER_VALORES), *[passo(e, x) for x in quadros_valores(parcial, antes)], passo(e, LER_VALORES)]
    return dict(antes=antes, depois=depois, parcial=parcial, passos=passos)


def dialogo_leitura_diferente(antes, depois):
    e = ecu_valores(antes)
    passos = [passo(e, LER_VALORES), passo(e, LER_VALORES), *[ack_sem_efeito(q) for q in quadros_valores(antes, depois)], passo(e, LER_VALORES)]
    return dict(antes=antes, depois=depois, passos=passos)


def gerar():
    return {
        'schema': 'anti-apagao-dialogo-ecu/1',
        'fonte': 'tools/emulacao/ecu_emulator.py (respostas das capturas reais; efeito da escrita DERIVADO do código do original)',
        'flag_ligar': dialogo_flag([0x0010, 0x1234], [0x0030, 0x1234]),
        'flag_desligar': dialogo_flag([0x0030, 0x1234], [0x0010, 0x1234]),
        'valores_rotacao_maxima': dialogo_valores([1600, 100, 1500, 2, 20], [1800, 100, 1500, 2, 20]),
        'valores_restaurar_fabrica': dialogo_valores([1800, 150, 2000, 4, 30], [1600, 100, 1500, 2, 20]),
        'valores_varios_campos': dialogo_valores([1600, 100, 1500, 2, 20], [1600, 150, 1500, 2, 30]),
        'valores_falha_segundo_quadro': dialogo_falha_segundo_quadro([1600, 100, 1500, 2, 20], [1600, 150, 1500, 2, 30]),
        'valores_leitura_diferente': dialogo_leitura_diferente([1600, 100, 1500, 2, 20], [1800, 100, 1500, 2, 20]),
    }


def test_arquivo_congelado_e_igual_ao_que_o_emulador_gera():
    assert json.loads(FIXTURE.read_text(encoding='utf-8')) == gerar()


def test_a_ecu_emulada_mudou_de_verdade():
    for nome, ler, ecu_de, quadros, antes, depois in (
        ('flag', LER_FLAG, ecu_flag, lambda a, d: [quadro_flag(d)], [0x0010, 0x1234], [0x0030, 0x1234]),
        ('valores', LER_VALORES, ecu_valores, quadros_valores, [1600, 100, 1500, 2, 20], [1800, 150, 1500, 2, 30]),
    ):
        e = ecu_de(antes)
        assert decodificar_resposta(ler, e.responder(ler))[2] == u16(antes), nome
        for q in quadros(antes, depois):
            r = e.responder(q)
            assert r == q + montar_quadro(ACK, b''), (nome, hexa(r))   # eco + 53 00 53, como no fio
        assert decodificar_resposta(ler, e.responder(ler))[2] == u16(depois), nome


def test_quadros_iguais_aos_do_original():
    """Bytes do código ORIGINAL emulado (doc anti-apagao-quadros-original.md, EV-ANTIAPAGAO-026/027)."""
    assert hexa(quadro_campo(0, 1800)) == '14 33 01 00 08 07 57'
    assert [hexa(q) for q in quadros_valores([1600, 100, 1500, 2, 20], [1600, 150, 1500, 2, 30])] == ['14 33 01 01 96 00 DF', '14 33 01 04 1E 00 6A']
    texto = json.dumps(gerar())
    assert '37 33 0C' not in texto


def test_quadro_errado_nao_muda_a_ecu():
    e = ecu_valores([1600, 100, 1500, 2, 20])
    ruim = bytearray(quadro_campo(0, 1800)); ruim[-1] ^= 0xFF  # soma errada
    e.responder(bytes(ruim))
    assert decodificar_resposta(LER_VALORES, e.responder(LER_VALORES))[2] == u16([1600, 100, 1500, 2, 20])


if __name__ == '__main__':
    if '--escrever' in sys.argv:
        FIXTURE.write_text(json.dumps(gerar(), indent=1, ensure_ascii=False) + '\n', encoding='utf-8')
    for nome, fn in list(globals().items()):
        if nome.startswith('test_'):
            fn()
    print('DIALOGO_ANTIAPAGAO_ECU=PASS')
