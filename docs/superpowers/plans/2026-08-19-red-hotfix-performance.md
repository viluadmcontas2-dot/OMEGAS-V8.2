# OMEGAS V8.0 RED Hotfix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a small, auditable V8.0 stability hotfix that reduces serial/UI pressure, keeps long sessions bounded, removes the internal WebView float, improves the official overlay/dashboard readability, and preserves existing human-reviewed ECU write safety.

**Architecture:** Keep `ResponseDrivenEcuEngine` as the single MP48 physical/serial authority. Add only an admission/backpressure wrapper around the existing `Mp48SerialScheduler`; keep UI delivery latest-only and learning bounded; make any learning/predictor changes revision-driven and isolated. Do not import the V8.2 Adaptive runtime or create new polling/store/writer authorities.

**Tech Stack:** Android/Kotlin, WebView HTML/CSS/JavaScript, Python contract tests, Gradle/JUnit, GitHub Actions.

**Spec:** Canonical Notion context `OMEGAS_V8_0_RED` — https://app.notion.com/p/3c18ee52ac5481eb9d8ccb6c940f9f10

## Global Constraints

- Source baseline: `work/v8.2-clean@9cd9a6ac9960b26a752a316667a325b1fe184c75`.
- Work branch: `hotfix/v8.0-red-performance`.
- One MP48 acquisition/serial authority and one writer only.
- No second polling loop, scientific Store, writer, or historical visual queue.
- Visual delivery remains latest-only; hidden screens do not create secondary visual work.
- ECU writes remain human-reviewed with ACK/readback; hotfix never auto-writes.
- V8.2 Adaptive architecture is out of scope.
- UI changes are minimal and must preserve state ownership and failure visibility.
- FINAL PASS requires fresh audit + meta-audit; physical behavior not exercised is reported as unverified.

---

### Task 1: RED contract harness

**Files:**
- Create: `.github/workflows/verify-red-hotfix.yml`
- Create: `tests/test_red_hotfix_contract.py`

**Interfaces:**
- Consumes: repository source files on the RED branch.
- Produces: fast structural regression checks that fail on the untouched baseline and gate later source edits.

- [ ] **Step 1: Write failing tests**

Create tests that assert the desired RED behavior before production edits: `NativeRuntimeManager.serialScheduler()` must return an admission wrapper; `RuntimeBackpressurePolicy` and `Mp48BackpressureScheduler` must exist; WebView internal floating telemetry must not be referenced from `index.html`/`app.js`; the native overlay touch target must be at least 56dp and telemetry text at least 13sp; Dashboard hero must put Petrol Injection before RPM; expensive Tools payloads remain route-gated.

- [ ] **Step 2: Run the lightweight workflow and verify RED**

Run through GitHub Actions on `hotfix/v8.0-red-performance`:

```bash
python -m unittest tests.test_red_hotfix_contract -v
```

Expected: FAIL because the admission wrapper does not exist and overlay/dashboard requirements are not yet met.

### Task 2: Serial admission/backpressure

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/util/RuntimeBackpressurePolicy.kt`
- Create: `app/src/main/java/com/omegas/prohub/ecu/Mp48BackpressureScheduler.kt`
- Modify: `app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt`
- Test: `tests/test_red_hotfix_contract.py`

**Interfaces:**
- Consumes: `Mp48SerialScheduler`, `Mp48SerialUnit`, `Mp48WorkClass`, `UsbProtocolReply`.
- Produces: `Mp48BackpressureScheduler(delegate)` implementing the same scheduler interface; `NativeRuntimeManager.serialScheduler()` returns this wrapper.

- [ ] **Step 1: Keep RED assertion focused on observable architecture**

Required behavior: READ_ONLY work uses non-blocking bounded admission; MANUAL_WRITE/SAFETY use a separately reserved bounded-wait lane; wrapper delegates transactions/units to the existing engine and does not create transport threads or queues.

- [ ] **Step 2: Implement minimal wrapper**

Use `Semaphore` for two lanes and bounded counters/metrics only. Do not copy V8.2 `LearningMutationAuthority` dependencies; RED only adds admission control.

- [ ] **Step 3: Route every manager through the wrapper**

Change only `NativeRuntimeManager.serialScheduler()` and status metrics; keep `ResponseDrivenEcuEngine` as the physical engine.

- [ ] **Step 4: Verify GREEN**

Run `python -m unittest tests.test_red_hotfix_contract -v`; then run existing serial scheduler contract/behavior tests in the broader gate.

### Task 3: Long-session hot path

**Files:**
- Inspect/modify only if baseline still violates the contract: `TelemetryForegroundService.kt`, `MotorLearningMemory.kt`, `SignalLearningStore.kt`, `LearningMemoryBudget.kt`, `AdvisorRevisionGate.kt`.
- Test: existing hot-path/memory/advisor contract tests plus `tests/test_red_hotfix_contract.py` as needed.

**Interfaces:**
- Consumes: existing bounded learning pipeline and persistence APIs.
- Produces: no new runtime authority; only bounded/revision-driven behavior.

- [ ] **Step 1: Characterize current baseline**

Confirm automatic portable checkpoints are absent from first-telemetry/onDestroy paths; provenance lists are bounded and JSON/digest work is outside primary memory locks; Advisor refresh is revision-driven rather than per eligible frame.

- [ ] **Step 2: Change only confirmed gaps**

If a criterion already passes at baseline, do not rewrite it. If it fails, add a failing contract assertion first, then make the smallest change.

- [ ] **Step 3: Verify focused regression tests**

Run existing tests named for checkpoint hot path, learning memory budget, advisor revision budget, and backpressure.

### Task 4: Minimal UI performance/readability fixes

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/service/TelemetryOverlayController.kt`
- Modify: `app/src/main/assets/ui/screens/dashboard.js`
- Modify/delete reference only if present: `app/src/main/assets/ui/components/floating-telemetry.js`, `app/src/main/assets/ui/styles-floating-telemetry.css`, `app/src/main/assets/ui/index.html`, `app/src/main/assets/ui/app.js`
- Test: `tests/test_red_hotfix_contract.py`

