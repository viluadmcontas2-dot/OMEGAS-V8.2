# Feature 001 Tasks — Blue Runtime Convergence

- [x] T001 Add convergence contract covering Spec Kit, single-engine, RPM write gate, session retention/vault and Auto-Cal binding.
- [x] T002 Remove stale legacy-engine tests/assets rather than preserving deleted decision APIs.
- [x] T003 Add high-RPM write-safety regression while retaining legitimate service/USB/ECU/freshness gates.
- [x] T004 Remove reachable V7 equivalence compatibility authority and use Blue runtime contracts.
- [x] T005 Remove reachable Advisor/AutoMatch/Predictor correction authorities from runtime/UI.
- [x] T006 Bind Auto-Cal analysis to the real Blue proposal; no independent correction math.
- [x] T007 Implement `SessionRelevancePolicy` with PROBE/VALID/PROTECTED semantics.
- [x] T008 Preserve logical recording across USB segments; useful retention defaults to 30 and minimum is 20.
- [x] T009 Implement persisted session-vault promotion with private-spool fail-safe.
- [x] T010 Keep Learning measurement layers separate from Blue correction proposal.
- [x] T011 Include convergence/drift contracts in FAST CI.
- [x] T012 Enforce `FAST -> JVM/unit -> lint -> READY FOR APK GENERATION`; APK is a separate owner-authorized manual gate.

Exact-SHA readiness is an external ephemeral condition: canonical CI must be `completed/success` on current remote HEAD before READY is declared.
