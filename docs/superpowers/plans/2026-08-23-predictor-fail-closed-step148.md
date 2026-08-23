# Step 148 — Predictor fail-closed states

**Goal:** Refine the Step-147 typed boundary with explicit non-actionable reason codes and quarantine semantics, without inventing thresholds or creating a stateful second runtime.

**Execution authority:** Linear VIT-302.
**Starting SHA:** `38977f6aced81bc79f71f7f8ffcc64047a68058a`.

## Design

- Keep `PredictorContract` pure/stateless.
- Add typed input state supplied by the scientific Store, not inferred from UI/telemetry heuristics:
  - mutation state: `STABLE | MUTATING | RECONCILING | UNKNOWN`;
  - reference freshness: `CURRENT | STALE | UNKNOWN`;
  - physics knownness: `KNOWN | UNKNOWN`;
  - per-observation context state: `SUFFICIENT | INSUFFICIENT | UNKNOWN`;
  - per-observation support state: `SUFFICIENT | INSUFFICIENT | UNKNOWN`.
- Do not invent a numeric support threshold in Predictor; the upstream authority classifies support sufficiency.
- Split fail-closed reasons into the Phase-07 vocabulary: `IDENTITY_MISMATCH`, `GENERATION_STALE`, `GEOMETRY_UNKNOWN`, `REFERENCE_STALE`, `CONTEXT_INSUFFICIENT`, `MUTATION_QUARANTINE`, `PHYSICS_UNKNOWN`, `SUPPORT_INSUFFICIENT`, retaining precise source/revision/geometry mismatch reasons already frozen by Step 147.
- A quarantined/invalid evaluation emits zero new candidates.
- If a previous READY snapshot is supplied, retain it only as `PredictorDiagnosticSnapshot` (`revisionToken + candidates`) marked stale/diagnostic; never place old candidates back into the new actionable `candidates` list.
- Historical/mismatched evidence is never converted to `PredictorObservation` authority by this contract.

## TDD

RED first:
1. generation N evidence under calibration N+1 => `GENERATION_STALE`, zero candidates;
2. geometry absent => `GEOMETRY_UNKNOWN`;
3. stale/unknown reference => `REFERENCE_STALE`;
4. insufficient/unknown context => `CONTEXT_INSUFFICIENT`;
5. mutation/reconciling/unknown => `MUTATION_QUARANTINE`;
6. unknown physics => `PHYSICS_UNKNOWN`;
7. insufficient/unknown support => `SUPPORT_INSUFFICIENT`;
8. delayed epoch/session callback remains non-actionable;
9. previous READY snapshot during quarantine survives only in diagnostic stale projection;
10. stable/current/known/sufficient input stays READY and K*-driven.

## Verification

Compile and execute the exact remote `PredictorContract.kt` blob with focused deterministic/property probes. Static scan must remain free of USB/serial/writer/UI/Store/Router/Scheduler imports. Full Android Gradle evidence is carried only when a build fabric is actually available; GitHub Actions are not a fallback.
