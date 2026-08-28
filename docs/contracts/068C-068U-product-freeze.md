# 068C–068U — Product contract freeze

Mirror of the binding Notion interfase between G2 and Phase 04. This document freezes future behavior; it does **not** claim that the current UI/bundle/draft already implements it.

## 068C — Canonical minimal overlay
Normal state shows only: RPM, Petrol Inj. (ms), human fuel (`Gasolina | GNV | Transição/Desconhecido`) and backend-owned `Coletando | Não coletando`. Unknown is `—`. Map K, Curve K, cell, STFT/LTFT, pressure, queues, confidence, logs, Predictor and history stay hidden by default. Future owner: F9/F11 overlay.

## 068D — Overlay geometry, interaction and lifecycle
Baseline 176×112dp, functional minimum 160×104dp. Drag repositions; simple tap foregrounds OMEGAS while preserving route/context. ON/OFF, permission and reset position live in Tools. Position is normalized/clamped and restored bounded across restart/resize/split. Same Store/latest-only; no new scheduler. Future owner: F9/F11.

## 068E — Overlay TayTech gate
Under pressure, reduce rendering/animation/visual refresh before acquisition or science. Overlay must not serialize JSON per frame or duplicate telemetry state. Physical RK3326 gate compares overlay OFF/ON for CPU, render count, allocations and ECU→state latency. Future owner: F11/HW-001.

## 068F — One SessionBundle
Human flow becomes one exportable package for a selected immutable session plus manifest/schema/hashes, Learning snapshot at export, full current Map K, full current Curve K and Calibration Identity/geometry. Historical session facts and export-time snapshots remain distinguishable. Future owner: F11.

## 068G — Export-time Map + Curve capture
When ECU/identity is available, `Exportar pacote` performs a composed **read-only** acquisition through the existing serial arbiter: full Map K + full Curve K + geometry/identity, completed before manifest sealing. Offline fallback uses last confirmed snapshot with `snapshotFresh=false`, `snapshotSource=LAST_CONFIRMED`. DOM/UI cache is never physical truth. Future owner: F2/F3 APIs + F11.

## 068H — Learning snapshot in same bundle
Bundle includes serializable current Learning at `learningSnapshotAt`: petrol reference, calibration-bound CNG evidence, provenance, uncertainty/summaries and schema version. Import classifies compatibility; inclusion never means automatic activation. Future owner: F4 + F11.

## 068I — Historical session is not current snapshot
Use explicit namespaces: `SESSION_RECORDED_AT`, `SESSION_CLOSE_SNAPSHOT`, `EXPORT_TIME_CURRENT`, `LAST_CONFIRMED`. Current calibration exported today must never be relabeled as the historical calibration of an old session. Future owner: F11.

## 068J — Bundle identity, dates and manifest
Minimum fields: `bundleId`, `sessionId`, `sessionStartedAt`, `sessionEndedAt`, `exportedAt`, `learningSnapshotAt`, `mapCapturedAt`, `curveCapturedAt`, `timezone`, `schemaVersion`, app version/commit, ECU/calibration/geometry fingerprints, map/curve hashes, `snapshotFresh`, `snapshotSource`, `bundleHash`. Persist unambiguous ISO-8601 offset/UTC; UI may localize. Future owner: F11.

## 068K — Single import with explicit classification
Flow: choose package → validate size/schema/hash → read manifest → classify components → preview → human confirm → result per component. States: `ACTIVE_COMPATIBLE | PRIOR_ONLY | OBSERVATIONAL | REJECTED_INCOMPATIBLE | CORRUPT`. Session remains immutable; petrol may become compatible prior; CNG only active when identity policy allows it. Future owner: F4 099–101 + F11.

## 068L — Tools surface
Reuse one existing route with local Store contexts/tabs: `Sessões | Logs | Flutuante | Diagnóstico`. Map K, Curve K, Predictor and OBD remain first-class destinations. No second Router/Scheduler. 1280×720 uses 58/42 list/detail, 16dp gutter/padding; 1024×600 stacks detail without shrinking touch targets. Future owner: F9.

