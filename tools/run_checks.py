#!/usr/bin/env python3
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE_MARKER = "@requires-gradle-test-results"

args = sys.argv[1:]
if any(arg not in {"--full"} for arg in args):
    raise SystemExit("USAGE: run_checks.py [--full]")
full = "--full" in args

python_tests = sorted((ROOT / "tests").glob("test_*.py"))
ui_tests = sorted((ROOT / "tests" / "ui").glob("*.test.cjs"))

if not python_tests:
    raise SystemExit("QUALITY_GATE_DISCOVERY_ERROR: no Python contracts found")
if not ui_tests:
    raise SystemExit("QUALITY_GATE_DISCOVERY_ERROR: no UI tests found")

gradle_ui_tests = []
fast_ui_tests = []
for path in ui_tests:
    source = path.read_text(encoding="utf-8")
    if GRADLE_MARKER in source:
        gradle_ui_tests.append(path)
    else:
        fast_ui_tests.append(path)

commands = [
    [sys.executable, "-B", str(path.relative_to(ROOT))]
    for path in python_tests
]
commands += [
    ["node", "--test", str(path.relative_to(ROOT))]
    for path in fast_ui_tests
]

print(
    "QUALITY_GATE_DISCOVERED "
    f"python={len(python_tests)} ui={len(ui_tests)} "
    f"fast_ui={len(fast_ui_tests)} gradle_ui={len(gradle_ui_tests)}",
    flush=True,
)

for command in commands:
    print("+", " ".join(command), flush=True)
    result = subprocess.run(command, cwd=ROOT)
    if result.returncode:
        raise SystemExit(result.returncode)

if not full:
    print(
        f"QUALITY_GATE_FAST=PASS deferred_gradle_ui={len(gradle_ui_tests)}",
        flush=True,
    )
    raise SystemExit(0)

gradlew = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
if not gradlew.is_file():
    raise SystemExit("QUALITY_GATE_FULL_ERROR: Gradle wrapper missing")

gradle_command = [str(gradlew), "--no-daemon", "testDebugUnitTest"]
print("+", " ".join(gradle_command), flush=True)
gradle = subprocess.run(gradle_command, cwd=ROOT)
if gradle.returncode:
    raise SystemExit(gradle.returncode)

for path in gradle_ui_tests:
    command = ["node", "--test", str(path.relative_to(ROOT))]
    print("+", " ".join(command), flush=True)
    result = subprocess.run(command, cwd=ROOT)
    if result.returncode:
        raise SystemExit(result.returncode)

print("QUALITY_GATE_FULL=PASS", flush=True)
