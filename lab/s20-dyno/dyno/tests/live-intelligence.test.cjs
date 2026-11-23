const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const read = f => fs.readFileSync(path.join(__dirname, '../src/main/', f), 'utf8');
test('app emits bounded local-only OBD/MP48 telemetry without network upload', () => {
 const src=read('java/com/omegas/dyno/lab/TelemetryStream.kt');
 assert.match(src, /MediaStore\.Downloads/);
 assert.match(src, /Download\/Omegas\/Inteligencia/);
 assert.match(src, /LinkedBlockingQueue<String>\(8192\)/);
 assert.doesNotMatch(src, /HttpURLConnection|https?:\/\//);
 const runtime=read('java/com/omegas/dyno/lab/DynoRuntime.kt');
 assert.match(runtime, /stream\.record\(observation\)/);
 assert.match(runtime, /vault\.start\(\)/);
});
test('MP48 and OBD survive activity and are owned by one application session', () => {
 const activity=read('java/com/omegas/dyno/lab/DynoActivity.kt');
 const service=read('java/com/omegas/dyno/lab/ObdKeepAliveService.kt');
 assert.doesNotMatch(activity, /mp48\.close\(\)|obd\.close\(\)|vault\.close\(\)/);
 assert.match(service, /session\.mp48\.start\(device\)/);
 assert.match(service, /session\.obd\.start\(savedId\)/);
 assert.match(read('java/com/omegas/dyno/lab/Mp48UsbReader.kt'), /intArrayOf\(8, 15, 30, 45\)/);
});
test('new fuel data remains evidence and leaves control/ECU writes untouched', () => {
 const code=read('java/com/omegas/dyno/lab/Protocols.kt');
 assert.match(code, /ECU_VOLTAGE\(0x42/);
 assert.match(code, /FUEL_TYPE\(0x51/);
 assert.match(code, /ETHANOL_PERCENT\(0x52/);
 assert.match(code, /0x40 to "0140"/);
 const js=read('assets/dashboard.js');
 assert.match(js, /getIntelligence/);
 assert.match(js, /configureGemini/);
 assert.match(js, /SEM_MODELO_VALIDADO|Ainda não é possível identificar só pelo OBD/);
 const man=read('AndroidManifest.xml');
 assert.match(man, /android.permission.INTERNET/);
 assert.match(read('res/xml/loopback_network_security.xml'), /cleartextTrafficPermitted="false"/);
});

[executed on device: S20-FE (abd78d23-57fd-4034-814b-4a26d96e02ff)]