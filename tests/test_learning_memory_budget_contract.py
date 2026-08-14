#!/usr/bin/env python3
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
BUDGET = ROOT / "app/src/main/java/com/omegas/prohub/learning/LearningMemoryBudget.kt"
MEMORY = ROOT / "app/src/main/java/com/omegas/prohub/learning/MotorLearningMemory.kt"
GRID = ROOT / "app/src/main/java/com/omegas/prohub/learning/LearningGridProjection.kt"
SIGNAL = ROOT / "app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt"

budget = BUDGET.read_text(encoding="utf-8")
memory = MEMORY.read_text(encoding="utf-8")
grid = GRID.read_text(encoding="utf-8")
signal = SIGNAL.read_text(encoding="utf-8")

checks = {
    "region visit provenance bounded": "MAX_REGION_VISIT_IDS = 16" in budget,
    "region session provenance bounded": "MAX_REGION_SESSION_IDS = 8" in budget,
    "main-state byte target explicit": "TARGET_PERSISTED_BYTES = 5 * 1024 * 1024" in budget,
    "scientific policy explicit": 'POLICY = "BOUNDED_PROVENANCE_EXACT_COUNTS_V1"' in budget,
    "exact visit counts persist apart from ids": '.put("visit_count", max(visitCount, retainedVisits.size))' in memory,
    "exact session counts persist apart from ids": '.put("session_count", max(sessionCount, retainedSessions.size))' in memory,
    "legacy exact counts survive restore": 'raw.optInt("visit_count"' in memory and 'raw.optInt("session_count"' in memory,
    "persistence copies under lock only": "val snapshot = synchronized(lock) { persistenceSnapshotLocked() }" in memory,
    "json build occurs after lock copy": "val newest = buildPersistedState(snapshot)" in memory,
    "persisted regions are primary science only": 'snapshot.regions.map { it.toPersistedJson(visitLimit, sessionLimit) }' in memory,
    "persisted payload omits derived cell projection": 'regionsJsonLocked()' not in memory[memory.index("private fun buildPersistedState"):memory.index("private fun writePersistedState")],
    "compact json persisted": 'val encoded = root.toString().toByteArray(Charsets.UTF_8)' in memory,
    "advisor uses minimal snapshot": 'AssistedCalibrationAdvisor.analyze(delegate.advisorSnapshot())' in signal,
    "advisor snapshot excludes heavy ui projections": '.put("regions", JSONArray(regions.map { it.toAdvisorJson() }))' in memory and '.put("comparisons", JSONArray(comparisons.map { it.toJson() }))' in memory,
    "grid never fabricates legacy visit ids": "legacy-visit" not in grid and "legacy-session" not in grid,
    "grid retains exact count floor": "visitCountFloor" in grid and "sessionCountFloor" in grid,
    "byte pressure only compacts provenance": "provenanceLevels" in memory and "snapshot.regions.remove" not in memory and "snapshot.comparisons.remove" not in memory,
}

for name, ok in checks.items():
    if not ok:
        raise AssertionError(f"FAIL: {name}")
    print(f"OK: {name}")

# Structural ordering: copying state under the lock must precede expensive JSON construction.
copy_pos = memory.index("val snapshot = synchronized(lock) { persistenceSnapshotLocked() }")
build_pos = memory.index("val newest = buildPersistedState(snapshot)", copy_pos)
if build_pos <= copy_pos:
    raise AssertionError("FAIL: persisted JSON must be built after the locked snapshot copy")
print("OK: persisted JSON build is outside the locked snapshot expression")

kotlinc = shutil.which("kotlinc")
if kotlinc:
    harness = r'''
package com.omegas.prohub.learning

fun main() {
    val ids = (1..40).map { "v$it" }
    val retained = LearningMemoryBudget.retainNewestIds(ids, LearningMemoryBudget.MAX_REGION_VISIT_IDS)
    check(retained.size == 16)
    check(retained.first() == "v25")
    check(retained.last() == "v40")

    val sessions = (1..20).map { "s$it" }.toCollection(linkedSetOf())
    LearningMemoryBudget.trimNewestIds(sessions, LearningMemoryBudget.MAX_REGION_SESSION_IDS)
    check(sessions.size == 8)
    check(sessions.first() == "s13")
    check(sessions.last() == "s20")

    val none = LearningMemoryBudget.retainNewestIds(ids, 0)
    check(none.isEmpty())
    check(LearningMemoryBudget.provenanceLevels.last() == (0 to 0))
    check(LearningMemoryBudget.TARGET_PERSISTED_BYTES == 5 * 1024 * 1024)
    println("LEARNING_MEMORY_BUDGET_BEHAVIOR=PASS")
}
'''
    with tempfile.TemporaryDirectory(prefix="omegas-memory-budget-") as tmp:
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
        if "LEARNING_MEMORY_BUDGET_BEHAVIOR=PASS" not in result.stdout:
            raise AssertionError("FAIL: Kotlin memory budget behavior harness")
        print("OK: Kotlin memory budget behavior harness")
else:
    print("SKIP: kotlinc unavailable; Android/JUnit remains authoritative when available")

print("LEARNING_MEMORY_BUDGET_CONTRACT=PASS")
