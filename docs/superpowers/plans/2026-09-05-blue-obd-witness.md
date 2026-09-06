# Blue OBD Witness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the existing RED-derived OBD sidecar into a reliable STFT witness that pairs each OBD observation with MP48 RPM/MAP/Petrol Inj., builds gasoline-relative residuals by calibration state, and accelerates Blue confidence without ever computing or writing K.

**Architecture:** Keep OBD physically and logically separate from `BlueCausalEngine` and all writers. Shrink live OBD acquisition to an ELM connection state machine plus PID `01 06` STFT; every accepted STFT is timestamp-paired to a historical MP48 frame, then stored by fuel and stable calibration fingerprint. A pure witness engine compares GNV STFT against compatible gasoline STFT in RPM/MAP/Petrol Inj. space and returns `SUPPORTS`, `CONFLICTS`, `INSUFFICIENT`, or `UNAVAILABLE`; only `SUPPORTS` can increase exposed Blue confidence, never change a correction target.

**Tech Stack:** Kotlin/JVM, Android Bluetooth RFCOMM, ELM327 ASCII command protocol, `org.json`, JUnit 4, existing Python FAST contracts, GitHub Actions Android CI.

**Spec:** `docs/superpowers/specs/2026-09-05-blue-obd-witness-design.md`

## Global Constraints

- Blue/MP48 remains fully functional without OBD.
- The OBD learning signal is STFT only; `01 00`/ELM commands are transport handshake, not evidence.
- Only MP48 RPM, MP48 MAP and MP48 Petrol Inj. participate in operating-condition matching.
- Coolant, calculated load, throttle, MAF, vehicle speed, IAT, LTFT and other PIDs do not participate in the evidence model.
- Gasoline and GNV observations are stored separately.
- A confirmed Map K or Curve K readback creates a new calibration-state boundary.
- OBD cannot call Map K/Curve K writers and never changes a Blue K target.
- Manual calibration remains prepare → review → confirm → ACK → readback; RPM never authorizes or blocks a write.
- Every implementation slice is TDD: observe RED for the intended reason, implement the smallest coherent change, then focused GREEN.
- Final proof is FAST → full JVM/unit → lint → APK on one exact SHA. Physical Bluetooth/vehicle behavior remains unverified until tested in the car.

---

