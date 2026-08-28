# Phase 06 Equivalence Oracle

Offline-only validation for the pragmatic OMEGAS V8.2 equivalence contract.

The runtime authority remains `RPM + MAP -> petrol Tinj`; this tool does not run on the TayTech unit and never changes ECU state. It consumes only semantic OMEGAS session logs from the frozen manifest. Raw Portmon captures are excluded unless separately decoded.

## Reproduce

```bash
python tools/phase06/equivalence_oracle.py \
  --manifest tools/phase06/equivalence_corpus_manifest.json \
  --corpus-root /path/to/OMEGAS/logs \
  --output build/phase06-equivalence-oracle.json
```

## Fresh 2026-08-21 local replay receipt

A fresh read-only replay was executed against the mounted OMEGAS corpus after deduplicating identical `sessionId` values. It found 15 unique V8.0 sessions and 105,235 usable gasoline/GNV telemetry rows.

For cross-session gasoline-reference holdout with the current engineering candidate `80 RPM x 0.02 bar`, max 16 neighbors and normalized support radius 1.5:

- evaluated accepted gasoline observations: 5,105;
- supported: 4,676;
- coverage: 91.60%;
- median absolute Tinj reference error: 1.15%;
- P90: 3.49%;
- P95: 5.45%.

Short-term accepted-gasoline stability produced 3,960 tight consecutive pairs (`<=40 RPM`, `<=0.01 bar`):

- median relative Tinj difference: 0.71%;
- P90: 1.87%;
- P95: 2.95%.

This is the evidence behind the runtime single-observation noise candidate of 1.9%; it is a corpus-derived engineering calibration, not an OEM resolution claim.

Rejected-evidence replay again showed that pressure-only hard rejection loses materially useful RPM+MAP/Tinj evidence. Among rejected GNV observations with close accepted RPM/MAP support, `Pressão diferencial mudando` had median Tinj divergence ~2.76%, with ~53.6% within 3% and ~71.1% within 5%. `Pressão diferencial instável` had median ~3.35%, with ~47.0% within 3% and ~67.2% within 5%. These are substantially better than the dominant `RPM mudando continuamente` bucket, whose median divergence was ~6.64%.

The release decision must still use the executable report generated from the exact candidate source and frozen corpus manifest. This README is a human-readable receipt, not a substitute for the JSON oracle output.
