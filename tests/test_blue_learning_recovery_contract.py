#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSEMBLER = (ROOT / "app/src/main/java/com/omegas/prohub/learning/LearningUiSnapshotAssembler.kt").read_text()
PROJECTION = (ROOT / "app/src/main/java/com/omegas/prohub/learning/LearningGridProjection.kt").read_text()
STORE = (ROOT / "app/src/main/java/com/omegas/prohub/learning/BlueEvidenceStore.kt").read_text()

# RED #19: a projection that feeds Aprender may not erase comparisons that belong
# to Blue authority. The exact source can evolve, but hard-coded empty payloads are
# forbidden because they make `Desvio medido` empty by construction.
assert '.put("comparisons", JSONArray())' not in ASSEMBLER, (
    "LearningUiSnapshotAssembler ainda apaga todas as comparações Blue"
)
assert '.put("comparisonCount", 0)' not in ASSEMBLER, (
    "LearningUiSnapshotAssembler ainda publica comparisonCount=0 fixo"
)
assert '.put("comparison_count", 0)' not in ASSEMBLER, (
    "LearningUiSnapshotAssembler ainda publica comparison_count=0 fixo"
)

# RED #19: the evidence store persists physical quality as `quality`. Grid
# projection must preserve that value (or an explicit compatible fallback), not
# silently read a missing `confidence` field and turn valid evidence into 0%.
assert '.put("quality", sample.quality)' in STORE, "Blue store precisa continuar preservando quality física"
quality_fallback = (
    'region.optDouble("quality"' in PROJECTION
    or 'region.optDouble("confidence", region.optDouble("quality"' in PROJECTION
)
assert quality_fallback, (
    "LearningGridProjection não consome `quality`; evidência válida vira qualidade 0% na UI"
)

print("BLUE_LEARNING_RECOVERY_CONTRACT=PASS")
