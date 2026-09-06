# OMEGAS-BLUE-UI-001 — Runtime UI recovery and Curve K interaction

- Issue: #17
- Branch authority: `work/omegas-blue-causal-engine`
- Repo authority: `AGENTS.md` + `.specify/memory/constitution.md` + `specs/001-blue-runtime-convergence/`
- Trigger: physical vehicle validation on 2026-09-06
- State: IN_PROGRESS

## Incident
The APK built from Blue passed the prior FAST/JVM/lint/APK pipeline but physical validation exposed a runtime UI regression. The previous verification was insufficient because it validated static contracts/buildability without proving actual UI bootstrap/render behavior.

## Confirmed symptoms
1. `Agora` shell is visible, but dashboard content is blank.
2. `OBD` route does not render useful runtime content.
3. `Ferramentas` is incomplete/broken at runtime.
4. Curve K multi-selection does not give immediate, unmistakable visual feedback.
5. Curve K lacks an explicit selection mode ON/OFF suitable for touch use.
6. Curve K absolute assignment semantics are ambiguous/wrong: `Definir 1,20` must set every selected point to exactly `1,20`; it must not add `+1,20`.

## Scope
Only recover runtime rendering and make Curve K batch editing didactic/correct. Do not reopen Blue architecture, MP48 protocol, OBD scientific model, writer safety, or unrelated screens.

## Required behavior
### Runtime bootstrap
- Dashboard/Agora must mount non-empty content on bootstrap even before native telemetry arrives.
- OBD must mount non-empty content on route activation even before adapter data arrives.
- Tools must mount its diagnostic workspace on route activation.
- UI bootstrap failure must be visible/logged; an essential route must never silently remain blank.

### Curve K
- Explicit `Seleção: OFF/ON` control.
- When OFF, normal point interaction remains single-point/editor behavior.
- When ON, touching a point toggles it immediately and visibly.
- Dragging while ON selects a continuous set of points.
- Selected points remain visibly distinct before any numeric adjustment.
- Selection count is always visible.
- `±0,01` and `±0,05` are relative deltas applied to every selected point.
- `Definir valor` is absolute assignment applied to every selected point.
- Preview/editing remains local only; ECU mutation remains review → human confirm → write → ACK → readback.

## TDD gates
1. RED: add a bootstrap/runtime test that executes the actual UI modules and proves Dashboard/OBD/Tools mount content. It must fail on the current broken behavior/compatibility condition.
2. RED: extend Curve K behavioral test for selection mode, immediate selected state, drag selection, counter, delta semantics and absolute assignment.
3. GREEN: minimal production fix.
4. Regression: existing UI tests + FAST.
5. Full: JVM/unit → lint → APK on exact final SHA.

## Repo-first closure
Before closing #17:
- update `specs/001-blue-runtime-convergence/tasks.md` with this Work Unit;
- update `STATUS.md` to Blue/current SHA and explicitly record physical validation limits;
- attach exact workflow run/SHA evidence to #17;
- do not mark physical validation complete until the corrected APK is tested in the vehicle.
