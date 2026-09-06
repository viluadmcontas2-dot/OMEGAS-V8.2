#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


text = SERVICE.read_text(encoding="utf-8")
text = replace_once(
    text,
    "import com.omegas.prohub.obd.ObdAssistManager\nimport com.omegas.prohub.obd.ObdWitnessEngine",
    "import com.omegas.prohub.obd.ObdAssistManager\nimport com.omegas.prohub.obd.ObdFuelState\nimport com.omegas.prohub.obd.ObdWitnessEngine",
    "import",
)
text = replace_once(
    text,
    '        val fuel = frame.optString("fuel", "").trim().uppercase()\n        val skewMs = frame.optLong("skew_ms", Long.MAX_VALUE)\n        if (rpm <= 0.0 || mapBar <= 0.0 || petrolMs <= 0.0 || skewMs < 0L || skewMs > 250L) return\n        if (fuel !in setOf("PETROL", "GASOLINA", "CNG", "GNV", "GAS")) return',
    '        val fuel = frame.optString("fuel", "").trim().uppercase()\n        val scientificFuel = ObdFuelState.normalize(fuel) ?: return\n        val skewMs = frame.optLong("skew_ms", Long.MAX_VALUE)\n        if (rpm <= 0.0 || mapBar <= 0.0 || petrolMs <= 0.0 || skewMs < 0L || skewMs > 250L) return',
    "fuel gate",
)
text = replace_once(
    text,
    "                fuel = fuel,\n                calibrationState = calibrationState,",
    "                fuel = scientificFuel.name,\n                calibrationState = calibrationState,",
    "canonical scientific fuel",
)
text = replace_once(
    text,
    '            .put("residualPp", result.residualPp ?: JSONObject.NULL)\n            .put("quality", result.quality)',
    '            .put("residualPp", result.residualPp ?: JSONObject.NULL)\n            .put("correctionRatio", result.correctionRatio ?: JSONObject.NULL)\n            .put("errorLog", result.errorLog ?: JSONObject.NULL)\n            .put("correctionPercent", result.correctionPercent ?: JSONObject.NULL)\n            .put("quality", result.quality)',
    "physical correction payload",
)
text = replace_once(
    text,
    '            .put("fuel", fuel)\n            .put("skew_ms", skewMs)',
    '            .put("fuel", fuel)\n            .put("scientificFuel", scientificFuel.name)\n            .put("skew_ms", skewMs)',
    "scientific fuel payload",
)
SERVICE.write_text(text, encoding="utf-8")
print("OBD_FUEL_GATE_APPLICATOR=READY")
