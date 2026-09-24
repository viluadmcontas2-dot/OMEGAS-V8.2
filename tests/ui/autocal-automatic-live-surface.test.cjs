'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const cockpitCss = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/styles-autocal-cockpit.css'), 'utf8');
const app = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/app.js'), 'utf8');
const monitor = fs.readFileSync(path.join(root, 'app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt'), 'utf8');

assert.equal(cockpit.includes('Consultar ECU'), false, 'AutoCal cockpit must not require a manual ECU read');
assert.doesNotMatch(cockpit, /Consulte a ECU/i, 'operator copy must not tell the user to manually refresh ECU state');
assert.equal(cockpit.includes('data-autocal-read'), false, 'manual reader controls must not be exposed on the operational surface');
assert.equal(cockpit.includes('this.api.startRead()'), false, 'cockpit must rely on automatic native monitor');
assert.equal(cockpit.includes('autocal-secondary-details'), true, 'secondary information must be collapsed below the chart');
assert.equal(cockpit.includes('data-autocal-toggle'), true, 'acquisition control remains available');
assert.match(cockpitCss, /\.app-shell\.autocal-focus \.autocal-chart-host,[\s\S]*height:\s*clamp\(420px,\s*68vh,\s*520px\)/);
assert.match(app, /setCadenceMs\(route === 'autocal' \? 50 : 200\)/);
assert.match(monitor, /snapshotRequested = newSessionId > 0L/);
assert.match(monitor, /snapshotReason = if \(newSessionId > 0L\) "SESSION_BOOTSTRAP"/);

console.log('AUTOCAL_AUTOMATIC_LIVE_SURFACE=PASS');
