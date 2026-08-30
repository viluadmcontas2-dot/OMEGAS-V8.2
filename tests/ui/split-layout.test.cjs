const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/components/split-layout.js'), 'utf8');
const style = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/styles-split-layout.css'), 'utf8');

assert.equal(source.includes('app?.store'), true);
assert.equal(source.includes('app?.router'), true);
assert.equal(source.includes("addEventListener('resize'"), true);
assert.equal(source.includes('setInterval'), false);
assert.equal(source.includes('new Store'), false);
assert.equal(source.includes('new Router'), false);
assert.equal(source.includes('store.patch'), false);
assert.equal(source.includes('router.navigate'), false);
assert.equal(source.includes("dataset.layout = compact ? 'split-compact' : 'full-width'"), true);
for (const selector of ['.map-workspace', '.curve-workspace', '.predictor-workspace', '.autocal-layout']) {
  assert.equal(style.includes(selector), true, `missing split reflow for ${selector}`);
}
console.log('SPLIT_LAYOUT_CONTRACT=PASS');
