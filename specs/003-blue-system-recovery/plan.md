# Plan 003 — Blue System Recovery

## Execution order

### Phase 0 — Authority and triage
1. Keep #18 as recovery epic and child issues scoped.
2. Compare relevant RED behavior against Blue without importing RED math blindly.
3. Record root cause before production modification.
4. Keep APK generation blocked.

### Phase 1 — Scientific/data contract (#19)
1. RED: Blue comparisons exist but Learning UI projection loses them.
2. GREEN: expose a single read-only Blue comparison projection to Learning.
3. RED: `quality` survives store -> grid -> model; visits do not substitute for quality.
4. GREEN: normalize evidence-quality schema end-to-end.
5. RED: TRANSITION is gasoline for collection/fuel boundary; CUT-OFF is invalid.
6. Audit all tolerance consumers and remove cross-domain coupling (notably learning tolerance changing serial recovery).
7. Replace owner-facing tolerance profiles with automatic/internal policy unless a specific control survives the classification matrix.

### Phase 2 — Runtime/bootstrap (#17)
1. Keep existing runtime RED tests.
2. Add/finish actual browser bootstrap smoke.
3. Provide static useful fallbacks for Agora/OBD.
4. Remove essential `:has()` dependency.
5. Add explicit bootstrap failure surface and asset/build identity.
6. Verify route navigation/mounting in browser harness.

### Phase 3 — Telemetry/background/overlay (#20)
1. Measure MP48 `lastIntervalMs`, USB transaction elapsed, native delivery queue delay, store age, bridge revision and redraw cadence.
2. Reproduce ~700 ms at the layer that actually owns it; do not tune UI polling blindly.
3. Verify latest-only visual delivery and bounded learning buffer.
4. Audit serial purge policy; only purge at protocol/session recovery boundaries where necessary.
5. Verify foreground lifecycle/wakelock/reconnect and overlay restore/permission/enable flow.
6. Remove any overlay dependency on cut OBD-map legacy data.

### Phase 4 — Tools (#21)
1. RED browser test: details stays open across refresh.
2. RED: edited settings retain focus/value across log refresh.
3. Refactor render into stable shell + incremental metric/log updates.
4. Clarify `telemetryEveryMs` as log-recording cadence, not physical acquisition rate.

### Phase 5 — Curve K (#22)
1. Browser interaction harness with 30 synthetic curve points.
2. RED for Selection ON/OFF, strong highlight, drag, deselect, count/range.
3. RED for delta vs absolute assignment on heterogeneous values.
4. Measure render/mutation count; prevent rerender during scroll/pointer move unless selection actually changes.
5. Redesign review as compact batch summary, not 30-row scroll dependency.
6. Keep ECU write path unchanged: review -> writer -> ACK -> readback.

### Phase 6 — Convergence
1. Re-run all focused behavior suites.
2. Run browser runtime smoke for all essential routes.
3. Run FAST then JVM/unit then lint on exact head.
4. Update `PROJECT.md`, `STATUS.md`, specs/tasks and incident receipts to current truth.
5. Resolve all P0/P1 child issues.
6. Only then run `assembleDebug` and produce a single candidate APK for physical validation.

## Failure handling
- One hypothesis at a time.
- After a failed fix, inspect new evidence before changing another variable.
- Three failed fixes in one subsystem => stop and re-evaluate architecture.
- Never weaken a valid safety/science contract to make CI green.

## Definition of done
See `spec.md` acceptance + Work Unit exit criteria. Physical car validation is the final human/environment gate and is never inferred from CI.