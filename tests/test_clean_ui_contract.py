import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/assets/ui"
MAIN = ROOT / "app/src/main/java/com/omegas/prohub/MainActivity.kt"


class CleanUiContract(unittest.TestCase):
    def setUp(self):
        self.html = (UI / "index.html").read_text("utf-8")
        self.css = (UI / "styles.css").read_text("utf-8")
        self.refine_css = (UI / "styles-refine.css").read_text("utf-8")
        self.obd_css = (UI / "styles-obd-evidence.css").read_text("utf-8")
        self.calibration_obd_css = (UI / "styles-calibration-obd.css").read_text("utf-8")
        self.app = (UI / "app.js").read_text("utf-8")
        self.store = (UI / "core/store.js").read_text("utf-8")
        self.router = (UI / "core/router.js").read_text("utf-8")
        self.scheduler = (UI / "core/scheduler.js").read_text("utf-8")
        self.native_api = (UI / "core/native-api.js").read_text("utf-8")
        self.grid = (UI / "components/physical-grid.js").read_text("utf-8")
        self.map_editor = (UI / "map-editor.js").read_text("utf-8")
        self.map_screen = (UI / "screens/map.js").read_text("utf-8")
        self.curve_screen = (UI / "screens/curve.js").read_text("utf-8")
        self.learning_screen = (UI / "screens/learning.js").read_text("utf-8")
        self.obd_screen = (UI / "screens/obd.js").read_text("utf-8")
        self.dashboard = (UI / "screens/dashboard.js").read_text("utf-8")

    def test_only_clean_ui_is_active(self):
        main = MAIN.read_text("utf-8")
        self.assertIn('file:///android_asset/ui/index.html', main)
        self.assertNotIn('android_asset/hub/', main)
        self.assertIn('<title>OMEGAS V8</title>', self.html)
        self.assertIn('aria-label="OMEGAS V8"', self.html)
        self.assertNotIn('drawerScrim', self.html)
        self.assertNotIn('rpm-gauge', self.html)
        self.assertNotIn('styles-expansion.css', self.html)
        self.assertNotIn('styles-expansion-panels.css', self.html)
        self.assertIn('styles-calibration-obd.css', self.html)
        self.assertIn("refinementStyle.href = 'styles-refine.css'", self.app)

    def test_seven_human_destinations_are_first_class(self):
        routes = re.findall(r'data-route="([^"]+)"', self.html)
        expected = ['dashboard', 'learning', 'map', 'curve', 'obd', 'suggestions', 'tools']
        self.assertEqual(expected, routes)
        for route in expected:
            self.assertIn(f'data-screen="{route}"', self.html)
        self.assertIn("['dashboard', 'learning', 'map', 'curve', 'obd', 'suggestions', 'tools']", self.router)
        for label in ('Agora', 'Aprender', 'Ajuste local', 'Ajuste global', 'OBD', 'Sugestões', 'Ferramentas'):
            self.assertIn(f'<span>{label}</span>', self.html)

    def test_one_store_one_router_one_scheduler(self):
        self.assertIn('class Store', self.store)
        self.assertIn('class Router', self.router)
        self.assertIn('class Scheduler', self.scheduler)
        self.assertEqual(1, self.scheduler.count('setInterval('))
        self.assertNotIn('setInterval(', self.app)
        active_sources = self.app + self.store + self.router + self.scheduler + self.map_screen + self.curve_screen + self.learning_screen + self.grid + self.obd_screen
        self.assertNotIn('MutationObserver', active_sources)
        self.assertNotIn('.onclick', active_sources)
        self.assertNotIn('tick:', self.store)
        self.assertNotIn('store.patch({ telemetry, tick })', self.app)

    def test_low_end_1280x720_design_budget(self):
        self.assertIn('--rail-width:202px', self.css)
        self.assertIn('grid-template-columns:var(--rail-width) minmax(0,1fr)', self.css)
        self.assertIn('contain:layout paint style', self.css)
        combined_css = self.css + self.obd_css + self.calibration_obd_css + self.refine_css
        for forbidden in ('backdrop-filter', '@keyframes', 'filter:brightness', 'linear-gradient', 'radial-gradient'):
            self.assertNotIn(forbidden, combined_css)
        self.assertNotRegex(self.calibration_obd_css, r'animation:(?!none)')
        self.assertNotRegex(self.calibration_obd_css, r'transition:(?!none)')
        self.assertIn('animation:none!important', self.calibration_obd_css)
        self.assertIn('filter:none!important', self.calibration_obd_css)
        self.assertNotIn('--rpm-ratio', self.app)
        self.assertNotIn('--rpm-ratio', self.css)

    def test_learning_fast_path_has_no_visual_live_tracing(self):
        self.assertNotIn('setTrace(', self.app)
        self.assertNotIn('TRACE_MAX_CONTRIBUTORS', self.grid)
        self.assertNotIn('TRACE_WEIGHT_STEPS', self.grid)
        self.assertNotIn('continuousWeights', self.learning_screen)
        self.assertNotIn('live-contributor', self.grid)
        self.assertNotIn('live-nearest', self.grid)
        self.assertIn('function renderLightLiveContext', self.app)
        self.assertIn("route === 'dashboard' || route === 'learning' || route === 'map'", self.app)
        self.assertIn("if (route === 'learning') setText('learningLiveLabel'", self.app)
        self.assertIn('A interpolação bilinear continua no Kotlin', self.learning_screen)

    def test_learning_grid_has_physical_axes_and_direct_edit_entrypoint(self):
        self.assertIn('physical-grid-with-axes', self.grid)
        self.assertIn('setAxes(rpmBins, petrolBins)', self.grid)
        self.assertIn('data-edit-learning-cell', self.learning_screen)
        self.assertIn("this.router.navigate('map'", self.learning_screen)
        self.assertIn("origin: 'learning'", self.learning_screen)
        self.assertIn('Abrir o editor não escreve na ECU', self.learning_screen)
        for forbidden in ('writeMap(', 'startKBatchWrite(', 'writeCurve('):
            self.assertNotIn(forbidden, self.learning_screen)

    def test_dashboard_prioritizes_rpm_and_groups_context(self):
        self.assertIn('dashHeroRpm', self.dashboard)
        self.assertIn('CONDIÇÃO DO MOTOR', self.dashboard)
        self.assertIn('hero-context-grid', self.dashboard)
        for marker in ('dashPetrol', 'dashMap', 'dashFuel', 'dashCell'):
            self.assertIn(marker, self.dashboard)
        self.assertIn('dashGas', self.dashboard)
        self.assertIn('dashStft', self.html)
        self.assertIn('dashLtft', self.html)

    def test_learning_map_curve_and_obd_have_expected_contracts(self):
        for layer in ('petrol', 'cng', 'comparison', 'suggestion'):
            self.assertIn(f'data-learning-layer="{layer}"', self.html)
        self.assertIn('id="mapSelectAll"', self.html)
        self.assertIn('id="curveChart"', self.html)
        self.assertIn('OBD é somente observação', self.html)

    def test_map_k_has_axes_now_and_bulk_selection_without_second_writer(self):
        self.assertIn('map-k-grid-with-axes', self.map_screen)
        self.assertIn('data-select-column', self.map_screen)
        self.assertIn('data-select-row', self.map_screen)
        self.assertIn('toggleColumn', self.map_editor)
        self.assertIn('toggleRow', self.map_editor)
        self.assertIn('MAX_SELECTION = ROWS * COLUMNS', self.map_editor)
        self.assertIn('mapLiveLabel', self.map_screen)
        self.assertIn('mapBackToLearning', self.map_screen)
        self.assertIn('targetOverrides', self.map_editor)
        self.assertIn('this.api.writeMap', self.map_screen)
        self.assertEqual(1, self.map_screen.count('this.api.writeMap'))

    def test_curve_k_has_global_learning_aligned_to_30_point_editor(self):
        self.assertIn('data-curve-view="learning"', self.html)
        self.assertIn('data-curve-view="editor"', self.html)
        self.assertIn('id="curveLearningChart"', self.html)
        self.assertIn('id="curveLearningSummary"', self.html)
        self.assertIn("Array.from({ length: 30 }", self.curve_screen)
        self.assertIn('ERRO GLOBAL · alvo 0%', self.curve_screen)
        self.assertIn('CURVA K · atual × proposta', self.curve_screen)
        self.assertIn('operation.points.length !== 30', self.curve_screen)
        self.assertIn('curve-point-hit', self.curve_screen)
        for nudge in ('-0.05', '-0.01', '0.01', '0.05'):
            self.assertIn(f'data-curve-nudge="{nudge}"', self.html)
        self.assertIn('this.api.previewCurvePoint', self.curve_screen)
        self.assertIn('this.api.writeCurve', self.curve_screen)
        self.assertIn('Gasolina × GNV por MAP', self.curve_screen)

    def test_obd_is_three_compact_views_on_rpm_petrol_axes(self):
        for view in ('observe', 'map', 'setup'):
            self.assertIn(f'data-obd-view="{view}"', self.html)
            self.assertIn(f'data-obd-panel="{view}"', self.html)
        self.assertIn('id="obdPetrol"', self.html)
        self.assertIn('PETROL INJ. ↓', self.html)
        self.assertIn('maps?.rpmBins', self.obd_screen)
        self.assertIn('maps?.petrolMsBins', self.obd_screen)
        self.assertNotIn('loadBins', self.obd_screen)
        self.assertNotIn('calculatedLoadPct', self.obd_screen)
        self.assertIn('GNV direto · alvo STFT 0%', self.obd_screen)
        self.assertIn('Bluetooth', self.obd_screen)
        self.assertIn('ELM327', self.obd_screen)
        self.assertIn('Protocolo', self.obd_screen)
        self.assertIn('Sensores', self.obd_screen)
        self.assertNotIn('setInterval(', self.obd_screen)
        for forbidden in ('writeMap', 'writeCurve', 'startKWrite', 'startKBatchWrite', 'startKFactorWrite'):
            self.assertNotIn(forbidden, self.obd_screen)

    def test_persistent_suggestions_are_review_only(self):
        self.assertIn('suggestionItems', self.app)
        self.assertIn("['PENDING', 'OBSERVING']", self.app)
        self.assertIn('Selecionar prontas', self.app)
        self.assertIn('Revisar selecionadas', self.app)
        self.assertIn("router.navigate('map'", self.app)
        self.assertIn("router.navigate('curve'", self.app)
        self.assertNotIn('applySuggestion(', self.app)
        self.assertNotIn('.writeMap(', self.app)
        self.assertNotIn('.writeCurve(', self.app)

    def test_tools_editing_is_not_replaced_by_periodic_render(self):
        self.assertIn('function toolsEditing()', self.app)
        self.assertIn("route === 'tools' && !toolsEditing()", self.app)
        self.assertIn('.diagnostic-settings-grid .check-setting input[type="checkbox"]', self.refine_css)
        self.assertIn('.log-filters select', self.refine_css)

    def test_map_and_curve_are_separate_and_review_before_write(self):
        self.assertIn('data-screen="map"', self.html)
        self.assertIn('data-screen="curve"', self.html)
        self.assertIn('Gravar alterações na ECU', self.html)
        self.assertIn('Gravar pontos na ECU', self.html)
        self.assertIn('this.api.writeMap', self.map_screen)
        self.assertIn('this.api.writeCurve', self.curve_screen)
        self.assertNotIn('startKBatchWrite(', self.map_screen)
        self.assertNotIn('startKFactorWrite(', self.curve_screen)

    def test_browser_demo_can_never_write_ecu(self):
        self.assertGreaterEqual(self.native_api.count('simulationOnly: true'), 2)
        self.assertIn("'startMapBatchWrite'", self.native_api)
        self.assertIn("'startCurveBatchWrite'", self.native_api)

    def test_technical_row_is_protected_and_full_grid_is_editable(self):
        self.assertIn('const MAX_SELECTION = ROWS * COLUMNS', self.map_editor)
        self.assertIn('selectAll()', self.map_editor)
        self.assertIn('row >= ROWS', self.map_editor)
        self.assertRegex(self.html, r'Linha técnica 0C[^<]*protegida')
        self.assertIn('technical-row-note', self.map_screen)

    def test_batch_success_still_requires_native_confirmation(self):
        bridge = (ROOT / "app/src/main/java/com/omegas/prohub/web/V7JavascriptBridge.kt").read_text("utf-8")
        writer = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/KWriteManager.kt").read_text("utf-8")
        self.assertIn('failure == null && completedCells == plan.totalCells', bridge)
        self.assertIn('.put("state", "BATCH_CONFIRMED")', bridge)
        self.assertIn('.put("readbackValid", true)', bridge)
        self.assertIn('BATCH_PARTIAL_FAILED', bridge)
        self.assertIn('requireAck', writer)
        self.assertIn('ECU_READBACK_NATIVE', writer)

    def test_no_duplicate_ids_in_html(self):
        ids = re.findall(r'id="([^"]+)"', self.html)
        duplicates = sorted({item for item in ids if ids.count(item) > 1})
        self.assertEqual([], duplicates)

    def test_no_private_signing_material_is_committed(self):
        forbidden = list(ROOT.rglob('*.jks')) + list(ROOT.rglob('*.keystore'))
        self.assertEqual([], forbidden)
        self.assertFalse((ROOT / 'ci/omegas-continuity.keystore.b64').exists())


if __name__ == '__main__':
    unittest.main()
