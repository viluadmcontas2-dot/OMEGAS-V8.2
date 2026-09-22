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
| LEVELS RAW routing | **INTENTIONAL IMPROVEMENT** | LEVELS is global MP48 telemetry owned by Dashboard/AGORA. AutoCal projection and cockpit intentionally do not consume it. |
| PetrolCurve / GasCurve identity | **MATCH** | Same common `PETR_INJ_TBP` X and petrol/gas RV vectors. |
| PetrolCurve / GasCurve freshness | **WRONG** | ProgBase recurrently renews slow reference vectors; OMEGAS full snapshot is event-driven and can remain unchanged indefinitely. |
| CurrentBand | **MATCH — resolved** | RED `b05705e...` proved the consumer/layer missing. The cockpit now selects the original MAP threshold interval from physical `MNFLD_PRESS_THD` and renders a dedicated horizontal live band, separate from maturity. |
| Petrol/Gas maturity | **MATCH — acquisition refresh resolved** | Grouped ~2 s refresh now reads both `NUM_BUF_UPD_PETR` and `NUM_BUF_UPD_GAS`; richer deduplicated maturity events remain an intentional improvement. |
| ACQUIRED_ZONES | **MATCH — freshness resolved** | Petrol and GNV zone vectors are refreshed together in the grouped acquisition path and merged into the current native snapshot. |
| PollingPetrol/Gas literal shapes | **INTENTIONAL IMPROVEMENT** | Human-readable states replace red/lime polling lamps. |
| Enable/disable | **MATCH** | 0x014A manual action semantics preserved with stronger ACK/readback safety. |
| Reference skew gate | **INTENTIONAL IMPROVEMENT** | OMEGAS adds current-session and temporal-coherence safety. |
| 0x0165 index semantics | **MATCH — original DFM proven** | `VECT_AUTOCAL_U8_2.FileKeyName=MaxAutomatch`; index 1 is `!AUTOCAL_IDLE_MIN_BUF_UPD_PETR_THD`. Lognovo contains the corresponding indexed reads. |
| Session ownership | **INCONCLUSIVE / #86** | Deliberately excluded from this slice. |

## 1. AGORA XY — preserve, do not rebuild

`ResponseDrivenEcuEngine` continuously polls `48 01 49` when the serial queue is empty and polls telemetry after each queued secondary work item when `telemetryAfter=true`. The accepted frame updates `TelemetryStateStore`. `HubJavascriptBridge.getPresentSnapshot()` reads that latest-only state without serial work. The UI scheduler executes every 200 ms; on AutoCal, `refreshFast()` patches the global telemetry store before the AutoCal fast hook calls `renderLiveCursor()`.

Therefore AGORA XY is not coupled to `NativeAutoCalMonitor.latestSnapshot`. A rewrite that routes it through AutoCal snapshot state would be a regression.

The 200 ms HMI cadence differs from ProgBase's 75 ms `TimerDati`, but this matrix does not label that difference a defect without render/performance evidence.

## 2. LEVELS RAW — Dashboard/AGORA ownership; outside AutoCal

MP48 payload byte 13 remains decoded as `level_raw` in global telemetry. Product ownership is explicit: **Dashboard/AGORA presents LEVELS RAW; AutoCal does not consume, project or display it.**

This is an intentional product boundary:
- LEVELS is the GNV cylinder/sensor quantity signal for the operator;
- it does not participate in PetrolCurve/GasCurve, CurrentBand, acquisition maturity, AutoMatch, equivalence or AutoCal writes;
- `AutoCalUiProjection` has no LEVELS field and no global telemetry dependency;
- `AutoCalJavascriptBridge.getUiProjection()` does not pull `TelemetryStateStore.liveJson()`;
- the AutoCal AGORA rail is restricted to RPM, Petrol Injection and MAP.

The raw decode remains available to Dashboard, and no conversion to percentage/litres/m³ is authorized.

## 3. Curves — correct identity, wrong renewal model

OMEGAS correctly identifies:
- `PETR_INJ_TBP` = common X;
- `PETR_MNFLD_PRESS_RV` = PetrolCurve Y;
- `GAS_MNFLD_PRESS_RV` = GasCurve Y.

It also adds a useful current-session/temporal-coherence gate.

The mismatch is renewal. ProgBase evidence shows slow recurring reads, with the RV curve vectors around four seconds in the captured run. `NativeAutoCalMonitor`, by design and by test contract, says “snapshot completo só é lido por evento”. A full snapshot is requested at session bootstrap, native status/count change, GNV maturity, or manual action. If none occurs, `latestSnapshot` can remain unchanged even while the ECU's native reference evolves.

The existing test `test_monitor_uses_existing_health_tick_and_event_driven_snapshot` protects this old design, so the future fix must deliberately revise that contract rather than accidentally work around it.

**RED:** `AUTOCAL_REFERENCE_PERIODIC_REFRESH`.

## 4. CurrentBand — confirmed missing, repaired by RED → GREEN candidate

ProgBase locates the live MAP inside `MNFLD_PRESS_THD` and updates a dedicated `CurrentBand` horizontal area on the live RunPoint path.

The RED at `b05705e772835ebf0dec7206c3dffb1cbd35026e` failed in all three expected dimensions: no `currentBand` consumer, no boundary rule, and no dedicated horizontal layer.

The recovered helper `0x51A614` is more specific than a generic nearest-bin rule: the valid global domain excludes the first and last threshold, and band `i` is selected when `threshold[i] < live MAP <= threshold[i+1]`. Internal equality therefore belongs to the immediately previous band.

The fix at `38ccb9ee38d833b911687ab1e1c5e87b23bf6959` uses the already-physical `MNFLD_PRESS_THD` values plus the fast live MAP. It adds a dedicated SVG horizontal band updated by `renderLiveCursor()`; the 18 maturity regions remain untouched and keep their separate meaning. No unit conversion, serial read, timer or ECU write was added.

**GREEN:** pending after evidence reconciliation.

## 5. Maturity and zones — confirmed defect, grouped acquisition repair applied

The original recurring family includes both `NUM_BUF_UPD_PETR` and `NUM_BUF_UPD_GAS` at roughly two seconds in the capture, together with recurring acquired-zone reads.

The RED at `0dbeed2feb51263efc96374290134e345acff03a` required an explicit grouped acquisition cadence and failed because the planner/path did not yet exist.

The repair at `714725f2e7417a847c19a94e4ddb70096b0af2e3` adds a pure refresh planner and a shared service cadence, while keeping `NativeAutoCalMonitor` free of its own thread/timer. The grouped acquisition path reads `NUM_BUF_UPD_PETR`, `NUM_BUF_UPD_GAS`, `ACQUIRED_ZONES_PETROL` and `ACQUIRED_ZONES_GAS` through the existing `Mp48SerialScheduler`, then merges only those operational fields into the current native snapshot.

This removes the gasoline-side blind spot without converting the whole AutoCal snapshot into a 2 s full read. The remaining parity RED is the slower reference family (`PETR_INJ_TBP`, `MNFLD_PRESS_THD`, petrol/gas RV vectors).

### 5.1 0x0165 semantic debt closed

The original ProgBase DFM binds `VECT_AUTOCAL_U8_2` directly to `FileKeyName=MaxAutomatch` and `VECT_AUTOCAL_U8_1` to `FileKeyName=!AUTOCAL_IDLE_MIN_BUF_UPD_PETR_THD`. Lognovo independently contains `0A 65 01 01 71` and `0A 65 01 02 72` three times each. The old `INCONCLUSIVE` label is therefore stale; OMEGAS index 2 → `MAX_AUTOMATCH` is a match to the original artifact.

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
