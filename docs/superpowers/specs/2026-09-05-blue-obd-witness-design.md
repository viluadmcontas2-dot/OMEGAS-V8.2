# OMEGAS Blue — OBD Witness Design

## Purpose
Use ELM327/OBD as an optional evidence sidecar that accelerates confidence in the existing Blue causal model without becoming a second calibration engine, without writing the ECU, and without expanding the operating model beyond the variables the OMEGAS already uses.

## Non-negotiable boundary
- Blue/MP48 remains the primary model and works without OBD.
- OBD never computes or writes Map K or Curve K targets.
- OBD contributes one production signal: STFT.
- Every accepted STFT reading is paired to an MP48 frame.
- The only operating-condition variables used for OBD matching are MP48 RPM, MP48 MAP and MP48 Petrol Inj.
- Fuel label and calibration-state id are metadata/state boundaries, not extra matching dimensions.
- No coolant, load, throttle, MAF, vehicle speed, IAT or other PID participates in the evidence model.

## Architecture
### 1. ELM transport
Own Bluetooth/RFCOMM and ELM command lifecycle only. It exposes explicit connection stages:
`PERMISSION -> RFCOMM -> ELM_INIT -> PROTOCOL -> STFT_READY -> LIVE`.
Failures are bounded, retryable and visible.

### 2. STFT reader
The live polling loop prioritizes PID `01 06` (STFT Bank 1). `01 00` may be used during handshake to prove protocol/PID support, but it is not a learning signal. No broad scanner PID loop is required.

### 3. Temporal matcher
For each STFT response, use the response observation time and select the nearest valid MP48 telemetry frame from the in-memory history. The pair is accepted only when the temporal skew is inside a configured small bound. The stored pair contains STFT, MP48 RPM, MAP, Petrol Inj., fuel, calibration state and timestamps.

### 4. OBD evidence store
Store paired observations by calibration state and fuel. Gasoline and GNV are never pooled. Region compatibility is evaluated only in RPM/MAP/Petrol Inj. space.

### 5. Gasoline-relative residual
For a GNV pair, find a compatible gasoline STFT reference and calculate:

`obdResidualPp = stftGnv - stftGasolineReference`

If no compatible gasoline reference exists, the observation remains provisional. Direct STFT-vs-zero is allowed only as a live diagnostic label, never as the equivalence rule.

### 6. Blue witness adapter
Translate OBD evidence into one of four outcomes:
- `SUPPORTS`
- `CONFLICTS`
- `INSUFFICIENT`
- `UNAVAILABLE`

When OBD and Blue agree in correction direction, Blue confidence may increase faster. When they conflict, OBD cannot increase confidence and the conflict is surfaced. OBD never changes the computed K target.

### 7. Calibration-state boundary
A confirmed Map K or Curve K write/readback opens a new OBD evidence epoch. Pre-write and post-write STFT evidence are never mixed.

## Data flow
`ELM327 STFT -> timestamp -> nearest MP48 frame -> RPM/MAP/Petrol Inj. region -> gasoline/GNV evidence -> gasoline-relative residual -> OBD Witness -> Blue confidence`

The write path remains completely separate:
`Blue proposal -> human review -> writer -> ACK/readback`.

## Connection strategy
Keep the existing OBD screen as a separate area. Replace generic `CONECTANDO/ERRO` behavior with a stage-aware state machine. The first implementation optimizes for getting one reliable STFT stream instead of implementing a generic Car Scanner feature set.

## UI
OBD remains a separate screen. Primary live content:
- STFT now
- matched MP48 RPM
- matched MP48 MAP
- matched MP48 Petrol Inj.
- fuel
- gasoline STFT reference when available
- GNV STFT
- residual OBD
- witness state: supports/conflicts/insufficient/unavailable
- connection stage/error

Learning/Main UI may show only a small OBD witness status; it does not duplicate the OBD screen.

## Testing strategy
TDD slices:
1. connection state machine and bounded retry
2. STFT PID parsing
3. nearest MP48 temporal matching
4. rejection on excessive skew
5. gasoline/GNV state separation
6. gasoline-relative residual
7. calibration-state epoch split
8. SUPPORTS/CONFLICTS confidence behavior
9. proof OBD cannot reach writer APIs
10. full JVM/lint/APK on exact SHA

## Audit of the design
### Over-complexity check
Removed coolant, load, throttle, MAF, speed, IAT, LTFT and generic scanner behavior from the learning path. They are unnecessary for the approved first implementation.

### Authority check
Only Blue computes correction targets. OBD produces evidence status only.

### Scientific contamination check
Gasoline/GNV are stored separately, comparison is region-compatible in RPM/MAP/Petrol Inj., and calibration-state boundaries prevent before/after mixing.

### Timing check
STFT is paired to the nearest historical MP48 frame rather than whatever telemetry happens to be current after the serial response returns.

### Failure-mode check
OBD failure degrades to `UNAVAILABLE`; Blue continues to function. Connection attempts are bounded and diagnosable.

### Scope check
This is one coherent subsystem enhancement: reliable STFT acquisition -> temporal pairing -> gasoline-relative witness -> confidence acceleration. No unrelated scanner functionality is included.

## Acceptance summary
The feature is complete when OBD can connect reliably, stream STFT, pair each accepted observation to MP48 RPM/MAP/Petrol Inj., build gasoline-relative residuals by calibration state, accelerate Blue confidence when evidence agrees, refuse to accelerate on conflict, and remain unable to write the ECU directly.