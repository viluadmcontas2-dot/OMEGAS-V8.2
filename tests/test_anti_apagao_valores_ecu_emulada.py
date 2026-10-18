"""Anti-apagão, os 5 números (ANTI_STALLO, SerialCode 307 = 0x0133, 5 x u16): o quadro de escrita alimenta a ECU EMULADA
(tools/emulacao/ecu_emulator.py) e o fluxo prévia -> escrita -> ACK -> leitura de volta fecha, byte a byte.

Marcas: OBS-COD = decompilação de TAebProtocol.SetVector (docs/evidence/progbase-retained-protocol-export-v1.json): com n+1 >= 7 o
original manda 0x37, endereço baixo, n+1, endereço ALTO, dados, soma. 5 x u16 = 10 bytes => `37 33 0C 01 <10 bytes> <soma>`
(o opcode compacto 0x3B NÃO existe: só vai até 0x36). DERIVADO = os bytes montados. SINTÉTICO = estado de partida inventado.
O montador de quadro do JS (core/anti-apagao-valores.js, o mesmo que a tela usa) roda em Node; o do Kotlin é conferido
pelos mesmos literais nos testes JUnit (checagem de texto abaixo).
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


def quadro_estendido(valores_novos):
    """Conta independente do JS e do Kotlin, a partir da decompilação do original."""
    dados = bytes5(valores_novos)
    corpo = bytes([0x37, 0x33, len(dados) + 2, 0x01]) + dados
    return corpo + bytes([soma(corpo)])


class EmuladorEntendeOQuadroEstendido(unittest.TestCase):
    def test_tamanho_do_pedido_estendido_e_o_byte_do_meio_mais_3(self):
        q = quadro_estendido([1700, 100, 1500, 2, 20])
        self.assertEqual(len(q), 15)
        self.assertEqual(tamanho_pedido(q), 15)            # o código antigo lia o endereço baixo (0x33) como tamanho

    def test_a_ecu_emulada_grava_no_endereco_307_e_responde_ack(self):
        e = ecu_com(FABRICA)
        q = quadro_estendido([1700, 100, 1500, 2, 20])
        self.assertEqual(e.responder(q), q + ACK_VAZIO)
        self.assertEqual(valores(e.responder(LEITURA)), [1700, 100, 1500, 2, 20])

    def test_nao_grava_em_outro_endereco(self):
        e = ecu_com(FABRICA)
        e.responder(quadro_estendido([1, 2, 3, 4, 5]))
        outra = pedido(0x29, 0x08, 0x01)                      # o endereço errado que o código antigo usaria (0x0108)
        self.assertNotIn(outra, e.estado)


class QuadroDoJs(unittest.TestCase):
    def test_js_gera_exatamente_o_quadro_da_conta_independente(self):
        plano = js(op='plan', before=FABRICA, changes={'0': 1700})
        self.assertTrue(plano['ok'])
        self.assertEqual(bytes(plano['frame']), quadro_estendido([1700, 100, 1500, 2, 20]))
        self.assertEqual(plano['frame'][0], 0x37)

    def test_quadro_de_fabrica_literal(self):
        plano = js(op='plan', before=[1700, 100, 1500, 2, 20], changes={'0': 1600})
        self.assertEqual(bytes(plano['frame']).hex().upper(), '37330C01400664 00DC050200140018'.replace(' ', ''))

    def test_kotlin_afirma_os_mesmos_literais(self):
        kt = (ROOT / 'app/src/test/java/com/omegas/prohub/autocal/AntiStalloParamsCoreTest.kt').read_text(encoding='utf-8')
        self.assertIn('37 33 0C 01 40 06 64 00 DC 05 02 00 14 00 18', kt)
        self.assertIn('o primeiro byte NAO e 3B', kt)

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
        plano = js(op='plan', before=antes, changes=mudancas)
        self.assertTrue(plano['ok'], plano)
        resposta = ecu.responder(bytes(plano['frame']))
        volta = valores(ecu.responder(LEITURA))
        veredito = js(op='verify', before=antes, changes=mudancas, readback=volta)
        return plano, resposta, volta, veredito

    def test_editar_a_rotacao_fecha_o_ciclo(self):
        e = ecu_com(FABRICA)
        plano, resposta, volta, veredito = self.fluxo(e, {'0': 1700})
        self.assertEqual(resposta, bytes(plano['frame']) + ACK_VAZIO)
        self.assertEqual(volta, [1700, 100, 1500, 2, 20])
        self.assertTrue(veredito['ok'])

    def test_so_o_campo_pedido_muda_os_outros_sao_preservados(self):  # SINTÉTICO
        e = ecu_com([1234, 99, 1400, 3, 21])
        _, _, volta, _ = self.fluxo(e, {'2': 2000})
        self.assertEqual(volta, [1234, 99, 2000, 3, 21])

    def test_restaurar_fabrica_e_desfazer_voltam_byte_a_byte(self):
        e = ecu_com([1800, 100, 1500, 5, 20])
        original = e.responder(LEITURA)
        _, _, volta, _ = self.fluxo(e, {'0': 1600, '3': 2})
        self.assertEqual(volta, FABRICA)
        _, _, volta2, _ = self.fluxo(e, {'0': 1800, '3': 5})           # desfazer
        self.assertEqual(volta2, [1800, 100, 1500, 5, 20])
        self.assertEqual(e.responder(LEITURA), original)

    def test_fora_da_faixa_nunca_vira_quadro(self):
        for i, maximo in enumerate([6000, 1000, 10000, 1000, 1000]):
            plano = js(op='plan', before=FABRICA, changes={str(i): maximo + 1})
            self.assertFalse(plano['ok'])
            self.assertNotIn('frame', plano)

    def test_mesmo_valor_nao_ha_o_que_gravar(self):
        self.assertTrue(js(op='plan', before=FABRICA, changes={'0': 1600})['noop'])

    def test_idempotente_repetir_o_mesmo_quadro(self):
        e = ecu_com(FABRICA)
        q = quadro_estendido([1700, 100, 1500, 2, 20])
        r1 = e.responder(q); l1 = e.responder(LEITURA)
        r2 = e.responder(q)
        self.assertEqual((r1, l1), (r2, e.responder(LEITURA)))

    def test_mutacao_checksum_errado_a_ecu_nao_grava(self):
        e = ecu_com(FABRICA)
        q = quadro_estendido([1700, 100, 1500, 2, 20])
        ruim = q[:-1] + bytes([(q[-1] + 1) & 0xFF])
        self.assertEqual(e.responder(ruim), ruim)
        self.assertEqual(valores(e.responder(LEITURA)), FABRICA)

    def test_mutacao_ordem_de_bytes_trocada_e_pega_pela_conferencia(self):
        e = ecu_com(FABRICA)
        dados = bytearray(bytes5([1700, 100, 1500, 2, 20])); dados[0], dados[1] = dados[1], dados[0]   # big-endian por engano
        corpo = bytes([0x37, 0x33, 12, 0x01]) + bytes(dados)
        e.responder(corpo + bytes([soma(corpo)]))
        volta = valores(e.responder(LEITURA))
        self.assertNotEqual(volta, [1700, 100, 1500, 2, 20])
        self.assertFalse(js(op='verify', before=FABRICA, changes={'0': 1700}, readback=volta)['ok'])


if __name__ == '__main__':
    unittest.main()
