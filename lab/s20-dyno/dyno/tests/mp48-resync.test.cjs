const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const base=path.join(__dirname,'../src/main/java/com/omegas/dyno/lab');
const mp48=fs.readFileSync(path.join(base,'Mp48UsbReader.kt'),'utf8');
const hub=fs.readFileSync(path.join(base,'TelemetryHub.kt'),'utf8');
const activity=fs.readFileSync(path.join(base,'DynoActivity.kt'),'utf8');
test('USB presence is exposed independently from validated MP48 telemetry',()=>{
 assert.match(activity,/usbPresent/);
 assert.match(activity,/usbAuthorized/);
 assert.match(hub,/lastGoodMp48Ns/);
 assert.match(hub,/lastGoodAgeMs/);
});
test('one corrupt telemetry echo does not force long USB reconnect',()=>{
 assert.match(mp48,/consecutiveBadTelemetry/);
 assert.match(mp48,/MAX_BAD_TELEMETRY/);
 assert.match(mp48,/purgeHwBuffers\(true, true\)/);
 assert.match(mp48,/wire = Wire\(port\)/);
});
test('read-only retry remains telemetry only, with bounded physical re-open',()=>{
 assert.match(mp48,/Mp48Command\.TELEMETRY/);
 assert.match(mp48,/intArrayOf\(8, 15, 30, 45\)/);
 assert.match(mp48,/MAX_BAD_TELEMETRY\s*=\s*3/);
});
