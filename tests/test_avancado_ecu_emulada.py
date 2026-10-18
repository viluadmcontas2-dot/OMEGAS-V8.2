"""Ferramentas > Avançado: o quadro de escrita do anti-apagão (bit 0x20 de FLAG_CONF3) alimenta a ECU EMULADA
(tools/emulacao/ecu_emulator.py) e o fluxo escrita -> ACK -> leitura de volta fecha, byte a byte.

Marcas: OBS-FIO = bytes vistos no Portmon LOGNOVO (a leitura `29 FA 00 23` -> `53 04 00 00 00 00 57`);
OBS-COD = o quadro `35 FA 00 20 00 00 00 4F` (execução emulada do setter original, 2026-10-18-cpu-setter-original.md; nunca visto ao vivo no fio);
SINTÉTICO = estado de partida inventado só para provar a preservação dos outros bits.
O montador de quadro do JS (core/advanced-flags.js, o mesmo que a tela usa) roda em Node; o do Kotlin
(AutoCalProtocol.writeVectorU16) é conferido pelas mesmas constantes nos testes JUnit (checagem de texto abaixo).
"""
import json
import os
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.emulacao.ecu_emulator import ACK, EcuEmulada, montar_quadro, pedido, soma  # noqa: E402

UI = ROOT / 'app/src/main/assets/ui'
LEITURA = bytes.fromhex('29FA0023')                      # leitura do vetor 250 (OBS-FIO)
LEITURA_ZERO = LEITURA + bytes.fromhex('53040000000057')  # resposta real do LOGNOVO (OBS-FIO)
ACK_VAZIO = bytes.fromhex('530053')
LIGAR_ZERO = bytes.fromhex('35FA00200000004F')           # OBS-COD

NODE_LOADER = r"""
const fs = require('fs'), vm = require('vm');
const ctx = { console, Math, JSON, Number, String, Array, Object, Set, Map }; ctx.window = ctx; ctx.globalThis = ctx; vm.createContext(ctx);
for (const f of ['core/advanced-flags-data.js', 'core/advanced-flags.js']) vm.runInContext(fs.readFileSync(process.argv[1] + '/' + f, 'utf8'), ctx);
const A = ctx.OmegasUi.AdvancedFlags;
const input = JSON.parse(process.argv[2]);
let out;
if (input.op === 'plan') { const p = A.planAntiStallo(input.words, input.enable); out = { ok: p.ok, frame: p.frame, before: p.before, after: p.after, changedMask: p.changedMask, noop: p.noop }; }
if (input.op === 'verify') { const p = A.planAntiStallo(input.before, input.enable); out = A.verifyReadback(p, input.readback); }
if (input.op === 'frame') out = { frame: A.frameForVector(input.address, input.words) };
console.log(JSON.stringify(out));
"""


def js(**entrada):
    r = subprocess.run(['node', '-e', NODE_LOADER, str(UI), json.dumps(entrada)], capture_output=True, text=True, timeout=30)
    assert r.returncode == 0, r.stderr
    return json.loads(r.stdout)


def ecu_com_flag3(words):
    """ECU emulada que já 'leu' FLAG_CONF3 com essas palavras (mesma remontagem que a captura real faz)."""
    e = EcuEmulada()
    dados = b''.join(w.to_bytes(2, 'little') for w in words)
    e.observar(LEITURA, LEITURA + montar_quadro(ACK, dados))
    return e


def palavras(resposta):
    """eco + 53 04 d0 d1 d2 d3 ck -> [w0, w1] (little-endian, mesma ordem do quadro de escrita)."""
    corpo = resposta[len(LEITURA):]
    assert corpo[0] == ACK and corpo[1] == 4 and soma(corpo[:-1]) == corpo[-1], corpo.hex()
    d = corpo[2:6]
    return [d[0] | d[1] << 8, d[2] | d[3] << 8]


def checksum_python(corpo):  # conta independente do JS e do Kotlin
    return sum(corpo) & 0xFF