### Task 1: Lock the three-variable scientific invariant

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/blue/BlueCausalEngine.kt`
- Create: `app/src/test/java/com/omegas/prohub/blue/BluePhysicalMatchingInvariantTest.kt`
- Create: `tests/test_blue_obd_minimal_signal_contract.py`

**Interfaces:**
- Consumes: existing `BlueCausalEngine.petrolReference(FuelEvidence, List<FuelEvidence>)`.
- Produces: Blue reference matching whose distance depends on RPM and MAP only; Petrol Inj. remains the measured output and downstream Map-K coordinate. OBD structural contract forbids evidence dependence on coolant/load/throttle/MAF/speed/IAT/LTFT.

- [ ] **Step 1: Write the failing Blue matching regression**

```kotlin
@Test fun `temperature cannot change petrol reference selection`() {
    val engine = BlueCausalEngine()
    val target = evidence(FuelKind.CNG, rpm = 2000.0, map = 0.55, petrol = 4.20, water = 20.0)
    val cold = evidence(FuelKind.PETROL, rpm = 2000.0, map = 0.55, petrol = 4.00, water = 20.0)
    val hot = evidence(FuelKind.PETROL, rpm = 2000.0, map = 0.55, petrol = 4.40, water = 100.0)

    val reference = engine.petrolReference(target, listOf(cold, hot))!!

    assertEquals(4.20, reference.petrolMs, 0.0001)
    assertEquals(2, reference.supportCount)
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*BluePhysicalMatchingInvariantTest*' --no-daemon`
Expected: FAIL because the current normalized distance includes `waterC` and excludes the thermally distant otherwise-identical reference.

- [ ] **Step 3: Remove temperature from Blue matching math**

```kotlin
private fun normalizedDistance(reference: FuelEvidence, target: FuelEvidence): Double {
    val rpmScale = max(policy.minimumRpmWindow, target.rpm * policy.relativeRpmWindow)
    val rpm = abs(reference.rpm - target.rpm) / rpmScale
    val map = abs(reference.mapBar - target.mapBar) / policy.mapWindowBar
    return sqrt(rpm.pow(2) + map.pow(2))
}
```

Remove `waterWindowC` from `BluePolicy` if no production caller remains.

- [ ] **Step 4: Add FAST structural contract for OBD minimal signal**

```python
def test_obd_evidence_model_is_stft_plus_mp48_three_variables():
    obd = read('app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt')
    assert '0106' in obd
    for forbidden in ['0105', '0104', '0111', '0110', '010F', '010D', '0107']:
        assert forbidden not in evidence_poll_commands(obd)
```

The contract may allow `0100` only in connection/PID-support handshake.

- [ ] **Step 5: Run focused JVM + FAST, then commit**

Run: `./gradlew testDebugUnitTest --tests '*BluePhysicalMatchingInvariantTest*' --no-daemon && python3 -B tools/run_checks.py`
Expected: PASS.
Commit: `test(blue): lock RPM MAP physical matching invariant`

---

### Task 2: Add deterministic nearest-frame MP48 matching

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/telemetry/TelemetryStateStore.kt`
- Create: `app/src/test/java/com/omegas/prohub/telemetry/TelemetryNearestFrameTest.kt`

**Interfaces:**
- Produces: `fun nearestFrame(observedAtMs: Long, maxSkewMs: Long): JSONObject?` returning a defensive copy with `timestamp`, `rpm`, `map_bar`, `petrol_ms`, `fuel`, `sequence`, and `skew_ms`.
- Consumes later: OBD STFT observation timestamp.

- [ ] **Step 1: Write RED tests for nearest frame, tie-breaking and skew rejection**

```kotlin
@Test fun `nearest frame returns historical MP48 context not current snapshot`() {
    val store = TelemetryStateStore(historyLimit = 10)
    store.beginSession(7)
    store.updateFromEngineEvent(frame(7, 1000L, rpm = 1500, map = 0.40, petrol = 3.0, fuel = "PETROL"))
    store.updateFromEngineEvent(frame(7, 1200L, rpm = 1800, map = 0.55, petrol = 4.5, fuel = "CNG"))

    val matched = store.nearestFrame(1160L, 100L)!!

    assertEquals(1800, matched.getInt("rpm"))
    assertEquals(40L, matched.getLong("skew_ms"))
    assertEquals("CNG", matched.getString("fuel"))
}

@Test fun `nearest frame rejects excessive temporal skew`() {
    assertNull(store.nearestFrame(5000L, 120L))
}
```

Test helper events must carry a deterministic source timestamp field; production history must preserve the source frame timestamp instead of replacing it with wall-clock receipt time when available.

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*TelemetryNearestFrameTest*' --no-daemon`
Expected: FAIL because no nearest-frame API exists and history does not preserve fuel/source timestamp sufficiently.

- [ ] **Step 3: Implement the minimal history seam**

Store `fuel` and a source timestamp in each history record. Implement nearest search over the bounded in-memory deque and reject when `abs(frame.timestamp - observedAtMs) > maxSkewMs`.

- [ ] **Step 4: Run focused tests and commit**

Run: `./gradlew testDebugUnitTest --tests '*TelemetryNearestFrameTest*' --no-daemon`
Expected: PASS.
Commit: `feat(telemetry): match OBD observations to nearest MP48 frame`

---

### Task 3: Make ELM connection bounded and stage-aware

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/obd/ElmConnectionState.kt`
- Create: `app/src/main/java/com/omegas/prohub/obd/ElmResponseParser.kt`
- Create: `app/src/test/java/com/omegas/prohub/obd/ElmConnectionStateTest.kt`
- Create: `app/src/test/java/com/omegas/prohub/obd/ElmResponseParserTest.kt`
- Modify later in this task: `app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt`

**Interfaces:**
- `enum class ElmStage { IDLE, PERMISSION, RFCOMM, ELM_INIT, PROTOCOL, STFT_READY, LIVE, ERROR }`
- `data class ElmConnectionStatus(stage, errorCode, detail, startedAtMs, updatedAtMs, retryable)`
- `ElmResponseParser.mode01(response: String, pid: Int): List<Int>?`

- [ ] **Step 1: Write parser RED cases**

```kotlin
@Test fun `parses spaced and compact 4106 responses`() {
    assertEquals(listOf(0x90), ElmResponseParser.mode01("41 06 90 >", 0x06))
    assertEquals(listOf(0x90), ElmResponseParser.mode01("410690", 0x06))
}

@Test fun `rejects NO DATA and wrong PID`() {
    assertNull(ElmResponseParser.mode01("NO DATA", 0x06))
    assertNull(ElmResponseParser.mode01("41 0C 1A F8", 0x06))
}
```

- [ ] **Step 2: Write connection-state RED cases**

```kotlin
@Test fun `stalled rfcomm becomes retryable timeout`() {
    val state = ElmConnectionState(connectTimeoutMs = 12_000)
    state.enter(ElmStage.RFCOMM, 1_000)
    val timed = state.onClock(13_001)
    assertEquals(ElmStage.ERROR, timed.stage)
    assertEquals("RFCOMM_TIMEOUT", timed.errorCode)
    assertTrue(timed.retryable)
}
```

- [ ] **Step 3: Run focused tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*Elm*Test*' --no-daemon`
Expected: FAIL because these components do not exist.

- [ ] **Step 4: Implement parser/state machine and integrate status only**

Do not change evidence behavior yet. `ObdAssistManager.statusJson()` must expose `connectionStage`, `connectionErrorCode`, `connectionDetail`, `retryable`. A socket blocked beyond the RFCOMM deadline must be closed from the controller path so the worker can recover.

- [ ] **Step 5: Run focused tests + existing OBD tests and commit**

Run: `./gradlew testDebugUnitTest --tests '*obd*' --no-daemon`
Expected: PASS.
Commit: `feat(obd): add bounded ELM connection state machine`

---

### Task 4: Replace scanner-style polling with STFT-first acquisition

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt`
- Create: `app/src/test/java/com/omegas/prohub/obd/ObdStftAcquisitionTest.kt`

**Interfaces:**
- Internal observation: `data class StftObservation(val requestedAtMs: Long, val observedAtMs: Long, val stftPct: Double)`.
- Runtime evidence callback receives only STFT plus timestamps; MP48 variables are supplied by Task 2 matching.

- [ ] **Step 1: Write RED conversion/parsing test**

```kotlin
@Test fun `PID 0106 byte converts to SAE STFT percent`() {
    assertEquals(0.0, ObdStftCodec.percent(128), 0.0001)
    assertEquals(10.15625, ObdStftCodec.percent(141), 0.0001)
    assertEquals(-10.15625, ObdStftCodec.percent(115), 0.0001)
}
```

- [ ] **Step 2: Write structural RED that production poll loop contains no scanner PID sweep**

The FAST contract from Task 1 must fail against the current manager because it still polls LTFT, speed, coolant, load, throttle, MAP, IAT, MAF, fuel level and voltage.

- [ ] **Step 3: Simplify ELM initialization and live loop**

Connection handshake may use `ATZ`, `ATI`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATAT1`, `ATSP0`, `0100`, and protocol identity. The live loop repeatedly requests `0106`; it records request/response timestamps and emits STFT observations. Do not request other learning PIDs.

- [ ] **Step 4: Run focused + FAST tests and commit**

Run: `./gradlew testDebugUnitTest --tests '*ObdStftAcquisitionTest*' --tests '*Elm*Test*' --no-daemon && python3 -B tools/run_checks.py`
Expected: PASS.
Commit: `refactor(obd): make live acquisition STFT first`

---

### Task 5: Build gasoline-relative OBD witness evidence

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/obd/ObdWitnessEngine.kt`
- Create: `app/src/test/java/com/omegas/prohub/obd/ObdWitnessEngineTest.kt`
- Modify: `app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt`

**Interfaces:**

```kotlin
data class ObdWitnessSample(
    val observedAtMs: Long,
    val stftPct: Double,
    val rpm: Double,
    val mapBar: Double,
    val petrolMs: Double,
    val fuel: String,
    val calibrationState: String,
    val skewMs: Long,
)

enum class ObdWitnessState { SUPPORTS, CONFLICTS, INSUFFICIENT, UNAVAILABLE }

data class ObdWitnessResult(
    val state: ObdWitnessState,
    val gasolineReferencePct: Double?,
    val gnvStftPct: Double?,
    val residualPp: Double?,
    val quality: Double,
    val gasolineSamples: Int,
    val gnvSamples: Int,
)
```

- [ ] **Step 1: Write RED for gasoline-relative residual**

```kotlin
@Test fun `GNV residual is relative to compatible gasoline STFT not zero`() {
    val engine = ObdWitnessEngine()
    repeat(5) { engine.observe(sample("PETROL", stft = 2.0 + it * 0.1, rpm = 2000.0, map = 0.55, petrol = 4.5, state = "A")) }
    repeat(5) { engine.observe(sample("GNV", stft = 9.8 + it * 0.1, rpm = 2010.0, map = 0.56, petrol = 4.5, state = "A")) }

    val result = engine.evaluate(rpm = 2000.0, mapBar = 0.55, petrolMs = 4.5, calibrationState = "A")

    assertEquals(2.2, result.gasolineReferencePct!!, 0.15)
    assertEquals(10.0, result.gnvStftPct!!, 0.15)
    assertEquals(7.8, result.residualPp!!, 0.25)
}
```

- [ ] **Step 2: Write RED for three-dimensional compatibility and state isolation**

Different calibration states must never share GNV evidence. Gasoline references may be reused only when RPM/MAP/Petrol Inj. compatibility passes. No temperature/load/throttle criterion exists.

- [ ] **Step 3: Implement robust median-based witness engine**

Use bounded observation history. Candidate matching uses RPM/MAP/Petrol Inj. windows only. Use median STFT rather than single instantaneous values. Quality derives only from temporal skew, support count and STFT dispersion; these are evidence-quality metrics, not extra operating variables.

- [ ] **Step 4: Integrate `ObdAssistManager` with `TelemetryStateStore.nearestFrame` and stable calibration-state provider**

The manager constructor receives callbacks rather than writer references:

```kotlin
nearestMp48Frame: (observedAtMs: Long, maxSkewMs: Long) -> JSONObject?,
calibrationStateProvider: () -> String,
```

Fuel comes from the matched MP48 frame. Reject pairs without RPM > 0, MAP > 0, Petrol Inj. > 0 or fuel `PETROL/GASOLINA/GNV/CNG`.

- [ ] **Step 5: Run focused tests and commit**

Run: `./gradlew testDebugUnitTest --tests '*ObdWitnessEngineTest*' --tests '*TelemetryNearestFrameTest*' --no-daemon`
Expected: PASS.
Commit: `feat(obd): build gasoline relative STFT witness`

---

### Task 6: Bind OBD witness to Blue confidence without touching correction math

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/blue/BlueWitnessConfidence.kt`
- Create: `app/src/test/java/com/omegas/prohub/blue/BlueWitnessConfidenceTest.kt`
- Modify: `app/src/main/java/com/omegas/prohub/calibration/BlueCalibrationCoordinator.kt`
- Modify: `app/src/main/java/com/omegas/prohub/service/BlueCalibrationAccess.kt`
- Modify: `app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt`

**Interfaces:**
- `BlueWitnessConfidence.assess(blueErrorPercent, baseQuality, obdResidualPp, obdQuality): BlueWitnessAssessment`.
- `SUPPORTS` iff both signals are outside their deadbands and have the same sign; `CONFLICTS` iff opposite signs; otherwise `INSUFFICIENT`.
- `SUPPORTS` effective confidence: `base + (1 - base) * 0.25 * obdQuality`, clamped to 1.0.
- `CONFLICTS`, `INSUFFICIENT`, `UNAVAILABLE`: no confidence boost.
- Correction multiplier, gain and target values remain byte-for-byte independent of OBD.

- [ ] **Step 1: Write RED confidence tests**

```kotlin
@Test fun `supporting OBD accelerates confidence but cannot change target`() {
    val result = BlueWitnessConfidence.assess(8.0, 0.60, 7.0, 0.80)
    assertEquals(ObdWitnessState.SUPPORTS, result.state)
    assertEquals(0.68, result.effectiveConfidence, 0.0001)
}

@Test fun `conflicting OBD never boosts confidence`() {
    val result = BlueWitnessConfidence.assess(8.0, 0.60, -7.0, 0.90)
    assertEquals(ObdWitnessState.CONFLICTS, result.state)
    assertEquals(0.60, result.effectiveConfidence, 0.0001)
}
```

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*BlueWitnessConfidenceTest*' --no-daemon`
Expected: FAIL because the adapter does not exist.

- [ ] **Step 3: Implement confidence adapter and coordinator projection**

Add witness metadata/effective confidence to state/proposal JSON, but do not feed OBD into `cngErrorLog`, `actuatorGain`, `correctionMultiplier`, Map K or Curve K target math.

- [ ] **Step 4: Add structural test proving OBD cannot import/call writer APIs**

```python
def test_obd_has_no_writer_dependency():
    for path in obd_production_files():
        text = read(path)
        assert 'KWriteManager' not in text
        assert 'KFactorManager' not in text
        assert 'startWrite(' not in text
        assert 'startBatchWrite(' not in text
```

- [ ] **Step 5: Run focused + FAST and commit**

Run: `./gradlew testDebugUnitTest --tests '*BlueWitnessConfidenceTest*' --tests '*ObdWitnessEngineTest*' --no-daemon && python3 -B tools/run_checks.py`
Expected: PASS.
Commit: `feat(blue): accelerate confidence with OBD witness`

---

### Task 7: Make the OBD screen explain the witness, not scanner noise

**Files:**
- Modify: `app/src/main/assets/ui/screens/obd.js`
- Modify: `app/src/main/assets/ui/index.html`
- Modify: `app/src/main/assets/ui/core/native-api.js`
- Modify: `app/src/main/assets/ui/app.js`
- Create: `tests/ui/blue-obd-witness.test.cjs`

**Interfaces:**
- OBD status JSON exposes: `connected`, `connectionStage`, `connectionErrorCode`, `stft`, `matchedMp48 {rpm,mapBar,petrolMs,fuel,skewMs}`, `witness {state, gasolineReferencePct, gnvStftPct, residualPp, quality}`.

- [ ] **Step 1: Write RED UI test**

```javascript
assert.match(obdHtml, /STFT/i);
assert.match(obdHtml, /RPM/i);
assert.match(obdHtml, /MAP/i);
assert.match(obdHtml, /PETROL INJ/i);
assert.match(obdHtml, /referência gasolina/i);
assert.doesNotMatch(obdHtml, /MAF|borboleta|temperatura|velocidade|LTFT/i);
```

- [ ] **Step 2: Run and verify RED**

Run: `node --test tests/ui/blue-obd-witness.test.cjs`
Expected: FAIL because the current screen is scanner-like and contains extra sensor panels.

- [ ] **Step 3: Simplify OBD UI**

Keep three concepts only: connection, live STFT+matched MP48, and accumulated gasoline/GNV witness. Connection panel must expose the exact failing stage. No scanner sensor table in the production OBD screen.

- [ ] **Step 4: Run UI test + FAST and commit**

Run: `node --test tests/ui/blue-obd-witness.test.cjs && python3 -B tools/run_checks.py`
Expected: PASS.
Commit: `refactor(ui): focus OBD screen on STFT witness`

---

### Task 8: Integrated regression, CI proof and physical-test handoff

**Files:**
- Modify: `specs/001-blue-runtime-convergence/tasks.md`
- Modify: `STATUS.md` only after exact-SHA proof.
- No production behavior changes in this task unless a failing integrated test reveals a defect; if it does, return to RED/root-cause workflow for that defect.

**Interfaces:**
- Final integrated behavior: OBD optional; connection bounded/stage-aware; STFT-only evidence; nearest MP48 RPM/MAP/Petrol Inj.; gasoline-relative residual by calibration state; witness can boost confidence only on agreement; no writer access.

- [ ] **Step 1: Run affected JVM suite**

Run: `./gradlew testDebugUnitTest --no-daemon --stacktrace`
Expected: PASS.

- [ ] **Step 2: Run FAST contracts**

Run: `python3 -B tools/run_checks.py`
Expected: PASS.

- [ ] **Step 3: Trigger canonical remote Blue CI on the exact integration SHA**

Required jobs: `FAST contracts` then `FULL JVM lint APK`.

- [ ] **Step 4: Poll until terminal**

Do not report `queued` or `in_progress` as completion. On failure, fetch the exact failed job log, classify root cause, add/strengthen the regression if needed, fix, commit a new SHA and repeat from Step 1.

- [ ] **Step 5: Verify artifact/evidence**

Confirm APK artifact exists for the same green SHA. Record SHA and job conclusions in `STATUS.md` and `specs/001-blue-runtime-convergence/tasks.md`.

- [ ] **Step 6: State physical limitation precisely**

Software proof may establish parser, matching, witness, writer isolation and Android build. It may not claim the user's real ELM327 connects successfully until tested on the actual multimedia/adapter/car. The runtime must make that physical test maximally diagnostic by exposing connection stage and error code.

## Plan self-review

- Spec coverage: connection reliability, STFT acquisition, temporal MP48 matching, RPM/MAP/Petrol Inj. exclusivity, gasoline-relative residual, calibration-state isolation, confidence acceleration, writer isolation, UI and exact-SHA CI are each mapped to a task.
- Placeholder scan: no TBD/TODO/"implement later" steps remain.
- Type consistency: `nearestFrame`, `ObdWitnessSample`, `ObdWitnessResult`, `ObdWitnessState` and confidence projection are defined before use.
- Scope audit: no generic scanner feature, temperature/load/throttle/MAF/speed/IAT/LTFT learning, automatic K write or unrelated refactor is included.
