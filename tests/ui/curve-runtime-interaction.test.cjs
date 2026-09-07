const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const test = require('node:test');

const ROOT = path.join(__dirname, '../..');
function findBrowser() {
  const candidates = process.platform === 'win32'
    ? ['C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe']
    : ['google-chrome', 'google-chrome-stable', 'chromium', 'chromium-browser'];
  for (const candidate of candidates) {
    if (process.platform === 'win32') { if (fs.existsSync(candidate)) return candidate; continue; }
    const probe = spawnSync('bash', ['-lc', `command -v ${candidate}`], { encoding: 'utf8' });
    if (probe.status === 0 && probe.stdout.trim()) return probe.stdout.trim();
  }
  return null;
}

test('Curva K executa seleção em lote e Set absoluto em browser real', { timeout: 20000 }, () => {
  const browser = findBrowser();
  assert.ok(browser, 'runner precisa de Chrome/Chromium para o smoke da Curva K');
  const harness = pathToFileURL(path.join(ROOT, 'tests/ui/curve-runtime-interaction-smoke.html')).href;
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'omegas-curve-smoke-'));
  const run = spawnSync(browser, [
    '--headless=new', '--no-sandbox', '--disable-gpu', '--disable-background-networking',
    '--allow-file-access-from-files', `--user-data-dir=${userDataDir}`,
    '--virtual-time-budget=6500', '--dump-dom', harness,
  ], { cwd: ROOT, encoding: 'utf8', timeout: 15000, maxBuffer: 8 * 1024 * 1024 });
  try { fs.rmSync(userDataDir, { recursive: true, force: true }); } catch (_) {}
  assert.equal(run.status, 0, `browser curve smoke falhou: ${run.stderr || run.stdout}`);
  assert.match(run.stdout, /data-curve-runtime="PASS"/, `Curva K runtime falhou:\n${run.stdout.slice(-5000)}`);
  assert.match(run.stdout, /PASS: CURVE_K_BROWSER_INTERACTION_OK/);
});

console.log('CURVE_RUNTIME_INTERACTION=PASS');
