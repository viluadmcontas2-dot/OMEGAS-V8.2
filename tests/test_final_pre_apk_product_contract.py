from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
manager = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/KFactorManager.kt").read_text(encoding="utf-8")
service = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
bridge = (ROOT / "app/src/main/java/com/omegas/prohub/web/V7JavascriptBridge.kt").read_text(encoding="utf-8")
api = (ROOT / "app/src/main/assets/ui/core/native-api.js").read_text(encoding="utf-8")
curve = (ROOT / "app/src/main/assets/ui/screens/curve.js").read_text(encoding="utf-8")
index = (ROOT / "app/src/main/assets/ui/index.html").read_text(encoding="utf-8")
app = (ROOT / "app/src/main/assets/ui/app.js").read_text(encoding="utf-8")
autocal = (ROOT / "app/src/main/assets/ui/screens/autocal-cockpit.js").read_text(encoding="utf-8")
css = (ROOT / "app/src/main/assets/ui/styles-autocal-cockpit.css").read_text(encoding="utf-8")
base_css = (ROOT / "app/src/main/assets/ui/styles.css").read_text(encoding="utf-8")

# Curva K: backup explícito, persistente e restauração somente via writer já validado.
for marker in ("saveCurrentBackup(", "listBackups(", "prepareRestore("):
    assert marker in manager, marker
assert "MANUAL_SNAPSHOT" in manager
assert "PRE_WRITE" in manager
assert "KFactorBackupRetention.visibleFiles(backupDir.listFiles())" in manager
assert "KFactorBackupRetention.pruneAutomatic(backupDir)" in manager
assert "geometryMismatch" in manager
assert "startBatchWrite(points" in curve or "writeCurve(points" in curve
assert "saveKFactorBackup(" in service
assert "listKFactorBackups(" in service
assert "prepareKFactorRestore(" in service
assert "startCurveBackup(" in bridge
assert "listCurveBackups(" in bridge
assert "startCurveRestorePrepare(" in bridge
assert "startCurveBackup(" in api
assert "curveBackups()" in api
assert "prepareCurveRestore(" in api
assert 'id="curveBackupSave"' in index
assert 'id="curveBackupSelect"' in index
assert 'id="curveBackupRestore"' in index
assert "Restaurar backup" in index
assert "backupTask" in curve
assert "restoreContext" in curve
assert "Restaurar backup Curva K" in curve
assert "curveBackupSelect" in curve and "prepareRestore(" in curve
assert "curveBackupRestore" in curve and "writeRestore()" in curve
assert "this.writePrepared()" in curve
assert "Restauração pronta" in curve
assert "Restauração validada · iniciando escrita segura" not in curve
assert "classList.add('is-reviewing')" not in curve
assert 'id="curveWriteButton"' not in index
assert 'id="mapWriteButton"' not in index

# AutoCal: uma única identificação de rota e 18 regiões fora da superfície primária.
autocal_screen = index.split('data-screen="autocal"', 1)[1].split('data-screen="obd"', 1)[0]
assert "page-intro" not in autocal_screen
assert "autocal-focus" in app
assert ".app-shell.autocal-focus .workspace-head" in base_css
assert "display: none" in base_css.split(".app-shell.autocal-focus .workspace-head", 1)[1].split("}", 1)[0]
assert autocal.index('<details id="autocalTechnicalDetails"') < autocal.index('id="autocalBands"')
assert "18 REGIÕES · DETALHE TÉCNICO" in autocal
assert "height: clamp(340px, 52vh, 430px)" in css

print("FINAL_PRE_APK_PRODUCT_CONTRACT=PASS")

# Multimidia: sem scroll horizontal operacional e sem mini-scroll no card de revisão.
assert "overflow-x: auto" not in css
assert "overflow-x: scroll" not in css
review_card_block = css.split(".autocal-review-card {", 1)[1].split("}", 1)[0]
assert "overflow: auto" not in review_card_block
assert "overflow: scroll" not in review_card_block
assert "overflow: visible" in review_card_block
review_overlay_block = css.split(".autocal-review {", 1)[1].split("}", 1)[0]
assert "overflow-y: auto" in review_overlay_block
assert "overflow-x: hidden" in review_overlay_block
