# OMEGAS Verde — AutoCal Instrument UI Refinement

## Owner visual direction

The owner-provided visual reference is treated as a **hierarchy and interaction reference**, not as a scientific source and not as a pixel-copy target.

## Product objective

At 1280×720 the AutoCal screen should read first as an automotive acquisition instrument:

1. the acquisition curve is the dominant surface;
2. gasoline and GNV remain visually distinguishable;
3. AGORA remains on the same graph, never in a separate tab;
4. live RPM / Petrol Injection / MAP / LEVELS RAW remain visible in a compact rail;
5. state/session/actions remain available but do not push the graph below the fold;
6. point inspection is contextual and non-destructive;
7. technical RAW details remain available on demand.

## Scientific boundaries

Allowed on the dominant surface:
- native Petrol Inj. reference;
- native petrol MAP reference;
- native GNV MAP response;
- proven GNV-equivalent points;
- live AGORA telemetry when fresh;
- CurrentBand from proven MAP thresholds;
- LEVELS RAW only.

Not allowed:
- LEVELS percentage/litres/m³;
- synthetic acquired points;
- guessed interpolation where original semantics are not proven;
- automatic ECU write;
- presenting emulator USB state as physical MP48 connectivity.

## Visual hierarchy

- Primary: acquisition chart.
- Secondary: bottom live-telemetry rail.
- Tertiary: acquisition/session state and operator action.
- On-demand: point inspector, sessions, band detail, technical RAW.

## Verification

A visual refinement is not accepted from source inspection alone. Required:
- Node/UI contracts;
- canonical CI;
- Android WebView render at 1280×720;
- screenshot artifact;
- DOM assertions for AGORA/live rail/curve host;
- no regression of stale telemetry safety.
