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


## Complete matrix and controlled comparator (PR #117)

- Full Python matrix: **17** benchmark tests each in CodSpeed CPU Simulation and CodSpeed WallTime (15 original wizard benchmarks + 2 matched-run Portmon implementations).
- Actual JavaScript matrix: **4** walltime exec targets from `codspeed.yml` (two LearningModel grid workloads and two AutoCalUxModel projection algorithms). All four were executed first as non-timed correctness smoke tests via Node 22.
- Total: **38** instrument results in one CodSpeed run, https://app.codspeed.io/viluadmcontas2-dot/OMEGAS-V8.2/runs/6abf13354408cbe43cda1374. This run completed with all three CodSpeed jobs green: https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/36954508140 .

The paired benchmark in `benchmarks/test_bench_portmon_pair.py` includes the exact pre-optimization `iter_events` implementation (function renamed only), loaded beside the current production function. Both consume identical 2,000-transaction synthetic log lines, and collection aborts if their event arrays differ. Crucially, the baseline and optimized variants are measured in the **same CodSpeed job and the same runner**, for each instrument:

| Matched-run Portmon event parser | Original | Optimized | Reduction in modeled/measured time |
| --- | ---: | ---: | ---: |
| Simulation | 469.4 ms | 360.7 ms | approximately **23.2%** |
| WallTime | 50.3 ms | 38.9 ms | approximately **22.7%** |

The paired numbers substantiate a reduction **for this CPU-bound offline parser workload**, not a physical AutoCal/ECU or WebView latency improvement. Walltime on regular GitHub-hosted runners remains subject to scheduling variance; the same-run design eliminates the previously noted cross-CPU baseline/head confounder in this particular comparison.

### Isolated Learning/AutoCal instrumentation

First completed four-target script walltime measurement in CodSpeed run https://app.codspeed.io/viluadmcontas2-dot/OMEGAS-V8.2/runs/6abf127fa0c264d9f80d73b3 :

| Complete exec script | Walltime |
| --- | ---: |
| 12×12 Learning grid, 120 comparisons | 199.1 ms |
| 12×12 Learning grid, 1,500 comparisons | 201.9 ms |
| AutoCal 18-band strip, 30,000 iterations | 738.6 ms |
| AutoCal 18-point reference fingerprint, 30,000 iterations | 2.3 s |

These are **entire process-script timings** including Node VM/startup and repeated calls, not single-refresh user latency. The later 38-result run has different reported absolute JS values under another hosted runner, illustrating why per-run environment provenance matters. JavaScript product code was not changed.

### CI compatibility and promotion

The current Platina branch removed its inherited APK-generation workflow while retaining an earlier test that assumed gated APK build steps must exist. The scoped adjustment in `tests/ui/autocal-ci-no-apk.test.cjs` recognizes either safe configuration: no APK build configuration at all (and rejects hidden assemble/bundle commands), or the historical three named APK stages explicitly gated by `workflow_dispatch && inputs.build_apk == true`. This is **test-only** and adds no APK build path.

The performance workflow checks relevant PRs targeting `OmegasPlatina` and post-merge performance-related pushes to that branch. The canonical Platina source branch is not directly edited, and promotion is via reviewed PR #117: https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/pull/117 .

No same-SHA Android device render, physical ECU readback, real host bridge, or UI frame-pacing claim is included in this offline performance tranche.
