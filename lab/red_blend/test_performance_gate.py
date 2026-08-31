import unittest

from lab.red_blend.performance_gate import classify_runtime_delta


PINNED_UI_BLOBS = {
    "app/src/main/assets/ui/screens/learning.js": "ef05bb106e68e3fa23443596357eaa509cbae04c",
    "app/src/main/java/com/omegas/v7/runtime/V7UiProjection.kt": "af1d38597768739793f4c40650854ca1512024bf",
}

PINNED_ENGINE_CONSOLIDATION_BLOBS = {
    "app/src/main/java/com/omegas/prohub/learning/PredictorSurface.kt": "49f12ec28bc0b09cddf0cfa140118a0ee9335b4b",
    "app/src/main/java/com/omegas/prohub/service/V7CalibrationAccess.kt": "d5f7955d319a9ec7979d13a69f6ab88c5e47e907",
    "app/src/main/java/com/omegas/prohub/learning/PredictorInterpolator.kt": "MISSING",
    "app/src/main/java/com/omegas/prohub/learning/PredictorSpatialConfidence.kt": "MISSING",
}


class PerformanceGateTest(unittest.TestCase):
    def test_exact_reviewed_engine_consolidation_preserves_red_core_and_requires_android_validation(self):
        result = classify_runtime_delta(
            [*PINNED_ENGINE_CONSOLIDATION_BLOBS],
            current_blobs=PINNED_ENGINE_CONSOLIDATION_BLOBS,
        )

        self.assertEqual("RED_HOT_PATH_PRESERVED_PINNED_NON_HOT_PATH_DELTA", result.status)
        self.assertEqual((), result.runtime_input_changes)
        self.assertEqual(tuple(sorted(PINNED_ENGINE_CONSOLIDATION_BLOBS)), result.non_hot_path_runtime_changes)
        self.assertTrue(result.hot_path_preserved)
        self.assertFalse(result.android_runtime_identical)
        self.assertTrue(result.requires_full_android_validation)

    def test_offline_science_only_delta_preserves_red_android_runtime_inputs(self):
        result = classify_runtime_delta(
            [
                "lab/red_blend/ood_falsification.py",
                "tests/fixtures/science/episodes/index.json",
                "docs/evidence/red-blend-risk-coverage.md",
                ".github/workflows/red-v82-science-blend.yml",
                "STATUS.md",
                "tools/science/reconstruct_fixture.py",
            ]
        )
        self.assertEqual("RED_ANDROID_RUNTIME_INPUTS_IDENTICAL", result.status)
        self.assertEqual((), result.runtime_input_changes)
        self.assertTrue(result.hot_path_preserved)
        self.assertTrue(result.android_runtime_identical)
        self.assertFalse(result.requires_full_android_validation)

    def test_exact_reviewed_ui_projection_blobs_preserve_hot_path_but_not_runtime_identity(self):
        changed = [
            *PINNED_UI_BLOBS,
            "app/src/test/java/com/omegas/v7/runtime/V7SessionRuntimeTest.kt",
        ]
        result = classify_runtime_delta(changed, current_blobs=PINNED_UI_BLOBS)

        self.assertEqual("RED_HOT_PATH_PRESERVED_PINNED_NON_HOT_PATH_DELTA", result.status)
        self.assertEqual((), result.runtime_input_changes)
        self.assertEqual(tuple(sorted(PINNED_UI_BLOBS)), result.non_hot_path_runtime_changes)
        self.assertTrue(result.hot_path_preserved)
        self.assertFalse(result.android_runtime_identical)
        self.assertTrue(result.requires_full_android_validation)

    def test_same_ui_path_with_unreviewed_blob_fails_closed(self):
        path = "app/src/main/assets/ui/screens/learning.js"
        result = classify_runtime_delta([path], current_blobs={path: "deadbeef"})
        self.assertEqual("BLOCKED_RUNTIME_INPUT_DELTA", result.status)
        self.assertEqual((path,), result.runtime_input_changes)
        self.assertFalse(result.hot_path_preserved)

    def test_unknown_app_delta_blocks_structural_hot_path_claim(self):
        result = classify_runtime_delta(["app/src/main/java/com/omegas/Foo.kt"])
        self.assertEqual("BLOCKED_RUNTIME_INPUT_DELTA", result.status)
        self.assertEqual(("app/src/main/java/com/omegas/Foo.kt",), result.runtime_input_changes)
        self.assertFalse(result.hot_path_preserved)

    def test_build_configuration_delta_also_blocks(self):
        for path in (
            "app/build.gradle.kts",
            "build.gradle.kts",
            "settings.gradle.kts",
            "gradle.properties",
            "gradle/wrapper/gradle-wrapper.properties",
            "config/omegas-release.json",
        ):
            with self.subTest(path=path):
                result = classify_runtime_delta([path])
                self.assertEqual("BLOCKED_RUNTIME_INPUT_DELTA", result.status)
                self.assertFalse(result.hot_path_preserved)

    def test_unknown_top_level_delta_fails_closed_instead_of_being_assumed_safe(self):
        result = classify_runtime_delta(["mystery/runtime-loader.conf"])
        self.assertEqual("BLOCKED_UNCLASSIFIED_DELTA", result.status)
        self.assertEqual(("mystery/runtime-loader.conf",), result.unclassified_changes)
        self.assertFalse(result.hot_path_preserved)

    def test_red_blend_evidence_is_offline_without_whitelisting_other_namespaces(self):
        governed = classify_runtime_delta(
            ["evidence/red_blend/full_corpus/mechanistic-asu-simulation.json"]
        )
        unknown = classify_runtime_delta(["evidence/other/runtime-loader.conf"])

        self.assertEqual("RED_ANDROID_RUNTIME_INPUTS_IDENTICAL", governed.status)
        self.assertTrue(governed.hot_path_preserved)
        self.assertEqual("BLOCKED_UNCLASSIFIED_DELTA", unknown.status)
        self.assertFalse(unknown.hot_path_preserved)


if __name__ == "__main__":
    unittest.main()
