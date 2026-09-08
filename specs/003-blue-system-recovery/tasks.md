# Tasks 003 — Blue System Recovery

## Governance / triage
- [x] T001 Create recovery epic #18 and scoped child issues.
- [x] T002 Create umbrella Work Unit and Spec 003.
- [x] T003 Record root causes for blank UI, missing comparison, zero quality, Tools reset and telemetry freshness.
- [x] T004 Reconcile `PROJECT.md`, `STATUS.md`, Specs and Work Units to current Blue truth.

## #19 Learning / science
- [x] T100 Preserve Blue comparisons through Learning projection.
- [x] T101 Keep Blue as the single comparison authority.
- [x] T102 Preserve evidence `quality`; visits remain independent audit metadata.
- [x] T103 Normalize quality schema end-to-end.
- [x] T104 Treat TRANSITION as gasoline and CUT-OFF as invalid.
- [x] T105 Reconcile fuel-boundary semantics.
- [x] T106 Inventory tolerance consumers.
- [x] T107 Classify tolerance rules as truth/safety, automatic quality, diagnostic context or legacy.
- [x] T108 Separate transport recovery policy from learning tolerance.
- [x] T109 Remove owner-facing tolerance profiles from normal scientific authority.
- [x] T110 Keep support/samples/visits/quality meanings distinct in Learning UI.

## #17 WebView runtime
- [x] T200 RED blank essential hosts / unsupported essential-selector dependency.
- [x] T201 Real-browser bootstrap smoke for Agora, OBD and Tools.
- [x] T202 Useful static fallback states.
- [x] T203 Essential layout without `:has()` dependency.
- [x] T204 Visible bootstrap failure + asset/build identity.
- [x] T205 Route mount/navigation behavior verified.

## #20 Telemetry / background / overlay
- [x] T300 Expose MP48 response/interval timing.
- [x] T301 Expose native delivery queue/processing timing.
- [x] T302 Preserve physical store age separately from receipt/delivery age.
- [x] T303 RED a ~700 ms delayed physical frame being falsely reported fresh; fix at store boundary.
- [x] T304 Audit purge policy; normal telemetry/AutoCal reads remain `purgeBefore=false`.
- [x] T305 Verify latest-only delivery, bounded hot learning buffer and stale-generation rejection.
- [x] T306 Verify ForegroundService/wakelock/reconnect/battery-policy contracts.
- [x] T307 Restore overlay after permission return without recreating legacy overlay architecture.

## #21 Tools
- [x] T400 Browser regression: retention `<details>` remains open across refresh.
- [x] T401 Settings interaction is not destroyed by live refresh.
- [x] T402 Preserve stable DOM state instead of destructive settings rerender.
- [x] T403 Keep diagnostic recording cadence distinct from physical MP48 acquisition cadence.

## #22 Curve K
- [x] T500 Real-browser interaction harness.
- [x] T501 Explicit Selection ON/OFF.
- [x] T502 Strong selection state + count/range + clear/deselect.
- [x] T503 Drag multi-select.
- [x] T504 Relative nudge semantics across heterogeneous factors.
- [x] T505 Absolute assignment sets all selected factors exactly to target.
- [x] T506 Selection/batch path avoids ECU reread and unnecessary render churn.
- [x] T507 Compact batch review UX.

## #23 OBD witness integration
- [x] T600 MP48 remains primary RPM×MAP gasoline↔GNV comparison.
- [x] T601 GNV STFT works without gasoline OBD baseline.
- [x] T602 LTFT is absent from correction math.
- [x] T603 Same-region witness can boost confidence; conflict cannot.
- [x] T604 Map K addressing uses current GNV RPM × current GNV Petrol Inj.
- [x] T605 OBD has no writer path.

## #16 convergence
- [x] T700 Single causal authority and no reachable browser Predictor/Advisor/AutoMatch authority.
- [x] T701 Auto-Cal analysis delegates to Blue proposal and imported evidence enters Blue once.
- [x] T702 Causal log-ratio/gain and independent curve/map revision behavior have direct JVM coverage.
- [x] T703 High RPM is not a calibration write gate.
- [x] T704 Sessions use PROBE/VALID/PROTECTED, default useful retention 30/minimum 20 and fail-safe vault promotion.

## Convergence
- [x] T900 Recovery-scope P0/P1 implementations have focused GREEN evidence.
- [x] T901 Browser/runtime essential route suite is part of FAST.
- [x] T902 Canonical FAST gate defined for exact SHA.
- [x] T903 Canonical JVM/unit gate defined for exact SHA.
- [x] T904 Canonical lint gate defined for exact SHA.
- [x] T905 Repo governance/status/spec/workunits reconciled.
- [x] T906 APK generation isolated behind explicit owner-authorized manual workflow dispatch.
- [ ] T907 Physical vehicle validation of a future authorized APK; intentionally post-artifact and not a prerequisite for software READY.

Final ephemeral gate: after publishing this reconciliation, canonical `OMEGAS Blue CI` must be `completed/success` on the exact remote HEAD. Then, and only then, declare `READY FOR APK GENERATION` and stop without generating an APK.
