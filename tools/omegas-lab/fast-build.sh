#!/usr/bin/env bash
# Dinamômetro Ômegas: incremental Android build, NEVER launches apps or talks to a car.
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"
MODE="${1:-status}"
LOG_ROOT="${OMEGAS_BUILD_LOG_DIR:-$HOME/.cache/omegas_workflow/builds}"
mkdir -p "$LOG_ROOT"
cd "$ROOT"

fail() { printf 'ERRO: %s\n' "$*" >&2; exit 2; }
graph_bin() {
  if command -v graphify >/dev/null 2>&1; then command -v graphify
  elif [ -x "$HOME/.local/share/graphify-venv/bin/graphify" ]; then
    printf '%s\n' "$HOME/.local/share/graphify-venv/bin/graphify"
  else return 1; fi
}
check_ui() {
  if command -v node >/dev/null 2>&1; then
    node --check dyno/src/main/assets/dashboard.js
    node --test dyno/tests/ui.test.cjs
  else
    printf 'AVISO: Node.js indisponível; os testes JS não foram executados.\n' >&2
    return 2
  fi
}
check_sdk() {
  export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  [ -r "$ANDROID_HOME/platforms/android-35/android.jar" ] ||
    fail "Android SDK 35 ausente. Não há tentativa de instalar APK ou abrir a UI."
  if [ -z "${JAVA_HOME:-}" ] && [ -d "/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk" ]; then
    export JAVA_HOME="/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk"
  fi
  command -v java >/dev/null 2>&1 || fail "Java não instalado"
  [ -f gradlew ] || fail "Gradle Wrapper ausente"
}
run_gradle() {
  check_sdk
  local -a args=(--no-daemon --max-workers=1 --console=plain)
  # On native Termux Android, replace x86_64 Maven aapt2 with ARM64 binary.
  if command -v aapt2 >/dev/null 2>&1; then
    args+=("-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)")
  fi
  if [ "${OMEGAS_OFFLINE:-0}" = "1" ]; then args+=(--offline); fi
  local log="$LOG_ROOT/$(date +%Y%m%d)-$(date +%H%M%S)-$MODE.log"
  printf 'Gradle local [prioridade reduzida] • %s\n' "$log"
  if nice -n 12 bash ./gradlew "${args[@]}" "$@" 2>&1 | tee "$log"; then
    printf 'BUILD_RESULT=PASS\n' | tee -a "$log"
  else
    printf 'BUILD_RESULT=FAIL\n' | tee -a "$log"
    return 1
  fi
}
case "$MODE" in
  status)
    printf 'LAB_SHA='
    git rev-parse --short HEAD
    printf 'ANDROID_HOME=%s\n' "${ANDROID_HOME:-$HOME/android-sdk}"
    if [ -r "${ANDROID_HOME:-$HOME/android-sdk}/platforms/android-35/android.jar" ]; then echo 'SDK35=READY';else echo 'SDK35=MISSING';fi
    if command -v aapt2 >/dev/null 2>&1;then echo 'ARM_AAPT2=READY';else echo 'ARM_AAPT2=MISSING';fi
    if command -v java >/dev/null 2>&1;then java -version 2>&1 | head -1;else echo 'JAVA=MISSING';fi
    if [ -f graphify-out/graph.json ];then
      python3 -c "import json;g=json.load(open('graphify-out/graph.json'));print('GRAPH_NODES='+str(len(g.get('nodes',[]))))"
    else echo 'GRAPH=NOT_INDEXED';fi
    test ! -f dyno/build/outputs/apk/debug/dyno-debug.apk || {
      sha256sum dyno/build/outputs/apk/debug/dyno-debug.apk
      echo 'LOCAL_UNSIGNED_APK_PRESENT'
    }
    ;;
  graph)
    shift
    [ "$#" -gt 0 ] || fail "Use: bash tools/omegas-lab/fast-build.sh graph 'Pergunta sobre o código'"
    G="$(graph_bin)" || fail "Graphify não está instalado"
    [ -r graphify-out/graph.json ] || fail "graphify-out/graph.json não disponível"
    "$G" query "$*" --budget 500 --depth 2 --graph graphify-out/graph.json
    ;;
  ui)
    check_ui
    ;;
  test)
    check_ui
    run_gradle :dyno:testDebugUnitTest
    ;;
  hot)
    # Small iteration: compile only the experimental APK and reuse Gradle caches.
    run_gradle :dyno:assembleDebug
    [ -s dyno/build/outputs/apk/debug/dyno-debug.apk ] || fail "Gradle did not generate APK"
    sha256sum dyno/build/outputs/apk/debug/dyno-debug.apk
    ;;
  qa)
    # Integration gate: always test before producing the next APK.
    check_ui
    run_gradle :dyno:testDebugUnitTest :dyno:assembleDebug
    [ -s dyno/build/outputs/apk/debug/dyno-debug.apk ] || fail "APK ausente"
    unzip -t dyno/build/outputs/apk/debug/dyno-debug.apk >/dev/null
    sha256sum dyno/build/outputs/apk/debug/dyno-debug.apk
    ;;
  *) fail "Modo inválido: $MODE. Use status|graph|ui|test|hot|qa.";;
esac
