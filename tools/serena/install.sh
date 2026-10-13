#!/usr/bin/env bash
# OMEGAS — Serena (MCP de navegação semântica de código, via LSP) idempotente na nuvem.
# Esquenta o cache do uvx (versão fixa) e registra o MCP no escopo de usuário: vale em qualquer
# projeto. Re-rodar não duplica nada. Uso: hook SessionStart ou Setup script do ambiente.
set -uo pipefail
VER="2.0.0"
log() { printf '[serena-install] %s\n' "$*"; }

if ! command -v uvx >/dev/null 2>&1; then
  command -v pip3 >/dev/null 2>&1 && pip3 install -q uv >/dev/null 2>&1 || true
fi
command -v uvx >/dev/null 2>&1 || { log "AVISO: uvx ausente; Serena não instalada"; exit 0; }

uvx --from "serena-agent==$VER" serena --help >/dev/null 2>&1 \
  && log "serena-agent $VER no cache" || log "AVISO: falhou baixar serena-agent $VER"

command -v claude >/dev/null 2>&1 || { log "claude CLI ausente; MCP não registrado"; exit 0; }
out="$(claude mcp add --scope user serena -- uvx --from "serena-agent==$VER" serena start-mcp-server \
  --context claude-code --project-from-cwd --enable-web-dashboard false --open-web-dashboard false 2>&1)" \
  && log "MCP serena registrado (usuário)" \
  || { printf '%s' "$out" | grep -qi "already exists" && log "MCP serena já registrado (usuário)" \
       || log "AVISO: falhou registrar o MCP serena: $out"; }
exit 0
