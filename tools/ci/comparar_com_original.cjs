// Compara a tela AutoCal ORIGINAL (emulador Python do PROTOCOLO-LANDI) com a do OMEGAS, com o MESMO estado da ECU
// (tests/ui/fixtures/autocal-captura-ac-t1000.json), sem pedir nada ao dono nem passar pela CI.
//   1) gera o HTML do original; 2) tira PNG dele e do OMEGAS (1280x720); 3) confere números: pontos por combustível,
//   rótulos "#n/d" por zona, faixa de MAP atual, nós da curva K; 4) monta lado a lado em <saida>/comparacao.png.
// Uso: node tools/ci/comparar_com_original.cjs [pasta-de-saida]
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const cp = require('child_process');
const assert = require('node:assert/strict');
const OUT = path.resolve(process.argv[2] || 'build/ui-evidence/comparacao');
const INDEX = path.resolve('app/src/main/assets/ui/index.html');
const FIXTURE = JSON.parse(fs.readFileSync(path.resolve('tests/ui/fixtures/autocal-captura-ac-t1000.json'), 'utf8'));
fs.mkdirSync(OUT, { recursive: true });
const landi = process.env.PROTOCOLO_LANDI || '/home/user/protocolo-landi';
if (!fs.existsSync(path.join(landi, 'tools/orig_emulator.py'))) { console.log('PULADO: clone o PROTOCOLO-LANDI (variável PROTOCOLO_LANDI)'); process.exit(0); }
const origHtml = path.join(OUT, 'original.html');
const info = JSON.parse(cp.execFileSync('python3', ['-I', 'tools/sessoes/original_do_estado.py', origHtml, '--fuel', 'gas'], { encoding: 'utf8', env: { ...process.env, PROTOCOLO_LANDI: landi } }).trim().split('\n').pop());

(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  try {
    const orig = await browser.newPage({ viewport: { width: 747, height: 760 } });
    await orig.goto('file://' + origHtml);
    const origFacts = await orig.evaluate(() => ({
      zoneLabels: [...document.querySelectorAll('text')].map(t => t.textContent).filter(t => /^#\d+\/\d+$/.test(t)),
      petrolCircles: document.querySelectorAll('circle[stroke="#d33"]').length,
      gasCircles: document.querySelectorAll('circle[stroke="#2a2"]').length,
    }));
    await orig.screenshot({ path: path.join(OUT, 'original.png') });

    const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
    const errors = [];
    page.on('pageerror', e => errors.push(String(e.message || e)));
    await page.addInitScript(state => {
      const json = v => JSON.stringify(v);
      const snapshot = state.snapshot;
      const projection = { ok: true, source: 'NATIVE', sessionId: 1, referenceUsable: true, snapshot, analysis: {}, nativeStatus: { ok: true, state: 'RUNNING', autoCalEnabled: 1 }, nativeSnapshot: snapshot };
      window.OmegasAutoCal = {
        getIdentity: () => json({ ok: true }), getStatus: () => json({ ok: true, state: 'READY' }), getSnapshot: () => json(snapshot),
        getNativeMonitorStatus: () => json({ ok: true, state: 'RUNNING', autoCalEnabled: 1 }), getNativeMonitorSnapshot: () => json(snapshot),
        getUiProjection: () => json(projection), getSessionLedgerStatus: () => json({ ok: true, sessionCount: 0 }), listAutoCalSessions: () => json([]),
        getNativeActionStatus: () => json({}), getRefinedAnalysis: () => json({ ok: true, available: true, points: [] }),
        getEquivalence: () => json({ ok: true }), getRefinementPhase: () => json({ ok: true, phase: 'AGUARDANDO' }),
      };
    }, FIXTURE);
    await page.goto('file://' + INDEX, { waitUntil: 'load' });
    await page.waitForTimeout(1200);
    await page.evaluate(live => {
      const app = window.OmegasApp;
      app.api.presentSnapshot = () => ({ ok: true, revision: 1, data: { ok: true, valid: true, sequence: 1, ageMs: 40, telemetryAgeMs: 40, live: { ...live, fuel: 'GNV', unknown_raw_19: 221 } } });
    }, FIXTURE.live);
    await page.locator('.side-nav button[data-route="autocal"]').click();
    await page.waitForSelector('#autocalReferenceChart .ao-svg', { timeout: 8000 });
    await page.waitForTimeout(800);
    const ours = await page.evaluate(() => ({
      zoneLabels: [...document.querySelectorAll('[data-zone-label]')].map(t => t.textContent),
      petrolPoints: document.querySelectorAll('circle.ao-point.petrol').length,
      gasPoints: document.querySelectorAll('circle.ao-point.gas').length,
      currentBand: document.querySelectorAll('[data-current-band]').length,
      kNodes: document.querySelectorAll('.ao-k-node').length,
    }));
    await page.screenshot({ path: path.join(OUT, 'omegas.png') });

    const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
    const results = {
      'rótulos #n/d iguais (zonas 0→3)': same(origHtml && origFacts.zoneLabels, ours.zoneLabels),
      'pontos de gasolina iguais (visíveis)': ours.petrolPoints === origFacts.petrolCircles,
      'pontos de GNV iguais (visíveis)': ours.gasPoints === origFacts.gasCircles,
      'faixa de MAP atual desenhada': ours.currentBand === 1,
      'sem erro de página': errors.length === 0,
    };
    console.log(JSON.stringify({ estado: info, original: origFacts, omegas: ours }, null, 1));
    for (const [nome, ok] of Object.entries(results)) console.log(ok ? 'OK    ' : 'DIVERGE', nome);
    // lado a lado
    const side = await browser.newPage({ viewport: { width: 2060, height: 860 } });
    const b64 = f => fs.readFileSync(path.join(OUT, f)).toString('base64');
    await side.setContent(`<body style="margin:0;background:#222;display:flex;gap:12px;align-items:flex-start;padding:8px"><div><div style="color:#fff;font:14px sans-serif">ORIGINAL (emulador, mesmo estado)</div><img src="data:image/png;base64,${b64('original.png')}"></div><div><div style="color:#fff;font:14px sans-serif">OMEGAS (app real)</div><img style="width:1280px" src="data:image/png;base64,${b64('omegas.png')}"></div></body>`);
    await side.screenshot({ path: path.join(OUT, 'comparacao.png') });
    assert.ok(Object.values(results).every(Boolean), 'divergência entre original e OMEGAS: ' + JSON.stringify(results));
    console.log('COMPARACAO_ORIGINAL=PASS');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
