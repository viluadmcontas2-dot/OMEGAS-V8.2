# OMEGAS V8.2 RED — exhaustive logic validation ledger — 2026-09-03

## Authority and isolation

- Product authority branch: `work/red-v82-science-blend`
- Product branch HEAD at campaign start: `bbd589da5f53f0e3842c7db6454614c9a00a7491`
- Executable product parent: `6682dc85a2c9e581a83a48d7ae341e3b2b48449f`
- Validation-only branch: `work/red-v82-logic-validation-20260903`
- Validation branch may change tests/evidence only. Runtime, mathematics, Predictor and ECU behavior stay untouched unless a reproduced defect forces a separate TDD fix.
- MMMACHINE executor: CODEXMOB `main@c7622f4cac1ce86996f4dd1c81a45e1a9689b860`
- Health proof before campaign: bootstrap `SUPERVISING`, root `SUPERVISING`, fabric `RUNNING`, `maintenance=null`, 16 useful slots, no queued/active operation at snapshot.

## Safety invariants

- `AUTO_WRITE_ECU=false` remains mandatory.
- Review/open-editor actions must never write the ECU.
- Actual ECU change remains prepare → review → human confirm → writer/ECU → ACK/readback.
- No physical economy or vehicle claim is derived from this offline campaign.
- `P_IMPROVE_PROVEN=false` and `VEHICLE_PROVEN=false` remain unchanged.

## Fresh baseline campaign

Three independent workers were submitted against the exact authoritative OMEGAS branch HEAD:

1. Python/science: every root `test_*.py`, every lab `test_*.py`, unittest discovery and repeated high-risk science modules.
2. Node: every repository `*.test.*` / `*.spec.*` JavaScript file independently.
3. Android: `testDebugUnitTest`, XML failure/error aggregation, `lintDebug`, `assembleDebug`.

Initial CODEXMOB operations:

- Python: `580a13dd-5e43-47bd-9170-8a89407ee429`
- Node: `01563ecf-5753-457f-ba2e-31d3964591f4`
- Android: `cb3328ad-bcf9-4f81-b7ff-de7eed56aab5`

The Python baseline operation reported `RUNTIME_FAILED` / exit code 1 after ~7.8 s. This is not classified as an OMEGAS defect until stdout/stderr identify the failing boundary. Systematic debugging is mandatory before any correction.

## Persisted adversarial tests

### Node suggestion projection — 30 tests

`tests/ui/suggestion-model.adversarial.logic.test.cjs`

Covers global/local separation, invalid input fail-closed behavior, confidence/evidence boundaries, Predictor precedence, `GLOBAL_ONLY` local suppression, `DIRECT`/`NEAR` projection, human-review-only actions, deterministic repeated evaluation, input immutability, absent numeric field handling and 2,000 invalid fuzz inputs.

### Android runtime invariants — 35 tests

`app/src/test/java/com/omegas/v7/runtime/V7LogicAdversarialMatrix20260903Test.kt`

Covers physical visit immutability, independent visits, calibration revision binding, suggestion de-duplication by ID, duplicate-list rejection, stale suggestion rejection, no mutation from proposal registration, writer/readback fail-closed behavior, checkpoint-before-write, exact map/curve revision updates, sibling supersession, checkpoint restore, map/curve shape constraints and invalid evidence rejection.

## Result rule

No `LOGIC_VALIDATION=PASS` is allowed until:

- all fresh baseline workers have final receipts;
- persisted adversarial tests run on MMMACHINE;
- all unexpected failures are root-caused;
- any genuine product defect follows failing test → minimal fix → GREEN → full regression;
- final test/build/lint evidence belongs to one auditable validation head whose runtime source is still byte-equivalent to product authority for production files.

## Status

`LOGIC_VALIDATION=IN_PROGRESS`
