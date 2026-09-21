# OMEGAS Amarelo — AutoCAL UX Design

**Date:** 2026-09-21  
**Program:** #72  
**WorkUnit:** OMEGAS-AMARELO-WU-005  
**Issue:** #77  
**Branch:** `work/omegas-amarelo-foundation`

## 1. Purpose

Build an AutoCAL surface that preserves the proven operational structure of the original ProgBase while applying the owner's CUSTOMROM + Omega Dev human-experience criteria.

This is **not** a visual clone of ProgBase and **not** a redesign of the current Verde cockpit.

The original defines:
- what exists;
- what the ECU owns;
- what is polled;
- what feeds each curve/point/band/state;
- which actions cause native state transitions.

The OMEGAS Amarelo UX defines:
- what the operator needs to see first;
- how confidence, age and validity are communicated;
- how technical detail is progressively disclosed;
- how the same native truth becomes safer and easier to use at 1280×720.

## 2. Product authority

### Native technical authority
ProgBase 4.2.0.6 + raw Portmon evidence.

The UI must not create a second mathematical authority for native AutoCAL.

### Human UX authority
Repo-copied principles derived from:
- Blueprint Premium UI/UX — Método CUSTOMROM reutilizável;
- Método aplicado — Omega Dev 4.0 Premium UI/UX.

Binding principles:
- human work first;
- one state authority;
- intention -> action -> consequence;
- normality compact;
- problem gets space;
- human summary first;
- technical detail on demand;
- semantic color;
- preserve context;
- automotive readability.

## 3. Original ProgBase semantics to preserve

The following original concepts are treated as product semantics, subject to byte/consumer proof in WU-001/WU-002:

- `TAutoCalDM` as native data/ECU-side model surface;
- `TAutoCalUI` as projection/action surface;
- PetrolCurve;
- GasCurve;
- PetrolPoint;
- GasPointPrev / GasPoint where proven;
- current/run point;
- K line / `MUL_ACT`;
- acquisition zones/bands;
- AutoMatch counter/state;
- ação principal contextual Auto Calibration: um único botão ativa/desativa o modo; a lógica original de AutoCAL + AutoMatch é preservada sem copiar o checkbox do ProgBase;
- reset petrol/gas/all;
- finish AutoCAL;
- polling of live telemetry independently from slower AutoCAL vectors.

Current proven action pattern:
- reset petrol -> action code 1;
- reset gas -> action code 2;
- reset all -> action code 4;
- manual AutoMatch -> action code 8;
- shared dispatcher -> native bridge -> ECU/native state -> refresh/projection.

Operator-observed original UX contract:
- the original uses one Auto Calibration control; Amarelo preserves that one-control behavior but may use a button instead of a checkbox;
- activating it enables Auto Calibration and automatic AutoMatch together;
- deactivating it disables both directly; no redundant confirmation modal;
- the wire control remains AUTO_CAL_ENABLE; do not invent a second AutoMatch enable command.

Unknown lower-level side effects remain UNKNOWN until WU-001 proves them.

## 4. Screen information architecture

The primary AutoCAL route has four levels of importance.

### Level A — dominant operational surface

Occupies most of the visible viewport.

Contains:
1. human native state;
2. primary MAP × Petrol Injection graph;
3. live/current point;
4. immediate next action when needed.

Nothing else may visually compete with this layer during normal operation.

### Level B — global correction surface

Compact secondary graph immediately associated with the primary graph.

Shows:
- current `MUL_ACT` / Curve K;
- previous curve only around a proven native change event;
- highlighted changed range when supported;
- no invented target/suggestion curve.

### Level C — acquisition coverage

Compact strip or overlay, not a second dashboard.

Shows:
- acquired/not acquired regions;
- current region/band when proven;
- recent valid acquisition activity;
- gaps that need operator attention.

It must not expose internal counter values as the primary visual language.

### Level D — technical detail

Collapsed by default.

May expose:
- raw object names;
- addresses;
- buffers;
- counters;
- raw bytes;
- snapshot/hash;
- timing;
- provenance;
- decoder state;
- source freshness.

This layer exists for diagnosis and evidence, not routine driving.

## 5. Primary graph contract

### Axes

- X: Petrol injection time [ms].
- Y: MAP [bar].

This follows the original ProgBase mental model.

RPM is **not** promoted to an axis because the native AutoCAL curve does not currently prove RPM as an output dimension.

RPM may appear as:
- live metadata;
- filter;
- diagnostic annotation;
- correlation input only in a future WorkUnit explicitly scoped after WU-001/WU-002 prove the native relationship.

### Layers

The graph supports only evidence-backed layers:

