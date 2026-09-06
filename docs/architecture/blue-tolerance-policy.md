# Blue tolerance policy — recovery decision

Issue: #19  
Epic: #18

## Decision
**Tolerances continue to exist internally, but they are not an owner calibration feature.**

A physical sample cannot require RPM/MAP/Petrol Inj. to be mathematically identical across every frame; therefore deterministic internal stability windows are necessary. What is not acceptable is allowing the normal user to choose `Muito rigoroso / Flexível` and thereby change which physical evidence becomes scientific truth.

The current V5/V6 object mixes four different domains in one mutable preference. Recovery separates them.

## A — hard internal validity / safety
Not owner-configurable.

- physical telemetry plausibility;
- `ENGINE_OFF`;
- `CUT-OFF` detection;
- fuel/calibration/session boundary;
- breaking telemetry gap / continuity loss;
- USB generation/session mismatch;
- confirmed write boundary / readback boundary.

`TRANSITION` is **not** in this rejection bucket: per confirmed physical behavior, it still burns gasoline and must be normalized to the gasoline evidence side. CUT-OFF remains distinct.

## B — automatic sample-quality windows
Remain internal and deterministic; no normal UI knob.

- minimum/desired frame budget;
- RPM center/oscillation;
- MAP center/oscillation;
- Petrol Inj. center/oscillation;
- warm-engine stabilization before evidence is admitted.

These rules decide whether a short observation window is representative. They do not decide the final correction. Final matching/error remains `BlueCausalEngine` authority.

## C — contextual physical diagnostics
Do not become matching dimensions or user tolerances.

### GNV pressure
Pressure variation can change gas delivery even at the same commanded condition. Therefore it is useful as a **sample-stability diagnostic** and may veto a clearly unstable GNV window when the sensor is valid. It must not be an RPM×MAP matching dimension and must not be a user slider. Missing/invalid pressure must be treated explicitly rather than silently as perfect stability.

### Water temperature
Useful only to avoid cold-engine enrichment/transients. Keep an automatic warm-engine gate; remove routine owner tuning.

### Gas temperature
Useful for diagnosis/audit. It is not a primary matching dimension in current Blue equivalence.

## D — legacy / duplicate policy to remove from LearningTolerancePolicy
These belong to removed models or another subsystem and must not remain inside owner-editable learning preferences:

- `toleratedSerialFailures`, `hardRecoveryFailures`, `hardRecoverySilenceMs` → transport/recovery policy;
- `historicalRpm*`, `historicalMapBar`, `historicalTemperatureC` → legacy historical matching path unless a current Blue consumer is explicitly proven;
- `referenceMaximumSpreadMs`, `directionConsensusMinimum`, `comparisonMaximumMadMs`, `comparisonMaximumGasTempSpanC`, `comparisonMaximumPressureSpanBar` → legacy/duplicate comparison policy unless Blue authority consumes them;
- `equivalenceDeadbandMs`, `equivalenceDeadbandPercent` → duplicate of Blue comparison policy and must not compete with `BlueCausalEngine`;
- `confidenceSampleTarget`, `provisionalVisits`, `acceptedVisits`, `confirmedVisits` → visits/count thresholds cannot be confidence authority under the Blue constitution.

## UI consequence
The normal Learning inspector should not contain a `Tolerâncias` editor.

Replace it with a read-only **Qualidade da coleta** explanation when useful:
- condição estável / instável;
- motivo objetivo if a window is rejected;
- frames used;
- freshness/gap;
- fuel state;
- optional diagnostic pressure/temperature note.

No `Muito rigoroso → Muito flexível` control in the normal cockpit flow.

## Scientific separation

```text
MP48 raw frames
   ↓
internal sample validity/stability (A+B, selected C)
   ↓
physical evidence with quality
   ↓
BlueCausalEngine RPM×MAP matching + Petrol Inj response
   ↓
measured error / proposal
```

Transport recovery and visit-count bookkeeping are outside this chain.

## Migration requirement
Existing saved `omegas_learning_v5` preferences may exist on installed devices. Recovery must migrate safely to fixed/internal defaults and must not let stale owner profiles continue affecting evidence invisibly after the UI control disappears.

## Tests required before implementation is considered complete
1. user-facing control model cannot alter scientific thresholds in normal runtime;
2. transport failure thresholds are independent of learning policy;
3. TRANSITION enters gasoline side; CUT-OFF never enters evidence;
4. RPM/MAP/Petrol stability still rejects genuinely unstable windows;
5. clearly unstable valid GNV pressure can be flagged without becoming a matching axis;
6. visits do not raise quality by count;
7. Blue error/deadband authority is unique.