# RED V8.2 Science Blend — Local Science Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a deterministic, dependency-free offline scientific harness that can falsify the RED Blend hypotheses about local `Tinj | RPM, MAP`, uncertainty reduction with repetition, and multimodality without changing the Android/runtime fast path.

**Architecture:** Add a small Python-only laboratory under `lab/red_blend/`. The laboratory is deliberately outside production Android code and uses only the Python standard library. It exposes robust descriptive statistics, seeded bootstrap/Monte Carlo uncertainty, deterministic 1-Gaussian versus 2-component Gaussian-mixture comparison, and conservative multimodality evidence. A dedicated GitHub Actions workflow runs the science tests plus existing RED fast contracts; Android stays untouched in this phase.

**Tech Stack:** Python 3 standard library (`math`, `random`, `statistics`, `dataclasses`, `unittest`), GitHub Actions standard `ubuntu-latest`, existing `tools/run_checks.py`.

**Spec:** `docs/superpowers/specs/2026-08-30-red-v82-science-blend-design.md`

## Global Constraints

- Branch authority: `work/red-v82-science-blend`; do not mutate `hotfix/v8.0-red-performance`.
- RED functional comparison anchor remains `b637f5fff19b1ece93f22d1fced9640618609a60`.
- `RPM×MAP` defines the local physical region; local repeated valid frames are not discounted merely for sharing a session.
- Session/epoch evidence is reserved for persistence/transfer tests; this plan does not claim transfer proof.
- No raw Google Drive logs, device/network identifiers, credentials, or secrets enter the public repository.
- No Android production/runtime source is modified in this plan.
- No automatic ECU write; no removal of confirmation, ACK, or readback.
- No `P(improve)` claim in this phase.
- Gaussianity is a hypothesis, not an assumption. A mixture is evidence only when model selection, component mass, and component separation all support it.
- Any thresholds used by the lab are explicitly tagged `LAB_HEURISTIC`; they are not production thresholds and cannot become action authority from this plan.
- No third-party Python dependencies are added.

---

## File Structure

- `lab/red_blend/__init__.py` — package boundary only.
- `lab/red_blend/local_science.py` — deterministic descriptive statistics, bootstrap/Monte Carlo, 1-Gaussian fit, 2-Gaussian EM fit, BIC comparison and multimodality decision.
- `lab/red_blend/test_local_science.py` — falsification-first unit tests using deterministic synthetic data only.
- `.github/workflows/red-v82-science-blend.yml` — Blend-only remote science gate; no Android build in this phase.
- `docs/evidence/red-blend-local-science.md` — written only after the first GREEN, recording SHA, commands, measured synthetic outcomes, limits and non-claims.

---

### Task 1: Freeze the public interfaces with failing tests

**Files:**
- Create: `lab/red_blend/__init__.py`
- Create: `lab/red_blend/test_local_science.py`
- Create: `.github/workflows/red-v82-science-blend.yml`

**Interfaces:**
- Consumes: Python standard library only.
- Produces: tests expecting `lab.red_blend.local_science` to expose `DistributionSummary`, `BootstrapInterval`, `GaussianFit`, `GaussianMixtureFit`, `MultimodalityPolicy`, `MultimodalityDecision`, `summarize_distribution(samples)`, `bootstrap_mean_interval(samples, draws, seed, alpha)`, `fit_gaussian(samples)`, `fit_gmm2(samples, max_iterations, tolerance)`, and `detect_multimodality(samples, policy)`.

- [ ] **Step 1: Create the package marker**

```python
# lab/red_blend/__init__.py
"""Offline scientific harness for RED V8.2 Blend experiments."""
```

- [ ] **Step 2: Write the failing tests**

Create `lab/red_blend/test_local_science.py` with deterministic synthetic cases:

