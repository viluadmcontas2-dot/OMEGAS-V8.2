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
        # Predictor is an optional visual extension. It may add one route,
        # but it must reuse the same Router/Store/Scheduler instead of changing
        # the seven static destinations baked into the base HTML shell.
        self.assertIn("const ROUTES = ['dashboard', 'learning', 'predictor', 'map', 'curve', 'obd', 'suggestions', 'tools']", self.router)
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

    def test_learning_fast_path_has_bounded_visual_trace_without_weight_chasing(self):
        # The live path must not chase bilinear contributors from app.js or the
        # Learning renderer. A small temporal trace is intentionally owned by
        # PhysicalGrid (#46), with a strict budget and no timer/writer path.
        self.assertNotIn('setTrace(', self.app)
        self.assertNotIn('TRACE_MAX_CONTRIBUTORS', self.grid)
        self.assertNotIn('TRACE_WEIGHT_STEPS', self.grid)
        self.assertNotIn('continuousWeights', self.learning_screen)
        self.assertIn('setTrace(', self.grid)
        self.assertIn('traceTrailMs = 1400', self.grid)
        self.assertIn('traceTrailMax = 16', self.grid)
        self.assertIn('live-contributor', self.grid)
        self.assertIn('live-nearest', self.grid)
        self.assertIn('live-trail', self.grid)
        self.assertNotIn('setInterval(', self.grid)
        self.assertNotIn('setTimeout(', self.grid)
        self.assertNotRegex(self.grid, r'writeMap|startMapBatchWrite|protocolTransaction')
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

    def test_dashboard_prioritizes_petrol_injection_and_groups_context(self):
        self.assertIn('PETROL INJECTION', self.dashboard)
        self.assertIn('dashHeroPetrol', self.dashboard)
        self.assertIn('now-dashboard-shell', self.dashboard)
        for marker in ('dashRpm', 'dashMap', 'dashFuel', 'dashStft', 'dashCell'):
            self.assertIn(marker, self.dashboard)
        self.assertNotIn('dashHeroRpm', self.dashboard)
        self.assertNotIn('dashGas', self.dashboard)

    def test_learning_map_curve_and_obd_have_expected_contracts(self):
        for layer in ('petrol', 'cng', 'comparison', 'suggestion'):
            self.assertIn(f'data-learning-layer="{layer}"', self.html)
        self.assertIn('id="mapSelectAll"', self.html)
        self.assertIn('id="curveChart"', self.html)
        self.assertIn('OBD é somente observação', self.html)

    def test_map_k_has_axes_now_and_bulk_selection_without_second_writer(self):
        self.assertIn('id="mapNowCell"', self.html)
        self.assertIn('id="mapBulkAction"', self.html)
        self.assertIn('id="mapSelectAll"', self.html)
        self.assertIn('data-map-bulk', self.map_screen)
        self.assertIn('MapKPhysicalAxes', self.map_editor)
        self.assertNotIn('writeMap(', self.map_screen)

    def test_curve_k_is_global_and_manual(self):
        self.assertIn('Curva K', self.curve_screen)
        self.assertIn('global', self.curve_screen.lower())
        self.assertNotIn('writeCurve(', self.curve_screen)

    def test_obd_is_observation_only(self):
        self.assertIn('OBD é somente observação', self.html)
        for forbidden in ('writeMap(', 'writeCurve(', 'startKBatchWrite('):
            self.assertNotIn(forbidden, self.obd_screen)

    def test_native_api_is_single_bridge_surface(self):
        self.assertIn('class NativeApi', self.native_api)
        self.assertNotIn('window.Android.', self.app)
        self.assertNotIn('window.Android.', self.map_screen)
        self.assertNotIn('window.Android.', self.curve_screen)
        self.assertNotIn('window.Android.', self.learning_screen)

    def test_no_runtime_ui_uses_legacy_hub_assets(self):
        active_sources = self.html + self.app + self.store + self.router + self.scheduler + self.grid + self.map_screen + self.curve_screen + self.learning_screen
        for marker in ('android_asset/hub/', '/hub/', 'hub/index.html'):
            self.assertNotIn(marker, active_sources)

    def test_map_editor_remains_manual_review_surface(self):
        self.assertIn('human_confirmation_required', self.map_editor)
        self.assertIn('readback', self.map_editor.lower())
        self.assertNotIn('automatic_write', self.map_editor)

    def test_learning_route_never_calls_writer(self):
        for forbidden in ('writeMap(', 'writeCurve(', 'startKBatchWrite(', 'protocolTransaction'):
            self.assertNotIn(forbidden, self.learning_screen)

    def test_suggestions_route_is_review_not_auto_apply(self):
        suggestions = (UI / 'screens/suggestions.js').read_text('utf-8')
        self.assertIn('manual', suggestions.lower())
        self.assertNotIn('autoApply', suggestions)

    def test_map_and_curve_share_router_state(self):
        self.assertIn("route === 'map'", self.app)
        self.assertIn("route === 'curve'", self.app)
        self.assertIn('navigate', self.router)

    def test_scheduler_has_single_timer_budget(self):
        self.assertEqual(1, self.scheduler.count('setInterval('))
        self.assertNotIn('setInterval(', self.grid)
        self.assertNotIn('setTimeout(', self.grid)

    def test_grid_selection_does_not_create_second_state_store(self):
        self.assertNotIn('new Store(', self.grid)
        self.assertNotIn('localStorage', self.grid)

    def test_no_opacity_or_filter_animation_in_active_grid(self):
        combined_css = self.css + self.refine_css + self.obd_css + self.calibration_obd_css
        self.assertNotIn('@keyframes', combined_css)
        self.assertNotIn('backdrop-filter', combined_css)


if __name__ == '__main__':
    unittest.main()