**Interfaces:**
- Consumes: existing Store/latest telemetry and native overlay snapshot.
- Produces: one official observational overlay with >=56dp touch target and >=13sp details; Dashboard gives `Petrol Injection` primary visual hierarchy while preserving RPM/context.

- [ ] **Step 1: Verify internal float boot path**

If internal float is already unreferenced, do not add a new removal mechanism; delete or leave dead files according to minimal-risk diff. The acceptance criterion is that it cannot boot from the shipped UI.

- [ ] **Step 2: Enlarge official overlay**

Increase collapsed Ω control min width/height to 56dp and detail text to 13sp while keeping 250ms draw throttling and observational-only semantics.

- [ ] **Step 3: Promote Petrol Injection in Dashboard hero**

Make `PETROL INJECTION` the hero numeric value and keep RPM/fuel/MAP as compact context without introducing new polling.

- [ ] **Step 4: Verify route-aware work remains intact**

Tools sessions/logs are still requested only on the Tools route and visual refresh remains screen-aware.

### Task 5: Predictor/suggestions minimal backport decision

**Files:**
- Inspect: `AssistedCalibrationAdvisor.kt`, `PredictorSurface.kt`, `PredictorInterpolator.kt`, suggestion lifecycle projection.
- Modify only an isolated change with existing unit coverage and no new Adaptive/calibration-identity dependency.

**Interfaces:**
- Consumes: current observation/support data.
- Produces: no prediction-as-observation feedback and no direct writer authority.

- [ ] **Step 1: Compare RED baseline against Fast-to-Zero contract**

Confirm whether `IdealTarget` and operational step are entangled, whether prediction feeds evidence, and whether suggestion lifecycle is persistent.

- [ ] **Step 2: Apply only safe isolated correction**

If separation requires the V8.2 calibration identity/scientific registry stack, mark it `DEFERRED_SCOPE_GUARD` instead of importing that stack. Performance/stability takes precedence over algorithm breadth in this hotfix.

- [ ] **Step 3: Verify existing predictor/suggestion tests**

No change is promoted without focused tests; no automatic ECU write is introduced.

### Task 6: Broad verification, build and independent audit

**Files:**
- No production edits during audit run.
- Evidence recorded in Notion RED context and Linear RED project.

**Interfaces:**
- Consumes: frozen RED source SHA.
- Produces: audit verdict, meta-audit verdict, APK artifact only if build succeeds.

- [ ] **Step 1: Run broad remote checks**

At minimum: `python -m unittest discover -s tests`, `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug`, `./gradlew :app:assembleDebug` in the build workflow.

- [ ] **Step 2: Freeze audit epoch**

Record branch/SHA/tree-equivalent scope fingerprint, files changed, applicable contracts and coverage manifest.

- [ ] **Step 3: Independent read-only audit**

Re-open remote source, review changed surface and tests without normative source mutation. A finding triggers a remediation run and a new audit epoch.

- [ ] **Step 4: Meta-audit**

Use a distinct audit run ID; verify coverage, source freshness, authority chain, no dual writer, no omitted applicable contract and no false PASS from partial tests.

- [ ] **Step 5: Build APK**

Only after implementation verification; use the existing Android build workflow or equivalent remote CI and preserve SHA-256/build receipt with the artifact.
