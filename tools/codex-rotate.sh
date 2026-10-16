#!/usr/bin/env bash
# Troca para a próxima conta Codex guardada em segredo: CODEX_AUTH_JSON_B64, _2, _3, _4.
# Uso: tools/codex-rotate.sh   (chame quando o Codex responder "usage limit"). Estado em ~/.codex/.slot
set -u
AUTH="${CODEX_HOME:-$HOME/.codex}/auth.json"; SLOT_F="$(dirname "$AUTH")/.slot"
mkdir -p "$(dirname "$AUTH")"
names=(CODEX_AUTH_JSON_B64 CODEX_AUTH_JSON_B64_2 CODEX_AUTH_JSON_B64_3 CODEX_AUTH_JSON_B64_4)
cur=$(cat "$SLOT_F" 2>/dev/null || echo 0)
for i in 1 2 3 4; do
  n=$(( (cur + i) % 4 )); v="${!names[$n]:-}"
  if [ -n "$v" ]; then printf '%s' "$v" | base64 -d > "$AUTH" && chmod 600 "$AUTH"; echo "$n" > "$SLOT_F"; echo "codex: conta ${names[$n]}"; exit 0; fi
done
echo "codex: nenhuma outra conta configurada (só 1 segredo)"; exit 1