1. **Petrol reference**
   - source: proven native/reference producer;
   - visually stable;
   - never hidden merely because a subsequent partial snapshot is missing fields when a valid current reference is still retained.

2. **Gas/current native curve**
   - source: proven native gas/reference producer;
   - visually distinct but equal in hierarchy to petrol reference.

3. **Previous native curve**
   - only after a proven state transition;
   - faded;
   - removed after its comparison purpose expires.

4. **Live point — AGORA**
   - source: fresh live MP48 telemetry;
   - plotted at `petrol_ms, MAP`;
   - must continue updating at the live telemetry cadence independent of slow native-vector reads;
   - stale point disappears or changes state explicitly; never silently freezes as current.

5. **Acquisition markers/bands**
   - only if bit/zone/consumer semantics are proven;
   - otherwise hidden from primary graph until WU-002 resolves them.

## 6. Fade semantics

Fade is semantic, never decoration.

### Time
Older transient points fade relative to current evidence.

### Validity
Invalid/rejected data is not shown as normal evidence.

### Freshness
A current native/reference curve remains visually authoritative while valid. A stale source is labeled stale instead of merely dimmed into ambiguity.

### Previous revision
Previous Curve K or previous gas state is faded after a proven AutoMatch/native mutation so the operator can see what changed.

### Confidence
Only OMEGAS-derived analytical overlays may encode confidence through opacity. Native ECU truth must not be made visually “less true” because the app has low local confidence.

## 7. Human state model

Primary states are human-oriented projections of proven native/runtime facts.

Allowed top-level states:

- **Aguardando ECU**
- **Pronto para AutoCAL**
- **Coletando gasolina**
- **Coletando GNV**
- **Ajustando na ECU**
- **Verificando ajuste**
- **AutoCAL concluído**
- **AutoCAL pausado**
- **Dados parciais**
- **Conexão perdida**
- **Estado nativo inconclusivo**

State names may be refined during WU-002 but must map one-to-one to evidence-backed conditions.

Do not surface technical names such as `READY_PARTIAL`, `TAutoCalDM`, or `VECT_AUTOCAL_U8_1` as primary copy.

## 8. Action model

Normal surface shows at most one primary action and one secondary action group.

Examples:
- Iniciar AutoCAL;
- Pausar AutoCAL;
- Continuar;
- Finalizar AutoCAL.

Potentially destructive or data-reset actions live under deliberate secondary disclosure:
- reset gasoline acquisition;
- reset GNV acquisition;
- reset all;
- reset K-factor, if its native semantics remain part of the Amarelo product.

Every action must show:
1. current state;
2. intended action;
3. native consequence expected;
4. explicit action with immediate consequence; reserve confirmation only for genuinely destructive/irreversible cases;
5. ACK/readback/proven result;
6. failure without false success.

## 9. AutoMatch event UX

A proven native AutoMatch event is surfaced as an event, not as an OMEGAS recommendation.

Example human projection:

> A ECU realizou a 3ª correção automática.

Then show:
- previous Curve K;
- current Curve K;
- changed range;
- event timestamp/provenance;
- readback status.

The app must not claim why the firmware chose the change unless the firmware logic is actually proven.

## 10. Acquisition coverage

The original uses acquisition zones/bands and dedicated polling.

The Amarelo should preserve the usefulness while reducing visual noise.

Preferred presentation:
- one compact horizontal coverage rail aligned with the primary graph;
- acquired regions visually solid;
- active region highlighted;
- missing regions muted;
- technical counters hidden by default.

If WU-002 proves 18 physical acquisition bands + four zone groups, the UI may present 18 segments grouped into four zones.

If that relationship remains incomplete, the UX must not invent it.

## 11. Curve K secondary graph

The secondary graph is a compact, always-available context surface.

X:
- native K-factor axis / petrol-injection-time axis exactly as proven.

Y:
- decoded native factor value.

Shows:
- current `MUL_ACT`;
- optional previous curve after proven mutation;
- active bracket/range only when evidence supports it.

No smoothing that changes native values.

Visual interpolation between samples is allowed only for rendering; raw sample points remain inspectable.

## 12. Performance contract

The screen must preserve the original's architectural advantage: fast live telemetry plus slower background AutoCAL reads.

Initial evidence-backed cadence classes:

- live MP48 `48 01 49`: median ~46.57 ms in PortmonAUTOCAL;
- native AutoCAL vector family: ~2.01 s observed;
- RV vectors `0x018D/0x018E`: ~4.05 s observed.

UX consequences:
- AGORA moves independently of slow vector refresh;
- vector refresh cannot block the live cursor;
- DOM/SVG updates are incremental;
- curve data changes only when source revision changes;
- labels are not rebuilt on every live frame;
- offscreen technical detail does not redraw the main graph;
- reconnect does not create scientific confidence.

