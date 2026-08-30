# RED V8.2 Science Blend — Session Independence Plan

> Execute with Superpowers TDD discipline on `work/red-v82-science-blend`. This is an offline science gate only; Android/runtime is out of scope.

**Goal:** Determine whether local `Tinj | RPM, MAP` precision persists across independent sessions or whether pooled certainty/GMM structure is explained by between-session drift.

**Why now:** The governed real-corpus audit found that dense regions are mostly one-Gaussian-supported or ambiguous, while the strongest `MULTIMODAL` labels occur in sparse regions. Before any GMM/runtime promotion, the Blend must attribute variation to within-session noise versus between-session shift and test new-session transfer directly.

## Statistical model

For one fuel and one governed RPM×MAP bin:

`y_ij = mu_region + b_session_i + epsilon_ij`

where `b_session` represents between-session offset and `epsilon` is within-session variation.

Use a dependency-free one-way random-effects moments decomposition for unequal session sizes:

- `SSW = sum_i sum_j (y_ij - mean_i)^2`
- `MSW = SSW / (N-k)` when `N>k`
- `SSB = sum_i n_i (mean_i-grand_mean)^2`
- `MSB = SSB / (k-1)`
- `n0 = (N - sum(n_i^2)/N)/(k-1)`
- `tau2 = max(0, (MSB-MSW)/n0)`
- `ICC = tau2 / (tau2 + MSW)` when denominator > 0

`tau2` is a diagnostic variance floor candidate only; it is **not** a production-calibrated uncertainty model.

## Independent transfer ruler

For every region with at least 3 sessions:

1. hold out one complete `session_key`;
2. compute each remaining session mean;
3. predict the held-out session mean using the **unweighted mean of training session means**, so one long session cannot dominate the independent-session ruler;
4. record absolute relative error of predicted versus held-out session mean;
5. aggregate median/P90/max across held-out sessions.

This is explicitly different from local frame precision. Repeated frames remain useful locally; they simply do not count as independent-session replications.

## Mixture attribution test

For a pooled RPM×MAP region:

1. compute existing pooled GMM/BIC evidence;
2. center every observation by its session mean and add the overall region mean;
3. rerun the same GMM/BIC detector on the session-centered values;
4. compare pooled versus centered `bic_gain`, separation and classification.

Interpretation:
- if pooled mixture evidence collapses after session centering, the apparent modes are substantially attributable to session offsets/drift;
- if strong separation survives session centering, a within-session/local hidden regime remains a candidate;
- neither outcome alone authorizes runtime promotion.

## TDD contracts

### Synthetic tests first

1. Same true mean across several sessions, different random noise:
   - low `tau2` / low ICC;
   - leave-one-session-out (LOSO) error small.
2. Sessions with deliberately shifted means but individually tight distributions:
   - pooled bootstrap can be tight with many frames;
   - `tau2` and ICC must expose strong between-session drift;
   - LOSO error must be materially larger than same-mean case.
3. Session-offset pseudo-bimodality:
   - pooled data should show strong mixture evidence;
   - session-centering should collapse or materially reduce that evidence.
4. True within-session bimodality repeated inside every session:
   - strong mixture evidence should survive session-centering.
5. Determinism and finite low-variance behavior.

### Real-corpus tests

- fixture identity remains fail-closed and unchanged;
- session-aware audit is deterministic for a fixed corpus;
- regions with <3 sessions are excluded from LOSO transfer claims rather than promoted;
- gasoline bin `(5,13)` with only 2 sessions must be explicitly `INSUFFICIENT_INDEPENDENT_SESSIONS` for LOSO regardless of pooled GMM classification;
- dense gasoline bins `(5,9)` and `(5,10)` must expose session decomposition and mixture-attribution fields without hard-coding a desired scientific outcome.

## Files

- Create: `lab/red_blend/session_science.py`
- Create: `lab/red_blend/test_session_science.py`
- Extend: `lab/red_blend/test_real_corpus.py`
- Extend: `.github/workflows/red-v82-science-blend.yml`
- After GREEN: create `docs/evidence/red-blend-session-independence.md`
- Comment Issue #11 with exact RED/GREEN SHAs and run IDs.

## Promotion / non-promotion

This gate may prove `SESSION_INDEPENDENCE_AUDITED=true` and may produce a session-aware uncertainty-floor candidate. It cannot prove causal Map K response, `P(improve)`, vehicle behavior, fuel economy, or production/runtime readiness.

No Android source, ECU writer, confirmation, ACK/readback, or RED hot path may change in this plan.