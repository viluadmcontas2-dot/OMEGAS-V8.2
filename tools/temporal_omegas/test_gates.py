"""Zero-network unit tests: python -m unittest tools.temporal_omegas.test_gates -v"""
import unittest

from tools.temporal_omegas.gates import REQUIRED_WORKFLOWS, evaluate_runs

SHA = "a" * 40


def run(name, conclusion="success", sha=SHA, branch="OmegasPlatina", ident=1):
    return {
        "name": name, "head_sha": sha, "head_branch": branch,
        "created_at": "2026-10-01T00:00:00Z", "id": ident, "run_attempt": 1,
        "status": "completed", "conclusion": conclusion,
        "html_url": "https://github.com/example/actions/runs/" + str(ident),
    }


class GateTests(unittest.TestCase):
    def test_all_required_pass(self):
        self.assertEqual(
            evaluate_runs([run(name, ident=i) for i, name in enumerate(REQUIRED_WORKFLOWS)], SHA)["overall"],
            "PASS",
        )

    def test_old_sha_cannot_prove_current_sha(self):
        rows = [run(name, sha="b" * 40, ident=i) for i, name in enumerate(REQUIRED_WORKFLOWS)]
        self.assertEqual(evaluate_runs(rows, SHA)["overall"], "WAITING")

    def test_missing_render_never_green(self):
        rows = [run(name, ident=i) for i, name in enumerate(REQUIRED_WORKFLOWS[:-1])]
        self.assertEqual(evaluate_runs(rows, SHA)["checks"][REQUIRED_WORKFLOWS[-1]]["state"], "MISSING")

    def test_failed_lane_blocks_aggregate(self):
        rows = [run(name, ident=i) for i, name in enumerate(REQUIRED_WORKFLOWS)]
        rows[2]["conclusion"] = "failure"
        self.assertEqual(evaluate_runs(rows, SHA)["overall"], "BLOCKED")

    def test_latest_run_wins(self):
        rows = [run(name, ident=i) for i, name in enumerate(REQUIRED_WORKFLOWS)]
        rows += [run(REQUIRED_WORKFLOWS[0], "failure", ident=999)]
        self.assertEqual(evaluate_runs(rows, SHA)["overall"], "BLOCKED")


if __name__ == "__main__":
    unittest.main()
