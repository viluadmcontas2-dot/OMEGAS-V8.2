"""Pure, deterministic same-SHA release gate evaluation (no network access)."""

REQUIRED_WORKFLOWS = (
    "OMEGAS PLATINA CI",
    "OmegasPlatina fast contracts",
    "OmegasVerde global reality fanout",
    "OmegasVerde Android render evidence",
)


def evaluate_runs(runs, sha, branch="OmegasPlatina", required=REQUIRED_WORKFLOWS):
    """Evaluate only completed GitHub Actions runs on the precise product SHA.

    Latest run per workflow wins. Do not mistake an older green run for the
    current revision. A required workflow not triggered by path filters is
    MISSING, never implicitly green.
    """
    relevant = [
        run for run in runs
        if run.get("head_sha") == sha and run.get("head_branch") == branch
    ]
    latest = {}
    for run in relevant:
        name = run.get("name")
        key = (run.get("created_at") or "", run.get("id") or 0, run.get("run_attempt") or 0)
        old = latest.get(name)
        old_key = (
            (old.get("created_at") or "", old.get("id") or 0, old.get("run_attempt") or 0)
            if old else None
        )
        if old is None or key > old_key:
            latest[name] = run

    checks = {}
    for name in required:
        run = latest.get(name)
        if not run:
            checks[name] = {"state": "MISSING", "url": None, "run_id": None}
        elif run.get("status") != "completed":
            checks[name] = {"state": "PENDING", "url": run.get("html_url"), "run_id": run.get("id")}
        else:
            conclusion = run.get("conclusion")
            checks[name] = {
                "state": "PASS" if conclusion == "success" else "FAIL",
                "conclusion": conclusion,
                "url": run.get("html_url"),
                "run_id": run.get("id"),
            }

    states = [row["state"] for row in checks.values()]
    overall = "BLOCKED" if "FAIL" in states else (
        "WAITING" if "MISSING" in states or "PENDING" in states else "PASS"
    )
    return {"sha": sha, "branch": branch, "overall": overall, "checks": checks}
