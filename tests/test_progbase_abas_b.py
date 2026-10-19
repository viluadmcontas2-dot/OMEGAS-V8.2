import os, sys, unittest
import importlib.util
_UC = importlib.util.find_spec('unicorn') is not None
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
sys.path.insert(0, os.path.join(ROOT, 'tools', 'progbase'))
import abas_b_re as A


@unittest.skipUnless(A.achar_exe() and _UC, 'ProgBase.exe ou unicorn ausente')
class AbasB(unittest.TestCase):
    def test_tab_iniezione_nao_aborta_memoria_delphi(self):
        import abas_b_emul as E
        s = E.Sessao(A.achar_exe())
        m = s.cenario()
        r = s.rodar(m, s.ctx.metodos_inv["TabIniezioneShow"], m.ctl("TabIniezione"))
        # A regressão é o heap: APIs de janela continuam um limite explícito.
        self.assertNotEqual(r["erro"], "API sem stub: ExitProcess", r)
        self.assertIn(0x4005000, r["funcoes"], "alocador substituto não executado")
        self.assertFalse(any("Runtime error 203" in x for x in r["caixas"]))

    def test_cursor_compartilhado_modelado_sem_aceitar_recurso_desconhecido(self):
        import abas_b_emul as E
        s = E.Sessao(A.achar_exe())
        m = s.m
        self.assertIn("LoadCursorW", m.api_py)
        esp = m.uc.reg_read(m.X.UC_X86_REG_ESP)
        m.w32(esp + 4, 0)
        m.w32(esp + 8, 32512)
        cursor = m.api_py["LoadCursorW"](m)
        self.assertNotEqual(cursor, 0)
        self.assertEqual(m.api_py["LoadCursorW"](m), cursor)
        m.w32(esp + 4, cursor)
        self.assertEqual(m.api_py["SetCursor"](m), 0)
        self.assertEqual(m.api_py["SetCursor"](m), cursor)
        m.w32(esp + 4, 1)
        with self.assertRaises(E.C.ErroEmulacao):
            m.api_py["LoadCursorW"](m)

    def test_delphi_realloc_preserva_bytes(self):
        import abas_b_emul as E
        s = E.Sessao(A.achar_exe())
        m = s.m
        m.chamar(m.r32(0xAAFB84), {"eax": 4})
        p = m.uc.reg_read(m.X.UC_X86_REG_EAX)
        m.uc.mem_write(p, b"ABCD")
        m.chamar(m.r32(0xAAFB8C), {"eax": p, "edx": 16})
        q = m.uc.reg_read(m.X.UC_X86_REG_EAX)
        self.assertEqual(bytes(m.uc.mem_read(q, 4)), b"ABCD")

    def test_inventario_17_abas_e_eventos(self):
        import abas_b_gerar as G
        dados = G.inventario(A.Ctx(A.achar_exe()), A.indexa(A.dfm_arvore()))
        self.assertEqual(len(dados), 17)
        self.assertEqual(len({d["aba"] for d in dados}), 17)
        inj = next(d for d in dados if d["aba"] == "TabIniezione")
        ev = next(c for c in inj["controles"] if c["nome"] == "TabIniezione")["eventos"]
        self.assertTrue(any(e["handler"] == "TabIniezioneShow" and e["endereco"] for e in ev))
        self.assertTrue(all(c["formula"] == "DESCONHECIDO" for d in dados for c in d["controles"]))

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