## 068M — Tools > Sessions
Rows expose local start, duration, size, integrity and bundle/export indicator. Detail exposes start/end, duration, schema, size, fuels, last export and snapshot provenance. Actions: `Exportar pacote` primary; `Importar pacote`, `Ver detalhes`; isolated destructive `Excluir sessão`. Progress states are real producer states only: reading calibration → capturing learning → assembling → hashing → complete/partial/fail. Future owner: F9 + F11.

## 068N — Tools > Logs
Filters: category, level, period; minimum categories when present: Sistema, MP48, Learning, Writer/ECU, UI, Sessão, OBD. Actions: export filtered logs, create diagnostic package, clear selection/period with destructive confirmation as needed. Log view/export stays outside telemetry hot path. Future owner: F9 + F11.

## 068O — Per-cell Map Draft
Canonical `MapDraftItem` owns `row`, `column`, `before`, `target`, `delta`, `source`, `validationState`. A single Draft may hold different deltas simultaneously (for example +5%, +2%, -5%, +3%). `selectedCells + oneGlobalDelta` is not Draft truth. Future owner: F8.

## 068P — Individual and multi-select coexist
Tap one cell opens its own editor without a selection mode. `Selecionar várias` enables tap/drag/row/column batch selection. Leaving multi-select does not erase already prepared DraftItems; later cells may receive different targets. Future owner: F8 + F9.

## 068Q — RPM never locks edit intent
With known current geometry, user may inspect/select/nudge/edit/review Draft at any RPM/condition. This never removes immediate pre-write validation, Calibration Identity, mutation barrier, ACK/readback/recovery or other writer gates. Future owner: F8 + F9.

## 068R — Owner-requested hard target ranges
Map K target: `120 ≤ K ≤ 200`. Curve/MUL_ACT factor target: `0.7 ≤ factor ≤ 2.0`. Apply at UI input → Draft validator → pre-write validator. **No silent clamp.** Current outside range remains visible as `OUT_OF_OWNER_RANGE`; never falsify current or autocorrect it. Factor `1.0` is neutral/1×, not a scientific ceiling/plateau. Future owner: F8 + F9; physical protocol encoding must be revalidated before writer use.

## 068S — Measured equivalence error is not K adjustment
Never show ambiguous `Correção +11%`. Separate `Erro de equivalência` (Petrol Inj. on CNG vs petrol reference, ms and %) from `Ajuste K proposto` (`K atual → K alvo` and delta). If StepPolicy exists, `K alvo ideal` and `próximo K a aplicar` are distinct. Future owners: F4/F6 semantics, F8 review, F9 inspector.

## 068T — Learning cell inspector
Order: physical cell/range → petrol reference + quality/knownness → observed Petrol Inj. on CNG → difference ms + signed error % → evidence/provenance/state → current/proposed K semantics → `K atual`, ideal target and next K when available → `Preparar correção` in the same route. Observed/derived/predicted remain visually distinct. Future owner: F4 projection + F9.

## 068U — Learning collection explains what/why/what is missing
Live state is one of `Coletando referência gasolina | Comparando GNV | Aguardando condição | Revalidando`. When not collecting, show a material human reason from backend; technical detail is secondary. Never invent progress from arbitrary counts when no real sequential-stop progress exists. Every material backend reason maps to a human next action or `aguardar/dirigir normalmente`. Future owner: F4 states/reasons + F9.

## Current gaps confirmed at freeze time

These are evidence for future owners, not permission to fix them here:

- Android overlay currently exposes cell/STFT and WebView floating telemetry exposes GAS/cell/freshness; both differ from 068C.
- Current floating WebView component subscribes to the existing Store (good) but is not the final Android external overlay contract.
- Current SessionRecorder exports immutable session ZIP only; LearningArchive exports `.omegas` separately. Canonical SessionBundle does not exist yet.
- Current `map-editor.js` allows up to 144 selected cells, uses K range 100–255, and clamps loaded rows. That does not satisfy 068O/068R or the later 1–16 Draft/writer contract.
- Current MapEditor has per-cell target overrides, which is reusable evidence, but it is not yet the canonical persistent `MapDraftItem` model.

All future implementation must keep one Store/Router/Scheduler and all ECU write safety invariants.
