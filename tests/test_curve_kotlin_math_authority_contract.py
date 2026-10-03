#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CURVE = (ROOT / "app/src/main/assets/ui/screens/curve.js").read_text("utf-8")

# A UI pode calcular coordenadas de desenho, mas não pode transformar delta de
# aprendizado em novo fator K. O alvo precisa chegar exato do Kotlin.
for forbidden in (
    "currentFactor * (1 + deltaPercent / 100)",
    "factor * (1 + Number(advice.suggestedDeltaPercent) / 100)",
    "Number(current.factor) * (1 + suggested / 100)",
):
    assert forbidden not in CURVE, forbidden

assert "this.api.previewCurvePoint(index, requested)" in CURVE
assert "A sugestão ainda não possui alvo K exato calculado pelo Kotlin" in CURVE
assert "A UI só desenha alvos K exatos vindos do Kotlin" in CURVE

print("CURVE_KOTLIN_MATH_AUTHORITY_CONTRACT=PASS")
