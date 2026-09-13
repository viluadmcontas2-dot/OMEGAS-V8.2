# OMEGAS RED Final Engine Closure — Plan

Parent Issue: #30
Spec: `specs/004-red-final-engine-closure/spec.md`

## Architecture
The closure keeps one measured authority and one optional projection path. `BlueCausalEngine` owns measured gasoline reference/GNV deviation and causal actuator attribution. Gasoline evidence forms a permanent continuous RPM×MAP surface. Predictor consumes that same authority to estimate sparse-but-supported regions and converges toward observed evidence. Map K addressing remains current GNV RPM×Petrol Inj. Runtime calculation is revision-driven/cached outside the hot path. ECU writes remain manual.

## Issue decomposition

### #31 — Core scientific closure
Owns:
- removal of mandatory 30-second gasoline-reference gate;
- permanent gasoline surface semantics;
- physical/quality gates;
- 2D interpolation including diagonal/irregular neighborhoods;
- observed vs interpolated provenance;
- Map K current-GNV address;
- causal K gain separation from gasoline-reference validity.

Execution shape:
1. Re-read HEAD + #31 + this Spec Kit package.
2. Normalize existing unexecuted test commits into #31 context; do not treat them as RED evidence until run.
3. Write/complete failing tests for old gasoline, physical rejection, quality rejection and interpolation geometry.
4. Execute focused tests and capture genuine RED.
5. Implement minimal correction in existing owners.
6. Execute focused GREEN and broad core regressions.
7. Commit referencing `#31`; attach SHA/results to Issue.
8. Close only when all #31 acceptance criteria are evidenced remotely.

### #36 — Predictor + Learning UI
Depends on #31 semantics being stable enough to consume.
Owns:
- one reachable Predictor projection path;
- sparse supported prediction;
- prediction convergence and observation dominance;
- origin/uncertainty publication;
- operational Learning pane and compact grid cells;
- no automatic writer.

Execution shape:
1. Discover the single current runtime Predictor/advisor owner before editing.
2. If multiple owners are reachable, remove/disable duplication rather than adding another engine.
3. Write genuine RED for sparse prediction, convergence, observation dominance and outside-support abstention.
4. Write UI RED for required fields and forbidden engineering jargon.
5. Implement minimally using the #31 authority.
6. Run focused GREEN + Predictor/UI regression suites.
7. Commit referencing `#36`; attach SHA/results.

### #32 — Runtime/performance
Depends on the actual seams used by #31/#36.
Owns:
- no heavy surface rebuild per telemetry frame;
- no full Predictor rebuild per UI tick;
- coalesced/latest-revision computation;
- self-paced UI scheduling;
- non-blocking/coalesced evidence persistence.

Execution shape:
1. Trace actual hot path after #31/#36.
2. Add/adjust contract tests that fail if heavy work is wired into frame/render loops.
3. Implement caching/coalescence only where tests prove a violation.
4. Run scheduler/backpressure/soak regressions.
5. Commit referencing `#32` and attach evidence.

### #33 — OBD
Keep the independent GNV STFT learner/witness contract. Reconcile it against #31 so missing/conflicting OBD cannot erase permanent gasoline/MP48 measurement. Close only with its existing acceptance matrix.

### #34 — Consumption
Keep consumption separated from learning/calibration science. Close only with its existing distance/refill/uncertainty tests and evidence.

### #35 — Integrated release
Runs after software Issues are implementation-complete.
Owns:
- adversarial matrix across #31/#32/#33/#34/#36;
- exact-SHA FAST + JVM + lint;
- independent adversarial review;
- STATUS exact evidence;
- owner-authorized APK artifact and integrity/signing/package verification when tools exist.

Any product-code change after the final review invalidates the previous final-SHA gate and requires rerun.

### #25 — Physical vehicle gate
Runs only after #35 supplies the final APK. It must not require gasoline→GNV within 30 seconds. It verifies permanent gasoline reference, measured deviation, interpolation behavior, Learning usability and manual-write safety on the real vehicle.

## Dependency order
1. #31
2. #36
3. #32
4. #33 and #34 (may be verified independently once no conflicting core change remains)
5. #35
6. #25 physical

## Remote-first execution rules
- Before each write, read canonical remote HEAD.
- If HEAD diverged, reconcile before writing.
- No force-push.
- No parallel execution branch for this closure.
- No local checkout/worktree is authority; GitHub remote state wins.
- Every production commit cites the Issue it advances.
- Every Issue comment used as proof records exact SHA and exact test command/result.
- Tests added but never executed failing are not RED proof.
