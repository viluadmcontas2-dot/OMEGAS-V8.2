# OMEGAS Verde SIL — Scientific Results Ledger

**Repository:** `viluadmcontas2-dot/OMEGAS-V8.2`  
**Continuity branch:** `work/omegas-verde-sil`  
**Scientific code/data baseline before this ledger:** `4b8e4e423173fa21f6f6022be9076cfb98f73e11`  
**Base production branch:** `OmegasVerde`  
**Updated:** 2026-09-19

## 1. Scope and decision rule

This ledger consolidates the headless SIL evidence that was previously spread across AgentRed issues and receipts.

Operational thresholds:
- target correction: 0%;
- preferred band: `abs(correction) <= 3.5%`;
- practical acceptable band: `abs(correction) <= 4.0%`.

Priority order:
1. robustness;
2. cross-session consistency;
3. fast learning;
4. reduction of large transient errors;
5. coverage;
6. simplicity;
7. only then marginal mean-error gains.

A result is not promoted from mean error alone. Leave-one-session-out / temporal causality, tails, transients, frame loss/lag and coverage matter.

Statuses:
- **PROMOTE** — evidence supports making it part of the candidate;
- **KEEP_AS_LAYER** — useful signal, but not a primary mechanism;
- **REJECT** — tested formulation does not justify its complexity / performs worse;
- **INCONCLUSIVE** — insufficient or failed evidence.

## 2. Corpus and SIL provenance

Canonical source cache:
`C:\Users\hugov\AppData\Local\AgentRed\cache\omegas-sil-v2\canonical_frames.csv.gz`

Corpus census already established:
- 46 ZIP archives found under `G:\Meu Drive\OMEGAS`;
- 21 archives with telemetry usable by the principal parser;
- 14 canonical sessions after exact deduplication;
- 135,094 canonical/unique frames.

LOGNOVO parity:
- source: `PortmonLOGNOVO (1)(2).zip`;
- 39,515 total reconstructed transactions;
- 20,450 valid telemetry transactions;
- 2 invalid responses;
- real SIL engine consumed 20,450 / 20,450 frames and emitted 20,450 callbacks;
- 6,778 accepted samples;
- 2,400 learning registrations/events;
- 143 regions;
- 494 comparisons.

The authoritative SIL chain remains:

`recorded MP48 -> RecordedMp48Transport -> real ResponseDrivenEcuEngine -> real Mp48Protocol -> real MotorSampleAnalyzer -> real MotorLearningMemory`

Python is corpus/orchestration only and is not a second production algorithm.

## 3. Current production-learning semantics

The current Verde memory already performs the core bridge in production classes:

1. PETROL samples create local gasoline reference regions/surface.
2. CNG samples query the gasoline reference surface.
3. Error is computed as:
   `(petrol_on_cng_ms - petrol_target_ms) / petrol_target_ms`.
4. Positive error means increase CNG delivery; negative error means decrease CNG delivery.
5. The correction address is the current CNG coordinate:
   `RPM_CNG x petrol_ms_CNG`.
6. Evidence is projected to K cells; automatic writing remains disabled and human confirmation is required.

Therefore the scientific problem is **not** to invent a second complete GNV learner. It is to improve the confidence, temporal semantics and transfer speed of the existing gasoline-reference -> CNG-error -> K-address chain.

## 4. External semantic evidence

### AEB MP48

Official AEB MP48 product documentation states that, while running on gas, MP48 uses **petrol injection time** together with gas pressure/temperature to calculate the equivalent amount of gas:
- https://www.aeb.it/en/product/injections-mp48/
- https://www.aeb.it/prodotto/iniezioni-mp48/

This is direct evidence that petrol injection time remains a live input to MP48 during gas operation; it is not merely an old gasoline-only value.

### AEB sequential-gas calibration manual

AEB software manual:
https://lpgautosupplies.co.uk/wp-content/uploads/2021/03/AEB-SOFTWARE-MANUAL.pdf

