#!/usr/bin/env bash
# SessionStart (nuvem): deixa o ambiente de engenharia reversa (REA + JADX + Ghidra) pronto a cada sessão.
set -euo pipefail
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then exit 0; fi
echo '{"async": true, "asyncTimeout": 900000}'
exec "$CLAUDE_PROJECT_DIR/tools/rea/install.sh"
