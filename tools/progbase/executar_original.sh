#!/usr/bin/env bash
# Executa o ProgBase.exe original sob Wine+Xvfb e tira prints (F1..F5). NÃO patcheia nada.
# Requer: PB_DIR com ProgBase.exe, Resources.dll e License.dat (do dono; não versionado); wine, xvfb, xdotool, imagemagick.
# Uso: PB_DIR=/caminho OUT=/saida tools/progbase/executar_original.sh
set -u
: "${PB_DIR:?defina PB_DIR}"; OUT="${OUT:-$PB_DIR/prints}"; mkdir -p "$OUT"
export WINEPREFIX="${WINEPREFIX:-$PB_DIR/prefix}" WINEARCH=win32 WINEDEBUG=-all DISPLAY=:78
cd "$PB_DIR"; Xvfb :78 -screen 0 1280x720x24 >/dev/null 2>&1 & XP=$!
sleep 2; wine ProgBase.exe >"$OUT/run.log" 2>&1 & sleep 35; import -window root "$OUT/main.png"
for k in F1 F2 F3 F4 F5; do xdotool key $k; sleep 12; import -window root "$OUT/$k.png"; xdotool key Escape; sleep 3; done
wineserver -k 2>/dev/null; kill $XP 2>/dev/null
