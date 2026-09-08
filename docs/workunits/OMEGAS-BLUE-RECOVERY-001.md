# Work Unit — OMEGAS-BLUE-RECOVERY-001

Status: RECOVERY_IMPLEMENTATION_COMPLETE  
Epic: #18  
Branch authority: `work/omegas-blue-causal-engine`  
Scientific authority: `.specify/memory/constitution.md` + `BlueCausalEngine`

## Human objective
Recover a coherent, responsive and didactic OMEGAS Blue without regenerating an APK during recovery.

## Completed workstreams
- #16 — single causal authority, Auto-Cal convergence, session/vault and safety convergence.
- #17 — Agora/OBD WebView bootstrap and compatibility.
- #19 — Learning comparisons, quality, TRANSITION and tolerance semantics.
- #20 — telemetry freshness/backpressure/background/overlay.
- #21 — Tools/log retention interaction stability.
- #22 — Curve K selection/batch UX and browser interaction.
- #23 — MP48 primary equivalence + read-only GNV STFT witness.

## Recovery evidence
- Browser runtime smoke mounts Agora, OBD and Tools and preserves Tools disclosure state.
- Curve K browser interaction proves Selection ON/OFF and absolute batch assignment.
- Telemetry RED reproduces a ~700 ms physically old frame being falsely marked fresh; fixed store preserves physical age and delivery delay.
- Overlay permission-return contract restores the requested native overlay.
- Latest-only and USB generation tests reject historical visual state.
- #16 reconciliation adds explicit causal math/session-policy tests and fixes duplicate Auto-Cal evidence ingestion.

## Release boundary
Software recovery completion means the branch may become `READY FOR APK GENERATION` **only after** canonical `OMEGAS Blue CI` succeeds on the exact remote HEAD containing this Work Unit. Normal push must not assemble/upload APK. Artifact generation requires explicit owner authorization.

Physical vehicle validation is a separate post-artifact gate; it does not keep software recovery issues artificially open and is never inferred from CI.

## Exit criteria
- [x] Recovery workstreams #16/#17/#19/#20/#21/#22/#23 implemented with focused regression evidence.
- [x] Browser/runtime essential route suite is part of FAST.
- [x] FAST/JVM/lint are the canonical pre-artifact gates.
- [x] `PROJECT.md`, `STATUS.md`, specs and workunits describe the current Blue truth.
- [x] No known recovery-scope P0/P1 defect remains intentionally open.
- [x] APK generation is isolated behind the owner-authorized manual gate.

Final ephemeral condition: read back remote HEAD and canonical CI after publishing this reconciliation. Only a `completed/success` result on that exact SHA permits the external READY declaration.
