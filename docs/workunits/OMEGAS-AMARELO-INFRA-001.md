# OMEGAS-AMARELO-INFRA-001 — GitHub Actions Research Farm

Issue: #96
Parent: #72
State: ACTIVE

## Goal
Use GitHub Actions as the primary compute fabric for parallel reverse engineering, replay, falsification, static consumer analysis and verification.

## Wave 001
- 160 independent lanes.
- 40 lanes: pinned Verde forensic/oracle baseline at `2e3eaffedeb6a20972275bfee7d065e5d2a88c86`.
- 76 lanes: Kotlin production source scans.
- 24 lanes: UI source scans.
- 20 lanes: cross-repository symbol/authority scans.
- fail-fast=false.
- one receipt artifact per lane.
- aggregate artifact + issue #96 comment.

## Why this shape
A matrix has a hard maximum of 256 jobs per workflow run. Wave 001 intentionally stays below that boundary. GitHub runner concurrency is allowed to queue naturally; no AgentRed coordination is required.

## Inputs
- Amarelo branch SHA that triggers the workflow.
- Verde watermark pinned in the request file.
- compact real-log fixtures already present in the remote repo.
- ProgBase/Portmon provenance hashes already encoded in the harvested oracle fixtures.

## Outputs
Each receipt includes status, target, evidence and metrics. Research RED is a finding; BROKEN means the lane infrastructure itself failed.

## AgentRed policy
Not used by this WorkUnit. If a future question requires a local-only raw artifact, create an explicit acquisition issue rather than silently falling back to AgentRed.

## Gates
- workflow queues successfully;
- 160 lane artifacts are addressable;
- aggregate artifact exists;
- #96 receives the summary;
- BROKEN count = 0 before using results as evidence.
