# Feature 002 Plan — Blue UI Runtime Recovery

## Strategy
Recover actual WebView/runtime behavior first, then improve Curve K interaction. Keep scope constrained to Issue #17 and Work Unit `OMEGAS-BLUE-UI-001`.

## Workstream A — reproduce blank routes
1. Inspect the exact Blue HTML/script load order and WebView bootstrap.
2. Add a runtime-oriented UI test that loads the actual local UI modules and executes bootstrap/route activation, rather than only scanning source strings.
3. Make the current broken state fail for the observed reason: Dashboard/OBD/Tools do not mount meaningful content.
4. Capture the exact JavaScript/bootstrap exception or missing-module condition.

## Workstream B — resilient essential-route bootstrap
1. Fix the root bootstrap/module issue with the smallest production change.
2. Essential local route content must not depend on native telemetry to exist.
3. Add visible fallback content and console diagnostics for an essential screen module failure so a blank panel cannot silently pass again.
4. Preserve one scheduler and existing native bridge ownership.

## Workstream C — Curve K touch interaction
1. Add explicit selection-mode state and control.
2. Separate selection state from proposal/edit state.
3. Render selected point styling immediately on touch/drag.
4. Support toggle and brush/drag selection with visible selected count.
5. Preserve relative nudge buttons as delta operations across selection.
6. Make manual target entry an explicit absolute assignment across selection.
7. Keep preview local and preserve review/write/ACK/readback flow.

## Workstream D — verification/governance closure
1. Run new runtime UI behavior tests and existing UI regression suite.
2. Run FAST.
3. Run JVM/unit → lint → APK on exact final SHA.
4. Update `STATUS.md` from stale RED status to current Blue incident status/evidence.
5. Link final SHA/workflow evidence in Issue #17.
6. Leave Issue #17 open until the corrected APK is physically validated in the vehicle.

## Risks and controls
- Do not treat static string assertions as proof of runtime rendering.
- Do not rewrite MP48 protocol, OBD science or writer safety.
- Do not hide bootstrap failure with an empty-state that masks a real exception; fallback must expose the failure diagnostically.
- Do not allow absolute-factor assignment to route through delta math.
- Do not reintroduce per-point ECU writes while brushing/selecting.