```python
import math
import random
import unittest

from lab.red_blend.local_science import (
    MultimodalityPolicy,
    bootstrap_mean_interval,
    detect_multimodality,
    fit_gaussian,
    fit_gmm2,
    summarize_distribution,
)


def gaussian_samples(seed: int, mean: float, sigma: float, n: int) -> list[float]:
    rng = random.Random(seed)
    return [rng.gauss(mean, sigma) for _ in range(n)]


class LocalScienceTest(unittest.TestCase):
    def test_summary_tracks_dense_stable_local_region(self):
        samples = gaussian_samples(11, 2.68, 0.03, 1200)
        summary = summarize_distribution(samples)
        self.assertEqual(summary.count, 1200)
        self.assertAlmostEqual(summary.mean, 2.68, delta=0.006)
        self.assertLess(summary.cv, 0.02)
        self.assertLess(summary.p90 - summary.p10, 0.09)

    def test_seeded_bootstrap_is_reproducible_and_tightens_with_more_samples(self):
        small = gaussian_samples(22, 2.68, 0.05, 40)
        large = gaussian_samples(22, 2.68, 0.05, 800)
        a = bootstrap_mean_interval(small, draws=1200, seed=7, alpha=0.05)
        b = bootstrap_mean_interval(small, draws=1200, seed=7, alpha=0.05)
        large_ci = bootstrap_mean_interval(large, draws=1200, seed=7, alpha=0.05)
        self.assertEqual(a, b)
        self.assertLess(large_ci.high - large_ci.low, a.high - a.low)

    def test_single_gaussian_wins_for_unimodal_region(self):
        samples = gaussian_samples(33, 2.70, 0.035, 800)
        one = fit_gaussian(samples)
        two = fit_gmm2(samples)
        self.assertLess(one.bic, two.bic)
        decision = detect_multimodality(samples, MultimodalityPolicy())
        self.assertFalse(decision.is_multimodal)

    def test_two_component_mixture_wins_for_separated_balanced_modes(self):
        samples = (
            gaussian_samples(44, 2.55, 0.025, 450)
            + gaussian_samples(45, 2.85, 0.030, 450)
        )
        decision = detect_multimodality(samples, MultimodalityPolicy())
        self.assertTrue(decision.is_multimodal)
        self.assertGreater(decision.bic_gain, 10.0)
        self.assertGreater(decision.separation_sigma, 2.5)
        self.assertGreaterEqual(decision.min_component_weight, 0.15)

    def test_tiny_outlier_cluster_does_not_become_second_regime(self):
        samples = (
            gaussian_samples(55, 2.70, 0.035, 970)
            + gaussian_samples(56, 3.10, 0.010, 30)
        )
        decision = detect_multimodality(samples, MultimodalityPolicy(min_component_weight=0.10))
        self.assertFalse(decision.is_multimodal)
        self.assertLess(decision.min_component_weight, 0.10)

    def test_results_are_finite_for_low_variance_region(self):
        samples = [2.7000 + (i % 3) * 1e-7 for i in range(300)]
        one = fit_gaussian(samples)
        two = fit_gmm2(samples)
        self.assertTrue(math.isfinite(one.log_likelihood))
        self.assertTrue(math.isfinite(two.log_likelihood))
        self.assertTrue(math.isfinite(one.bic))
        self.assertTrue(math.isfinite(two.bic))


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 3: Add the Blend-only workflow that proves the TDD RED**

Create `.github/workflows/red-v82-science-blend.yml`:

```yaml
name: OMEGAS RED V8.2 Science Blend

on:
  workflow_dispatch:
  push:
    branches:
      - work/red-v82-science-blend
    paths:
      - "lab/red_blend/**"
      - "tests/**"
      - "tools/**"
      - ".github/workflows/red-v82-science-blend.yml"

permissions:
  contents: read

concurrency:
  group: omegas-red-v82-blend-${{ github.ref }}
  cancel-in-progress: true

jobs:
  science-local:
    name: SCIENCE LOCAL falsification
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - name: Checkout exact Blend SHA
        uses: actions/checkout@v7
      - name: Prove source identity
        shell: bash
        run: |
          set -euo pipefail
          test "$(git rev-parse HEAD)" = "$GITHUB_SHA"
          echo "SOURCE_SHA=$GITHUB_SHA"
          echo "SOURCE_TREE=$(git rev-parse HEAD^{tree})"
      - name: RED fast contracts
        shell: bash
        run: |
          set -euo pipefail
          python3 -B tools/run_checks.py
      - name: Blend local science tests
        shell: bash
        run: |
          set -euo pipefail
          python3 -B -m unittest -v lab.red_blend.test_local_science
