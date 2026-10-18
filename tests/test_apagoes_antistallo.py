import json
import os
import sys
import unittest

ROOT = os.path.join(os.path.dirname(__file__), '..')
sys.path.insert(0, os.path.join(ROOT, 'tools', 'sessoes'))
import apagoes_x_antistallo as a  # noqa: E402

FIXTURE = os.path.join(ROOT, 'tests', 'fixtures', 'sessoes-uteis', 'apagao-2026-10-13-1613.json')


def sample(rpm, fuel='GNV', state='GNV_ATIVO', fuel_byte=144, gas_ms=4, map_bar=.33, petrol_ms=1.5, **extra):
    return dict(rpm=rpm, fuel=fuel, state=state, fuel_byte=fuel_byte, gas_ms=gas_ms,
                petrol_ms=petrol_ms, map_bar=map_bar, dyn=220, **extra)


def gnv(rpms, **kw):
    return [sample(r, **kw) for r in rpms]


def cutoff(rpm, map_bar=.2):
    return sample(rpm, fuel='CUTOFF', state='CUTOFF_DESACELERACAO', fuel_byte=148, gas_ms=None,
                  petrol_ms=0, map_bar=map_bar)


def gasolina(rpm, map_bar=.33):
    return sample(rpm, fuel='GASOLINA', state='GASOLINA_ATIVA', fuel_byte=128, gas_ms=None,
                  petrol_ms=2.0, map_bar=map_bar)


