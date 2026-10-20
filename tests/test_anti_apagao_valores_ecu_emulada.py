"""Anti-apagão, os 5 números (ANTI_STALLO, SerialCode 307 = 0x0133, 5 x u16): o quadro de escrita alimenta a ECU EMULADA
(tools/emulacao/ecu_emulator.py) e o fluxo prévia -> escrita -> ACK -> leitura de volta fecha, byte a byte.

Marcas: OBS-COD = código ORIGINAL executado no emulador (docs/progbase/protocolo/anti-apagao-quadros-original.md,
EV-ANTIAPAGAO-026/027): o original grava UM elemento por vez, `14 33 01 <índice> <u16 LE> <soma>`, um quadro por campo alterado,
e lê de volta com `29 33 01`. O app passou a gravar assim (dono autorizou, 2026-10-20); o SetVector estendido `37 33 0C 01 …` que
o app mandava antes o original nunca usa. DERIVADO = os bytes montados. SINTÉTICO = estado de partida inventado.
Quem monta os quadros que vão à central é o Kotlin (AntiStalloParamsCore, conferido pelos mesmos literais nos testes JUnit);
o JS (core/anti-apagao-valores.js) só valida a faixa e confere a leitura de volta, e é usado aqui só para isso.
"""
import json
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.emulacao.ecu_emulator import ACK, EcuEmulada, montar_quadro, pedido, soma, tamanho_pedido  # noqa: E402

UI = ROOT / 'app/src/main/assets/ui'
LEITURA = pedido(0x29, 0x33, 0x01)                    # 29 33 01 5D
ACK_VAZIO = bytes.fromhex('530053')
FABRICA = [1600, 100, 1500, 2, 20]

NODE_LOADER = r"""
const fs = require('fs'), vm = require('vm');
const ctx = { console, Math, JSON, Number, String, Array, Object, Set, Map }; ctx.window = ctx; ctx.globalThis = ctx; vm.createContext(ctx);
vm.runInContext(fs.readFileSync(process.argv[1] + '/core/anti-apagao-valores.js', 'utf8'), ctx);
const V = ctx.OmegasUi.AntiApagaoValores;
const input = JSON.parse(process.argv[2]);
let out;
if (input.op === 'plan') { const p = V.plan(input.before, input.changes); out = p.ok ? { ok: true, frame: p.frame, after: p.after, changed: p.changed, noop: p.noop } : p; }
if (input.op === 'verify') { const p = V.plan(input.before, input.changes); out = V.verifyReadback(p, input.readback); }
if (input.op === 'validate') out = V.validate(input.changes);
console.log(JSON.stringify(out));
"""


def js(**entrada):
    r = subprocess.run(['node', '-e', NODE_LOADER, str(UI), json.dumps(entrada)], capture_output=True, text=True, timeout=30)
    assert r.returncode == 0, r.stderr
    return json.loads(r.stdout)


def bytes5(valores):
    return b''.join(v.to_bytes(2, 'little') for v in valores)


def ecu_com(valores):
    e = EcuEmulada()
    e.observar(LEITURA, LEITURA + montar_quadro(ACK, bytes5(valores)))
    return e


def valores(resposta):
    corpo = resposta[len(LEITURA):]
    assert corpo[0] == ACK and corpo[1] == 10 and soma(corpo[:-1]) == corpo[-1], corpo.hex()
    d = corpo[2:12]
    return [d[i] | d[i + 1] << 8 for i in range(0, 10, 2)]


def quadro_campo(indice, valor):
    """Conta independente do Kotlin: o SetNumber indexado do original (OBS-COD)."""
    corpo = bytes([0x14, 0x33, 0x01, indice, valor & 0xFF, valor >> 8])
    return corpo + bytes([soma(corpo)])


def quadros(antes, depois):
    """Como o original: um quadro por campo alterado, em ordem 0..4."""
    return [quadro_campo(i, depois[i]) for i in range(5) if antes[i] != depois[i]]


