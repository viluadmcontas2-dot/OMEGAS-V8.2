#!/bin/bash
# SessionStart (somente sessão remota): garante que o gate rápido do OMEGAS roda.
# O repositório não tem manifesto de dependências: tools/run_checks.py usa só
# Python 3 e Node (testes `node --test`), ambos da biblioteca padrão. Android/Gradle
# fica fora do gate rápido (prova de APK é CI sob demanda). Idempotente e sem rede.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(pwd)}"

# Node: usa o do PATH; se faltar, procura as instalações conhecidas do ambiente.
if ! command -v node >/dev/null 2>&1; then
  for dir in /opt/node22/bin /opt/node20/bin /opt/node21/bin; do
    if [ -x "$dir/node" ]; then
      export PATH="$dir:$PATH"
      if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
        echo "export PATH=\"$dir:\$PATH\"" >> "$CLAUDE_ENV_FILE"
      fi
      break
    fi
  done
fi

for tool in python3 node; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "session-start: '$tool' não encontrado; o gate rápido (tools/run_checks.py) não vai rodar" >&2
    exit 1
  fi
done

# Evita criar __pycache__ ao importar tools/ e scripts/ nos testes e relatórios.
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo 'export PYTHONDONTWRITEBYTECODE=1' >> "$CLAUDE_ENV_FILE"
fi

echo "session-start: python $(python3 --version 2>&1 | cut -d' ' -f2), node $(node --version)"
