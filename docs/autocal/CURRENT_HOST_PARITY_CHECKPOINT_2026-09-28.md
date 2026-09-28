# AutoCal host-parity checkpoint — 2026-09-28

This commit is a verification anchor for the current OmegasVerde generation.

Product authority:
- target branch: OmegasVerde
- ProgBase host behavior is the compatibility oracle
- ECU-internal AutoMatch mathematics are out of scope
- no APK generation is authorized by this checkpoint

Required product properties:
- native acquisition remains ECU-owned;
- host commands/actions mirror proven ProgBase operations;
- Finish AutoCal/Finish AutoMatch must commit native state and verify readback;
- original ProgBase Reset K and OMEGAS live-K neutralization stay distinct;
- acquired-point reacquisition uses full SetVector masks + native commit;
- acquisition mirror remains single-serial-authority and backpressure-aware;
- AutoCal evidence/actions persist through the canonical session recorder.
