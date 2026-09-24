'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/styles-autocal-cockpit.css'), 'utf8');

assert.equal(cockpit.includes('autocal-secondary-rail'), false,
  'AutoCal must not expose a horizontal secondary rail');
assert.equal(cockpit.includes('autocal-secondary-stack'), true,
  'secondary AutoCal content must use a vertical stack');
assert.ok(cockpit.indexOf('autocalReferenceChart') < cockpit.indexOf('autocal-secondary-details'),
  'graph must precede all secondary content in reading order');

assert.match(css, /\.autocal-cockpit-view\s*\{[\s\S]*?overflow-y:\s*auto;/,
  'the page must own vertical scrolling');
assert.match(css, /\.autocal-cockpit\s*\{[\s\S]*?overflow:\s*visible;/,
  'cockpit must not trap scrolling in an inner viewport');
assert.match(css, /\.autocal-secondary-details\s*\{[\s\S]*?position:\s*static;/,
  'secondary disclosure must participate in normal vertical flow');
assert.match(css, /\.autocal-secondary-stack\s*\{[\s\S]*?display:\s*grid;[\s\S]*?grid-template-columns:\s*1fr;/,
  'secondary information must stack vertically');
assert.match(css, /\.autocal-secondary-card\s*\{[\s\S]*?width:\s*100%;[\s\S]*?overflow:\s*visible;/,
  'secondary cards must not create nested scroll containers');
assert.match(css, /\.app-shell\.autocal-focus \.autocal-chart-host,[\s\S]*?height:\s*clamp\(420px,\s*68vh,\s*520px\)/,
  'graph must dominate the initial surface');

console.log('AUTOCAL_VERTICAL_FLOW=PASS');
