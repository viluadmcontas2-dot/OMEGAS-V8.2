#!/usr/bin/env python3
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
BUDGET = ROOT / "app/src/main/java/com/omegas/prohub/learning/LearningEvidenceBudget.kt"
STORE = ROOT / "app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt"
WRITER = ROOT / "app/src/main/java/com/omegas/prohub/learning/CoalescedSnapshotWriter.kt"

budget = BUDGET.read_text(encoding="utf-8")
store = STORE.read_text(encoding="utf-8")
writer = WRITER.read_text(encoding="utf-8")

checks = {
    "explicit byte budget": "MAX_PERSISTED_BYTES = 256 * 1024" in budget,
    "native snapshots bounded": "MAX_NATIVE_SNAPSHOTS = 16" in budget and "trimNativeEvidenceLocked()" in store,
    "visit accumulators bounded": "MAX_VISIT_ACCUMULATORS = 256" in budget and "trimVisitAccumulatorsLocked()" in store,
    "provenance bounded": "MAX_PROVENANCE_ENTRIES = 64" in budget,
    "sidecar schema revisioned": 'EVIDENCE_STATE_SCHEMA = "omegas-learning-evidence-v6-v3"' in store,
    "coalesce before payload build": "evidenceStateWriter.request { buildEvidencePayload(snapshot) }" in store,
    "writer accepts deferred provider": "fun request(payloadProvider: () -> String): Boolean" in writer,
    "live ingest remains lightweight": "return decorate(result, includeAdvisor = false)" in store,
    "heavy native evidence gated": 'if (includeAdvisor) {' in store and '.put("native_ecu_evidence"' in store,
}

for name, ok in checks.items():
    if not ok:
        raise AssertionError(f"FAIL: {name}")
    print(f"OK: {name}")

kotlinc = shutil.which("kotlinc")
if kotlinc:
    harness = r'''
package com.omegas.prohub.learning

data class Native(val snapshot: String, val band: Int)
data class Visit(val id: String, val lastSeen: Long)

fun main() {
    val native = (1..24).flatMap { snapshot ->
        (0 until 18).map { band -> Native("s$snapshot", band) }
    }
    val keptNative = LearningEvidenceBudget.retainNewestSnapshotGroups(native, { it.snapshot })
    val ids = keptNative.map { it.snapshot }.distinct()
    check(ids.size == 16)
    check(ids.first() == "s9")
    check(ids.last() == "s24")
    check(ids.all { id -> keptNative.count { it.snapshot == id } == 18 })

    val visits = (1..400).map { Visit("v$it", it.toLong()) }
    val keptVisits = LearningEvidenceBudget.retainNewestVisits(visits, { it.lastSeen })
    check(keptVisits.size == 256)
    check(keptVisits.first().id == "v145")
    check(keptVisits.last().id == "v400")

    val provenance = (1..100).toList()
    val keptProvenance = LearningEvidenceBudget.retainNewestEntries(provenance)
    check(keptProvenance == (37..100).toList())
    println("LEARNING_EVIDENCE_BUDGET_BEHAVIOR=PASS")
}
'''
    with tempfile.TemporaryDirectory(prefix="omegas-evidence-budget-") as tmp:
        tmp = Path(tmp)
        harness_file = tmp / "Harness.kt"
        jar = tmp / "budget.jar"
        harness_file.write_text(harness, encoding="utf-8")
        subprocess.run(
            [kotlinc, str(BUDGET), str(harness_file), "-include-runtime", "-d", str(jar)],
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
        if "LEARNING_EVIDENCE_BUDGET_BEHAVIOR=PASS" not in result.stdout:
            raise AssertionError("FAIL: Kotlin budget behavior harness")
        print("OK: Kotlin budget behavior harness")
else:
    print("SKIP: kotlinc unavailable; Gradle/JUnit remains authoritative when available")

print("LEARNING_EVIDENCE_BUDGET_CONTRACT=PASS")
