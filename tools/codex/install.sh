#!/usr/bin/env bash
# OMEGAS — Codex MCP persistente na nuvem.
# Instala/atualiza o CLI do Codex (@openai/codex) num prefixo global e deixa o binário pronto para o
# servidor MCP declarado no .mcp.json do projeto. Idempotente: confere a versão antes de baixar.
# Uso: tools/codex/install.sh            (chamado pelo hook SessionStart)
#      tools/codex/install.sh --doctor   (só diagnóstico)
set -euo pipefail

CODEX_VERSION="${CODEX_VERSION:-0.153.4}"
CODEX_PREFIX="${CODEX_PREFIX:-/opt/codex-mcp}"
CODEX_BIN="$CODEX_PREFIX/bin/codex"

log() { printf '[codex-install] %s\n' "$*"; }

# ---- Node / npm ---------------------------------------------------------------------------------
node_major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
if [ "$node_major" -lt 18 ]; then log "Node >= 18 necessário (achei $(node -v 2>/dev/null || echo nenhum))"; exit 1; fi

# ---- Codex CLI (servidor MCP via 'codex mcp-server') --------------------------------------------
have="$("$CODEX_BIN" --version 2>/dev/null | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || true)"
if [ "$have" != "$CODEX_VERSION" ]; then
  log "instalando Codex ${CODEX_VERSION} em ${CODEX_PREFIX} (achei '${have:-nenhum}')"
  mkdir -p "$CODEX_PREFIX"
  npm i -g --prefix "$CODEX_PREFIX" "@openai/codex@${CODEX_VERSION}" >/dev/null 2>&1 \
    || { log "ERRO: npm i -g @openai/codex@${CODEX_VERSION} falhou"; exit 1; }
fi
[ -x "$CODEX_BIN" ] || { log "ERRO: binário não encontrado em $CODEX_BIN após instalar"; exit 1; }
log "Codex ok: $("$CODEX_BIN" --version 2>&1 | head -1) ($CODEX_BIN)"

if [ "${1:-}" = "--doctor" ]; then exit 0; fi
log "pronto"
