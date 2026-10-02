"""Read-only GitHub operations. All I/O belongs in activities, not workflows."""
import json
import os
import urllib.error
import urllib.parse
import urllib.request

from temporalio import activity
from temporalio.exceptions import ApplicationError

REPO = "viluadmcontas2-dot/OMEGAS-V8.2"
BRANCH = "OmegasPlatina"


def _github_get(path):
    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        raise ApplicationError("GITHUB_TOKEN missing; no mutation attempted", non_retryable=True)
    url = "https://api.github.com/repos/" + REPO + "/" + path
    req = urllib.request.Request(url, headers={
        "Authorization": "Bearer " + token,
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "omegas-temporal-readonly",
    })
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            return json.load(response)
    except urllib.error.HTTPError as exc:
        if exc.code in (401, 403, 404, 422):
            raise ApplicationError(
                "GitHub returned HTTP " + str(exc.code) + " for " + path,
                non_retryable=True,
            ) from exc
        raise


@activity.defn
def resolve_platina_head() -> str:
    payload = _github_get("branches/" + urllib.parse.quote(BRANCH))
    sha = payload["commit"]["sha"]
    if len(sha) != 40:
        raise ApplicationError("Unexpected commit SHA", non_retryable=True)
    return sha


@activity.defn
def list_exact_sha_runs(sha: str) -> list:
    """GitHub paginates runs; use server-side exact SHA and verify locally too."""
    rows = []
    for page in range(1, 6):
        query = urllib.parse.urlencode({
            "head_sha": sha, "per_page": 100, "page": page,
        })
        payload = _github_get("actions/runs?" + query)
        batch = payload.get("workflow_runs", [])
        rows.extend({
            "name": run.get("name"),
            "head_sha": run.get("head_sha"),
            "head_branch": run.get("head_branch"),
            "status": run.get("status"),
            "conclusion": run.get("conclusion"),
            "created_at": run.get("created_at"),
            "id": run.get("id"),
            "run_attempt": run.get("run_attempt"),
            "html_url": run.get("html_url"),
        } for run in batch)
        if len(batch) < 100:
            return rows
    # Never return a partial list and claim closure.
    raise ApplicationError("Run pagination limit reached; review required", non_retryable=True)
