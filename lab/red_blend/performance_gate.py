"""Fail-closed structural performance guard for RED V8.2 Science Blend.

RED remains the performance anchor. Offline science/docs are safe. A very small
set of reviewed UI/projection changes may coexist with the RED hot path, but only
when their exact Git blob identities match this reviewed checkpoint. Any other
Android/runtime or build-input delta blocks the structural hot-path claim.

A pinned UI delta is *not* Android runtime identity: it requires the full Android
JVM/lint/APK gate before a candidate can be used.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable, Mapping


@dataclass(frozen=True)
class RuntimeDeltaResult:
    status: str
    runtime_input_changes: tuple[str, ...]
    unclassified_changes: tuple[str, ...]
    hot_path_preserved: bool
    non_hot_path_runtime_changes: tuple[str, ...] = ()
    android_runtime_identical: bool = False
    requires_full_android_validation: bool = False
    claim_scope: str = "STRUCTURAL_RED_HOT_PATH_CLASSIFICATION_NOT_DEVICE_BENCHMARK"


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
    "lab/",
    "tests/",
    "tools/",
)
_SAFE_OFFLINE_EXACT = frozenset({"STATUS.md"})
_SAFE_APP_TEST_PREFIX = "app/src/test/"

# These are the exact reviewed non-hot-path production blobs introduced by the
# guarded geometric-field/UI work. A later edit to either file changes its blob
# SHA and therefore fails closed until separately reviewed and re-pinned.
_PINNED_NON_HOT_PATH_RUNTIME_BLOBS = {
    "app/src/main/assets/ui/screens/learning.js": "ef05bb106e68e3fa23443596357eaa509cbae04c",
    "app/src/main/java/com/omegas/v7/runtime/V7UiProjection.kt": "af1d38597768739793f4c40650854ca1512024bf",
    "app/src/main/java/com/omegas/prohub/learning/PredictorSurface.kt": "49f12ec28bc0b09cddf0cfa140118a0ee9335b4b",
    "app/src/main/java/com/omegas/prohub/service/V7CalibrationAccess.kt": "d5f7955d319a9ec7979d13a69f6ab88c5e47e907",
    "app/src/main/java/com/omegas/prohub/learning/PredictorInterpolator.kt": "MISSING",
    "app/src/main/java/com/omegas/prohub/learning/PredictorSpatialConfidence.kt": "MISSING",
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
    non_hot_path_runtime: list[str] = []
    unclassified: list[str] = []

    for path in paths:
        if path.startswith(_SAFE_APP_TEST_PREFIX):
            continue
        pinned_sha = _PINNED_NON_HOT_PATH_RUNTIME_BLOBS.get(path)
        if pinned_sha is not None:
            if blobs.get(path) == pinned_sha:
                non_hot_path_runtime.append(path)
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
            hot_path_preserved=False,
            non_hot_path_runtime_changes=tuple(non_hot_path_runtime),
            android_runtime_identical=False,
            requires_full_android_validation=True,
        )
    if unclassified:
        return RuntimeDeltaResult(
            status="BLOCKED_UNCLASSIFIED_DELTA",
            runtime_input_changes=(),
            unclassified_changes=tuple(unclassified),
            hot_path_preserved=False,
            non_hot_path_runtime_changes=tuple(non_hot_path_runtime),
            android_runtime_identical=False,
            requires_full_android_validation=bool(non_hot_path_runtime),
        )
    if non_hot_path_runtime:
        return RuntimeDeltaResult(
            status="RED_HOT_PATH_PRESERVED_PINNED_NON_HOT_PATH_DELTA",
            runtime_input_changes=(),
            unclassified_changes=(),
            hot_path_preserved=True,
            non_hot_path_runtime_changes=tuple(non_hot_path_runtime),
            android_runtime_identical=False,
            requires_full_android_validation=True,
        )
    return RuntimeDeltaResult(
        status="RED_ANDROID_RUNTIME_INPUTS_IDENTICAL",
        runtime_input_changes=(),
        unclassified_changes=(),
        hot_path_preserved=True,
        non_hot_path_runtime_changes=(),
        android_runtime_identical=True,
        requires_full_android_validation=False,
    )
