const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { match, doesNotMatch, deepEqual } = require('node:assert/strict');
const { test } = require('node:test');

const main = join(__dirname, '../src/main');
const read = path => readFileSync(join(main, path), 'utf8');
const lab = 'java/com/omegas/dyno/lab/';
const service = () => read(lab + 'PassiveObdBackgroundService.kt');

test('background service opens only bonded OBDII with secure SPP and reads only', () => {
    const source = service();
    match(source, /adapter\.bondedDevices\.filter/);
    match(source, /startsWith\("OBDII"\)/);
    match(source, /devices\.size != 1/);
    match(source, /00001101-0000-1000-8000-00805f9b34fb/);
    deepEqual([...source.matchAll(/\bsocket\.(\w+)/g)].map(result => result[1]).sort(),
        ['close', 'connect', 'inputStream']);
    match(source, /input\.read\(buffer\)/);
    doesNotMatch(source, /\b(?:outputStream|getOutputStream|startDiscovery|fetchUuidsWithSdp|createBond|ObdBluetoothReader|Mp48UsbReader|PendingIntent|startActivity|WebView)\b/);
    // This passive service itself remains network-free. An independent,
    // explicitly configured Gemini investigator requires INTERNET elsewhere.
    doesNotMatch(source, /HttpURLConnection|\bURL\(|https?:\/\//);
    const network = read('res/xml/loopback_network_security.xml');
    match(network, /<base-config cleartextTrafficPermitted="false"\s*\/>/);
    match(network, /<domain[^>]*>127\.0\.0\.1<\/domain>/);

});

test('manifest limits start and stop to privileged callers and declares connected-device foreground service', () => {
    const manifest = read('AndroidManifest.xml');
    const declaration = manifest.match(/<service\b[^>]*android:name="\.PassiveObdBackgroundService"[^>]*\/>/s)?.[0] || '';
    match(declaration, /android:exported="true"/);
    match(declaration, /android:permission="android\.permission\.DUMP"/);
    match(declaration, /android:foregroundServiceType="connectedDevice"/);
    match(manifest, /android\.permission\.FOREGROUND_SERVICE_CONNECTED_DEVICE/);
    match(manifest, /android\.permission\.BLUETOOTH_CONNECT/);
    match(service(), /NotificationManager\.IMPORTANCE_LOW/);
    match(service(), /START_NOT_STICKY/);
});

test('silent reads keep counters at zero and successful chunks go to the existing vault', () => {
    const source = service();
    match(source, /bytes = 0L/);
    match(source, /frames = 0L/);
    match(source, /if \(size == 0\) continue\s+bytes \+= size/);
    match(source, /vault\.observePassiveObd\([^\n]+\)\s+frames\+\+/);
    match(source, /vault\.start\(receiptStamp\(\), passive = true\)/);
    match(source, /finally\s*\{\s*vault\.stop\(session, receiptStamp\(\), passive = true\)/);
    match(source, /AtomicFile\(File\(filesDir, "passive-obd-state\.json"\)\)/);
    match(source, /READ_TIMEOUT_MS = 3_000L/);
    match(source, /RETRY_DELAY_MS = 25_000L/);
    const vault = read(lab + 'VaultStore.kt');
    match(vault, /"dyno-vault\.db"/);
    match(vault, /"capturing_passive" else "capturing"/);
    match(vault, /"OBD_RAW_PASSIVE"/);
    for (const field of ['hex', 'size', 'serialReadAgeNs', 'monoNs', 'utc']) {
        match(vault, new RegExp('\\.put\\("' + field + '"'));
    }
});

[executed on device: S20-FE (abd78d23-57fd-4034-814b-4a26d96e02ff)]