The documented non-OBD carburation test:
- hold the operating point steady;
- switch PETROL -> GAS;
- observe PETROL injection time during GAS;
- if PETROL injection time increases, increase K;
- if PETROL injection time decreases, decrease K;
- repeat/check PETROL/GAS changes at constant speed.

The same manual explicitly warns that transient acceleration/deceleration makes direct PETROL/GAS comparison unreliable in those map areas.

This matches the direction already observed in the corpus and is compatible with a non-OBD calibration path.

## 5. Core gasoline/reference baseline

### AgentRed #560 — deadtime-aware fast gain sweep
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/560  
Artifact manifest: https://github.com/viluadmcontas2-dot/AgentRed/blob/27a191a5fddaae65e409ce11d1e8aeedc508bd03/manifest.json

Baseline near deadtime 0:
- mean absolute correction: **2.9012%**;
- within ±3.5%: **82.65%**;
- within ±4%: **85.21%**;
- worst session mean: **4.9910%**;
- P90: **6.3700%**.

Small deadtime adjustments produce only marginal changes and generally trade mean error against tail metrics.

**Status: REJECT as a primary new mechanism.**  
**Falsification:** a real deadtime layer should produce a material cross-session gain, especially in low-pulse regions, without worsening the overall mean/worst session. It did not.

### AgentRed #527 — transient guard for last-ratio
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/527

Best reported guard (`dMAP=0.05`, `dRPM=200`, half-authority fallback):
- mean absolute correction: **2.8003%**;
- within ±3.5%: **82.68%**;
- within ±4%: **85.22%**;
- worst session mean: **4.6599%**;
- P90: **6.1224%**;
- P99: **24.0694%**.

Compared with the unguarded baseline (~2.9012%, worst session ~4.9910%, P90 ~6.3700%), this is a real improvement in mean and session-level tail with essentially preserved ±4% coverage.

**Status: PROMOTE.**  
**Falsification:** reject if blocked-temporal / frame-lag replay shows the guard merely hides target information or loses coverage under real timing.

### AgentRed #529 — ratio shock limiter
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/529

Best tested hard limiter around 30%:
- mean absolute correction: **2.9127%**;
- within ±4%: **85.08%**;
- worst session mean: **4.9085%**;
- P90: **6.4356%**;
- P99: **25.5493%**.

It trims some worst behavior but loses slightly on the central metrics versus the plain baseline and is weaker than the transient guard.

**Status: KEEP_AS_LAYER.**  
Use only as a final safety cap, not as the main adaptive mechanism.

## 6. GNV transfer/calibration evidence

### AgentRed #563 — direct PETROL -> CNG switch-pair truth
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/563  
Artifact: https://github.com/viluadmcontas2-dot/AgentRed/blob/2b7b4fd94fc7d71ddd9dee587e45bc8442fe54f3/manifest.json

19 usable direct switch pairs:
- median direct correction: **3.5110%**;
- static/model-vs-direct MAE: **10.0510%**;
- within ±4% agreement: **26.32%**.

**Conclusion:** direct, temporally paired switch evidence is substantially different from a static cross-session gasoline model.

**Status: PROMOTE direct switch pairs as truth anchors; REJECT static model as substitute for anchors.**

### AgentRed #571 — switch-pair stability threshold sweep
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/571

Examples:
- 3-frame window, MAP gap <=0.02 bar, RPM gap <=150/250: 19 pairs, median correction **2.961%**, MAD **1.661%**, P90 absolute **6.833%**.
- tighter operating-point matching generally reduces the tail while reducing sample count.

**Status: PROMOTE confidence gating for switch anchors.**

### AgentRed #575 — strict switch-reference benchmark
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/575  
Artifact: https://github.com/viluadmcontas2-dot/AgentRed/blob/94b63574e8922c016af482cf42b6085d0aa9c433/manifest.json

