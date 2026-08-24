import assert from 'node:assert/strict';

const ref = process.env.OMEGAS_REF;
if (!ref) throw new Error('OMEGAS_REF is required');
const base = `https://raw.githubusercontent.com/viluadmcontas2-dot/OMEGAS-V8.2/${ref}/`;

async function read(path) {
  const response = await fetch(base + path, { cache: 'no-store' });
  if (!response.ok) throw new Error(`${path}: HTTP ${response.status}`);
  return response.text();
}

const [index, automotiveCss, floatingJs, floatingCss, steppedJs, manifest] = await Promise.all([
  read('app/src/main/assets/ui/index.html'),
  read('app/src/main/assets/ui/styles-automotive-1280.css'),
  read('app/src/main/assets/ui/components/floating-telemetry.js'),
  read('app/src/main/assets/ui/styles-floating-telemetry.css'),
  read('app/src/main/assets/ui/components/stepped-controls.js'),
  read('app/src/main/AndroidManifest.xml'),
]);

assert.match(index, /styles-automotive-1280\.css/, '1280 stylesheet must be loaded');
assert.match(index, /components\/floating-telemetry\.js/, 'floating telemetry must be loaded globally');
assert.match(index, /components\/stepped-controls\.js/, 'stepped controls must be loaded globally');

assert.match(automotiveCss, /OMEGAS_CANONICAL_VIEWPORT_1280X720/, 'canonical 1280x720 marker missing');
assert.match(automotiveCss, /--rail-width:\s*184px/, 'rail must release horizontal space at 1280');
assert.match(automotiveCss, /min-height:\s*48px/, 'automotive primary touch floor must be 48px');
assert.match(automotiveCss, /max-height:\s*760px/, '720p-specific bounded media contract missing');
assert.doesNotMatch(automotiveCss, /1270/, '1270 must never appear in canonical HMI stylesheet');

assert.match(floatingJs, /omegas\.floating\.v1280/, 'floating persisted state key missing');
assert.match(floatingJs, /data-floating-enabled/, 'settings ON\/OFF control missing');
assert.match(floatingJs, /pointerdown/, 'floating drag start missing');
assert.match(floatingJs, /pointermove/, 'floating drag movement missing');
assert.match(floatingJs, /pointerup|pointercancel/, 'floating drag finish missing');
assert.match(floatingJs, /localStorage/, 'floating state must persist across restart');
assert.match(floatingJs, /setEnabled\(/, 'floating enabled state must be explicit');
assert.doesNotMatch(floatingJs, /serial|writer|startMap|writeMap|writeCurve/i, 'floating must remain observational');

assert.match(floatingCss, /min-width:\s*220px/, 'collapsed floating control must remain touch-readable');
assert.match(floatingCss, /min-height:\s*48px/, 'floating touch target must be at least 48px');

assert.match(steppedJs, /pointerdown/, 'stepper long-press start missing');
assert.match(steppedJs, /setInterval/, 'stepper repeat behavior missing');
assert.match(steppedJs, /data-direct-entry-toggle/, 'direct numeric entry must be on-demand');
assert.doesNotMatch(steppedJs, /OmegasNative|OmegasV7|serial|writer|writeMap|writeCurve/i, 'stepper may only reuse existing UI events');

assert.match(manifest, /android:screenOrientation="landscape"/, 'multimedia candidate must be fixed landscape');

console.log('PREAPK_1280_CONTRACT=PASS');
