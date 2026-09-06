# Work Unit — OMEGAS-BLUE-RECOVERY-001

Status: IN PROGRESS  
Epic: #18  
Branch authority: `work/omegas-blue-causal-engine`  
Physical reference: last stable RED behavior (`hotfix/v8.0-red-performance`)  
Scientific authority: `.specify/memory/constitution.md` + `BlueCausalEngine`

## Human objective
Stop patching isolated symptoms. Recover a coherent, responsive and didactic OMEGAS Blue that can be trusted in the car.

## Absolute execution rule
**No new APK is generated/published until this Work Unit completes triage + remediation + regression gates.**
Build success alone is not acceptance. Physical validation is a separate final gate.

## Child workstreams
- #17 `BLUE-UI-001` — Agora/OBD bootstrap and WebView compatibility.
- #19 `BLUE-LEARN-001` — measured deviation, evidence quality, semantics and tolerance convergence.
- #20 `BLUE-RUNTIME-002` — telemetry freshness, backpressure, background and native overlay.
- #21 `BLUE-TOOLS-001` — Tools/log retention interaction stability.
- #22 `BLUE-CURVE-001` — cockpit Curve K editing, selection and jank.

## Triage matrix
| Symptom | RED reference | BLUE evidence/cause | Classification | Owner-facing target | Issue |
|---|---|---|---|---|---|
| Agora blank | RED mounted useful runtime content | Dynamic host was made empty; essential CSS used `:has()`; old gate only inspected strings | P0 runtime | Always render a useful state, with explicit bootstrap failure instead of blank area | #17 |
| OBD blank | Route rendered operational content | Same empty-host/dynamic-bootstrap dependency as Agora | P0 runtime | OBD state always visible even disconnected | #17 |
| Desvio medido empty | RED had multiple comparison/prediction sources | `LearningUiSnapshotAssembler` forcibly emits `comparisons=[]` while Blue comparison authority lives in `BlueCalibrationCoordinator` | P0 data contract | Project **Blue** comparisons into Learning; do not restore legacy predictor authority | #19 |
| Quality stuck at 0 | RED/legacy UI exposed confidence | Blue store persists `quality`; grid projection reads only `confidence`, producing 0 | P0 schema | Show physical evidence quality correctly; visits remain secondary audit support | #19 |
| Tolerances confusing | RED exposed semantic tolerance controls | Blue still carries large V5/V6 tolerance policy into current analyzer | P1 architecture/UX | Classify each rule; remove owner tuning that changes scientific truth unless defensible | #19 |
| TRANSITION not learned | Physical truth: transition still burns gasoline | `MotorSampleAnalyzer` resets and rejects `Mp48Fuel.TRANSITION` | P0 science contract | Treat TRANSITION consistently as gasoline for learning; CUT-OFF remains invalid | #19 |
| Telemetry feels ~700 ms stale | RED had repeated latency incidents | UI scheduler is 200 ms but physical MP48, native delivery, bridge and redraw are separate clocks; exact bottleneck not yet proven | P0 performance | Measure each stage; fix actual bottleneck without blind purge/rate changes | #20 |
| Background degraded | RED had foreground/runtime stabilizations | ForegroundService exists; lifecycle/vendor battery behavior still requires audit | P1 runtime | Service owns acquisition with screen off; UI redraw is not required for learning | #20 |
| Floating overlay gone | Native overlay still exists | `TelemetryOverlayController` survives and restore is conditional on persisted enable + permission; access/permission path needs tracing | P1 UX/runtime | Restore explicit, discoverable overlay control and truthful status | #20 |
| Retention panel closes itself | RED tools similar but current physical bug reproduced | Tools rebuilds workspace with `innerHTML`; periodic refresh recreates `<details>` and loses `open` | P1 interaction | Preserve disclosure/focus/scroll; update data without destructive rerender | #21 |
| Curve selection ambiguous | RED interaction was laborious | Current Blue has Set + drag code but weak visual contract and physical behavior diverges | P0 cockpit UX | Explicit Selection ON/OFF, strong persistent selection, count/range summary | #22 |
| Curve absolute value ambiguous | Desired semantics confirmed by owner | Repository path intends absolute preview but physical build behaved ambiguously | P0 behavior | `Definir 1,20` makes every selected point exactly 1.20; nudges remain deltas | #22 |
| Curve scroll/jank | RED editing already costly | Rebuild-heavy SVG/list path and low-performance WebView are suspects; measure mutations/renders | P0 performance | Batch action <= one intentional render; no scroll-triggered work/re-read | #22 |

## Tolerance decision framework
Do not decide by name. Every existing threshold must land in exactly one bucket:

**A — hard internal truth/safety gate**  
Examples: telemetry plausibility, engine-off, CUT-OFF, calibration/session boundary, continuity loss. Not owner-configurable.

**B — automatic sampling-quality rule**  
Candidate: RPM/MAP/Petrol-Inj stability needed to form a representative physical observation. Internal and deterministic; normal UI does not offer `rigoroso/flexível` knobs.

**C — diagnostic context, not equivalence authority**  
Candidates to prove: GNV pressure and gas/water temperature variability. Keep observable if useful, but do not silently change the answer unless a causal scientific reason is demonstrated.

**D — obsolete/legacy**  
Anything that only supports removed predictor/advisor/visit-count-confidence behavior or duplicates Blue policy must be removed from runtime/UI after regression proof.

### Working hypothesis (NOT final until tests/source audit complete)
- Owner-facing tolerance profiles should disappear from normal use.
- Transport recovery thresholds must be separated from learning/science policy; changing a learning slider must never change serial recovery behavior.
- RPM/MAP/Petrol stability may remain as internal automatic sample quality.
- Visits are evidence/audit diversity, never confidence-by-count authority.
- Blue matching/correction remains owned by `BlueCausalEngine`, not by UI tolerance controls.

## Test strategy
1. Reproduce bug with behavior-level RED test.
2. Fix one root cause, not a visible symptom.
3. Run focused GREEN.
4. Run child regression set.
5. Only after all children: runtime/browser bootstrap + FAST + JVM + lint.
6. `assembleDebug` only at the final Work Unit gate, not during triage.
7. Physical vehicle validation follows; CI cannot certify it.

## Exit criteria
- [ ] #17 closed with runtime/browser proof.
- [ ] #19 closed with Blue comparison/quality pipeline coherent and tolerance policy simplified.
- [ ] #20 closed with measured telemetry budget and background/overlay path tested.
- [ ] #21 closed with stable Tools interactions.
- [ ] #22 closed with cockpit Curve UX behavior tests and render budget.
- [ ] `PROJECT.md`, `STATUS.md`, relevant specs/tasks describe current Blue truth.
- [ ] No known P0/P1 regression remains open.
- [ ] Canonical CI green on exact final SHA.
- [ ] Only then produce APK for physical validation.
