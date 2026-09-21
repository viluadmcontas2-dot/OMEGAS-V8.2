# GitHub Actions Compute Contract — OMEGAS Amarelo

## Default executor
GitHub Actions is the default compute plane for OMEGAS Amarelo.

Use it for:
- protocol fixtures;
- replay;
- deterministic reverse-engineering transforms over versioned inputs;
- static producer/consumer scans;
- JVM/Python/Node tests;
- mutation/falsification;
- WebView/browser render gates;
- build and artifact generation when later authorized.

## Scaling
- one workflow matrix may create at most 256 jobs;
- larger investigations are split into numbered waves;
- jobs are independent wherever possible;
- integration/writes remain serialized;
- fail-fast=false for research;
- runner concurrency is a queueing concern, not a reason to collapse independent work.

## Receipts
Every lane writes a machine-readable receipt:
- lane id;
- driver;
- pinned ref;
- target;
- status;
- evidence;
- metrics.

Every wave has an aggregate receipt and a GitHub issue.

## Status semantics
- PASS: deterministic contract satisfied.
- INFO: analysis result, no binary gate.
- RED: hypothesis/contract falsified; scientifically useful.
- BROKEN: infrastructure/reproducibility failure.

Only BROKEN is an infrastructure failure.

## Local-only evidence
Do not hide local dependencies. If an input is not available to Actions, the corresponding WorkUnit is BLOCKED_ON_ACQUISITION until a compact provenance-preserving fixture exists.
