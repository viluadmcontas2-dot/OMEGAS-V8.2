#!/usr/bin/env python3
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
GATE = ROOT / "app/src/main/java/com/omegas/prohub/learning/AdvisorRevisionGate.kt"
STORE = ROOT / "app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt"
ADVISOR = ROOT / "app/src/main/java/com/omegas/prohub/learning/AssistedCalibrationAdvisor.kt"

gate = GATE.read_text(encoding="utf-8")
store = STORE.read_text(encoding="utf-8")
advisor = ADVISOR.read_text(encoding="utf-8")

checks = {
    "no advisor timer introduced": "Timer" not in gate and "scheduleAtFixedRate" not in store and "scheduleWithFixedDelay" not in store,
    "ingest requests semantic refresh": "advisorEstimate?.let { requestAdvisorRefresh(it, advisorRpm, advisorMap) }" in store,
    "raw eligible sample no longer refreshes unconditionally": "if (prepared.learningEligible && prepared.sample != null) scheduleAdvisorRefresh" not in store,
    "equivalence token uses observation milestone": "AdvisorRevisionGate.observationMilestone" in store and '"EQ"' in store,
    "equivalence token quantizes scientific change": "0.02" in store and "0.0025" in store,
    "petrol reference feeds bounded equivalence runtime": "FuelLane.PETROL_REFERENCE" in store and "equivalenceRuntime.observe(" in store,
    "export does not recompute advisor synchronously": "advisor = AssistedCalibrationAdvisor.analyze(exported)" not in store,
    "advisor derived state exposes revision freshness": '"advisorRevision"' in store and '"advisorFresh"' in store and '"advisor_fresh"' in store,
    "calibration and merge force revisions": "scheduleAdvisorRefresh(advisorRevisionGate.force())" in store,
    "advisor remains manual only": 'humanConfirmationRequired' in advisor and 'automatic", false' in advisor,
}

for name, ok in checks.items():
    if not ok:
        raise AssertionError(f"FAIL: {name}")
    print(f"OK: {name}")

kotlinc = shutil.which("kotlinc")
if kotlinc:
    harness = r'''
package com.omegas.prohub.learning

fun main() {
    val gate = AdvisorRevisionGate()
    check(gate.revise(null) == null)
    check(gate.revise("") == null)
    check(gate.revise("CMP:a:1") == 1L)
    check(gate.revise("CMP:a:1") == null)
    check(gate.revise("CMP:a:2") == 2L)
    check(gate.force() == 3L)
    check(gate.currentRevision() == 3L)

    check(AdvisorRevisionGate.observationMilestone(1) == 1)
    check(AdvisorRevisionGate.observationMilestone(2) == 2)
    check(AdvisorRevisionGate.observationMilestone(3) == 2)
    check(AdvisorRevisionGate.observationMilestone(7) == 4)
    check(AdvisorRevisionGate.observationMilestone(8) == 8)
    check(AdvisorRevisionGate.quantize(1.01, 0.25) == 4L)
    check(AdvisorRevisionGate.quantize(1.12, 0.25) == 4L)
    check(AdvisorRevisionGate.quantize(1.14, 0.25) == 5L)
    println("ADVISOR_REVISION_GATE_BEHAVIOR=PASS")
}
'''
    with tempfile.TemporaryDirectory(prefix="omegas-advisor-gate-") as tmp:
        tmp = Path(tmp)
        harness_file = tmp / "Harness.kt"
        jar = tmp / "gate.jar"
        harness_file.write_text(harness, encoding="utf-8")
        subprocess.run(
            [kotlinc, str(GATE), str(harness_file), "-include-runtime", "-d", str(jar)],
            check=True,
            cwd=ROOT,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        result = subprocess.run(
            ["java", "-jar", str(jar)],
            check=True,
            cwd=ROOT,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        if "ADVISOR_REVISION_GATE_BEHAVIOR=PASS" not in result.stdout:
            raise AssertionError("FAIL: AdvisorRevisionGate Kotlin behavior")
        print("OK: AdvisorRevisionGate Kotlin behavior")
else:
    print("SKIP: kotlinc unavailable")

print("ADVISOR_REVISION_BUDGET_CONTRACT=PASS")
