"""Trava do hook PreToolUse(Bash): barra git stash e push --force, ignora texto entre aspas e heredocs."""
import json
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOOK = ROOT / ".claude/hooks/pre-bash.py"

BLOQUEIA = [
    "git stash",
    "git status && git stash pop",
    "git push -f origin x",
    "git push --force origin x",
]
LIBERA = [
    "git push --force-with-lease origin x",
    'git commit -m "fala de git stash e git push --force"',
    "git commit -m 'git stash'",
    "cat <<'EOF'\ngit stash\nEOF",
    "git push origin x",
    "echo git stashing",
]


def roda(cmd: str) -> int:
    r = subprocess.run([sys.executable, str(HOOK)], input=json.dumps({"tool_input": {"command": cmd}}), text=True, capture_output=True)
    return r.returncode


class HookPreBash(unittest.TestCase):
    def test_bloqueia(self):
        for cmd in BLOQUEIA:
            self.assertEqual(2, roda(cmd), cmd)

    def test_libera(self):
        for cmd in LIBERA:
            self.assertEqual(0, roda(cmd), cmd)

    def test_registrado_nas_configuracoes(self):
        cfg = json.loads((ROOT / ".claude/settings.json").read_text(encoding="utf-8"))
        cmds = [h["command"] for g in cfg["hooks"]["PreToolUse"] for h in g["hooks"]]
        self.assertTrue(any(c.endswith("pre-bash.py") for c in cmds))


if __name__ == "__main__":
    unittest.main()
