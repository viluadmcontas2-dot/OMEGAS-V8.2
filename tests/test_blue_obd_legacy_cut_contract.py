#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


# O runtime Blue não pode continuar carregando a antiga segunda ciência OBD.
for removed in [
    "app/src/main/java/com/omegas/prohub/obd/ObdConditionEngine.kt",
    "app/src/main/java/com/omegas/prohub/obd/ObdEvidenceLedger.kt",
    "app/src/main/java/com/omegas/prohub/obd/ObdIndependentEvidenceMap.kt",
    "app/src/main/java/com/omegas/prohub/obd/ObdLearningGate.kt",
    "app/src/test/java/com/omegas/prohub/obd/ObdConditionEngineTest.kt",
    "app/src/test/java/com/omegas/prohub/obd/ObdEvidenceLedgerTest.kt",
    "app/src/test/java/com/omegas/prohub/obd/ObdIndependentEvidenceMapTest.kt",
    "app/src/main/assets/ui/components/floating-telemetry.js",
    "app/src/main/assets/ui/styles-floating-telemetry.css",
    "tests/ui/floating-telemetry.test.cjs",
]:
    assert not (ROOT / removed).exists(), f"legado ainda embarcado: {removed}"

assist = read("app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt")
service = read("app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt")
bridge = read("app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt")
api = read("app/src/main/assets/ui/core/native-api.js")
archive = read("app/src/main/java/com/omegas/prohub/learning/LearningArchiveManager.kt")

# Aquisição científica LIVE: STFT 0106. Demais PIDs não podem sobreviver no manager.
assert '"0106"' in assist
for forbidden in [
    '"0107"', '"010D"', '"0105"', '"0104"', '"0111"',
    '"010B"', '"010F"', '"0110"', '"012F"', '"0142"',
    "ContextReadings", "CellStats", "ObdConditionEngine", "ObdEvidenceLedger",
    "ObdIndependentEvidenceMap", "localMaps", "remoteComponents", "fusedMap(",
    "readContext(", "calculatedLoadPct", "ltft",
]:
    assert forbidden not in assist, f"scanner/ciência OBD antiga ainda no runtime: {forbidden}"

# Não existe mais superfície pública de mapa OBD paralelo.
for forbidden in ["getObdMaps", "obdMapsJson"]:
    assert forbidden not in bridge + service, f"API OBD legada ainda alcançável: {forbidden}"
for forbidden in ["demoObdMaps", "obdMaps()"]:
    assert forbidden not in api, f"frontend ainda expõe mapa OBD legado: {forbidden}"

# CalibrationStateID deve vir do estado canônico Blue, nunca do mapa/epoch OBD antigo.
assert "blueCalibrationStateId()" in service
assert "obd?.mapsJson()" not in service
assert "mapEpochId" not in service
assert "curveEpochId" not in service

# Arquivo portátil preserva aprendizado Blue e histórico K, mas não serializa mapas OBD extintos.
assert '.put("obd",' not in archive
assert 'optJSONObject("obd")' not in archive

print("BLUE_OBD_LEGACY_CUT_CONTRACT=PASS")