class EmuladorEntendeOQuadroDoOriginal(unittest.TestCase):
    def test_tamanho_do_pedido_e_7(self):
        q = quadro_campo(0, 1800)
        self.assertEqual(q.hex(' ').upper(), '14 33 01 00 08 07 57')     # bytes do original emulado
        self.assertEqual(tamanho_pedido(q), 7)

    def test_a_ecu_emulada_grava_so_o_elemento_e_responde_eco_mais_ack(self):
        e = ecu_com(FABRICA)
        q = quadro_campo(0, 1700)
        self.assertEqual(e.responder(q), q + ACK_VAZIO)
        self.assertEqual(valores(e.responder(LEITURA)), [1700, 100, 1500, 2, 20])

    def test_nao_grava_em_outro_endereco(self):
        e = ecu_com(FABRICA)
        for q in quadros(FABRICA, [1, 2, 3, 4, 5]):
            e.responder(q)
        outra = pedido(0x29, 0x08, 0x01)                      # o endereço errado que o código antigo usaria (0x0108)
        self.assertNotIn(outra, e.estado)


class QuadroDoKotlin(unittest.TestCase):
    def test_kotlin_afirma_os_literais_do_original(self):
        kt = (ROOT / 'app/src/test/java/com/omegas/prohub/autocal/AntiStalloParamsCoreTest.kt').read_text(encoding='utf-8')
        for literal in ('14 33 01 00 08 07 57', '14 33 01 01 96 00 DF', '14 33 01 04 1E 00 6A', '14 33 01 00 40 06 8E'):
            self.assertIn(literal, kt)
        self.assertIn('NUNCA 37 nem 3B', kt)

    def test_kotlin_monta_pelo_setnumber_indexado_e_nao_pelo_setvector(self):
        kt = (ROOT / 'app/src/main/java/com/omegas/prohub/autocal/AntiStalloParamsCore.kt').read_text(encoding='utf-8')
        self.assertIn('AutoCalProtocol.writeIndexedU16(ADDRESS, index, value)', kt)
        self.assertNotIn('writeVectorU16', kt)

    def test_faixa_do_js_e_a_do_kotlin(self):
        kt = (ROOT / 'app/src/main/java/com/omegas/prohub/autocal/AntiStalloParamsCore.kt').read_text(encoding='utf-8')
        for trecho in ('Field(0, 0, 6000, 1600)', 'Field(1, 0, 1000, 100)', 'Field(2, 0, 10000, 1500)', 'Field(3, 0, 1000, 2)', 'Field(4, 0, 1000, 20)'):
            self.assertIn(trecho, kt)
        faixa = js(op='validate', changes={'0': 6001})
        self.assertFalse(faixa['ok'])
        for i, maximo in enumerate([6000, 1000, 10000, 1000, 1000]):
            self.assertTrue(js(op='validate', changes={str(i): maximo})['ok'], i)
            self.assertFalse(js(op='validate', changes={str(i): maximo + 1})['ok'], i)
            self.assertFalse(js(op='validate', changes={str(i): -1})['ok'], i)


