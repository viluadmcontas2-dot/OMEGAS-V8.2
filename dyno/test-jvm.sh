#!/bin/sh
set -eu
: "${DYNO_JVM_LIBS:?Diretório com Kotlin 2.0.21 e JUnit 4.13.2}"
DYNO_JAVA=${DYNO_JAVA:-java}
classes=$(mktemp -d)
trap 'find "$classes" -depth -delete' EXIT
root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
libs="$DYNO_JVM_LIBS"
cp="$libs/kotlin-stdlib-2.0.21.jar:$libs/junit-4.13.2.jar:$libs/hamcrest-core-1.3.jar:$libs/json-20240303.jar"
"$DYNO_JAVA" -cp "$libs/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect \
    -classpath "$cp" -d "$classes" \
    "$root"/src/main/java/com/omegas/dyno/lab/Protocols.kt \
    "$root"/src/main/java/com/omegas/dyno/lab/TelemetryHub.kt \
    "$root"/src/main/java/com/omegas/dyno/lab/DriveResponseAnalyzer.kt \
    "$root"/src/test/java/com/omegas/dyno/lab/*.kt
"$DYNO_JAVA" -cp "$classes:$cp" org.junit.runner.JUnitCore \
    com.omegas.dyno.lab.ProtocolTest \
    com.omegas.dyno.lab.TelemetryHubTest \
    com.omegas.dyno.lab.DriveResponseAnalyzerTest \
    com.omegas.dyno.lab.PassiveContractTest
