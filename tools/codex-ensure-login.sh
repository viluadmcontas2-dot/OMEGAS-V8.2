#!/usr/bin/env bash
# Idempotente: garante o login do Codex antes de despachar tarefas.
#  - já logado            -> não faz nada (exit 0)
#  - login em andamento   -> reimprime o link/código, não abre outro (exit 2)
#  - não logado           -> inicia device-auth em segundo plano e imprime link/código (exit 2)
# Depois que o dono digitar o código, rode de novo: vira "já logado".
# Persistir entre sessões: guardar ~/.codex/auth.json como segredo do ambiente (env secret) e restaurar aqui.
set -u
CODEX="${CODEX_BIN:-$(command -v codex || echo /opt/codex-mcp/bin/codex)}"
LOG="${CODEX_LOGIN_LOG:-/tmp/codex-login.log}"
AUTH="${CODEX_HOME:-$HOME/.codex}/auth.json"

# restaura login guardado em segredo, se houver e se ainda não existe
if [ ! -s "$AUTH" ] && [ -n "${CODEX_AUTH_JSON_B64:-}" ]; then
  mkdir -p "$(dirname "$AUTH")" && printf '%s' "$CODEX_AUTH_JSON_B64" | base64 -d > "$AUTH" && chmod 600 "$AUTH"
fi

if "$CODEX" login status 2>&1 | grep -q "Logged in"; then echo "codex: já logado"; exit 0; fi

if ! pgrep -f "codex.* login --device-auth" >/dev/null 2>&1; then
  nohup "$CODEX" login --device-auth > "$LOG" 2>&1 &
  sleep 6
fi
sed 's/\x1b\[[0-9;]*m//g' "$LOG" | grep -E "https://|[A-Z0-9]{4}-[A-Z0-9]{4,5}"
echo "codex: aguardando o dono digitar o código (expira em 15 min); rode este script de novo depois."
exit 2
