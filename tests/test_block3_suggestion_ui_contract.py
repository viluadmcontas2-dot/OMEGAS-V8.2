import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/assets/ui"


class Block3SuggestionUiContract(unittest.TestCase):
    def setUp(self):
        self.html = (UI / "index.html").read_text("utf-8")
        self.app = (UI / "app.js").read_text("utf-8")
        self.learning = (UI / "screens/learning.js").read_text("utf-8")
        self.map_screen = (UI / "screens/map.js").read_text("utf-8")
        self.curve_screen = (UI / "screens/curve.js").read_text("utf-8")
        self.runtime = (ROOT / "app/src/main/java/com/omegas/v7/runtime/V7SessionRuntime.kt").read_text("utf-8")
        self.coordinator = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/V7CalibrationCoordinator.kt").read_text("utf-8")
        self.bridge = (ROOT / "app/src/main/java/com/omegas/prohub/web/V7JavascriptBridge.kt").read_text("utf-8")

    def test_persistent_queue_has_explicit_lifecycle(self):
        for marker in (
            "PENDING", "OBSERVING", "APPLIED", "SUPERSEDED",
            "fun replaceSuggestions", "actionableAt",
        ):
            self.assertIn(marker, self.runtime)
        for marker in (
            'suggestionPending', 'suggestionObserving', 'suggestionApplied',
            'suggestionSuperseded', 'suggestionItems',
        ):
            self.assertIn(marker, self.coordinator)

    def test_queue_ui_separates_pending_observing_and_applied(self):
        self.assertIn('suggestion-queue-summary', self.app)
        self.assertIn('PENDENTES', self.app)
        self.assertIn('OBSERVANDO', self.app)
        self.assertIn('APLICADAS', self.app)
        self.assertIn('Selecionar prontas', self.app)
        self.assertIn('Revisar selecionadas', self.app)
        self.assertIn('selectedSuggestionIds', self.app)

    def test_review_actions_only_navigate_to_official_editors(self):
        self.assertIn("router.navigate('map'", self.app)
        self.assertIn("router.navigate('curve'", self.app)
        self.assertNotIn('.writeMap(', self.app)
        self.assertNotIn('.writeCurve(', self.app)
        self.assertNotIn('startKBatchWrite(', self.app)
        self.assertNotIn('startKFactorWrite(', self.app)
        self.assertIn('Sugestão não escreve diretamente', self.bridge)
        self.assertIn('MANUAL_REVIEW_REQUIRED', self.bridge)

    def test_learning_cell_can_open_same_map_editor_without_writing(self):
        self.assertIn('data-edit-learning-cell', self.learning)
        self.assertIn("this.router.navigate('map'", self.learning)
        self.assertIn("origin: 'learning'", self.learning)
        self.assertIn('cell: { row, column }', self.learning)
        self.assertNotIn('writeMap(', self.learning)
        self.assertIn('applyContext(context)', self.map_screen)
        self.assertIn('this.editor.selectOnly(row, column)', self.map_screen)

    def test_persistent_local_targets_are_loaded_exactly_into_map_review(self):
        self.assertIn('suggestion?.mapChanges', self.map_screen)
        self.assertIn('this.editor.setTargetOverrides(changes)', self.map_screen)
        self.assertIn('this.api.writeMap', self.map_screen)
        self.assertEqual(1, self.map_screen.count('this.api.writeMap'))

    def test_persistent_curve_targets_go_through_native_preview_before_review(self):
        self.assertIn('suggestion.curveChanges', self.curve_screen)
        self.assertIn('this.api.previewCurvePoint(index, requested)', self.curve_screen)
        self.assertIn('this.acceptPreview(preview, true)', self.curve_screen)
        self.assertIn('this.api.writeCurve', self.curve_screen)
        self.assertEqual(1, self.curve_screen.count('this.api.writeCurve'))

    def test_suggestion_route_remains_first_class(self):
        self.assertIn('data-screen="suggestions"', self.html)
        self.assertIn('id="suggestionList"', self.html)
        self.assertIn("route === 'suggestions'", self.app)


if __name__ == '__main__':
    unittest.main()
