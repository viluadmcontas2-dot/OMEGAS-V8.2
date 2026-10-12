#!/usr/bin/env bash
# OMEGAS — ambiente de engenharia reversa (REA) persistente na nuvem.
# Instala/atualiza o REA, o JADX headless (APK) e o Ghidra (binários nativos), registra o MCP do REA no
# Claude Code e exporta as variáveis para a sessão. Idempotente: cada etapa confere antes de baixar.
# Uso: tools/rea/install.sh            (chamado pelo hook SessionStart ou pelo script de setup do ambiente)
#      tools/rea/install.sh --doctor   (só diagnóstico)
set -euo pipefail

REA_VERSION="${REA_VERSION:-6.4.0}"
JADX_VERSION="0.7.1"
JADX_URL="https://github.com/1013503897/jadx-headless-mcp/releases/download/v${JADX_VERSION}/jadx-headless-mcp-${JADX_VERSION}-all.jar"
JADX_SHA256="6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75"   # auditado pelo REA (src/android/JadxRelease.ts)
GHIDRA_VERSION="${GHIDRA_VERSION:-12.1.4}"                                            # REA 6.4 exige 12.1.x
GHIDRA_TAG="Ghidra_${GHIDRA_VERSION}_build"
# Zip oficial da tag 12.1.4 (nome resolvido por tentativa em 2026-10-12: a API de releases é negada pelo proxy da nuvem,
# mas o download direto passa). Troque GHIDRA_ZIP_URL junto com GHIDRA_VERSION ao atualizar.
GHIDRA_ZIP_URL="${GHIDRA_ZIP_URL:-https://github.com/NationalSecurityAgency/ghidra/releases/download/Ghidra_12.1.4_build/ghidra_12.1.4_PUBLIC_20260921.zip}"
GHIDRA_ZIP_SHA256="${GHIDRA_ZIP_SHA256:-ddac49f903da9d5bac833e5cc79395098b9c33cfd3279be5f31bd00387d2d4db}"   # conferido no download de 2026-10-12

TOOLS="${REA_TOOLS_DIR:-$HOME/.omegas-rea}"
mkdir -p "$TOOLS"
log() { printf '[rea-install] %s\n' "$*"; }

# ---- Node / npm ---------------------------------------------------------------------------------
node_major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
if [ "$node_major" -lt 22 ]; then log "Node >= 22 necessário (achei $(node -v 2>/dev/null || echo nenhum))"; exit 1; fi

# ---- REA (CLI + servidor MCP) --------------------------------------------------------------------
if ! npx -y "rea-agents@${REA_VERSION}" --help >/dev/null 2>&1; then log "REA ${REA_VERSION} indisponível no npm"; exit 1; fi
log "REA ${REA_VERSION} ok"

# ---- JADX headless MCP (APK) ---------------------------------------------------------------------
JADX_JAR="$TOOLS/jadx-headless-mcp-${JADX_VERSION}-all.jar"
if [ ! -f "$JADX_JAR" ] || [ "$(sha256sum "$JADX_JAR" | cut -d' ' -f1)" != "$JADX_SHA256" ]; then
  log "baixando JADX headless ${JADX_VERSION}"
  curl -fsSL -o "$JADX_JAR.part" "$JADX_URL"
  echo "$JADX_SHA256  $JADX_JAR.part" | sha256sum -c - >/dev/null
  mv "$JADX_JAR.part" "$JADX_JAR"
fi
log "JADX ok: $JADX_JAR"

