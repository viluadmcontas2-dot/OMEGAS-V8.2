# OMEGAS-AMARELO-WU-003 — Replay real e GitHub Actions

Issue: #75
Estado: **IN_PROGRESS — replay infra PROVEN; LOGNOVO timing replay pending**

## Resultado observável
CI reexecuta contratos AutoCAL/MP48 a partir de fixtures compactos derivados dos dois logs crus.

## Requisitos
- source SHA + recipe + range;
- parser/runtime real;
- timing/cadência;
- artifacts de diagnóstico;
- mudanças docs-only não disparam build pesado;
- falha determinística em regressão.

## Harvest inicial
Reavaliar fixture/test adicionados no Verde em `6049a6f4...`.

## Não escopo
Release APK.

## Execution receipt — 2026-09-22

### Implemented
- deterministic raw builder: `tools/omegas_amarelo/build_portmon_replay_fixture.py`;
- dual-corpus manifest: `tests/fixtures/amarelo-portmon-replay-manifest-v1.json`;
- WU-003 test gate: `tests/test_amarelo_wu003_replay_kit.py`;
- lightweight Actions workflow: `.github/workflows/amarelo-replay-contracts.yml`;
- CI diagnostic artifact: `amarelo-replay-contract-report`.

### Proven AUTOCAL corpus
- raw SHA-256: `4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b`;
- compact replay: `tests/fixtures/portmon-autocal-cycle-v1.json`;
- 36,463 observed writes / 21 unique commands;
- cumulative-operation clock remains the binding timing rule;
- harvested replay fixture/test are unchanged in current Verde since their original commits.

### Proven LOGNOVO bytes
- raw SHA-256: `43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64`;
- control evidence: `tests/fixtures/portmon-lognovo-control-v1.json`;
- original-derived reference bytes: `tests/fixtures/portmon-lognovo-autocal-reference-v1.json`;
- reference fixture preserves exact original request/response bytes for PETR_INJ_TBP, MNFLD_PRESS_THD, MUL_ACT, PETR_MNFLD_PRESS_RV and GAS_MNFLD_PRESS_RV;
- its provenance explicitly states that original timing is **not** claimed.

### CI receipt
Workflow: **OMEGAS Amarelo replay contracts**
- run: `35777532367`;
- SHA: `e996d874a670f679d16366f439929a7f571f0298`;
- conclusion: **SUCCESS**;
- artifact id: `10716509088`;
- artifact: `amarelo-replay-contract-report`.

General fast-contract regression:
- run `35777192904` at SHA `3be57ae7944cae061d116ac5a870167c7cc5e168`: **SUCCESS** for builder + initial manifest;
- newer fast-contract runs are separate regression receipts and do not substitute the dedicated replay gate.

## Remaining blocker

WU-003 is **not closed**.

Missing artifact:
`tests/fixtures/portmon-lognovo-replay-v1.json`

It must be generated from the original raw LOGNOVO using the canonical parser/builder, preserving:
- exact raw SHA;
- `sequence`;
- `portmon_index`;
- cumulative `at_ms`;
- full command counts;
- live cadence;
- slow AutoCAL/reference cadence;
- deterministic selection recipe.

Canonical generation command:
`python3 tools/omegas_amarelo/build_portmon_replay_fixture.py <PortmonLOGNOVO.LOG> --output tests/fixtures/portmon-lognovo-replay-v1.json`

Do **not** substitute `sourceEvent`, source line, render timestamps or synthetic timestamps for `at_ms`.

## Gate to close #75
1. materialize the original LOGNOVO raw outside Git;
2. generate the compact replay with the canonical builder;
3. promote the manifest to dual replay PROVEN;
4. add dual-corpus cadence/ordering assertions;
5. obtain GREEN dedicated replay workflow + diagnostic artifact on the exact final SHA.

No APK.
