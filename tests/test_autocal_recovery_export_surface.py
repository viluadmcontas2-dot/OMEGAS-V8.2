from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
api = (ROOT / "app/src/main/assets/ui/core/native-api.js").read_text(encoding="utf-8")
drawers = (ROOT / "app/src/main/assets/ui/components/drawers.js").read_text(encoding="utf-8")
index = (ROOT / "app/src/main/assets/ui/index.html").read_text(encoding="utf-8")

assert "exportData() { return this.demo ? false : invoke(this.native, 'exportData'" in api
assert 'id="toolExportData"' in index
assert "toolExportData" in drawers
assert "this.api.exportData()" in drawers

print("AUTOCAL_RECOVERY_EXPORT_SURFACE=PASS")
