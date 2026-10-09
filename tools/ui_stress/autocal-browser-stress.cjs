'use strict';
// Execução OPT-IN: NODE_PATH aponta ao Playwright instalado fora do repo, OMEGAS_CHROMIUM ao Chrome existente.
// Exercita a UI REAL em Chromium com telemetria derivada de sessão real + ECU simulada. Não é prova física.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { open, go, playwright, chromiumPath } = require('../../tests/ui/render/lib.js');
const { SIM_SCRIPT } = require('../../tests/ui/helpers/teia-sim.cjs');

const pw = playwright();
if (!pw || !chromiumPath(pw.chromium)) {
  console.error('STRESS_NOT_RUN: Chrome e Playwright são obrigatórios, sem skip silencioso.');
  process.exit(2);
}
const output = process.env.OMEGAS_STRESS_OUT || path.join(process.cwd(), 'tests/ui/__teia__/out-stress');
fs.mkdirSync(output, { recursive: true });
const n = Math.max(100, Math.min(10000, Number(process.env.OMEGAS_STRESS_EVENTS || 1400)));
const stageEvents = Math.ceil(n / 3);
const delayMs = Math.max(1, Number(process.env.OMEGAS_STRESS_DELAY_MS || 9));
const report = { kind: 'UI_BROWSER_STRESS_SIMULATED_ECU', nativeHardwareValidated: false, eventsRequested: n, eventsInjected: 0, stages: [], errors: [] };
const delay = ms => new Promise(r => setTimeout(r, ms));

async function feed(page, stage, qty) {
  return page.evaluate(async ({ stage, qty, delayMs }) => {
    // O fator altera SOMENTE a velocidade do replay real de telemetria do mock.
    window.__SPEED = 24;
    let seed = 0x1234 + stage * 919;
    const rand = () => { seed ^= seed << 13; seed ^= seed >>> 17; seed ^= seed << 5; return seed >>> 0; };
    const started = performance.now();
    for (let i = 0; i < qty; i++) {
      const band = rand() % 18;
      const fuel = (rand() % 4) === 0 ? 'petrol' : 'gas';
      const arr = fuel === 'petrol' ? window.__ss.p : window.__ss.g;
      // Contadores válidos monotônicos dentro da época; interrupção brusca = outra época.
      const count = Math.max(arr[band], 1 + rand() % 12);
      window.__sim.acquire(fuel, [band], count);
      await new Promise(r => setTimeout(r, delayMs));
    }
    return { eventCount: qty, wallMs: Math.round(performance.now() - started), counter: window.__ss.rev };
  }, { stage, qty, delayMs });
}
async function stage(o, name, eventCount, scenario) {
  const page = o.page;
  const started = Date.now();
  if (scenario === 'CLEAR') await page.evaluate(() => window.__sim.clear());
  if (scenario === 'AUTOMATCH_EMPTY') await page.evaluate(() => window.__sim.automatch());
  if (eventCount) {
    const result = await feed(page, report.stages.length + 1, eventCount);
    report.eventsInjected += result.eventCount;
  }
  if (!['CLEAR', 'AUTOMATCH_EMPTY', 'POST_RESET_EMPTY'].includes(scenario)) {
    // Cada estágio de aquisição termina completo; resets mantêm o quadro vazio.
    await page.evaluate(() => {
      const bands = Array.from({ length: 18 }, (_, i) => i);
      window.__sim.acquire('petrol', bands, 12);
      window.__sim.acquire('gas', bands, 12);
    });
  }
  const finalEventAt = Date.now();
  let expected = await page.evaluate(() => {
    const p = window.__sim.state();
    return [...p.p.map((v,i) => v > 0 ? 'PETROL:'+i : null), ...p.g.map((v,i) => v > 0 ? 'GAS:'+i : null)].filter(Boolean).sort();
  });
  await page.waitForFunction(want => {
    const got = [...document.querySelectorAll('#autocalReferenceChart circle.autocal-acquired-point')].map(c => c.dataset.autocalPointKey).sort();
    const cockpitRevision = window.OmegasApp?.autoCalCockpit?.projection?.revision;
    const simulatedRevision = window.__ss?.rev;
    return JSON.stringify(got) === JSON.stringify(want) && Number(cockpitRevision) === Number(simulatedRevision);
  }, expected, { timeout: 12000, polling: 100 });
  const timeToConfirmedUiMs = Date.now() - finalEventAt;
  const ui = await page.evaluate(() => ({
    acquired: [...document.querySelectorAll('#autocalReferenceChart circle.autocal-acquired-point')].map(c => c.dataset.autocalPointKey),
    petrolZones: [...document.querySelectorAll('#autocalReferenceChart [data-autocal-zone-surface]')].map(e => e.dataset.petrolState),
    gasZones: [...document.querySelectorAll('#autocalReferenceChart [data-autocal-zone-surface]')].map(e => e.dataset.gasState),
    svgCount: document.querySelectorAll('#autocalReferenceChart svg.autocal-reference-svg').length,
    grid: document.querySelectorAll('#autocalReferenceChart .autocal-grid-line').length,
    visiblePrevious: [...document.querySelectorAll('#autocalScreenHost *')].some(e => e.children.length === 0 && /leitura anterior/i.test(e.textContent || '')),
    polls: window.__polls,
    revision: window.__ss.rev,
    frameIntervals: (window.__stressFrames || []).slice(-4000),
  }));
  assert.equal(ui.svgCount, 1, name + ': o gráfico não pode sumir');
  assert.ok(ui.grid >= 8, name + ': grade desapareceu');
  assert.equal(ui.visiblePrevious, false, name + ': leitura anterior reapareceu');
  assert.deepEqual(ui.acquired.slice().sort(), expected, name + ': pontos desenhados divergem da ECU simulada');
  if (ui.acquired.length === 36) {
    assert.deepEqual(ui.petrolZones, Array(4).fill('acquired'), name+': gasolina não marcou 4 zonas');
    assert.deepEqual(ui.gasZones, Array(4).fill('acquired'), name+': GNV não marcou 4 zonas');
  }
  const shot = path.join(output, name + '.png');
  await page.screenshot({ path:shot });
  const percentile = (a, p) => { const sorted = a.slice().sort((x,y)=>x-y); return sorted.length ? Math.round(sorted[Math.floor((sorted.length-1)*p)]*10)/10 : null; };
  report.stages.push({ name, injected: eventCount, scenario, expectedPoints: expected.length, shownPoints: ui.acquired.length, polls: ui.polls,
    revision: ui.revision, wallMs: Date.now()-started, timeToConfirmedUiMs, rafP95ms:percentile(ui.frameIntervals,.95), rafP99ms:percentile(ui.frameIntervals,.99),
    screenshot:path.basename(shot) });
  assert.ok(ui.polls >= 1, 'o aplicativo precisa consumir projeções do bridge');
}

