#!/usr/bin/env bash
# SessionStart (nuvem): deixa o ambiente pronto a cada sessão — REA (engenharia reversa:
# REA + JADX + Ghidra), Superpowers e plugins LSP (TypeScript/Python). Todos idempotentes.
set -euo pipefail
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then exit 0; fi
echo '{"async": true, "asyncTimeout": 900000}'
"$CLAUDE_PROJECT_DIR/tools/rea/install.sh" || echo "[session-start] AVISO: instalação do REA falhou (ver log acima)"
"$CLAUDE_PROJECT_DIR/tools/superpowers/install.sh" || echo "[session-start] AVISO: instalação do Superpowers falhou"
"$CLAUDE_PROJECT_DIR/tools/lsp/install.sh" || echo "[session-start] AVISO: instalação dos plugins LSP falhou"
