#!/usr/bin/env bash
# OMEGAS — Superpowers (plugin do Claude Code) idempotente na nuvem.
# Registra o marketplace e instala/habilita o plugin. Cada etapa confere antes de agir:
# re-rodar não duplica nada (o CLI responde "already ..." com exit 0).
# Uso: tools/superpowers/install.sh   (chamado pelo hook SessionStart ou pelo setup do ambiente)
set -euo pipefail

MARKETPLACE="obra/superpowers-marketplace"
PLUGIN="superpowers@superpowers-marketplace"
log() { printf '[superpowers-install] %s\n' "$*"; }

if ! command -v claude >/dev/null 2>&1; then
  log "claude CLI ausente; pulando (Superpowers depende do Claude Code)"
  exit 0
fi

# Marketplace: idempotente — se já estiver no disco, o CLI apenas confirma.
claude plugin marketplace add "$MARKETPLACE" >/dev/null 2>&1 \
  || log "AVISO: não registrei o marketplace $MARKETPLACE (rede/permite?); tentando instalar mesmo assim"

# Plugin: idempotente — se já instalado, o CLI apenas confirma.
claude plugin install "$PLUGIN" >/dev/null 2>&1 \
  || log "AVISO: comando de instalação do $PLUGIN retornou erro"

if claude plugin list 2>/dev/null | grep -q "superpowers@superpowers-marketplace"; then
  log "Superpowers ok (instalado e habilitado)"
else
  log "AVISO: Superpowers não confirmado na lista de plugins"
fi
