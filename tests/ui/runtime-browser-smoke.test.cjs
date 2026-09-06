'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const test = require('node:test');

const ROOT = path.join(__dirname, '../..');

function findBrowser() {
  for (const candidate of ['google-chrome', 'google-chrome-stable', 'chromium', 'chromium-browser']) {
    const probe = spawnSync('bash', ['-lc', `command -v ${candidate}`], { encoding: 'utf8' });
    if (probe.status === 0 && probe.stdout.trim()) return probe.stdout.trim();
  }
  return null;
}

test('browser real monta Agora, OBD e Ferramentas com assets empacotados', { timeout: 20000 }, () => {
  const browser = findBrowser();
  assert.ok(browser, 'runner precisa de Chrome/Chromium para o smoke de runtime');

  const harness = pathToFileURL(path.join(ROOT, 'tests/ui/runtime-browser-smoke.html')).href;
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'omegas-ui-smoke-'));
  const run = spawnSync(browser, [
    '--headless=new',
    '--no-sandbox',
    '--disable-gpu',
    '--disable-background-networking',
    '--allow-file-access-from-files',
    `--user-data-dir=${userDataDir}`,
    '--virtual-time-budget=4200',
    '--dump-dom',
    harness,
  ], { cwd: ROOT, encoding: 'utf8', timeout: 15000, maxBuffer: 8 * 1024 * 1024 });

  try { fs.rmSync(userDataDir, { recursive: true, force: true }); } catch (_) {}
  assert.equal(run.status, 0, `browser smoke falhou: ${run.stderr || run.stdout}`);
  assert.match(run.stdout, /data-runtime-smoke="PASS"/,
    `UI não montou as três rotas em browser real:\n${run.stdout.slice(-4000)}\nSTDERR:\n${run.stderr}`);
  assert.match(run.stdout, /PASS: dashboard \+ obd \+ tools montaram em runtime/);
});

console.log('RUNTIME_BROWSER_SMOKE=PASS');
