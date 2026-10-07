'use strict';
// Revisto (W2): AutoCal sem cabeçalho/Detalhes/Histórico/Opções; asserções sobre esses blocos foram removidas (2).
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/styles-autocal-cockpit.css'), 'utf8');

assert.equal(cockpit.includes('autocal-secondary-rail'), false,
  'AutoCal must not expose a horizontal secondary rail');

assert.match(css, /\.screen\.autocal-route-screen\s*\{[\s\S]*?overflow-y:\s*auto;/,
  'the AutoCal screen itself must be the single vertical scroll authority');
assert.match(css, /\.autocal-cockpit-view\s*\{[\s\S]*?overflow:\s*visible;/,
  'the cockpit host must not create a nested scroll viewport');
assert.match(css, /\.autocal-cockpit\s*\{[\s\S]*?overflow:\s*visible;/,
  'cockpit must not trap scrolling in an inner viewport');
assert.match(css + fs.readFileSync(path.join(root, 'app/src/main/assets/ui/styles-autocal-refino.css'), 'utf8'), /\.ar-secondary\s*\{[\s\S]*?position:\s*static;/,
  'secondary disclosure must participate in normal vertical flow');
assert.match(fs.readFileSync(path.join(root, 'app/src/main/assets/ui/styles-autocal-refino.css'), 'utf8'), /\.ar-secondary\s*\{[\s\S]*?display:\s*grid;[\s\S]*?grid-template-columns:\s*1fr;/,
  'secondary information must stack vertically');
assert.match(fs.readFileSync(path.join(root, 'app/src/main/assets/ui/styles-autocal-refino.css'), 'utf8'), /\.ar-card\s*\{[\s\S]*?width:\s*100%;[\s\S]*?overflow:\s*visible;/,
  'secondary cards must not create nested scroll containers');
// Actual graph size and visible point context are verified by the rendered layout gate.

console.log('AUTOCAL_VERTICAL_FLOW=PASS');
