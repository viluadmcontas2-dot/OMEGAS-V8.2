# Feature 002 — Blue UI Runtime Recovery

## Authority
- Issue: #17
- Work Unit: `docs/workunits/OMEGAS-BLUE-UI-001.md`
- Branch: `work/omegas-blue-causal-engine`
- Parent constitution: `.specify/memory/constitution.md`

## Problem
Physical validation in the vehicle proved that the previous software gate was incomplete: the shell/navigation could load while essential route content did not render. `Agora` and `OBD` were observed blank, `Ferramentas` was incomplete/broken, and Curve K batch editing was technically present but not didactic or semantically correct for touch operation.

A build/static-contract pass is therefore not sufficient evidence of a usable UI.

## User stories
1. As the operator, opening `Agora` always shows a meaningful dashboard shell immediately, before live telemetry arrives.
2. As the operator, opening `OBD` always shows connection/witness content immediately, even while disconnected.
3. As the operator, opening `Ferramentas` always shows its operational/diagnostic workspace.
4. As the operator, I can explicitly turn Curve K selection mode ON/OFF and immediately see every selected point.
5. As the operator, I can drag across Curve K points to select several without editing one-by-one.
6. As the operator, delta buttons adjust all selected points relatively, while `Definir valor` assigns the exact same absolute factor to all selected points.

## Functional requirements
- FR-201: Dashboard bootstrap must mount non-empty route content synchronously from local assets; native telemetry availability may update values but may not be required to create the UI.
- FR-202: OBD route activation must mount non-empty disconnected/connecting/connected UI from local assets; Bluetooth/ELM availability may update state but may not be required to create the UI.
- FR-203: Tools route activation must mount its diagnostic workspace and static actions without requiring science/session/log data to exist.
- FR-204: Essential route bootstrap failures must be logged and must surface a visible fallback instead of a silent blank route.
- FR-205: Runtime tests must execute the actual UI scripts/bootstrap against a DOM-like environment; source-string/grep assertions alone are insufficient for FR-201..204.
- FR-206: Curve K has explicit selection mode `OFF/ON` with visible state.
- FR-207: In selection mode, point touch toggles selection immediately; visual selected state is independent of whether a numeric proposal has been applied.
- FR-208: Selection mode supports drag/brush selection and exposes a visible selected-count.
- FR-209: Curve K relative controls `±0,01` and `±0,05` apply a delta to every selected point.
- FR-210: Curve K absolute action `Definir valor X` sets every selected point target to exactly X. It must never interpret X as a delta.
- FR-211: Selection and preview never write the ECU. Existing manual review, confirmation, writer, ACK and readback remain unchanged.
- FR-212: Do not redesign unrelated screens or alter MP48/OBD scientific authority as part of this incident.

## Acceptance criteria
- Actual bootstrap test proves Dashboard host receives meaningful content.
- Actual route test proves OBD host receives meaningful content while disconnected.
- Actual route test proves Tools diagnostics workspace receives meaningful content with empty native data.
- A forced bootstrap/module failure produces visible fallback and a logged diagnostic rather than blank content.
- Curve selection OFF/ON is visible and testable.
- Selecting a point changes its selected state before any factor edit.
- Dragging across several points selects them and updates the count.
- Given selected current factors `[1.00, 1.10, 1.30]`, `+0.05` targets become `[1.05, 1.15, 1.35]`.
- Given the same selected points, `Definir 1.20` targets become `[1.20, 1.20, 1.20]`.
- Existing write-safety tests remain green.
- Final exact SHA passes FAST → JVM/unit → lint → APK.
- Physical vehicle validation remains a separate final gate and cannot be inferred from CI.
