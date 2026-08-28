#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text("utf-8")
RUNTIME = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt").read_text("utf-8")
MEMORY = (ROOT / "app/src/main/java/com/omegas/prohub/learning/MotorLearningMemory.kt").read_text("utf-8")

consume_start = SERVICE.index("    private fun consumeEngineEvent(")
consume_end = SERVICE.index("\n    private fun consumeGpsUpdate", consume_start)
consume = SERVICE[consume_start:consume_end]

destroy_start = SERVICE.index("    override fun onDestroy()")
destroy_end = SERVICE.index("\n    fun status()", destroy_start)
destroy = SERVICE[destroy_start:destroy_end]

checks = {
    "first telemetry no longer creates portable checkpoint": "Primeira telemetria da sessão" not in SERVICE and "saveInternalCheckpoint" not in consume,
    "service destroy no longer builds portable checkpoint before shutdown": "Serviço encerrado" not in SERVICE and "saveInternalCheckpoint" not in destroy,
    "pre map cell write checkpoint preserved": 'saveInternalCheckpoint("Antes de ajustar célula K")' in SERVICE,
    "pre map batch write checkpoint preserved": 'saveInternalCheckpoint("Antes de ajustar mapa K: "' in SERVICE,
    "pre k factor write checkpoint preserved": 'saveInternalCheckpoint("Antes de ajustar K factor: "' in SERVICE,
    "confirmed map checkpoint preserved": 'saveInternalCheckpoint("Após escrita K confirmada")' in SERVICE,
    "confirmed factor checkpoint preserved": 'saveInternalCheckpoint("Após escrita K factor confirmada")' in SERVICE,
    "runtime close still drains pipelines": 'flushPipelines("encerramento do runtime", 2_000L)' in RUNTIME,
    "learning primary state still persists asynchronously": "persistExecutor" in MEMORY and "persistDirty" in MEMORY,
}

for name, ok in checks.items():
    if not ok:
        raise AssertionError(f"FAIL: {name}")
    print(f"OK: {name}")

print("CHECKPOINT_HOT_PATH_CONTRACT=PASS")
