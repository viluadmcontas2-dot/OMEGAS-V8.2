from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
detector = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeOutlierDetector.kt").read_text(encoding="utf-8")
manager = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt").read_text(encoding="utf-8")
ledger = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/EquivalenceLedger.kt").read_text(encoding="utf-8")

# A inteligência de limpeza reaproveita a mesma rejeição robusta do Refino e nunca
# deriva um limiar apenas de "X% abaixo da curva".
assert "AutoMatchRefinedEngine.monotoneFit" in detector
assert "OUTLIER_MAD_K" in detector
assert "MIN_DOWN_GAP_BAR" in detector
assert "confirmationsRequired" in detector
assert "generation" in detector
assert "markSubmitted" in detector

# O detector é puro: não ganha acesso a serial/writer. A mutação continua no caminho
# explícito já existente do AutoCal.
assert "Mp48WorkClass" not in detector
assert "transaction(" not in detector
assert "executeAutomaticPointDelete" not in manager
assert "fun preparePointDelete" in manager
assert "fun preparePointDeletes" in manager

# Refino continua recusando lenta e ruído antes de produzir a evidência de condução.
assert "DRIVING_MIN_RPM = 1_000.0" in ledger
assert "fun drivingPairs()" in ledger

print("AUTOCAL_AUTO_CLEANUP_CONTRACT=PASS")
