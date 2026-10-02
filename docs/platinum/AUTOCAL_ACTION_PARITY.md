# Ômegas Platina — AutoCal action parity matrix v1

This is the **product-facing completion boundary** for AutoCal. Forensics only continues when an item below is PARTIAL and blocks a real product behavior.

| Behavior | Status | Platina contract |
|---|---|---|
| Enable acquisition | PROVEN | `AUTO_CAL_ENABLE 0x014A = 1`, ACK + readback |
| Disable acquisition | PROVEN | `AUTO_CAL_ENABLE 0x014A = 0`, ACK + readback |
| Readquirir gasolina | PROVEN | native mode `0x01`, explicit user action, ACK + petrol acquisition readback |
| Readquirir GNV | PROVEN | native mode `0x02`, explicit user action, ACK + gas acquisition readback |
| Reset all acquisition | PROVEN | native mode `0x04`, explicit user action, ACK + petrol/gas acquisition + `MUL_ACT` readback; broad effect observed |
| Manual AutoMatch | PROVEN | native mode `0x08`, explicit user action only + valid `MUL_ACT` before/after bracket |
| Finish AutoCal | PROVEN_HOST_BEHAVIOR | copy MAX_AUTOMATCH to NUM_AUTOMATCH_EXECUTED, 100 ms settle, readback; does **not** disable `AUTO_CAL_ENABLE` — pause/disable is the stop proof |
| Finish AutoMatch | PROVEN_HOST_BEHAVIOR | same copy without final 100 ms settle |
| Reset K | PROVEN | 30 × `MUL_ACT 0x0161[index] = 0x4000`, full readback |
| Point reacquisition | PROVEN | complete petrol/gas 18-point masks + one `01 24 05 2A` commit + fuel-scoped acquisition readback |
| Native AutoMatch | PROVEN_OBSERVED_AUTONOMOUS | ECU-owned; host only observes, brackets, explains and records |
| Sample acceptance internals | PARTIAL_NOT_BLOCKING | never reimplemented as host writer |
| 18→30 native reference internals | PARTIAL_NOT_BLOCKING | consume ECU vectors, do not synthesize authoritative replacements |
| Exact firmware AutoMatch formula | PARTIAL_NOT_BLOCKING | lab/predictor only; cannot trigger writes |

### Command-specific readback

`ACK` alone never makes a mutation `CONFIRMED`. Readback must come from the surface
the command can affect: `AUTO_CAL_ENABLE` for start/pause, petrol/gas acquisition
fields for resets and point reacquisition, `NUM_AUTOMATCH_EXECUTED` for Finish, and
`MUL_ACT` for Manual AutoMatch/Reset K. Reset All reads both acquisition sides plus
`MUL_ACT` because the canonical Portmon captured a broad effect. A valid scoped readback
proves the post-command state was actually read; it does not invent a universal zeroing
rule for selective reset modes whose exact firmware effect remains unproven.

## Readiness rule

An unknown is allowed at release only when the ECU remains the authority and the missing internal detail cannot change a host mutation command or mislead the primary UI.

A mutation path is release-blocking when address, encoding, payload semantics, ACK handling or readback is not evidence-backed.

## UX rule

The primary AutoCal screen presents **state and intention**, not reverse-engineering terminology. Technical evidence stays available on demand. Inconclusive evidence must remain visibly inconclusive.

## Safety invariant

Native AutoMatch can never call a K/Map writer. Predictor and reconstructed math can prepare analysis only. Every host mutation requires explicit operator intent and current-session validation.
