from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AUTOCAL = ROOT / "app/src/main/java/com/omegas/prohub/autocal"
FLAG = (AUTOCAL / "AntiStalloFlagWriter.kt").read_text(encoding="utf-8")
PARAMS = (AUTOCAL / "AntiStalloParamsWriter.kt").read_text(encoding="utf-8")
BRIDGE = (AUTOCAL / "AutoCalJavascriptBridge.kt").read_text(encoding="utf-8")
SERVICE = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
UI = ROOT / "app/src/main/assets/ui/core"


def corpo(texto: str, inicio: str, fim: str) -> str:
    a = texto.index(inicio)
    return texto[a:texto.index(fim, a)]


def test_escritores_gravam_numa_unica_etapa_manual_write():
    for nome, fonte, fim in (("flag", FLAG, "    /** 1º toque"), ("params", PARAMS, "    /** 1º toque")):
        apply = corpo(fonte, "    fun apply(", fim)
        assert apply.count("serial.unit(") == 1, nome
        assert "Mp48WorkClass.MANUAL_WRITE" in apply, nome
        assert "READ_ONLY" not in apply, nome  # faixa de leitura recusa na hora quando a serial está ocupada
        assert "esperandoAVez" not in apply, nome
        assert "execute(io(unit)" in apply, nome  # ler -> montar -> gravar -> reler dentro da mesma etapa


def test_ponte_e_servico_expoem_a_chamada_unica():
    assert "fun applyAntiStalloFlag(enable: Boolean)" in BRIDGE
    assert "fun applyAntiStalloValues(changesJson: String)" in BRIDGE
    assert "antiStalloWriter.apply(enable)" in SERVICE
    assert "antiStalloParamsWriter.apply(changesJson)" in SERVICE


def test_js_prefere_a_chamada_unica():
    flags = (UI / "advanced-flags.js").read_text(encoding="utf-8")
    valores = (UI / "anti-apagao-valores.js").read_text(encoding="utf-8")
    assert flags.index("applyAntiStalloFlag") < flags.index("prepareAntiStalloFlag(enable))")
    assert valores.index("applyAntiStalloValues") < valores.index("prepareAntiStalloValues(JSON.stringify")


if __name__ == "__main__":
    test_escritores_gravam_numa_unica_etapa_manual_write()
    test_ponte_e_servico_expoem_a_chamada_unica()
    test_js_prefere_a_chamada_unica()
    print("ANTI_APAGAO_CHAMADA_UNICA=PASS")
