#!/usr/bin/env bash
# OMEGAS — plugins LSP (TypeScript e Python) idempotentes na nuvem.
# Instala os servidores de linguagem só se faltarem e registra/instala os plugins do marketplace
# oficial. Re-rodar não duplica nada. Chamado pelo hook SessionStart ou pelo setup do ambiente.
set -uo pipefail

MARKETPLACE="anthropics/claude-plugins-official"
PLUGINS=("typescript-lsp@claude-plugins-official" "pyright-lsp@claude-plugins-official")
log() { printf '[lsp-install] %s\n' "$*"; }

# Servidores de linguagem (os plugins só ligam a eles; sem o binário o LSP não sobe).
if ! command -v typescript-language-server >/dev/null 2>&1; then
  if command -v npm >/dev/null 2>&1; then
    npm install -g typescript typescript-language-server >/dev/null 2>&1 \
      && log "typescript-language-server instalado" \
      || log "AVISO: falhou instalar typescript-language-server"
  else
    log "AVISO: npm ausente; TypeScript LSP sem servidor"
  fi
fi
if ! command -v pyright-langserver >/dev/null 2>&1; then
  if command -v npm >/dev/null 2>&1; then
    npm install -g pyright >/dev/null 2>&1 && log "pyright instalado" || log "AVISO: falhou instalar pyright"
  else
    log "AVISO: pyright-langserver ausente e sem npm"
  fi
fi

if ! command -v claude >/dev/null 2>&1; then
  log "claude CLI ausente; pulando plugins"
  exit 0
fi

claude plugin marketplace add "$MARKETPLACE" >/dev/null 2>&1 \
  || log "AVISO: marketplace $MARKETPLACE não registrado (rede?)"
for p in "${PLUGINS[@]}"; do
  claude plugin install "$p" >/dev/null 2>&1 || log "AVISO: instalação de $p retornou erro"
done

list="$(claude plugin list 2>/dev/null || true)"
for p in "${PLUGINS[@]}"; do
  if printf '%s' "$list" | grep -q "$p"; then log "$p ok"; else log "AVISO: $p não confirmado"; fi
done
exit 0
