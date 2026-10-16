#!/usr/bin/env bash
# Delegação padrão ao Codex (regra fixa do dono, 2026-10-16: Codex primeiro).
# Uso: tools/codex-task.sh <pasta-de-trabalho> <arquivo-com-o-prompt> <arquivo-de-saida> [read-only|workspace-write]
# Roda em segundo plano com `codex exec` (login ChatGPT via tools/codex-ensure-login.sh; nunca API paga).
set -u
CWD="$1"; PROMPT_FILE="$2"; OUT="$3"; SANDBOX="${4:-workspace-write}"
CODEX="${CODEX_BIN:-$(command -v codex || echo /opt/codex-mcp/bin/codex)}"
"$(dirname "$0")/codex-ensure-login.sh" >/dev/null 2>&1 || { echo "codex: sem login" >&2; exit 2; }
nohup "$CODEX" exec --skip-git-repo-check --sandbox "$SANDBOX" -C "$CWD" "$(cat "$PROMPT_FILE")" > "$OUT" 2>&1 &
echo "codex pid $! → $OUT"
