#!/bin/bash
set -euo pipefail
root="$1"
out="$2"
jvm=/tmp/omegas-jvm
cp="$jvm/android-all.jar:$jvm/stubs:$jvm/coroutines.jar"
cd "$root"
mkdir -p "$out"
find app/src/main/java -name '*.kt' > "$out/main.args"
"$jvm/kotlinc/bin/kotlinc" -J-Xmx2g -jvm-target 17 -module-name app -Xnullability-annotations=@android.annotation:warn -cp "$cp" @"$out/main.args" -d "$out/main"
find app/src/test -name '*.kt' > "$out/tests.args"
"$jvm/kotlinc/bin/kotlinc" -J-Xmx2g -jvm-target 17 -module-name tests -Xnullability-annotations=@android.annotation:warn -Xfriend-paths="$out/main" -cp "$out/main:$cp:$jvm/junit.jar:$jvm/hamcrest.jar" @"$out/tests.args" -d "$out/tests"
mapfile -t classes < <(find "$out/tests" -name '*Test.class' | sed "s|$out/tests/||;s|/|.|g;s|.class$||" | sort)
java -cp "$out/tests:$out/main:$cp:$jvm/junit.jar:$jvm/hamcrest.jar:$jvm/kotlinc/lib/kotlin-stdlib.jar:$jvm/kotlinc/lib/kotlin-reflect.jar" org.junit.runner.JUnitCore "${classes[@]}"
