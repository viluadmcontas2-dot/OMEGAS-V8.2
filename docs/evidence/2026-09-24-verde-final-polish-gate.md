# OMEGAS Verde — final remote-only APK gate

Authority: GitHub remote only.

Product base SHA:
`2a7b58ef00374b3be7382d4cab769245d5e1bec8`

This evidence-only commit exists to run PR #101 against the exact current OmegasVerde product tree.

Gate:
- fast contracts;
- JVM unit tests;
- Android lint;
- assembleDebug;
- APK SHA-256;
- published GitHub Actions artifact.

Locked product requirements:
- sessions mirrored under `Download/Omegas`;
- manual Curve K backup published to `Download/Omegas`;
- session retention default/minimum 20;
- log-retention controls stay stable while open/editing;
- Curve K reset reaches the existing verified 1.0 writer;
- write safety keeps USB/engine/fresh-telemetry interlocks, with no RPM-only threshold;
- ECU writes retain ACK + readback.