11 strict high-confidence pairs:

| Method | MAE | Median | within ±4% | Worst |
|---|---:|---:|---:|---:|
| frozen immediate reference | **2.112%** | 1.858% | **81.82%** | **5.753%** |
| K memory | 2.929% | 1.742% | 63.64% | 7.668% |
| local gasoline twin | 4.355% | 2.745% | 54.55% | 9.219% |
| static reference | 10.589% | 7.449% | 27.27% | 31.453% |

**Status: PROMOTE the strict immediate switch reference as a high-confidence GNV calibration anchor.**

Important limitation: this result validates the switch moment, not indefinite operation on frozen gain.

### AgentRed #566 — looser frozen switch reference
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/566

19 looser pairs:
- MAE **4.532%**;
- median absolute difference **2.839%**;
- within ±4% **52.63%**.

**Status: KEEP_AS_LAYER only with strict confidence gates.**  
The strong #575 result depends on choosing genuinely equivalent/stable switch conditions.

### AgentRed #567 — frozen anchor plus local slow memory
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/567

On the same 19 looser pairs:
- frozen only: 4.532% MAE / 52.6% within4;
- 25% slow-memory blend: **3.970% MAE / 63.2% within4**;
- heavier slow-memory blends get worse.

**Status: KEEP_AS_LAYER.**  
A small slow component may help, but high authority is harmful.

### AgentRed #584 — PETROL-CNG-PETROL roundtrip frozen gain
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/584

18 long/roundtrip segments:
- best tested window mean absolute error ~**7.83%**;
- within ±4% ~**40.7%**;
- worst ~**34.1%**.

**Status: REJECT indefinite frozen gain.**

### AgentRed #585 — frozen-gain duration drift
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/585

21 segments:
- duration-bin mean absolute errors roughly **6.99–10.74%**;
- within ±4% only **20–40%**;
- worst segment up to **28.18%**.

**Status: REJECT indefinite frozen gain.**  
A switch anchor is a high-confidence starting point, not a permanent reference for an entire CNG trip.

### AgentRed #586 — long-CNG frozen-to-slow blend
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/586

Slow-memory decay improves mean only from ~8.26% to ~8.05% and within4 from ~34% to ~37%, while worst remains ~28%.

**Status: REJECT this long-CNG formulation.**

## 7. Why a second full GNV map is not justified

### AgentRed #564 — nearest gasoline twin
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/564

65,407 CNG frames:
- even nearest gasoline twins give ~**11.94% mean absolute correction** overall;
- P90 absolute ~**27.12%**;
- best-distance quartile remains ~**7.83% mean abs**.

**Status: REJECT as continuous primary truth.**

### AgentRed #565 — GNV manual-delta cell stability
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/565

Across K-like grids:
- weighted MAD ~**6.27–6.88%**;
- cross-session range ~**29–33%**.

**Status: REJECT the idea that a dense static second GNV map alone solves calibration.**

### AgentRed #576 — same-session gasoline twins on CNG
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/576

22,458 matched CNG frames across 6 sessions:
- coverage vs all CNG: **34.3%**;
- mean absolute correction: **11.31%**;
- P90 absolute: **24.41%**;
- within ±4%: **25.16%**.

**Status: REJECT as primary continuous calibration truth.**

### AgentRed #578 — twin-reference correction map stability
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/578

Finest tested grid (0.25 ms x 250 RPM):
- coverage ~**90.2%**;
- weighted MAD ~**4.53%**;
- only ~2.4 sessions per cell on average.

Coarser grids increase weighted MAD toward 5.7–6.7%.

**Status: KEEP_AS_LAYER for sparse residual memory; do not promote a second full GNV map.**

## 8. Transient/tail evidence

### AgentRed #579 — twin-reference transient split
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/579

Non-transient:
- n=18,914;
- mean abs **9.83%**;
- P90 **21.08%**.

