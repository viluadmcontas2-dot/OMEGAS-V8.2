"""Fail-closed structural baseline guard for OMEGAS V8.2 RED.

RED remains the comparison anchor and fallback. Runtime or build-input deltas
are accepted only when every changed file matches an exact reviewed Git blob.
Any byte drift blocks again. A pin is not Android runtime identity or a device
performance claim, so full Android JVM/lint/APK validation remains mandatory.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable, Mapping


@dataclass(frozen=True)
class RuntimeDeltaResult:
    status: str
    runtime_input_changes: tuple[str, ...]
    unclassified_changes: tuple[str, ...]
    baseline_preserved: bool
    reviewed_runtime_changes: tuple[str, ...] = ()
    android_runtime_identical: bool = False
    requires_full_android_validation: bool = False
    claim_scope: str = "STRUCTURAL_EXACT_BLOB_REVIEW_NOT_DEVICE_BENCHMARK"


_RUNTIME_PREFIXES = (
    "app/",
    "gradle/",
    "config/",
)
_RUNTIME_EXACT = frozenset(
    {
        "gradlew",
        "gradlew.bat",
        "build.gradle",
        "build.gradle.kts",
        "settings.gradle",
        "settings.gradle.kts",
        "gradle.properties",
        "package.json",
        "package-lock.json",
    }
)
_SAFE_OFFLINE_PREFIXES = (
    ".github/",
    "docs/",
    "evidence/red_blend/",
    "governance/",
    "lab/",
    "tests/",
    "tools/",
)
_SAFE_OFFLINE_EXACT = frozenset({"AGENTS.md", "PROJECT.md", "STATUS.md"})
_SAFE_APP_TEST_PREFIX = "app/src/test/"

# Exact reviewed runtime/build blobs in the final product checkpoint.
# Paths are never permanently whitelisted: any byte change fails closed.
_PINNED_REVIEWED_RUNTIME_BLOBS = {
    "app/src/main/assets/ui/core/learning-model.js": "2a3ebc3897e3dfd258d8ac992423ed460878baf5",
    "app/src/main/assets/ui/core/native-api.js": "bf7b557aef9f9ff701e251496d4942bbf004df96",
    "app/src/main/assets/ui/core/predictor-model.js": "d96d033a6c40adac11c5f58802f995f85a464aae",
    "app/src/main/assets/ui/index.html": "b9f3f4c1d9b5b169cfd19095b4a984b95371e74c",
    "app/src/main/assets/ui/screens/curve.js": "cd35917ce4d39ffe3e4a134087bc796be1232d4d",
    "app/src/main/assets/ui/screens/learning.js": "14ca7fe45c5545459c959fe3374813bbadee32de",
    "app/src/main/assets/ui/screens/predictor.js": "46bc05cd39a91f2c1d2d2290e5bf6cebbf623a42",
    "app/src/main/assets/ui/styles-predictor.css": "b5de5d0f19ffe2af3dfd45c3dfd0c5c0f81a6f87",
    "app/src/main/assets/ui/styles-refine.css": "e896846ef73107e6e33fd45db3645c657ea7a3f2",
    "app/src/main/java/com/omegas/prohub/ecu/Mp48Protocol.kt": "f46468621f9c9136981fe791bbbdd49010d1950b",
    "app/src/main/java/com/omegas/prohub/learning/AssistedCalibrationAdvisor.kt": "862b4ea322ad967033385da09505d32114d245ce",
    "app/src/main/java/com/omegas/prohub/learning/PredictorInterpolator.kt": "MISSING",
    "app/src/main/java/com/omegas/prohub/learning/PredictorSpatialConfidence.kt": "MISSING",
    "app/src/main/java/com/omegas/prohub/learning/PredictorSurface.kt": "49f12ec28bc0b09cddf0cfa140118a0ee9335b4b",
    "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt": "da5e1ca1eecfcab4240a87368430457c1013395f",
    "app/src/main/java/com/omegas/prohub/service/V7CalibrationAccess.kt": "d5f7955d319a9ec7979d13a69f6ab88c5e47e907",
    "app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt": "cf6ff97a45c3ead394b82df6528da5dce2edf838",
    "app/src/main/java/com/omegas/v7/runtime/V7UiProjection.kt": "af1d38597768739793f4c40650854ca1512024bf",
    "config/omegas-release.json": "a5582ef3749483ee85290a6126217e7bbc956807",
}


def _normalize_paths(changed_paths: Iterable[str]) -> tuple[str, ...]:
    result: list[str] = []
    seen: set[str] = set()
    for raw in changed_paths:
        path = str(raw).strip().replace("\\", "/")
        while path.startswith("./"):
            path = path[2:]
        if not path or path in seen:
            continue
        seen.add(path)
        result.append(path)
    return tuple(sorted(result))


def classify_runtime_delta(
    changed_paths: Iterable[str],
    *,
    current_blobs: Mapping[str, str] | None = None,
) -> RuntimeDeltaResult:
    paths = _normalize_paths(changed_paths)
    blobs = {str(k): str(v) for k, v in (current_blobs or {}).items()}
    runtime: list[str] = []
    reviewed_runtime: list[str] = []
    unclassified: list[str] = []

    for path in paths:
        if path.startswith(_SAFE_APP_TEST_PREFIX):
            continue
        pinned_sha = _PINNED_REVIEWED_RUNTIME_BLOBS.get(path)
        if pinned_sha is not None:
            if blobs.get(path) == pinned_sha:
                reviewed_runtime.append(path)
            else:
                runtime.append(path)
            continue
        if path in _RUNTIME_EXACT or path.startswith(_RUNTIME_PREFIXES):
            runtime.append(path)
            continue
        if path in _SAFE_OFFLINE_EXACT or path.startswith(_SAFE_OFFLINE_PREFIXES):
            continue
        unclassified.append(path)

    if runtime:
        return RuntimeDeltaResult(
            status="BLOCKED_RUNTIME_INPUT_DELTA",
            runtime_input_changes=tuple(runtime),
            unclassified_changes=tuple(unclassified),
            baseline_preserved=False,
            reviewed_runtime_changes=tuple(reviewed_runtime),
            android_runtime_identical=False,
            requires_full_android_validation=True,
        )
    if unclassified:
        return RuntimeDeltaResult(
            status="BLOCKED_UNCLASSIFIED_DELTA",
            runtime_input_changes=(),
            unclassified_changes=tuple(unclassified),
            baseline_preserved=False,
            reviewed_runtime_changes=tuple(reviewed_runtime),
            android_runtime_identical=False,
            requires_full_android_validation=bool(reviewed_runtime),
        )
    if reviewed_runtime:
        return RuntimeDeltaResult(
            status="RED_BASELINE_PRESERVED_PINNED_REVIEWED_RUNTIME_DELTA",
            runtime_input_changes=(),
            unclassified_changes=(),
            baseline_preserved=True,
            reviewed_runtime_changes=tuple(reviewed_runtime),
            android_runtime_identical=False,
            requires_full_android_validation=True,
        )
    return RuntimeDeltaResult(
        status="RED_ANDROID_RUNTIME_INPUTS_IDENTICAL",
        runtime_input_changes=(),
        unclassified_changes=(),
        baseline_preserved=True,
        reviewed_runtime_changes=(),
        android_runtime_identical=True,
        requires_full_android_validation=False,
    )
