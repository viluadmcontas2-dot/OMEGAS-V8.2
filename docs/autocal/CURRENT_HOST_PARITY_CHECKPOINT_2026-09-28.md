# AutoCal host-parity checkpoint — 2026-09-28

This commit is a verification anchor for the current OmegasPlatina generation.

Product authority:
- target branch: OmegasPlatina
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


Platina 2026-09-29 update:
- This checkpoint is historical evidence, not the active branch authority.
- Active product branch is `OmegasPlatina`.
- Reset K authority is normal `MUL_ACT 0x0161[30] -> 0x4000`, with complete readback. `VECT_AUTOCAL_EE` remains a distinct EE surface.
- Predictor/V7 suggestions are analysis/review only and cannot start an ECU write.