```

- [ ] **Step 4: Push/commit tests before implementation and verify the expected failure remotely**

Expected command semantics:

```bash
python3 -B -m unittest -v lab.red_blend.test_local_science
```

Expected result: FAIL before implementation with `ModuleNotFoundError: No module named 'lab.red_blend.local_science'`. GitHub Actions run for the exact test-only SHA must conclude `failure` for this reason, not because the workflow is malformed and not because an existing RED contract regressed.

- [ ] **Step 5: Commit the RED test milestone**

```bash
git add lab/red_blend/__init__.py lab/red_blend/test_local_science.py .github/workflows/red-v82-science-blend.yml
git commit -m "test(blend): define local science falsification contracts"
```

---

### Task 2: Implement robust local distribution and seeded Monte Carlo bootstrap

**Files:**
- Create: `lab/red_blend/local_science.py`
- Test: `lab/red_blend/test_local_science.py`

**Interfaces:**
- Produces:
  - `DistributionSummary(count: int, mean: float, median: float, std: float, mad: float, p10: float, p90: float, cv: float)`
  - `BootstrapInterval(low: float, estimate: float, high: float, draws: int, seed: int)`
  - `summarize_distribution(samples: Sequence[float]) -> DistributionSummary`
  - `bootstrap_mean_interval(samples: Sequence[float], draws: int = 2000, seed: int = 0, alpha: float = 0.05) -> BootstrapInterval`

- [ ] **Step 1: Implement deterministic validation and quantiles**

Rules:
- reject fewer than two samples with `ValueError`;
- reject NaN/Inf with `ValueError`;
- sort a copied float list;
- linear-interpolation quantile at `index=(n-1)*q`;
- sample standard deviation uses denominator `n-1`;
- MAD is median absolute deviation around the sample median;
- CV is `std/abs(mean)` when `abs(mean)>1e-12`, otherwise `inf`.

- [ ] **Step 2: Implement seeded bootstrap of the sample mean**

Algorithm:

```python
rng = random.Random(seed)
for _ in range(draws):
    resample = [values[rng.randrange(n)] for _ in range(n)]
    means.append(sum(resample) / n)
means.sort()
low = quantile(means, alpha / 2)
high = quantile(means, 1 - alpha / 2)
```

Validation:
- `draws >= 100`;
- `0 < alpha < 1`;
- output includes the original-sample mean as `estimate`.

- [ ] **Step 3: Run the summary/bootstrap tests**

```bash
python3 -B -m unittest -v \
  lab.red_blend.test_local_science.LocalScienceTest.test_summary_tracks_dense_stable_local_region \
  lab.red_blend.test_local_science.LocalScienceTest.test_seeded_bootstrap_is_reproducible_and_tightens_with_more_samples
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add lab/red_blend/local_science.py lab/red_blend/test_local_science.py
git commit -m "feat(blend): add deterministic local distribution bootstrap"
```

---

### Task 3: Implement Gaussian-vs-mixture falsification without third-party dependencies

**Files:**
- Modify: `lab/red_blend/local_science.py`
- Test: `lab/red_blend/test_local_science.py`

**Interfaces:**
- Produces:
  - `GaussianFit(mean: float, variance: float, log_likelihood: float, bic: float)`
  - `GaussianComponent(weight: float, mean: float, variance: float)`
  - `GaussianMixtureFit(components: tuple[GaussianComponent, GaussianComponent], log_likelihood: float, bic: float, iterations: int, converged: bool)`
  - `MultimodalityPolicy(bic_gain_min: float = 10.0, min_component_weight: float = 0.15, separation_sigma_min: float = 2.5)`
  - `MultimodalityDecision(is_multimodal: bool, bic_gain: float, min_component_weight: float, separation_sigma: float, one: GaussianFit, two: GaussianMixtureFit, policy_label: str = "LAB_HEURISTIC")`
  - `fit_gaussian(samples) -> GaussianFit`
  - `fit_gmm2(samples, max_iterations=200, tolerance=1e-9) -> GaussianMixtureFit`
  - `detect_multimodality(samples, policy=MultimodalityPolicy()) -> MultimodalityDecision`

- [ ] **Step 1: Implement numerically stable one-Gaussian likelihood and BIC**

Use population MLE variance for likelihood with floor `1e-12`.

```python
log_pdf = -0.5 * (math.log(2 * math.pi * variance) + ((x - mean) ** 2) / variance)
bic = 2 * math.log(n) - 2 * log_likelihood  # k=2
```

- [ ] **Step 2: Implement deterministic two-component 1D EM**

Initialization:
- means from empirical Q25 and Q75;
- weights `0.5, 0.5`;
- both variances initialized to the one-Gaussian variance, floored at `1e-12`.

E-step:
- compute `log(weight) + gaussian_log_pdf` per component;
- normalize with two-value log-sum-exp to avoid underflow.

M-step:
- `weight_j = responsibility_sum_j / n`;
- `mean_j = sum(r_ij*x_i)/sum(r_ij)`;
- `variance_j = sum(r_ij*(x_i-mean_j)^2)/sum(r_ij)`, floor `1e-12`;
- if either responsibility mass is `<1e-9`, return a degenerate two-component fit with BIC worse than the one-Gaussian fit rather than fabricating a regime.

Convergence:
- stop when absolute log-likelihood improvement `<= tolerance * (1 + abs(previous_log_likelihood))`;
- component order is canonicalized by increasing mean.

BIC:

```python
bic = 5 * math.log(n) - 2 * log_likelihood  # 2 means + 2 variances + 1 free weight
```

- [ ] **Step 3: Implement conservative multimodality evidence**

Definitions:

```python
bic_gain = one.bic - two.bic
min_weight = min(c.weight for c in two.components)
pooled_sigma = math.sqrt((c1.variance + c2.variance) / 2.0)
separation = abs(c2.mean - c1.mean) / max(pooled_sigma, 1e-12)
is_multimodal = (
    two.converged
    and bic_gain >= policy.bic_gain_min
    and min_weight >= policy.min_component_weight
    and separation >= policy.separation_sigma_min
)
```

The returned `policy_label` is exactly `LAB_HEURISTIC` to prevent accidental interpretation as a production-calibrated gate.

- [ ] **Step 4: Run all science tests**

```bash
python3 -B -m unittest -v lab.red_blend.test_local_science
```

Expected: all six tests PASS.

- [ ] **Step 5: Run RED fast regression contracts**

```bash
python3 -B tools/run_checks.py
```

Expected: PASS with the existing RED contract count/output; no runtime source has changed.

- [ ] **Step 6: Commit**

```bash
git add lab/red_blend/local_science.py lab/red_blend/test_local_science.py
git commit -m "feat(blend): add Gaussian mixture falsification lab"
```

---

### Task 4: Prove GREEN remotely and record evidence without overclaiming

**Files:**
- Create after GREEN: `docs/evidence/red-blend-local-science.md`
- Modify: Issue `#11` with a checkpoint comment.

