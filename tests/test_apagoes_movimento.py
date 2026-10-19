"""Não confundir motor girando, movimento, falta de leitura e reinício."""
import unittest
from tools.sessoes.sugerir_antiapagao import analisar_sessoes


def rows(rpms, **extra):
    return [dict(sessao='s', rpm=r, t_ms=i*250, fuel='GNV', fuel_byte=144,
                 state='GNV_ATIVO', sample_state='SAMPLE_ACCEPTED', map_bar=.4, **extra)
            for i, r in enumerate(rpms)]


class ApagoesMovimentoTest(unittest.TestCase):
    def test_um_evento_ate_recuperar_sem_inventar_movimento(self):
        r = analisar_sessoes(rows([1800, 900, 250, 0, 0, 200, 850, 900]))
        self.assertEqual(len(r['eventos']), 1)
        e = r['eventos'][0]
        self.assertEqual(e['tipo'], 'MOTOR_PAROU')
        self.assertEqual(e['movimento'], 'DESCONHECIDO')
        self.assertEqual(e['leituras_ate_recuperar'], 3)
        self.assertEqual(e['tempo_registrado_ate_recuperar_ms'], 750)
        self.assertEqual(e['linha_do_tempo'][0]['rpm'], 1800)

    def test_lacuna_nao_e_apagao_nem_reinicio(self):
        s = rows([1800, 0, 850]); s[1]['sample_state'] = 'TELEMETRY_GAP'
        self.assertEqual(analisar_sessoes(s)['eventos'], [])
        s = rows([1800, 200, 0, 850]); s[2]['rpm'] = None
        e = analisar_sessoes(s)['eventos'][0]
        self.assertIsNone(e['leituras_ate_recuperar'])
        self.assertEqual(e['desfecho'], 'LEITURA_INTERROMPIDA')

    def test_partida_nao_e_quase_apagao(self):
        self.assertEqual(analisar_sessoes(rows([0, 200, 300, 850]))['eventos'], [])

    def test_sem_recuperacao_fica_inconclusivo(self):
        e = analisar_sessoes(rows([900, 0, 0]))['eventos'][0]
        self.assertEqual(e['desfecho'], 'SEM_RECUPERACAO_REGISTRADA')
        self.assertIsNone(e['tempo_registrado_ate_recuperar_ms'])

    def test_relogio_recuando_nao_reordena_nem_inventa_duracao(self):
        s = rows([1800, 0, 850]); s[2]['t_ms'] = -1
        e = analisar_sessoes(s)['eventos'][0]
        self.assertIsNone(e['tempo_registrado_ate_recuperar_ms'])
        self.assertEqual(e['leituras_ate_recuperar'], 1)

    def test_separa_sessoes_e_contabiliza_vazias(self):
        s = rows([1800, 0]); s[-1]['sessao'] = 'outra'
        r = analisar_sessoes(s, ['s', 'outra', 'vazia'])
        self.assertEqual(r['eventos'], [])
        self.assertEqual(r['sessoes_sem_telemetria'], ['vazia'])

    def test_cobertura_nao_promete_prevencao_ou_consumo(self):
        r = analisar_sessoes(rows([1800, 1400, 900, 200, 850]))
        a = r['limiares'][0]
        self.assertIn('eventos_com_leitura_previa_abaixo', a)
        self.assertIsNone(a['gasolina_poupar_litros'])
        self.assertEqual(r['extras'], 'SEM_DADO_PARA_SUGERIR')


if __name__ == '__main__': unittest.main()
