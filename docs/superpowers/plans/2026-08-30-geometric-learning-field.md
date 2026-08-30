# Geometric Learning Field — Implementation Plan

1. Lock the RPM × MAP semantics with failing Kotlin and UI contract tests.
2. Correct the learning explanation and reorganize cell details around the six
   human questions, without changing prediction values.
3. Add a tested offline local-affine estimator that preserves evidence mass and
   reports session independence and chronological provenance separately.
4. Add a nested chronological comparison against the proven RED anchor.
5. Generate checksummed evidence; promote nothing unless all blind gates pass.
6. Run focused suites, full Python/UI suites and remote Kotlin/Android CI before
   claiming completion.

