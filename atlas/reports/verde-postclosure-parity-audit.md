# OMEGAS Atlas — post-closure audit against OmegasVerde

Atlas base: `6fbca0e704272023b9c58ce40caca4b43f57ed70`  
Verde reviewed: `a4ff14001dff4019c5402f1a28ed33bdbdb22bff`  
Canonical ProgBase SHA-256: `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`

## Result

The Verde review produced useful product refinements, but **no new ProgBase fact supersedes the final Atlas closure**.

The audit found one critical scientific conflict in current Verde: its action oracle is shifted relative to the raw Delphi RTTI + wrapper bytes of the canonical EXE.

### Critical correction — action identities

Canonical Atlas proof:

- `ActionAutoMatchExecute @ 0x005189B4` → mode `0x08` → `02 24 04 08 32`
- `ActionResetPetrolExecute @ 0x005189C0` → mode `0x01` → `02 24 04 01 2B`
- `ActionResetGasExecute @ 0x005189CC` → mode `0x02` → `02 24 04 02 2C`
- `ActionResetAllExecute @ 0x005189D8` → mode `0x04` → `02 24 04 04 2E`
- `ActionAutoCalRifExecute @ 0x005187A0` is the separate Modify Map Refs path.

Proof: run `35809435511`, artifact `10729032358`, plus `atlas/proofs/behavior/action-host-mode-map.json`.

The current Verde fixture `tests/fixtures/progbase-autocal-action-map-v1.json` must **not** be imported into Atlas as native truth.

## Other Verde novelties

**75 ms cadence:** product/HMI choice. Native ProgBase uses `refresh_time_ms`, default 300 ms, configurable 200–2000 ms. See `scheduler-refresh-cadence.json`.

**Z1–Z4 / OK-FALTA-AGORA:** compatible presentation. Native proof already distinguishes `ACQUIRED_ZONES_PETROL/GAS` from `CurrentBand`. Synthetic sparse-zone scenarios remain visual-only evidence.

**Curve K backup/restore:** useful OMEGAS safety extension. Native ProgBase proof remains: Reset K Factor writes every `MUL_ACT[i] = 1.0` through the ECU-backed vector setter, then refreshes.

**Reset interlocks / pre-mutation backup:** justified product safety, especially because canonical Reset All `0x04` has a captured broad destructive effect. It is not itself native ProgBase behavior.

**Focus shell / larger plot / collapsed technical regions:** UX/product refinements only.

## Authority rule

Verde is valuable as a consumer, regression surface and lead generator. It is not a source of native truth.

When Verde and Atlas disagree about original ProgBase behavior:

**canonical EXE + raw Portmon/LOGNOVO + reproducible Atlas proof win.**


## Post-closure refinements

After the Verde review, Atlas added three refinements without reopening scientific closure:

- **Indexed `0x0165` semantics:** direct canonical DFM proof run `35814233455`, artifact `10731072232`, confirms the original `FileKeyName`/RowIndex identities for `VECT_AUTOCAL_U8_0/1/2`.
- **Acquisition topology:** the 18-element reference/counter grid is explicitly separated from the 4-element `ACQUIRED_ZONES_PETROL/GAS` state and from the separate `CurrentBand` render path.
- **Post-closure hard-stop:** run `35814342790` proved that stale recursive frontiers are terminated before research/reconcile/redispatch once the durable closure receipt is present.
