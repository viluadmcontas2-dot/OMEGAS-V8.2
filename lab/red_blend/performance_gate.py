"""Fail-closed structural performance guard for RED V8.2 Science Blend.

The strongest available hot-path proof while Blend science remains offline is
source/build-input identity with the proven RED baseline. This module does not
pretend wall-clock measurements from different hosted runners are causal
performance evidence. Any Android/runtime input delta blocks structural
promotion and requires a real A/B measurement.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable


@dataclass(frozen=True)
class RuntimeDeltaResult:
    status: str
    runtime_input_changes: tuple[str, ...]
    unclassified_changes: tuple[str, ...]
    hot_path_preserved: bool
    claim_scope: str = "STRUCTURAL_RED_RUNTIME_IDENTITY_NOT_DEVICE_BENCHMARK"


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
    "lab/",
    "tests/",
    "tools/",
)
_SAFE_OFFLINE_EXACT = frozenset({"STATUS.md"})


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


def classify_runtime_delta(changed_paths: Iterable[str]) -> RuntimeDeltaResult:
    paths = _normalize_paths(changed_paths)
    runtime: list[str] = []
    unclassified: list[str] = []

    for path in paths:
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
        )
    if unclassified:
        return RuntimeDeltaResult(
            status="BLOCKED_UNCLASSIFIED_DELTA",
            runtime_input_changes=(),
            unclassified_changes=tuple(unclassified),
            hot_path_preserved=False,
        )
    return RuntimeDeltaResult(
        status="RED_ANDROID_RUNTIME_INPUTS_IDENTICAL",
        runtime_input_changes=(),
        unclassified_changes=(),
        hot_path_preserved=True,
    )
