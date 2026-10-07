#!/usr/bin/env bash
# Executor rastreável de tarefas do Codex: nada fica só no disco local.
# Uso: tools/codex-task.sh <nome> <arquivo-do-prompt> [base=origin/OmegasDiamante] [sandbox=workspace-write]
#  - cada tarefa roda numa worktree própria (/tmp/wt/<nome>) e branch work/codex-<nome>
#  - a cada 180 s tudo vira commit WIP e é enviado ao GitHub (sobrevive a travada, limite de uso ou container reciclado)
#  - status em /tmp/codex-runs/<nome>.status: RUNNING | DONE | FAILED | TIMEOUT | LIMIT  (+ tokens e último push)
#  - mensagem final do Codex em /tmp/codex-runs/<nome>.last ; log completo em /tmp/codex-runs/<nome>.log
set -u
NAME="$1"; PROMPT="$2"; BASE="${3:-origin/OmegasDiamante}"; SANDBOX="${4:-workspace-write}"
CODEX="${CODEX_BIN:-$(command -v codex || echo /opt/codex-mcp/bin/codex)}"
ROOT="$(git rev-parse --show-toplevel)"; WT="/tmp/wt/$NAME"; BR="work/codex-$NAME"; RUN=/tmp/codex-runs; mkdir -p "$RUN" /tmp/wt
ST="$RUN/$NAME.status"; LOG="$RUN/$NAME.log"; LAST="$RUN/$NAME.last"; MAXSEC="${CODEX_MAX_SEC:-1500}"
START="$(date -u +%H:%M:%S)"; PUSHED=""
setst(){ printf '%s %s | inicio=%s | ultimo_push=%s\n' "$1" "${2:-}" "$START" "${PUSHED:-nunca}" > "$ST"; }
setst RUNNING
cd "$ROOT" && git fetch -q origin 2>/dev/null
rm -rf "$WT"; git worktree prune; git worktree add -q -B "$BR" "$WT" "$BASE" || { setst FAILED "worktree"; exit 1; }
snap(){ ( cd "$WT" && git add -A && { git diff --cached --quiet || git commit -q -m "WIP codex-$NAME $(date -u +%H:%M)" ; } ; for d in 0 2 4; do sleep $d; git push -q -f origin "HEAD:$BR" 2>/dev/null && { echo "$(date -u +%H:%M:%S)" > "$RUN/$NAME.push"; break; }; done ); PUSHED="$(cat "$RUN/$NAME.push" 2>/dev/null)"; }
( while sleep 180; do snap; setst RUNNING; done ) & LOOP=$!
timeout "$MAXSEC" "$CODEX" exec -s "$SANDBOX" -C "$WT" -o "$LAST" - < "$PROMPT" > "$LOG" 2>&1; RC=$?
kill $LOOP 2>/dev/null; snap
TOK="$(grep -A1 'tokens used' "$LOG" | tail -1 | tr -d ' ')"
if [ $RC -eq 124 ]; then setst TIMEOUT "tokens=$TOK"
elif [ $RC -ne 0 ] && grep -qiE "usage limit|rate limit|quota|insufficient|429|exceeded your" "$LOG"; then setst LIMIT "tokens=$TOK"
elif [ $RC -ne 0 ]; then setst FAILED "rc=$RC tokens=$TOK"
else setst DONE "tokens=$TOK"; fi
