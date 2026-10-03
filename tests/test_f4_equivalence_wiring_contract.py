#!/usr/bin/env python3
"""Fatia F4: o serviço alimenta o cérebro único e o pacote `equivalence` só observa (zero comandos à ECU)."""
import pathlib

root = pathlib.Path(__file__).resolve().parents[1]
svc = (root / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
for must in (
    "equivalenceRuntime = EquivalenceRuntime(paths.runtimeRoot)",
    "equivalenceRuntime.onFrame(",
    "equivalenceRuntime.onStall(event)",
    "equivalenceRuntime.onCurveWritten(",
    "equivalenceRuntime.evaluate(",
    'equivalenceRuntime.onGasReset("MAPA_K_GRAVADO"',
    'equivalenceRuntime.onGasReset("AUTOMATCH_NATIVO"',
    'equivalenceRuntime.onGasReset("CURVA_K_GRAVADA"',
    'record("equivalence_result"',
    'record("reference_frozen"',
    "fun freezeReference(): String",
    "fun restorePreviousReference(): String",
    "fun equivalenceResultJson(): String",
    "references.endSession()",
):
    assert must in svc, must

live = svc.index("EcuPetrolReference.fromAcquisition")
assert svc.rfind("references.current()", 0, live) != -1, "a curva viva da ECU só vale sem Referência congelada"

pkg = root / "app/src/main/java/com/omegas/prohub/equivalence"
sources = sorted(pkg.glob("*.kt"))
assert len(sources) >= 9, [f.name for f in sources]
for f in sources:
    src = f.read_text(encoding="utf-8")
    assert "import android." not in src, f
    for forbidden in (
        "UsbSerialManager", "KWriteManager", "KFactorManager", "AutoCalNativeActionManager",
        "ResponseDrivenEcuEngine", "AutoCalProtocol", "serialScheduler",
    ):
        assert forbidden not in src, (f.name, forbidden)

# O freeze não grava na ECU: o serviço só chama o cérebro e o gravador de sessão.
freeze = svc[svc.index("fun freezeReference(): String"):svc.index("fun restorePreviousReference(): String")]
for forbidden in ("kWriter", "kFactor", "nativeAutoCal.", "usb."):
    assert forbidden not in freeze.replace("nativeAutoCal.autoMatchProgressJson", ""), forbidden

# A fase ganhou a prova por ponto sem mudar o formato nem o nome do arquivo.
phases = (root / "app/src/main/java/com/omegas/prohub/autocal/EquivalencePhases.kt").read_text(encoding="utf-8")
assert 'const val FORMAT = "omegas-refinement-autopilot-v1"' in phases
for must in ("fun beginProof(", "fun judgePoints(", "fun restartProofs(", "fun interruptProofs(", "PROOF_TIMEBOX_ONLINE_MS"):
    assert must in phases, must

# Os arquivos de comando da ECU não foram tocados pelo cérebro (continuam sem referência a ele).
for name in ("UsbSerialManager", "ResponseDrivenEcuEngine", "AutoCalProtocol", "KFactorManager", "KWriteManager", "AutoCalNativeActionManager"):
    for path in (root / "app/src/main/java").rglob(f"{name}.kt"):
        assert "prohub.equivalence" not in path.read_text(encoding="utf-8"), path

print("F4_EQUIVALENCE_WIRING=PASS")
