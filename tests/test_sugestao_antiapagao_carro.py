"""A sugestão exibida na tela (SUGESTAO no JS) é exatamente a calculada dos logs do dono (JSON versionado) e sempre cai dentro das faixas do original."""
import json, re, pathlib
ROOT = pathlib.Path(__file__).resolve().parents[1]
J = json.loads((ROOT / "docs/progbase/bins/sugestao-antiapagao-carro-do-dono.json").read_text(encoding="utf-8"))
JS = (ROOT / "app/src/main/assets/ui/core/anti-apagao-valores.js").read_text(encoding="utf-8")
FAIXAS = [(0, 6000), (0, 1000), (0, 10000), (0, 1000), (0, 1000)]

def test_js_igual_ao_json():
    import subprocess, sys
    r = subprocess.run([sys.executable, "-B", str(ROOT / "tools/sessoes/gerar_sugestao_js.py"), "--check"])
    assert r.returncode == 0, "bloco SUGESTAO do JS diferente do JSON: rode tools/sessoes/gerar_sugestao_js.py"

def test_sugestao_dentro_das_faixas_do_original_e_baseada_em_evidencia():
    v = J["sugerido"]["valores"]
    assert all(lo <= x <= hi for x, (lo, hi) in zip(v, FAIXAS))
    # só sugere MANTER se com a função ligada o pior mínimo é >= 75% da marcha lenta típica (OBS-FIO)
    assert J["resultado"]["ligado_pior_minimo"] >= 0.75 * J["marcha_lenta_tipica_rpm"]
    assert J["resultado"]["desligado_pior_minimo"] < J["resultado"]["ligado_pior_minimo"], "a função ligada precisa ser melhor que desligada"
    assert J["faixa_rotacao_maxima"]["maximo"] >= max(e["rpm_inicio_da_queda"] for e in J["eventos"])

if __name__ == "__main__":
    test_js_igual_ao_json(); test_sugestao_dentro_das_faixas_do_original_e_baseada_em_evidencia()
    print("SUGESTAO_ANTIAPAGAO_CARRO=PASS")
