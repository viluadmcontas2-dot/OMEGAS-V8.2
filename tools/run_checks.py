#!/usr/bin/env python3
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
commands = [
    [sys.executable, "-B", "tests/test_blue_single_engine_contract.py"],
    [sys.executable, "-B", "tests/test_governance_contract.py"],
    [sys.executable, "-B", "tests/test_clean_ui_contract.py"],
    [sys.executable, "-B", "tests/test_block1_session_contract.py"],
    [sys.executable, "-B", "tests/test_block3_suggestion_ui_contract.py"],
    [sys.executable, "-B", "tests/test_v7_map_batch_contract.py"],
    [sys.executable, "-B", "tests/test_mp48_k_map_axes_contract.py"],
    [sys.executable, "-B", "tests/test_mp48_extended_status_contract.py"],
    [sys.executable, "-B", "tests/test_multimedia_telemetry_backpressure_contract.py"],
    [sys.executable, "-B", "tests/test_ux_didactic_expansion_contract.py"],
    [sys.executable, "-B", "tests/test_usb_permission_identity_contract.py"],
    [sys.executable, "-B", "tests/test_obd_independent_evidence_contract.py"],
    [sys.executable, "-B", "tests/test_background_power_overlay_contract.py"],
    [sys.executable, "-B", "tests/test_curve_kotlin_math_authority_contract.py"],
    [sys.executable, "-B", "tests/test_map_kotlin_math_authority_contract.py"],
    [sys.executable, "-B", "tests/test_suggestion_readback_lifecycle_contract.py"],
    [sys.executable, "-B", "tests/test_learning_consolidation_contract.py"],
    [sys.executable, "-B", "tests/test_startup_learning_restore_contract.py"],
    [sys.executable, "-B", "tests/test_learning_evidence_budget_contract.py"],
    [sys.executable, "-B", "tests/test_learning_memory_budget_contract.py"],
    [sys.executable, "-B", "tests/test_advisor_revision_budget_contract.py"],
    [sys.executable, "-B", "tests/test_checkpoint_hot_path_contract.py"],
    [sys.executable, "-B", "tests/test_mp48_serial_scheduler_contract.py"],
    [sys.executable, "-B", "tests/test_native_autocal_contract.py"],
    ["node", "--test", "tests/ui/autocal-cockpit.test.cjs"],
    ["node", "--test", "tests/ui/curve-autocal-interoperability.test.cjs"],
    ["node", "--test", "tests/ui/didactic-expansion.test.cjs"],
    ["node", "--test", "tests/ui/obd-independent-map.test.cjs"],
    ["node", "--test", "tests/ui/obd-runtime-controls.test.cjs"],
    ["node", "--test", "tests/ui/map-editor-flow.test.cjs"],
    ["node", "--test", "tests/ui/app-shell-runtime.test.cjs"],
    ["node", "--test", "tests/ui/map-workflow-e2e.test.cjs"],
    ["node", "--test", "tests/ui/map-ecu-simulator-e2e.test.cjs"],
    ["node", "--test", "tests/ui/portmon-replay-adapter.test.cjs"],
    ["node", "--test", "tests/ui/portmon-browser-simulator-e2e.test.cjs"],
    ["node", "--test", "tests/ui/portmon-frame-decoder.test.cjs"],
    ["node", "--test", "tests/ui/learning-view.test.cjs"],
    ["node", "--test", "tests/ui/live-tracing-budget.test.cjs"],
    ["node", "--test", "tests/ui/suggestion-model.test.cjs"],
]

for command in commands:
    print("+", " ".join(command), flush=True)
    result = subprocess.run(command, cwd=ROOT)
    if result.returncode:
        raise SystemExit(result.returncode)

print("QUALITY_GATE_FAST=PASS", flush=True)
