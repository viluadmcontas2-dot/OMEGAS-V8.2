import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/sessoes"))
import limiares_em_movimento as L  # noqa: E402

R = L.analisar()
BASE = {t["maxima"]: t for t in R["tabela"] if t["velocidade_rpm_s"] == L.VELOCIDADES[0]}


def test_corpus_inteiro_e_so_quedas_em_movimento():
    assert R["sessoes"] == 89 and R["apagoes"] == 20 and R["quase_apagoes"] == 35
    assert min(R["picos_dos_apagoes"]) >= L.PICO_MIN  # marcha lenta (~870 rpm) não entra


def test_rotacao_maxima_vem_do_dado_e_o_valor_de_fabrica_perde_um_apagao():
    assert [BASE[m]["apagoes_vistos"] for m in (1400, 1600, 1800, 2000)] == [15, 19, 20, 20]
    assert L.escolher(R) == 1800
    assert BASE[2000]["quedas_comuns_por_hora"] > BASE[1800]["quedas_comuns_por_hora"] > BASE[1600]["quedas_comuns_por_hora"]


def test_relatorio_publicado_esta_em_dia():
    publicado = json.loads((ROOT / "docs/progbase/bins/limiares-em-movimento.json").read_text(encoding="utf-8"))
    assert publicado["tabela"] == R["tabela"] and publicado["rotacao_maxima_sugerida"] == 1800
    assert (ROOT / "docs/progbase/bins/limiares-em-movimento.md").read_text(encoding="utf-8") == L.markdown(R)


def test_mutacao_limiar_errado_muda_o_resultado():
    antigo = L.PICO_MIN
    try:
        L.PICO_MIN = 0
        assert L.analisar()["apagoes"] != R["apagoes"]  # sem o corte de movimento a marcha lenta entra e o teste acima falharia
    finally:
        L.PICO_MIN = antigo


if __name__ == "__main__":
    for nome, fn in list(globals().items()):
        if nome.startswith("test_"):
            fn()
    print("LIMIARES_EM_MOVIMENTO=PASS")
