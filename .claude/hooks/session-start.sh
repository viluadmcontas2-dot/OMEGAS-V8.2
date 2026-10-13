#!/usr/bin/env bash
# SessionStart (nuvem): deixa o ambiente pronto a cada sessao — Codex MCP + REA (JADX + Ghidra).
# Mantem os DOIS servidores MCP (codex + rea) sempre instalados; registro vem do .mcp.json do projeto.
set -euo pipefail
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then exit 0; fi
echo '{"async": true, "asyncTimeout": 900000}'
"$CLAUDE_PROJECT_DIR/tools/codex/install.sh"
exec "$CLAUDE_PROJECT_DIR/tools/rea/install.sh"
