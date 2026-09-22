# AutoCal fixture provenance audit — 2026-09-22

Authority: GitHub branch `OmegasVerde`; original artifacts are evidence sources, never OMEGAS output.

## Original artifact hashes

- ProgBase 4.2.0.6 — `Copy of ProgBase (3).exe` — SHA-256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`.
- Portmon AutoCal raw — `PortmonAUTOCAL (1).LOG` — 162,700,984 bytes — SHA-256 `4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b`.
- Portmon AutoCal zip — `PortmonAUTOCAL (1) (1) (1).zip` — SHA-256 `a927795da5800baef53d498273f4e210bc814c2e0e3649572e6de42781073b47`.
- Lognovo raw — `PortmonLOGNOVO.LOG` — 149,911,521 bytes — SHA-256 `43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64`.
- Lognovo zip — `PortmonLOGNOVO (1)(2).zip` — SHA-256 `6879fa2a7931d22c207cd7fa47dffb59e1df0fe1de216e34e3f11e0c08cc1c17`.

Extraction/parsing authority for Portmon transactions: `scripts/omegas/portmon_parser.py`. Full-corpus packaging: `tools/omegas/build_portmon_full_corpus.py`.

## Fixture classification

| Fixture / evidence | Source | Extractor / chain | Class | Oracle use | Known consumers |
|---|---|---|---|---|---|
| `tests/fixtures/portmon-autocal-cycle-v1.json` | PortmonAUTOCAL original, raw+zip hashes embedded | compact transaction selection from original capture | ORIGINAL_DERIVED | allowed for captured wire/acquisition/live values | Android live replay; ProgBase consumer map; forensic contracts |
| `tests/fixtures/portmon-autocal-real-sample.json` | PortmonAUTOCAL original, raw hash embedded | passive sample from original capture | ORIGINAL_DERIVED | allowed only for bytes represented in the sample | real MP48 replay fixture contract |
| `evidence/portmon/full-corpus-manifest.json` | PortmonAUTOCAL original | `build_portmon_full_corpus.py` + `portmon_parser.py` | ORIGINAL_DERIVED | allowed as corpus provenance/coverage evidence | forensic/corpus checks |
| `tests/fixtures/portmon-lognovo-autocal-reference-v1.json` | Lognovo original + ProgBase original | exact first complete ACK for 0x014B/0x014C/0x0161/0x018D/0x018E; source line/event recorded | ORIGINAL_DERIVED | allowed for reference-curve byte/decoder/render parity | Android `autocal-reference-curves`; fixture contract |
| `fixtures/autocal/autocal_snapshot_complete.json` | no proven original-byte chain | legacy deterministic structural data | TEST_ONLY | **forbidden** as science/parity oracle | retained as compatibility asset; not consumed by current scientific Android render |
| `fixtures/autocal/autocal_snapshot_partial.json` | no proven original-byte chain | deliberately partial structural data | TEST_ONLY | **forbidden** as science/parity oracle | retained for unavailable/partial structural behavior |
| `fixtures/autocal/autocal_snapshot_shifted_equivalence.json` | synthetic mutation | explicitly `SYNTHETIC_NON_SCIENTIFIC` | TEST_ONLY | **forbidden** as science; visual geometry only | Android shifted-equivalence visual scenario; receipt carries classification |
| `tests/fixtures/progbase-autocal-consumer-map-v1.json` | ProgBase original + original Portmon evidence | binary consumer mapping tied to ProgBase SHA and Portmon hashes | ORIGINAL_DERIVED semantic evidence | allowed for producer/consumer identity; not a substitute for raw captured values | ProgBase parity contracts |
| `tests/fixtures/omegas-autocal-progbase-parity-v1.json` | original evidence + OMEGAS implementation inspection | comparison/classification artifact | OMEGAS_DERIVED | **forbidden** as independent oracle | parity status/traceability only |
| `tests/fixtures/autocal-forensic-lanes-v1.json` / `autocal-forensic-plan-v2.json` | test harness definitions | hand-authored lane plans | TEST_ONLY | not scientific data | forensic fan-out scheduler |

## Original byte coverage for the AutoCal cockpit

Counts below were obtained by parsing the exact raw files above with the canonical Portmon parser. Zero means the command is absent from that capture, not that the ECU value is zero.

| Field | Lognovo transactions | PortmonAUTOCAL transactions | Role |
|---|---:|---:|---|
| PETR_INJ_TBP 0x014B | 3 | 0 | common X axis for gasoline/GNV reference curves |
| MNFLD_PRESS_THD 0x014C | 3 | 0 | MAP thresholds / CurrentBand / acquisition geometry |
| MUL_ACT 0x0161 | 439 | 747 | native multiplier/reference |
| PETR_MNFLD_PRESS_RV 0x018D | 218 | 373 | gasoline reference curve Y |
| GAS_MNFLD_PRESS_RV 0x018E | 220 | 373 | GNV reference curve Y |
| PETR_INJ_TBUF 0x0162 | 437 | 745 | current gasoline acquisition X |
| MNFLD_PRESS_BUF 0x0163 | 437 | 745 | current gasoline acquisition Y |
| PETR_INJ_TBUF_GAS_PREV 0x015D | 437 | 744 | previous GNV acquisition X |
| MNFLD_PRESS_BUF_GAS_PREV 0x015E | 437 | 744 | previous GNV acquisition Y |
| PETR_INJ_TBUF_GAS 0x015F | 440 | 747 | current GNV acquisition X |
| MNFLD_PRESS_BUF_GAS 0x0160 | 440 | 747 | current GNV acquisition Y |
| NUM_BUF_UPD_PETR 0x015B | 437 | 745 | gasoline maturity counters |
| NUM_BUF_UPD_GAS 0x015C | 440 | 747 | GNV maturity counters |
| ACQUIRED_ZONES_PETROL 0x016F | 111 | 244 | gasoline acquired-zone mask |
| ACQUIRED_ZONES_GAS 0x0170 | 325 | 501 | GNV acquired-zone mask |

Important consequence: the compact PortmonAUTOCAL capture is sufficient for acquisition, maturity and zones, but it does **not** contain the two geometry reads 0x014B/0x014C. Those are present in Lognovo and are now versioned as exact original request/response bytes. No OMEGAS-generated log is required.

## Render gate policy

- `autocal-reference-curves` must decode the ORIGINAL_DERIVED Lognovo bytes through production `AutoCalProtocol`.
- `autocal-equivalence-shifted` is intentionally synthetic and must serialize `SYNTHETIC_NON_SCIENTIFIC / VISUAL_ONLY_NON_SCIENTIFIC` into its receipt.
- Legacy complete/partial snapshots are explicitly quarantined and cannot become scientific or parity oracles.
- CurrentBand must be rendered from original `MNFLD_PRESS_THD` plus fresh live MAP; the Android receipt records visibility and geometry.
- A green workflow badge is insufficient: instrumentation log, JSON receipt and screenshot remain required.
