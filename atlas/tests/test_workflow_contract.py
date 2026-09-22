import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/atlas-battle-royale.yml"


class WorkflowContractTest(unittest.TestCase):
    def test_no_literal_newline_escape_between_shell_commands(self):
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertNotIn(r"autocal-semantics.json\n", text)
        self.assertNotIn(r"autocal-metadata.json\n", text)

    def test_semantic_index_and_provenance_are_fail_fast(self):
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("test -s atlas-index/undelphi/autocal-semantics.json", text)
        self.assertIn("test -s atlas-index/ghidra/autocal-semantics.json", text)


if __name__ == "__main__":
    unittest.main()
