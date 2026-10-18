#!/usr/bin/env bash
# Custo por quadro do caminho "ECU -> estado -> envelope da WebView" (puro, sem Android). Antes = fontes do commit dado
# (padrão HEAD); Depois = árvore atual. Uso: tools/jvm/quadro_ao_vivo/medir.sh [commit]. Precisa de build/jvm-tools (ver rodar_aquisicao_emulada.sh).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"; T="$ROOT/build/jvm-tools"; K="$T/kotlinc/bin/kotlinc"; L="$T/kotlinc/lib/kotlin-stdlib.jar"
BASE="${1:-HEAD}"; W="$(mktemp -d)"; P=app/src/main/java/com/omegas/prohub
mkdir -p "$W/h"; for f in telemetry/TelemetryStateStore.kt runtime/RuntimeSnapshotBus.kt; do mkdir -p "$W/h/$(dirname $f)"; git -C "$ROOT" show "$BASE:$P/$f" > "$W/h/$f"; done
"$K" -nowarn -cp "$T/json.jar" -d "$W/a.jar" "$ROOT/tools/jvm/quadro_ao_vivo/Antes.kt" "$W/h/telemetry/TelemetryStateStore.kt" "$W/h/runtime/RuntimeSnapshotBus.kt"
"$K" -nowarn -cp "$T/json.jar" -d "$W/b.jar" "$ROOT/tools/jvm/quadro_ao_vivo/Depois.kt" "$ROOT/$P/telemetry/TelemetryStateStore.kt" "$ROOT/$P/runtime/RuntimeSnapshotBus.kt"
java -cp "$W/a.jar:$T/json.jar:$L" AntesKt; java -cp "$W/b.jar:$T/json.jar:$L" DepoisKt
