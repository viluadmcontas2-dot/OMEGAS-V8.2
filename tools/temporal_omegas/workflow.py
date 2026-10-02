"""Bounded, read-only Temporal workflow. Does not call an LLM or modify GitHub."""
from datetime import timedelta

from temporalio import workflow
from temporalio.common import RetryPolicy

with workflow.unsafe.imports_passed_through():
    from tools.temporal_omegas.activities import resolve_platina_head, list_exact_sha_runs
    from tools.temporal_omegas.gates import evaluate_runs


@workflow.defn
class PlatinaSameShaGate:
    def __init__(self):
        self.result = {"overall": "NOT_STARTED"}
        self.stop_requested = False

    @workflow.query
    def status(self) -> dict:
        return self.result

    @workflow.signal
    def stop(self):
        self.stop_requested = True

    @workflow.run
    async def run(self, max_checks: int = 4) -> dict:
        if not 1 <= max_checks <= 12:
            return {"overall": "BLOCKED", "reason": "max_checks must be 1..12"}
        policy = RetryPolicy(
            initial_interval=timedelta(seconds=3),
            maximum_interval=timedelta(seconds=20),
            maximum_attempts=3,
        )
        sha = await workflow.execute_activity(
            resolve_platina_head,
            start_to_close_timeout=timedelta(seconds=30),
            retry_policy=policy,
        )
        self.result = {"overall": "WAITING", "sha": sha, "attempt": 0}
        for attempt in range(1, max_checks + 1):
            if self.stop_requested:
                self.result = dict(self.result, overall="STOPPED")
                return self.result
            runs = await workflow.execute_activity(
                list_exact_sha_runs,
                sha,
                start_to_close_timeout=timedelta(seconds=120),
                retry_policy=policy,
            )
            self.result = dict(evaluate_runs(runs, sha), attempt=attempt)
            if self.result["overall"] != "WAITING":
                return self.result
            if attempt < max_checks:
                await workflow.sleep(timedelta(seconds=60))
        self.result = dict(
            self.result, overall="BLOCKED",
            reason="Missing/pending same-SHA evidence after bounded checks; never infer PASS",
        )
        return self.result
