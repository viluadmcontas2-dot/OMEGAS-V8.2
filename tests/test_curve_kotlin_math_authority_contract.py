#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CURVE = (ROOT / "app/src/main/assets/ui/screens/curve.js").read_text("utf-8")
ADAPTER = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/AdvisorSuggestionAdapter.kt").read_text("utf-8")

# A UI pode calcular coordenadas de desenho, mas não pode transformar delta de
# aprendizado em novo fator K. O alvo precisa chegar exato do Kotlin.
for forbidden in (
    "currentFactor * (1 + deltaPercent / 100)",
    "factor * (1 + Number(advice.suggestedDeltaPercent) / 100)",
    "Number(current.factor) * (1 + suggested / 100)",
):
    assert forbidden not in CURVE, forbidden

assert "this.api.previewCurvePoint(index, requested)" in CURVE
# (dono, 2026-10-11: texto para leigo): sem "Kotlin" na tela; a regra continua no código: a proposta desenhada só vem da prévia
# nativa ou do alvo exato registrado, e sugestão sem alvo exato não prepara nada.
assert "Esta sugestão ainda não tem um valor exato para gravar." in CURVE

# A adaptação científica/quantização continua no Kotlin.
assert "KFactorProtocol.rawFromFactor" in ADAPTER
assert "KFactorProtocol.factorFromRaw" in ADAPTER
assert "CurvePointChange" in ADAPTER

print("CURVE_KOTLIN_MATH_AUTHORITY_CONTRACT=PASS")
