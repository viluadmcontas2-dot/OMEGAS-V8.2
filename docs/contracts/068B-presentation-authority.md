# 068B — Presentation authority freeze

Status: contract only. This file does not authorize UI rebuild, serial reads, or ECU writes.

| Human field | Typed producer | Store / projection | Surface | Freshness / knownness |
|---|---|---|---|---|
| RPM | `CanonicalEvidence.frame.rpm` via `TelemetryStateStore` / `HubStatus` | single UI Store telemetry/status projection | Dashboard, Learning context, future canonical Overlay | unknown/stale must render as unavailable; never invent zero |
| Petrol Inj. | `CanonicalEvidence.frame.petrolMs` via `TelemetryStateStore` / `HubStatus` | single UI Store telemetry/status projection | Dashboard, Learning context, future canonical Overlay | same telemetry freshness authority; invalid/unknown is unavailable |
| Fuel | `CanonicalEvidence.frame.fuel` / native telemetry state | single UI Store telemetry/status projection | status strip, Learning context, future Overlay | human projection only: Gasolina, GNV, Transição/Desconhecido |
| Collecting state | Kotlin Learning `SampleDecision` / Learning projection | single UI Store `learningDecision` / `learningStatus` | Learning and future Overlay | UI never infers from RPM, route, timers, colors, or frame counts |
| Learning evidence / comparison | Kotlin Learning store and revisioned snapshot | `LearningUiSnapshotAssembler` cached by persisted revision → UI Store | Learning, Suggestions, evidence disclosure | projection may be stale/unavailable explicitly; navigation never creates evidence |
| Calibration state | Kotlin calibration authority / confirmed readback state | existing calibration projection → UI Store | Map, Curve, Suggestions | current/known identity required where owner contract demands it |

## Boundary

- UI may render state and emit explicit human intent.
- UI may not parse MP48, own scientific math, touch USB directly, or write ECU directly.
- Router/navigation never starts a scientific session, calls `ingest`, or owns a scientific heartbeat.
- Learning screen does not call Map/Curve writer entry points.
- Tools may request diagnostic/export actions through the native API, but do not own MP48 serial access. The current self-test only builds/checks protocol frames in memory.
- Native overlay is observational. Its current cell/STFT content is legacy presentation and is **not** the final 068C content contract; future F9/F11 owners must adapt it without creating a second Store/Scheduler.
- Heavy Learning UI derivation is revision-cached; the bridge still reads the persisted snapshot file when queried, so zero UI disk I/O is **not** claimed here.

Future implementation owners remain F9 200–202 / 213–219 and the 068C–068E overlay bindings. All ECU mutation invariants remain unchanged.
