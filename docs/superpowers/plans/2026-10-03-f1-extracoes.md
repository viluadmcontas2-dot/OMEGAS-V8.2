# Fatia 1 — Extrações Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tirar de dentro do V7 e do `learning/` tudo o que o produto continua usando (escrita/operações de Curva K e Mapa K, célula ao vivo, analisador de amostra, máquina de fases), sem mudar nenhum comportamento, para que as Podas (F2/F3) apaguem sem quebrar nada.

**Architecture:** Cinco movimentos de código, cada um com um teste que prova que é o mesmo: `CalibrationOperationsBridge` (JS `OmegasCalibration`) recebe os 9 endpoints de curva/mapa do `V7JavascriptBridge`, com a mesma fila de execução (um executor, uma flag `busy`), e passa a ser a única ponte de escrita que o JS chama; `LiveCellProjection` (`calibration/`) assume a célula ao vivo sobre `KMapPhysicalAxes`; `MotorSampleAnalyzer`/`SampleDecision` mudam para `ecu/`; `RefinementAutopilot` é renomeado para `EquivalencePhases`, mas o formato e o arquivo em disco ficam iguais. `OmegasV7` continua registrado até a F2, mas sem nenhum chamador JS.

**Tech Stack:** Kotlin (Android, JVM 17, JUnit 4, org.json), WebView JS vanilla (`node --test`), contratos Python, GitHub Actions (`tools/ci/remote-test.sh`, `OMEGAS PLATINA CI`, `verde-android-render-evidence.yml`).

**Spec:** docs/superpowers/specs/2026-10-03-omegas-platina-norte-unico-design.md  ·  **Índice/contrato:** docs/superpowers/plans/2026-10-03-00-norte-unico-index.md

**Branch:** `work/platina-f1-extracoes`, criada a partir de `OmegasPlatina` já com a F0 mesclada. **Commits:** mensagem convencional e, no fim de toda mensagem, as duas linhas:
```
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01YN94YqPpeRPSaeNkh2QkF7
```


> **Antes de começar:** leia a seção **Reconciliação entre planos** do índice (`2026-10-03-00-norte-unico-index.md`). Onde este plano divergir dela (intents, payloads, formato do snapshot, comando do emulador), vale o índice.
## Global Constraints

Todas as do índice, mais:
- Nenhuma chave JSON, string de erro, nome de evento de sessão, `FORMAT` ou nome de arquivo em disco muda (`autopilot`, `interpolation`, `sample_state`, `sample{}`, `refinement_autopilot.json`, `"Outra operação V8 está em andamento"`…).
- Uma única fila de execução para operações seriais de calibração: um `Executors.newSingleThreadExecutor` e um `AtomicBoolean busy`, ambos em `CalibrationOperationsBridge`. O `synchronizeFromEcu` do V7 também passa por eles.
- `OmegasV7` continua registrado (só morre na F2); ao fim da F1, nenhum arquivo em `app/src/main/assets/ui` cita `OmegasV7`.
- `LearningGridProjection` fica e só delega (é apagado na F3); nenhum código novo pode depender dele.
- O androidTest só compila no workflow de render. Ele roda por dispatch na branch antes do merge (Task 1.6).
- Nenhum texto de UI, CSS ou cor novos. As telas `curve.js`, `map.js` e `refino.js` não mudam: elas chamam `NativeApi`, e só o `native-api.js` muda.

## Review Focus

1. **`refinement_autopilot.json` gravado por um APK antigo** (`format = omegas-refinement-autopilot-v1`, `alertedPhase = PROPOSTA_PRONTA`): `EquivalencePhases` o carrega e não repete o aviso. Um formato desconhecido é ignorado; um JSON corrompido não derruba o app. Teste em Task 1.3.
2. **Duas operações seriais ao mesmo tempo** (curva em escrita e, em seguida, mapa ou `synchronizeFromEcu`): a segunda recebe `{"ok":false,"busy":true,"error":"Outra operação V8 está em andamento"}`, porque existe uma única flag. Contrato em Task 1.4 (uma flag, um executor) e Task 1.5 (o V7 delega a `operations.startOperation`).
3. **Célula ao vivo idêntica**: `liveInterpolationJson(2000.0, 4.2, 0.62, 42, 1000, true)` dá `key="4:2"` e pesos `3/13, 0.9/13, 7/13, 2.1/13`. A versão que delega em `LearningGridProjection` produz o mesmo `toString()`. Teste em Task 1.1.
4. **Evento de telemetria depois da mudança de pacote**: `SampleDecision.toTelemetryJson()` mantém exatamente as 20 chaves de hoje, e `cell_key` continua vindo da célula ao vivo (`"4:2"` para 2000 rpm / 4,2 ms). Teste em Task 1.2.
5. **Escrita confirmada sem a reconciliação V7**: `lastOperation` continua com `BATCH_CONFIRMED`, `readbackValid=true` e `humanConfirmed=true`, sem `suggestionReconciliation`. O JS não lê esse campo (grep nos testes). Contrato em Task 1.4.

---