class FluxoNaEcuEmulada(unittest.TestCase):
    def fluxo(self, ecu, mudancas):
        antes = valores(ecu.responder(LEITURA))
        depois = list(antes)
        for k, v in mudancas.items():
            depois[int(k)] = v
        enviados = quadros(antes, depois)
        respostas = [ecu.responder(q) for q in enviados]
        volta = valores(ecu.responder(LEITURA))
        veredito = js(op='verify', before=antes, changes=mudancas, readback=volta)
        return enviados, respostas, volta, veredito

    def test_editar_a_rotacao_fecha_o_ciclo(self):
        e = ecu_com(FABRICA)
        enviados, respostas, volta, veredito = self.fluxo(e, {'0': 1800})
        self.assertEqual([q.hex(' ').upper() for q in enviados], ['14 33 01 00 08 07 57'])
        self.assertEqual(respostas, [q + ACK_VAZIO for q in enviados])
        self.assertEqual(volta, [1800, 100, 1500, 2, 20])
        self.assertTrue(veredito['ok'])

    def test_so_o_campo_pedido_muda_os_outros_sao_preservados(self):  # SINTÉTICO
        e = ecu_com([1234, 99, 1400, 3, 21])
        enviados, _, volta, _ = self.fluxo(e, {'2': 2000})
        self.assertEqual(len(enviados), 1)
        self.assertEqual(volta, [1234, 99, 2000, 3, 21])

    def test_restaurar_fabrica_e_desfazer_voltam_byte_a_byte(self):
        e = ecu_com([1800, 100, 1500, 5, 20])
        original = e.responder(LEITURA)
        enviados, _, volta, _ = self.fluxo(e, {'0': 1600, '3': 2})
        self.assertEqual([q[3] for q in enviados], [0, 3])                 # um por campo, em ordem
        self.assertEqual(volta, FABRICA)
        _, _, volta2, _ = self.fluxo(e, {'0': 1800, '3': 5})           # desfazer
        self.assertEqual(volta2, [1800, 100, 1500, 5, 20])
        self.assertEqual(e.responder(LEITURA), original)

    def test_falha_no_segundo_quadro_deixa_a_central_no_meio_e_o_desfazer_volta_so_o_gravado(self):
        e = ecu_com(FABRICA)
        q = quadros(FABRICA, [1600, 150, 1500, 2, 30])
        self.assertEqual(e.responder(q[0]), q[0] + ACK_VAZIO)              # o 2º quadro "se perde": não chega à central
        parcial = valores(e.responder(LEITURA))
        self.assertEqual(parcial, [1600, 150, 1500, 2, 20])
        self.assertFalse(js(op='verify', before=FABRICA, changes={'1': 150, '4': 30}, readback=parcial)['ok'])
        desfazer = quadros(parcial, FABRICA)
        self.assertEqual([x.hex(' ').upper() for x in desfazer], ['14 33 01 01 64 00 AD'])
        e.responder(desfazer[0])
        self.assertEqual(valores(e.responder(LEITURA)), FABRICA)

    def test_fora_da_faixa_nunca_vira_quadro(self):
        for i, maximo in enumerate([6000, 1000, 10000, 1000, 1000]):
            plano = js(op='plan', before=FABRICA, changes={str(i): maximo + 1})
            self.assertFalse(plano['ok'])
            self.assertNotIn('frame', plano)

    def test_mesmo_valor_nao_ha_o_que_gravar(self):
        self.assertTrue(js(op='plan', before=FABRICA, changes={'0': 1600})['noop'])
        self.assertEqual(quadros(FABRICA, FABRICA), [])

    def test_idempotente_repetir_o_mesmo_quadro(self):
        e = ecu_com(FABRICA)
        q = quadro_campo(0, 1700)
        r1 = e.responder(q); l1 = e.responder(LEITURA)
        r2 = e.responder(q)
        self.assertEqual((r1, l1), (r2, e.responder(LEITURA)))

    def test_mutacao_checksum_errado_a_ecu_nao_grava(self):
        e = ecu_com(FABRICA)
        q = quadro_campo(0, 1700)
        ruim = q[:-1] + bytes([(q[-1] + 1) & 0xFF])
        self.assertEqual(e.responder(ruim), ruim)
        self.assertEqual(valores(e.responder(LEITURA)), FABRICA)

    def test_mutacao_ordem_de_bytes_trocada_e_pega_pela_conferencia(self):
        e = ecu_com(FABRICA)
        corpo = bytes([0x14, 0x33, 0x01, 0x00, 1700 >> 8, 1700 & 0xFF])   # big-endian por engano
        e.responder(corpo + bytes([soma(corpo)]))
        volta = valores(e.responder(LEITURA))
        self.assertNotEqual(volta, [1700, 100, 1500, 2, 20])
        self.assertFalse(js(op='verify', before=FABRICA, changes={'0': 1700}, readback=volta)['ok'])


if __name__ == '__main__':
    unittest.main()
