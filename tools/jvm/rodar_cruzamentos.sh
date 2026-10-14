#!/usr/bin/env bash
# Cruzamento de comandos do Refino contra ECU falsa (JVM, sem Android). Uso: tools/jvm/rodar_cruzamentos.sh [mundos] [passos]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; T="$ROOT/build/jvm-tools"; J="$ROOT/app/src/main/java/com/omegas/prohub"
mkdir -p "$T/stub/android/os" "$T/stub/android/content"
[ -x "$T/kotlinc/bin/kotlinc" ] || { curl -fsSL -o "$T/k.zip" https://github.com/JetBrains/kotlin/releases/download/v2.0.21/kotlin-compiler-2.0.21.zip && unzip -q -o "$T/k.zip" -d "$T"; }
[ -f "$T/json.jar" ] || curl -fsSL -o "$T/json.jar" https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar
printf 'package android.os\nobject SystemClock { @JvmStatic var now: Long = 100000L\n @JvmStatic fun elapsedRealtime(): Long = now\n @JvmStatic fun uptimeMillis(): Long = now }\n' > "$T/stub/android/os/SystemClock.kt"
FILES=$(grep -L "import android\|import androidx" $(find "$J" -name '*.kt') | tr '\n' ' ')
for i in 1 2 3 4 5 6; do
  OUT=$("$T/kotlinc/bin/kotlinc" -nowarn -cp "$T/json.jar" -d "$T/cruz.jar" "$T/stub/android/os/SystemClock.kt" "$ROOT/tools/jvm/CruzamentosEmulado.kt" $FILES 2>&1 || true)
  BAD=$(echo "$OUT" | grep -E "error:" | sed -E 's#^([^:]+):.*#\1#' | sort -u | grep -v CruzamentosEmulado || true)
  [ -z "$BAD" ] && break
  for b in $BAD; do case "$b" in /*) a="$b";; *) a="$ROOT/$b";; esac; FILES=$(echo $FILES | tr ' ' '\n' | grep -v -x "$a" | tr '\n' ' '); done
done
echo "$OUT" | grep -E "error:" && { echo "Não compilou"; exit 2; }
java -Dstdout.encoding=UTF-8 -cp "$T/cruz.jar:$T/json.jar:$T/kotlinc/lib/kotlin-stdlib.jar" com.omegas.prohub.autocal.CruzamentosEmuladoKt "$@" 2>&1 | grep -v "^Picked"