(async () => {
  const o = await open(pw.chromium, 'connected');
  try {
    const page = o.page;
    await delay(800); await go(page,'autocal'); await delay(1500);
    await page.evaluate(SIM_SCRIPT);
    await page.evaluate(() => {
      window.__SPEED=24;
      window.__stressFrames=[];
      let previous=performance.now();
      (function track(now) {
        window.__stressFrames.push(now-previous);
        if(window.__stressFrames.length>6000) window.__stressFrames.shift();
        previous=now;
        requestAnimationFrame(track);
      })(performance.now());
    });
    await stage(o,'00-quadro-vazio',0,'CLEAR');
    await stage(o,'01-carga-alta',stageEvents,'FULL');
    await stage(o,'02-automatch-zerou-gnv',0,'AUTOMATCH_EMPTY');
    await stage(o,'03-reaprendizagem',stageEvents,'FULL');
    await stage(o,'04-reset-duplo',0,'CLEAR');
    await stage(o,'05-nova-sessao',n-2*stageEvents,'FULL');
    report.errors=o.errors.slice();
    assert.equal(report.eventsInjected,n);
    assert.equal(report.stages.at(-1).shownPoints,36);
    assert.equal(report.errors.filter(s=>s.startsWith('pageerror:')).length,0,'exceções no navegador');
    console.log('OMEGAS_UI_STRESS_PASS',JSON.stringify({n,stages:report.stages.length,last:report.stages.at(-1),errors:report.errors.length}));
  } catch (error) {
    report.failed=String(error?.stack||error);
    console.error('OMEGAS_UI_STRESS_FAILED',report.failed);
    process.exitCode=1;
  } finally {
    fs.writeFileSync(path.join(output,'stress-report.json'),JSON.stringify(report,null,2));
    await o.browser.close();
  }
})().catch(e=>{console.error(e.stack||e);process.exitCode=1;});
