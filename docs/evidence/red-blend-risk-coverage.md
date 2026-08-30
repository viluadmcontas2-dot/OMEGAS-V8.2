# RED V8.2 Science Blend — G9 risk/coverage evidence

## Authority

- Branch: `work/red-v82-science-blend`
- Implementation SHA: `05a493418e8724eefb3ee0f356d4f39f7aa013fc`
- GitHub Actions run: `33329888113`
- Remote job: `SCIENCE LOCAL falsification` — `SUCCESS`
- Governing Issue: `#11` (`RED-BLEND-001`)

## Root cause resolved

The preceding run `33329597214` failed at `Blend empirical risk coverage tests` because the TDD contract imported `lab.red_blend.risk_coverage`, but that production module did not yet exist. No scientific threshold failed and no threshold/test was weakened.

The minimal implementation added a deterministic empirical risk/coverage curve that:

- sorts by the predeclared risk score with deterministic order tie-breaking;
- reports every prefix as coverage plus mean and empirical P90 absolute relative error;
- rejects empty, non-finite or negative risk/error input;
- keeps `p_improve=None`;
- keeps `actionable=False`;
- limits its claim to `EMPIRICAL_RISK_COVERAGE_ONLY`.

The module is offline laboratory code under `lab/red_blend/`; it is not promoted into the RED Android hot path.

## Remote verification

Run `33329888113` on exact SHA `05a493418e8724eefb3ee0f356d4f39f7aa013fc` completed successfully. The same run passed, in order:

- RED fast contracts;
- local science and governed WU-006 fixture reconstruction;
- session-independence/LOSO evidence;
- blind walk-forward;
- risk-gated global+local hybrid tuning;
- causal MAP_K contracts and real causal support audit;
- sensitivity fail-closed contracts;
- empirical risk/coverage tests.

Result: `G9_RISK_COVERAGE=PASS` for the offline empirical contract.

## G10 boundary discovered

`G10_P_IMPROVE` is **not proven**. The governed real-corpus causal audit currently returns `INSUFFICIENT_CAUSAL_OUTCOME_SUPPORT` with `UNPROVEN_COMMON_TIMEBASE`: the privacy-safe WU-006 episode fixture does not contain a repository-proven clock-domain bridge to the confirmed manual MAP_K intervention snapshot.

Therefore no real held-out intervention outcome can currently be aligned without guessing. In accordance with the design contract, `P(improve)` remains null and actionability remains false. This is a scientific fail-closed gate, not a reason to weaken causality requirements.

To clear G10, a governed privacy-safe bridge or new vehicle capture must establish intervention identity/timebase plus confirmed manual write, ACK/readback, and subsequent held-out comparable outcomes. Automatic ECU write remains forbidden.
