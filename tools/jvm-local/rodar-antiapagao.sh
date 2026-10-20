#!/bin/bash
# Roda os testes JUnit do anti-apagão (escritores reais + diálogo com a ECU emulada) SEM Android SDK, em ~1 min.
# Só o núcleo JVM é compilado (nada de android.*). Baixa o compilador Kotlin 2.0.21 uma vez em $CACHE.
# Uso: tools/jvm-local/rodar-antiapagao.sh        (o CI roda a suíte inteira com gradle; isto é o atalho local)
set -euo pipefail
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$RAIZ"
CACHE="${OMEGAS_JVM_CACHE:-/tmp/omegas-kt}"; mkdir -p "$CACHE"
if [ ! -x "$CACHE/kotlinc/bin/kotlinc" ]; then
  curl -sSL -o "$CACHE/kc.zip" https://github.com/JetBrains/kotlin/releases/download/v2.0.21/kotlin-compiler-2.0.21.zip
  python3 -I -m zipfile -e "$CACHE/kc.zip" "$CACHE"; chmod +x "$CACHE"/kotlinc/bin/*
fi
baixar() { [ -s "$CACHE/$2" ] && [ "$(wc -c <"$CACHE/$2")" -gt 10000 ] || curl -sSL -o "$CACHE/$2" "https://repo1.maven.org/maven2/$1/$2"; }
baixar junit/junit/4.13.2 junit-4.13.2.jar
baixar org/json/json/20240303 json-20240303.jar
HAM="$(find / -name 'hamcrest-core-1.3.jar' -size +10k 2>/dev/null | head -1 || true)"; [ -n "$HAM" ] || { baixar org/hamcrest/hamcrest-core/1.3 hamcrest-core-1.3.jar; HAM="$CACHE/hamcrest-core-1.3.jar"; }
M=app/src/main/java/com/omegas/prohub; T=app/src/test/java/com/omegas/prohub/autocal
CP="$CACHE/junit-4.13.2.jar:$HAM:$CACHE/json-20240303.jar"
rm -rf "$CACHE/out"; mkdir -p "$CACHE/out"
"$CACHE/kotlinc/bin/kotlinc" -nowarn -cp "$CP" -d "$CACHE/out" \
  $M/ecu/Mp48SerialScheduler.kt $M/ecu/Mp48BackpressureScheduler.kt $M/util/RuntimeBackpressurePolicy.kt $M/ecu/AutoCalProtocol.kt $M/ecu/AutoCalScale.kt \
  $M/ecu/Mp48Protocol.kt $M/ecu/Mp48TelemetryScale.kt $M/autocal/AntiStalloFlagWriter.kt $M/autocal/AntiStalloParamsWriter.kt $M/autocal/AntiStalloFlagCore.kt \
  $M/autocal/AntiStalloParamsCore.kt $M/usb/UsbProtocolReply.kt $M/learning/NativeAnchorTelemetryWindow.kt \
  $T/AntiStalloDialogoEcuEmuladaTest.kt $T/AntiStalloFlagWriterTest.kt $T/AntiStalloParamsWriterTest.kt $T/AntiStalloFlagCoreTest.kt $T/AntiStalloParamsCoreTest.kt
java -cp "$CACHE/out:$CP:$CACHE/kotlinc/lib/kotlin-stdlib.jar" org.junit.runner.JUnitCore \
  com.omegas.prohub.autocal.AntiStalloDialogoEcuEmuladaTest com.omegas.prohub.autocal.AntiStalloFlagWriterTest com.omegas.prohub.autocal.AntiStalloParamsWriterTest \
  com.omegas.prohub.autocal.AntiStalloFlagCoreTest com.omegas.prohub.autocal.AntiStalloParamsCoreTest 2>&1 | grep -v '^\s*at \|JAVA_TOOL'
