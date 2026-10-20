#!/bin/bash
# Roda os testes JUnit da volta à gasolina (núcleo + escritor + leitor) SEM Android SDK. Mesmo atalho de rodar-antiapagao.sh
# (kotlinc 2.0.21 + JUnit 4.13.2 em $CACHE). O CI roda a suíte inteira com gradle; isto é o atalho local.
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
OUT="$CACHE/out-volta"; rm -rf "$OUT"; mkdir -p "$OUT"
"$CACHE/kotlinc/bin/kotlinc" -nowarn -cp "$CP" -d "$OUT" \
  $M/ecu/Mp48SerialScheduler.kt $M/ecu/Mp48BackpressureScheduler.kt $M/util/RuntimeBackpressurePolicy.kt $M/ecu/AutoCalProtocol.kt $M/ecu/AutoCalScale.kt \
  $M/ecu/Mp48Protocol.kt $M/ecu/Mp48TelemetryScale.kt $M/usb/UsbProtocolReply.kt $M/learning/NativeAnchorTelemetryWindow.kt \
  $M/autocal/VoltaGasolinaCore.kt $M/autocal/VoltaGasolinaWriter.kt $M/autocal/EcuParameterSnapshot.kt \
  $T/VoltaGasolinaCoreTest.kt $T/VoltaGasolinaWriterTest.kt $T/EcuParameterSnapshotTest.kt
java -cp "$OUT:$CP:$CACHE/kotlinc/lib/kotlin-stdlib.jar" org.junit.runner.JUnitCore \
  com.omegas.prohub.autocal.VoltaGasolinaCoreTest com.omegas.prohub.autocal.VoltaGasolinaWriterTest com.omegas.prohub.autocal.EcuParameterSnapshotTest 2>&1 | grep -v '^\s*at \|JAVA_TOOL'
