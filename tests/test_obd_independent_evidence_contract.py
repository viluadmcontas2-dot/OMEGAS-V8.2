from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANAGER = ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt"
LEGACY_INDEPENDENT = ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdIndependentEvidenceMap.kt"
UI = ROOT / "app/src/main/assets/ui/screens/obd.js"
HTML = ROOT / "app/src/main/assets/ui/index.html"

manager = MANAGER.read_text(encoding="utf-8")
legacy = LEGACY_INDEPENDENT.read_text(encoding="utf-8")
ui = UI.read_text(encoding="utf-8")
html = HTML.read_text(encoding="utf-8")
combined_obd = manager + "\n" + legacy + "\n" + ui

# A prova operacional OBD usa exatamente o mesmo espaço físico do aprendizado:
# RPM da MP48 x Petrol Inj. da MP48. Carga/MAP continuam apenas como contexto OBD.
assert "KMapPhysicalAxes.rpmBins" in manager
assert "KMapPhysicalAxes.petrolBins" in manager
assert '.put("rpmBins"' in manager
assert '.put("petrolMsBins"' in manager
assert 'nearest(rpm, RPM_BINS)' in manager
assert 'nearest(petrol, PETROL_MS_BINS)' in manager
assert "maps?.rpmBins" in ui
assert "maps?.petrolMsBins" in ui
assert "RPM × Petrol Inj." in ui or "RPM × Petrol Inj." in html
assert "PETROL INJ. ↓" in html
assert "calculatedLoadPct" not in ui
assert "loadBins" not in ui

# A camada principal usa STFT do GNV diretamente; gasolina permanece contexto.
assert "compare?.gnv" in ui
assert "alvo STFT 0%" in ui
assert "Gasolina" in html and "GNV" in html
assert 'data-obd-map-layer="gasoline"' in html
assert 'data-obd-map-layer="gnv"' in html
assert 'data-obd-map-layer="comparison"' in html
assert "deltaStft" not in ui

# A implementação RPM x carga antiga pode permanecer apenas como legado de arquivo,
# mas não é a superfície ativa apresentada ao usuário.
assert '.put("y", "calculatedLoadPct")' in legacy
assert "Mapa independente RPM × carga" not in ui
assert "Mapa independente RPM × carga" not in html

# PIDs adicionais continuam condicionados ao anúncio da ECU.
for command in ("0104", "010B", "010F", "0110", "012F", "0142"):
    assert command in manager, f"PID {command} ausente do coletor OBD"
assert "readSupportedPidTimed" in manager
assert "supportedStandardPids" in manager

# OBD permanece estritamente observacional: nenhum caminho de escrita ECU.
for forbidden in (
    "startKWrite(",
    "startKBatchWrite(",
    "startKFactorWrite(",
    "KWriteManager(",
    "KFactorManager(",
    ".writeMap(",
    ".writeCurve(",
):
    assert forbidden not in combined_obd, f"OBD ganhou caminho proibido de escrita: {forbidden}"

print("OBD_RPM_PETROL_STFT_CONTRACT=PASS")
