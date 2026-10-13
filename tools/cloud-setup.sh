#!/usr/bin/env bash
# OMEGAS 8.2 — preparação mínima e idempotente do ambiente de nuvem.
# Instala só o que o projeto usa: REA (engenharia reversa), Superpowers, plugins LSP e Serena.
# Uso: bash tools/cloud-setup.sh   (hook SessionStart e Setup script do ambiente)
set -uo pipefail
D="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
for s in rea superpowers lsp serena; do
  "$D/$s/install.sh" || echo "[cloud-setup] AVISO: $s falhou"
done
