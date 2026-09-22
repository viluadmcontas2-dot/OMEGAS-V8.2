# OMEGAS-AMARELO-WU-003 — Replay real e GitHub Actions

Issue: #75
Estado: **PROVEN — dual raw replay + dedicated GitHub Actions gate**

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
- newer fast-contract runs are separate regression receipts and do not substitute the dedicated replay gate.\n\n## Closure receipt — 2026-09-22

WU-003 está **PROVEN**.

### LOGNOVO raw → compact replay
- raw materializado fora do Git a partir do arquivo original de Drive;
- raw size: `149911521` bytes;
- raw SHA-256: `43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64` — match exato com a autoridade canônica;
- fixture: `tests/fixtures/portmon-lognovo-replay-v1.json`;
- fixture SHA-256: `62001a5c38206466e1444b24438eba608440c0192b9e923721e0504c446de70e`;
- 39,517 writes / 567 comandos únicos;
- 964 transações compactas selecionadas deterministicamente;
- `sequence`, `portmon_index` e `at_ms` são estritamente crescentes no replay selecionado.

### Cadência raw-derived
- live `48 01 49`: mediana `59.628 ms`;
- famílias AutoCAL lentas: aproximadamente `1.99–2.02 s`;
- referências `0x018D/0x018E`: aproximadamente `4.02 s`;
- timing permanece definido exclusivamente pelo relógio cumulativo das operações Portmon, nunca por source line/render timestamp.

### CI receipts no primeiro SHA com o dual replay
Source SHA: `86270351c05e050dd1ce1e83ccc2bc3578668921`

Dedicated replay workflow:
- run `35796107887` — **SUCCESS**;
- artifact `amarelo-replay-contract-report`, id `10723599842`;
- artifact digest `sha256:64ec886fcb7ce1f33fb7336ca98c95bd19947747d8b94d925eddbaf52a2b272a`.

Fast regression:
- run `35796107621` — **SUCCESS**;
- protocolo, consumer graph, evidência nativa, Kotlin projection/publisher e UI contracts passaram no mesmo SHA.

### Final exact-SHA rule
Qualquer commit documental de fechamento que altere o HEAD deve tocar o manifest de replay e obter novamente o dedicated replay workflow verde. O receipt terminal fica registrado na GitHub issue #75.

## Próximo WorkUnit
WU-005 / #77 — fechar a tela AutoCAL por `replay → runtime/bridge → WebView real → screenshot/DOM 1280×720`.

No APK.
