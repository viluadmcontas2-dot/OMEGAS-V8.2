# Feature 001 Plan — Blue Runtime Convergence

## Architecture
Keep proven MP48 transport, K writers and readback protocol. Replace decision-layer compatibility with Blue-native domain contracts and one `BlueCausalEngine` authority. Session recording becomes logical-session + connection-segment based. Public storage is a promotion target, never the hot recording path.

## Workstreams

### A. Single-engine hard cut
- Introduce/complete Blue-native evidence/session/proposal contracts.
- Move runtime reconciliation callers directly to `BlueCausalEngine`.
- Remove `BlueEquivalenceCompatibility.kt`, V7 equivalence engine tests, AutoMatch stale tests/classes, and independent advisor/predictor correction paths.
- Strengthen static gate to scan reachable decision authority, not filenames only.

### B. RPM-independent write authorization
- Add regression at safety boundary with high RPM.
- Inspect Map K, Curve K, Auto-Cal, web bridges and UI for idle/1200 RPM thresholds.
- Delete all RPM-based write authorization while preserving real service/USB/fresh-telemetry/human-confirmation/ACK/readback checks.

### C. Session durability
- Add pure `SessionRelevancePolicy` and tests first.
- Change retention default to 30, configuration range 20..100.
- Keep logical session active across transient USB disconnect/reconnect; record segment boundaries.
- Prune PROBE independently; never let it evict VALID/PROTECTED.
- Protect calibration/readback sessions.

### D. Session vault
- Add persisted SAF tree URI setting and a vault promoter that copies a closed immutable session package after validation.
- Keep private spool until promotion succeeds; expose pending/failed promotion status.
- Add Android bridge to choose/revoke vault folder without making storage permission a recording prerequisite.

### E. Learn / proposal UX
- Remove suggestion-as-grid-layer semantics and prediction fallback presented as direct comparison.
- Expose Petrol, GNV, Desvio as evidence layers.
- Add one Blue proposal panel with reason when unavailable.
- Rewrite cell detail hierarchy for location → reference → observed → deviation → meaning → correction.

### F. Auto-Cal binding
- Bind bridge to live Blue comparison/proposal source.
- Remove placeholder `BLUE_ENGINE_PROPOSAL_NOT_BOUND_YET` and legacy draft APIs from reachable UI.
- Keep native ECU actions manual and protocol-exact.

### G. CI convergence
- Remove stale tests that compile deleted legacy engines.
- Add Spec Kit/drift/legacy/RPM/session contracts to FAST.
- Run FAST → full JVM/unit → lint → APK and publish exact-SHA evidence.

## Risk controls
- Do not rewrite MP48 protocol or serial scheduler.
- Do not weaken ACK/readback or manual confirmation.
- Session vault failure cannot delete private spool.
- Migration deletes dead authority only after callers are moved and tests prove the new path.
- Vehicle behavior/economy remains unproven until physical validation.
