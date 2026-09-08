# OMEGAS-BLUE-UI-001 — Runtime UI recovery

- Issue: #17
- Branch authority: `work/omegas-blue-causal-engine`
- Trigger: physical vehicle validation on 2026-09-06
- State: IMPLEMENTED_VERIFIED

## Incident
The earlier Blue build passed static/build gates but the vehicle exposed blank Agora/OBD runtime content. The recovery replaced source-presence confidence with executable browser bootstrap evidence.

## Verified behavior
- Agora and OBD have non-empty local fallback states.
- Bootstrap failure is visible/diagnosable rather than a silent blank panel.
- Essential route layout does not depend on `:has()`.
- Packed asset identity/cache handling is auditable.
- Chrome runtime smoke mounts Agora, OBD and Tools from the packaged assets.

Curve K was split to #22 and Tools persistence to #21; their browser tests are part of the same FAST suite.

## Closure gates
1. RED reproduced empty essential hosts / incompatible runtime assumptions.
2. Minimal runtime fix restored useful fallbacks and compatible routing.
3. Real-browser regression passes on the recovered branch.
4. FAST → JVM/unit → lint is the pre-artifact gate.
5. APK generation is not part of #17 closure; it remains an explicit owner-authorized manual gate.

Vehicle validation of a future authorized APK remains separate from this software issue.
