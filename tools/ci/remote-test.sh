#!/usr/bin/env bash
# Laço de teste remoto (Task 0.1 do índice Norte Único).
#   tools/ci/remote-test.sh <gradle|node|python|checks> <target>
#   tools/ci/remote-test.sh android [cenario]   (R6: render no emulador)
# Empurra a branch, dispara o workflow no GitHub, espera o run do SHA
# exato e imprime REMOTE_TEST=PASS ou REMOTE_TEST=FAIL (+ log das falhas).
set -euo pipefail

kind="${1:?uso: remote-test.sh <kind> <target>}"
target="${2:-}"
branch="$(git rev-parse --abbrev-ref HEAD)"
source_sha="$(git rev-parse HEAD)"

git push -u origin "$branch" >/dev/null 2>&1 || git push -u origin "$branch"

if [ "$kind" = "android" ]; then
  # verde-android-render-evidence.yml roda a matriz inteira no HEAD da ref;
  # o cenário não é filtrável por dispatch, só registrado aqui.
  workflow="verde-android-render-evidence.yml"
  [ -n "$target" ] && echo "CENARIO_PEDIDO=$target (matriz completa roda; filtre no log)"
  gh workflow run "$workflow" --ref "$branch"
else
  workflow="task-check.yml"
  gh workflow run task-check.yml --ref "$branch" -f kind="$kind" -f target="$target"
fi

# Espera o run deste workflow cujo headSha é exatamente source_sha.
run_id=""
for _ in $(seq 1 60); do
  run_id="$(gh run list --workflow "$workflow" --branch "$branch" -L 5 \
              --json databaseId,headSha,event \
              --jq "[.[] | select(.headSha == \"$source_sha\" and .event == \"workflow_dispatch\")][0].databaseId // empty")"
  [ -n "$run_id" ] && break
  sleep 5
done
if [ -z "$run_id" ]; then
  echo "REMOTE_TEST=FAIL (run de $workflow para $source_sha não apareceu)"
  exit 1
fi
echo "RUN=$(gh run view "$run_id" --json url --jq .url) SOURCE_SHA=$source_sha"

if gh run watch "$run_id" --exit-status >/dev/null; then
  echo "REMOTE_TEST=PASS"
else
  gh run view "$run_id" --log-failed | tail -200
  echo "REMOTE_TEST=FAIL"
  exit 1
fi
