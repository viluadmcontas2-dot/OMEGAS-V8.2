# OMEGAS V8.0 RED Fast Learning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Repair AutoCal navigation and make global suggestions faster, clearer and safer without expanding the RED architecture.

**Architecture:** Preserve the existing advisor, adapter and manual writer. Add independent-visit-aware step sizing and publish ideal target separately from the bounded review step.

**Tech Stack:** Kotlin/JVM, org.json, JavaScript WebView UI, Node test runner, Gradle Android.

**Spec:** `docs/superpowers/specs/2026-08-29-red-fast-global-suggestions-design.md`

## Global Constraints

- Branch: `hotfix/v8.0-red-performance`.
- No automatic ECU writes.
- Preserve ACK/readback and human confirmation.
- No new polling loop, store, writer or unbounded history.
- Source mutations occur on GitHub remote; verification uses the exact remote SHA.

### Task 1: AutoCal tab ownership

- [x] Add a failing runtime regression.
- [x] Prove CurveScreen overrides the AutoCal controller.
- [x] Make CurveScreen ignore externally owned views.
- [x] Run focused AutoCal tests.

### Task 2: Global target and bounded step

- [ ] Add failing advisor tests for ideal target and visit-aware caps.
- [ ] Publish ideal target and step policy.
- [ ] Bound the current step by independent visit support.
- [ ] Run focused advisor tests.

### Task 3: Human-readable global suggestion

- [ ] Add failing adapter test proving the step is used.
- [ ] Explain ideal error, safe step and expected residual.
- [ ] Run adapter and session lifecycle tests.

### Task 4: Verification and artifact

- [ ] Run affected Node/JVM tests.
- [ ] Run the fast quality gate.
- [ ] Run broad Android/JVM, lint and assemble only after the fast gate is green.
- [ ] Record exact final SHA and artifact evidence; do not claim physical fuel economy without vehicle evidence.
