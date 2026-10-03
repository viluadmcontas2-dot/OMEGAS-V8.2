# OMEGAS VERDE — Adaptive Reference Surface

**Status:** APPROVED BY OWNER  
**Approved:** 2026-09-14  
**Repository:** `viluadmcontas2-dot/OMEGAS-V8.2`  
**Branch:** `OmegasVerde`  
**Observed branch HEAD before this spec:** `d1b1350bff178d42525b26856c93928796481211`

## 1. Decision

The gasoline reference will no longer require the application to physically relearn every RPM × MAP point before it can produce a useful reference. The Verde architecture will use a hierarchical hybrid reference:

1. a mathematical prior surface available from cold start;
2. a real-car residual surface learned incrementally from gasoline observations;
3. adaptive interpolation over the residual surface;
4. explicit uncertainty and provenance;
5. real evidence progressively replacing prior authority without allowing predictions to become evidence.

This design extends the existing Verde RPM × MAP reference concept. It does not authorize automatic ECU writes.

## 2. Core model

Let `r = RPM`, `m = MAP` and `t = petrol injection time`.

The cold-start prior is:

`T0(r,m) = S_local * F2(r,m)`

where `F2` is the current empirical gasoline formula family and `S_local` is an optional bounded normalization learned from the current vehicle/session domain when justified by evidence.

Each real gasoline observation produces a residual:

`delta_i = Tinj_real_i - T0(r_i,m_i)`

The operational reference is:

`Tref(r,m) = T0(r,m) + delta_hat(r,m)`

where `delta_hat` is an adaptive robust interpolation of **real observed residuals only**.

The prior is a starting estimate, not a measurement and not universal truth.

## 3. Authority hierarchy

For any query point, the result must expose how much authority comes from each layer:

- prior formula;
- nearby real gasoline observations;
- interpolation of real residuals;
- uncertainty / distance from direct support;
- independent visit/session evidence.

As direct evidence accumulates, its authority rises and the prior contribution falls. A region may therefore move from `PRIOR_DOMINANT` to `HYBRID` to `EVIDENCE_DOMINANT` without changing the public query interface.

Predicted/interpolated values never increment sample count, visit count, support or confidence.

## 4. Spatial learning policy

A new gasoline observation updates only the residual field relevant to its physical neighborhood.

The update is continuous rather than nearest-cell-only. Existing bilinear/trilinear geometry and spatial kernels may be reused, but they are implementation details rather than scientific authority.

Confidence and propagation radius are separate concepts:

- weak evidence has low amplitude/authority even if represented smoothly;
- repeated independent evidence strengthens the local correction;
- strong local evidence should become more spatially specific rather than contaminating distant regions;
- disagreement increases uncertainty instead of being silently averaged away.

No editable acceptance radius is introduced as a user-facing scientific threshold.

## 5. Prior confidence

The prior has a spatial confidence field `C_prior(r,m)`.

Regions in which the empirical formula is historically reliable resist large corrections from one contradictory observation. Regions with known structural weakness yield authority faster to coherent real evidence.

This allows fast learning where knowledge is weak without making the entire surface unstable.

## 6. GNV use of the reference

For a GNV observation at the current operating condition:

`e = (PetrolInj_GNV - Tref(RPM,MAP)) / Tref(RPM,MAP)`

or its logarithmic equivalent where the downstream model requires additive composition.

The application does not need a gasoline observation at the exact same point during the same drive if the Adaptive Reference Surface has sufficient supported confidence there.

A poor GNV calibration is still useful evidence. It records the response of the engine/ECU under the **current confirmed calibration state**; it is not discarded merely because the error is large.

## 7. Four bounded responsibilities

### 7.1 `GasolinePriorSurface`

Immutable/versioned mathematical prior and its spatial confidence. It cannot learn from GNV and cannot claim direct observation.

### 7.2 `GasolineResidualSurface`

Stores and incrementally updates robust residual evidence from real gasoline observations. It owns real support, dispersion and independence metadata.

### 7.3 `AdaptiveReferenceSurface`

Combines prior + learned residual and returns value, uncertainty, provenance and support state through one authoritative query interface.

### 7.4 `GnvResponseSurface`

Stores GNV error under a confirmed calibration state/epoch. It does not redefine gasoline reference. After confirmed calibration changes it provides the before/after evidence used to learn actuator response.

## 8. Incremental state

The runtime should update sufficient statistics rather than replaying all historical frames for every observation. Persisted statistics must retain enough provenance to rebuild/audit results from raw evidence when needed.

At minimum, local evidence carries:

- effective weight/sample size;
- robust residual center;
- dispersion;
- independent visit/session identity;
- last update;
- directional consistency;
- source/provenance;
- direct-vs-interpolated distinction.

## 9. Invariants

- RPM × MAP remains the geometry of gasoline equivalence.
- RPM × Petrol Injection remains the downstream physical geometry used to address the Mapa K.
- Prior/prediction is never counted as observation.
- `gas_ms` is not the equivalence target.
- Auxiliary sensors may diagnose context but do not become mandatory reference dimensions without new evidence.
- No neural network or opaque ML is introduced by this design.
- No fixed universal gain from gasoline/GNV error to K is assumed.
- No automatic ECU write is authorized.
- Manual prepare/review/confirm/ACK/readback semantics remain intact.

## 10. Success criterion

The reference should be useful immediately from cold start, improve after the first real observations, and converge toward the individual vehicle's observed behavior without requiring exhaustive coverage of every point.

The system succeeds when additional real evidence monotonically improves calibration knowledge or increases uncertainty when observations conflict; it must never manufacture certainty by interpolating its own predictions.

## 11. Follow-on architecture

Owner requested, after approving this design, that the same evidence-first complexity be extended to Curva K global learning, Mapa K local learning and suggestion logic using the historical gasoline/GNV sessions and their multiple calibration states. That follow-on remains a separate architectural approval gate and is not implemented by this document.
