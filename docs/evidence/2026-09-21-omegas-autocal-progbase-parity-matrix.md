# OMEGAS Verde — AutoCal parity matrix against ProgBase

**Issue:** #83  
**WorkUnit:** OMEGAS-WU-006  
**Remote baseline:** `7e6e37ae42527621e20fdada0e55b7c1f03c97d2`  
**Original evidence:** `docs/evidence/2026-09-21-progbase-autocal-byte-consumer-matrix.md`

## Result

The first hypothesis — “OMEGAS AGORA is tied to the slow AutoCal snapshot” — is **falsified by current source**. The current runtime already has a separate latest-only telemetry plane:

`ResponseDrivenEcuEngine -> TelemetryStateStore -> getPresentSnapshot -> app Store -> Scheduler fast -> AutoCal renderLiveCursor`.

That separation is a match worth preserving. The parity defects are narrower and more actionable.

| Behavior | Classification | What is actually different |
|---|---|---|
| RunPoint / AGORA XY | **MATCH** | Same live Petrol Inj. × MAP semantics, independently refreshed. OMEGAS renders at 200 ms rather than ProgBase's 75 ms presentation timer. |
| LEVELS RAW live strip | **MATCH — resolved** | Baseline diagnosis was WRONG freshness; RED `9df2a69...` proved stale projection 91 overriding live 126. The HMI now uses the fresh telemetry frame and fails closed when RAW is absent. |
| PetrolCurve / GasCurve identity | **MATCH** | Same common `PETR_INJ_TBP` X and petrol/gas RV vectors. |
| PetrolCurve / GasCurve freshness | **WRONG** | ProgBase recurrently renews slow reference vectors; OMEGAS full snapshot is event-driven and can remain unchanged indefinitely. |
| CurrentBand | **MISSING** | OMEGAS has live MAP and `MNFLD_PRESS_THD`, but does not locate/highlight the current threshold band. Its 18-band strip is maturity, not CurrentBand. |
| Petrol/Gas maturity | **WRONG/PARTIAL** | ProgBase observes both `NUM_BUF_UPD_PETR` and `NUM_BUF_UPD_GAS`; OMEGAS lightweight monitor probes only GAS. |
| ACQUIRED_ZONES | **WRONG freshness** | Values are projected correctly when a snapshot is current, but refresh is event-driven and gas-biased. |
| PollingPetrol/Gas literal shapes | **INTENTIONAL IMPROVEMENT** | Human-readable states replace red/lime polling lamps. |
| Enable/disable | **MATCH** | 0x014A manual action semantics preserved with stronger ACK/readback safety. |
| Reference skew gate | **INTENTIONAL IMPROVEMENT** | OMEGAS adds current-session and temporal-coherence safety. |
| 0x0165 index semantics | **INCONCLUSIVE** | Current OMEGAS names index 2 `MAX_AUTOMATCH`; original evidence has not yet proved that mapping. |
| Session ownership | **INCONCLUSIVE / #86** | Deliberately excluded from this slice. |

## 1. AGORA XY — preserve, do not rebuild

`ResponseDrivenEcuEngine` continuously polls `48 01 49` when the serial queue is empty and polls telemetry after each queued secondary work item when `telemetryAfter=true`. The accepted frame updates `TelemetryStateStore`. `HubJavascriptBridge.getPresentSnapshot()` reads that latest-only state without serial work. The UI scheduler executes every 200 ms; on AutoCal, `refreshFast()` patches the global telemetry store before the AutoCal fast hook calls `renderLiveCursor()`.

Therefore AGORA XY is not coupled to `NativeAutoCalMonitor.latestSnapshot`. A rewrite that routes it through AutoCal snapshot state would be a regression.

The 200 ms HMI cadence differs from ProgBase's 75 ms `TimerDati`, but this matrix does not label that difference a defect without render/performance evidence.

## 2. LEVELS RAW — confirmed defect, repaired by RED → GREEN

The Kotlin side is correct: `Mp48Protocol` decodes payload byte 13 into `level_raw`, and `AutoCalUiProjection.levelsRaw()` enforces valid, fresh, same-session telemetry.

The HMI then weakens this path. `AutoCalUxModel.livePoint()` reads Petrol Inj., MAP and RPM from the fast telemetry object, but reads LEVELS from `projection.levelsRaw`. The projection is refreshed by the AutoCal `context` hook (~2 s), while `renderLiveCursor()` is invoked by the `fast` hook (200 ms).

