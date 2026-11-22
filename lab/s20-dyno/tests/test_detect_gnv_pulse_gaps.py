import importlib.util
from pathlib import Path
import unittest

tool = Path(__file__).with_name('detect_gnv_pulse_gaps.py')
spec = importlib.util.spec_from_file_location("pulse_detector", tool)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def sample(index, *, state=0x90, gas1=150, gas2=150, rpm=2000,
           map_bar=.18, petrol_ms=1.5, pressure=2.7, gap_ns=0):
    raw = bytearray(34)
    raw[11] = state
    raw[6:8] = gas1.to_bytes(2, 'little')
    raw[24:26] = gas2.to_bytes(2, 'little')
    return {
        "source": "mp48",
        "mono_ns": 1_000_000_000 + index * 45_000_000 + gap_ns,
        "data": {
            "rawHex": raw.hex().upper(),
            "rpm": rpm,
            "mapBar": map_bar,
            "petrolMs": petrol_ms,
            "gasPressureBar": pressure,
        },
    }


class DetectorTests(unittest.TestCase):
    def test_dois_eventos_sinteticos_sao_separados_sem_confundir_espera(self):
        frames = [sample(i, state=0x88, gas1=0, gas2=0) for i in range(8)]
        frames += [sample(i, gas1=150, gas2=100) for i in range(8, 14)]
        frames += [sample(i, gas1=0, gas2=0) for i in range(14, 26)]
        frames += [sample(i, gas1=150, gas2=100) for i in range(26, 35)]
        frames += [
            sample(i, gas1=0, gas2=0, rpm=850, map_bar=.69,
                   petrol_ms=6.8, pressure=3.0)
            for i in range(35, 47)
        ]
        out = module.detect(frames)
        self.assertEqual(len(out["windows"]), 2)
        self.assertEqual(out["windows"][0]["interpretation"],
                         "COMPATIVEL_COM_CORTE_EM_DESACELERACAO")
        self.assertEqual(out["windows"][1]["interpretation"],
                         "REVISAR_INTERRUPCAO_GNV_PROXIMA_DA_MARCHA_LENTA")
        self.assertEqual(out["windows"][0]["frames_both_gnv_banks_zero"], 12)
        self.assertIn("HIPOTESE", out["windows"][1]["certainty"])
        self.assertNotIn("rawHex", str(out))

    def test_nao_marca_gas_ausente_durante_espera_de_comutacao(self):
        waiting = [sample(i, state=0x88, gas1=0, gas2=0) for i in range(100)]
        self.assertEqual(module.detect(waiting)["windows"], [])

    def test_nao_confunde_apenas_banco_um_zerado(self):
        only_one = [sample(i, gas1=0, gas2=90) for i in range(20)]
        self.assertEqual(module.detect(only_one)["windows"], [])

    def test_quebra_janela_apos_grande_intervalo_sem_resposta(self):
        frames = [sample(i, gas1=0, gas2=0) for i in range(4)]
        frames += [sample(i, gas1=0, gas2=0, gap_ns=2_000_000_000) for i in range(4, 8)]
        self.assertEqual(module.detect(frames)["windows"], [])

    def test_nao_marca_poucos_quadros_como_evento(self):
        frames = [sample(i, gas1=0, gas2=0) for i in range(3)]
        self.assertEqual(module.detect(frames)["windows"], [])

    def test_json_sempre_omite_bytes_brutos(self):
        frames = [sample(i, gas1=0, gas2=0) for i in range(7)]
        encoded = __import__('json').dumps(module.detect(frames))
        self.assertNotIn('rawHex', encoded)
        self.assertIn('mp48_frames', encoded)


if __name__ == "__main__":
    unittest.main()
