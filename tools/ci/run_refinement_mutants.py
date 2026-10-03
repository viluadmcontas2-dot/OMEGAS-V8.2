#!/usr/bin/env python3
"""Classe 2: mutantes reais Kotlin; compilação falha nunca conta como bug detectado."""
import json
import pathlib
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
PILOT = ROOT / "app/src/main/java/com/omegas/prohub/autocal/RefinementAutopilot.kt"
JOURNAL = ROOT / "app/src/main/java/com/omegas/prohub/autocal/RefinementJournal.kt"
RESUMO = ROOT / "app/src/main/java/com/omegas/prohub/diagnostics/SessionResumo.kt"
RESULTS = ROOT / "app/build/test-results/testDebugUnitTest"
CLASSES = [
    "com.omegas.prohub.autocal.RefinementLifecycleRegressionTest",
    "com.omegas.prohub.autocal.RefinementJournalDecisionTest",
    "com.omegas.prohub.diagnostics.SessionResumoDiagnosticTest",
]
COMMAND = ["bash", "./gradlew", "testDebugUnitTest", "--console=plain"]
for name in CLASSES:
    COMMAND += ["--tests", name]
MUTANTS = [
    ("clock-only-experiment-id", JOURNAL, '.put("id", "EXP-${clock()}-$experimentSequence")',
     '.put("id", "EXP-${clock()}")', "confirmedWritesHaveDistinctIdentityEvenWhenClockIsFrozen"),
    ("elapsed-time-is-visible-change", JOURNAL, 'it.optJSONObject("decision")?.remove("onlineMs")',
     'it.optJSONObject("decision")?.remove("notAnOperand")', "elapsedTimeAloneDoesNotRepublishVisibleDecision"),
    ("no-phase-ceiling", PILOT, "val budget = PHASE_BUDGET_MS[candidate]", "val budget: Long? = null",
     "automaticWaitHasCeilingWithoutDeclaringEcuDone"),
    ("false-offline-stable", PILOT, '!ecuOnline -> "SEM_ECU"', '!ecuOnline -> "ESTAVEL"',
     "offlineNeverPresentsLastStableStateAsCurrent"),
    ("stale-gas-acquisition", PILOT, 'activeCount(liveAcquisition, "GNV")', 'activeCount(acquisition, "GNV")',
     "offlineCannotReuseStaleGasAcquisition"),
    ("restart-resets-deadline", PILOT, 'lastDurationAt = root.optLong("durationAt").takeIf { it >= 0L }',
     'lastDurationAt = null', "reopeningAppCannotResetReadingDeadline"),
    ("silence-means-native-done", PILOT, 'else -> null\n            }\n            if (fresh != ecuDoneLatch)',
     'petrolZones >= 4 && gasZones >= 4 && quietMs >= QUIET_MS -> "AQUISICAO_COMPLETA"\n                else -> null\n            }\n            if (fresh != ecuDoneLatch)',
     "acquisitionSilenceNeverConfirmsNativeCompletion"),
    ("sticky-native-latch", PILOT, 'if (fresh != ecuDoneLatch)', 'if (fresh != null && fresh != ecuDoneLatch)',
     "nativeCompletionCannotOutliveMissingCounter"),
    ("false-summary-confirmation", RESUMO, '"VERIFICADO" to "verificação concluída (resultado por faixa abaixo)"',
     '"VERIFICADO" to "melhorou e foi confirmada"', "verifiedWithoutConfirmedBandsDoesNotInventImprovement"),
    ("wrong-journal-cause", JOURNAL, '"PIOROU_EM_PARTE" -> "WORSE_BANDS_DETECTED"',
     '"PIOROU_EM_PARTE" -> "BAND_VERIFICATION_COMPLETE"', "realWorseningCarriesItsOperandsAndFunctionalCause"),
]

def run_case():
    for xml in RESULTS.glob("TEST-*.xml"):
        xml.unlink()
    started = time.monotonic()
    run = subprocess.run(COMMAND, cwd=ROOT, text=True, stdout=subprocess.PIPE,
                         stderr=subprocess.STDOUT, timeout=240)
    cases, failures = [], []
    for xml in RESULTS.glob("TEST-*.xml"):
        root = ET.parse(xml).getroot()
        for case in root.findall("testcase"):
            cases.append(case.attrib["name"])
            if case.find("failure") is not None or case.find("error") is not None:
                failures.append(case.attrib["name"])
    # No report, skipped test, or compilation error is not a valid kill.
    if not cases or "Compilation error" in run.stdout or "compileDebugKotlin FAILED" in run.stdout:
        raise RuntimeError("Mutação sem prova comportamental: " + run.stdout[-6000:])
    return run.returncode, failures, len(cases), round(time.monotonic() - started, 3)

def main():
    # Build canonical chama chmod +x; isso não é mutação de código. Verificar bytes
    # antes de restaurar só o modo registrado, sem dispensar git diff --exit-code.
    wrapper = ROOT / "gradlew"
    expected_wrapper = subprocess.check_output(["git", "show", "HEAD:gradlew"], cwd=ROOT)
    if wrapper.read_bytes() != expected_wrapper:
        raise RuntimeError("gradlew mudou conteúdo antes da mutação")
    wrapper_mode = int(subprocess.check_output(
        ["git", "ls-files", "-s", "--", "gradlew"], cwd=ROOT).split()[0], 8) & 0o777
    wrapper.chmod(wrapper_mode)
    originals = {file: file.read_text() for _, file, _, _, _ in MUTANTS}
    receipt = {"class": 2, "physicalValidationClaimed": False, "mutants": []}
    try:
        rc, failed, count, seconds = run_case()
        if rc or failed:
            raise RuntimeError("Baseline deve ser GREEN: " + str(failed))
        receipt["baselineTests"] = count
        for name, file, needle, replacement, expected in MUTANTS:
            original = originals[file]
            if original.count(needle) != 1:
                raise RuntimeError("Mutante inválido/ambíguo: " + name)
            file.write_text(original.replace(needle, replacement, 1))
            try:
                rc, failures, count, seconds = run_case()
                valid_kill = rc != 0 and any(expected in failure for failure in failures)
                receipt["mutants"].append({"name": name, "killed": valid_kill,
                    "expectedTest": expected, "failedTests": failures, "seconds": seconds})
                print(json.dumps(receipt["mutants"][-1], ensure_ascii=False), flush=True)
                if not valid_kill:
                    raise RuntimeError("MUTANTE SOBREVIVEU/sem RED esperado: " + name)
            finally:
                file.write_text(original)
        rc, failed, count, seconds = run_case()
        if rc or failed:
            raise RuntimeError("Fonte restaurada precisa ser GREEN: " + str(failed))
        receipt["restoredBaselineTests"] = count
        receipt["killed"] = len(receipt["mutants"])
        receipt["survived"] = 0
        subprocess.run(["git", "diff", "--exit-code"], cwd=ROOT, check=True)
    finally:
        for file, original in originals.items():
            file.write_text(original)
        destination = ROOT / "build/evidence/refinement-mutants.json"
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n")
    print("REFINEMENT_MUTANTS=" + json.dumps(receipt, ensure_ascii=False), flush=True)

if __name__ == "__main__":
    main()
