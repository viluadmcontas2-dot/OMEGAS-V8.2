#!/usr/bin/env python3
"""PreToolUse(Bash): barra comandos que já causaram dano neste projeto.

- git stash: é global entre worktrees e já misturou o trabalho de agentes (regra do dono).
- git push --force sem --force-with-lease: pode apagar trabalho remoto alheio.
Texto entre aspas e heredocs é ignorado (uma mensagem de commit pode citar o comando).
"""
import json
import re
import sys

try:
    cmd = json.load(sys.stdin).get("tool_input", {}).get("command", "")
except Exception:
    sys.exit(0)

c = re.sub(r"<<-?\s*'?\"?(\w+)'?\"?.*?\n.*?\n\1\b", " ", cmd, flags=re.S)
c = re.sub(r"'[^']*'|\"(?:[^\"\\]|\\.)*\"", " ", c)

if re.search(r"(^|[;&|(\s])git\s+stash(\s|$)", c):
    print("Bloqueado: não use git stash neste projeto (mistura trabalho entre agentes). Faça commit ou copie o arquivo.", file=sys.stderr)
    sys.exit(2)
if re.search(r"git\s+push\b[^;&|]*\s(--force|-f)(\s|$)", c):
    print("Bloqueado: push --force pode apagar trabalho alheio. Use --force-with-lease e só com autorização.", file=sys.stderr)
    sys.exit(2)