**Interfaces:**
- Consumes: exact GREEN GitHub Actions run URL/ID, exact source SHA/tree, test output.
- Produces: auditable evidence for `G3_LOCAL_TINJ_DISTRIBUTION` and `G4_MULTIMODALITY_FALSIFICATION` **at synthetic-harness level only**. It does not close those gates for the real historical corpus.

- [ ] **Step 1: Verify the workflow for the implementation SHA**

Required evidence:
- workflow `OMEGAS RED V8.2 Science Blend`;
- exact implementation SHA;
- `RED fast contracts` PASS;
- `Blend local science tests` PASS;
- standard `ubuntu-latest` runner;
- no Android build and no ECU operation in this phase.

- [ ] **Step 2: Record the limits explicitly**

Evidence document must state:
- `SYNTHETIC_HARNESS_PROVEN=true` only if the Actions run is GREEN;
- `REAL_CORPUS_LOCAL_DISTRIBUTION_PROVEN=false`;
- `REAL_CORPUS_MULTIMODALITY_PROVEN=false`;
- `TRANSFER_PROVEN=false`;
- `P_IMPROVE_PROVEN=false`;
- `VEHICLE_PROVEN=false`;
- `PRODUCTION_RUNTIME_CHANGED=false`.

- [ ] **Step 3: Commit evidence**

```bash
git add docs/evidence/red-blend-local-science.md
git commit -m "docs(blend): record local science harness proof"
```

- [ ] **Step 4: Comment Issue #11 with SHA/run/gate state**

The comment must link the exact source SHA and workflow run and identify the next gate as privacy-safe real-corpus replay. Do not mark Issue #11 complete.

---

## Self-Review

- Spec coverage for this plan: design sections 3.1, 3.3, H1, H2, H4, Stage A, Stage B and the offline portion of Stage F are directly exercised. The rest of the spec is intentionally deferred to later independently reviewable plans because transfer/causal/performance/Android are separate subsystems/gates.
- Placeholder scan: no `TBD`, `TODO`, `implement later`, or unspecified error-handling steps remain.
- Type consistency: all functions/types imported by Task 1 are defined in Tasks 2–3 with matching names and signatures.
- Safety: no production runtime files, calibration writers, ECU paths or raw private corpus files are touched.
- Promotion discipline: a GREEN from this plan proves the synthetic scientific harness only; it cannot prove the real RPM×MAP hypothesis, vehicle behavior, fuel economy, `P(improve)`, or production readiness.

## Execution Mode

Owner already instructed `Execute` / `siga`; execute inline with `superpowers:executing-plans`, preserving TDD order and remote evidence checkpoints.