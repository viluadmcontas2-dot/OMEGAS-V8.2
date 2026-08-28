@echo off
setlocal EnableExtensions

where adb >nul 2>nul || (
  echo [ERRO] adb nao encontrado no PATH.
  exit /b 1
)

for /f "delims=" %%T in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "STAMP=%%T"
set "OUT=%CD%\OMEGAS-122A-%STAMP%"
mkdir "%OUT%" >nul 2>nul

set /a DEVICE_COUNT=0
for /f "skip=1 tokens=1,2" %%A in ('adb devices') do (
  if "%%B"=="device" set /a DEVICE_COUNT+=1
)
if not "%DEVICE_COUNT%"=="1" (
  echo [ERRO] Conecte exatamente um aparelho via adb. Detectados: %DEVICE_COUNT%
  exit /b 2
)

echo [1/3] Identidade do hardware...
(
  echo capturedAt=%DATE% %TIME%
  adb shell getprop ro.product.board
  adb shell getprop ro.hardware
  adb shell getprop ro.product.cpu.abilist
  adb shell getprop ro.build.version.sdk
  adb shell getprop ro.build.version.release
  adb shell getprop ro.build.fingerprint
) > "%OUT%\device-identity.txt" 2>&1

echo [2/3] Snapshot de memoria/processo inicial...
adb shell dumpsys meminfo com.omegas.prohub > "%OUT%\meminfo-start.txt" 2>&1
adb shell top -b -n 1 > "%OUT%\top-start.txt" 2>&1

echo [3/3] Capturando receipt OMEGAS-122A.
echo Rode a sessao AutoCal normal no aparelho. Pressione Ctrl+C quando terminar.
echo Saida: %OUT%

echo capturedAt=%DATE% %TIME%> "%OUT%\capture-start.txt"
adb logcat -v threadtime OMEGAS-122A:I *:S > "%OUT%\omegas-122a.log"

endlocal
