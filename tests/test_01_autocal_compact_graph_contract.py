from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
cockpit = (ROOT / "app/src/main/assets/ui/screens/autocal-cockpit.js").read_text(encoding="utf-8")
css = (ROOT / "app/src/main/assets/ui/styles-autocal-cockpit.css").read_text(encoding="utf-8")
shared_css = (ROOT / "app/src/main/assets/ui/styles-autocal-refino.css").read_text(encoding="utf-8")

assert "autocal-zone-strip" not in cockpit, "Z1-Z4 não devem ocupar uma faixa separada acima do gráfico"
assert 'id="autocalZoneMeter"' not in cockpit, "estado das zonas pertence ao próprio gráfico"
assert "data-autocal-zone-surface" in (ROOT / "app/src/main/assets/ui/components/curve-chart.js").read_text(encoding="utf-8")

main_at = cockpit.index("autocal-main-actions")
pause_at = cockpit.index("data-autocal-toggle")
assert pause_at > main_at, "Pausar/retomar deve ficar na barra operacional abaixo do gráfico"

point_block = cockpit[cockpit.index("autocal-point-actions"):cockpit.index("autocalRelearnNote")]
assert "data-autocal-reacquire-point" not in point_block
assert "data-autocal-toggle-point-selection" not in point_block
for required in (
    "data-autocal-reacquire-selected",
    "data-autocal-clear-point-selection",
    "data-autocal-toggle",
    "data-autocal-done-points",
):
    assert required in point_block

assert "pointSelectionMode" in cockpit
assert "toggleAcquiredPointSelection" in cockpit

key_start = cockpit.index("const key = JSON.stringify")
key_end = cockpit.index("if (this.epochChartHost", key_start)
key_block = cockpit[key_start:key_end]
assert "ACQUIRED_ZONES_PETROL" not in key_block
assert "ACQUIRED_ZONES_GAS" not in key_block
assert "MNFLD_PRESS_THD" in key_block
assert "renderGraphZoneState" in cockpit

combined = css + "\n" + shared_css
assert ".ar-shell.ar-autocal .ar-chart-host" in combined
assert "autocal-point-actions" in css
assert "flex-wrap: nowrap" in css

print("AUTOCAL_COMPACT_GRAPH_CONTRACT=PASS")
