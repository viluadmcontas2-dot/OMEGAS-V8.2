# OMEGAS-SPEED — isolated performance program

Fork point: `OmegasPlatina@117da4487b75ebce10aefbe77a74403c652daa3f`. All changes below live only in the `OMEGAS-SPEED` branch; no merge into OmegasPlatina or main.

## Committed instrumentation

- 15 pytest-codspeed Python scenarios imported from the setup wizard (Portmon, OBD × MP48 and synthetic adaptive learning). The standalone synthetic learning harness is included under `tests/` because it is absent from the Platina fork point.
- Four independent Node.js exec scenarios in `codspeed.yml` for LearningModel and AutoCalUxModel. **Prepared, not yet represented by the Python CodSpeed results below**.
- `.github/workflows/codspeed.yml` runs simulation on pushes to OMEGAS-SPEED only, with OIDC and no server/APK dependency.
- `tests/test_portmon_payload_lazy.py` checks six equivalence scenarios before the measured run; the original application's ECU-write path is unchanged.

## Offline Portmon: first isolated optimization

CodSpeed baseline on this branch: `4fbf1cb` (15 benchmark results, GitHub Actions success).
Optimized revision: `9a4b2a6` (six regression checks + 15 benchmark results, GitHub Actions success).

The only production edit is `scripts/omegas/portmon_parser.py::iter_events`. It keeps raw event detail until SUCCESS is read. If SUCCESS already contains a valid READ payload, the parser no longer performs a redundant earlier extraction; otherwise, it decodes the original event detail as before.

| CodSpeed Simulation workload | Baseline | Optimized | CodSpeed reported speed gain |
| --- | ---: | ---: | ---: |
| `test_iter_events` | 453.5 ms | 353.8 ms | +28.19% |
| `test_parse_transactions[200_tx]` | 51.7 ms | 41.4 ms | +24.83% |
| `test_parse_transactions[2000_tx]` | 495.7 ms | 402.3 ms | +23.21% |
| `test_summarize_gzip_log` | 547.0 ms | 449.6 ms | +21.67% |

CodSpeed reports four improved benchmarks and 11 unchanged:

- Baseline: https://app.codspeed.io/viluadmcontas2-dot/OMEGAS-V8.2/runs/6abf0cf0cbb1cd4088410507
- Head: https://app.codspeed.io/viluadmcontas2-dot/OMEGAS-V8.2/runs/6abf0d7b2b6e65edf9dc7ea4
- Comparison: https://app.codspeed.io/viluadmcontas2-dot/OMEGAS-V8.2/runs/compare/6abf0cf0cbb1cd4088410507..6abf0d7b2b6e65edf9dc7ea4

**Comparability caveat:** baseline ran on Intel Xeon Platinum 8573C and head on AMD EPYC 9V45. The CodSpeed simulation cache model reflects the physical CPU, so these gains are preliminary rather than hardware-controlled proof. Confirm with comparable runners and walltime before claiming a universal speedup.

## Safety and scope

This optimization concerns the passive/offline Python Portmon parser. It does not measure Android frame pacing, Kotlin/USB latency, actual ECU traffic or AutoCal responsiveness on a device. The Node Learning/AutoCal scenarios remain a separate baseline to execute. No changes to Predictor abstention, ECU authority, scientific parity, ACK/readback requirements or automatic ECU writes have been made. No new branch beyond OMEGAS-SPEED, no paid runner and no server are required by this change.
