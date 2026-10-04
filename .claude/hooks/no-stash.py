#!/usr/bin/env python3
"""Bloqueia `git stash`: o stash é GLOBAL entre worktrees e já misturou o trabalho de agentes paralelos."""
import json, re, sys
try:
    cmd = json.load(sys.stdin).get("tool_input", {}).get("command", "")
except Exception:
    sys.exit(0)
if re.search(r"(^|[;&|]\s*|\s)git\s+stash(\s|$)", cmd):
    print("Bloqueado: git stash é global entre worktrees (já misturou agentes). Faça commit WIP na própria branch.", file=sys.stderr)
    sys.exit(2)
