import os, sys, unittest
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
sys.path.insert(0, os.path.join(ROOT, 'tools', 'progbase'))
import abas_b_re as A


@unittest.skipUnless(A.achar_exe(), 'ProgBase.exe ausente')
class AbasB(unittest.TestCase):
    def test_anti_stallo_click_quadro(self):
        import json, abas_b_emul as E, abas_b_log as L
        p = os.path.join(ROOT, 'docs', 'progbase', 'abas', '_ecu_dono_vetores.json')
        tab = L.de_json(json.load(open(p)))
        s = E.Sessao(A.achar_exe(), tab_ecu=tab)
        m = s.cenario(estado=s.estado_dono())
        m.uc.mem_write(m.ctl('CheckAntiStallo') + E.OFF_CHECK_STATE, bytes([1]))
        r = s.rodar(m, s.ctx.metodos_inv['CheckAntiStalloClick'], m.ctl('CheckAntiStallo'))
        self.assertEqual(r['quadros'][0].hex(' '), '35 fa 00 20 00 00 00 4f')

    def test_tempo_abertura_977(self):
        import json, abas_b_emul as E, abas_b_log as L
        p = os.path.join(ROOT, 'docs', 'progbase', 'abas', '_ecu_dono_vetores.json')
        s = E.Sessao(A.achar_exe(), tab_ecu=L.de_json(json.load(open(p))))
        m = s.cenario(estado=s.estado_dono())
        m.wf64(m.ctl('EditAperturaInj') + E.OFF_NUM_VALUE, 2.5)
        r = s.rodar(m, s.ctx.metodos_inv['EditAperturaInjKeyPress'], m.ctl('EditAperturaInj'))
        self.assertEqual(r['quadros'][0].hex(' '), '13 53 00 d1 03 3a')


if __name__ == '__main__':
    unittest.main()