Exact scheduler policy belongs to WU-006.

## 13. Layout for 1280×720

Normal landscape layout:

```text
┌──────────────────────────────────────────────────────────────┐
│ AutoCAL   [native state]                    [primary action] │
├───────────────────────────────────────────────┬──────────────┤
│                                               │ compact      │
│     MAP × Petrol injection time               │ human state  │
│                                               │ / next step  │
│     Petrol reference                          │              │
│     Gas curve                                 │              │
│     AGORA                                     │              │
│                                               │              │
├───────────────────────────────────────────────┴──────────────┤
│ acquisition coverage rail                                    │
├──────────────────────────────────────────────────────────────┤
│ Curve K / MUL_ACT                                            │
├──────────────────────────────────────────────────────────────┤
│ [Technical details ▾]                   [secondary actions ▾]│
└──────────────────────────────────────────────────────────────┘
```

The right-side human state panel collapses when no explanation is needed, giving more space back to the graph.

No scrolling should be required for the dominant operational state at 1280×720.

## 14. Failure/degraded states

### Partial native snapshot
Keep last valid current reference if provenance/revision allows it.
Explain missing fields in technical/detail context.
Do not blank the main graph unnecessarily.

### Live telemetry stale
Freeze is forbidden.
Hide/mark AGORA as stale after the agreed freshness threshold.

### USB reconnect
Reconnect changes transport generation, not scientific meaning.
Native state is reacquired.
UI does not claim a new independent learning session.

### Unsupported module variant
Do not coerce 18/30 element structures.
Show a clear degraded message with module/version evidence.

### Decoder contradiction
Prefer `Estado nativo inconclusivo` over invented fallback values.

## 15. Relationship to OMEGAS learning

AutoCAL and OMEGAS learning coexist but remain separate authorities.

AutoCAL screen shows what the native ECU is doing.

The 144-node learning system may consume proven native revision events/provenance only through an explicitly scoped integration WorkUnit after WU-004 is proven, but:
- it does not rewrite the AutoCAL graph;
- it does not invent RPM into native curves;
- it does not reinterpret a native curve as local Map K truth;
- its residual/local conclusions live in Learning/Map surfaces.

A future explicitly scoped cross-surface integration may say:
> AutoCAL global atualizado. Mapa local sendo revalidado.

It must not merge the two visual models.

## 16. Current Verde harvest classification

As of `OmegasVerde@08666dbf0b7bef935950a810776a4b8e89ae0b1b`:

### HARVEST
- real Portmon compact fixture;
- real MP48 replay contract;
- native cadence observations;
- idea of live AGORA + reference curve coexistence;
- native projection classes as reference for current known fields.

### REVALIDATE
- current `autocal-cockpit.js` states/layout;
- band/correlation presentation;
- session strip inside AutoCAL;
- manual snapshot UX;
- source/freshness messaging.

### REJECT as direct product template
- current cockpit information density;
- technical state dominance;
- repeated manual “consult ECU” as normal operation;
- any JS-derived science acting as source of truth;
- any layout that makes session/debug/detail compete with the primary graph.

## 17. Verification requirements

Before implementation is accepted:

1. primary graph renders from real replay data;
2. AGORA advances at live cadence while curves refresh more slowly;
3. reference remains visible through partial reads when still valid;
4. native curve mutation produces a visible before/after event;
5. stale live telemetry never masquerades as current;
6. 1280×720 screenshot proves no-scroll dominant operation;
7. write flow proves immediate action + ACK/readback; no duplicate confirmation for an already explicit write;
8. both raw Portmon sources remain provenance anchors;
9. no UI-generated science changes native values;
10. performance budgets from WU-006 are met.

## 18. Non-goals

- pixel-copy of ProgBase;
- immediate implementation in OmegasVerde;
- APK generation;
- automatic ECU writes;
- speculative firmware algorithm reproduction;
- Map K/RPM interpolation inside the AutoCAL graph;
- session-count confidence.

## 19. Done

This design is ready for implementation planning when:
- WU-001/WU-002 provide enough proven field/action semantics for every primary visual element;
- owner approves this written UX contract;
- #77 links to this spec;
- implementation plan is written from this spec, not from chat memory.


## Anti-friction rule — explicit write is already consent

For ordinary, intentional calibration actions:
- editing/preparing a K target is not a write;
- the single CTA `Aplicar alterações` is the user's consent to write;
- after that CTA, the product must execute and show execution/readback state;
- do not ask “tem certeza?” again;
- do not add a second review/confirm screen just to repeat the same intent.

A separate confirmation is reserved only for actions whose primary purpose is destructive/irreversible data loss, not for normal AutoCAL enable/disable or an explicit Curve K write.