class ApagoesTest(unittest.TestCase):
    def test_apagao_conhecido_sintetico(self):
        rows = [dict(r, ignition_on=True) for r in gnv((1339, 1100, 700, 0))]
        events = a.detect_events(rows)
        self.assertEqual(len(events), 1)
        self.assertEqual(events[0]['kind'], 'APAGAO_GNV')
        self.assertEqual(events[0]['rule']['answer'], 'SIM')
        self.assertEqual(events[0]['rule']['max_drop'], 400)

    def test_regra_nao_atuaria_acima_de_1600(self):
        events = a.detect_events(gnv((2000, 1900, 1800, 1700, 0)))
        self.assertEqual(len(events), 1)
        self.assertEqual(events[0]['rule']['answer'], 'NAO')

    def test_mutacao_limiar_1600_para_2000_muda_resultado(self):
        rows = gnv((2000, 1900, 1800, 1700, 0))
        self.assertEqual(a.detect_events(rows)[0]['rule']['answer'], 'NAO')
        self.assertEqual(a.detect_events(rows, max_rpm=2000)[0]['rule']['answer'], 'SIM')
        old = a.RULE_MAX_RPM
        try:
            a.RULE_MAX_RPM = 2000
            self.assertEqual(a.detect_events(rows)[0]['rule']['answer'], 'SIM')
        finally:
            a.RULE_MAX_RPM = old
        self.assertEqual(a.detect_events(rows)[0]['rule']['answer'], 'NAO')

    def test_indeterminado_sem_amostras_suficientes_ou_com_lacuna(self):
        self.assertEqual(a.detect_events(gnv((1200, 0)))[0]['rule']['answer'], 'INDETERMINADO')
        rows = gnv((1800, 1750, 1700, 0))
        rows[1]['sample_state'] = 'TELEMETRY_GAP'
        self.assertEqual(a.detect_events(rows)[0]['rule']['answer'], 'INDETERMINADO')
        rows = gnv((1800, 1750, 1700, 0))
        rows[1]['rpm'] = None
        self.assertEqual(a.detect_events(rows)[0]['rule']['answer'], 'INDETERMINADO')

    def test_chave_desligada_ou_rpm_baixa_nao_e_apagao(self):
        rows = [dict(r, ignition_on=False) for r in gnv((1339, 1100, 700, 0))]
        self.assertEqual(a.detect_events(rows), [])
        self.assertEqual(a.detect_events(gnv((400, 300, 0))), [])
        self.assertEqual(a.detect_events(gnv((1339, 1100, 700, 150))), [])

    def test_apagao_em_gasolina_nao_conta_como_gnv(self):
        rows = [gasolina(r) for r in (1339, 1100, 700, 0)]
        events = a.detect_events(rows)
        self.assertEqual([e['kind'] for e in events], ['APAGAO_FORA_DO_GNV'])

    def test_apagao_depois_de_sair_do_gnv_e_classe_propria(self):
        rows = gnv((1500, 1400)) + [gasolina(1200), gasolina(800), gasolina(0)]
        kinds = [e['kind'] for e in a.detect_events(rows) if e['kind'].startswith('APAGAO')]
        self.assertEqual(kinds, ['APAGAO_APOS_SAIDA_GNV'])

    def test_apagao_depois_de_corte_ainda_e_gnv(self):
        rows = gnv((2200, 2100)) + [cutoff(1900), cutoff(1500)] + gnv((1300, 800, 0))
        kinds = [e['kind'] for e in a.detect_events(rows) if e['kind'].startswith('APAGAO')]
        self.assertEqual(kinds, ['APAGAO_GNV'])

    def test_sessoes_diferentes_nao_se_misturam(self):
        rows = [dict(r, sessao='A') for r in gnv((1500, 1400, 1300))] + \
               [dict(r, sessao='B') for r in gnv((0, 0))]
        self.assertEqual(a.detect_events(rows), [])

    def test_janela_anterior_por_amostras(self):
        rows = gnv((1000, 1500, 1400, 1300, 1250, 1200, 1150, 1100, 1050, 700, 0))
        ev = a.detect_events(rows)[0]
        self.assertEqual(len(ev['window']), a.LOOKBACK)
        self.assertEqual(ev['window'][-1]['rpm'], 700)
        self.assertEqual(ev['window'][0]['rpm'], 1400)

    def test_saida_corte_de_desaceleracao(self):
        rows = gnv((2300, 2250)) + [cutoff(2200)] + gnv((2100,))
        ex = [e for e in a.detect_events(rows) if e['kind'] == 'SAIDA_GNV']
        self.assertEqual(len(ex), 1)
        self.assertEqual(ex[0]['causa'], 'CORTE_DESACELERACAO')
        self.assertEqual(ex[0]['estado_destino'], '0x94')

    def test_saida_anti_apagao_candidato_rpm_baixa_e_caindo(self):
        rows = gnv((1600, 1450, 1300)) + [gasolina(1180)]
        ex = [e for e in a.detect_events(rows) if e['kind'] == 'SAIDA_GNV']
        self.assertEqual(len(ex), 1)
        self.assertEqual(ex[0]['causa'], 'ANTI_APAGAO_CANDIDATO')
        self.assertEqual(ex[0]['estado_destino'], 'GASOLINA_ATIVA')

    def test_saida_rpm_alta_e_estavel_e_outra_causa(self):
        rows = gnv((3000, 3010)) + [gasolina(3005)]
        ex = [e for e in a.detect_events(rows) if e['kind'] == 'SAIDA_GNV']
        self.assertEqual(ex[0]['causa'], 'OUTRA_OU_INDETERMINADA')

    def test_saida_com_map_alto_em_cutoff_nao_e_corte_confirmado(self):
        rows = gnv((2300, 2250)) + [cutoff(2200, map_bar=.9)]
        ex = [e for e in a.detect_events(rows) if e['kind'] == 'SAIDA_GNV']
        self.assertEqual(ex[0]['causa'], 'OUTRA_OU_INDETERMINADA')

    def test_motor_parado_nao_e_saida(self):
        rows = gnv((900, 800)) + [sample(0, fuel='DESLIGADO', state='ECU_SEM_MOTOR', fuel_byte=0, gas_ms=None)]
        self.assertEqual([e for e in a.detect_events(rows) if e['kind'] == 'SAIDA_GNV'], [])

    def test_apagao_real_2026_10_13(self):
        frames = json.load(open(FIXTURE))['quadros']
        rows = [dict(f, sessao='real') for f in frames]
        events = [e for e in a.detect_events(rows) if e['kind'].startswith('APAGAO')]
        self.assertEqual([e['kind'] for e in events], ['APAGAO_GNV'])
        rule = events[0]['rule']
        # registro de regressao: queda 1221 -> 749 (472) com rpm <= 1600
        self.assertEqual((rule['answer'], rule['max_drop']), ('SIM', 472))

    def test_relatorio_idempotente_no_corpus(self):
        first = a.build_report()
        second = a.build_report()
        self.assertEqual(first, second)
        self.assertIn('PROVADO', first)
        self.assertIn('INDETERMINADO', first)
        self.assertIn('H1', first)
        self.assertIn('H2', first)
        self.assertIn('H3', first)


if __name__ == '__main__':
    unittest.main()
