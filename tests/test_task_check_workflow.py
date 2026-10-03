import pathlib
root = pathlib.Path(__file__).resolve().parents[1]
wf = (root / ".github/workflows/task-check.yml").read_text(encoding="utf-8")
sh = (root / "tools/ci/remote-test.sh").read_text(encoding="utf-8")
assert "workflow_dispatch" in wf and "push:" not in wf
for k in ("gradle", "node", "python", "checks"): assert k in wf, k
assert "verde-android-render-evidence.yml" in sh and "source_sha" in sh  # kind=android (R6)
assert '--tests "${{ inputs.target }}"' in wf
assert "gh workflow run task-check.yml" in sh and "gh run watch" in sh and "--exit-status" in sh
assert "REMOTE_TEST=PASS" in sh and "REMOTE_TEST=FAIL" in sh
assert "--log-failed" in sh
print("TASK_CHECK_CONTRACT=PASS")
