import unittest

from lab.red_blend.performance_gate import classify_runtime_delta


class PerformanceGateTest(unittest.TestCase):
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

    def test_any_app_delta_blocks_structural_performance_promotion(self):
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
