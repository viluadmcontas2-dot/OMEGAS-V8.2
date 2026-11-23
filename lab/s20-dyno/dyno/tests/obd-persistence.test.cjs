const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const source = f => fs.readFileSync(path.join(__dirname, '../src/main/', f), 'utf8');

test('OBD has exactly one foreground service owner with connectedDevice', () => {
  const manifest = source('AndroidManifest.xml');
  assert.match(manifest, /name="\.ObdKeepAliveService"[^>]*exported="false"[^>]*foregroundServiceType="connectedDevice"/);
  assert.match(manifest, /android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE/);
  const service = source('java/com/omegas/dyno/lab/ObdKeepAliveService.kt');
  assert.match(service, /startForeground\(NOTIFICATION_ID, notification\)/);
  assert.match(service, /START_STICKY/);
  assert.match(service, /DynoRuntime\.get\(applicationContext\)\.obd\.start\(savedId\)/);
});

test('persist the selected bonded OBD MAC and reconnect without requiring the Activity', () => {
  const activity = source('java/com/omegas/dyno/lab/DynoActivity.kt');
  const runtime = source('java/com/omegas/dyno/lab/DynoRuntime.kt');
  assert.match(activity, /getSharedPreferences\(DynoRuntime\.PREFS, MODE_PRIVATE\)/);
  assert.match(activity, /putString\(DynoRuntime\.LAST_OBD, id\)/);
  assert.match(activity, /startForegroundService\(service\)/);
  assert.match(activity, /restoreObdConnection\(\)/);
  assert.doesNotMatch(activity, /obd\.close\(\)|vault\.close\(\)/);
  assert.match(runtime, /@Volatile private var shared/);
});

test('socket reconnection retries with bounded backoff, never two concurrent workers', () => {
  const reader = source('java/com/omegas/dyno/lab/ObdBluetoothReader.kt');
  assert.match(reader, /if \(worker != null\)/);
  assert.match(reader, /private fun keepConnected\(\)/);
  assert.match(reader, /intArrayOf\(2, 5, 10, 20, 30\)/);
  assert.match(reader, /state\("reconectando em \$seconds s"/);
  assert.match(reader, /while \(!closed && desiredId != null\)/);
  assert.match(reader, /Mode 01 allowlist/);
});

test('battery exemption is requested transparently and only with user approval', () => {
  const activity = source('java/com/omegas/dyno/lab/DynoActivity.kt');
  const manifest = source('AndroidManifest.xml');
  assert.match(manifest, /android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS/);
  assert.match(activity, /Settings\.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS/);
  assert.match(activity, /setPositiveButton\("Autorizar"/);
  assert.match(activity, /setNegativeButton\("Agora não"/);
  assert.match(activity, /batteryIsUnrestricted\(\)/);
  const script = source('assets/dashboard.js');
  const html = source('assets/dashboard.html');
  assert.match(script, /requestBatteryOptimization/);
  assert.match(html, /id="battery-authorize"/);
});

test('APK package identity remains exactly the previous version and version increases', () => {
  const gradle = fs.readFileSync(path.join(__dirname, '../build.gradle.kts'), 'utf8');
  assert.match(gradle, /applicationId\s*=\s*"com\.omegas\.dyno\.lab"/);
  assert.match(gradle, /versionCode\s*=\s*10/);
  assert.match(gradle, /versionName\s*=\s*"1\.0\.0-persistente"/);
});