The baseline therefore mixed two clocks: XY/RPM could be current while LEVELS lagged roughly an order of magnitude. ProgBase consumes LEVELS byte 13 on the same live presentation path as the other live values.

**RED receipt:** commit `9df2a69b7f009fb7c8fb7301a778a27c4bd9fdf7`, fast run #115 failed exactly with `91 !== 126` and `91 !== null`.

**Minimal repair:** `AutoCalUxModel.livePoint()` now reads `live.level_raw ?? live.levelRaw` under the existing telemetry-validity/age gate. No serial, decoder, bridge, snapshot or scheduler behavior changed.

## 3. Curves — correct identity, wrong renewal model

OMEGAS correctly identifies:
- `PETR_INJ_TBP` = common X;
- `PETR_MNFLD_PRESS_RV` = PetrolCurve Y;
- `GAS_MNFLD_PRESS_RV` = GasCurve Y.

It also adds a useful current-session/temporal-coherence gate.

The mismatch is renewal. ProgBase evidence shows slow recurring reads, with the RV curve vectors around four seconds in the captured run. `NativeAutoCalMonitor`, by design and by test contract, says “snapshot completo só é lido por evento”. A full snapshot is requested at session bootstrap, native status/count change, GNV maturity, or manual action. If none occurs, `latestSnapshot` can remain unchanged even while the ECU's native reference evolves.

The existing test `test_monitor_uses_existing_health_tick_and_event_driven_snapshot` protects this old design, so the future fix must deliberately revise that contract rather than accidentally work around it.

**RED:** `AUTOCAL_REFERENCE_PERIODIC_REFRESH`.

## 4. CurrentBand — consumer missing, not merely styled differently

ProgBase locates the live MAP inside `MNFLD_PRESS_THD` and updates a dedicated `CurrentBand` area on the live RunPoint path.

OMEGAS possesses both inputs:
- live `load_bar`;
- `MNFLD_PRESS_THD` in the AutoCal snapshot.

But `AutoCalUxModel.bandStrip()` uses `NUM_BUF_UPD_GAS`, acquisition-zone flags and maturity/correlation events. It never consumes `MNFLD_PRESS_THD` and does not locate the current live MAP band. Choosing the first non-empty maturity segment is not equivalent.

**RED:** `AUTOCAL_CURRENT_BAND_PRESENTATION`.

The preferred repair is to project current-band semantics from authoritative native data and let JS render it. Do not invent a second scientific transform in the DOM.

## 5. Maturity and zones — gas-only lightweight observation is insufficient

The original recurring family includes both `NUM_BUF_UPD_PETR` and `NUM_BUF_UPD_GAS` at roughly two seconds in the capture.

The OMEGAS lightweight monitor calls only `probeMaturityCounters()` for `NUM_BUF_UPD_GAS`. Petrol counters and acquired-zone vectors are otherwise renewed only by a full snapshot. Existing tests explicitly enforce the gas-only probe.

This creates a plausible blind spot: gasoline-side activity/zone progress can change without triggering the event that would refresh the snapshot.

**RED:** `AUTOCAL_PETROL_MATURITY_REFRESH`.

## 6. Architecture chosen for the repair

Do **not** copy Delphi timer objects or add polling loops to screens.

The shortest robust architecture is a grouped incremental refresh policy on the **existing MP48 serial authority**:

- live telemetry remains the current independent latest-only path;
- a light ~2 s group refreshes acquisition counters/zones needed for operator progress;
- a slower ~4 s group refreshes reference vectors needed by PetrolCurve/GasCurve and threshold geometry;
- every secondary serial read keeps the existing `telemetryAfter=true` behavior so live telemetry is interleaved;
- publish/merge refreshed native fields in one AutoCal state authority;
- keep manual writes, ACK/readback, temporal-coherence checks and no-automatic-write guarantees unchanged.

A periodic full `READ_ONLY_FIELDS` snapshot every ~2 seconds is explicitly rejected: it would create unnecessary serial load and would be less faithful to the original grouped cadence.

## 7. Stop conditions before production surgery

Implementation must stop and replan if any RED demonstrates one of these:

1. real reference vectors do not evolve unless native status/count changes;
2. grouped periodic reads materially degrade live `48 01 49` freshness despite scheduler interleaving;
3. `MNFLD_PRESS_THD` cannot produce the same current-band semantics under real replay;
4. a current-session safety rule would be weakened by a proposed incremental merge.

Until a RED falsifies the current behavior, production remains untouched.
