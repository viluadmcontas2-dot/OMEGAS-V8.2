#!/usr/bin/env bash
# SessionStart (nuvem): deixa o ambiente pronto a cada sessão (REA, Superpowers, LSP, Serena).
# Tudo idempotente e centralizado em tools/cloud-setup.sh.
set -euo pipefail
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then exit 0; fi
echo '{"async": true, "asyncTimeout": 900000}'
"$CLAUDE_PROJECT_DIR/tools/cloud-setup.sh"
