const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/curve.js'), 'utf8');

const buttons = [
  { dataset: { curveView: 'learning' }, classList: { toggle() {} } },
  { dataset: { curveView: 'editor' }, classList: { toggle() {} } },
  { dataset: { curveView: 'autocal' }, classList: { toggle() {} } },
];
const panels = [
  { dataset: { curvePanel: 'learning' }, classList: { toggle() {} } },
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
vm.runInNewContext(source, context);

const screen = Object.create(context.OmegasUi.CurveScreen.prototype);
screen.view = 'editor';
screen.store = { get: () => ({}) };
screen.renderLearning = () => { throw new Error('AutoCal must not trigger learning'); };
screen.renderChart = () => { throw new Error('AutoCal must not force editor'); };
let rendered = 0;
screen.renderAutoCal = () => { rendered += 1; };

assert.equal(screen.setView('autocal'), true);
assert.equal(screen.view, 'autocal');
assert.equal(rendered, 1);
console.log('CURVE_AUTOCAL_INTEROPERABILITY=PASS');
