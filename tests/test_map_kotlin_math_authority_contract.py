#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EDITOR = (ROOT / "app/src/main/assets/ui/map-editor.js").read_text("utf-8")
SCREEN = (ROOT / "app/src/main/assets/ui/screens/map.js").read_text("utf-8")
API = (ROOT / "app/src/main/assets/ui/core/native-api.js").read_text("utf-8")
PLANNER = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/MapKManualPlanner.kt").read_text("utf-8")
BRIDGE = (ROOT / "app/src/main/java/com/omegas/prohub/web/V7JavascriptBridge.kt").read_text("utf-8")

# Produção: seleção e revisão ficam na UI; transformação percentual/delta/target
# em valor K exato fica no Kotlin.
assert "targetFor(current)" not in EDITOR
assert "current * (1 + this.adjustment / 100)" not in EDITOR
assert "current + this.adjustment" not in EDITOR
assert "applyNativePreview(items)" in EDITOR
assert "Gere a prévia nativa antes de revisar" in EDITOR

assert "this.api.previewMapAdjustment" in SCREEN
assert "this.editor.selectedCells()" in SCREEN
assert "this.editor.applyNativePreview(preview.items)" in SCREEN
assert "current * (1 +" not in SCREEN
assert "current + this.adjustment" not in SCREEN

assert "previewMapAdjustment(cells, mode, adjustment)" in API
assert "invoke(this.v7, 'previewMapAdjustment'" in API
assert "simulationOnly: true" in API  # cálculo JS restante pertence apenas ao adaptador demo/Netlify.

assert "object MapKManualPlanner" in PLANNER
assert '"percent" -> current * (1.0 + adjustment / 100.0)' in PLANNER
assert '"delta" -> current + adjustment' in PLANNER
assert "coerceIn(MINIMUM_K, MAXIMUM_K)" in PLANNER
assert "automatic\", false" in PLANNER
assert "requiresReview\", true" in PLANNER

assert "MapKManualPlanner.preview(cellsJson, mode, adjustment)" in BRIDGE
assert "fun previewMapAdjustment" in BRIDGE

print("MAP_KOTLIN_MATH_AUTHORITY_CONTRACT=PASS")
