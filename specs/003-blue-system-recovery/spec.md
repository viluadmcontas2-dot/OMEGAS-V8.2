# Spec 003 — Blue System Recovery

Epic: #18  
Work Unit: `docs/workunits/OMEGAS-BLUE-RECOVERY-001.md`

## Problem
The current Blue branch can compile and pass static contracts while core cockpit behavior is unusable or blank. The recovery must converge scientific truth, runtime behavior, telemetry performance and touch UX without restoring obsolete RED predictor authority.

## Invariants
1. Repo-first / remote-first.
2. `BlueCausalEngine` is the only correction authority.
3. MP48 remains calibration/fuel/write truth; OBD remains physical correction evidence and has zero writer authority.
4. `TRANSITION` is physically gasoline; `CUT-OFF` is distinct and not learning-eligible.
5. No automatic K write.
6. No owner-adjustable control may silently redefine scientific truth without explicit documented rationale.
7. No screen considered valid because its source contains expected strings; essential UI needs runtime/bootstrap behavior proof.
8. No APK during triage or partial remediation.

## Functional requirements

### FR-UI-01 Essential routes
Agora and OBD MUST have a non-empty local fallback state. Dynamic bootstrap MAY enhance/replace it, but failure MUST be visible and diagnosable rather than a blank panel.

### FR-UI-02 WebView compatibility
Essential layout MUST NOT depend on CSS/JS features unsupported by the target vendor WebView without a fallback. Asset identity MUST be visible/auditable so installed-version mismatch can be distinguished from runtime failure.

### FR-LEARN-01 Blue comparison projection
The Learning `Desvio medido` layer MUST consume comparisons produced by Blue authority. A UI projection layer MUST NOT zero or fabricate those comparisons.

### FR-LEARN-02 Evidence quality
Evidence quality MUST survive store -> grid projection -> UI without being renamed to a missing field. Visits/sample counts MUST be displayed separately from physical quality.

### FR-LEARN-03 Fuel boundary
TRANSITION frames MUST be classified consistently with the confirmed physical rule that fuel is still gasoline. CUT-OFF MUST reset/deny learning. No interval may mix fuels.

### FR-LEARN-04 Tolerance ownership
Every legacy tolerance MUST be classified as hard truth/safety, internal automatic sampling quality, diagnostic context, or obsolete. User-facing `Muito rigoroso...Muito flexível` profiles MUST be removed from normal workflow unless a surviving setting has a clear owner decision use-case and cannot alter scientific truth unpredictably.

### FR-TELEM-01 Latency budget
Telemetry timing MUST be measured as separate stages: MP48 transaction, native delivery, store freshness, bridge/presentation, WebView render. The system MUST expose enough metrics to identify which stage causes staleness.

### FR-TELEM-02 Backpressure
Live display MUST remain latest-only under a slow consumer. Scientific hot buffer MUST stay bounded and stale USB generations MUST be purged. Durable session recording remains the audit backlog.

### FR-BG-01 Background
ForegroundService MUST remain acquisition/learning authority while the WebView is not drawing. Vendor battery policy and overlay permission MUST be surfaced truthfully.

### FR-OVERLAY-01 Floating telemetry
The native overlay MUST have an explicit discoverable enable/permission/status path. It MUST not depend on legacy OBD-map state for its basic cell/RPM/Petrol display.

### FR-TOOLS-01 Stable interaction
Periodic data refresh MUST NOT recreate the retention/settings disclosure while it is open or edited. Focus, values, disclosure state and scroll MUST survive live log refreshes.

### FR-CURVE-01 Selection mode
Curve editor MUST provide explicit Selection ON/OFF. In Selection ON, touch/drag changes selection only. Selected points MUST be visually unmistakable before any value operation.

### FR-CURVE-02 Batch semantics
Nudge buttons are deltas. Absolute entry is assignment. For selected factors [0.95,1.00,1.05], `+0.05` yields [1.00,1.05,1.10]; `Definir 1.20` yields [1.20,1.20,1.20].

### FR-CURVE-03 Cockpit performance
Selection/batch editing MUST not trigger ECU reread and SHOULD render at most once per user batch action. Review MUST be reachable without a long scroll through 30 per-point rows.

## Non-goals
- Reintroduce RED Advisor/Predictor as decision authority.
- Add new automatic calibration.
- Solve every future product idea.
- Claim physical vehicle success from GitHub CI.

## Acceptance
All child issues #17, #19, #20, #21, #22 satisfy their behavior tests; no P0/P1 known regression remains; project/status/specs are coherent; canonical exact-SHA gates pass. APK generation happens only after this state.