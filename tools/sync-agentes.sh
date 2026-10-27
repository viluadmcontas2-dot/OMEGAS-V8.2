#!/usr/bin/env bash
# Sincronia de agentes (dono, 2026-10-27): Claude, Codex e AGY trabalham sobre o MESMO remoto no GitHub.
# Este script nunca usa force, nunca resolve conflito sozinho e confirma lendo o remoto de volta.
#
# Uso:  tools/sync-agentes.sh [branch]            (padrão: a branch atual)
#       tools/sync-agentes.sh --so-ver [branch]   (só mostra o estado, não mexe em nada)
#       SYNC_BASE=OMEGASCANONICO tools/sync-agentes.sh --com-base   (também traz a base canônica)
#
# Passos: 1) busca o remoto  2) mostra quem mexeu e há quanto tempo  3) traz o que outros mandaram para a MESMA branch
#         4) (opcional) traz a base canônica  5) envia  6) lê o remoto de volta e confere o SHA.
# Códigos de saída: 0 ok · 2 sem repositório/branch · 3 conflito (nada foi alterado; resolver à mão) · 4 envio recusado · 5 leitura de volta não bate.
set -u

SO_VER=0; COM_BASE=0; BRANCH=""
for a in "$@"; do
  case "$a" in
    --so-ver) SO_VER=1 ;;
    --com-base) COM_BASE=1 ;;
    -*) echo "opção desconhecida: $a" >&2; exit 2 ;;
    *) BRANCH="$a" ;;
  esac
done
git rev-parse --git-dir >/dev/null 2>&1 || { echo "não é um repositório git" >&2; exit 2; }
[ -n "$BRANCH" ] || BRANCH="$(git rev-parse --abbrev-ref HEAD)"
[ "$BRANCH" != "HEAD" ] || { echo "HEAD solto: entre numa branch" >&2; exit 2; }
BASE="${SYNC_BASE:-OMEGASCANONICO}"

retry() { # retry <comando...>: só repete falha de rede (2s, 4s, 8s, 16s)
  local n=0 espera=2
  until "$@"; do
    n=$((n + 1)); [ "$n" -ge 5 ] && return 1
    echo "falhou; tentando de novo em ${espera}s ($n/4)…" >&2; sleep "$espera"; espera=$((espera * 2))
  done
}

echo "== 1) buscando o remoto"
retry git fetch origin --prune || { echo "não consegui buscar o remoto" >&2; exit 2; }

echo "== 2) quem mexeu por último (outros agentes podem estar trabalhando agora)"
git for-each-ref --sort=-committerdate refs/remotes/origin \
  --format='   %(refname:short)  %(objectname:short)  %(committerdate:relative)  %(authorname)' | head -8

LOCAL="$(git rev-parse HEAD)"
REMOTE="$(git rev-parse -q --verify "refs/remotes/origin/$BRANCH" 2>/dev/null || true)"
if [ -n "$REMOTE" ]; then
  read -r ATRAS A_FRENTE < <(git rev-list --left-right --count "origin/$BRANCH...HEAD")
  echo "   $BRANCH: local à frente $A_FRENTE · remoto à frente $ATRAS"
else
  echo "   $BRANCH ainda não existe no remoto (primeiro envio)"; ATRAS=0; A_FRENTE=1
fi
if git rev-parse -q --verify "refs/remotes/origin/$BASE" >/dev/null 2>&1; then
  read -r B_ATRAS B_FRENTE < <(git rev-list --left-right --count "origin/$BASE...HEAD")
  echo "   base $BASE: ela está $B_ATRAS à frente da sua branch (a sua tem $B_FRENTE próprios)"
fi
[ "$SO_VER" -eq 1 ] && { echo "(--so-ver: nada foi alterado)"; exit 0; }

if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo "aviso: há alterações não commitadas; elas NÃO serão enviadas (faça commit antes)" >&2
fi

merge_ou_parar() { # merge_ou_parar <ref> <rótulo>
  local ref="$1" rotulo="$2"
  if git merge --no-edit "$ref" >/dev/null 2>&1; then
    echo "   $rotulo: mesclado sem conflito"; return 0
  fi
  echo "CONFLITO ao mesclar $rotulo. Nada foi alterado; arquivos em conflito:" >&2
  git diff --name-only --diff-filter=U | sed 's/^/   - /' >&2
  git merge --abort 2>/dev/null
  exit 3
}

if [ -n "$REMOTE" ] && [ "${ATRAS:-0}" -gt 0 ]; then
  echo "== 3) trazendo o que outro agente mandou para $BRANCH"
  merge_ou_parar "origin/$BRANCH" "origin/$BRANCH"
fi
if [ "$COM_BASE" -eq 1 ] && [ "${B_ATRAS:-0}" -gt 0 ]; then
  echo "== 4) trazendo a base $BASE"
  merge_ou_parar "origin/$BASE" "origin/$BASE"
fi

echo "== 5) enviando (sem force)"
if ! retry git push -u origin "$BRANCH"; then
  echo "envio recusado. Outro agente mandou algo novo? Rode de novo; se repetir, há conflito real." >&2; exit 4
fi

echo "== 6) lendo o remoto de volta"
LOCAL="$(git rev-parse HEAD)"
NO_REMOTO="$(git ls-remote origin "refs/heads/$BRANCH" | cut -f1)"
if [ "$NO_REMOTO" = "$LOCAL" ]; then
  echo "OK: o remoto está igual ao local ($BRANCH @ ${LOCAL:0:8}). Nada foi sobrescrito."
  exit 0
fi
echo "ATENÇÃO: remoto=${NO_REMOTO:0:8} local=${LOCAL:0:8}. Não anuncie como publicado." >&2
exit 5
