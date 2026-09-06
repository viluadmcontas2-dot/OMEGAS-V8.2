# Blue OBD stage integration checkpoint — 2026-09-06

Candidate implementation parent: `4f5802bd48be06325a74550aa669eb52e47cbe6e`.

Scope is intentionally limited to Task 3 of the Blue OBD Witness plan:
- connect status is explicit across PERMISSION/RFCOMM/ELM_INIT/PROTOCOL/STFT_READY/LIVE/ERROR;
- RFCOMM has a bounded watchdog that closes a stalled socket;
- ELM handshake probes `ATI`, configures `ATAT1` best-effort, negotiates `ATSP0`, and requires a real `0106` response before LIVE;
- PID parsing is delegated to `ElmResponseParser`;
- evidence/polling math is unchanged in this slice.

Verification state at creation: **pending exact-SHA Blue CI**. Do not treat this checkpoint as completion evidence until FAST and FULL JVM/lint/APK pass on this commit or a direct descendant containing no production-code changes.
