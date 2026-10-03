const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/curve.js'), 'utf8');

const buttons = [
  { dataset: { curveView: 'overview' }, classList: { toggle() {} } },
  { dataset: { curveView: 'editor' }, classList: { toggle() {} } },
  { dataset: { curveView: 'autocal' }, classList: { toggle() {} } },
];
const panels = [
  { dataset: { curvePanel: 'overview' }, classList: { toggle() {} } },
  { dataset: { curvePanel: 'editor' }, classList: { toggle() {} } },
  { dataset: { curvePanel: 'autocal' }, classList: { toggle() {} } },
];

const document = {
  querySelector() { return null; },
  querySelectorAll(selector) {
    if (selector === '[data-curve-view]') return buttons;
    if (selector === '[data-curve-panel]') return panels;
    return [];
  },
  getElementById() { return null; },
};

const context = { globalThis: null, document, console };
context.globalThis = context;
vm.createContext(context);
require('./_support.cjs').preload(context);
vm.runInContext(source, context);

const screen = Object.create(context.OmegasUi.CurveScreen.prototype);
screen.view = 'editor';
screen.store = { get: () => ({}) };
screen.renderOverview = () => { throw new Error('external AutoCal tab must not trigger the overview'); };
screen.renderChart = () => { throw new Error('external AutoCal tab must not force editor'); };

assert.equal(screen.setView('autocal'), false);
assert.equal(screen.view, 'editor');
console.log('CURVE_AUTOCAL_INTEROPERABILITY=PASS');