Transient:
- n=1,137;
- mean abs **22.91%**;
- P90 **56.87%**.

**Status: PROMOTE transient authority reduction.**

### AgentRed #569 — low-pulse/transient audit
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/569

Representative examples:
- 4–5 ms, stable: mean abs **5.19%**, P90 **11.45%**;
- 4–5 ms, transient: mean abs **23.40%**, P90 **64.91%**;
- 3–4 ms, stable: mean abs **9.64%**, P90 **19.76%**;
- 3–4 ms, transient: mean abs **20.27%**, P90 **55.88%**.

**Status: PROMOTE transient guard; KEEP_AS_LAYER low-pulse awareness.**

## 9. Pressure / temperature

### AgentRed #559 — pressure-temperature incremental audit
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/559

Cross-session correction model:
- base: MAE 10.61%, P90 18.85%, worst 15.73%;
- + pressure/temp/water: MAE **9.52%**, P90 **18.33%**, but worst **16.47%**.

**Status: KEEP_AS_LAYER.**

### AgentRed #577 — pressure-temperature after local twin reference
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/577

20,051 matched CNG frames, 6 sessions:
- base: MAE 10.65%, P90 17.59%, worst 14.06%;
- + pressure/temp: MAE **8.10%**, P90 **16.25%**, worst **11.07%**.

**Status: KEEP_AS_LAYER.**  
There is real residual signal, but it is nowhere near sufficient as the primary learner.

### AgentRed #568 — pressure/temp after frozen reference
Receipt: https://github.com/viluadmcontas2-dot/AgentRed/issues/568

Execution failed because the experiment referenced Pandas `DataFrame.corr` as a method instead of the intended `corr` column.

**Status: INCONCLUSIVE.**

## 10. Timeouts / incomplete experiments

- #572 causal petrol-memory interpolation sweep — **TIMEOUT**, no usable result.
- #580 strict fast-slow K-memory fine sweep — **TIMEOUT**, no usable result.

Do not infer scientific conclusions from these two jobs.

## 11. Candidate architecture after consolidation

The evidence currently supports this incremental architecture:

```text
DENSE GASOLINE REFERENCE
  learned from petrol operation
        |
        v
FAST TEMPORAL REFERENCE GAIN
  last-ratio + transient guard
        |
        +------------------------------+
        |                              |
        v                              v
STRICT PETROL->CNG SWITCH         CONTINUOUS CNG OBSERVATION
ANCHOR (high confidence)          (lower authority)
        |                              |
        +----------> RESIDUAL K <------+
                     MEMORY
                 sparse/local/slow
                        |
             optional P/T residual layer
                        |
                 shock/safety limiter
                        |
                 K SUGGESTION ONLY
                 no automatic writer
```

Interpretation:
- gasoline builds the dense reference;
- a good switch provides a **high-confidence anchor** for GNV correction;
- the anchor is not frozen forever;
- during longer CNG operation, continuous residual evidence may move the local correction slowly, only when reference confidence is high and the frame is not transient;
- sparse residual K memory/interpolation transfers corrections to nearby operating points, avoiding a second dense GNV map;
- pressure/temperature are secondary residual compensators;
- transient frames have reduced or zero update authority.

## 12. Next falsification target

The key unresolved question is no longer “can gasoline be learned?” It is:

> Can sparse, strict switch anchors plus low-authority continuous residual updates predict the required K correction across CNG operation with <=4% practical accuracy and much smaller tails, without learning a second full GNV map?

The next experiments must therefore test:
1. exact MP48 `petrol_ms` semantics and continuity during CNG;
2. sparse switch-anchor interpolation / cross-session generalization;
3. whether `gas_ms / petrol_ms` exposes a stable multiplicative bridge to K;
4. long-CNG adaptation after a switch anchor without future leakage;
5. transient guard + shock limiter on that bridge.

No APK, Emulator, OBD dependency or automatic writer belongs to this stage.