class QuadroBateComEsperado(unittest.TestCase):
    def test_js_gera_exatamente_o_quadro_derivado(self):
        self.assertEqual(bytes(js(op='plan', words=[0, 0], enable=True)['frame']), LIGAR_ZERO)

    def test_conta_independente_em_python(self):
        corpo = bytes([0x35, 0xFA, 0x00, 0x20, 0x00, 0x00, 0x00])
        self.assertEqual(corpo + bytes([checksum_python(corpo)]), LIGAR_ZERO)
        self.assertEqual(pedido(0x35, 0xFA, 0x00, 0x20, 0x00, 0x00, 0x00), LIGAR_ZERO)  # montador do emulador

    def test_formato_igual_ao_visto_no_fio_para_flag_conf1(self):
        self.assertEqual(bytes(js(op='frame', address=3, words=[0x2C86, 0x1051])['frame']), bytes.fromhex('3503008 62C5110 4B'.replace(' ', '')))

    def test_kotlin_afirma_os_mesmos_bytes(self):
        """O Kotlin não roda aqui sem Android; os JUnit (CI) afirmam os MESMOS literais que este teste usa."""
        kt = (ROOT / 'app/src/test/java/com/omegas/prohub/ecu/AutoCalProtocolTest.kt').read_text(encoding='utf-8')
        core = (ROOT / 'app/src/test/java/com/omegas/prohub/autocal/AntiStalloFlagCoreTest.kt').read_text(encoding='utf-8')
        for texto in (kt, core):
            self.assertIn('35 FA 00 20 00 00 00 4F', texto)
        self.assertIn('35 FA 00 00 00 00 00 2F', core)