### Task 1.1: `LiveCellProjection` (célula ao vivo sobre `KMapPhysicalAxes`)

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/calibration/LiveCellProjection.kt`
- Modify: `app/src/main/java/com/omegas/prohub/learning/LearningGridProjection.kt:16-18` (eixos), `:36-64` (`cellFor`), `:66-104` (`liveInterpolationJson` com KDoc), `:267-278` (`nearest`, que sai se ficar sem uso)
- Modify: `app/src/main/java/com/omegas/prohub/learning/ContinuousLearningMath.kt:46-47`, `:71-72`
- Modify: `app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt:10` (import), `:91`, `:354` (`:101` e `:364` continuam `.put("interpolation", interpolation)`)
- Create: `tests/test_f1_extracoes_contract.py`
- Create: `app/src/test/java/com/omegas/prohub/calibration/LiveCellProjectionTest.kt`
- Modify (test): `app/src/test/java/com/omegas/prohub/learning/LearningGridProjectionTest.kt` (paridade de delegação)
- Modify (test): `tests/ui/live-tracing-contract.test.cjs:8,18-23`, `tests/test_v82_integral_regression_contract.py:122-130` (apontar para `calibration/LiveCellProjection.kt` e `LiveCellProjection.liveInterpolationJson(`)

**Interfaces:**
- Consumes: `KMapPhysicalAxes.rpmBins(): IntArray`, `KMapPhysicalAxes.petrolBins(): DoubleArray`, `KMapPhysicalAxes.SCHEMA`, `KMapPhysicalAxes.LOCK_SHA256`, `ContinuousLearningMath.bilinearWeights`, `ContinuousLearningMath.trilinearWeights`.
- Produces (contrato do índice):
```kotlin
package com.omegas.prohub.calibration
object LiveCellProjection {
    val rpmBins: IntArray          // = KMapPhysicalAxes.rpmBins()
    val petrolBins: DoubleArray    // = KMapPhysicalAxes.petrolBins()
    val mapBins: DoubleArray       // = doubleArrayOf(0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 1.00)  (nome novo desta fatia)
    fun cellFor(rpm: Double, petrolMs: Double, mapBar: Double = 0.60): JSONObject
    fun liveInterpolationJson(rpm: Double, petrolMs: Double, mapBar: Double, sequence: Long, updatedAt: Long, telemetryValid: Boolean): JSONObject
}
```
O corpo é o de `LearningGridProjection.kt:36-104` de hoje, movido sem edição, junto com o `nearest` privado. `ContinuousLearningMath` passa a ler os eixos de `KMapPhysicalAxes`, guardados em `private val` (`rpmAxis = KMapPhysicalAxes.rpmBins().map(Int::toDouble).toDoubleArray()`, `petrolAxis = KMapPhysicalAxes.petrolBins()`), e não mais de `LearningGridProjection`. Com isso não se cria ciclo de inicialização com `LiveCellProjection`. `LearningGridProjection.rpmBins/petrolBins/mapBins` passam a ser `= LiveCellProjection.<mesmo>`, e `cellFor` e `liveInterpolationJson` passam a ser delegações de uma linha.

- [ ] **Step 1: Write the failing test**

`tests/test_f1_extracoes_contract.py` (o arquivo cresce a cada tarefa: uma classe por tarefa):
```python
import pathlib, re, unittest
ROOT = pathlib.Path(__file__).resolve().parents[1]
K = ROOT / "app/src/main/java/com/omegas/prohub"
def read(p): return pathlib.Path(p).read_text(encoding="utf-8")

class T11LiveCellProjection(unittest.TestCase):
    def test_live_cell_lives_in_calibration(self):
        src = read(K / "calibration/LiveCellProjection.kt")
        for must in ("object LiveCellProjection", "fun liveInterpolationJson(", "fun cellFor(",
                     "KMapPhysicalAxes.rpmBins()", "KMapPhysicalAxes.petrolBins()",
                     "ContinuousLearningMath.bilinearWeights", '.put("affectsCalibration", false)'):
            self.assertIn(must, src)
        hub = read(K / "web/HubJavascriptBridge.kt")
        self.assertEqual(hub.count("LiveCellProjection.liveInterpolationJson("), 2)
        self.assertNotIn("LearningGridProjection.liveInterpolationJson(", hub)
        clm = read(K / "learning/ContinuousLearningMath.kt")
        self.assertNotIn("LearningGridProjection", clm)
        self.assertIn("KMapPhysicalAxes", clm)
        lgp = read(K / "learning/LearningGridProjection.kt")
        self.assertIn("LiveCellProjection.cellFor(", lgp)
        self.assertIn("LiveCellProjection.liveInterpolationJson(", lgp)
        self.assertNotIn("ContinuousLearningMath.bilinearWeights", lgp)

if __name__ == "__main__":
    unittest.main()
```
`LiveCellProjectionTest.kt`:
```kotlin
@Test fun `celula ao vivo entre quatro pontos tem pesos bilineares exatos`() {
    val t = LiveCellProjection.liveInterpolationJson(2_000.0, 4.2, 0.62, 42L, 1_000L, true)
    val cell = t.getJSONObject("cell")
    assertTrue(t.getBoolean("valid")); assertEquals("BILINEAR_RPM_X_PETROL_MS", t.getString("method"))
    assertEquals("mp48-k-map-physical-axes-v1", t.getString("axisSchema"))
    assertEquals("4:2", cell.getString("key")); assertEquals(1_850, cell.getInt("rpmBin"))
    assertEquals(4.5, cell.getDouble("petrolBin"), 1e-12); assertEquals(0.60, cell.getDouble("mapBin"), 1e-12)
    val w = cell.getJSONArray("continuousWeights")
    val expected = listOf(Triple(3, 2, 3.0 / 13), Triple(3, 3, 0.9 / 13), Triple(4, 2, 7.0 / 13), Triple(4, 3, 2.1 / 13))
    assertEquals(4, w.length())
    expected.forEachIndexed { i, (r, c, v) -> val o = w.getJSONObject(i)
        assertEquals(r, o.getInt("row")); assertEquals(c, o.getInt("column")); assertEquals(v, o.getDouble("weight"), 1e-9) }
    assertEquals(1.0, t.getDouble("totalWeight"), 1e-9)
    assertFalse(t.getBoolean("affectsLearning")); assertFalse(t.getBoolean("affectsCalibration"))
}
@Test fun `telemetria parada fica invalida e cai no canto zero`() {
    val t = LiveCellProjection.liveInterpolationJson(0.0, 0.0, 0.0, 1L, 1L, true)
    assertFalse(t.getBoolean("valid")); assertEquals("0:0", t.getJSONObject("cell").getString("key"))
    assertEquals(0.20, t.getJSONObject("cell").getDouble("mapBin"), 1e-12)
}
@Test fun `eixos sao os do contrato fisico`() {
    assertArrayEquals(KMapPhysicalAxes.rpmBins(), LiveCellProjection.rpmBins)
    assertArrayEquals(KMapPhysicalAxes.petrolBins(), LiveCellProjection.petrolBins, 0.0)
}
```
`LearningGridProjectionTest.kt` (morre na F3 junto com a classe), novo método:
```kotlin
@Test fun `delegacao produz exatamente a mesma celula ao vivo`() {
    assertEquals(LiveCellProjection.liveInterpolationJson(2_000.0, 4.2, 0.62, 42L, 1_000L, true).toString(),
        LearningGridProjection.liveInterpolationJson(2_000.0, 4.2, 0.62, 42L, 1_000L, true).toString())
    assertEquals(LiveCellProjection.cellFor(2_500.0, 4.0).toString(), LearningGridProjection.cellFor(2_500.0, 4.0).toString())
}
```

- [ ] **Step 2: Run to see it fail**

Primeiro push: `git switch -c work/platina-f1-extracoes origin/OmegasPlatina && git push -u origin HEAD && gh pr create --draft --base OmegasPlatina --title "F1: extrações" --body "WIP"`.
Run: `tools/ci/remote-test.sh python tests/test_f1_extracoes_contract.py` → `REMOTE_TEST=FAIL` com `FileNotFoundError: …calibration/LiveCellProjection.kt`.
Run: `tools/ci/remote-test.sh gradle "com.omegas.prohub.calibration.LiveCellProjectionTest"` → `REMOTE_TEST=FAIL` (`Unresolved reference: LiveCellProjection`).

- [ ] **Step 3: Implement**: criar `LiveCellProjection`, fazer `LearningGridProjection` delegar, `ContinuousLearningMath` ler `KMapPhysicalAxes`, `HubJavascriptBridge` chamar `LiveCellProjection.liveInterpolationJson`. Repontar os dois testes de texto (`live-tracing-contract.test.cjs`, `test_v82_integral_regression_contract.py`) para o arquivo novo.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh gradle "com.omegas.prohub.calibration.*"` → `REMOTE_TEST=PASS`
Run: `tools/ci/remote-test.sh gradle "com.omegas.prohub.learning.*"` → `REMOTE_TEST=PASS` (inclui `ContinuousLearningMathTest` e `LearningGridProjectionTest` inalterados)
Run: `tools/ci/remote-test.sh python tests/test_f1_extracoes_contract.py` → `REMOTE_TEST=PASS`
Run: `tools/ci/remote-test.sh node tests/ui/live-tracing-contract.test.cjs` → `REMOTE_TEST=PASS`

- [ ] **Step 5: Commit** `refactor(calibration): LiveCellProjection assume a célula ao vivo sobre KMapPhysicalAxes`

---

### Task 1.2: `MotorSampleAnalyzer` e `SampleDecision` para `com.omegas.prohub.ecu`

**Files:**
- Move: `app/src/main/java/com/omegas/prohub/learning/MotorSampleAnalyzer.kt` → `app/src/main/java/com/omegas/prohub/ecu/MotorSampleAnalyzer.kt` (`git mv`). O arquivo leva `MotorSampleAnalyzer`, `SampleClassification`, `MotorSample`, `SampleDiagnostics`, `SampleTiming` e `SampleDecision`. Muda `package` na linha 1; os imports passam a ser `com.omegas.prohub.learning.{LearningTolerancePolicy, LearningToleranceSettings, AdaptiveSampleWindow, LearningTemperatureSettings}`; a linha `:646` troca `LearningGridProjection.cellFor` por `com.omegas.prohub.calibration.LiveCellProjection.cellFor`.
- Modify: `app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt:5,:7` (os dois imports saem, porque agora é o mesmo pacote; `:31`, `:49`, `:478` e `:491` não mudam)
- Modify: `app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt:6` (o import sai; `:312` e `:323-330` não mudam)
- Modify (só imports `com.omegas.prohub.ecu.<símbolo usado>`): `learning/AdaptiveSampleWindow.kt`, `learning/DeferredLiveOnlyLearningStore.kt`, `learning/LiveOnlyLearningStore.kt`, `learning/MotorLearningMemory.kt`, `learning/SignalLearningStore.kt`. São edições mecânicas de import, que precisam ir no mesmo commit para compilar.
- Move (test): `app/src/test/java/com/omegas/prohub/learning/MotorSampleAnalyzerTest.kt` e `MotorSampleAnalyzerBoundaryTest.kt` → `app/src/test/java/com/omegas/prohub/ecu/`, com `package com.omegas.prohub.ecu` e `import com.omegas.prohub.learning.LearningTolerancePolicy`.
- Modify (test, só imports): `app/src/test/java/com/omegas/prohub/learning/{AdaptiveSampleWindowTest, CumulativeSessionEvidenceTest, FuelEquivalenceMemoryTest, LiveOnlyLearningStoreTest, MotorLearningAdaptiveReferenceTest, MotorLearningMemoryTest, MotorLearningScaleCarryForwardTest, SignalLearningStoreTest}.kt`. `E2ELearningFlowTest` e `LearningLatencyContractTest` já fazem `import com.omegas.prohub.ecu.*`.
- Modify (test): `tests/test_mp48_serial_scheduler_behavior.py:40-53`. O stub sai de `com/omegas/prohub/learning/Stubs.kt` e se divide em dois: `com/omegas/prohub/ecu/SampleStubs.kt` (`package com.omegas.prohub.ecu`, com `SampleDecision` e `MotorSampleAnalyzer`, as mesmas linhas de hoje) e `com/omegas/prohub/learning/Stubs.kt`, que fica só com `Tolerances` e `LearningToleranceSettings`.
- Modify: `tests/test_f1_extracoes_contract.py` (classe `T12SampleAnalyzerInEcu`)

**Interfaces:**
- Produces: `com.omegas.prohub.ecu.MotorSampleAnalyzer(policyProvider: () -> LearningTolerancePolicy = { LearningToleranceSettings.current })` e `com.omegas.prohub.ecu.SampleDecision`, com a mesma API de hoje (contrato do índice). `onTelemetry: (Mp48Telemetry, SampleDecision, EngineMetrics) -> Unit` não muda.

- [ ] **Step 1: Write the failing test**

Em `tests/test_f1_extracoes_contract.py`:
```python
class T12SampleAnalyzerInEcu(unittest.TestCase):
    def test_analyzer_moved_to_ecu(self):
        self.assertFalse((K / "learning/MotorSampleAnalyzer.kt").exists())
        src = read(K / "ecu/MotorSampleAnalyzer.kt")
        self.assertTrue(src.startswith("package com.omegas.prohub.ecu"))
        for must in ("class MotorSampleAnalyzer(", "data class SampleDecision(", "LiveCellProjection.cellFor("):
            self.assertIn(must, src)
        self.assertNotIn("LearningGridProjection", src)
        for path in (ROOT / "app/src").rglob("*.kt"):
            text = read(path)
            self.assertNotIn("import com.omegas.prohub.learning.MotorSampleAnalyzer", text, path)
            self.assertNotIn("import com.omegas.prohub.learning.SampleDecision", text, path)
        runtime = read(K / "ecu/NativeRuntimeManager.kt")
        self.assertIn('.put("sample_state", decision.state)', runtime)
        self.assertIn('.put("sample", decision.toTelemetryJson())', runtime)
        self.assertIn('"com/omegas/prohub/ecu/SampleStubs.kt"', read(ROOT / "tests/test_mp48_serial_scheduler_behavior.py"))
```
Em `app/src/test/java/com/omegas/prohub/ecu/MotorSampleAnalyzerTest.kt` (já movido), dois métodos novos:
```kotlin
@Test fun `celula do quadro vem da projecao ao vivo`() {
    val d = MotorSampleAnalyzer().add(frame(0L, rpm = 2_000, petrolMs = 4.2))
    assertEquals("4:2", d.cellKey); assertEquals(4, d.cellRow); assertEquals(2, d.cellColumn)
}
@Test fun `evento de telemetria mantem as mesmas 20 chaves`() {
    val keys = SampleDecision.transition(state = "CUTOFF", reason = "cutoff").toTelemetryJson().keySet()
    assertEquals(setOf("state","reason","classification","frame_count","minimum_frames","desired_frames","duration_ms",
        "median_interval_ms","gap_ms","learning_eligible","fuel_confirmed","reason_code","window_age_ms","window_budget_ms",
        "frames_evicted","plausibility_reasons","cell_key","cell_row","cell_column","quality"), keys)
}
```

- [ ] **Step 2: Run to see it fail**

Run: `tools/ci/remote-test.sh python tests/test_f1_extracoes_contract.py` → `REMOTE_TEST=FAIL` (`AssertionError: True is not false` em `learning/MotorSampleAnalyzer.kt`)
Run: `tools/ci/remote-test.sh gradle "com.omegas.prohub.ecu.MotorSampleAnalyzerTest"` → `REMOTE_TEST=FAIL` (falha de compilação: `MotorSampleAnalyzer` não existe em `ecu`)

- [ ] **Step 3: Implement**: `git mv` do arquivo e dos dois testes, troca de `package`, imports, `:646` para `LiveCellProjection`, e o stub dividido no teste Python.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh gradle "com.omegas.prohub.ecu.*"` → `REMOTE_TEST=PASS`
Run: `tools/ci/remote-test.sh gradle "com.omegas.prohub.learning.*"` → `REMOTE_TEST=PASS`
Run: `tools/ci/remote-test.sh python tests/test_mp48_serial_scheduler_behavior.py` → `REMOTE_TEST=PASS` (log `MP48_SERIAL_SCHEDULER_BEHAVIOR=PASS`, ou `skipped` se o runner não tiver `kotlinc`)
Run: `tools/ci/remote-test.sh python tests/test_f1_extracoes_contract.py` → `REMOTE_TEST=PASS`

- [ ] **Step 5: Commit** `refactor(ecu): MotorSampleAnalyzer e SampleDecision passam a viver em ecu/`

---

### Task 1.3: `RefinementAutopilot` → `EquivalencePhases`

**Files:**
- Move: `app/src/main/java/com/omegas/prohub/autocal/RefinementAutopilot.kt` → `autocal/EquivalencePhases.kt`. Muda `class RefinementAutopilot(` para `class EquivalencePhases(` (`:29`) e o KDoc `:9` passa a dizer "Fases da equivalência". `FORMAT` (`:31`), `save`/`load` (`:285-317`) e todas as constantes ficam iguais.
- Modify: `app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt:16` (import), `:113-115` (`lateinit var equivalencePhases: EquivalencePhases`, com `private set`), `:159` (`equivalencePhases = EquivalencePhases(File(paths.runtimeRoot, "refinement_autopilot.json"))`), `:840-841` e `:853` (uso)
- Modify: `app/src/main/java/com/omegas/prohub/autocal/EquivalenceView.kt:14` (`phases: EquivalencePhases`), `:21` (`.put("autopilot", phases.json())`, chave inalterada)
- Modify: `app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt:292` (`service.equivalencePhases`), `:301` (`.put("autopilot", service.equivalencePhases.json())`)
- Verified, sem mudança: `diagnostics/SessionResumo.kt:31`, `:135-138` e `:200-202` usam só o evento `"refinement_phase"` e o texto "Fases do piloto do Refino", e não o tipo.
- Move (test): `app/src/test/java/com/omegas/prohub/autocal/RefinementAutopilotTest.kt` → `EquivalencePhasesTest.kt` (classe `EquivalencePhasesTest`, `pilot() = EquivalencePhases(null) { now }`)
- Modify (test): `RefinementRealSessionTest.kt:164`, `RefinementCycleScenarioTest.kt:22`, `EcuReferenceCycleTest.kt:20`, `EquivalenceViewPerformanceTest.kt:35`, `EcuReferenceScenarioTest.kt:136,152,158,167,175`, `app/src/androidTest/java/com/omegas/prohub/RefinoRenderTest.kt:185` (`service.equivalencePhases.observe(`)
- Modify: `tests/test_f1_extracoes_contract.py` (classe `T13EquivalencePhases`)

**Interfaces:**
- Produces (contrato do índice): `class EquivalencePhases(file: File? = null, clock: () -> Long = System::currentTimeMillis)` com `fun observe(ecuOnline: Boolean, monitor: JSONObject?, acquisition: JSONObject?, index: JSONObject, journal: JSONObject, restoreCount: Int): JSONObject`, `fun takeAlert(): JSONObject?`, `fun json(): JSONObject` e `companion object { const val FORMAT = "omegas-refinement-autopilot-v1"; … }`. `TelemetryForegroundService.equivalencePhases: EquivalencePhases` é um nome novo desta fatia, que a F4/F5 consomem.

- [ ] **Step 1: Write the failing test**

```python
class T13EquivalencePhases(unittest.TestCase):
    def test_renamed_with_same_disk_format(self):
        self.assertFalse((K / "autocal/RefinementAutopilot.kt").exists())
        src = read(K / "autocal/EquivalencePhases.kt")
        self.assertIn("class EquivalencePhases(", src)
        self.assertIn('const val FORMAT = "omegas-refinement-autopilot-v1"', src)
        svc = read(K / "service/TelemetryForegroundService.kt")
        self.assertIn('EquivalencePhases(File(paths.runtimeRoot, "refinement_autopilot.json"))', svc)
        self.assertIn("lateinit var equivalencePhases: EquivalencePhases", svc)
        self.assertIn('.put("autopilot", service.equivalencePhases.json())', read(K / "autocal/AutoCalJavascriptBridge.kt"))
        self.assertIn('.put("autopilot", phases.json())', read(K / "autocal/EquivalenceView.kt"))
        for path in (ROOT / "app/src").rglob("*.kt"):
            self.assertNotIn("RefinementAutopilot", read(path), path)
            self.assertNotIn("refinementAutopilot", read(path), path)
```
`EquivalencePhasesTest.kt`: os 8 testes de hoje, renomeados, mais três (Review Focus 1):
```kotlin
@get:Rule val tmp = TemporaryFolder()
private fun oldFile(body: String) = tmp.newFile("refinement_autopilot.json").apply { writeText(body) }

@Test fun `arquivo do APK antigo no formato v1 e lido e o aviso ja dado nao se repete`() {
    val f = oldFile("""{"format":"omegas-refinement-autopilot-v1","lastCount":3,"quietMs":0,"phase":"PROPOSTA_PRONTA","alertedPhase":"PROPOSTA_PRONTA","ecuDoneLatch":"MAX_AUTOMATCH","campoFuturo":true}""")
    val p = EquivalencePhases(f) { now }
    assertEquals("PROPOSTA_PRONTA", p.observe(true, monitor(3), acquisition(18, 15), offIndex, noJournal, 0).getString("phase"))
    assertNull(p.takeAlert())
}
@Test fun `formato desconhecido e ignorado e JSON quebrado nao derruba`() {
    val p1 = EquivalencePhases(oldFile("""{"format":"omegas-x-v9","alertedPhase":"PROPOSTA_PRONTA"}""")) { now }
    p1.observe(true, monitor(3), acquisition(18, 15), offIndex, noJournal, 0)
    assertTrue(p1.takeAlert() != null)
    tmp.root.resolve("refinement_autopilot.json").writeText("{")
    val p2 = EquivalencePhases(tmp.root.resolve("refinement_autopilot.json")) { now }
    assertEquals("SEM_ECU", p2.json().getString("phase"))
}
@Test fun `gravacao continua no formato v1 com o mesmo nome de arquivo`() {
    val f = tmp.root.resolve("refinement_autopilot.json")
    val p = EquivalencePhases(f) { now }
    p.observe(true, monitor(3), acquisition(18, 15), offIndex, noJournal, 0); p.takeAlert()
    val saved = JSONObject(f.readText())
    assertEquals("omegas-refinement-autopilot-v1", saved.getString("format"))
    assertEquals("PROPOSTA_PRONTA", saved.getString("alertedPhase"))
}
```

- [ ] **Step 2: Run to see it fail**

Run: `tools/ci/remote-test.sh gradle "com.omegas.prohub.autocal.EquivalencePhasesTest"` → `REMOTE_TEST=FAIL` (`Unresolved reference: EquivalencePhases`)
Run: `tools/ci/remote-test.sh python tests/test_f1_extracoes_contract.py` → `REMOTE_TEST=FAIL` (`RefinementAutopilot.kt` ainda existe)

- [ ] **Step 3: Implement**: `git mv`, renomear a classe, o campo do serviço e os consumidores listados. Nenhuma chave JSON muda.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh gradle "com.omegas.prohub.autocal.*"` → `REMOTE_TEST=PASS` (inclui `EcuReference*`, `Refinement*`, `EquivalenceViewPerformanceTest`)
Run: `tools/ci/remote-test.sh gradle "com.omegas.prohub.diagnostics.SessionResumoTest"` → `REMOTE_TEST=PASS`
Run: `tools/ci/remote-test.sh python tests/test_f1_extracoes_contract.py` → `REMOTE_TEST=PASS`

- [ ] **Step 5: Commit** `refactor(autocal): RefinementAutopilot vira EquivalencePhases (formato em disco inalterado)`

---

### Task 1.4: `CalibrationOperationsBridge` (JS `OmegasCalibration`) criado e registrado

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/web/CalibrationOperationsBridge.kt`. É uma cópia de `V7JavascriptBridge.kt:22-35` (companion `OPERATION_TIMEOUT_MS`, `activityRef`, executor, `busy`, `lastOperation`), `:50-57`, `:92-110`, `:112-223`, `:225-335`, `:337-536`, `:546-594` e `:596-599`, **sem** as linhas `:299-305`/`:314` e `:487-493`/`:508` (reconciliação V7). O nome da thread vira `"omegas-calibration-operations"`.
- Modify: `app/src/main/java/com/omegas/prohub/MainActivity.kt:29-31` (import), `:44` (`private var calibrationBridge: CalibrationOperationsBridge? = null`), `:252-260` (`calibrationBridge?.destroy()`, `= null`, `removeJavascriptInterface(CalibrationOperationsBridge.JS_NAME)`), `:307-312` (criar **antes** do `V7JavascriptBridge` e `addJavascriptInterface(calibrationBridge!!, CalibrationOperationsBridge.JS_NAME)`)
- Modify (test, repontar para o arquivo novo): `tests/test_v7_map_batch_contract.py:7`, `:18-27` (nova constante `CALIBRATION` e `self.calibration`; os métodos `:28-33`, `:54-58`, `:60-63` e `:65-85` usam `self.calibration` no lugar de `self.bridge`; `test_legacy_apply_suggestion_is_prepare_only` `:87-91` continua em `self.bridge`), `tests/test_block1_session_contract.py:7`, `tests/test_map_kotlin_math_authority_contract.py:9`, `tests/test_final_storage_and_curve_reset_contract.py:9`, `tests/test_final_pre_apk_product_contract.py:6`
- Modify: `tests/test_f1_extracoes_contract.py` (classe `T14CalibrationBridge`)

**Interfaces:**
- Produces (contrato do índice, mesmas assinaturas do V7):
```kotlin
package com.omegas.prohub.web
class CalibrationOperationsBridge(activity: MainActivity) {
    companion object { const val JS_NAME = "OmegasCalibration" }           // nome novo desta fatia
    fun destroy()
    @JavascriptInterface fun getLastOperation(): String
    @JavascriptInterface fun previewMapAdjustment(cellsJson: String, mode: String, adjustment: Double): String
    @JavascriptInterface fun startCurveRead(): String
    @JavascriptInterface fun startCurveBackup(label: String): String
    @JavascriptInterface fun listCurveBackups(): String
    @JavascriptInterface fun startCurveRestorePrepare(fileName: String): String
    @JavascriptInterface fun startCurveReset(): String
    @JavascriptInterface fun startCurveBatchWrite(pointsJson: String, reason: String): String
    @JavascriptInterface fun startMapBatchWrite(cellsJson: String, maxStep: Int, pauseMs: Int, reason: String): String
    internal fun startOperation(state: String, action: (TelemetryForegroundService) -> String): String  // consumido pelo V7 na 1.5
}
```
Nesta tarefa o `V7JavascriptBridge` ainda não muda, e o JS continua chamando `OmegasV7`. Assim nada muda em comportamento até a 1.5.

- [ ] **Step 1: Write the failing test**

```python
NINE = {
    "startCurveRead": "fun startCurveRead(): String",
    "startCurveBackup": "fun startCurveBackup(label: String): String",
    "listCurveBackups": "fun listCurveBackups(): String",
    "startCurveRestorePrepare": "fun startCurveRestorePrepare(fileName: String): String",
    "startCurveReset": "fun startCurveReset(): String",
    "startCurveBatchWrite": "fun startCurveBatchWrite(pointsJson: String, reason: String): String",
    "startMapBatchWrite": "fun startMapBatchWrite(cellsJson: String, maxStep: Int, pauseMs: Int, reason: String): String",
    "previewMapAdjustment": "fun previewMapAdjustment(cellsJson: String, mode: String, adjustment: Double): String",
    "getLastOperation": "fun getLastOperation(): String",
}
class T14CalibrationBridge(unittest.TestCase):
    def test_bridge_owns_the_nine_endpoints_without_v7(self):
        src = read(K / "web/CalibrationOperationsBridge.kt")
        self.assertIn('const val JS_NAME = "OmegasCalibration"', src)
        for name, sig in NINE.items():
            self.assertRegex(src, r"@JavascriptInterface\s+" + re.escape(sig), name)
        for gone in ("v7Reconcile", "suggestionReconciliation", "com.omegas.prohub.service.v7"):
            self.assertNotIn(gone, src)
        self.assertIn("internal fun startOperation(", src)
        self.assertEqual(src.count("Executors.newSingleThreadExecutor"), 1)
        self.assertEqual(src.count("AtomicBoolean(false)"), 1)
        for kept in ('.put("state", "BATCH_CONFIRMED")', '.put("readbackValid", true)', '.put("humanConfirmed", true)',
                     '"Outra operação V8 está em andamento"', "MapBatchPlan.build(cells)"):
            self.assertIn(kept, src)
        main = read(K / "MainActivity.kt")
        self.assertIn("addJavascriptInterface(calibrationBridge!!, CalibrationOperationsBridge.JS_NAME)", main)
        self.assertIn("removeJavascriptInterface(CalibrationOperationsBridge.JS_NAME)", main)
        self.assertIn("calibrationBridge?.destroy()", main)
        self.assertLess(main.index("calibrationBridge = CalibrationOperationsBridge(this)"), main.index("V7JavascriptBridge(this"))
    def test_no_ui_reads_suggestion_reconciliation(self):
        for path in (ROOT / "app/src/main/assets/ui").rglob("*.js"):
            self.assertNotIn("suggestionReconciliation", read(path), path)
```
Os cinco testes Python repontados (lista em Files) passam a ler `web/CalibrationOperationsBridge.kt` e falham com `FileNotFoundError` até o arquivo existir.

- [ ] **Step 2: Run to see it fail**

Run: `tools/ci/remote-test.sh python tests/test_f1_extracoes_contract.py` → `REMOTE_TEST=FAIL` (`FileNotFoundError: …web/CalibrationOperationsBridge.kt`)
Run: `tools/ci/remote-test.sh python tests/test_v7_map_batch_contract.py` → `REMOTE_TEST=FAIL` (o mesmo `FileNotFoundError`)

- [ ] **Step 3: Implement**: criar a ponte conforme Files/Interfaces e registrar no `MainActivity`.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh checks ""` → `REMOTE_TEST=PASS` (compila, roda os contratos, o JVM e o lint; os 5 Python repontados ficam verdes)

- [ ] **Step 5: Commit** `feat(web): CalibrationOperationsBridge (OmegasCalibration) com os 9 endpoints de curva/mapa`

---

### Task 1.5: JS passa a usar `OmegasCalibration`; V7 perde os endpoints; androidTest pela ponte nova

**Files:**
- Modify: `app/src/main/assets/ui/core/native-api.js:170` (`this.calibration = root.OmegasCalibration || null;`), `:292`, `:296`, `:298`, `:302`, `:306`, `:310`, `:314`, `:318`, `:322` e `:341` (`invoke(this.v7, …` vira `invoke(this.calibration, …`; fallbacks e mensagens iguais)
- Modify: `app/src/main/java/com/omegas/prohub/web/V7JavascriptBridge.kt`. Saem `:5-7`, `:11`, `:18-19` (imports), `:23-25` (companion), `:29-35` (executor, `busy`, `lastOperation`), `:37-40` (`destroy`), `:50-57`, `:92-536` e `:546-594`. `activityRef` (`:27-28`) fica. O construtor (`:22`) vira `V7JavascriptBridge(activity: MainActivity, private val operations: CalibrationOperationsBridge)`, e `synchronizeFromEcu` (`:71-74`) passa a ser `operations.startOperation("SYNCHRONIZING_ECU") { it.v7SynchronizeCalibration(fileName) }`. `applySuggestion`, `getState`, `listSessionFiles`, `saveSession`, `loadSession` e `unavailable()` ficam.
- Modify: `app/src/main/java/com/omegas/prohub/MainActivity.kt:254-255` (sai `v7Bridge?.destroy()`), `:308` (`V7JavascriptBridge(this, calibrationBridge!!)`)
- Modify (test): `app/src/androidTest/java/com/omegas/prohub/DashboardLevelsRenderTest.kt:443` e `:453-456` (`"v7Bridge"` vira `"calibrationBridge"`; mensagem `"Calibration bridge unavailable"`; `getLastOperation` e `lastOperation` agora na ponte nova)
- Modify (test): `tests/ui/app-shell-runtime.test.cjs:127-155`, `tests/ui/didactic-expansion.test.cjs:49` (`OmegasCalibration: {}`), `tests/test_map_kotlin_math_authority_contract.py:26` (`invoke(this.calibration, 'previewMapAdjustment'`)
- Delete (test): `tests/test_suggestion_readback_lifecycle_contract.py`. Ele exige a reconciliação V7 que sai aqui; a spec §4.6 já o lista entre os testes que morrem.
- Modify: `tests/test_f1_extracoes_contract.py` (classe `T15OnlyCalibrationWrites`)

**Interfaces:**
- Consumes: `CalibrationOperationsBridge.startOperation` (Task 1.4).
- Produces: `NativeApi` com `this.calibration`. A API pública de `NativeApi` (`writeMap`, `writeCurve`, `startCurveRead`, `startCurveBackup`, `curveBackups`, `prepareCurveRestore`, `resetCurve`, `curveOperation`, `mapWriteOperation`, `previewMapAdjustment`) não muda.

- [ ] **Step 1: Write the failing test**

`tests/ui/app-shell-runtime.test.cjs`: substituir o teste `:127-155` por:
```js
test('APK usa OmegasCalibration para curva e mapa e nunca OmegasV7', () => {
  const { context } = bootCore();
  const calls = []; const v7Touched = [];
  context.OmegasNative = { getStatus: () => '{}', getLiveTelemetry: () => '{}', getLearningMaps: () => '{}', getLearningSyncStatus: () => '{}', getObdStatus: () => '{}' };
  context.OmegasV7 = new Proxy({}, { get: (_, key) => { v7Touched.push(String(key)); return undefined; } });
  const ok = type => (...args) => { calls.push({ type, args }); return JSON.stringify({ ok: true, started: true }); };
  context.OmegasCalibration = {
    startMapBatchWrite: ok('map'), startCurveBatchWrite: ok('curve'), startCurveRead: ok('read'),
    startCurveBackup: ok('backup'), startCurveRestorePrepare: ok('restore'), startCurveReset: ok('reset'),
    previewMapAdjustment: ok('preview'),
    listCurveBackups: (...args) => { calls.push({ type: 'list', args }); return '[]'; },
    getLastOperation: (...args) => { calls.push({ type: 'last', args }); return JSON.stringify({ ok: true, state: 'IDLE', busy: false }); },
  };
  const api = new context.OmegasUi.NativeApi();
  assert.equal(api.isDemo(), false);
  api.writeMap([{ row: 0, column: 0, current: 120, target: 125 }], 3, 150, 'teste');
  api.writeCurve([{ index: 0, currentRaw: 12000, targetRaw: 12100 }], 'teste curva');
  api.startCurveRead(); api.startCurveBackup('b1'); api.curveBackups(); api.prepareCurveRestore('k.json');
  api.resetCurve(); api.previewMapAdjustment([{ row: 0, column: 0, current: 120 }], 'percent', 2);
  assert.equal(api.curveOperation().state, 'IDLE'); assert.equal(api.mapWriteOperation().state, 'IDLE');
  assert.deepEqual(calls.map(c => c.type), ['map', 'curve', 'read', 'backup', 'list', 'restore', 'reset', 'preview', 'last', 'last']);
  assert.deepEqual(calls[0].args.slice(1), [0, 0, 'teste']);
  assert.equal(JSON.parse(calls[0].args[0]).length, 1);
  assert.deepEqual(calls[7].args.slice(1), ['percent', 2]);
  assert.deepEqual(v7Touched, []);
});
```
`tests/test_f1_extracoes_contract.py`:
```python
class T15OnlyCalibrationWrites(unittest.TestCase):
    def test_v7_has_no_write_endpoint_and_ui_never_calls_it(self):
        v7 = read(K / "web/V7JavascriptBridge.kt")
        for name in NINE:
            self.assertNotRegex(v7, r"fun %s\(" % name, name)
        for gone in ("MapBatchPlan", "CalibrationWriteSafetyPolicy", "v7ReconcileConfirmedManualWrite", "Executors", "AtomicBoolean"):
            self.assertNotIn(gone, v7)
        self.assertIn('operations.startOperation("SYNCHRONIZING_ECU")', v7)
        api = read(ROOT / "app/src/main/assets/ui/core/native-api.js")
        self.assertIn("root.OmegasCalibration", api)
        self.assertNotIn("this.v7", api)
        for name in NINE:
            self.assertIn("invoke(this.calibration, '%s'" % name, api)
        for path in (ROOT / "app/src/main/assets/ui").rglob("*.js"):
            self.assertNotIn("OmegasV7", read(path), path)
        self.assertFalse((ROOT / "tests/test_suggestion_readback_lifecycle_contract.py").exists())
        android = read(ROOT / "app/src/androidTest/java/com/omegas/prohub/DashboardLevelsRenderTest.kt")
        self.assertIn('"calibrationBridge"', android)
        self.assertNotIn('"v7Bridge"', android)
```

- [ ] **Step 2: Run to see it fail**

Run: `tools/ci/remote-test.sh node tests/ui/app-shell-runtime.test.cjs` → `REMOTE_TEST=FAIL` (`calls` vazio: o `native-api.js` ainda usa `root.OmegasV7`; `v7Touched` contém `'startMapBatchWrite'`)
Run: `tools/ci/remote-test.sh python tests/test_f1_extracoes_contract.py` → `REMOTE_TEST=FAIL` (`fun startCurveRead(` ainda em `V7JavascriptBridge.kt`)

- [ ] **Step 3: Implement**: `native-api.js`, enxugar o V7, `MainActivity`, `DashboardLevelsRenderTest`, os dois testes UI e o teste Python `:26`; apagar `test_suggestion_readback_lifecycle_contract.py`.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh node tests/ui/app-shell-runtime.test.cjs` → `REMOTE_TEST=PASS`
Run: `tools/ci/remote-test.sh node tests/ui/didactic-expansion.test.cjs` → `REMOTE_TEST=PASS`
Run: `tools/ci/remote-test.sh checks ""` → `REMOTE_TEST=PASS` (inclui `map-workflow-e2e`, `refino-screen`, `curve-map-editor-coherence` e `test_block3_suggestion_ui_contract`, que ainda lê `applySuggestion` no V7)

- [ ] **Step 5: Commit** `refactor(web): UI grava curva/mapa só por OmegasCalibration; V7 sem endpoints de escrita`

---

### Task 1.6: Gate final, render no emulador e merge

**Files:** nenhum arquivo de produção. Só `docs`, se o PR exigir corpo, e nenhum outro.

- [ ] **Step 1: Write the failing test**: não se aplica. O gate é a suíte inteira e o render existente, que tem que continuar como está.

- [ ] **Step 2: Run the full gate**

Run: `tools/ci/remote-test.sh checks ""` → `REMOTE_TEST=PASS`

- [ ] **Step 3: Render inalterado (androidTest compila e roda só aqui)**

Run: `gh workflow run verde-android-render-evidence.yml --ref work/platina-f1-extracoes && sleep 15 && gh run watch "$(gh run list --workflow verde-android-render-evidence.yml --branch work/platina-f1-extracoes -L1 --json databaseId -q '.[0].databaseId')" --exit-status`
Expected: todos os jobs da matriz `success`, inclusive `refino-ecu-automatico` (`RefinoRenderTest`, que usa `service.equivalencePhases`) e `curve-original-lognovo` (`DashboardLevelsRenderTest`, que injeta `lastOperation` em `calibrationBridge`). Se falhar: `gh run view <id> --log-failed | tail -200`, corrigir e repetir.

- [ ] **Step 4: Corpo do PR**

`gh pr edit --body` com: **o que mudou** (as 5 extrações e a lista de arquivos movidos); **classe de prova** 1 (contrato), 2 (JVM sintético) e 4 (render no emulador); **não provado**: escrita real de Curva K e Mapa K pela ponte `OmegasCalibration` em ECU física (classe 5), e um aparelho com `refinement_autopilot.json` antigo de verdade (só um arquivo sintético no teste).

- [ ] **Step 5: Marcar pronto e mesclar**

Run: `gh pr ready && gh pr checks --watch && gh pr merge --merge`
Expected: `OMEGAS PLATINA CI` verde no SHA do PR; PR mesclado em `OmegasPlatina`.

---

## Cobertura da spec

| Requisito (spec) | Task |
|---|---|
| §4.2 escrita/operações de Curva K e Mapa K (9 endpoints) saem do `V7JavascriptBridge`, sem `v7Reconcile…` | 1.4, 1.5 |
| §4.2 `curve.js`/`map.js`/`refino.js` deixam de gravar por `OmegasV7` (via `native-api.js`) | 1.5 |
| §4.2 célula ao vivo (`liveInterpolationJson`, `cellFor`) → `calibration/` sobre `KMapPhysicalAxes` | 1.1 |
| §4.2 eixos `rpmBins`/`petrolBins` → `KMapPhysicalAxes` (consumo do `ContinuousLearningMath`) | 1.1 |
| §4.2 `MotorSampleAnalyzer`/`SampleDecision` → `ecu/`; formato de sessão (`sample_state`, `sample{}`) inalterado | 1.2 |
| §4.2 `RefinementAutopilot` → `EquivalencePhases` | 1.3 |
| §4.2 reflexão de `v7Bridge`/`lastOperation` no `DashboardLevelsRenderTest` | 1.5 |
| §4.4 dados instalados: `refinement_autopilot.json` antigo abre sem crash (Review Focus 1 do índice) | 1.3 |
| §4.6 `RefinementAutopilotTest` vira `EquivalencePhasesTest`; editados `test_mp48_serial_scheduler_behavior`, `app-shell-runtime`, `didactic-expansion`, `live-tracing-contract`, `RefinoRenderTest`, `DashboardLevelsRenderTest` | 1.1, 1.2, 1.3, 1.5 |
| §4.6 `test_suggestion_readback_lifecycle` morre (antecipado para cá, porque testa a reconciliação removida) | 1.5 |
| §7 Fatia 1: "tudo que existe continua verde; render do Refino inalterado" | 1.6 |
| §4.5 passo 0 = um PR verde | 1.6 |

**Fica para outra fatia:** spec §4.2 manda a escrita para a "fila única (§2)". Nesta fatia ela vai para `CalibrationOperationsBridge`, que já tem uma fila só (um executor e uma flag). A `OperationQueue`, com intents, foto e Desfazer, nasce na F5 e passa a ser chamada por essa ponte.