# ---- Ghidra (binários nativos) -------------------------------------------------------------------
GHIDRA_DIR="$TOOLS/ghidra_${GHIDRA_VERSION}_PUBLIC"
if [ ! -x "$GHIDRA_DIR/support/analyzeHeadless" ]; then
  url="$GHIDRA_ZIP_URL"
  if [ -z "$url" ]; then
    # Resolve o nome do zip pela API do GitHub (gh já vem configurado para o proxy da nuvem).
    url="$(gh api "repos/NationalSecurityAgency/ghidra/releases/tags/${GHIDRA_TAG}" --jq '.assets[] | select(.name|endswith(".zip")) | .browser_download_url' 2>/dev/null | head -1 || true)"
  fi
  if [ -z "$url" ]; then
    log "AVISO: não consegui resolver o zip do Ghidra ${GHIDRA_VERSION} (acesso ao repositório NationalSecurityAgency/ghidra negado?). Binários nativos ficam indisponíveis; APK/JS/.NET seguem funcionando. Defina GHIDRA_ZIP_URL para forçar."
  else
    log "baixando Ghidra ${GHIDRA_VERSION}"
    curl -fsSL -o "$TOOLS/ghidra.zip.part" "$url"
    if [ -n "$GHIDRA_ZIP_SHA256" ]; then echo "$GHIDRA_ZIP_SHA256  $TOOLS/ghidra.zip.part" | sha256sum -c - >/dev/null || { log "ERRO: SHA-256 do Ghidra não confere"; rm -f "$TOOLS/ghidra.zip.part"; exit 1; }; fi
    rm -rf "$TOOLS/ghidra.extract"; mkdir -p "$TOOLS/ghidra.extract"
    unzip -q "$TOOLS/ghidra.zip.part" -d "$TOOLS/ghidra.extract"
    mv "$TOOLS/ghidra.extract"/ghidra_* "$GHIDRA_DIR"
    rm -rf "$TOOLS/ghidra.zip.part" "$TOOLS/ghidra.extract"
    chmod +x "$GHIDRA_DIR/support/analyzeHeadless"
  fi
fi
[ -x "$GHIDRA_DIR/support/analyzeHeadless" ] && log "Ghidra ok: $GHIDRA_DIR"

# ---- Variáveis para a sessão e para o MCP --------------------------------------------------------
export REA_JADX_MCP_JAR="$JADX_JAR"
[ -x "$GHIDRA_DIR/support/analyzeHeadless" ] && export GHIDRA_INSTALL_DIR="$GHIDRA_DIR"
export REA_BROWSER_NO_SANDBOX=1   # Chromium na nuvem roda como root
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export REA_JADX_MCP_JAR=\"$JADX_JAR\""
    [ -n "${GHIDRA_INSTALL_DIR:-}" ] && echo "export GHIDRA_INSTALL_DIR=\"$GHIDRA_INSTALL_DIR\""
    echo "export REA_BROWSER_NO_SANDBOX=1"
  } >> "$CLAUDE_ENV_FILE"
fi

# ---- Registro do MCP no Claude Code (pessoal; o do projeto fica em .mcp.json) --------------------
if [ "${1:-}" != "--doctor" ]; then
  npx -y "rea-agents@${REA_VERSION}" setup --yes --client claude_code --format json >/dev/null 2>&1 || log "AVISO: rea setup falhou (registro pessoal); o .mcp.json do projeto continua valendo"
  # O rea setup grava GHIDRA_INSTALL_DIR/JAVA_HOME mas não o JAR do JADX: completa o registro pessoal (~/.claude.json).
  python3 - "$JADX_JAR" <<'PY' || log "AVISO: não completei o registro pessoal com REA_JADX_MCP_JAR"
import json, os, sys
p = os.path.expanduser("~/.claude.json")
try:
    d = json.load(open(p))
except Exception:
    sys.exit(0)
srv = d.setdefault("mcpServers", {}).get("rea")
if srv is not None:
    env = srv.setdefault("env", {})
    env["REA_JADX_MCP_JAR"] = sys.argv[1]
    env["REA_BROWSER_NO_SANDBOX"] = "1"
    json.dump(d, open(p, "w"), indent=2)
PY
fi
npx -y "rea-agents@${REA_VERSION}" doctor --format json 2>/dev/null | python3 -c '
import json,sys
d=json.load(sys.stdin); doc=d.get("doctor",d)
for p in doc.get("providerInspections",[]): print("[rea-install] motor", p["id"], "disponível" if p.get("available") else "indisponível")
' || true
log "pronto"