class FluxoNaEcuEmulada(unittest.TestCase):
    def fluxo(self, ecu, enable):
        """toque: ler -> planejar (JS) -> escrever -> ACK -> ler de volta -> verificar (JS). Devolve (plano, ack, readback, veredito)."""
        antes = palavras(ecu.responder(LEITURA))
        plano = js(op='plan', words=antes, enable=enable)
        self.assertTrue(plano['ok'])
        quadro = bytes(plano['frame'])
        resposta = ecu.responder(quadro)
        volta = palavras(ecu.responder(LEITURA))
        veredito = js(op='verify', before=antes, enable=enable, readback=volta)
        return plano, resposta, volta, veredito

    def test_ligar_com_a_ecu_do_dono_fecha_o_ciclo(self):
        ecu = EcuEmulada()
        ecu.observar(LEITURA, LEITURA_ZERO)                 # OBS-FIO: FLAG_CONF3 = 0000 0000
        plano, resposta, volta, veredito = self.fluxo(ecu, True)
        self.assertEqual(resposta, LIGAR_ZERO + ACK_VAZIO)  # eco + ACK (53 00 53)
        self.assertEqual(ecu.responder(LEITURA), LEITURA + bytes.fromhex('53042000000077'))  # leitura de volta documentada
        self.assertEqual(volta, [0x0020, 0])
        self.assertTrue(veredito['ok'])

    def test_desfazer_restaura_o_valor_anterior_pelo_mesmo_fluxo(self):
        ecu = EcuEmulada()
        ecu.observar(LEITURA, LEITURA_ZERO)
        self.fluxo(ecu, True)
        _, resposta, volta, veredito = self.fluxo(ecu, False)
        self.assertEqual(resposta, bytes.fromhex('35FA00000000002F') + ACK_VAZIO)
        self.assertEqual(volta, [0, 0])
        self.assertTrue(veredito['ok'])
        self.assertEqual(ecu.responder(LEITURA), LEITURA_ZERO)   # byte a byte igual ao original da captura

    def test_so_um_bit_muda_e_os_outros_sao_preservados(self):  # SINTÉTICO
        ecu = ecu_com_flag3([0xDFCF, 0xA5A5])
        plano, _, volta, veredito = self.fluxo(ecu, True)
        self.assertEqual(volta, [0xDFEF, 0xA5A5])
        self.assertEqual(plano['changedMask'], 0x20)
        self.assertTrue(veredito['ok'])
        ecu2 = ecu_com_flag3([0xFFFF, 0xFFFF])
        _, _, volta2, _ = self.fluxo(ecu2, False)
        self.assertEqual(volta2, [0xFFDF, 0xFFFF])

    def test_caso_do_codigo_original_0c10_abcd(self):  # OBS-COD (docs/evidence/2026-10-18-cpu-setter-original.md)
        ecu = ecu_com_flag3([0x0C10, 0xABCD])
        plano, resposta, volta, veredito = self.fluxo(ecu, True)
        self.assertEqual(bytes(plano['frame']), bytes.fromhex('35FA00300CCDABE3'))
        self.assertEqual(resposta, bytes.fromhex('35FA00300CCDABE3') + ACK_VAZIO)
        self.assertEqual(volta, [0x0C30, 0xABCD])
        self.assertTrue(veredito['ok'])

    def test_ja_ligado_nao_ha_o_que_gravar(self):
        ecu = ecu_com_flag3([0x0020, 0])
        plano = js(op='plan', words=palavras(ecu.responder(LEITURA)), enable=True)
        self.assertTrue(plano['noop'])

    def test_idempotente_repetir_o_mesmo_quadro_da_o_mesmo_estado(self):
        ecu = EcuEmulada()
        ecu.observar(LEITURA, LEITURA_ZERO)
        r1 = ecu.responder(LIGAR_ZERO)
        e1 = ecu.responder(LEITURA)
        r2 = ecu.responder(LIGAR_ZERO)
        self.assertEqual((r1, e1), (r2, ecu.responder(LEITURA)))

    def test_mutacao_checksum_errado_a_ecu_nao_grava_e_a_leitura_de_volta_reprova(self):
        ecu = EcuEmulada()
        ecu.observar(LEITURA, LEITURA_ZERO)
        ruim = LIGAR_ZERO[:-1] + bytes([(LIGAR_ZERO[-1] + 1) & 0xFF])
        self.assertEqual(ecu.responder(ruim), ruim)           # só eco, sem ACK (NÃO MODELADO no emulador)
        volta = palavras(ecu.responder(LEITURA))
        self.assertEqual(volta, [0, 0])
        self.assertFalse(js(op='verify', before=[0, 0], enable=True, readback=volta)['ok'])

    def test_mutacao_bit_errado_no_quadro_e_pego_pela_conferencia(self):
        ecu = EcuEmulada()
        ecu.observar(LEITURA, LEITURA_ZERO)
        errado = pedido(0x35, 0xFA, 0x00, 0x10, 0x00, 0x00, 0x00)   # bit 0x10 em vez de 0x20
        ecu.responder(errado)
        volta = palavras(ecu.responder(LEITURA))
        self.assertEqual(volta, [0x0010, 0])
        self.assertFalse(js(op='verify', before=[0, 0], enable=True, readback=volta)['ok'])

    def test_ordem_de_bytes_trocada_e_pega(self):
        ecu = EcuEmulada()
        ecu.observar(LEITURA, LEITURA_ZERO)
        trocado = pedido(0x35, 0xFA, 0x00, 0x00, 0x20, 0x00, 0x00)    # 0x2000 na palavra 0 (big-endian por engano)
        ecu.responder(trocado)
        volta = palavras(ecu.responder(LEITURA))
        self.assertEqual(volta, [0x2000, 0])
        self.assertFalse(js(op='verify', before=[0, 0], enable=True, readback=volta)['ok'])


CAPTURA = os.environ.get('ECU_CAPTURA', '/tmp/claude-0/-home-user/57e20309-4640-5e31-b02d-4b2faf9cf2b3/scratchpad/portmon/x/PortmonLOGNOVO.LOG')


@unittest.skipUnless(os.path.exists(CAPTURA), 'captura Portmon ausente (opcional)')
class ComACaptura(unittest.TestCase):
    def test_estado_real_da_captura_liga_e_desfaz(self):
        ecu = EcuEmulada()
        ecu.carregar_captura(CAPTURA)
        inicial = ecu.responder(LEITURA)
        ecu.responder(LIGAR_ZERO) if palavras(inicial) == [0, 0] else self.skipTest('FLAG_CONF3 não era 0000 na captura')
        self.assertEqual(palavras(ecu.responder(LEITURA)), [0x20, 0])


if __name__ == '__main__':
    unittest.main()
