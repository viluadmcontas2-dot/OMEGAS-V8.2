#!/usr/bin/env bash
# Roda o NativeAutoCalMonitor REAL contra uma ECU falsa (tools/jvm/AquisicaoEmulada.kt). Sem Android SDK.
# Baixa o kotlinc 2.0.21 e o org.json uma vez em build/jvm-tools (não vai para o git).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; T="$ROOT/build/jvm-tools"; J="$ROOT/app/src/main/java/com/omegas/prohub"
mkdir -p "$T/stub/android/os" "$T/stub/android/content"
[ -x "$T/kotlinc/bin/kotlinc" ] || { curl -fsSL -o "$T/k.zip" https://github.com/JetBrains/kotlin/releases/download/v2.0.21/kotlin-compiler-2.0.21.zip && unzip -q -o "$T/k.zip" -d "$T"; }
[ -f "$T/json.jar" ] || curl -fsSL -o "$T/json.jar" https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar
printf 'package android.os\nobject SystemClock { @JvmStatic var now: Long = 100000L\n @JvmStatic fun elapsedRealtime(): Long = now\n @JvmStatic fun uptimeMillis(): Long = now }\n' > "$T/stub/android/os/SystemClock.kt"
cat > "$T/stub/android/content/Context.kt" <<'KT'
package android.content
interface SharedPreferences { fun getInt(k: String, d: Int): Int; fun edit(): Editor
  interface Editor { fun putInt(k: String, v: Int): Editor; fun apply() } }
open class Context { companion object { const val MODE_PRIVATE = 0 }
  open fun getSharedPreferences(name: String, mode: Int): SharedPreferences = object : SharedPreferences {
    val m = HashMap<String, Int>()
    override fun getInt(k: String, d: Int) = m[k] ?: d
    override fun edit() = object : SharedPreferences.Editor { override fun putInt(k: String, v: Int) = this.also { m[k] = v }; override fun apply() {} } } }
KT
# Todo o código de produção que não depende de Android (+ as duas configurações que só usam Context como tipo).
FILES=$(grep -L "import android\|import androidx" $(find "$J" -name '*.kt'))
FILES="$FILES $J/learning/LearningToleranceSettings.kt $J/learning/LearningTemperatureSettings.kt $J/autocal/NativeAutoCalMonitor.kt"
# Remove arquivos que dependem de pacotes de Android indiretamente (a lista é estável; ver rodadas de compilação).
for i in 1 2 3 4 5 6 7 8 9 10; do
  OUT=$("$T/kotlinc/bin/kotlinc" -nowarn -cp "$T/json.jar" -d "$T/out.jar" "$T/stub/android/os/SystemClock.kt" "$T/stub/android/content/Context.kt" "$ROOT/tools/jvm/AquisicaoEmulada.kt" $FILES 2>&1 || true)
  BAD=$(echo "$OUT" | grep -E "error:" | sed -E 's#^([^:]+):.*#\1#' | sort -u | grep -v AquisicaoEmulada || true)
  echo "rodada $i fora: $(echo $BAD | xargs -n1 basename 2>/dev/null | tr "\n" " ")" >&2
  [ -z "$BAD" ] && break
  for b in $BAD; do case "$b" in /*) abs="$b";; *) abs="$ROOT/$b";; esac; FILES=$(echo $FILES | tr ' ' '\n' | grep -v -x "$abs" | tr '\n' ' '); done
done
echo "$OUT" | grep -E "error:" && { echo "Não compilou"; exit 2; }
java -Dstdout.encoding=UTF-8 -cp "$T/out.jar:$T/json.jar:$("$T/kotlinc/bin/kotlinc" -version >/dev/null 2>&1; echo $T/kotlinc/lib/kotlin-stdlib.jar)" com.omegas.prohub.autocal.AquisicaoEmuladaKt
