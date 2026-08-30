# RED Blend — Causal MAP_K Evidence

Date: 2026-08-30
WorkUnit: `RED-BLEND-001`
Issue: `#11`
Branch: `work/red-v82-science-blend`

## Scope

This checkpoint establishes a deterministic fail-closed offline causal MAP_K laboratory. It does not claim a physical vehicle effect, production calibration sensitivity, P(improve), or automatic actionability.

The independent experimental unit is a confirmed manual MAP_K adjustment/batch, never the number of cell writes inside that batch.

## Governed intervention fixture

Privacy-safe fixture:

`tests/fixtures/science/k_history/confirmed_map_k_20260818.json`

- schema: `omegas-confirmed-map-k-adjustments-v1`
- source scope: `PRIVATE_OWNER_SNAPSHOT_DERIVED_AGGREGATED`
- source content SHA256: `8ce231682c5b49a513201759bbeccc3fd6fe5521067b88c048eae57769850b11`
- axis schema: `mp48-k-map-physical-axes-v1`
- axis lock SHA256: `0cc7273171fbe47a8d28235be00f1af49889d0934f6fb3c73fca35ccd2fee7c7`
- confirmed cell events: **133**
- independent manual interventions: **11**

The public fixture contains only privacy-safe aggregated intervention proof. Raw private adjustment identities are not repository content.

## Proof envelope

A cell history event is admissible only when all of the following are present and valid:

- manual adjustment identity;
- confirmed state;
- before and after values;
- ACK/readback with `readback == after`;
- finalized batch;
- final map hash;
- writable 12×12 coordinate;
- valid U8 MAP_K values.

Cells from one adjustment remain one intervention. The laboratory rejects inconsistent final hashes, duplicate conflicting cell coordinates, unconfirmed writes, and readback mismatch.

## TDD proof

### RED 1 — causal contract absent

- source SHA: `31a7e96367527b8ea8926da2b0f7389652505f8e`
- Actions run: `33328575477`
- inherited RED/science contracts: PASS before causal step
- expected failure: `ModuleNotFoundError: No module named 'lab.red_blend.causal_map_k'`

### GREEN 1 — fail-closed causal contract

- implementation SHA: `3cfe2d4b6b6e05f3894df183fbb223aca73619f1`
- causal MAP_K contract: PASS

### RED 2 — intervention fixture/effect lab absent

- source SHA: `89d66a194ab15b08b4674411ceb91dd7983bd5c5`
- Actions run: `33329013290`
- all earlier contracts including causal MAP_K: PASS
- expected failure: `ModuleNotFoundError: No module named 'lab.red_blend.causal_science'`

### GREEN 2 — fixture + synthetic intervention effects

- implementation SHA: `f308a3eff9b2394fbd26471c99d11a0c6b531025`
- Actions run: `33329084454`
- all workflow steps: PASS
- confirmed fixture: 133 cells / 11 interventions
- synthetic comparable improvement/worsening direction tests: PASS
- no production runtime changes

### RED 3 — governed real-outcome support audit absent

- source SHA: `aa9a4b79d3b09a16262b624a44bc351eb725a593`
- Actions run: `33329168375`
- all prior contracts passed until the new real causal audit contract
- expected missing capability: `audit_real_causal_support`

### GREEN 3 — real causal support audit

- implementation SHA: `fd368815043af225ba313a8e39c25a977b549295`
- Actions run: `33329259415`
- inherited RED fast contracts: PASS
- local/session/walk-forward/hybrid science: PASS
- causal MAP_K contract: PASS
- causal fixture/synthetic effects: PASS
- real causal support audit: PASS

## Real-corpus causal result

The governed episode fixture and the governed MAP_K intervention fixture do not currently contain a repository-proven bridge declaring that their timestamps share one comparable clock domain.

The laboratory therefore refuses to align intervention times with session episode times by timestamp magnitude, session order, or inference.

Deterministic result:

- `cell_event_count=133`
- `intervention_count=11`
- `comparable_interventions=0`
- `abstentions=11`
- `leakage_violations=0`
- `status=INSUFFICIENT_CAUSAL_OUTCOME_SUPPORT`
- reason: `UNPROVEN_COMMON_TIMEBASE` / `CLOCK_DOMAIN_UNPROVEN`
- `p_improve=null`
- `actionable=false`

This is a scientifically valid fail-closed outcome. It prevents temporal correlation or guessed clock alignment from being mislabeled as causality.

## Gate state

`G8_CAUSAL_MAP_K=ENGINEERING_REPLAY_PROVEN_REAL_OUTCOME_SUPPORT_INSUFFICIENT`

`CAUSAL_PROOF_ENVELOPE_PROVEN=true`

`INTERVENTION_UNIT_PROVEN=true`

`REAL_CAUSAL_OUTCOME_EFFECT_PROVEN=false`

`SENSITIVITY_PROVEN=false`

`P_IMPROVE_PROVEN=false`

`PRODUCTION_RUNTIME_CHANGED=false`

`ECU_AUTO_WRITE=false`

`MANUAL_CONFIRM_ACK_READBACK_INVARIANT_PRESERVED=true`

## Next scientific consequence

Sensitivity and P(improve) must remain null/fail-closed until a governed common-timebase bridge or another independently valid intervention/outcome linkage is available. Offline OOD/falsification, predictive risk/coverage, performance regression, and Android build verification may continue independently without promoting causal claims.
