# OMEGAS Platina — Temporal coordinator (read-only V1)

This is a **real Python Temporal workflow scaffold**, not an activated hosted
service. It does not require Codex, an OpenAI API key, or paid LLM calls. The
owner continues talking to **normal ChatGPT**; ChatGPT with GitHub access can
inspect the gate's evidence and explain it in Portuguese.

## Why it exists
The Platina project already has GitHub Actions. Avoid extra branches, infinite
retry cycles, and optimistic green badges: the gate resolves the HEAD of
`OmegasPlatina` **once**, then observes these existing workflows at that exact
commit: canonical CI, fast contracts, global reality, and Android render.
Path-filtered workflows that never ran are MISSING, never PASS.

- PASS: every required latest run completed successfully on the *same* SHA.
- WAITING: missing or pending evidence during the bounded observation window.
- BLOCKED: a run fails or the observation limit expires.
- STOPPED: an operator signals stop.
- A failing check is **not automatically rerun**: a deterministic product bug
  needs a changed patch, not 100,000 repetitions of the same input.

## Installation / activation (not yet performed)
Requires Python 3.10+, an accessible Temporal dev server or Temporal Cloud,
a long-running worker host, and `GITHUB_TOKEN` with **read-only**
repository metadata and Actions permissions (fine-grained token for this repo).

From repository root:

```bash
python -m pip install -r tools/temporal_omegas/requirements.txt
temporal server start-dev
# In a separate shell:
export GITHUB_TOKEN=YOUR_READ_ONLY_TOKEN
python -m tools.temporal_omegas.worker
# In a third shell:
python -m tools.temporal_omegas.start start
python -m tools.temporal_omegas.start status
python -m tools.temporal_omegas.start stop
```

To use Temporal Cloud, configure the supported SDK environment/client settings
before starting the worker and client. Never commit tokens or credentials.
A normal ChatGPT session **does not itself host a continuously running Worker**.
The GitHub connector currently lets GPT review the repository/Actions; direct
Temporal start/status from chat needs a connected action or suitable endpoint.

Test without any external services:

```bash
python -m unittest tools.temporal_omegas.test_gates -v
```

## V1 security boundary
No GitHub write API calls, dispatches, commit, branch, PR, APK build, ECU
commands, automatic calibration, or automatic map writing. No arbitrary shell
execution. A PASS indicates only the listed CI evidence, **not physical ECU
validation or release authorization**. Real APK publication, device tests,
merge and mutations remain separate explicit owner decisions.

The optional future V2 may trigger already approved GitHub Actions and create
a human-reviewed PR. It must never retry a mutating ECU action automatically.
